// URL configuration and stable DOM references used across the dashboard.
const params = new URLSearchParams(window.location.search);
const reportUrl = params.get("report");
const bundleIdFromQuery = params.get("bundle");
const bundleIndexUrl = "bundles/index.json";
const tileBaseUrl = "https://maps.runescape.wiki/osrs/versions/2026-03-04_a/tiles/rendered";
const MAP_ID = -1;
const MIN_ZOOM = -4;
const MIN_NATIVE_ZOOM = -2;
const MAX_ZOOM = 4;
const MAX_NATIVE_ZOOM = 3;
const summaryEl = document.getElementById("summary");
const bundleSelectEl = document.getElementById("bundle-select");
const runListEl = document.getElementById("run-list");
const runDetailsEl = document.getElementById("run-details");
const runSearchEl = document.getElementById("run-search");
const unreachedOnlyEl = document.getElementById("unreached-only");
const centerTargetEl = document.getElementById("center-target");
const prevRunEl = document.getElementById("prev-run");
const nextRunEl = document.getElementById("next-run");
const mapCoordinatesEl = document.getElementById("map-coordinates");
const transportInfoEl = document.getElementById("transport-info");
const mapHelpButtonEl = document.getElementById("map-help-button");
const mapHelpOverlayEl = document.getElementById("map-help-overlay");
const mapHelpCloseEl = document.getElementById("map-help-close");
const mapHelpContentEl = document.getElementById("map-help-content");

// Mutable dashboard state shared by rendering and interaction handlers.
let currentPlane = 0;
let availableBundles = [];
let selectedRun = null;
let allRuns = [];
let reportTransportLayers = [];
let selectedTransportOverlay = null;
let hoveredTileOverlay = null;

// Leaflet tile setup for the RuneScape Wiki raster format and simple world CRS.
const WikiTileLayer = L.TileLayer.extend({
  getTileUrl(coords) {
    return `${tileBaseUrl}/${MAP_ID}/${coords.z}/${currentPlane}_${coords.x}_${-(1 + coords.y)}.png`;
  },

  createTile(coords, done) {
    const tile = L.TileLayer.prototype.createTile.call(this, coords, done);
    tile.onerror = () => {};
    return tile;
  }
});

const map = L.map("map", {
  crs: L.CRS.Simple,
  minZoom: MIN_ZOOM,
  maxZoom: MAX_ZOOM,
  zoomSnap: 1,
  center: [3200, 3200],
  zoom: 1,
  maxBounds: [[-1000, -1000], [13800, 13800]],
  maxBoundsViscosity: 0.5
});

map.attributionControl.addAttribution('&copy; <a href="https://runescape.wiki/">RuneScape Wiki</a>');

const baseLayer = new WikiTileLayer("", {
  minZoom: MIN_ZOOM,
  minNativeZoom: MIN_NATIVE_ZOOM,
  maxNativeZoom: MAX_NATIVE_ZOOM,
  maxZoom: MAX_ZOOM,
  noWrap: true
}).addTo(map);
const transportRenderer = L.canvas({ padding: 0.2 });

const transportLayerState = {
  entry: false,
  exit: false,
  teleport: false
};

const mapHelpSections = [
  {
    title: "Scenario inspection",
    items: [
      "Click a scenario in the left-hand list to draw its route and populate the details panel.",
      "Use Previous and Next to move through the currently filtered scenario list.",
      "Center on target recenters the map on the selected run's target tile."
    ]
  },
  {
    title: "Map interaction",
    items: [
      "Move the mouse across the map to preview the hovered tile and read its coordinates.",
      "Ctrl-click a tile to copy its world coordinate as x, y, plane.",
      "Click an entry, exit, or teleport overlay marker to highlight that transport and inspect its metadata."
    ]
  },
  {
    title: "Transport layers",
    items: [
      "Use the Transport Layers control in the top-right corner to toggle entries, exits, and teleports.",
      "Entry and exit overlays are filtered to the currently selected plane.",
      "Transport colors distinguish inventory-valid, bank-valid, and invalid states."
    ]
  }
];

const TransportLayerControl = L.Control.extend({
  options: {
    position: "topright"
  },

  onAdd() {
    const container = L.DomUtil.create("div", "leaflet-bar leaflet-control transport-layer-control");
    const fieldset = L.DomUtil.create("fieldset", "", container);
    const legend = L.DomUtil.create("legend", "", fieldset);
    legend.textContent = "Transport Layers";

    [
      { key: "entry", label: "Entries" },
      { key: "exit", label: "Exits" },
      { key: "teleport", label: "Teleports" }
    ].forEach(item => {
      const label = L.DomUtil.create("label", "", fieldset);
      const input = L.DomUtil.create("input", "", label);
      input.type = "checkbox";
      input.checked = transportLayerState[item.key];
      const text = L.DomUtil.create("span", "", label);
      text.textContent = item.label;
      L.DomEvent.disableClickPropagation(label);
      L.DomEvent.on(input, "change", () => {
        transportLayerState[item.key] = input.checked;
        renderTransportOverlays();
      });
    });

    L.DomEvent.disableClickPropagation(container);
    L.DomEvent.disableScrollPropagation(container);
    return container;
  }
});

map.addControl(new TransportLayerControl());

let markerLayers = [];
let transportOverlayLayers = [];

// Coordinate helpers translate between world points and Leaflet's simple CRS.
function worldToLatLng(point) {
  return [point.y + 0.5, point.x + 0.5];
}

function latLngToWorldPoint(latlng) {
  return {
    x: Math.floor(latlng.lng),
    y: Math.floor(latlng.lat),
    plane: currentPlane
  };
}

function formatCoordinateText(point) {
  return `${point.x}, ${point.y}, ${point.plane}`;
}

function updateMapCoordinates(point, copied = false) {
  mapCoordinatesEl.textContent = copied
    ? `Copied: ${formatCoordinateText(point)}`
    : formatCoordinateText(point);
}

// Overlay helpers manage transient tile highlights and route markers.
function renderHoveredTile(point) {
  if (hoveredTileOverlay) {
    map.removeLayer(hoveredTileOverlay);
  }

  hoveredTileOverlay = L.rectangle([
    [point.y, point.x],
    [point.y + 1, point.x + 1]
  ], {
    color: "#fde047",
    weight: 1.5,
    fillColor: "#fde047",
    fillOpacity: 0.1,
    interactive: false
  }).addTo(map);
}

function clearLayers() {
  markerLayers.forEach(layer => map.removeLayer(layer));
  transportOverlayLayers.forEach(layer => map.removeLayer(layer));
  if (selectedTransportOverlay) {
    map.removeLayer(selectedTransportOverlay);
    selectedTransportOverlay = null;
  }
  if (hoveredTileOverlay) {
    map.removeLayer(hoveredTileOverlay);
    hoveredTileOverlay = null;
  }
  markerLayers = [];
  transportOverlayLayers = [];
  transportInfoEl.hidden = true;
}

function addMarker(point, label, color, radius = 6) {
  const marker = L.circleMarker(worldToLatLng(point), {
    radius,
    color,
    fillColor: color,
    fillOpacity: 0.9
  }).bindTooltip(label);
  marker.addTo(map);
  markerLayers.push(marker);
}

// Transport overlay rendering turns transport metadata into clickable map affordances.
function transportLayerColor(kind, validity) {
  const palette = {
    entry: {
      INVENTORY_VALID: "#2d6a4f",
      BANK_VALID: "#93c5a2",
      INVALID: "#6b7280"
    },
    exit: {
      INVENTORY_VALID: "#d1495b",
      BANK_VALID: "#f2a6af",
      INVALID: "#6b7280"
    },
    teleport: {
      INVENTORY_VALID: "#7c3aed",
      BANK_VALID: "#c4b5fd",
      INVALID: "#6b7280"
    }
  };
  return (palette[kind] && palette[kind][validity]) || "#6b7280";
}

function transportLayerStyle(validity) {
  if (validity === "INVENTORY_VALID") {
    return { radius: 5, fillOpacity: 0.85, weight: 2 };
  }
  if (validity === "BANK_VALID") {
    return { radius: 5, fillOpacity: 0.55, weight: 2 };
  }
  return { radius: 4, fillOpacity: 0.25, weight: 1 };
}

function isTeleportLayer(layer) {
  return layer.origin == null;
}

function transportLayerLabel(layer, kind) {
  const label = layer.displayInfo || layer.objectInfo || layer.type;
  const suffix = kind === "teleport" ? "teleport" : kind;
  return `${label} ${suffix} [${layer.validity}]`;
}

function showTransportInfo(layer, clickedKind) {
  const label = layer.displayInfo || layer.objectInfo || layer.type;
  const lines = [
    label,
    `Type: ${layer.type}`,
    `Validity: ${layer.validity}`,
    `Clicked: ${clickedKind}`
  ];
  if (layer.origin) {
    lines.push(`Entry: ${formatPoint(layer.origin)}`);
  }
  lines.push(`Exit: ${formatPoint(layer.destination)}`);
  transportInfoEl.textContent = lines.join("\n");
  transportInfoEl.hidden = false;
}

function highlightTransport(layer, clickedKind) {
  if (selectedTransportOverlay) {
    map.removeLayer(selectedTransportOverlay);
  }

  const overlayLayers = [];
  if (layer.origin) {
    overlayLayers.push(L.circleMarker(worldToLatLng(layer.origin), {
      renderer: transportRenderer,
      radius: 8,
      color: "#f59e0b",
      fillColor: "#fde68a",
      fillOpacity: 0.9,
      weight: 3
    }));
  }
  overlayLayers.push(L.circleMarker(worldToLatLng(layer.destination), {
    renderer: transportRenderer,
    radius: 8,
    color: "#f59e0b",
    fillColor: "#fde68a",
    fillOpacity: 0.9,
    weight: 3
  }));
  if (layer.origin) {
    overlayLayers.push(L.polyline([worldToLatLng(layer.origin), worldToLatLng(layer.destination)], {
      color: "#f59e0b",
      weight: 2,
      dashArray: "6 4",
      interactive: false
    }));
  }

  selectedTransportOverlay = L.layerGroup(overlayLayers).addTo(map);
  showTransportInfo(layer, clickedKind);

  if (layer.origin) {
    const target = clickedKind === "entry" ? layer.destination : layer.origin;
    map.flyTo(worldToLatLng(target), Math.max(map.getZoom(), 1), { duration: 0.4 });
  } else {
    map.flyTo(worldToLatLng(layer.destination), Math.max(map.getZoom(), 1), { duration: 0.4 });
  }
}

function addTransportOverlay(point, label, kind, validity, layer) {
  const color = transportLayerColor(kind, validity);
  const style = transportLayerStyle(validity);
  const marker = L.circleMarker(worldToLatLng(point), {
    renderer: transportRenderer,
    radius: style.radius,
    color,
    fillColor: color,
    fillOpacity: style.fillOpacity,
    weight: style.weight
  }).bindTooltip(label);
  marker.on("click", () => highlightTransport(layer, kind));
  marker.addTo(map);
  transportOverlayLayers.push(marker);
}

function renderTransportOverlays() {
  transportOverlayLayers.forEach(layer => map.removeLayer(layer));
  transportOverlayLayers = [];

  if (!reportTransportLayers || reportTransportLayers.length === 0) {
    return;
  }

  reportTransportLayers.forEach(layer => {
    const teleport = isTeleportLayer(layer);
    if (!teleport && transportLayerState.entry && layer.origin && layer.origin.plane === currentPlane) {
      addTransportOverlay(layer.origin, transportLayerLabel(layer, "entry"), "entry", layer.validity, layer);
    }

    if (layer.destination.plane !== currentPlane) {
      return;
    }

    if (teleport) {
      if (transportLayerState.teleport) {
        addTransportOverlay(layer.destination, transportLayerLabel(layer, "teleport"), "teleport", layer.validity, layer);
      }
    } else if (transportLayerState.exit) {
      addTransportOverlay(layer.destination, transportLayerLabel(layer, "exit"), "exit", layer.validity, layer);
    }
  });
}

// Run list and route rendering own the scenario-centric sidebar and path visualization.
function statusLabel(run) {
  const route = run.reached ? "reached" : "unreached";
  if (run.assertionPassed === true) return `pass, ${route}`;
  if (run.assertionPassed === false) return `fail, ${route}`;
  return route;
}

function formatPoint(point) {
  return `(${point.x}, ${point.y}, ${point.plane})`;
}

function markerColor(kind) {
  if (kind === "start") return "#1d4ed8";
  if (kind === "target") return "#2d6a4f";
  if (kind === "closest") return "#d97706";
  if (kind === "bank") return "#f59e0b";
  return "#475569";
}

function pathSegmentColor(reached, bankVisited) {
  if (bankVisited) {
    return reached ? "#7c3aed" : "#b45309";
  }
  return reached ? "#2d6a4f" : "#d1495b";
}

function buildPathSegments(path) {
  if (!path || path.length < 2) {
    return [];
  }

  const segments = [];
  let currentSegment = [path[0], path[1]];
  let currentBankVisited = Boolean(path[1].bankVisited);

  for (let i = 2; i < path.length; i++) {
    const point = path[i];
    const edgeBankVisited = Boolean(point.bankVisited);
    if (edgeBankVisited !== currentBankVisited) {
      segments.push({ points: currentSegment, bankVisited: currentBankVisited });
      currentSegment = [path[i - 1], point];
      currentBankVisited = edgeBankVisited;
    } else {
      currentSegment.push(point);
    }
  }

  segments.push({ points: currentSegment, bankVisited: currentBankVisited });
  return segments;
}

function buildDetails(run) {
  const details = [
    `Name: ${run.name}`,
    `Category: ${run.category || "default"}`,
    `Assertion: ${run.assertionPassed === true ? "passed" : run.assertionPassed === false ? "failed" : "n/a"}`,
    `Reached: ${run.reached}`,
    `Termination: ${run.terminationReason}`,
    `Path length: ${run.path.length}`,
    `Nodes checked: ${run.stats.nodesChecked}`,
    `Transports checked: ${run.stats.transportsChecked}`,
    `Elapsed: ${(run.stats.elapsedNanos / 1_000_000).toFixed(2)} ms`,
    `Start: ${formatPoint(run.start)}`,
    `Target: ${formatPoint(run.target)}`,
    `Closest reached: ${formatPoint(run.closestReachedPoint)}`
  ];

  if (run.assertionMessage) {
    details.splice(3, 0, `Assertion message: ${run.assertionMessage}`);
  }

  if (run.details && run.details.length > 0) {
    details.push("");
    details.push("Scenario details:");
    run.details.forEach(detail => details.push(`- ${detail}`));
  }

  if (run.markers && run.markers.length > 0) {
    details.push("");
    details.push("Markers:");
    run.markers.forEach(marker => details.push(`- ${marker.label}: ${formatPoint(marker.point)}`));
  }

  if (run.transports && run.transports.length > 0) {
    details.push("");
    details.push("Transports used:");
    run.transports.forEach(step => {
      const label = step.displayInfo || step.objectInfo || step.type;
      details.push(`- step ${step.stepIndex}: ${step.type} -> ${label}`);
    });
  }

  return details.join("\n");
}

async function copyCoordinate(point) {
  const text = formatCoordinateText(point);
  if (navigator.clipboard && navigator.clipboard.writeText) {
    await navigator.clipboard.writeText(text);
    return true;
  }

  const input = document.createElement("textarea");
  input.value = text;
  input.setAttribute("readonly", "");
  input.style.position = "absolute";
  input.style.left = "-9999px";
  document.body.appendChild(input);
  input.select();
  let copied = false;
  try {
    copied = document.execCommand("copy");
  } finally {
    document.body.removeChild(input);
  }
  return copied;
}

function normalizeSearchText(text) {
  return (text || "").toLowerCase().replace(/\s+/g, " ").trim();
}

function buildRunSearchText(run) {
  const parts = [
    run.name,
    run.category,
    run.terminationReason,
    ...(run.details || []),
    ...(run.transports || []).map(step => `${step.type} ${step.displayInfo || step.objectInfo || ""}`)
  ];
  return normalizeSearchText(parts.join(" "));
}

function fuzzyScore(query, text) {
  if (!query) {
    return 0;
  }

  if (text.includes(query)) {
    return query.length * 1000 - text.indexOf(query);
  }

  let score = 0;
  let textIndex = 0;
  let consecutive = 0;
  for (let i = 0; i < query.length; i++) {
    const ch = query[i];
    const foundIndex = text.indexOf(ch, textIndex);
    if (foundIndex === -1) {
      return Number.NEGATIVE_INFINITY;
    }

    if (foundIndex === textIndex) {
      consecutive += 1;
      score += 20 + consecutive * 5;
    } else {
      consecutive = 0;
      score += 5;
    }

    textIndex = foundIndex + 1;
  }

  return score - (text.length - query.length);
}

function filteredRuns() {
  const baseRuns = unreachedOnlyEl.checked ? allRuns.filter(run => !run.reached) : allRuns;
  const query = normalizeSearchText(runSearchEl.value);
  if (!query) {
    return baseRuns;
  }

  return baseRuns
    .map(run => ({ run, score: fuzzyScore(query, buildRunSearchText(run)) }))
    .filter(entry => entry.score > Number.NEGATIVE_INFINITY)
    .sort((a, b) => b.score - a.score || a.run.name.localeCompare(b.run.name))
    .map(entry => entry.run);
}

function updateRunNavigation() {
  const runs = filteredRuns();
  const selectedIndex = selectedRun ? runs.indexOf(selectedRun) : -1;
  const hasSelection = selectedIndex !== -1;

  prevRunEl.disabled = !hasSelection || selectedIndex === 0;
  nextRunEl.disabled = !hasSelection || selectedIndex === runs.length - 1;
}

function renderRunList() {
  const runs = filteredRuns();
  runListEl.innerHTML = "";

  if (runs.length === 0) {
    const item = document.createElement("li");
    item.textContent = "No matching scenarios.";
    runListEl.appendChild(item);
    updateRunNavigation();
    return;
  }

  runs.forEach(run => {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.textContent = `[${statusLabel(run)}] ${run.name}`;
    if (selectedRun === run) {
      button.classList.add("selected");
    }
    button.addEventListener("click", () => {
      renderRun(run);
      renderRunList();
    });
    item.appendChild(button);
    runListEl.appendChild(item);
  });

  updateRunNavigation();
}

function renderRun(run) {
  selectedRun = run;
  clearLayers();
  currentPlane = run.target.plane;
  baseLayer.redraw();

  const pathSegments = buildPathSegments(run.path || []);
  if (pathSegments.length > 0) {
    const polylines = pathSegments.map(segment => {
      const polyline = L.polyline(segment.points.map(worldToLatLng), {
        color: pathSegmentColor(run.reached, segment.bankVisited),
        weight: 3
      }).addTo(map);
      markerLayers.push(polyline);
      return polyline;
    });
    const group = L.featureGroup(polylines);
    map.fitBounds(group.getBounds().pad(0.25));
  } else {
    map.setView(worldToLatLng(run.target), 2);
  }

  (run.markers || []).forEach(marker => {
    const radius = marker.kind === "bank" ? 5 : 6;
    addMarker(marker.point, marker.label, markerColor(marker.kind), radius);
  });

  runDetailsEl.textContent = buildDetails(run);
  renderTransportOverlays();
}

// Help overlay rendering explains the intended interactive map workflow.
function renderMapHelp() {
  mapHelpContentEl.innerHTML = "";

  mapHelpSections.forEach(section => {
    const heading = document.createElement("h3");
    heading.textContent = section.title;
    mapHelpContentEl.appendChild(heading);

    const list = document.createElement("ul");
    section.items.forEach(item => {
      const entry = document.createElement("li");
      entry.textContent = item;
      list.appendChild(entry);
    });
    mapHelpContentEl.appendChild(list);
  });
}

function setMapHelpVisible(visible) {
  mapHelpOverlayEl.hidden = !visible;
}

// Data loading keeps the selected bundle, report payload, and URL state aligned.
function updateBundleQuery(bundleId) {
  const nextParams = new URLSearchParams(window.location.search);
  if (bundleId) {
    nextParams.set("bundle", bundleId);
  } else {
    nextParams.delete("bundle");
  }
  const query = nextParams.toString();
  window.history.replaceState({}, "", `${window.location.pathname}${query ? `?${query}` : ""}`);
}

function renderReport(report, selectedBundleTitle) {
  const runs = report.runs || [];
  reportTransportLayers = report.transportLayers || [];
  allRuns = runs;
  selectedRun = null;
  clearLayers();
  summaryEl.textContent =
    `${selectedBundleTitle ? `${selectedBundleTitle}\n` : ""}` +
    `${report.summary.successfulRuns}/${report.summary.totalRuns} reached, ` +
    `${report.summary.failedRuns} unreached` +
    (report.subtitle ? `\n${report.subtitle}` : "");

  if (runs.length === 0) {
    runDetailsEl.textContent = "No runs in report.json";
    runListEl.innerHTML = "";
    return;
  }

  currentPlane = runs[0].target.plane;
  baseLayer.redraw();
  renderRun(runs[0]);
  renderRunList();
}

async function loadReport(url, selectedBundleTitle) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to load ${url}`);
  }
  const report = await response.json();
  renderReport(report, selectedBundleTitle);
}

async function loadBundle(bundle) {
  if (bundleSelectEl) {
    bundleSelectEl.value = bundle.id;
  }
  updateBundleQuery(bundle.id);
  await loadReport(bundle.reportPath, bundle.title);
}

async function loadBundleIndex() {
  if (reportUrl) {
    if (bundleSelectEl) {
      bundleSelectEl.disabled = true;
    }
    await loadReport(reportUrl, null);
    return;
  }

  const response = await fetch(bundleIndexUrl);
  if (!response.ok) {
    throw new Error(`Failed to load ${bundleIndexUrl}`);
  }
  const index = await response.json();
  const bundles = index.bundles || [];
  availableBundles = bundles;
  if (!bundleSelectEl) {
    throw new Error("Bundle selector element is missing");
  }
  bundleSelectEl.innerHTML = "";

  if (bundles.length === 0) {
    throw new Error("No report bundles found");
  }

  bundles.forEach(bundle => {
    const option = document.createElement("option");
    option.value = bundle.id;
    option.textContent = bundle.title || bundle.id;
    bundleSelectEl.appendChild(option);
  });
  bundleSelectEl.disabled = false;

  const selectedBundle = bundles.find(bundle => bundle.id === bundleIdFromQuery) || bundles[0];
  await loadBundle(selectedBundle);
}

loadBundleIndex().catch(error => {
  summaryEl.textContent = error.message;
  runDetailsEl.textContent = "Unable to render dashboard.";
});

renderMapHelp();

if (bundleSelectEl) {
  bundleSelectEl.addEventListener("change", async () => {
    try {
      const bundle = availableBundles.find(entry => entry.id === bundleSelectEl.value);
      if (!bundle) {
        throw new Error(`Unknown bundle: ${bundleSelectEl.value}`);
      }
      await loadBundle(bundle);
    } catch (error) {
      summaryEl.textContent = error.message;
    }
  });
}

mapHelpButtonEl.addEventListener("click", () => {
  setMapHelpVisible(true);
});

mapHelpCloseEl.addEventListener("click", () => {
  setMapHelpVisible(false);
});

mapHelpOverlayEl.addEventListener("click", event => {
  if (event.target === mapHelpOverlayEl) {
    setMapHelpVisible(false);
  }
});

// Event wiring below keeps filters, navigation, and map gestures in sync.
runSearchEl.addEventListener("input", () => {
  const runs = filteredRuns();
  renderRunList();
  if (runs.length === 0) {
    selectedRun = null;
    runDetailsEl.textContent = "No matching scenarios.";
    clearLayers();
    return;
  }

  if (!selectedRun || !runs.includes(selectedRun)) {
    renderRun(runs[0]);
    renderRunList();
  }
});

unreachedOnlyEl.addEventListener("change", () => {
  const runs = filteredRuns();
  renderRunList();
  if (runs.length === 0) {
    selectedRun = null;
    runDetailsEl.textContent = "No matching scenarios.";
    clearLayers();
    return;
  }

  if (!selectedRun || !runs.includes(selectedRun)) {
    renderRun(runs[0]);
    renderRunList();
  }
});

centerTargetEl.addEventListener("click", () => {
  if (!selectedRun) {
    return;
  }

  map.setView(worldToLatLng(selectedRun.target), Math.max(map.getZoom(), 2));
});

prevRunEl.addEventListener("click", () => {
  const runs = filteredRuns();
  const selectedIndex = selectedRun ? runs.indexOf(selectedRun) : -1;
  if (selectedIndex <= 0) {
    return;
  }

  renderRun(runs[selectedIndex - 1]);
  renderRunList();
});

nextRunEl.addEventListener("click", () => {
  const runs = filteredRuns();
  const selectedIndex = selectedRun ? runs.indexOf(selectedRun) : -1;
  if (selectedIndex === -1 || selectedIndex >= runs.length - 1) {
    return;
  }

  renderRun(runs[selectedIndex + 1]);
  renderRunList();
});

map.on("mousemove", event => {
  const point = latLngToWorldPoint(event.latlng);
  updateMapCoordinates(point);
  renderHoveredTile(point);
});

map.on("mouseout", () => {
  mapCoordinatesEl.textContent = "-, -, -";
  if (hoveredTileOverlay) {
    map.removeLayer(hoveredTileOverlay);
    hoveredTileOverlay = null;
  }
});

map.on("click", async event => {
  if (!event.originalEvent || !event.originalEvent.ctrlKey) {
    return;
  }

  const point = latLngToWorldPoint(event.latlng);
  const copied = await copyCoordinate(point);
  updateMapCoordinates(point, copied);
  if (copied) {
    console.log(`Copied coordinate: ${formatCoordinateText(point)}`);
  }
});

document.addEventListener("keydown", event => {
  if (event.key === "Escape" && !mapHelpOverlayEl.hidden) {
    setMapHelpVisible(false);
  }
});
