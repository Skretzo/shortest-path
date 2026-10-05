#!/usr/bin/env python3
"""
Teleport restriction family map generator

Reads src/main/resources/transports/teleportation_items.tsv and emits
src/main/resources/transports/teleport_restrictions.tsv — the checked-in
family -> item-id map the config panel's restriction checklist groups by.

Why this exists: '#' lines in the transport TSVs are comments that TsvParser
skips at runtime, so the category grouping has to be materialized as its own
resource.

Family rule: a '#' comment line opens a new family unless it is an annotation
inside the current family. A line is an annotation when
  1. it directly follows another '#' line (the first line of a comment block
     is the header, the rest are notes), or
  2. the data rows between it and the next '#' line reference an item id that
     an earlier family already claimed — a comment that only re-groups ids the
     current family owns is a sub-note (e.g. '# 1 - Rimmington' inside the
     Teleport to house section, or '# Varbit 4480 is DIARY_VARROCK_MEDIUM'
     inside Teleport tablets), not a new family.
Every numeric item id therefore lands in exactly one family and family names
stay unique — the invariants TransportDataLintTest asserts.

Each member id's display label is harvested from the 'Display info' column:
the text before the first ':' (the whole cell when no colon is present),
stripped. When an id appears on multiple rows the longest label wins — the
most specific variant name ('Karamja gloves 4' over 'Karamja gloves') — and
ties keep the first occurrence in file order. Ids with no Display info at all
fall back to the family name.

The output is deterministic: families in file order, member ids sorted,
memberLabels as 'id=label' pairs sorted by id.
"""

import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
SOURCE_TSV = SCRIPT_DIR.parent / "src" / "main" / "resources" / "transports" / "teleportation_items.tsv"
OUTPUT_TSV = SCRIPT_DIR.parent / "src" / "main" / "resources" / "transports" / "teleport_restrictions.tsv"

ITEMS_COLUMN = "Items"
DISPLAY_INFO_COLUMN = "Display info"
UNLOCK_PREFIX = "UNLOCK_"


def fail(message):
    print(f"generate_teleport_restrictions: error: {message}", file=sys.stderr)
    sys.exit(1)


def parse_item_ids(items_cell, line_no):
    """Return the numeric item ids referenced by an Items cell, in cell order.

    Mirrors ItemRequirementParser's grammar: '&'/ '|' (and their doubled
    forms) separate 'NAME=qty' operands; UNLOCK_* tokens are unlock
    requirements, not item ids. A non-numeric, non-UNLOCK_ operand is an
    ItemVariations name — the map is numeric-only, so fail loudly.
    """
    ids = []
    normalized = items_cell.replace(" ", "").replace("&&", "&").replace("||", "|")
    for and_part in normalized.split("&"):
        for or_part in and_part.split("|"):
            if not or_part:
                continue
            if "=" not in or_part:
                fail(f"line {line_no}: malformed Items operand '{or_part}' in '{items_cell}'")
            name, _, _qty = or_part.partition("=")
            if name.upper().startswith(UNLOCK_PREFIX):
                continue
            if not name.isdigit():
                fail(
                    f"line {line_no}: non-numeric item token '{or_part}' in '{items_cell}' "
                    "(looks like an ItemVariations name — the family map is numeric-only; "
                    "this needs planner review)"
                )
            item_id = int(name)
            if item_id <= 0:
                fail(f"line {line_no}: non-positive item id '{or_part}'")
            if item_id not in ids:
                ids.append(item_id)
    return ids


def main():
    lines = SOURCE_TSV.read_text(encoding="utf-8").splitlines()
    if not lines:
        fail(f"{SOURCE_TSV} is empty")

    header = [h.strip() for h in lines[0].lstrip("#").split("\t")]
    if ITEMS_COLUMN not in header:
        fail(f"no '{ITEMS_COLUMN}' column in header of {SOURCE_TSV.name}")
    items_idx = header.index(ITEMS_COLUMN)
    display_idx = header.index(DISPLAY_INFO_COLUMN) if DISPLAY_INFO_COLUMN in header else None

    # Precompute, for every '#' line, the item ids in the data rows between it
    # and the next '#' line (the block the header would claim).
    comment_lines = {i for i, line in enumerate(lines) if line.startswith("#")}
    block_ids = {}
    for i in comment_lines:
        ids = []
        j = i + 1
        while j < len(lines) and not lines[j].startswith("#"):
            line = lines[j]
            if line.strip():
                cols = line.split("\t")
                if items_idx < len(cols) and cols[items_idx].strip():
                    ids.extend(parse_item_ids(cols[items_idx], j + 1))
            j += 1
        block_ids[i] = set(ids)

    families = []  # ordered; each: {"name": str, "ids": [int]}
    current_family = None
    claimed = {}  # item id -> owning family name
    labels = {}  # item id -> longest Display info label seen

    for i, line in enumerate(lines[1:], start=1):
        if line.startswith("#"):
            name = line.split("\t", 1)[0].lstrip("#").strip()
            follows_comment = lines[i - 1].startswith("#") and (i - 1) != 0
            overlaps = block_ids[i] & claimed.keys()
            if follows_comment or overlaps:
                # Annotation inside the current family — data rows keep
                # belonging to the family already open.
                if overlaps and current_family is not None:
                    foreign = [item_id for item_id in overlaps if claimed[item_id] != current_family["name"]]
                    if foreign:
                        fail(
                            f"line {i + 1}: '#'-comment's rows reference ids already claimed by a "
                            f"different family ({sorted(foreign)} owned by "
                            f"{sorted({claimed[i] for i in foreign})}, current family "
                            f"'{current_family['name']}') — cannot keep the id->family "
                            "partition disjoint; needs planner review"
                        )
                continue
            current_family = {"name": name, "ids": []}
            families.append(current_family)
            continue

        if not line.strip():
            continue

        if current_family is None:
            fail(f"line {i + 1}: data row before the first family header")

        cols = line.split("\t")
        row_ids = parse_item_ids(cols[items_idx], i + 1) if items_idx < len(cols) else []
        display_cell = cols[display_idx].strip() if display_idx is not None and display_idx < len(cols) else ""
        label = display_cell.split(":", 1)[0].strip()

        for item_id in row_ids:
            owner = claimed.get(item_id)
            if owner is not None:
                if owner != current_family["name"]:
                    fail(
                        f"line {i + 1}: item id {item_id} already belongs to family "
                        f"'{owner}' but also appears under '{current_family['name']}' — "
                        "cannot keep the id->family partition disjoint; needs planner review"
                    )
            else:
                claimed[item_id] = current_family["name"]
                current_family["ids"].append(item_id)

            if label and (item_id not in labels or len(label) > len(labels[item_id])):
                labels[item_id] = label

    if not families:
        fail("no families found — is the source TSV format as expected?")

    out_lines = ["# familyName\tdisplayItemId\tmemberItemIds\tmemberLabels"]
    for family in families:
        if not family["ids"]:
            fail(
                f"family '{family['name']}' has no member ids — a header whose section "
                "contains no unclaimed item ids should be an annotation or be dropped; "
                "needs planner review"
            )
        member_ids = sorted(family["ids"])
        label_entries = []
        for item_id in member_ids:
            label = labels.get(item_id) or family["name"]
            if any(ch in label for ch in (",", "=", "\t", "\n")):
                fail(
                    f"label '{label}' for item id {item_id} (family '{family['name']}') "
                    "contains a schema-breaking character (',', '=', tab or newline)"
                )
            label_entries.append(f"{item_id}={label}")
        out_lines.append(
            "\t".join(
                [
                    family["name"],
                    str(member_ids[0]),
                    ",".join(str(i) for i in member_ids),
                    ",".join(label_entries),
                ]
            )
        )

    OUTPUT_TSV.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(families)} families covering {len(claimed)} item ids -> {OUTPUT_TSV}")


if __name__ == "__main__":
    main()
