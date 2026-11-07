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

- **Static routing data** (`RoutingStatic`) is derived once per plugin session,
  on the pathfinding thread, the first time an A* search runs
  (`AStarRoutingStaticProvider`, `RoutingStaticBuilder`). It comes from
  `collision-map.zip`, the transport and bank resources, and
  `routing-cuts.bin`.
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

## Routing cuts

Large walking areas are split into routing components along separator cut
edges stored in `src/main/resources/routing-cuts.bin`. The cuts only affect how
much preparation and search the heuristic needs: cuts that no longer match the
collision map are ignored, so routes stay exact with any cut file, including an
empty one.

The file is generated with KaHIP by the `routingCuts` task in
[shortest-path-tooling](https://github.com/osrs-pathfinding/shortest-path-tooling).
The `ExtractCollisionMap` workflow regenerates it whenever it dumps a new
collision map and commits both files together.

## Sailing

A route may board one of the player's own boats at a mooring, sail it, and dock
at another mooring or end at a target at sea; or it may start aboard. All of it
happens in the one A* search, beside walking. Searches without boats are
unchanged.

### The model

- **The sea** is the water a boat can reach from a berth, read from the game's
  water textures. Each sea tile is open water or in a restricted area (Tempor
  Tantrum, the icy seas, crystal-flecked, fetid or kelp water), which a boat
  enters only with the right part fitted.
- **A boat** has a kind (raft, skiff or sloop), which sets its hull and the water
  it fits through; a speed from its hull; and the restricted areas its parts
  allow. The player's boats stay where they are while a route is planned.
- **Boarding** happens at a mooring's land tile, with a boat moored there or one a
  shipwright brings for the game's fee (up to the "Boat Recovery Limit"
  setting). **Docking** lands on a mooring's land tile, from anywhere in a box
  round its berth, and is the only way off a boat. Boarding and docking are
  transports: they need the mooring's requirements, and land tiles are walkable.
- **Sailing** moves hold a heading at the boat's speed, or at a tile a tick,
  since a boat can always slow down. The hull must clear every tile it sweeps.
  The boat turns at most one heading a tick while it moves.
- **A target at sea** is reached when the hull passes over it.

Speeds between full and slow, boats moving during a route, and positions
within a tile are left out. The docking box and the boarding and docking times
are estimates.

### Planning on a sea graph

Sailing every tile would be far too slow, so each boat kind's sea is a graph
(`SeaGraph`) of a few thousand nodes. The nodes are the corners of a smoothed
coastline, set a little out into the water, and each mooring's launch point.
The edges are straight runs between nodes that see each other. Every run was
sailed on the tiles when the graph was built, so it only exists if a boat can
really sail it, and it costs what sailing it took. The graph is generated by
shortest-path-tooling's `sailingData` task.

The search takes the sea as a side graph (`SideGraph`): states beside the
walking ones that a route enters from tiles and leaves onto tiles, or ends in.
`SeaSearch` is the sea for one query, with a copy of the graph for each
different boat the player has.

The search's estimate sees across the sea through estimate edges
(`EstimateEdges`). For each pair of moorings the account can use, an edge gives
a lower bound on boarding, sailing straight across at top speed, and docking.
The estimate uses these edges like transports, but the search never takes
them: it sails the graph itself. A target at sea is reached from ashore by
seeding the estimate at the moorings a boat could set out from.

### Drawing a route

The search's route at sea is a few graph nodes. To draw it, each stretch at sea
is sailed again on the tiles near that route (`HeadingSearch`): the fewest
ticks, then the fewest turns, so the boat holds a course and turns rarely, as a
player at the helm does.

### Where planning and drawing differ

A route's cost comes from the graph and its line from the tiles, so the two can
differ slightly. Routes bend only at graph nodes, each run's cost was measured
once per boat kind, and planning doesn't price turns. On the canonical sailing
routes, graph costs match the tile model at the median and are within 2% for
nine routes in ten.

### Data

Sailing reads the `sailing/` layer of the collision map: each sea tile's area
and, per boat kind, its body of water, plus each kind's sea graph. The moorings
are in `sailing/moorings.tsv`. Tooling's `sailingData` task makes the layer
from the collision map and the moorings; the `ExtractCollisionMap` workflow
reruns it for each new map, and `SailingDataTest` fails when the layer is out
of date.
