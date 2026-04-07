#!/usr/bin/env python3
"""
TSV Consistency Checker

Validates TSV transport files for:
  1. Header must start with '#'
  2. Column names must all be recognized by the parser
  3. Column count consistency (every data row must match the header)
  4. Origin/Destination cells must be 'x y plane' coordinates (or empty)
  5. Skill-like values ('N SkillName') must not appear in Items columns
  6. Items column values must follow 'NAME=qty' format (& / | separated)
  7. Skills column values must follow 'N SkillName' format (; separated)
  8. Varbits/VarPlayers must follow 'id<op>value' format (; separated)
  9. Duration must be a positive integer
 10. Wilderness level must be a non-negative integer
 11. Consumable must be 'T' or 'F'

Usage:
  python3 check_tsv.py [directory]

  directory  Path to search for *.tsv files (default: ../src/main/resources)
"""

import os
import re
import sys
import glob

# ---------------------------------------------------------------------------
# Constants matching TransportRecord.Fields and destination TSV headers
# ---------------------------------------------------------------------------
KNOWN_COLUMNS = {
    "Origin",
    "Destination",
    "menuOption menuTarget objectID",
    "Skills",
    "Items",
    "Quests",
    "Duration",
    "Display info",
    "Consumable",
    "Wilderness level",
    "Varbits",
    "VarPlayers",
    # Destination-file-only columns
    "Info",
}

SKILL_NAMES = {
    "attack", "strength", "defence", "ranged", "prayer", "magic",
    "runecraft", "hitpoints", "crafting", "mining", "smithing",
    "fishing", "cooking", "firemaking", "woodcutting", "agility",
    "herblore", "thieving", "fletching", "slayer", "farming",
    "construction", "hunter", "summoning", "dungeoneering",
    "divination", "invention", "archaeology", "necromancy",
    "sailing", "total", "combat", "quest", "points",
}

# Varbit/VarPlayer operator characters (from VarCheckType enum)
_VAR_REQ = re.compile(r"^\d+[=><&@]\d+$")
# World coordinate: 'x y plane'
_COORD = re.compile(r"^\d+ \d+ \d+$")
# Item requirement part after normalization: 'NAME=qty' where NAME is [A-Z0-9_]
_ITEM_PART = re.compile(r"^[A-Z0-9_]+=\d+$")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DIR = os.path.join(SCRIPT_DIR, "..", "src", "main", "resources")


# ---------------------------------------------------------------------------
# Field validators
# ---------------------------------------------------------------------------

def looks_like_skill(value):
    """Return True if value looks like a skill requirement, e.g. '35 Construction'."""
    parts = value.strip().split()
    if len(parts) == 2:
        try:
            int(parts[0])
            name = parts[1].lower()
            if name in SKILL_NAMES or name.rstrip("s") in SKILL_NAMES:
                return True
        except ValueError:
            pass
    return False


def validate_coordinate(value):
    """Return error string if value is not a valid coordinate (or empty for permutations)."""
    v = value.strip()
    if not v:
        return None  # empty = location permutation, valid
    if not _COORD.match(v):
        return f"invalid coordinate '{v}' (expected 'x y plane')"
    return None


def validate_skills(value):
    """Return error strings for any bad skill requirement (';' separated)."""
    errors = []
    for part in value.split(";"):
        part = part.strip()
        if not part:
            continue
        if not looks_like_skill(part):
            errors.append(f"invalid skill requirement '{part}' (expected 'N SkillName')")
    return errors


def validate_items(value):
    """Return error strings for any bad item requirement ('&' and '|' separated)."""
    errors = []
    # Normalize: strip spaces, collapse double operators, uppercase — mirrors ItemRequirementParser
    normalized = value.replace(" ", "").replace("&&", "&").replace("||", "|").upper()
    for and_part in normalized.split("&"):
        for or_part in and_part.split("|"):
            if not or_part:
                continue
            if not _ITEM_PART.match(or_part):
                errors.append(
                    f"invalid item requirement '{or_part}' in '{value}' "
                    f"(expected 'NAME=qty')"
                )
    return errors


def validate_var_requirements(value):
    """Return error strings for bad varbit/varplayer requirements (';' separated)."""
    errors = []
    for part in value.split(";"):
        part = part.strip()
        if not part:
            continue
        if not _VAR_REQ.match(part):
            errors.append(
                f"invalid var requirement '{part}' "
                f"(expected '<id><op><value>' where op is one of = > < & @)"
            )
    return errors


def validate_duration(value):
    """Return error string if value is not a positive integer."""
    v = value.strip()
    if not v:
        return None
    try:
        if int(v) <= 0:
            return f"duration '{v}' must be a positive integer"
    except ValueError:
        return f"duration '{v}' is not an integer"
    return None


def validate_wilderness_level(value):
    """Return error string if value is not a non-negative integer."""
    v = value.strip()
    if not v:
        return None
    try:
        if int(v) < 0:
            return f"wilderness level '{v}' must be >= 0"
    except ValueError:
        return f"wilderness level '{v}' is not an integer"
    return None


def validate_consumable(value):
    """Return error string if value is not a recognised consumable flag."""
    v = value.strip()
    if not v:
        return None
    if v not in ("T", "F"):
        return f"consumable '{v}' must be 'T' or 'F'"
    return None


# Map column name → validator function
COLUMN_VALIDATORS = {
    "Origin":           validate_coordinate,
    "Destination":      validate_coordinate,
    "Skills":           validate_skills,
    "Items":            validate_items,
    "Varbits":          validate_var_requirements,
    "VarPlayers":       validate_var_requirements,
    "Duration":         validate_duration,
    "Wilderness level": validate_wilderness_level,
    "Consumable":       validate_consumable,
}


# ---------------------------------------------------------------------------
# Per-file checker
# ---------------------------------------------------------------------------

def check_tsv(filepath):
    """Check a single TSV file. Returns a list of issue strings (empty = clean)."""
    issues = []
    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.readlines()

    if not lines:
        return issues

    raw_header = lines[0]

    # Check 1: header must start with '#'
    if not raw_header.startswith("#"):
        issues.append("  header line does not start with '#'")

    # Parse header
    header_line = raw_header.lstrip("#").strip()
    headers = [h.strip() for h in header_line.split("\t")]
    num_cols = len(headers)

    # Check 2: unknown column names
    for col in headers:
        if col not in KNOWN_COLUMNS:
            issues.append(f"  unknown column name: '{col}'")

    # Build per-column validator list (None = no validator)
    validators = [COLUMN_VALIDATORS.get(h) for h in headers]

    # Special-case: track the Items column index for the skill-in-items check
    items_idx  = next((i for i, h in enumerate(headers) if h == "Items"),  None)

    # Check data rows
    col_count_errors = 0
    for lineno, line in enumerate(lines[1:], start=2):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue

        cols = line.split("\t")

        # Check 3: column count
        if len(cols) != num_cols:
            col_count_errors += 1
            if col_count_errors <= 5:
                issues.append(
                    f"  line {lineno}: expected {num_cols} cols, got {len(cols)}"
                )
            continue  # skip per-cell validation when structure is wrong

        # Check 4-11: per-cell validators
        for col_idx, (col_val, validator) in enumerate(zip(cols, validators)):
            if validator is None or not col_val.strip():
                continue

            result = validator(col_val)
            if result is None:
                continue
            # Validators return either a single string or a list of strings
            errors = result if isinstance(result, list) else [result]
            col_name = headers[col_idx]
            for err in errors:
                # Extra check: skill-in-items is already covered by validate_items,
                # but we also keep the human-readable phrasing from validate_skills check
                issues.append(f"  line {lineno} [{col_name}]: {err}")

    if col_count_errors > 5:
        issues.append(f"  ... and {col_count_errors - 5} more column-count errors")

    return issues


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    resource_dir = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DIR

    tsv_files = sorted(
        glob.glob(os.path.join(resource_dir, "**", "*.tsv"), recursive=True)
    )

    if not tsv_files:
        print(f"No TSV files found under: {resource_dir}", file=sys.stderr)
        sys.exit(1)

    any_issues = False
    for filepath in tsv_files:
        rel = os.path.relpath(filepath)
        issues = check_tsv(filepath)
        if issues:
            any_issues = True
            print(f"FAIL  {rel}")
            for issue in issues:
                print(issue)
        else:
            print(f"ok    {rel}")

    print()
    if any_issues:
        print("TSV consistency check FAILED.")
        sys.exit(1)
    else:
        print("TSV consistency check passed.")


if __name__ == "__main__":
    main()

