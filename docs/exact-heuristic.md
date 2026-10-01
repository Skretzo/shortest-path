# Exact pathfinder heuristic

How the exact backend builds the A* heuristic for a target, and why the result
is admissible. Classes are in `src/main/java/shortestpath/pathfinder/exact/`.

## What the heuristic is

The heuristic is the exact distance to the target in a *relaxed* graph. That
graph keeps every transport, bank and separator edge but ignores collision
inside a routing component. For a tile `x` in routing component `c`, with
`banked` the search state's bank layer:

```text
h(x, banked) = min over sites s attached to c with a finite label:
                   label(s, banked) + chebyshev(x, s)
```

`label(s, banked)` is the relaxed shortest cost from site `s` to the target.
Walking costs 1 per step, diagonals included, so on one plane the real walking
distance is never below the Chebyshev distance. Any real route out of `c` has
to leave through a site: a transport origin, a separator crossing endpoint or
a bank. That makes `h` a lower bound. If `h` is `INF`, the relaxed graph proves
that no route exists, and the search prunes the state rather than treating it
as 0.

A tile whose component cannot reach the target through any site has every
bucket empty, so it is pruned without being expanded.

## Pipeline and lifetimes

| Stage | Lifetime | Class | Produces |
|---|---|---|---|
| Sites, components, sparse network | plugin session | `RoutingStaticBuilder` → `RoutingStatic` | sites, attachments, `SparseWalkingNetworkBuilder` CSR |
| Relaxed account graph | account fingerprint | `SiteGraph` | two-layer reverse CSR of explicit edges |
| Target overlay | target set | `TargetOverlay` | target nodes and attachment edges |
| Reverse search | target set | `ReverseLabels` | `label(node, banked)` and generator provenance |
| Seed and generator tables | target set | `PreparedHeuristic` | per-`(component, banked)` buckets |
| Lookup | search | `ExactForwardSearch.effectiveHeuristic` | `h` for each pushed state |

`ExactRoutingSession` caches the graph by account fingerprint, and
`PreparedTarget` holds the overlay, labels and heuristic, so an unchanged
account and target are reused across searches. Nothing before the lookup
depends on the start tile.

## 1. Static sites (`RoutingStatic`)

Sites are the union of all transport origins and destinations (local and
global), separator crossing endpoints, and structurally reachable banks. Each
site records **every** routing component it attaches to: its own component if it
is walkable, otherwise the components of its walkable neighbours (a blocked
fairy ring, for example, can attach to several). A site with no attachment
still exists as a node, which is how pure transport chains work.

## 2. Relaxed account graph (`SiteGraph`)

Each node has two states, `stateId(node, banked) = node * 2 + (banked ? 1 : 0)`.
The graph stores the *reverse* CSR of these explicit edges (`EdgeKind`):

| Edge | From → to | Cost | Starts generator |
|---|---|---|---|
| `LOCAL` | `(origin, b)` → `(dest, b)` for each available transport in layer `b` | duration + penalty | yes |
| `BANK_TRANSITION` | `(bank, false)` → `(bank, true)` | 0 | yes |
| `BANK_GLOBAL_ENTRY` | `(bank, false)` → `(hub, true)` | 0 | yes |
| `BANK_GLOBAL_DESTINATION` | `(hub, true)` → `(dest, true)` | cheapest banked global to `dest` | **no** |
| `SEPARATOR` | both directions, both layers | crossing cost (1) | yes |

The single abstract **hub** node replaces a banks × destinations bipartite
graph. It exists only when transports and bank paths are enabled, a reachable
bank exists, and at least one banked global destination exists.

Carried global teleports are deliberately **absent**. A global can be used from
anywhere, so using it later is never cheaper than using it at the start, and
the forward search seeds carried global destinations at the start instead.
Banked globals are only reachable through a bank, hence the hub. For
Wilderness starts, where globals are not usable at the start, see
[the restricted cap](#4-lookup-in-the-forward-search).

## 3. Target overlay (`TargetOverlay`)

A target that is already a site reuses that site's node. Any other target gets
a **synthetic** node after the graph's nodes, with:

- its routing components from `RoutingStatic.attachments` (all walkable
  neighbours' components if the tile is blocked);
- attachment edges to every site in those components, costing
  `chebyshev(target, site)`. A site shared by several target components is
  emitted once, under the first shared component.

Several targets are allowed (multi-target "find closest"). Every target is
seeded at 0, so the labels measure the distance to the nearest target.

## 4. Reverse search (`ReverseLabels`)

Dijkstra runs from every target state, in both layers at cost 0, over the
reverse explicit edges plus same-component walking. There are two
implementations, and their labels must be equal:

- **`computeClique`** is the reference oracle. On settling a site it relaxes
  every other site in each of its components at Chebyshev cost. This is
  quadratic in component size.
- **`computeSparse`** is the production path (`compute` uses it whenever a
  sparse network exists). It walks the static sparse network instead.

### The sparse walking network (`SparseWalkingNetworkBuilder`)

The network reproduces the component-wise Chebyshev clique exactly, with
O(n log n) edges:

- Rotate coordinates: `a = x + y`, `b = x - y`. Then `|Δa| + |Δb| = 2 ·
  chebyshev`, so all sparse weights are in **doubled** units.
- Per component, recursively split the points at the most balanced strict gap
  on axis `a`, or on `b` if all `a` are equal, at `m = floor((l + r) / 2)`.
  Each point gets a Steiner projection vertex on the line `axis = m`, joined
  by a spoke of cost `|coord − m|`. The projections are chained in order of
  the other coordinate.
- For `p` and `q` on opposite sides, the spoke, chain and spoke path costs
  exactly `|Δa| + |Δb|`. Same-side pairs are handled by the recursion, and
  coincident points are joined by 0-cost edges.
- A multi-component site is one shared vertex in every component's tree.

Steiner vertices get reverse states in both layers too. Explicit edge and
target attachment costs are doubled on entry (`ExactCosts.twice`), and each
label is halved once at the end (`ExactCosts.halve`). Halving is exact for
sites, because their doubled distances are always even.

### Generator provenance

`computeSparse` also records, for each state, the **generator** that its label
comes from:

- an edge that starts a generator (see the table in step 2) sets
  `origin = next` and `weight = newCost`;
- walking, Steiner and target attachment edges copy the predecessor's
  generator;
- each target seed is its own generator.

The hub → destination leg does not start a generator, so the abstract hub
never creates a geometric cone of its own.

## 5. Seed and generator tables (`PreparedHeuristic.prepare`)

There is one bucket per `(component, banked)`, with key `component * 2 +
banked`.

- **Raw seeds:** every spatial site, plus each synthetic target, with a finite
  label is added as `(tile, label)` to the bucket of **each** of its components.
- **Generators:** each seed is replaced by its generator `g` when
  `validOrigin` holds:
  - `g` is in the same layer;
  - `g` is a spatial site or a synthetic target;
  - `g` is attached to this component;
  - `label(s) = weight(g) + chebyshev(g, s)` holds exactly, i.e. the sparse
    path from `g` is a straight Chebyshev geodesic.

  Otherwise the seed stays its own generator. Each bucket is de-duplicated by
  generator state.

Generators are an optimisation, not an approximation. If `label(s) =
label(g) + cheb(g, s)`, the triangle inequality gives `label(g) + cheb(x, g) ≤
label(s) + cheb(x, s)` for every `x`. The cone of `s` is therefore dominated,
and dropping it leaves the minimum unchanged. Lookups scan the generator
bucket, falling back to the raw bucket if it is empty, and `estimateRaw` is
kept as the oracle for differential tests.

## 6. Lookup in the forward search

`ExactForwardSearch.heuristic` picks the lookup path:

- **Base tiles** (in the static search arrays) have exactly one component.
  They use `estimateBaseNode`.
- **Extras** are blocked transport endpoints, synthetic targets and a start
  outside the search arrays. They use `estimate`, which takes the minimum over
  all attached components.

Both paths also take the exact `label` when the tile is itself a site or a
target. Every bucket scan computes `min(label_i + chebyshev(x, tile_i))`.

`effectiveHeuristic` then adjusts the value for search state:

- **Bank dominance:** `bestBankCost` is the cheapest `g` seen at an unbanked
  reachable bank. For an unbanked state with `g > bestBankCost`, that state's
  own future bank/global opportunity is dominated. The search uses `max(h(x,
  false), h(x, true))`, ignoring the second term if it is `INF`. A popped state
  whose key has risen is re-queued (rekey).
- **Restricted cap:** in Wilderness-restricted queries, until all globals are
  activated, `h = min(h, globalBound[layer])`. Here `globalBound` is
  `min(globalCost + h(destination))` over that layer's globals, computed once
  per search by `globalBounds`. This restores admissibility, because the
  relaxed graph assumes globals are usable sooner than the restricted state
  allows.
- **Weight:** the priority is `g + round(weight · h)`. A weight of 1 is exact,
  and anything greater is an explicitly inexact mode.

## Invariants to preserve

1. The relaxed graph must contain every real edge, or a cheaper equivalent.
   Never drop an attachment or a transport.
2. `INF` means prune. Never turn a missing value into 0, except in the
   restricted fallback described under implementation notes.
3. Clique and sparse labels must be equal, and the
   generator scan must equal the raw scan (`PreparedHeuristic.estimateRaw`).
   `SparseManhattanTest` and `MultiTargetTest` check both.
4. Packed tiles are signed `int`s, and planes 2 and 3 are negative. Anything
   ordered by tile must use `Integer.compareUnsigned`: the routing data is
   sorted in unsigned order, and a signed comparison misplaces those planes.
5. Cost arithmetic goes through `ExactCosts`, which saturates at `INF` instead
   of overflowing.

## Implementation notes

- **Restricted INF:** when the capped value is still `INF`,
  `effectiveHeuristic` returns 0 (counted as `restrictedHeuristicZeroes`)
  rather than pruning the state. This is conservative: it never loses a route,
  but it can expand states that a stricter rule would prune.
- **Target attachments:** the reverse search relaxes only a synthetic target's
  own attachment edges, never site → target edges. Targets are seeded at 0, so
  those edges could never improve a label.
- **Generator lists:** `GeneratorList` de-duplicates with a linear scan and
  sorts with an insertion sort, so building a bucket is quadratic in its size.
  This is worth checking if heuristic preparation shows up in profiles.
