#!/usr/bin/env python3
"""Filter the head coordinate dump down to coordinates not present in the base dump.

This exists for the GitHub coordinate preview workflow, which computes dumps for the
merge-base commit and the PR head, then renders only newly introduced coordinates.

The comparison intentionally keys only on the packed coordinate string. For the
preview use-case we only care whether a tile is newly introduced to the rendered
set, not whether the label or source metadata for an existing tile changed.
"""

import json
import sys
from pathlib import Path


def read_json(path_str: str):
    return json.loads(Path(path_str).read_text(encoding="utf-8"))


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: diff_coordinate_json.py <base.json> <head.json> <output.json>",
            file=sys.stderr,
        )
        return 1

    base_path, head_path, output_path = sys.argv[1:]
    base_items = read_json(base_path)
    head_items = read_json(head_path)
    base_coordinates = {item["coordinate"] for item in base_items}
    changed = [item for item in head_items if item["coordinate"] not in base_coordinates]

    Path(output_path).write_text(json.dumps(changed), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
