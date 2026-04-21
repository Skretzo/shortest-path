#!/usr/bin/env python3
"""Rebuild bank.tsv from the cache placement dump.

Strategy
--------
1. Read cache placements from ``build/bank-tiles/bank_tile_placements.tsv``
   (produced by :class:`BankTileDumperTest`).
2. Skip deposit boxes — not relevant to this plugin.
3. For each booth/chest placement derive its **stand tile** (the walkable tile
   the player actually stands on to interact with the object):
      a. Try the orientation-based direction:
         orient=0 → +y (north), 1 → +x (east), 2 → -y (south), 3 → -x (west).
      b. If that tile is not walkable, try the opposite direction (the booth
         model may be rotated 180° relative to the default).
      c. If still no luck, try the two perpendiculars.
      d. Finally fall back to the object tile itself (pathfinder tolerates
         unreachable-but-adjacent destination tiles for things like fairy
         ring centres).
   Walkability is checked against ``src/main/resources/collision-map.zip``.
4. Look up the bank name by nearest neighbour in the existing ``bank.tsv``
   (so historical labels + requirements are preserved).
5. Preserve bank.tsv entries whose names have zero cache matches (Sailing,
   Clan Hall, XTEA-locked regions, instanced Chambers of Xeric, etc.).
6. Drop plane>0 cache placements unless their mapped name already has a
   plane>0 row — otherwise they are upper-floor rendering artefacts.
7. Emit rows sorted alphabetically by Info, then by (plane, y, x).

Run with::

    python3 scripts/rebuild_bank_tsv.py
"""

from __future__ import annotations

import csv
import io
import sys
import zipfile
from pathlib import Path
from typing import Optional

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
BANK_TSV = REPO / "src/main/resources/destinations/game_features/bank.tsv"
PLACEMENTS_TSV = REPO / "build/bank-tiles/bank_tile_placements.tsv"
COLLISION_ZIP = REPO / "src/main/resources/collision-map.zip"

# Name-assignment radius (Chebyshev tiles).
RADIUS = 100
# Plane-0 placements at/above this y live inside an instance (raids, POH,
# quest rooms) and are skipped unless they mapped to a known bank name.
INSTANCE_Y_THRESHOLD = 4800

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


# --- stand-tile derivation -------------------------------------------------

# orient → (dx, dy): direction the player stands relative to the object tile.
ORIENT_TO_DIR = {
    0: (0, 1),   # north
    1: (1, 0),   # east
    2: (0, -1),  # south
    3: (-1, 0),  # west
}
OPPOSITE = {0: 2, 1: 3, 2: 0, 3: 1}
PERPENDICULAR = {0: (1, 3), 1: (0, 2), 2: (1, 3), 3: (0, 2)}


def stand_tile(x: int, y: int, z: int, orient: int, cmap: CollisionMap) -> tuple[int, int, int]:
    candidates: list[tuple[int, int]] = []
    if orient in ORIENT_TO_DIR:
        candidates.append(ORIENT_TO_DIR[orient])
        candidates.append(ORIENT_TO_DIR[OPPOSITE[orient]])
        for p in PERPENDICULAR[orient]:
            candidates.append(ORIENT_TO_DIR[p])
    else:
        candidates = list(ORIENT_TO_DIR.values())

    for dx, dy in candidates:
        sx, sy = x + dx, y + dy
        if cmap.walkable(sx, sy, z):
            return sx, sy, z
    return x, y, z


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
            out.append({
                "id": int(row["id"]),
                "name": row["name"],
                "x": int(row["x"]),
                "y": int(row["y"]),
                "plane": int(row["plane"]),
                "orientation": int(row["orientation"]),
                "type": int(row["type"]),
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

    new_rows: dict[tuple[int, int, int, str], None] = {}
    matched_names: set[str] = set()
    unmatched_placements: list[dict] = []

    for pl in placements:
        name, d = nearest_name(pl["x"], pl["y"], pl["plane"], bank_rows)
        if name is None or d > RADIUS:
            if pl["plane"] == 0 and pl["y"] >= INSTANCE_Y_THRESHOLD:
                unmatched_placements.append({**pl, "near": f"{name}@{d}" if name else "<none>"})
                continue
            unmatched_placements.append({**pl, "near": f"{name}@{d}" if name else "<none>"})
            continue

        if pl["plane"] > 0:
            has_same_plane = any(bp == pl["plane"] and bn == name for _x2, _y2, bp, bn, *_ in bank_rows)
            if not has_same_plane:
                unmatched_placements.append({**pl, "near": f"{name}@{d}"})
                continue

        sx, sy, sz = stand_tile(pl["x"], pl["y"], pl["plane"], pl["orientation"], cmap)
        new_rows[(sx, sy, sz, name)] = None
        matched_names.add(name)

    # Preserve original rows whose name has zero cache matches.
    preserved_names: set[str] = set()
    for x, y, p, n, *_ in bank_rows:
        if not n or n in matched_names:
            continue
        new_rows[(x, y, p, n)] = None
        preserved_names.add(n)

    # Sort alphabetically by name, then plane, y, x.
    sorted_keys = sorted(new_rows.keys(),
                         key=lambda k: (k[3].lower(), k[2], k[1], k[0]))

    out_lines = [
        "# Destination\tInfo\tSkills\tQuests\tVarbits\tVarPlayers",
        "# Sailing island chests that require construction (exact Varbits TBD): Onyx Crest, Charred Island, Sunbleak, Buccaneers Haven, Deepfin Mine (E/W when build-gated)\t\t\t\t\t",
    ]
    for (x, y, p, name) in sorted_keys:
        s, q, vb, vp = name_reqs.get(name, ("", "", "", ""))
        out_lines.append(f"{x} {y} {p}\t{name}\t{s}\t{q}\t{vb}\t{vp}")
    BANK_TSV.write_text("\n".join(out_lines) + "\n")

    # Report.
    print(f"Wrote {len(sorted_keys)} rows to {BANK_TSV}")
    cache_rows = sum(1 for k in new_rows if k[3] in matched_names)
    preserved_rows = sum(1 for k in new_rows if k[3] in preserved_names)
    print(f"  Cache-derived stand tiles: {cache_rows}")
    print(f"  Preserved (no cache match): {preserved_rows} across {len(preserved_names)} names")
    print(f"  Unmatched cache placements: {len(unmatched_placements)}")
    print()
    if preserved_names:
        print("Preserved bank names:")
        for n in sorted(preserved_names):
            print(f"  - {n}")
        print()
    # Classify unmatched placements by likely cause.
    overworld = []  # plane 0, y<4800: real world, probably new/hidden bank
    instanced = []  # plane 0, y>=4800: raids/minigame instances
    upper = []      # plane>0: upper floor of a known bank (only flagged if name mismatch)
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
