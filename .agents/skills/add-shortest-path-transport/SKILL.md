---
name: add-shortest-path-transport
description: Add or verify shortest-path RuneLite plugin transports from WorldPoint coordinates, including choosing the correct TSV, finding object IDs and menu actions, modelling availability requirements conservatively, validating both directions, and preparing a focused pull request. Use for missing path connections, transport-coordinate reports, and transport TSV changes.
---

# Add a shortest-path transport

Work from the `shortest-path` repository. Preserve unrelated work and inspect
the current branch and diff before editing.

## Correctness invariant

Every route presented to a player must be executable to completion using the
player state known to the pathfinder. Prefer false negatives over false
positives: omitting a usable transport is better than routing a player to an
interaction they cannot take.

Fail closed when a transport has a known unlock or requirement that cannot be
modelled reliably. Encode a verified requirement, use a stricter condition that
is sufficient to guarantee access, or omit the transport until its availability
can be represented. Never leave a known gate off merely because its exact state
is not visible to the client.

## 1. Normalize and search

Convert pasted points to `x y plane`. For example:

```text
WorldPoint(x=2445, y=3416, plane=1) -> 2445 3416 1
WorldPoint(x=2446, y=3415, plane=0) -> 2446 3415 0
```

Treat a trailing integer as the duration in game ticks unless the user says
otherwise. Search both endpoints before adding anything:

```bash
rg -n '2445 3416 1|2446 3415 0' src/main/resources/transports
```

Read matching rows and their surrounding section. If the directed coordinate
pair already exists, compare its action, object ID, requirements, and duration
with the supplied or verified facts. Update the existing row when that evidence
establishes a missing or stale field; otherwise report the discrepancy. Never
add a duplicate. The example above already exists in `transports.tsv` in both
directions, but its rows must still be checked against the supplied duration.

## 2. Choose the TSV

Use the mechanism, not the location, to select a file. Confirm the authoritative
resource mapping in `src/main/java/shortestpath/transport/TransportType.java`.

- Use `agility_shortcuts.tsv` for obstacles governed by the Agility shortcut
  setting or an Agility/Ranged/Strength requirement.
- Use the dedicated file for boats, canoes, ships, minecarts, fairy rings,
  spirit trees, portals, spells, items, and other named transport types.
- Use `transports.tsv` for ordinary doors, gates, stairs, ladders, dungeon
  entrances, crevices, and other general traversal edges.

Read the target file's header and nearby rows before constructing a row. Headers
differ between files; never paste a row using remembered column positions.
`docs/Transport-TSV-format.md` is the schema reference.

## 3. Confirm the interaction anchor

The TSV's `Origin` is the player traversal tile, not necessarily the object's
placement tile. Confirm the exact menu option, object name, and object ID for
each direction. Never infer an ID or action from coordinates alone.

The quickest manual source is RuneLite developer mode's object-ID overlay. For
an offline cache lookup, use the sibling `shortest-path-tooling` checkout:

```bash
cd ../shortest-path-tooling
python3 scripts/maintenance.py cache  # only if cache/ and keys.json are absent or stale
./gradlew test \
  --tests shortestpath.dump.TileObjectProbeTest \
  -Dtile.probe=true \
  '-Dtile.probe.boxes=2442 2448 3412 3419' \
  -Dtile.probe.plane=1
```

Probe a small box around an endpoint, normally two or three tiles beyond it.
Run once per plane when the endpoints use different planes. The test reports
coordinates, `id`, `name`, `ops`, varbit/varp data, and transformed children.
If Gradle hides standard output, read it from:

```bash
rg -n 'id=|name=|ops=' \
  build/test-results/test/TEST-shortestpath.dump.TileObjectProbeTest.xml
```

For transformed objects, reuse the Java helpers in
`shortest-path-tooling/src/test/java/shortestpath/dump/CacheUtils.java`:

- `collectMultiLocParents` maps a known child object ID to placed parent
  definitions and exposes their controlling varbit/varp.
- `findObjectsByVarbit` maps a known varbit back to controlled object
  definitions.
- `collectLocationsByObjectId` maps candidate IDs to their world placements.

These helpers cover object-definition transforms, not every gameplay unlock.
Use this evidence order for availability requirements:

1. Search RuneLite source for named `VarbitID`/`VarPlayerID` constants, existing
   `getVarbitValue`/`getVarpValue` call sites, and code modelling the same unlock.
2. Inspect cache object transforms and their controlling varbit/varp.
3. Inspect relevant client-script references when the cache definition is not
   sufficient.
4. Use RuneLite's varbit/varplayer logger before, during, and after the
   interaction or unlock, and reproduce the observation.

The server does not transmit every gameplay condition. State is usually sent
when the client needs it for observable behaviour such as UI, object transforms,
menu options, animations, or map state; a server-authoritative eligibility
check may expose no usable varbit. Absence from RuneLite or the cache is not
proof that an interaction is unconditional, and an unrelated changing varbit is
not proof that it is the gate.

When a gate is known but no reliable client-side signal exists, use a verified
quest, skill, item, or stricter client-visible state that guarantees access. If
no such sufficient condition exists, do not add the transport yet.

Write the object-info cell as the exact in-game interaction followed by the
numeric ID, for example `Climb-down Staircase 16677`. Compare nearby existing
rows when an object has transforms or child actions. If neither the tooling
cache nor RuneLite evidence is available, stop and state what evidence is
missing; do not guess.

## 4. Add the directed edges

Use tabs and preserve every empty field required by the target header. Add the
row near related coordinates or under the matching area comment.

A row is one-way. Add a reverse row only when the connection is confirmed to be
two-way. Verify the reverse interaction separately: it may use a different menu
option, name, object ID, origin tile, requirement, or duration.

Do not invent requirements. Carry Skills, Items, Quests, Varbits, and VarPlayers
only from user-provided or verified game evidence. Use the supplied duration;
otherwise establish it in-game or follow a directly comparable existing row.
Before finishing, consider every known blocker to taking the edge: skill level,
quest stage, inventory/equipment, payment, diary or area unlock, construction
state, varbit/varplayer state, and direction-specific differences. The encoded
conditions may be stricter than the game's true minimum, but must not admit a
player known to be unable to complete the interaction.

## 5. Validate the smallest relevant surface

From `shortest-path`, run:

```bash
python3 scripts/check_tsv.py ./src/main/resources
./gradlew test --tests shortestpath.transport.TransportDataLintTest
git diff --check
git diff -- src/main/resources/transports
```

Check that only intended rows changed, both directions are present when needed,
the object-info cells are complete, no duplicate edge was introduced, and every
known availability gate is represented by a necessary or conservatively
sufficient condition.

For a cache-backed audit of all committed transport anchors, optionally run
from `shortest-path-tooling` after preparing its cache:

```bash
python3 scripts/maintenance.py validate --drift
```

## 6. Commit and open a PR only when requested

Do not infer permission to commit, push, or create a PR from a request to edit
or validate data. When the user requests a PR:

1. Inspect `git status --short --branch`, `git remote -v`, and the branch base.
2. Keep unrelated commits and changes out of the PR. Create a clean feature
   branch from the current upstream base when necessary.
3. Commit only the intended transport file with the user's requested message,
   or a concise transport-specific message when none was supplied.
4. Push to the contributor's fork; identify it from remote URLs instead of
   assuming `origin` is the fork.
5. Open the PR against the upstream repository and base branch. Include a short
   summary plus the exact validation commands run. Decode every introduced
   varbit, varplayer, or object ID whose meaning is not obvious from the diff:
   give its plain-language meaning and the evidence used to identify it. For an
   object ID, name the object, menu action, and relevant location or direction.
   For a varbit/varplayer condition, explain what state it represents and what
   the predicate permits; do not merely repeat the numeric expression.
6. Return the commit hash, PR link, and check results.

Do not modify generated collision data for an ordinary TSV-only transport.
