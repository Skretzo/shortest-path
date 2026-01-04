# Transport TSV format

This document describes the canonical column set and allowed values used across all files in `src/main/resources/transports/` (loaded by `TransportLoader.loadAllFromResources`). It reflects the actual parser behavior in `shortestpath.Transport`.

All transport TSV files share the same structure: rows are tab-separated fields, comment lines start with `#`, and many columns are optional depending on the transport type. The focus below is on columns and permitted value formats rather than file-specific variants.

## Quick rules

- Header: the first line is the header and may either be a commented header starting with `#` (optionally followed by a single space) or a bare header line (no `#`). Both forms are supported; the header defines tab-separated column names. Unknown columns are ignored by the pathfinding parser.
- Header-driven mapping: column order does not matter — the parser maps fields by the header text.
- Rows: split on tabs; ignore lines beginning with `#` (other than a commented header) and blank lines.
- Coordinate fields (Origin/Destination) are three integers separated by single spaces: `x y plane` (example: `3221 3218 0`).
- Empty cells are preserved. For permutation-style transports (e.g., fairy rings), leave Origin or Destination empty to signal “permutation” ([details below](#two-way-and-permutation-generation)).
- If a transport is a two-way connection, add two separate rows (one for each direction); the parser does not auto-generate reversals.
- Optional columns may be empty; keep column positions consistent within a file.

## Canonical columns recognized by the parser

- [Origin](#origin)
- [Destination](#destination)
- [menuOption menuTarget objectID](#menuoption-menutarget-objectid)
- [Skills](#skills)
- [Items](#items)
- [Quests](#quests)
- [Duration](#duration)
- [Display info](#display-info)
- [Consumable](#consumable)
- [Wilderness level](#wilderness-level)
- [Varbits](#varbits)
- [VarPlayers](#varplayers)

Other columns (for example, `menuOption menuTarget objectID`) may appear and are used by other layers (e.g., UI/interaction) but are ignored by the pathfinding parser.

### Origin

- Format: `x y plane` (three integers) or empty
- Meaning: starting tile for object-based transports.
- Special: an empty Origin marks a permutation row that pairs with every row that has an Origin and an empty Destination (see [Two-way and permutation generation](#two-way-and-permutation-generation)).
- Example: `3097 3107 0`

### Destination

- Format: `x y plane` (three integers) or empty
- Meaning: target tile of the transport/teleport.
- Special: an empty Destination marks a permutation row that pairs with every row that has a Destination and an empty Origin. (see [Two-way and permutation generation](#two-way-and-permutation-generation)).
- Example: `3221 3218 0`

### menuOption menuTarget objectID

- Format: The in-game text displayed in the context menu. The ID is the object ID as given as either a gameobject ID, Wall ID, or Ground object ID (e.g., `Open Door 9398` or `Travel Spirit-tree 8355`).

### Skills

- Format: one or more `LEVEL SkillName` tokens separated by `;`.
  - single: `25 Magic`
  - multiple: `75 Construction;83 Farming`
- Notes (parser constraints):
  - Use exactly one space between the level and the skill name (e.g., `83 Farming`).
  - Skill names must match the client’s enum names exactly (case/spacing), e.g., `Magic`, `Construction`.

### Items

- Format: expressions describing required items. Supported patterns:
  - OR group (any of): `ID=qty||ID2=qty` (or single `|`) — example: `3853=1||3855=1`.
  - AND group (all required): `NAME=qty&&NAME2=qty` (or single `&`) — example (runes): `AIR_RUNE=3&&FIRE_RUNE=1&&LAW_RUNE=1`.
  - Single token: `8013=1` or `EQUIPMENT_NAME=1`.
  - There are two ways of selecting items:
    - by numeric item ID (e.g., `3853=1` for Games necklace)
    - by named constant recognized by the client (e.g., `LAW_RUNE=1`). These names are created in the `ItemVariations.java` file and are used to combine multiple items with the same properties (e.g., runes that count as the same type, swords with slash, crossbows).
- Parsing specifics:
  - Spaces are removed and the whole token string is uppercased before parsing (item names are effectively case-insensitive).
  - `&&` and `||` are normalized to `&` and `|` respectively; both forms are accepted.
  - Within an OR group, if quantities differ across alternatives, the parser uses the maximum quantity of the group. Prefer keeping quantities consistent within an OR group.
  - Left side of `=` can be a numeric item id or a named constant recognized by the client (e.g., `LAW_RUNE`).
  - Equipment/slot tokens: some rows use named slot tokens (for example `HEADSLOT`, `CAPESLOT`, `CAPESLOT`) found in `ItemVariations` to check equipment slots rather than inventory items. These appear as `HEADSLOT=0` to require an empty head slot, and can be combined with `&` to require multiple slot states (e.g., `HEADSLOT=0&CAPESLOT=0`).


### Quests

- Format: one or more quest names separated by `;` (e.g., `Plague City;Song of the Elves`).
- Notes: quest names must match the client’s `Quest` enum names exactly. Avoid leading/trailing spaces around names.

### Duration

- Format: integer (commonly small: `1`, `4`, `23`, `5`).
- Meaning: UI/animation duration or travel time in game ticks (treated as an opaque integer by the pathfinder).
- Special: teleports are forced to have a minimum duration of `1` even if left `0` or empty, so their cost isn’t computed by walking distance.

### Display info

- Format: free text label used by the UI (e.g., `Varrock Teleport`, `Burthorpe Games Room Minigame Teleport`, `ZANARIS`).
- Meaning: human-readable description or selection label.

Notes: display labels sometimes include an index or letter prefix used by in-game menus (for example `1: Emir's Arena` or `A: Warriors' Guild`). These prefixes are the shortcut keys used in the in-game teleport menu and may not be shown as actual UI text.

### Consumable (flag)

- Format: `T`, `F`, `yes`, or empty.
- Meaning: indicates whether using the transport consumes an item/charge.
- Notes: the parser treats `T` and `yes` (case-insensitive) as true; anything else (including `F` or empty) is false.

### Wilderness level

- Format: integer (often `0` or `20`).
- Meaning: wilderness restriction or indicator. Kept as an integer; semantics are enforced at runtime.

### Varbits

- Format: semicolon-separated varbit expressions. Examples:
  - equality: `4070=0`
  - greater-than: `10032>0`
  - bitmask: `4560&2`
  - with at-sign: `892@30`. This varbit represents a real-time countdown in minutes (wall-clock minutes, not in-game minutes) (e.g., the Lumbridge Home Teleport timer).
- Meaning: conditions read from client state. Each clause is parsed against supported operators and must be of the form `ID<op>VALUE` with numeric `ID` and `VALUE`.

### VarPlayers

- Format: semicolon-separated expressions (same operators as [Varbits](#varbits)), e.g., `888@20;4560&2`.
- Meaning: additional player/state conditions. Each clause must be `ID<op>VALUE` with numeric `ID` and `VALUE`.

Notes: VarPlayers are used across many transport files (not just spells) — for example in quetzals, minigames, and teleportation files — to encode additional player-specific state conditions.

## Permutation generation

Instead of needing to list every possible origin-destination pair for certain transport types (e.g., fairy rings, spirit trees, teleportation spells), the parser supports a permutation-style format using empty Origin or Destination fields.

Use rows with a concrete `Origin` and empty `Destination` to declare “all of the following destinations are reachable from this origin” (e.g., the spirit trees or fairy rings).

```csv
# Origin	Destination	menuOption menuTarget objectID	Skills	Quests	Varbits	Duration	Display info
2412 4434 0		Configure Fairy ring 29560				5	
2996 3114 0		Configure Fairy ring 29495				5	
	2571 2956 0					5	A K S
	2503 3636 0					5	A L P
	3597 3495 0					5	A L Q
```


Use rows with empty `Origin` and a concrete `Destination` to declare that “this transport can be used from any origin” (e.g., teleportation spells).

```csv
# Destination	Items	Skills	Quests	Duration	Display info	Wilderness level	Varbits	VarPlayers
3221 3218 0				23	Lumbridge Home Teleport	20	4070=0	4560=0;892@30
```

The parser pairs every such origin row with every destination row in the same file type to produce edges, skipping pairs whose origin and destination are equal or closer than a small per-type radius threshold. Current thresholds (tiles) by type: Gnome gliders: 6; Hot air balloons: 7; Magic mushtrees: 5; Spirit trees: 5; others default to 0.

## Maintenance notes

- Keep header column names exactly as listed; casing and spacing matter.
- Header names should match the parser's expected text (casing and spacing matter). Leading `#` on the header is optional and column order does not matter because the parser maps by header text.
- When adding new rows, keep fields in the canonical order above even if many are left empty.
- Use `||`/`|` for item alternatives and `&&`/`&` for item/rune conjunctions; both are supported by the parser.
- Preserve varbit and varplayer token syntax; runtime code interprets operators and values.
