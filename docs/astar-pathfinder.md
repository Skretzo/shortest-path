# A* pathfinder

The A* pathfinder is the plugin's tile search — its only engine. It computes
every shown path and answers every cross-plugin `query`. The legacy engine
survives only in the test sources, as a frozen oracle the parity tests and the
route corpus check A* against. If A* fails to build its routing data or run a
search, the failure is reported — a query answers with `BACKEND_FAILURE` —
rather than silently degrading.

## Layout

A* core code lives in `src/main/java/shortestpath/pathfinder/astar/`.
`AStarPathfinder` adapts it to the plugin's `ActiveSearch` contract, which the
legacy `Pathfinder` also implements. `PathStep`, `PathfinderResult`, packed
coordinates, collision data and transport types are shared with legacy.

`ShortestPathConfig` owns RuneLite user settings. `PathfinderConfig` owns
client/account refresh, requirement evaluation, transport availability, costs,
wilderness preference, bank-path enablement, bank requirements and the
calculation cutoff. A* code consumes prepared values and never evaluates
requirements or reads the client directly.

## Data lifetimes

- **Static routing data** (`RoutingStatic`) is built once per plugin session,
  on the pathfinding thread, the first time an A* search runs
  (`AStarRoutingStaticProvider`, `RoutingStaticLoader`). It comes from the
  precomputed routing component layer `routing-components.bin`,
  `collision-map.zip`, and the transport and bank resources.
- **Prepared account data** (`PreparedRoutingAccount`) is compiled from the
  current account state for each search. The `SiteGraph` built from it is kept
  by `AStarRoutingSession` while the account's routing fingerprint is
  unchanged.
- **Per-target data** (`TargetOverlay`, `ReverseLabels`, `PreparedHeuristic`,
  held by `PreparedTarget`) gives an admissible heuristic for one target set;
  see [astar-heuristic.md](astar-heuristic.md). The session keeps recent
  targets for reuse while the graph and collision map are unchanged.
- **Search state** (`AStarForwardSearch`) belongs to one active search.
  Costs and predecessors use 1,024-entry pages allocated on first write,
  including bank layers and teleport hubs. Reading an untouched page returns
  the initial value without allocating it. Only the small page directories
  are allocated for the full state space upfront.

## Routing components and cuts

Large walking areas are split into routing components along separator cut
edges. The cuts (`src/test/resources/routing-cuts.bin`, generated with KaHIP by
the `routingCuts` task in
[shortest-path-tooling](https://github.com/osrs-pathfinding/shortest-path-tooling))
are an offline input. The test sources derive `src/main/resources/routing-components.bin`
from the collision map and the cuts: a routing component for every
collision-walkable tile, stored as runs, plus the crossings. The plugin ships
only that file. Reachability, sites, exact walks and the sparse network stay
runtime work because they depend on the transports.

`RoutingComponentsFreshnessTest` fails when the layer is stale. The
`ExtractCollisionMap` workflow regenerates the collision map, the cuts and the
layer together and commits all three files in one commit. To regenerate the
layer locally, run
`ROUTING_COMPONENTS_REGEN=1 ./gradlew test --tests '*RoutingComponentsFreshnessTest' --rerun`.

The cuts only affect how much preparation and search the heuristic needs: cuts
that no longer match the collision map are dropped at generation, so they only
cost performance, never routes.
