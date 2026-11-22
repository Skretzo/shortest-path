#!/usr/bin/env python3
"""
Spellbook Swap teleport generator

Reads teleportation_spells.tsv and teleportation_spells_home.tsv in
src/main/resources/transports and emits teleportation_spells_swapped.tsv and
teleportation_spells_home_swapped.tsv beside them — the teleports a Lunar
caster reaches through Spellbook Swap.

Why this exists: Spellbook Swap lets the player cast one spell from another
spellbook within two minutes, then returns them to Lunar. Since only one spell
may be cast, "Spellbook Swap, then Varrock Teleport" behaves as a single
teleport usable from anywhere, so every non-Lunar teleport spell gets a Lunar
twin row. Generating the rows keeps them in step with the spells they derive
from while the plugin loads them as ordinary rows of the source file's
transport type.

Twin rule, per source row whose Varbits cell names a spellbook other than
Lunar (4070=0, 1 or 3):
  - Items: the spell's runes plus Spellbook Swap's, quantities of a rune both
    cost summed (each Items operand is checked on its own, so a shared rune
    written twice would be counted once), and UNLOCK_SPELLBOOK_SWAP=1, which
    the useSpellbookSwap config key declares, so the key switches them off;
  - Skills: the higher of each skill, so at least 96 Magic;
  - Quests: the spell's plus Dream Mentor;
  - Duration: the spell's plus the swap cast's 4 ticks (the right-click menu
    picks the spellbook without a dialog);
  - Display info: 'Spellbook Swap (<spellbook>): <spell display info>', the
    spellbook named as in Spellbook Swap's right-click menu; the spellbook
    overlay reads the prefix to highlight Spellbook Swap;
  - Varbits: the spellbook requirement becomes 4070=2; the rest (diaries,
    house location) carry over. An Arceuus spell also needs 9631=1: the swap
    offers only the spellbooks the player has unlocked, which being on the
    Arceuus spellbook implied for the source row;
  - Destination, Wilderness level and VarPlayers (such as the home teleport
    cooldown) carry over.
Lunar rows and rows with no spellbook varbit get no twin.

Usage: generate_spellbook_swap_spells.py [--check]
  --check  exit 1 when the committed output differs from what would be
           generated, instead of writing it.
"""

import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
TRANSPORTS = SCRIPT_DIR.parent / "src" / "main" / "resources" / "transports"
# (source, output) file names in TRANSPORTS
FILES = [
    ("teleportation_spells.tsv", "teleportation_spells_swapped.tsv"),
    ("teleportation_spells_home.tsv", "teleportation_spells_home_swapped.tsv"),
]

SPELLBOOK_VARBIT = "4070"
LUNAR = "2"
SPELLBOOK_NAMES = {"0": "Standard", "1": "Ancient", "3": "Arceuus"}
# Extra varbits a twin needs, by source spellbook: ARCEUUS_SPELLBOOK_UNLOCKED
UNLOCK_VARBITS = {"3": ["9631=1"]}

SWAP_NAME = "Spellbook Swap"
SWAP_ITEMS = [("ASTRAL_RUNE", 3), ("COSMIC_RUNE", 2), ("LAW_RUNE", 1)]
# The pure-unlock term the useSpellbookSwap config key declares
SWAP_UNLOCK = "UNLOCK_SPELLBOOK_SWAP=1"
SWAP_SKILLS = {"Magic": 96}
SWAP_QUEST = "Dream Mentor"
SWAP_TICKS = 4


def fail(message):
    print(f"generate_spellbook_swap_spells: error: {message}", file=sys.stderr)
    sys.exit(1)


def merge_items(cell, line_no):
    """The spell's 'NAME=qty&&...' cell with Spellbook Swap's runes added."""
    items = []
    if cell:
        for operand in cell.replace(" ", "").replace("&&", "&").split("&"):
            if "|" in operand or "=" not in operand:
                fail(f"line {line_no}: unsupported Items operand '{operand}'")
            name, _, qty = operand.partition("=")
            items.append([name, int(qty)])
    for name, qty in SWAP_ITEMS:
        for item in items:
            if item[0] == name:
                item[1] += qty
                break
        else:
            items.append([name, qty])
    return "&&".join([f"{name}={qty}" for name, qty in items] + [SWAP_UNLOCK])


def merge_skills(cell, line_no):
    """The spell's 'level Skill;...' cell raised to Spellbook Swap's levels."""
    levels = {}
    if cell:
        for part in cell.split(";"):
            level, _, skill = part.strip().partition(" ")
            if not level.isdigit() or not skill:
                fail(f"line {line_no}: unsupported Skills entry '{part}'")
            levels[skill] = int(level)
    for skill, level in SWAP_SKILLS.items():
        levels[skill] = max(levels.get(skill, 0), level)
    return ";".join(f"{level} {skill}" for skill, level in levels.items())


def merge_quests(cell):
    quests = [q for q in cell.split(";") if q] if cell else []
    if SWAP_QUEST not in quests:
        quests.append(SWAP_QUEST)
    return ";".join(quests)


def generate(source):
    lines = (TRANSPORTS / source).read_text(encoding="utf-8").splitlines()
    header = lines[0]
    columns = header.lstrip("# ").split("\t")
    col = {name: i for i, name in enumerate(columns)}
    for required in ("Destination", "Items", "Skills", "Quests", "Duration", "Display info", "Varbits"):
        if required not in col:
            fail(f"{source} has no '{required}' column")

    # Comment lines carry the column count in tabs, as in the source, so
    # csvlint sees a consistent field count.
    padding = "\t" * (len(columns) - 1)
    out = [
        header,
        f"# generated by scripts/generate_spellbook_swap_spells.py from {source} - do not edit by hand"
        + padding,
    ]
    book = None
    for line_no, line in enumerate(lines[1:], start=2):
        if not line.strip() or line.startswith("#"):
            continue
        fields = line.split("\t")
        fields += [""] * (len(columns) - len(fields))
        varbits = [v for v in fields[col["Varbits"]].split(";") if v]
        spellbook = [v for v in varbits if v.startswith(SPELLBOOK_VARBIT + "=")]
        if not spellbook or spellbook[0] == f"{SPELLBOOK_VARBIT}={LUNAR}":
            continue
        source_book = spellbook[0].partition("=")[2]
        if source_book not in SPELLBOOK_NAMES:
            fail(f"{source} line {line_no}: unknown spellbook '{spellbook[0]}'")
        if source_book != book:
            book = source_book
            out.append(f"# {SPELLBOOK_NAMES[book]} Spellbook via Spellbook Swap - Varbit 4070={LUNAR}" + padding)

        twin = list(fields)
        twin[col["Items"]] = merge_items(fields[col["Items"]], line_no)
        twin[col["Skills"]] = merge_skills(fields[col["Skills"]], line_no)
        twin[col["Quests"]] = merge_quests(fields[col["Quests"]])
        twin[col["Duration"]] = str(int(fields[col["Duration"]]) + SWAP_TICKS)
        twin[col["Display info"]] = f"{SWAP_NAME} ({SPELLBOOK_NAMES[source_book]}): {fields[col['Display info']]}"
        twin[col["Varbits"]] = ";".join(
            [f"{SPELLBOOK_VARBIT}={LUNAR}" if v == spellbook[0] else v for v in varbits]
            + UNLOCK_VARBITS.get(source_book, []))
        out.append("\t".join(twin))
    return "\n".join(out) + "\n"


def main(argv):
    if argv not in ([], ["--check"]):
        fail("usage: generate_spellbook_swap_spells.py [--check]")
    for source, output in FILES:
        content = generate(source)
        path = TRANSPORTS / output
        if argv == ["--check"]:
            current = path.read_text(encoding="utf-8") if path.exists() else None
            if current != content:
                fail(f"{output} is stale; run python3 scripts/generate_spellbook_swap_spells.py and commit it")
            continue
        path.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1:])
