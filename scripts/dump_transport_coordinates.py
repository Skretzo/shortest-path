#!/usr/bin/env python3
"""Dump transport and destination coordinates as JSON for coordinate preview tooling.

Reads every *.tsv file from the transport and destination resources directories
and emits a JSON array where each element represents one previewable tile
coordinate:

    [{"id": "coord-<hash>", "entries": [{"label": "...", "source": "..."}], "coordinate": "X/Y/P"}, ...]

Coordinates that appear in multiple rows are merged into a single entry. Labels
stay structured in this intermediate JSON so diffing can reason about additions
and removals without re-parsing a display string. Each label keeps its first
source line alongside it so the final rendered `label` and `source` strings can
be reordered in lockstep. The emitted id is a stable hash of the rendered
preview identity (coordinate + merged labels), so it changes when the visible
meaning of a tile changes but not when only source line references move.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

ORIGIN_COLUMN = "Origin"
DESTINATION_COLUMN = "Destination"
DISPLAY_INFO_COLUMN = "Display info"
OBJECT_INFO_COLUMN = "menuOption menuTarget objectID"
INFO_COLUMN = "Info"
MERGED_VALUE_SEPARATOR = "; "


def parse_coordinate(value: str):
    """Parse an 'X Y P' coordinate string.  Returns (x, y, plane) or None."""
    if not value or not value.strip():
        return None
    parts = value.strip().split()
    if len(parts) != 3:
        return None
    try:
        return int(parts[0]), int(parts[1]), int(parts[2])
    except ValueError:
        return None


def coordinate_string(x: int, y: int, p: int) -> str:
    return f"{x}/{y}/{p}"


def coordinate_id(coordinate: str, label: str) -> str:
    digest = hashlib.sha1(f"{coordinate}|{label}".encode("utf-8")).hexdigest()[:12]
    return f"coord-{digest}"


def transport_type_name(filename: str) -> str:
    """Convert 'fairy_rings.tsv' -> 'Fairy Rings'."""
    return " ".join(word.capitalize() for word in Path(filename).stem.split("_"))


def merge_entries(existing: list[dict], label: str, source: str) -> list[dict]:
    label = (label or "").strip()
    source = (source or "").strip()
    if not label:
        return existing
    for entry in existing:
        if entry["label"] == label:
            return existing
    return [*existing, {"label": label, "source": source}]


def destination_type_name(path: Path) -> str:
    """Convert 'game_features/bank.tsv' -> 'Bank'."""
    return " ".join(word.capitalize() for word in path.stem.split("_"))


def build_label(type_name: str, role: str, display_info: str, object_info: str) -> str:
    seen = set()
    context_parts = []
    for val in (display_info, object_info):
        val = (val or "").strip()
        if val and val not in seen:
            seen.add(val)
            context_parts.append(val)
    label = f"{type_name}: {role}"
    if context_parts:
        label += " - " + " / ".join(context_parts)
    return label


def parse_tsv(path: Path) -> list:
    """Return a list of {header: value, '_line': N} dicts for each data row."""
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines:
        return []

    # Strip leading "# " or "#" from the header line
    header_line = lines[0]
    if header_line.startswith("# "):
        header_line = header_line[2:]
    elif header_line.startswith("#"):
        header_line = header_line[1:]
    headers = header_line.split("\t")

    records = []
    for line_idx, line in enumerate(lines[1:], start=2):
        if line.startswith("#") or not line.strip():
            continue
        # Split preserving trailing empty strings (matching Java's split(DELIM, -1))
        parts = line.split("\t")
        row = {
            headers[i]: (parts[i] if i < len(parts) else "")
            for i in range(len(headers))
        }
        row["_line"] = line_idx
        records.append(row)
    return records


def merge_item(items: dict, coord, label: str, source: str) -> None:
    x, y, p = coord
    coord_str = coordinate_string(x, y, p)

    if coord_str not in items:
        items[coord_str] = {
            "entries": [{"label": label, "source": source}],
            "coordinate": coord_str,
        }
        return

    existing = items[coord_str]
    items[coord_str] = {
        "entries": merge_entries(existing["entries"], label, source),
        "coordinate": coord_str,
    }


def export_transport_coordinates(base_dir: Path, items: dict) -> None:
    """Merge transport coordinate items from all *.tsv files in base_dir."""
    for tsv_path in sorted(base_dir.glob("*.tsv")):
        rel_source_prefix = f"transports/{tsv_path.name}"
        type_name = transport_type_name(tsv_path.name)

        for record in parse_tsv(tsv_path):
            display_info = record.get(DISPLAY_INFO_COLUMN, "")
            object_info = record.get(OBJECT_INFO_COLUMN, "")
            source = f"{rel_source_prefix}:{record['_line']}"

            for role, col in (
                ("origin", ORIGIN_COLUMN),
                ("destination", DESTINATION_COLUMN),
            ):
                coord = parse_coordinate(record.get(col, ""))
                if coord is None:
                    continue
                label = build_label(type_name, role, display_info, object_info)
                merge_item(items, coord, label, source)

def export_destination_coordinates(base_dir: Path, items: dict) -> None:
    """Merge destination coordinate items from all *.tsv files under base_dir."""
    for tsv_path in sorted(base_dir.rglob("*.tsv")):
        rel_source_prefix = tsv_path.relative_to(base_dir.parent).as_posix()
        type_name = destination_type_name(tsv_path)

        for record in parse_tsv(tsv_path):
            info = (record.get(INFO_COLUMN, "") or "").strip()
            source = f"{rel_source_prefix}:{record['_line']}"
            coord = parse_coordinate(record.get(DESTINATION_COLUMN, ""))
            if coord is None:
                continue

            label = f"Destination: {type_name}"
            if info:
                label += f" - {info}"
            merge_item(items, coord, label, source)


def export_coordinates(transport_dir: Path, destination_dir: Path) -> list:
    """Return merged coordinate items from transport and destination resources."""
    items: dict = {}  # keyed by coordinate string, insertion-ordered

    export_transport_coordinates(transport_dir, items)
    export_destination_coordinates(destination_dir, items)

    result = []
    for item in items.values():
        merged_label = MERGED_VALUE_SEPARATOR.join(
            entry["label"] for entry in item["entries"]
        )
        result.append({
            "id": coordinate_id(item["coordinate"], merged_label),
            "entries": item["entries"],
            "coordinate": item["coordinate"],
        })

    return result


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Dump transport coordinates as JSON for the coordinate preview workflow."
    )
    parser.add_argument(
        "--transport-dir",
        default="src/main/resources/transports",
        help="Directory containing transport .tsv files (default: src/main/resources/transports)",
    )
    parser.add_argument(
        "--destination-dir",
        default="src/main/resources/destinations",
        help="Directory containing destination .tsv files (default: src/main/resources/destinations)",
    )
    parser.add_argument("--output", help="Write JSON to this file instead of stdout")
    parser.add_argument(
        "--pretty", action="store_true", help="Pretty-print JSON output"
    )
    args = parser.parse_args()

    transport_dir = Path(args.transport_dir)
    if not transport_dir.is_dir():
        print(f"Error: transport directory not found: {transport_dir}", file=sys.stderr)
        return 1

    destination_dir = Path(args.destination_dir)
    if not destination_dir.is_dir():
        print(
            f"Error: destination directory not found: {destination_dir}",
            file=sys.stderr,
        )
        return 1

    result = export_coordinates(transport_dir, destination_dir)
    json_str = json.dumps(result, indent=2 if args.pretty else None)

    if args.output:
        out_path = Path(args.output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json_str, encoding="utf-8")
    else:
        print(json_str, end="")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
