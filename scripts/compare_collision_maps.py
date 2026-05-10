#!/usr/bin/env python3
"""Compare two collision-map.zip artifacts at the edge-flag level.

For each region present in either zip:
  - decode the per-tile-per-plane (north, east) flag pairs as raw bits
    using Java BitSet semantics (little-endian byte order, LSB-first bit order)
  - count edges that flipped open->blocked, blocked->open, or are unique
    to one side (region added/removed)

Region entry name is "<regionX>_<regionY>". Bit index for a tile at
(x, y, plane) inside a region is:
    ((plane * 64 * 64) + (y * 64) + x) * 2 + flag   (flag 0 = N, flag 1 = E)
where (x, y) are local 0..63.

Optionally, ``--probe x y plane`` prints the four-edge state at that world
coordinate in both maps.
"""

from __future__ import annotations

import argparse
import io
import sys
import zipfile
from collections import Counter
from typing import Dict, Optional, Tuple

REGION = 64


def read_zip(path: str) -> Dict[Tuple[int, int], bytes]:
    out: Dict[Tuple[int, int], bytes] = {}
    with zipfile.ZipFile(path) as zf:
        for name in zf.namelist():
            try:
                rx_s, ry_s = name.split("_")
                rx, ry = int(rx_s), int(ry_s)
            except ValueError:
                continue
            out[(rx, ry)] = zf.read(name)
    return out


def planes(b: bytes) -> int:
    if not b:
        return 0
    bits = len(b) * 8
    return bits // (REGION * REGION * 2)


def get_bit(b: bytes, index: int) -> bool:
    byte = index >> 3
    if byte >= len(b):
        return False
    return bool(b[byte] & (1 << (index & 7)))


def edge(b: bytes, lx: int, ly: int, p: int, flag: int) -> bool:
    idx = ((p * REGION * REGION) + (ly * REGION) + lx) * 2 + flag
    return get_bit(b, idx)


def compare_region(old: bytes, new: bytes) -> Dict[str, int]:
    po, pn = planes(old or b""), planes(new or b"")
    pmax = max(po, pn)
    stats = Counter()
    for p in range(pmax):
        for ly in range(REGION):
            for lx in range(REGION):
                for flag in range(2):
                    o = edge(old, lx, ly, p, flag) if p < po else False
                    n = edge(new, lx, ly, p, flag) if p < pn else False
                    if o == n:
                        if o:
                            stats["both_open"] += 1
                        else:
                            stats["both_blocked"] += 1
                    elif o and not n:
                        stats["opened_to_blocked"] += 1
                    else:
                        stats["blocked_to_opened"] += 1
    return stats


def probe(maps: Dict[str, Dict[Tuple[int, int], bytes]], wx: int, wy: int, p: int) -> None:
    rx, ry = wx // REGION, wy // REGION
    lx, ly = wx - rx * REGION, wy - ry * REGION
    for label, regions in maps.items():
        b = regions.get((rx, ry), b"")
        pc = planes(b)
        if p >= pc:
            print(f"{label}: region ({rx},{ry}) has only {pc} planes")
            continue
        n = edge(b, lx, ly, p, 0)
        e = edge(b, lx, ly, p, 1)
        bs = regions.get((rx, ry - 0), b"")
        # south edge = north edge of (lx, ly-1) in same region, or southern region if ly==0
        if ly > 0:
            s = edge(b, lx, ly - 1, p, 0)
        else:
            sb = regions.get((rx, ry - 1), b"")
            spc = planes(sb)
            s = edge(sb, lx, REGION - 1, p, 0) if p < spc else False
        if lx > 0:
            w = edge(b, lx - 1, ly, p, 1)
        else:
            wb = regions.get((rx - 1, ry), b"")
            wpc = planes(wb)
            w = edge(wb, REGION - 1, ly, p, 1) if p < wpc else False
        print(f"{label} ({wx},{wy},{p}): N={int(n)} E={int(e)} S={int(s)} W={int(w)}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--probe", nargs=3, type=int, metavar=("X", "Y", "PLANE"),
                    action="append", help="Print four-edge state for a world tile")
    ap.add_argument("--top", type=int, default=10,
                    help="Show top-N regions with most changes")
    args = ap.parse_args()

    old = read_zip(args.old)
    new = read_zip(args.new)

    totals = Counter()
    per_region = []
    keys = set(old) | set(new)
    for k in keys:
        s = compare_region(old.get(k, b""), new.get(k, b""))
        totals.update(s)
        changes = s.get("opened_to_blocked", 0) + s.get("blocked_to_opened", 0)
        if changes:
            per_region.append((k, changes, s.get("opened_to_blocked", 0),
                               s.get("blocked_to_opened", 0)))

    only_old = sorted(set(old) - set(new))
    only_new = sorted(set(new) - set(old))

    print("=" * 60)
    print(f"Regions in old: {len(old)}  new: {len(new)}")
    if only_old:
        print(f"Removed regions ({len(only_old)}): {only_old[:8]}{'...' if len(only_old)>8 else ''}")
    if only_new:
        print(f"Added regions   ({len(only_new)}): {only_new[:8]}{'...' if len(only_new)>8 else ''}")
    print()
    print("Edge totals:")
    for k in ("both_open", "both_blocked", "opened_to_blocked", "blocked_to_opened"):
        print(f"  {k:>22}: {totals[k]:>12,}")
    changed = totals["opened_to_blocked"] + totals["blocked_to_opened"]
    total = sum(totals.values()) or 1
    print(f"  {'changed':>22}: {changed:>12,} ({100.0*changed/total:.4f}%)")
    print(f"  {'total edges scanned':>22}: {total:>12,}")
    print()

    per_region.sort(key=lambda r: -r[1])
    print(f"Top {args.top} most-changed regions (region_x, region_y):")
    for (rx, ry), c, o2b, b2o in per_region[: args.top]:
        bx, by = rx * REGION, ry * REGION
        print(f"  ({rx:>2},{ry:>2}) world ~({bx},{by})  changed={c:>6}  "
              f"+blocked={o2b:>6}  -blocked={b2o:>6}")

    if args.probe:
        print()
        print("Probes:")
        maps = {"old": old, "new": new}
        for px, py, pp in args.probe:
            probe(maps, px, py, pp)

    return 0


if __name__ == "__main__":
    sys.exit(main())
