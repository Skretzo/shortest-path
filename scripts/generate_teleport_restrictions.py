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

Raw '#' headers double as authoring notes, so the emitted familyName (and
the memberLabels fallback, which is the family name) passes through
clean_family_name(): 'PS:'/'PS -' postscripts, prose ' - '/'. '/' , ' tails,
and note-style trailing parentheticals are dropped, while qualifiers that
keep names distinct ('Slayer ring (1-8)', 'Trinket of fairies - Spirit
Trees', "Sailors' amulet (Charged)") survive.

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


def _is_prose(tail):
    """A ' - ' tail reads as an authoring note (not a short qualifier like
    'Spirit Trees' or 'Tool Leprechauns') when it starts lowercase or with a
    digit, or carries sentence punctuation."""
    if not tail:
        return True
    return (tail[0].islower() or tail[0].isdigit()
            or any(ch in tail for ch in (",", ".", ":", ";")))


def _is_note_paren(content):
    """A trailing '(...)' group is an authoring note when it reads as prose —
    '(uncharged is 11113)', '(2 has 3 teleports to farm, ...)' — and a
    qualifier when it is a short tag like '(1-8)' or '(Charged)'."""
    if not content:
        return True
    return (any(ch in content for ch in (",", ";", ":")) or " " in content
            or content[0].islower())


def _depth_zero_cut(name):
    """Index of the first note delimiter occurring outside parentheses —
    ' PS:'/'PS -' postscripts, '. '/' , ' sentence tails, or a ' - ' tail
    that reads like prose — or len(name) when none is present."""
    depth = 0
    for i, ch in enumerate(name):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth = max(0, depth - 1)
        elif depth == 0:
            if (name.startswith(" PS:", i) or name.startswith(" PS -", i)
                    or name.startswith(" PS-", i)):
                return i
            if name.startswith(". ", i) or name.startswith(", ", i):
                return i
            if name.startswith(" - ", i) and _is_prose(name[i + 3:]):
                return i
    return len(name)


def clean_family_name(name):
    """Reduce a raw '#' header comment to the display label the restriction
    checklist renders. Header comments double as authoring notes ('Stony
    basalt - 6528 is MAKING_FRIENDS_WITH_MY_ARM ...'), so note tails are
    stripped while qualifiers that keep family names distinct ('Trinket of
    fairies - Spirit Trees', 'Slayer ring (1-8)') survive."""
    name = name[:_depth_zero_cut(name)].rstrip(" .-")
    while name.endswith(")"):
        open_idx = name.rfind("(")
        if open_idx < 0 or not _is_note_paren(name[open_idx + 1:-1]):
            break
        name = name[:open_idx].rstrip(" .-")
    return name


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
    display_names = set()
    for family in families:
        if not family["ids"]:
            fail(
                f"family '{family['name']}' has no member ids — a header whose section "
                "contains no unclaimed item ids should be an annotation or be dropped; "
                "needs planner review"
            )
        display_name = clean_family_name(family["name"])
        if not display_name:
            fail(
                f"family header '{family['name']}' reduces to an empty display name — "
                "the checklist renders familyName verbatim; needs planner review"
            )
        if display_name in display_names:
            fail(
                f"family '{family['name']}' reduces to display name '{display_name}', "
                "which an earlier family already emits — the checklist requires unique "
                "labels; needs planner review"
            )
        display_names.add(display_name)
        member_ids = sorted(family["ids"])
        label_entries = []
        for item_id in member_ids:
            label = labels.get(item_id) or display_name
            if any(ch in label for ch in (",", "=", "\t", "\n")):
                fail(
                    f"label '{label}' for item id {item_id} (family '{display_name}') "
                    "contains a schema-breaking character (',', '=', tab or newline)"
                )
            label_entries.append(f"{item_id}={label}")
        out_lines.append(
            "\t".join(
                [
                    display_name,
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
