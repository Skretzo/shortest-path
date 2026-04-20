# Dashboard Guide

## Purpose

The dashboard is a developer tool for inspecting pathfinder behavior visually.

It exists for cases where a pass/fail assertion is not enough:

- a route is unexpectedly unreachable
- a route reaches the target but uses the wrong transport
- a bank-state transition happens in the wrong place
- a user reports a bad destination and we want a lightweight regression case
- profiling data needs visual inspection (phase timings, heatmaps, queue sizes)

Instead of looking only at a failing JUnit assertion, the dashboard lets you inspect:

- the rendered route on a Leaflet map
- path statistics (nodes checked, transports checked, elapsed time)
- transports used along the path
- where banked state begins
- profiler phase breakdowns, sub-phase timings, and time-series charts
- tile visit heatmaps showing search distribution
- multiple datasets through one shared UI with a bundle selector

## Site Model

The dashboard site is a static bundle under:

`build/reports/pathfinder-dashboard`

It contains one shared frontend plus multiple report bundles:

```text
build/reports/pathfinder-dashboard/
  index.html
  app.js
  profiler.js
  styles.css
  bundles/
    index.json
    <bundle-name>/
      report.json
      heatmaps/
        <run-name>.json     (tile visit counts, one per route)
```

`bundles/index.json` is the registry the frontend reads first. Each bundle directory contains a `report.json` with all route results and, when profiling is enabled, a `heatmaps/` directory with per-route tile visit count data.

## Profiler

### Architecture

The profiler is implemented as a test-only pathfinder that replicates the production search loop with timing instrumentation added at each phase boundary.

Key files:

- `src/test/java/shortestpath/pathfinder/ProfilingPathfinder.java` — mirrors `Pathfinder.java`'s search loop, wrapping each phase in `System.nanoTime()` calls
- `src/test/java/shortestpath/pathfinder/PathfinderProfile.java` — data class collecting all profiling measurements
- `src/test/resources/reachability-dashboard/profiler.js` — frontend rendering of profiler data (bar charts, time-series, heatmap overlay)

### What It Measures

**Top-level phases** (accumulated nanos per search loop iteration):
- `queueSelection` — dequeuing the next node from the priority queue
- `addNeighbors` — expanding tile and transport neighbors
- `targetCheck` — checking if the current node is a target
- `wildernessCheck` — wilderness level boundary handling
- `cutoffCheck` — cost cutoff evaluation
- `bookkeeping` — iteration counter updates and sampling

**Sub-phases within addNeighbors**:
- `bankCheck` — checking bank proximity for state transitions
- `transportLookup` — looking up transports at the current position
- `collisionCheck` — collision map queries
- `walkableTile` — processing walkable tile neighbors
- `blockedTileTransport` — checking transports on blocked tiles
- `abstractNode` — abstract node expansion

**Counters**:
- `tileNeighborsAdded`, `transportNeighborsAdded` — neighbor counts (match `nodesChecked`/`transportsChecked` in `PathfinderResult`)
- `visitedSkipped` — nodes skipped because already visited
- `transportEvaluations` — total transport evaluations attempted
- `blockedTileTransportChecks` — blocked-tile transport lookups
- `bankTransitions`, `wildernessLevelChanges` — state change counts
- `peakBoundarySize`, `peakPendingSize` — high-water marks for queue sizes

**Time series** (sampled every 2000 iterations):
- boundary queue size, pending queue size, current cost, elapsed nanos

**Heatmap**:
- sparse map of `packedPosition → visitCount` for every tile visited during search

### Keeping the Profiler in Sync

`PathfinderTest.profilingDoesNotAffectResults` verifies that `ProfilingPathfinder` produces identical results to `Pathfinder` across multiple route types. It checks:

- path equality (every step's position and bank-visited flag)
- result metadata (reached, terminationReason, nodesChecked, transportsChecked)
- profiling data validity (phase nanos > 0, sub-phase nanos > 0, counter consistency, heatmap non-empty, elapsed nanos > 0)

This test catches any drift between the production pathfinder and the profiling replica. When modifying `Pathfinder.java`'s search loop, run this test to confirm the profiler still mirrors it faithfully.

## Datasets

### Named Convenience Tasks

These tasks have fixed bundle names and are used for standard workflows:

#### `pathfinder-tests` — `./gradlew scenarioDashboard`

Source: `PathfinderTest` (curated scenarios in Java)

Use for: transport-choice regressions, bank-state routing, hand-authored routes needing visual inspection and JUnit assertions.

#### `clue-steps-default` — `./gradlew clueStepsDashboard`

Source: `src/test/resources/reachability/clue_locations_full.csv`

Use for: broad reachability sweeps over the clue-step corpus after changing pathfinding behavior. Profiling is **off** by default.

#### `reported-targets` — `./gradlew targetsDashboard`

Source: `src/test/resources/reachability/targets.tsv`

Use for: lightweight regression list of user-reported unreachable destinations.

#### `dashboardSite` — `./gradlew dashboardSite`

Builds all three named bundles above into one site.

### Generic `dashboard` Task

The `dashboard` task is the flexible entry point for ad-hoc runs with custom datasets, bundle names, and profiling:

```bash
./gradlew dashboard \
  -PdashboardDataset=/reachability/clue_locations_full.csv \
  -PdashboardBundle=clue-steps-profiled \
  -PdashboardTitle="Clue Steps Profiled" \
  -PdashboardProfile=true
```

Properties:

| Property | Default | Description |
|---|---|---|
| `dashboardDataset` | `/reachability/routes.csv` | Classpath resource for the input CSV/TSV |
| `dashboardBundle` | `routes` | Bundle directory name |
| `dashboardTitle` | `Pathfinder Dashboard` | Title shown in the UI |
| `dashboardSubtitle` | *(empty)* | Subtitle shown in the UI |
| `dashboardProfile` | `true` | Enable profiling (heatmaps, phase timings) |
| `dashboardScenario` | *(empty)* | Set to `profiler` for profiler-specific scenario mode |

Profiling is **on** by default for the generic task, and **off** for `clueStepsDashboard` and `targetsDashboard` (to keep those runs fast).

### Profiled Runs

For profiled variants of the standard datasets, use the generic task with explicit bundle names:

```bash
# Profiled clue steps
./gradlew dashboard \
  -PdashboardDataset=/reachability/clue_locations_full.csv \
  -PdashboardBundle=clue-steps-profiled \
  -PdashboardTitle="Clue Steps Profiled"

# Profiled routes
./gradlew dashboard \
  -PdashboardBundle=routes-profiled \
  -PdashboardTitle="Routes Profiled"
```

These produce bundles with heatmaps and full profiler data, suitable for performance analysis.

## How To Choose A Dataset

Use `targets.tsv` first if the problem is "this destination should be reachable".

Use `PathfinderTest` if the problem is about route quality, bank usage, transport choice, or any case that needs custom setup and assertions.

Use the full clue-step CSV when you want a broader sweep after changing core search behavior.

Use a profiled run when you need to understand performance characteristics — where time is spent, which tiles are visited most, how queue sizes evolve.

## Suggested Workflow

For a reported unreachable destination:

1. Add the coordinate to `src/test/resources/reachability/targets.tsv`.
2. Run `./gradlew targetsDashboard`.
3. Serve the output directory locally.
4. Inspect the route in the dashboard.
5. If the case needs richer setup or a stronger assertion, promote it into `PathfinderTest`.

For a routing regression:

1. Add or update a scenario in `PathfinderTest`.
2. Run `./gradlew scenarioDashboard`.
3. Inspect the route and confirm the assertion still matches the intended behavior.

For profiling after a pathfinder change:

1. Run profiled bundles for the datasets you care about.
2. Compare heatmaps and phase breakdowns against the pre-change baselines.
3. Use `scripts/analyze_heatmap_counts.py` to get distribution statistics.

## Serving The Site

Open the generated site through a local webserver, not `file://`, because the frontend loads bundle JSON dynamically.

```bash
python -m http.server --directory build/reports/pathfinder-dashboard 8000
```

Then open `http://localhost:8000`.

## Scripts

Helper scripts in `scripts/`:

| Script | Purpose |
|---|---|
| `analyze_heatmap_counts.py` | Analyze tile visit count distributions from heatmap JSON files in a bundle |
| `check_tsv.py` | Validate TSV transport files for format errors |
| `tsv-lint.sh` | Lint all transport TSV files |
| `compute_changed_coordinates.sh` | Diff coordinate changes between branches |
| `diff_coordinate_json.py` | Compare coordinate JSON files |
| `dump_transport_coordinates.py` | Extract transport coordinates from TSV files |
| `gen_clue_csv.py` | Generate the clue locations CSV from source data |

## Implementation Split

The implementation is split into:

1. `PathfinderDashboardReportWriter` — builds the report payload (route results, profiler data, metadata)
2. `DashboardBundlePublisher` — publishes a named bundle into the shared site root and updates `bundles/index.json`
3. `PathfinderDashboardAssetWriter` — writes the shared frontend assets (`index.html`, `app.js`, `profiler.js`, `styles.css`)
