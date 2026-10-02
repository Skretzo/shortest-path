# Contributing

Thanks for helping keep the routing data accurate. This guide covers the
three ways people contribute here — pick the section that fits you:

- [I want to fix wrong/missing routes or teleports](#fixing-data) — most
  contributions are small edits to `.tsv` files; no Java needed.
- [I want to change plugin code](#changing-code) — Java, the standard
  RuneLite plugin workflow.
- [I'm driving an AI agent](#contributing-with-an-ai-agent) — agents work
  well on this repo *if* you hold them to the evidence rules below.

Before writing code, skim
[docs/Transport-TSV-format.md](docs/Transport-TSV-format.md) — every route
the plugin knows is a row in a tab-separated file, and that doc defines
the columns. [docs/Useful-Tools.md](docs/Useful-Tools.md) lists the map
viewers and databases contributors use to check coordinates.

## Fixing data

The data lives in `src/main/resources/`:

- `transports/` — doors, ladders, boats, teleports, shortcuts: anything
  that moves you between tiles
- `destinations/` — the named places you can path to
- `collision-map.zip` — **generated**, don't edit it by hand

Typical fix:

1. Reproduce the problem — find the row that matches the in-game object
   or teleport (search by name or coordinates).
2. Change it, then verify your edit:

   ```bash
   python3 scripts/check_tsv.py      # column names and formats
   bash scripts/tsv-lint.sh          # every row has the right columns
   ./gradlew test                    # full pathfinder test suite
   ```

3. Open a PR — see [PR conventions](#pr-conventions).

### Rules of evidence

The plugin routes thousands of players; a wrong value sends them into
walls or claims impossible teleports. So:

- **Every number needs a source**: something you checked in-game, the
  [OSRS wiki](https://oldschool.runescape.wiki), or the game cache.
  "Seems about right" is how bad data gets in.
- **Requirement columns describe reality, not wishes.** If a door needs
  a key, a slash weapon, or quest progress, the row must say so —
  *and only so*. A missing requirement makes the plugin offer routes
  players can't take; an invented one hides routes they can.
- **`Quests` means *completed*.** If a route unlocks partway through a
  quest, don't list the quest — use a `Varbits` threshold instead (see
  the TSV doc) with the value confirmed in-game or from the cache.
- **Two-way routes need two rows.** The parser never generates a return
  trip.
- **If you can't verify a value, say so.** Open the PR with `Refs #NNN`
  in the description instead of `Fixes #NNN`, and write what you
  couldn't confirm. An honestly-partial fix gets merged; a guessed one
  gets reverted.
- **Never delete a requirement to make a route "work"** — that offers
  the transport to players who can't actually use it. If an unlock
  isn't visible to the client, note it in the PR; those are handled by
  a config toggle, not by dropping data.

### Finding the right values

- **In-game**: the OSRS wiki's map pins and
  [Explv](https://explv.github.io/) / [mejrs](https://mejrs.github.io/osrs)
  maps are the fastest way to get exact `x y plane` coordinates. RuneLite
  developer mode adds a Var Inspector (watches quest/varbit state) and
  `::getvarp`/`::getvarb` console commands.
- **Wiki**: fetch machine-readable wikitext instead of scraping HTML:
  `https://oldschool.runescape.wiki/api.php?action=parse&page=<Page>&prop=wikitext&format=json`
- **Deeper digging** (cache enums, scripts, collision tiles): the
  [shortest-path-tooling](https://github.com/osrs-pathfinding/shortest-path-tooling)
  repo has the dumpers and probes the maintainers use for exactly this.

## Changing code

Standard RuneLite plugin workflow — Java 11, Gradle wrapper:

```bash
./gradlew build   # compile + checks
./gradlew test    # pathfinder test suite
./gradlew run     # launch a dev client with the plugin bundled
```

Things reviewers look for:

- Changes to `Pathfinder`/`Transport` logic come with a test in
  `PathfinderTest.java` — the suite doubles as the regression corpus
  for routing edge cases.
- Data-loaded fields (`Varbits`, `Items`, …) already have parsers —
  check `transport/parser/` before adding syntax.
- Keep behavior honest about the game: the pathfinder should model what
  players can actually do, not what would be convenient.

## Contributing with an AI agent

Agents are a great fit for this repository — the data is declarative,
every file has a schema, and the test suite catches a lot — **but they
will confidently invent values if you let them.** The maintainer can
verify your PR, but a bad guess wastes a review round. Hold your agent
to this checklist:

**Tell it:**

1. Fix only the reported issue — one route, one requirement, one file
   section. No "while I'm here" edits.
2. Cite a source for every changed number (wiki, in-game check, cache
   script). If it can't cite one, it must not write the value.
3. Run `python3 scripts/check_tsv.py`, `bash scripts/tsv-lint.sh`, and
   `./gradlew test` before the PR. Tabs, not spaces — TSV means tabs.
4. Describe the expected in-game behavior in the PR description so a
   human can sanity-check it.

**Red flags — don't open the PR if your agent did any of these:**

- Added a requirement or duration it can't trace to a source
  (invented varbit numbers are the classic failure)
- Deleted a requirement row "because it couldn't verify it"
- Hand-edited `collision-map.zip` — it's generated data
- Produced a huge diff for a one-line bug
- Marked the PR `Fixes #NNN` for something only partially confirmed —
  partial work uses `Refs #NNN` plus a note about what's missing

A good agent contribution looks boring: one or two changed lines, a
source for each, tests green.

## PR conventions

- **One logical fix per PR.** Mixed diffs stall in review.
- **The PR title becomes the commit** (squash merge) — write it as a
  plain imperative sentence describing the player-visible change, e.g.
  "Gate slash webs in deep Wilderness on having a slash weapon".
- Use `Fixes #NNN` when the change fully resolves an issue, `Refs #NNN`
  when it's partial or unverifiable on your account.
- CI runs CSV lint, the full Java test suite, and a coordinate-diff
  preview of what changed — keep it green.
- Regenerated `collision-map.zip` artifacts and drive-by reformats are
  fine to leave to the maintainers' tooling.

## Getting help

- Questions about game values, or "is this really how the object
  works?" — ask in the [issue](../../issues) you're fixing or on
  [Discord](https://discord.gg/uX47xg8u3M).
- Unsure whether something is data or code? Open an issue; misfiled
  PRs cost you more time than a question.
