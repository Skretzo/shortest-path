#!/usr/bin/env python3
"""Reconcile bank.tsv rows against exhaustive landscape placements from the cache.

The companion tool `bank-tiles-update/BankTileDumper.java` produces a TSV of
every bank-related object placement in the OSRS cache. This script:

    1. Groups those placements by label (the plane-labelled region of the
       existing bank.tsv, roughly one "named bank").
    2. For each row in bank.tsv, finds the nearest placement on the same plane
       (booths, chests, deposit boxes, etc.).
    3. For booths, projects the coordinate onto the standable tile adjacent
       to the booth (using the booth's `orientation`) so the row reflects
       where the player stands, which is what the pathfinder needs. Bank
       chests / deposit boxes are themselves standable, so no projection.
    4. Emits a human-readable diff. With --write, rewrites bank.tsv so each
       row's coordinate is snapped to the nearest placement.

Pass --no-stand-tile to disable projection and snap to the raw object tile.

No rows are added or removed; this only updates coordinates that can be
matched within --radius tiles of an existing entry.

Usage:

    python3 scripts/reconcile_bank_tiles.py \\
        --placements bank_tile_placements.tsv \\
        --bank-tsv   src/main/resources/destinations/game_features/bank.tsv \\
        [--radius 20] [--write]
"""

from __future__ import annotations

import argparse
import csv
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional

# 4-way adjacency offsets. Works for everything: bank chests / deposit boxes
# (clickable from any adjacent tile) and bank booths (orientation in the cache
# is a model-rotation, and different booth object ids have different default
# facings, so emitting all 4 neighbours and letting the matcher pick the one
# closest to the existing bank.tsv row is more robust than trying to decode
# "which side is the counter".)
ADJACENT_OFFSETS = [(-1, 0), (1, 0), (0, -1), (0, 1)]


@dataclass(frozen=True)
class Placement:
    obj_id: int
    name: str
    x: int
    y: int
    plane: int
    orientation: int

    def candidate_tiles(self, project_stand: bool = True) -> list[tuple[int, int]]:
        """Possible stand tiles for this placement.

        With projection enabled (the default), returns all 4 tiles adjacent to
        the booth/chest footprint; the matcher picks whichever is closest to
        the existing bank.tsv row. With --no-stand-tile, returns just the raw
        object tile.
        """
        if not project_stand:
            return [(self.x, self.y)]
        return [(self.x + dx, self.y + dy) for dx, dy in ADJACENT_OFFSETS]


@dataclass
class BankRow:
    raw: list[str]  # full tab-split row, preserved for faithful rewrite
    coord: tuple[int, int, int]
    label: str
    line_no: int

    @property
    def coord_text(self) -> str:
        return f"{self.coord[0]} {self.coord[1]} {self.coord[2]}"


def load_placements(path: Path) -> list[Placement]:
    out: list[Placement] = []
    with path.open(newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            out.append(
                Placement(
                    obj_id=int(row["id"]),
                    name=row["name"],
                    x=int(row["x"]),
                    y=int(row["y"]),
                    plane=int(row["plane"]),
                    orientation=int(row["orientation"]),
                )
            )
    return out


def load_bank_rows(path: Path) -> tuple[list[str], list[BankRow]]:
    """Return (preamble_lines, data_rows). Preamble keeps comments/header."""
    preamble: list[str] = []
    rows: list[BankRow] = []
    with path.open() as f:
        for line_no, line in enumerate(f, start=1):
            stripped = line.rstrip("\n")
            if not stripped.strip() or stripped.startswith("#"):
                preamble.append(stripped)
                continue
            parts = stripped.split("\t")
            coord_str = parts[0].strip()
            try:
                x_s, y_s, p_s = coord_str.split()
                coord = (int(x_s), int(y_s), int(p_s))
            except ValueError:
                # Not a coordinate row; preserve as-is.
                preamble.append(stripped)
                continue
            label = parts[1].strip() if len(parts) > 1 else ""
            rows.append(BankRow(raw=parts, coord=coord, label=label, line_no=line_no))
    return preamble, rows


def nearest_placement(
    row: BankRow,
    placements: Iterable[Placement],
    radius: int,
    project_stand: bool,
) -> Optional[tuple[Placement, tuple[int, int, int], int]]:
    """Return (placement, snapped_coord, chebyshev_distance) or None."""
    best: Optional[tuple[Placement, tuple[int, int, int], int]] = None
    rx, ry, rp = row.coord
    for p in placements:
        if p.plane != rp:
            continue
        for sx, sy in p.candidate_tiles(project_stand):
            d = max(abs(sx - rx), abs(sy - ry))
            if d > radius:
                continue
            if best is None or d < best[2]:
                best = (p, (sx, sy, rp), d)
    return best


def emit_diff(
    preamble: list[str],
    rows: list[BankRow],
    placements: list[Placement],
    radius: int,
    project_stand: bool,
) -> tuple[list[str], int, int]:
    diffs: list[str] = []
    updated = 0
    unmatched = 0
    for row in rows:
        match = nearest_placement(
            row, placements, radius=radius, project_stand=project_stand
        )
        if match is None:
            diffs.append(
                f"  ??   line {row.line_no}: {row.label!r:40s} "
                f"{row.coord_text}  (no placement within {radius} tiles)"
            )
            unmatched += 1
            continue
        placement, snapped, dist = match
        if snapped == row.coord:
            continue
        updated += 1
        diffs.append(
            f"  ->   line {row.line_no}: {row.label!r:40s} "
            f"{row.coord_text}  ->  {snapped[0]} {snapped[1]} {snapped[2]}  "
            f"(Δ={dist}, {placement.name} id={placement.obj_id})"
        )
    return diffs, updated, unmatched


def apply_diff(
    preamble: list[str],
    rows: list[BankRow],
    placements: list[Placement],
    radius: int,
    project_stand: bool,
    out_path: Path,
) -> None:
    # Read original line order back in, but rewrite matched rows.
    lines: list[str] = []
    by_line: dict[int, BankRow] = {r.line_no: r for r in rows}
    with out_path.open() as f:
        for line_no, line in enumerate(f, start=1):
            stripped = line.rstrip("\n")
            if line_no in by_line:
                row = by_line[line_no]
                match = nearest_placement(
                    row, placements, radius=radius, project_stand=project_stand
                )
                if match is not None:
                    _, snapped, _ = match
                    parts = list(row.raw)
                    parts[0] = f"{snapped[0]} {snapped[1]} {snapped[2]}"
                    stripped = "\t".join(parts)
            lines.append(stripped)
    out_path.write_text("\n".join(lines) + "\n")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--placements",
        type=Path,
        required=True,
        help="Path to bank_tile_placements.tsv produced by BankTileDumper.",
    )
    parser.add_argument(
        "--bank-tsv",
        type=Path,
        required=True,
        help="Path to src/main/resources/destinations/game_features/bank.tsv",
    )
    parser.add_argument(
        "--radius",
        type=int,
        default=20,
        help="Max Chebyshev distance (tiles) to consider a placement a match.",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="Rewrite bank.tsv in place with snapped coordinates.",
    )
    parser.add_argument(
        "--no-stand-tile",
        action="store_true",
        help="Snap to the raw object tile instead of projecting to the "
        "adjacent stand tile for booths. Default: project.",
    )
    args = parser.parse_args(argv)
    project_stand = not args.no_stand_tile

    placements = load_placements(args.placements)
    print(
        f"Loaded {len(placements)} placements from {args.placements}", file=sys.stderr
    )

    preamble, rows = load_bank_rows(args.bank_tsv)
    print(f"Loaded {len(rows)} bank rows from {args.bank_tsv}", file=sys.stderr)

    diffs, updated, unmatched = emit_diff(
        preamble, rows, placements, radius=args.radius, project_stand=project_stand
    )
    for line in diffs:
        print(line)
    print(
        f"\nSummary: {updated} rows would change, {unmatched} unmatched "
        f"(radius {args.radius}, project_stand={project_stand}).",
        file=sys.stderr,
    )

    if args.write and updated:
        apply_diff(
            preamble,
            rows,
            placements,
            radius=args.radius,
            project_stand=project_stand,
            out_path=args.bank_tsv,
        )
        print(f"Wrote updated {args.bank_tsv}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
