#!/usr/bin/env python3
"""Rebuild bank.tsv from the cache placement dump.

Strategy
--------
The cache dump alone is NOT sufficient to produce good stand tiles: the
``orient`` field does not reliably identify the customer side of a booth
because different ``Bank booth`` object IDs model their counters facing
different directions. So ``orient=2`` maps to the banker side in some
banks (e.g. Fishing Guild) and the customer side in others (e.g.
Falador east). A cache-only rebuild therefore produces wrong tiles for
a large fraction of banks.

Instead, the rebuild is **additive**: every curated tile in the current
``bank.tsv`` is preserved verbatim, and the cache is used solely to
discover NEW bank objects that have no curated tile within
``SNAP_RADIUS`` tiles on the same plane. The customer-side stand tile
for each new object is derived by a BFS reachability heuristic: of the
object's walkable 4-neighbours, the one that opens up into the largest
connected area (with the banker strip behind the counter capped by
``BFS_LIMIT``) is picked, ties broken by distance to the centroid of
all of that bank's placements.

Algorithm
~~~~~~~~~
1. Read cache placements from ``build/bank-tiles/bank_tile_placements.tsv``
   (produced by :class:`BankTileDumperTest`).
2. Skip deposit boxes and excluded regions.
3. Load the existing ``bank.tsv`` and keep every tile (the source of
   truth for stand-tile positions).
4. Assign every placement to its nearest bank name on the same plane
   (within ``RADIUS``); collect the full set of booth/table/chest
   footprint tiles per bank so the BFS treats same-bank counters as
   obstacles rather than walking through them.
5. For each assigned placement:

   a. Derive the customer-side stand tile via BFS (see ``_orient_stand_tile``).
   b. If an existing tile for that name is within ``SNAP_RADIUS``
      Chebyshev tiles on the same plane, the object is already covered
      and nothing is added.
   c. Otherwise the derived stand tile is emitted as a new row,
      inheriting the bank's requirements (skills/quests/varbits/...).
   d. Plane>0 cache placements are only considered if the mapped name
      already has a plane>0 row — otherwise they are upper-floor
      rendering artefacts.

6. Emit rows sorted alphabetically by Info, then by (plane, y, x).

Run with::

    python3 scripts/rebuild_bank_tsv.py
"""

from __future__ import annotations

import csv
import sys
import zipfile
from collections import deque
from pathlib import Path
from typing import Optional

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
BANK_TSV = REPO / "src/main/resources/destinations/game_features/bank.tsv"
PLACEMENTS_TSV = REPO / "build/bank-tiles/bank_tile_placements.tsv"
COLLISION_ZIP = REPO / "src/main/resources/collision-map.zip"

# Name-assignment radius (Chebyshev tiles): max distance a cache
# placement may be from a curated tile of the same bank before it's
# considered "unknown" and skipped.
RADIUS = 100
# Max Chebyshev distance from a cache object's derived customer-side
# stand tile to an existing bank.tsv tile before we consider that object
# already represented. The comparison uses the *stand tile* (not the raw
# cache position) so we only skip adding a new row when a curated tile
# is essentially the same spot; distinct banking objects whose stand
# tiles are a few tiles apart (e.g. Great Conch's 2 booths + 2 chests,
# or Falador's long counter) still each get their own bank.tsv entry.
SNAP_RADIUS = 1
# Plane-0 placements at/above this y live inside an instance (raids,
# POH, quest rooms) and are reported separately from genuine overworld
# matches.
INSTANCE_Y_THRESHOLD = 4800

# Regions that contain bank objects that must never appear in bank.tsv.
# Tutorial Island (region 12336) is permanently inaccessible after the
# tutorial. The Node (region 12335) is a Group Ironman onboarding area
# inaccessible outside of GIM mode, so its Bank booth at (3098, 3027) is
# also excluded.
EXCLUDED_REGIONS: set[int] = {12335, 12336}

# Banks that have NO bank booth / bank chest object in the cache because
# they are serviced by Banker NPCs only. NPC spawns are not in the client
# cache, so these entries live exclusively in bank.tsv and are preserved
# verbatim by the rebuild.
#
# Known NPC-only bank locations (for reference; don't need special logic):
#   - Shilo Village      (requires Shilo Village quest)
#   - Ape Atoll          (requires Monkey Madness II)
#   - Tree Gnome Village (bankers in the cave — cached as 'Banker' NPCs)
#   - Nardah
#   - Port Phasmatys     (bankers on the docks)
#   - Lunar Isle         (bankers in the bank tent)
#   - Pest Control lobby / Void Knights' Outpost
#   - Canifis (chairs)
#   - Jatizso
#   - Etceteria / Miscellania
#   - Tree Gnome Stronghold
#   - Warriors' Guild (upstairs NPC)
# The above all show up in the preserved-names list after rebuild.

REGION_SIZE = 64

# Order matches CollisionMap.java flags.
FLAG_N = 0
FLAG_E = 1


# --- collision map ---------------------------------------------------------

class CollisionMap:
    """Minimal Python port of FlagMap/SplitFlagMap needed for walkability."""

    def __init__(self, zip_path: Path):
        self.regions: dict[tuple[int, int], tuple[bytes, int]] = {}
        with zipfile.ZipFile(zip_path) as z:
            for name in z.namelist():
                rx, ry = (int(n) for n in name.split("_"))
                data = z.read(name)
                scale = REGION_SIZE * REGION_SIZE * 2
                plane_count = (len(data) * 8 + scale - 1) // scale
                self.regions[(rx, ry)] = (data, plane_count)

    def _bit(self, data: bytes, index: int) -> bool:
        byte = data[index >> 3]
        return bool((byte >> (index & 7)) & 1)

    def flag(self, x: int, y: int, z: int, flag: int) -> bool:
        rx, ry = x // REGION_SIZE, y // REGION_SIZE
        region = self.regions.get((rx, ry))
        if region is None:
            return False
        data, plane_count = region
        if z < 0 or z >= plane_count:
            return False
        lx = x - rx * REGION_SIZE
        ly = y - ry * REGION_SIZE
        idx = (z * REGION_SIZE * REGION_SIZE + ly * REGION_SIZE + lx) * 2 + flag
        if idx < 0 or idx >= len(data) * 8:
            return False
        return self._bit(data, idx)

    def n(self, x: int, y: int, z: int) -> bool:
        return self.flag(x, y, z, FLAG_N)

    def e(self, x: int, y: int, z: int) -> bool:
        return self.flag(x, y, z, FLAG_E)

    def s(self, x: int, y: int, z: int) -> bool:
        return self.n(x, y - 1, z)

    def w(self, x: int, y: int, z: int) -> bool:
        return self.e(x - 1, y, z)

    def walkable(self, x: int, y: int, z: int) -> bool:
        """A tile is considered walkable if there is any outgoing/incoming
        movement flag touching it (i.e. the collision map knows about it)."""
        return self.n(x, y, z) or self.e(x, y, z) or self.s(x, y, z) or self.w(x, y, z)

    def is_blocked(self, x: int, y: int, z: int) -> bool:
        return not self.walkable(x, y, z)


# --- flood-fill customer-side discriminator --------------------------------

_BFS_DIRS = (
    (0, 1, "n"),
    (0, -1, "s"),
    (1, 0, "e"),
    (-1, 0, "w"),
)
# Max BFS size when choosing a customer-side stand tile. A narrow
# banker strip behind a counter typically caps out well below this,
# while the customer side of a bank opens up to the main world and
# hits the limit quickly — that's what distinguishes the two.
BFS_LIMIT = 600


def _reachable_count(cmap: CollisionMap, start: tuple[int, int, int],
                     blocked: set[tuple[int, int, int]]) -> int:
    if cmap.is_blocked(*start) or start in blocked:
        return 0
    seen = {start}
    queue = deque([start])
    while queue and len(seen) < BFS_LIMIT:
        x, y, z = queue.popleft()
        for dx, dy, name in _BFS_DIRS:
            if not getattr(cmap, name)(x, y, z):
                continue
            nxt = (x + dx, y + dy, z)
            if nxt in seen or nxt in blocked:
                continue
            seen.add(nxt)
            queue.append(nxt)
    return len(seen)


# --- stand-tile derivation -------------------------------------------------

# orient → (dx, dy): direction the player stands relative to the object
# tile. Only used as a last-resort fallback when no walkable 4-neighbour
# exists (the BFS below is the real discriminator).
_ORIENT_TO_DIR = {
    0: (0, 1),   # north
    1: (1, 0),   # east
    2: (0, -1),  # south
    3: (-1, 0),  # west
}


def _object_footprint(x: int, y: int, size_x: int, size_y: int,
                      orient: int) -> list[tuple[int, int]]:
    """Tiles occupied by a cache placement after applying orientation.

    Cache ``(x, y)`` is the NW corner. Objects with ``sizeX`` or
    ``sizeY`` > 1 (notably Varlamore's 2x1 "Bank table") span additional
    tiles, and their orientation rotates the footprint 90° for odd
    ``orient`` values.
    """
    sx, sy = size_x, size_y
    if orient in (1, 3):
        sx, sy = sy, sx
    return [(x + dx, y + dy) for dx in range(sx) for dy in range(sy)]


def _orient_stand_tile(x: int, y: int, z: int, orient: int,
                       cmap: CollisionMap,
                       size_x: int = 1, size_y: int = 1,
                       all_booth_tiles: Optional[set] = None,
                       centroid: Optional[tuple[float, float]] = None
                       ) -> tuple[int, int, int]:
    """Pick a walkable customer-side stand tile for a cache placement.

    The ``orient`` field in the cache is unreliable for identifying the
    customer vs banker side (different booth models have different
    baseline orientations), so instead of trusting it we flood-fill
    from every walkable 4-neighbour of the object's footprint and
    prefer the candidate that reaches the largest connected area: the
    banker strip behind the counter is a small dead end (capped by
    ``BFS_LIMIT``), while the customer side opens onto the main
    walkable floor. Ties are broken by Manhattan distance to the
    bank's centroid, which tends to pick the "inside the bank" side
    for multi-booth banks.

    ``all_booth_tiles`` is the set of footprint tiles for every banking
    object at this bank on this plane; passing them lets the BFS treat
    the entire counter as an obstacle rather than just the current
    object, which matters for long counters like Fortis east where
    otherwise the flood walks through a neighbouring table and flips
    the chosen stand side.
    """
    footprint = set(_object_footprint(x, y, size_x, size_y, orient))
    adj: set[tuple[int, int]] = set()
    for fx, fy in footprint:
        for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            nb = (fx + dx, fy + dy)
            if nb not in footprint:
                adj.add(nb)

    if centroid is not None:
        cx, cy = centroid
    else:
        cx = sum(fx for fx, _ in footprint) / len(footprint)
        cy = sum(fy for _, fy in footprint) / len(footprint)
    blocked: set[tuple[int, int, int]] = {(fx, fy, z) for fx, fy in footprint}
    if all_booth_tiles:
        blocked |= {t for t in all_booth_tiles if t[2] == z}

    candidates = []
    for sx, sy in adj:
        if cmap.is_blocked(sx, sy, z):
            continue
        count = _reachable_count(cmap, (sx, sy, z),
                                 blocked - {(sx, sy, z)})
        dist = abs(sx - cx) + abs(sy - cy)
        candidates.append((-count, dist, sx, sy))

    if candidates:
        candidates.sort()
        _, _, sx, sy = candidates[0]
        return sx, sy, z

    # No walkable neighbour found (unusual — object walled in on all
    # sides on its plane). Fall back to the orient-derived hint so we
    # still emit something useful.
    if orient in _ORIENT_TO_DIR:
        dx, dy = _ORIENT_TO_DIR[orient]
        return x + dx, y + dy, z
    return x, y, z


def has_existing_tile_within(
    x: int, y: int, z: int, name: str,
    existing_tiles_by_name: dict[str, list[tuple[int, int, int]]],
) -> bool:
    """True if a curated tile for ``name`` is within ``SNAP_RADIUS`` on the same plane."""
    for ex, ey, ep in existing_tiles_by_name.get(name, []):
        if ep != z:
            continue
        if max(abs(ex - x), abs(ey - y)) <= SNAP_RADIUS:
            return True
    return False


# --- bank.tsv handling -----------------------------------------------------

def load_bank_tsv():
    rows = []
    for raw in BANK_TSV.read_text().splitlines():
        if raw.startswith("#") or not raw.strip():
            continue
        parts = raw.split("\t")
        while len(parts) < 6:
            parts.append("")
        dest = parts[0].split()
        if len(dest) != 3:
            continue
        rows.append((int(dest[0]), int(dest[1]), int(dest[2]),
                     parts[1].strip(), parts[2].strip(),
                     parts[3].strip(), parts[4].strip(), parts[5].strip()))
    return rows


def load_placements():
    out = []
    with PLACEMENTS_TSV.open() as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if "deposit" in row["name"].lower():
                continue
            if int(row["regionId"]) in EXCLUDED_REGIONS:
                continue
            out.append({
                "id": int(row["id"]),
                "name": row["name"],
                "x": int(row["x"]),
                "y": int(row["y"]),
                "plane": int(row["plane"]),
                "orientation": int(row["orientation"]),
                "type": int(row["type"]),
                "sizeX": int(row.get("sizeX", "1") or "1"),
                "sizeY": int(row.get("sizeY", "1") or "1"),
            })
    return out


def nearest_name(x: int, y: int, plane: int, bank_rows) -> tuple[Optional[str], int]:
    best: Optional[str] = None
    best_d = 10 ** 9
    for bx, by, bp, bn, *_ in bank_rows:
        if bp != plane or not bn:
            continue
        d = max(abs(bx - x), abs(by - y))
        if d < best_d:
            best_d = d
            best = bn
    return best, best_d


# --- main ------------------------------------------------------------------

def main() -> int:
    bank_rows = load_bank_tsv()
    placements = load_placements()
    cmap = CollisionMap(COLLISION_ZIP)

    # Per-name requirement (first non-empty wins across rows with that name).
    name_reqs: dict[str, tuple[str, str, str, str]] = {}
    for _x, _y, _p, n, s, q, vb, vp in bank_rows:
        if not n:
            continue
        if n not in name_reqs:
            name_reqs[n] = (s, q, vb, vp)
        else:
            ps, pq, pvb, pvp = name_reqs[n]
            name_reqs[n] = (ps or s, pq or q, pvb or vb, pvp or vp)

    # Start by keeping every existing tile — the current bank.tsv is the
    # source of truth for stand-tile coordinates.
    new_rows: dict[tuple[int, int, int, str], None] = {}
    existing_tiles_by_name: dict[str, list[tuple[int, int, int]]] = {}
    for bx, by, bp, bn, *_ in bank_rows:
        if not bn:
            continue
        new_rows[(bx, by, bp, bn)] = None
        existing_tiles_by_name.setdefault(bn, []).append((bx, by, bp))

    unmatched_placements: list[dict] = []
    covered_count = 0
    added_new: list[tuple[int, int, int, str]] = []

    # First pass: assign each placement to its nearest bank name and
    # compute the full set of booth/table/chest footprint tiles for
    # every bank. Stand-tile BFS needs ALL same-bank booths as
    # obstacles (not just the current one), otherwise for banks with
    # multiple aligned counters the flood walks through a neighbouring
    # booth and picks a non-customer-side tile. See Fortis east bank,
    # whose two Bank tables would yield mismatched stand sides if each
    # were considered in isolation.
    assigned: list[tuple[dict, str]] = []
    booth_tiles_by_name: dict[str, set[tuple[int, int, int]]] = {}
    placements_by_name: dict[str, list[dict]] = {}
    for pl in placements:
        name, d = nearest_name(pl["x"], pl["y"], pl["plane"], bank_rows)
        if name is None or d > RADIUS:
            unmatched_placements.append({**pl, "near": f"{name}@{d}" if name else "<none>"})
            continue
        if pl["plane"] > 0:
            has_same_plane = any(bp == pl["plane"] and bn == name
                                 for _x2, _y2, bp, bn, *_ in bank_rows)
            if not has_same_plane:
                unmatched_placements.append({**pl, "near": f"{name}@{d}"})
                continue
        assigned.append((pl, name))
        placements_by_name.setdefault(name, []).append(pl)
        fp = booth_tiles_by_name.setdefault(name, set())
        for fx, fy in _object_footprint(pl["x"], pl["y"],
                                        pl.get("sizeX", 1),
                                        pl.get("sizeY", 1),
                                        pl["orientation"]):
            fp.add((fx, fy, pl["plane"]))

    # Centroid of all same-named placements, used as the BFS tiebreaker.
    # Using a per-bank centroid rather than a per-object one keeps the
    # customer-side choice consistent across multiple booths of the
    # same bank.
    centroids_by_name: dict[str, tuple[float, float]] = {}
    for name, pls in placements_by_name.items():
        cx = sum(p["x"] for p in pls) / len(pls)
        cy = sum(p["y"] for p in pls) / len(pls)
        centroids_by_name[name] = (cx, cy)

    for pl, name in assigned:
        # Derive the customer-side stand tile up front with every
        # same-named booth as an obstacle, then ask whether bank.tsv
        # already has it (or an adjacent curated tile). Matching on
        # the derived tile rather than the raw cache position is
        # important for banks like Great Conch, where two Bank chests
        # sit 2-3 tiles from the Bank booth stand tiles — close enough
        # to look "covered" against the booth position, but their
        # customer-side stand tiles are elsewhere and need separate
        # bank.tsv entries.
        sx, sy, sz = _orient_stand_tile(
            pl["x"], pl["y"], pl["plane"], pl["orientation"], cmap,
            pl.get("sizeX", 1), pl.get("sizeY", 1),
            booth_tiles_by_name.get(name),
            centroids_by_name.get(name))

        if has_existing_tile_within(sx, sy, sz, name, existing_tiles_by_name):
            covered_count += 1
            continue

        key = (sx, sy, sz, name)
        if key not in new_rows:
            new_rows[key] = None
            added_new.append(key)
            existing_tiles_by_name.setdefault(name, []).append((sx, sy, sz))

    # Sort alphabetically by name, then plane, y, x.
    sorted_keys = sorted(new_rows.keys(),
                         key=lambda k: (k[3].lower(), k[2], k[1], k[0]))

    out_lines = [
        "# Destination\tInfo\tSkills\tQuests\tVarbits\tVarPlayers",
        "# Sailing island chests that require construction (exact Varbits TBD):"
        " Onyx Crest, Charred Island, Sunbleak, Buccaneers Haven, Deepfin Mine"
        " (E/W when build-gated)\t\t\t\t\t",
    ]
    for (x, y, p, name) in sorted_keys:
        s, q, vb, vp = name_reqs.get(name, ("", "", "", ""))
        out_lines.append(f"{x} {y} {p}\t{name}\t{s}\t{q}\t{vb}\t{vp}")
    BANK_TSV.write_text("\n".join(out_lines) + "\n")

    print(f"Wrote {len(sorted_keys)} rows to {BANK_TSV}")
    print(f"  Curated tiles preserved: {len(bank_rows)}")
    print(f"  Cache placements covered by existing tile: {covered_count}")
    print(f"  New tiles added from cache (BFS-derived): {len(added_new)}")
    print(f"  Unmatched cache placements: {len(unmatched_placements)}")
    print()
    if added_new:
        print("Newly added tiles:")
        for x, y, p, n in added_new:
            print(f"  + ({x}, {y}, {p})  {n}")
        print()

    overworld: list[dict] = []  # plane 0, y<4800: real world, probably a new/hidden bank
    instanced: list[dict] = []  # plane 0, y>=4800: raids/minigame instances
    upper: list[dict] = []      # plane>0: upper floor of a known bank
    for u in unmatched_placements:
        if u["plane"] > 0:
            upper.append(u)
        elif u["y"] >= INSTANCE_Y_THRESHOLD:
            instanced.append(u)
        else:
            overworld.append(u)
    if overworld:
        print(f"Unmatched OVERWORLD placements (manual review, {len(overworld)}):")
        for u in overworld:
            print(f"  id={u['id']:<6} {u['name']!r:<22} ({u['x']},{u['y']},0)  near={u['near']}")
        print()
    if instanced:
        print(f"Unmatched INSTANCED placements (minigame/raid instances, {len(instanced)}):")
        for u in instanced:
            print(f"  id={u['id']:<6} {u['name']!r:<22} ({u['x']},{u['y']},0)  near={u['near']}")
        print()
    if upper:
        print(f"Unmatched UPPER-FLOOR placements ({len(upper)}):")
        for u in upper:
            print(f"  id={u['id']:<6} {u['name']!r:<22} ({u['x']},{u['y']},{u['plane']})  near={u['near']}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
