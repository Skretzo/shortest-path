# Dashboard Guide

## Purpose

The dashboard is a developer tool for inspecting pathfinder behavior visually.

It exists for cases where a pass/fail assertion is not enough:

- a route is unexpectedly unreachable
- a route reaches the target but uses the wrong transport
- a bank-state transition happens in the wrong place
- a user reports a bad destination and we want a lightweight regression case

Instead of looking only at a failing JUnit assertion, the dashboard lets you inspect:

- the rendered route
- path statistics
- transports used
- where banked state begins
- multiple datasets through one shared UI

## Site Model

The dashboard site is a static bundle under:

`build/reports/pathfinder-dashboard`

It contains one shared frontend plus multiple report bundles:

```text
build/reports/pathfinder-dashboard/
  index.html
  app.js
  styles.css
  bundles/
    index.json
    pathfinder-tests/
      report.json
    clue-steps-default/
      report.json
    reported-targets/
      report.json
```

`bundles/index.json` is the registry the frontend reads first. Each bundle is just a `report.json` plus metadata in the shared index.

## Current Datasets

### `pathfinder-tests`

Source:
- `PathfinderTest`

Purpose:
- curated scenario debugging
- transport-choice regressions
- bank-state routing regressions
- interesting hand-authored routes worth visual inspection

When to add here:
- the behavior is specific to route selection
- the scenario needs custom setup in test code
- you want an assertion over exact path length or transport usage

Built by:
- `./gradlew scenarioDashboard`

### `clue-steps-default`

Source:
- `src/test/resources/reachability/clue_locations_full.csv`

Purpose:
- broad sweep over the larger clue-step dataset
- catch systemic reachability problems
- understand coverage and performance on a realistic batch of targets

When to use:
- after changing pathfinding behavior broadly
- when validating transport availability changes
- when you want confidence over the full clue-step corpus

Built by:
- `./gradlew clueStepsDashboard`

### `reported-targets`

Source:
- `src/test/resources/reachability/targets.csv`

Purpose:
- lightweight regression list
- easy contributor workflow for reported unreachable destinations
- small, reviewable target set that runs cheaply in CI

When to add here:
- a user reports a single unreachable destination
- you want a small PR that adds coordinates without editing Java code
- the case does not need bespoke scenario setup

Built by:
- `./gradlew targetsDashboard`

## How To Choose A Dataset

Use `targets.csv` first if the problem is “this destination should be reachable”.

Use `PathfinderTest` if the problem is about route quality, bank usage, transport choice, or any case that needs custom setup and assertions.

Use the full clue-step CSV when you want a broader sweep after changing core search behavior.

## Local Development

Useful tasks:

- `./gradlew scenarioDashboard`
- `./gradlew clueStepsDashboard`
- `./gradlew targetsDashboard`
- `./gradlew dashboardSite`

`dashboardSite` builds the shared site with all current bundles.

All dashboard tasks write into the same root, controlled by:

`-Ddashboard.outputRoot=...`

The dashboard-specific tasks use `ignoreFailures = true`, so a failing scenario can still emit a report bundle for inspection.

## Suggested Workflow

For a reported unreachable destination:

1. Add the coordinate to `src/test/resources/reachability/targets.csv`.
2. Run `./gradlew targetsDashboard`.
3. Serve the output directory locally.
4. Inspect the route in the dashboard.
5. If the case needs richer setup or a stronger assertion, promote it into `PathfinderTest`.

For a routing regression:

1. Add or update a scenario in `PathfinderTest`.
2. Run `./gradlew scenarioDashboard`.
3. Inspect the route and confirm the assertion still matches the intended behavior.

## Serving The Site

Open the generated site through a local webserver, not `file://`, because the frontend loads bundle JSON dynamically.

Example:

```bash
python -m http.server --directory build/reports/pathfinder-dashboard 8000
```

Then open:

`http://localhost:8000`

## Implementation Split

The current implementation is split into three parts:

1. `PathfinderDashboardReportWriter`
   Builds a report payload.
2. `DashboardBundlePublisher`
   Publishes a named bundle into the shared site root and updates `bundles/index.json`.
3. `PathfinderDashboardAssetWriter`
   Writes the shared frontend assets.

This keeps data producers separate from the shared dashboard host.
