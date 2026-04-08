#!/usr/bin/env python3
"""Filter the head coordinate dump down to semantically changed preview tiles.

This exists for the GitHub coordinate preview workflow, which computes dumps for the
merge-base commit and the PR head, then renders only newly introduced or
semantically changed coordinates.

The comparison keys on the rendered preview identity for a tile. New coordinates
are always included. For existing coordinates, pure label removals are ignored,
while label additions or mixed replacements are included. Source metadata is
intentionally excluded so line-number churn does not trigger preview noise.
"""

import json
import sys
from pathlib import Path

MERGED_VALUE_SEPARATOR = "; "
BOLD_MARKER = "**"


def read_json(path_str: str):
    return json.loads(Path(path_str).read_text(encoding="utf-8"))


def label_parts(item: dict) -> set[str]:
    return {
        entry["label"].strip()
        for entry in item["entries"]
    }


def is_changed(base_item: dict | None, head_item: dict) -> bool:
    if base_item is None:
        return True

    base_labels = label_parts(base_item)
    head_labels = label_parts(head_item)

    # Previews are asymmetric: we want to surface newly added meaning at a tile,
    # but avoid re-rendering old coordinates when a PR only removes one of the
    # existing labels. Mixed edits (some labels removed, others added) are still
    # shown because the tile's visible meaning changed in a non-removal-only way.
    if head_labels == base_labels:
        return False

    if head_labels < base_labels:
        return False

    return True


# Classify each unique head-side label once so both renderers can consume a
# single ordered list with explicit new/old metadata. New labels are moved
# to the front to make the reason this coordinate rendered obvious.
def order_preview_entries(base_item: dict | None, head_item: dict) -> list[dict]:
    base_labels = label_parts(base_item) if base_item is not None else set()
    new_entries = []
    existing_entries = []
    seen_labels = set()

    for entry in head_item["entries"]:
        label = entry["label"]
        if label in seen_labels:
            continue
        seen_labels.add(label)
        annotated_entry = {
            "label": entry["label"],
            "source": entry["source"],
            "is_new": label not in base_labels,
        }
        if label in base_labels:
            existing_entries.append(annotated_entry)
        else:
            new_entries.append(annotated_entry)

    return new_entries + existing_entries

### Render a field with new items bolded, separated by ";".
def render_preview_field(ordered_entries: list[dict], field: str) -> str:
    rendered_values = []
    for entry in ordered_entries:
        value = entry[field]
        if entry["is_new"]:
            rendered_values.append(f"{BOLD_MARKER}{value}{BOLD_MARKER}")
        else:
            rendered_values.append(value)
    return MERGED_VALUE_SEPARATOR.join(rendered_values)


def to_preview_item(base_item: dict | None, item: dict) -> dict:
    ordered_entries = order_preview_entries(base_item, item)
    return {
        "id": item["id"],
        "label": render_preview_field(ordered_entries, "label"),
        "coordinate": item["coordinate"],
        "source": render_preview_field(ordered_entries, "source"),
    }


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
    base_by_coordinate = {item["coordinate"]: item for item in base_items}
    changed = [
        to_preview_item(base_by_coordinate.get(item["coordinate"]), item)
        for item in head_items
        if is_changed(base_by_coordinate.get(item["coordinate"]), item)
    ]

    Path(output_path).write_text(json.dumps(changed), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
