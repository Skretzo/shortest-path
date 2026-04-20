/* Profiler extension for the Pathfinder Dashboard.
 * Renders phase breakdowns, time series, and counters in a collapsible
 * bottom drawer overlaying the map.  Adds a tile-level heatmap as a
 * single canvas image overlay on the main map.
 * Registers itself via window.dashboardExtensions. */
(function () {
  "use strict";

  const drawerEl = document.getElementById("profiler-drawer");
  const toggleBtn = document.getElementById("profiler-drawer-toggle");
  const runOverviewEl = document.getElementById("run-overview");
  const countersEl = document.getElementById("counters-view");
  const phaseChartEl = document.getElementById("phase-chart");
  const subphaseChartEl = document.getElementById("subphase-chart");
  const timeseriesCanvas = document.getElementById("timeseries-chart");

  if (!drawerEl) return;

  // ── Drawer toggle ───────────────────────────────────────────────────

  let pendingTimeSeries = null;

  toggleBtn.addEventListener("click", () => {
    const collapsed = drawerEl.classList.toggle("collapsed");
    toggleBtn.innerHTML = (collapsed ? "&#9650;" : "&#9660;") + " Profiler";
    if (!collapsed && pendingTimeSeries) {
      requestAnimationFrame(() => drawTimeSeries(timeseriesCanvas, pendingTimeSeries));
    }
  });

  // ── Color palettes ──────────────────────────────────────────────────

  const PHASE_COLORS = ["#4682b4", "#e8a838", "#6aaa64", "#c9534b", "#8b5cf6", "#f59e0b", "#999"];
  const SUBPHASE_COLORS = ["#4682b4", "#e8a838", "#6aaa64", "#c9534b", "#8b5cf6", "#f59e0b"];
  const TIMESERIES_COLORS = { boundary: "#4682b4", pending: "#e8a838" };

  const CHUNK_SIZE = 64;
  const HEATMAP_MAX = 55;   // count >= HEATMAP_MAX → magenta; observed max in data
  const HEAT_LOW = 0.20;    // count=1 (t≈0.17) sits just below, count=2 jumps into warm
  const HEAT_MID = 0.45;    // count≈5 is warm, count≈24 is hot orange

  // ── Phase descriptions ──────────────────────────────────────────────

  const PHASE_DESCRIPTIONS = {
    "Add neighbors": "Expanding tile and transport neighbors into the search frontier — the core work of each iteration.",
    "Queue selection": "Picking the next lowest-cost node from boundary or pending priority queues.",
    "Target check": "Testing whether the current node is the destination, and updating the best-known path when unreachable.",
    "Wilderness check": "Evaluating wilderness level constraints when the search crosses wilderness boundaries.",
    "Cutoff check": "Checking System.currentTimeMillis() each iteration to enforce the calculation timeout.",
    "Bookkeeping": "Peak queue size tracking, iteration counting, and periodic time-series sampling.",
    "Loop overhead": "Residual JVM overhead: loop control, System.nanoTime() timing calls, and JIT compilation artifacts."
  };

  const SUBPHASE_DESCRIPTIONS = {
    "Bank check": "Checking whether the current tile is a bank location to enable bank-requiring transports.",
    "Transport lookup": "Looking up available transports at the current tile and adding unvisited destinations.",
    "Collision check": "Reading the collision map to determine which cardinal and diagonal moves are walkable.",
    "Walkable tile": "Iterating traversable directions and creating neighbor nodes for walkable tiles.",
    "Blocked transport": "Fallback for blocked adjacent tiles — checking if a transport origin can bypass the obstacle.",
    "Abstract node": "Expanding abstract (teleport) nodes that provide global connectivity at each wilderness level."
  };

  // ── Helpers ─────────────────────────────────────────────────────────

  function msFromNanos(nanos) {
    return (nanos / 1_000_000).toFixed(2);
  }

  function pct(nanos, total) {
    if (total === 0) return "0.0";
    return ((nanos / total) * 100).toFixed(1);
  }

  function formatK(n) {
    return n >= 1000 ? (n / 1000).toFixed(1) + "K" : String(n);
  }

  // ── Shared floating tooltip ──────────────────────────────────────

  const floatingTip = document.createElement("div");
  floatingTip.className = "floating-tip";
  document.body.appendChild(floatingTip);

  function showFloatingTip(e, text) {
    floatingTip.textContent = text;
    floatingTip.style.display = "block";
    const x = e.clientX + 12;
    const y = e.clientY + 12;
    const fw = floatingTip.offsetWidth;
    const fh = floatingTip.offsetHeight;
    floatingTip.style.left = (x + fw > window.innerWidth ? e.clientX - fw - 8 : x) + "px";
    floatingTip.style.top = (y + fh > window.innerHeight ? e.clientY - fh - 8 : y) + "px";
  }

  function hideFloatingTip() {
    floatingTip.style.display = "none";
  }

  // ── Interactive HTML bar chart renderer ─────────────────────────────

  function renderBarChart(container, items, colors, descriptions) {
    container.innerHTML = "";
    const total = items.reduce((a, b) => a + b.value, 0);

    if (total === 0) {
      container.innerHTML = '<div class="bar-chart-empty">No data</div>';
      return;
    }

    items.forEach((item, i) => {
      const pctVal = pct(item.value, total);
      const msVal = msFromNanos(item.value);
      const widthPct = Math.max(0.5, (item.value / total) * 100);
      const color = colors[i % colors.length];
      const desc = descriptions[item.label] || "";

      const row = document.createElement("div");
      row.className = "bar-row";

      row.innerHTML =
        '<span class="bar-label">' + item.label + '</span>' +
        '<div class="bar-track">' +
          '<div class="bar-fill" style="width:' + widthPct + '%;background:' + color + '"></div>' +
        '</div>' +
        '<span class="bar-value">' + msVal + ' ms (' + pctVal + '%)</span>';

      if (desc) {
        row.addEventListener("mouseenter", (e) => showFloatingTip(e, desc));
        row.addEventListener("mousemove", (e) => showFloatingTip(e, desc));
        row.addEventListener("mouseleave", hideFloatingTip);
      }

      container.appendChild(row);
    });
  }

  // ── Time series renderer (canvas) ───────────────────────────────────

  function drawTimeSeries(canvas, samples) {
    const ctx = canvas.getContext("2d");
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, w, h);

    if (!samples || samples.length === 0) {
      ctx.fillStyle = "#999";
      ctx.font = "13px sans-serif";
      ctx.fillText("No time series data", 10, h / 2);
      return;
    }

    const margin = { top: 20, right: 20, bottom: 40, left: 60 };
    const chartW = w - margin.left - margin.right;
    const chartH = h - margin.top - margin.bottom;

    const maxIter = samples[samples.length - 1].iteration;
    const maxBoundary = Math.max(1, ...samples.map(s => s.boundarySize));
    const maxPending = Math.max(1, ...samples.map(s => s.pendingSize));
    const maxSize = Math.max(maxBoundary, maxPending);

    function xScale(iter) { return margin.left + (iter / maxIter) * chartW; }
    function yScale(val) { return margin.top + chartH - (val / maxSize) * chartH; }

    // Grid lines
    ctx.strokeStyle = "rgba(0,0,0,0.08)";
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = margin.top + (chartH / 4) * i;
      ctx.beginPath();
      ctx.moveTo(margin.left, y);
      ctx.lineTo(w - margin.right, y);
      ctx.stroke();
    }

    function drawLine(key, color) {
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.beginPath();
      samples.forEach((s, i) => {
        const x = xScale(s.iteration);
        const y = yScale(s[key]);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
    }

    drawLine("boundarySize", TIMESERIES_COLORS.boundary);
    drawLine("pendingSize", TIMESERIES_COLORS.pending);

    // Axis labels
    ctx.fillStyle = "#555";
    ctx.font = "11px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("Iteration", margin.left + chartW / 2, h - 6);
    for (let i = 0; i <= 4; i++) {
      ctx.textAlign = "right";
      ctx.fillText(formatK(Math.round(maxSize * (4 - i) / 4)), margin.left - 6, margin.top + (chartH / 4) * i + 4);
    }

    // Legend
    const legendY = 12;
    ctx.font = "11px sans-serif";
    ctx.textAlign = "left";
    [
      { label: "Boundary queue", color: TIMESERIES_COLORS.boundary },
      { label: "Pending queue", color: TIMESERIES_COLORS.pending }
    ].forEach((item, i) => {
      const x = margin.left + i * 140;
      ctx.fillStyle = item.color;
      ctx.fillRect(x, legendY - 8, 12, 12);
      ctx.fillStyle = "#333";
      ctx.fillText(item.label, x + 16, legendY + 2);
    });

    // ── Crosshair interaction ───────────────────────────────────────
    let crosshairCleanup = canvas._crosshairCleanup;
    if (crosshairCleanup) crosshairCleanup();

    const overlay = document.createElement("canvas");
    overlay.className = "crosshair-overlay";
    overlay.width = canvas.width;
    overlay.height = canvas.height;

    const tooltip = document.createElement("div");
    tooltip.className = "crosshair-tooltip";

    canvas.parentElement.style.position = "relative";
    canvas.parentElement.appendChild(overlay);
    canvas.parentElement.appendChild(tooltip);

    function onMouseMove(e) {
      const rect = canvas.getBoundingClientRect();
      const oCtx = overlay.getContext("2d");
      oCtx.clearRect(0, 0, overlay.width, overlay.height);
      oCtx.scale(dpr, dpr);

      const cssX = e.clientX - rect.left;
      const worldX = (cssX - margin.left) / chartW * maxIter;
      if (worldX < 0 || worldX > maxIter) {
        oCtx.setTransform(1, 0, 0, 1, 0, 0);
        tooltip.style.display = "none";
        return;
      }

      // Find nearest sample
      let nearest = samples[0];
      let bestDist = Infinity;
      for (const s of samples) {
        const d = Math.abs(s.iteration - worldX);
        if (d < bestDist) { bestDist = d; nearest = s; }
      }

      const sx = xScale(nearest.iteration);
      // Crosshair line
      oCtx.strokeStyle = "rgba(0,0,0,0.3)";
      oCtx.lineWidth = 1;
      oCtx.setLineDash([4, 3]);
      oCtx.beginPath();
      oCtx.moveTo(sx, margin.top);
      oCtx.lineTo(sx, margin.top + chartH);
      oCtx.stroke();
      oCtx.setLineDash([]);

      // Dots
      [
        { val: nearest.boundarySize, color: TIMESERIES_COLORS.boundary },
        { val: nearest.pendingSize, color: TIMESERIES_COLORS.pending }
      ].forEach(pt => {
        oCtx.fillStyle = pt.color;
        oCtx.beginPath();
        oCtx.arc(sx, yScale(pt.val), 4, 0, Math.PI * 2);
        oCtx.fill();
      });

      oCtx.setTransform(1, 0, 0, 1, 0, 0);

      // DOM tooltip
      tooltip.innerHTML =
        "Iter: " + formatK(nearest.iteration) + "<br>" +
        "Boundary: " + formatK(nearest.boundarySize) + "<br>" +
        "Pending: " + formatK(nearest.pendingSize);
      tooltip.style.display = "block";
      const tipRect = tooltip.getBoundingClientRect();
      const parentRect = canvas.parentElement.getBoundingClientRect();
      const tipX = (sx / w * rect.width) + rect.left - parentRect.left + 12;
      const tipY = margin.top / h * rect.height + 8;
      const flipX = tipX + tipRect.width > parentRect.width - 8;
      tooltip.style.left = (flipX ? tipX - tipRect.width - 24 : tipX) + "px";
      tooltip.style.top = tipY + "px";
    }

    function onMouseLeave() {
      const oCtx = overlay.getContext("2d");
      oCtx.clearRect(0, 0, overlay.width, overlay.height);
      tooltip.style.display = "none";
    }

    canvas.style.pointerEvents = "auto";
    canvas.addEventListener("mousemove", onMouseMove);
    canvas.addEventListener("mouseleave", onMouseLeave);
    canvas._crosshairCleanup = () => {
      canvas.removeEventListener("mousemove", onMouseMove);
      canvas.removeEventListener("mouseleave", onMouseLeave);
      if (overlay.parentElement) overlay.parentElement.removeChild(overlay);
      if (tooltip.parentElement) tooltip.parentElement.removeChild(tooltip);
    };
  }

  // ── Heatmap as tiled grid layer on main map ─────────────────────────

  let heatmapLayer = null;
  let heatmapEnabled = false;
  let currentHeatmapRun = null;

  function heatColor(t) {
    let r, g, b;
    if (t < HEAT_LOW) {
      const s = t / HEAT_LOW;
      r = 0;
      g = Math.round(80 + 175 * s);
      b = Math.round(220 - 80 * s);
    } else if (t < HEAT_MID) {
      const s = (t - HEAT_LOW) / (HEAT_MID - HEAT_LOW);
      r = Math.round(255 * s);
      g = 255;
      b = Math.round(140 - 140 * s);
    } else {
      const s = (t - HEAT_MID) / (1 - HEAT_MID);
      r = 255;
      g = Math.round(255 - 200 * s);
      b = 0;
    }
    return [r, g, b];
  }

  /** Build a spatial index: "chunkX:chunkY" -> [{x, y, count}, ...] */
  function indexTiles(tiles) {
    const index = new Map();
    for (const tile of tiles) {
      const cx = Math.floor(tile.x / CHUNK_SIZE);
      const cy = Math.floor(tile.y / CHUNK_SIZE);
      const key = cx + ":" + cy;
      let list = index.get(key);
      if (!list) { list = []; index.set(key, list); }
      list.push(tile);
    }
    return index;
  }

  const HeatmapGridLayer = L.GridLayer.extend({
    options: { tileSize: 256 },

    setHeatData(tiles, maxCount) {
      this._heatIndex = indexTiles(tiles);
      this._maxCount = maxCount;
      this.redraw();
    },

    createTile(coords) {
      const size = this.options.tileSize;
      const canvas = document.createElement("canvas");
      canvas.width = size;
      canvas.height = size;

      const index = this._heatIndex;
      if (!index) return canvas;

      // Compute world bounds for this Leaflet tile.
      // In CRS.Simple: pixel = latlng * 2^z, tile = floor(pixel / tileSize).
      // Our world uses lat=y, lng=x.  CRS.Simple flips y: pixel_y = -lat * scale.
      const scale = Math.pow(2, coords.z);
      const worldPerTile = size / scale;

      const wxMin = coords.x * worldPerTile;
      const wyMin = -(coords.y + 1) * worldPerTile;
      const wxMax = wxMin + worldPerTile;
      const wyMax = wyMin + worldPerTile;

      // Determine which spatial-index chunks overlap this tile
      const cxMin = Math.floor(wxMin / CHUNK_SIZE);
      const cxMax = Math.floor((wxMax - 1) / CHUNK_SIZE);
      const cyMin = Math.floor(wyMin / CHUNK_SIZE);
      const cyMax = Math.floor((wyMax - 1) / CHUNK_SIZE);

      const ctx = canvas.getContext("2d");
      const imageData = ctx.createImageData(size, size);
      const data = imageData.data;
      const logMax = Math.log1p(this._maxCount);
      let hasData = false;

      for (let ccx = cxMin; ccx <= cxMax; ccx++) {
        for (let ccy = cyMin; ccy <= cyMax; ccy++) {
          const list = index.get(ccx + ":" + ccy);
          if (!list) continue;

          for (const pt of list) {
            if (pt.x < wxMin || pt.x >= wxMax || pt.y < wyMin || pt.y >= wyMax) continue;

            let r, g, b, alpha;
            if (pt.count >= this._maxCount) {
              // Discontinuous error colour: magenta (count = observed max)
              r = 200; g = 0; b = 200; alpha = 230;
            } else {
              const t = Math.min(1, Math.log1p(pt.count) / logMax);
              alpha = Math.round((0.45 + 0.50 * t) * 255);
              [r, g, b] = heatColor(t);
            }

            // Map world coords to canvas pixel rectangle
            const pxStart = Math.floor((pt.x - wxMin) / worldPerTile * size);
            const pyStart = Math.floor((wyMax - pt.y - 1) / worldPerTile * size);
            const pxEnd = Math.max(pxStart + 1, Math.floor((pt.x + 1 - wxMin) / worldPerTile * size));
            const pyEnd = Math.max(pyStart + 1, Math.floor((wyMax - pt.y) / worldPerTile * size));

            for (let py = Math.max(0, pyStart); py < Math.min(size, pyEnd); py++) {
              for (let px = Math.max(0, pxStart); px < Math.min(size, pxEnd); px++) {
                const idx = (py * size + px) * 4;
                data[idx] = r;
                data[idx + 1] = g;
                data[idx + 2] = b;
                data[idx + 3] = alpha;
              }
            }
            hasData = true;
          }
        }
      }

      if (hasData) ctx.putImageData(imageData, 0, 0);
      return canvas;
    }
  });

  // Fixed absolute scale — counts >= HEATMAP_MAX get a discontinuous error colour

  function drawHeatmap(run) {
    const map = window._dashboardMap;
    if (!map) return;
    clearHeatmap();

    if (!run) return;

    // Lazy-load externalized heatmap data (compact flat array: [x1,y1,c1, x2,y2,c2, ...])
    if (!run.tileHeatmap && run.heatmapFile) {
      const base = window.currentBundleBase || "";
      const url = base + run.heatmapFile;
      fetch(url)
        .then(r => { if (!r.ok) throw new Error("Failed to load " + url); return r.json(); })
        .then(flat => {
          const tiles = [];
          for (let i = 0; i < flat.length; i += 3) {
            tiles.push({ x: flat[i], y: flat[i + 1], count: flat[i + 2] });
          }
          run.tileHeatmap = { tiles };
          drawHeatmap(run);
        })
        .catch(e => console.warn("Heatmap load failed:", e));
      return;
    }

    const heatmap = run.tileHeatmap;
    if (!heatmap || !heatmap.tiles || heatmap.tiles.length === 0) return;

    heatmapLayer = new HeatmapGridLayer({
      tileSize: 256,
      minZoom: -4,
      maxZoom: 4,
      updateWhenZooming: false,
      className: "heatmap-canvas-overlay"
    });
    heatmapLayer.setHeatData(heatmap.tiles, HEATMAP_MAX);
    heatmapLayer.addTo(map);
    createLegend();
  }

  function clearHeatmap() {
    if (heatmapLayer && window._dashboardMap) {
      window._dashboardMap.removeLayer(heatmapLayer);
      heatmapLayer = null;
    }
  }

  // ── Heatmap legend ───────────────────────────────────────────────

  let legendControl = null;

  function createLegend() {
    if (legendControl) {
      window._dashboardMap.removeControl(legendControl);
      legendControl = null;
    }
    const Legend = L.Control.extend({
      options: { position: "topright" },
      onAdd() {
        const div = L.DomUtil.create("div", "heatmap-legend leaflet-control");
        // Gradient bar 1–HEATMAP_MAX
        const bar = document.createElement("canvas");
        bar.width = 200;
        bar.height = 14;
        const ctx = bar.getContext("2d");
        for (let i = 0; i < 200; i++) {
          const t = i / 199;
          const [r, g, b] = heatColor(t);
          ctx.fillStyle = "rgb(" + r + "," + g + "," + b + ")";
          ctx.fillRect(i, 0, 1, 14);
        }
        // Labels at log-mapped positions for the actual observed count clusters
        const labels = document.createElement("div");
        labels.style.cssText = "position:relative;height:16px;margin-top:4px;";
        const logMax = Math.log1p(HEATMAP_MAX);
        [1, 2, 5, 24].forEach(count => {
          const pct = (Math.log1p(count) / logMax * 100).toFixed(1);
          const el = document.createElement("span");
          el.textContent = count;
          el.style.cssText = "position:absolute;left:" + pct + "%;transform:translateX(-50%);font:11px/1 var(--font-mono);color:#666;";
          labels.appendChild(el);
        });
        const title = document.createElement("div");
        title.className = "heatmap-legend-title";
        title.textContent = "Visit count (log scale)";
        div.appendChild(title);
        div.appendChild(bar);
        div.appendChild(labels);
        // Error swatch for >=HEATMAP_MAX
        const errorRow = document.createElement("div");
        errorRow.className = "heatmap-legend-error";
        const swatch = document.createElement("span");
        swatch.className = "heatmap-legend-error-swatch";
        const errorLabel = document.createElement("span");
        errorLabel.innerHTML = "≥" + HEATMAP_MAX + " <span style=\"color:#999;font-style:italic\">(empirical max)</span>";
        errorRow.appendChild(swatch);
        errorRow.appendChild(errorLabel);
        div.appendChild(errorRow);
        L.DomEvent.disableClickPropagation(div);
        return div;
      }
    });
    legendControl = new Legend();
    legendControl.addTo(window._dashboardMap);
  }

  function removeLegend() {
    if (legendControl && window._dashboardMap) {
      window._dashboardMap.removeControl(legendControl);
      legendControl = null;
    }
  }

  function toggleHeatmap(enabled) {
    heatmapEnabled = enabled;
    if (enabled && currentHeatmapRun) {
      drawHeatmap(currentHeatmapRun);
    } else {
      clearHeatmap();
      removeLegend();
    }
  }

  // Register heatmap toggle in the main map's transport layer control
  window.addMapLayerToggle({
    label: "Heatmap",
    checked: false,
    onChange: toggleHeatmap
  });

  // ── Run rendering ───────────────────────────────────────────────────

  function renderRunOverview(run) {
    const lines = [
      "Name:         " + run.name,
      "Category:     " + (run.category || "-"),
      "Reached:      " + run.reached,
      "Termination:  " + run.terminationReason,
      "Path length:  " + (run.path || []).length,
      "",
      "Nodes checked:      " + formatK(run.stats.nodesChecked),
      "Transports checked: " + formatK(run.stats.transportsChecked),
      "Total elapsed:      " + msFromNanos(run.stats.elapsedNanos) + " ms",
      "",
      "Start:  (" + run.start.x + ", " + run.start.y + ", " + run.start.plane + ")",
      "Target: (" + run.target.x + ", " + run.target.y + ", " + run.target.plane + ")"
    ];
    runOverviewEl.textContent = lines.join("\n");
  }

  function renderCounters(run) {
    const c = run.counters;
    const lines = [
      "Tile neighbors added:       " + formatK(c.tileNeighborsAdded),
      "Transport neighbors added:  " + formatK(c.transportNeighborsAdded),
      "Visited-skipped:            " + formatK(c.visitedSkipped),
      "Abstract nodes expanded:    " + c.abstractNodesExpanded,
      "Transport evaluations:      " + formatK(c.transportEvaluations),
      "Blocked-tile transport:     " + formatK(c.blockedTileTransportChecks),
      "Bank transitions:           " + c.bankTransitions,
      "Wilderness level changes:   " + c.wildernessLevelChanges,
      "Delayed-visit enqueued:     " + formatK(c.delayedVisitEnqueued),
      "Delayed-visit skipped:      " + formatK(c.delayedVisitSkipped),
      "Peak boundary queue:        " + formatK(c.peakBoundarySize),
      "Peak pending queue:         " + formatK(c.peakPendingSize)
    ];
    countersEl.textContent = lines.join("\n");
  }

  // ── Extension entry point ───────────────────────────────────────────

  window.dashboardExtensions.push({
    renderRun(run) {
      const hasProfiler = run.phases && run.counters;
      drawerEl.hidden = !hasProfiler;
      if (!hasProfiler) return;

      renderRunOverview(run);
      renderCounters(run);

      const p = run.phases;
      renderBarChart(phaseChartEl,
        [
          { label: "Add neighbors", value: p.addNeighborsNanos },
          { label: "Queue selection", value: p.queueSelectionNanos },
          { label: "Target check", value: p.targetCheckNanos },
          { label: "Wilderness check", value: p.wildernessCheckNanos },
          { label: "Cutoff check", value: p.cutoffCheckNanos || 0 },
          { label: "Bookkeeping", value: p.bookkeepingNanos || 0 },
          { label: "Loop overhead", value: p.otherNanos }
        ],
        PHASE_COLORS,
        PHASE_DESCRIPTIONS
      );

      const sp = run.subPhases;
      renderBarChart(subphaseChartEl,
        [
          { label: "Bank check", value: sp.bankCheckNanos },
          { label: "Transport lookup", value: sp.transportLookupNanos },
          { label: "Collision check", value: sp.collisionCheckNanos },
          { label: "Walkable tile", value: sp.walkableTileNanos },
          { label: "Blocked transport", value: sp.blockedTileTransportNanos },
          { label: "Abstract node", value: sp.abstractNodeNanos }
        ],
        SUBPHASE_COLORS,
        SUBPHASE_DESCRIPTIONS
      );

      pendingTimeSeries = run.timeSeries;
      if (!drawerEl.classList.contains("collapsed")) {
        drawTimeSeries(timeseriesCanvas, run.timeSeries);
      }

      // Update heatmap on main map
      currentHeatmapRun = run;
      if (heatmapEnabled) {
        drawHeatmap(run);
      }
    }
  });
})();
