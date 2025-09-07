# Transport TSV format

This document describes the canonical column set and allowed values used across all files in `src/main/resources/transports/` (loaded by `Transport.loadAllFromResources`). It reflects the actual parser behavior in `shortestpath.Transport`.

All transport TSV files share the same structure: rows are tab-separated fields, comment lines start with `#`, and many columns are optional depending on the transport type. The focus below is on columns and permitted value formats rather than file-specific variants.

## Quick rules

- Header: the first line is the header and may either be a commented header starting with `#` (optionally followed by a single space) or a bare header line (no `#`). Both forms are supported; the header defines tab-separated column names. Unknown columns are ignored by the pathfinding parser.
- Header-driven mapping: column order does not matter — the parser maps fields by the header text.
- Rows: split on tabs; ignore lines beginning with `#` (other than a commented header) and blank lines.
- Coordinate fields (Origin/Destination) are three integers separated by single spaces: `x y plane` (example: `3221 3218 0`).
- Empty cells are preserved. For permutation-style transports (e.g., fairy rings), leave Origin or Destination empty to signal “permutation” (details below).
- Optional columns may be empty; keep column positions consistent within a file.

## Canonical columns recognized by the parser

- Origin
- Destination
- Skills
- Items
- Quests
- Duration
- Display info
- Consumable
- Wilderness level
- Varbits
- VarPlayers

Other columns (for example, `menuOption menuTarget objectID`) may appear and are used by other layers (e.g., UI/interaction) but are ignored by the pathfinding parser.

### Origin

- Format: `x y plane` (three integers) or empty
- Meaning: starting tile for object-based transports.
- Special: an empty Origin marks a permutation row that pairs with every row that has an Origin and an empty Destination (see “Two-way and permutation generation”).
- Example: `3097 3107 0`

### Destination

- Format: `x y plane` (three integers) or empty
- Meaning: target tile of the transport/teleport.
- Special: an empty Destination marks a permutation row that pairs with every row that has a Destination and an empty Origin.
- Example: `3221 3218 0`

### menuOption menuTarget objectID

- Format: free text describing object interaction, frequently ending with an integer object id, e.g. `Open Door 9398` or `Travel Spirit tree 8355`.
- Meaning: used for object-based transports (doors, ladders, spirit trees). Treat the whole cell as a single string; no fixed token layout is enforced by the pathfinding parser.

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
- Parsing specifics:
  - Spaces are removed and the whole token string is uppercased before parsing (item names are effectively case-insensitive).
  - `&&` and `||` are normalized to `&` and `|` respectively; both forms are accepted.
  - Within an OR group, if quantities differ across alternatives, the parser uses the maximum quantity of the group. Prefer keeping quantities consistent within an OR group.
  - Left side of `=` can be a numeric item id or a named constant recognized by the client (e.g., `LAW_RUNE`).
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
- Meaning: UI/animation duration or travel time in ticks (treated as an opaque integer by the pathfinder).
- Special: teleports are forced to have a minimum duration of `1` even if left `0` or empty, so their cost isn’t computed by walking distance.

### Display info

- Format: free text label used by the UI (e.g., `Varrock Teleport`, `Burthorpe Games Room Minigame Teleport`, `ZANARIS`).
- Meaning: human-readable description or selection label.

Notes: display labels sometimes include an index or letter prefix used by in-game menus (for example `1: Emir's Arena` or `A: Warriors' Guild`). These prefixes are part of the label and preserved by the parser.

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
  - with at-sign: `892@30`
- Meaning: conditions read from client state. Each clause is parsed against supported operators and must be of the form `ID<op>VALUE` with numeric `ID` and `VALUE`.

### VarPlayers

- Format: semicolon-separated expressions (same operators as Varbits), e.g., `888@20;4560&2`.
- Meaning: additional player/state conditions. Each clause must be `ID<op>VALUE` with numeric `ID` and `VALUE`.

Notes: VarPlayers are used across many transport files (not just spells) — for example in quetzals, minigames, and teleportation files — to encode additional player-specific state conditions.

## Two-way and permutation generation

- One-way vs two-way:
  - A single row with `Origin=A` and `Destination=B` is one-way. If the reverse path is valid, add a second row with `Origin=B` and `Destination=A`—the parser does not auto-generate simple reversals.
  - Rows where `Origin` equals `Destination` are ignored.
- Permutation transports (e.g., fairy rings, spirit trees, gliders, mushtrees):
  - Use rows with a concrete `Origin` and empty `Destination` to declare “all outgoing edges from this origin”.
  - Use rows with empty `Origin` and a concrete `Destination` to declare “all incoming edges to this destination”.
  - The parser pairs every such origin row with every destination row in the same file type to produce edges, skipping pairs whose origin and destination are equal or closer than a small per-type radius threshold.
  - Current thresholds (tiles) by type: Gnome gliders: 6; Hot air balloons: 7; Magic mushtrees: 5; Spirit trees: 5; others default to 0.

## Value syntax summary

- Coordinate triple: three integers separated by single spaces, e.g., `x y plane`.
- Item tokens: `IDENT=NUM` where IDENT is an item id (digits) or item name constant; groups: `A|B` (OR) or `A&B` (AND). Double forms `||`/`&&` are accepted and normalized.
- Requirement lists: use `;` to separate multiple skill/quest/varbit/varplayer clauses.
- Varbit/VarPlayer tokens: allow `=`, `>`, `@`, and `&` operators; do not attempt to evaluate in the TSV.

## Parsing guidance and gotchas

- Keep skill lines strictly `LEVEL SkillName` with a single space; names must match the client enums exactly.
- Prefer consistent quantities within OR groups in `Items`; otherwise, the maximum is used.
- Avoid leading/trailing spaces in `Quests`, `Varbits`, and `VarPlayers` entries.
- Unknown columns are ignored by the pathfinding parser, so you can include UI/interaction columns without affecting routing.

## Examples

- Teleport spell row (fields tab-separated):
  - Destination: `3213 3424 0`
  - Items: `AIR_RUNE=3&&FIRE_RUNE=1&&LAW_RUNE=1`
  - Skills: `25 Magic`
  - Duration: `4`
  - Display info: `Varrock Teleport`
  - Wilderness level: `20`
  - Varbits: `4070=0`

- Item teleport (Games necklace):
  - Destination: `2898 3553 0`
  - Items: `3853=1||3855=1||3857=1` (any of these)
  - Display info: `Games necklace: Burthorpe`
  - Consumable: `T`

## Maintenance notes

- Keep header column names exactly as listed; casing and spacing matter.
- Header names should match the parser's expected text (casing and spacing matter). Leading `#` on the header is optional and column order does not matter because the parser maps by header text.
- When adding new rows, keep fields in the canonical order above even if many are left empty.
- Use `||`/`|` for item alternatives and `&&`/`&` for item/rune conjunctions; both are supported by the parser.
- Preserve varbit and varplayer token syntax; runtime code interprets operators and values.
