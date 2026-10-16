# Cross-plugin communication

Other plugins use Shortest Path through RuneLite's
[`PluginMessage`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/PluginMessage.java)
event. Every message uses the namespace `shortestpath`. The message name picks the action, and the
data map carries its arguments:

```java
eventBus.post(new PluginMessage("shortestpath", "path", data));
```

Plugin hub plugins can't share classes, so all data uses JDK and RuneLite types: `WorldPoint`,
`Integer`, `String`, `Boolean`, `Map`, `List`, `Set`, `Color`.

| Message | Direction | Purpose |
| --- | --- | --- |
| [`path`](#path) | to Shortest Path | Show a path, optionally overriding settings |
| [`query`](#query-and-result) | to Shortest Path | Find a path without showing it |
| [`result`](#query-and-result) | from Shortest Path | The answer to a `query` |
| [`getTarget`](#gettarget-and-currenttarget) | to Shortest Path | Ask what the shown path leads to |
| [`currentTarget`](#gettarget-and-currenttarget) | from Shortest Path | The answer to a `getTarget` |
| [`clear`](#clear) | to Shortest Path | Remove the shown path and setting overrides |
| [`transports`](#transports) | from Shortest Path | The transports on the shown path, when enabled |

## Targets

`path` and `query` take a `target`, which is one target or a `Collection` (`Set`, `List`, ...) of
targets. With several targets, one search finds whichever target is cheapest to reach.

A target is one of:

- a `WorldPoint`;
- a packed `Integer` point (`WorldPointUtil.packWorldPoint`);
- a **target map**, which can carry [item requirements](#item-requirements):

  ```java
  Map<String, Object> dig = new HashMap<>();
  dig.put("point", new WorldPoint(3240, 3225, 0)); // a WorldPoint or packed Integer
  dig.put("requirements", ItemID.SPADE);           // optional
  ```

  A target map without `requirements` is the same as a plain point. Other keys are ignored.

Plain points and target maps can be mixed in one collection. Giving the same point twice is fine
only when both copies have the same requirements, or neither does.

## Item requirements

A target's `requirements` lists the items the player must hold (inventory or worn) when they
arrive: a spade to dig, an emote clue's outfit, quest items. Item ids are plain `int`s, so
RuneLite's `ItemID` constants work directly.

| Value | Meaning | Example |
| --- | --- | --- |
| `Integer` | one of this item | `ItemID.SPADE` |
| `Map<Integer, Integer>` | any one of these items, at least that many | `Map.of(ItemID.COINS, 1000)` |
| any other `Collection<Integer>` | any one of these items | `Set.of(ItemID.SPADE, ItemID.ROPE)` |
| `List` of the above | every element | `List.of(ItemID.SPADE, Set.of(ItemID.ROPE, ItemID.KNIFE))` |

A top-level `List` means "all of these". A `List` *inside* a `List` is one "any of" group, like a
`Set`. Nesting stops there: a requirement is a list of groups, the player needs every group, and
any one item from a group is enough. Ids are matched exactly,
with no item variations added. To accept variants (charged, ornamented, ...), list them; RuneLite's
`ItemVariationMapping.getVariations(id)` gives a starting set:

```java
dig.put("requirements", new HashSet<>(ItemVariationMapping.getVariations(ItemID.RING_OF_WEALTH)));
```

Requirements are only about items. Skills, quests and other unlocks can't be fixed by a detour, so
callers check those themselves.

### How requirements change the route

When the search starts, Shortest Path checks each target's requirement against what the player
holds, and against what they hold plus the bank:

| Status | When | Route |
| --- | --- | --- |
| `CARRIED` | the inventory and worn items cover it | the same as a target without requirements |
| `BANK` | it is only covered once the bank is added | through a bank first, then to the target |
| `UNKNOWN` | neither covers it | only used when no target can be completed |

- **The bank detour is part of the search.** The search picks whichever bank makes the whole trip
  cheapest. It also weighs the detour against your other targets: a farther target whose items are
  carried beats a closer one that needs a bank trip, when that is cheaper overall. At the bank, the
  path's "Pick up" text lists the target's items and the bank highlights them.
- **The bank is "as last seen".** A bank that hasn't been opened this session counts as empty, the
  same as for item-gated transports. Storage Shortest Path doesn't track (tool leprechaun, STASH
  units, house storage) also counts as not owned. That is why the status is `UNKNOWN` and not
  "missing".
- **`UNKNOWN` targets are a last resort.** While any target is `CARRIED`, `BANK`, or has no
  requirement, `UNKNOWN` targets are not used. If every target is `UNKNOWN`, the search routes as
  if there were no requirements, so the player still sees the way to their destination. The shown
  path then reads "Not found in inventory or bank: ..." on the player's tile, adding "(bank not
  checked)" when the bank hasn't been opened yet.
- **Bank visits happen even with "Include path to bank" off**, because the player can't complete
  the target without one. They don't unlock teleports from the bank: which teleport items a route
  may use still follows the user's settings.
- **Items are checked once, when the search starts.** Items a route uses up on the way (coins for
  a transport) aren't tracked, and the path isn't recalculated when the inventory changes.
- A blocked target's nearby fallback tiles (see `unreachableTargetDistance`) need the same items as
  the target.
- Requirements are ignored while sailing.

## `path`

Shows a path, replacing the current one.

| Key | Type | Meaning |
| --- | --- | --- |
| `start` | `WorldPoint` or packed `Integer` | Where the path starts. Defaults to the player's location. |
| `target` | [target](#targets) or `Collection` of targets | Where the path goes. |
| `config` | `Map<String, Object>` | Optional [setting overrides](#setting-overrides). |

- Only `config`, without `start` or `target`: the overrides are applied and nothing else changes.
- No `target`, or an empty collection, while a path is shown: the path is recalculated from `start`
  towards the same targets, keeping their requirements.
- A new `target` replaces the shown targets and their requirements.
- A malformed `target` is logged and the path is left as it was. Setting overrides in the same
  message still apply.

```java
Map<String, Object> data = new HashMap<>();
data.put("start", new WorldPoint(3164, 3483, 0));
data.put("target", new WorldPoint(2834, 3271, 0));
eventBus.post(new PluginMessage("shortestpath", "path", data));
```

A path to a dig spot that needs a spade, or to an NPC that needs nothing, whichever is cheaper:

```java
Map<String, Object> dig = new HashMap<>();
dig.put("point", new WorldPoint(3240, 3225, 0));
dig.put("requirements", ItemID.SPADE);

Map<String, Object> data = new HashMap<>();
data.put("target", Set.of(dig, new WorldPoint(3260, 3230, 0)));
eventBus.post(new PluginMessage("shortestpath", "path", data));
```

### Setting overrides

`config` overrides the user's settings until the next `clear`. Each `path` message with a non-empty
`config` replaces the previous overrides. Valid keys:

- `boolean`: `avoidWilderness`, `collisionAwareBlockedTargets`, `drawClickPoints`,
  `drawCollisionMap`, `drawMap`, `drawMinimap`, `drawTiles`, `drawTransports`,
  `filterBankPickupItems`, `highlightBankPickupItems`, `highlightInventoryItems`, `highlightSpellbookSpells`,
  `includeBankPath`, `postTransports`, `respawnPrifddinas`, `showBankPickupInfo`,
  `showTeleportPulse`, `showTransportInfo`, `showUnreachableText`,
  `unlockCanoeAxe`, `unlockDragontoothPassage`, `unlockMorUlRek`, `unlockXericsHonour`, `usePoh`,
  `usePohFairyRing`, `usePohObelisk`, `usePohSpiritTree`, `useSailingMoves`, and each transport
  type's `use...` key: `useAgilityShortcuts`, `useBoats`, `useCanoes`, `useCharterShips`,
  `useFairyRings`, `useGnomeGliders`, `useGrappleShortcuts`, `useHotAirBalloons`,
  `useMagicCarpets`, `useMagicMushtrees`, `useMinecarts`, `useQuetzals`, `useSeasonalTransports`,
  `useShips`, `useSpiritTrees`, `useTeleportationLevers`, `useTeleportationMinigames`,
  `useTeleportationPortals`, `useTeleportationSpells`, `useTeleportationSpellsHome`,
  `useWildernessObelisks`
- `int`: `costBankVisit`, `costConsumableTeleportationItems`, `currencyThreshold`,
  `recalculateDistance`, `tileCounterStep`, `unreachableTargetDistanceThreshold`, and each
  transport type's `cost...` key (`costBoats`, `costFairyRings`, ...)
- `Color`: `colourBankPickupHighlight`, `colourCollisionMap`, `colourPath`,
  `colourPathCalculating`, `colourPathUnreachable`, `colourTeleportPulse`, `colourText`,
  `colourTransports`
- `String` naming an option as the settings panel shows it:
  - `useTeleportationItems`: `None`, `Inventory`, `Inventory (perm)`, `Inventory and Bank`,
    `Inventory and Bank (perm)`, `Unlocked`, `Unlocked (perm)`, `All`, `All (perm)`
  - `pohJewelleryBoxTier`: `None`, `Basic`, `Fancy`, `Ornate`
  - `showTileCounter`: `Disabled`, `Travelled`, `Remaining`
  - `pathStyle`: `Tiles`, `Lines`

```java
Map<String, Object> configOverride = new HashMap<>();
configOverride.put("colourPath", new Color(255, 255, 0));
configOverride.put("showTileCounter", "Disabled");
configOverride.put("useTeleportationItems", "Inventory (perm)");
data.put("config", configOverride);
```

## `query` and `result`

`query` finds a path without showing it, and answers with a `result` message carrying the same
`id`. Queries always use the legacy pathfinder, whichever one the user picked. A new shown path
cancels queries still waiting.

| `query` key | Type | Meaning |
| --- | --- | --- |
| `id` | any | Echoed in the `result`, to match answers to questions. |
| `start` | `WorldPoint` or packed `Integer` | Defaults to the player's location. |
| `target` | [target](#targets) or `Collection` of targets | Required. |

| `result` key | Type | Meaning |
| --- | --- | --- |
| `id` | any | The query's `id`. |
| `reached` | `Boolean` | Whether the path reaches a target (or a tile close enough to one). |
| `start` | `WorldPoint` | Where the search started. |
| `target` | `WorldPoint` | The target the path leads to (when not reached: one of the targets). |
| `path` | `List<WorldPoint>` | The path's tiles. |
| `closest` | `WorldPoint` | The closest tile reached. |
| `cost` | `Integer` | The path's cost in ticks; -1 without a path. |
| `transports` | `List<Map>` | One map per transport used: `origin`, `destination` (`WorldPoint`), `objectInfo`, `displayInfo` (`String`). |
| `reason` | `String` | `TARGET_REACHED`, `SEARCH_EXHAUSTED`, `CUTOFF_REACHED` or `CANCELLED`; for a query that didn't run: `INVALID`, `CANCELLED`, `SHUTDOWN` or `ERROR`. |
| `bankVisited` | `Boolean` | Whether the path visits a bank. |
| `targetStatus` | `String` | The reached target's [status](#how-requirements-change-the-route): `CARRIED`, `BANK` or `UNKNOWN`. `null` when it has no requirement or nothing was reached. |
| `missingItems` | `List<List<Integer>>` | For an `UNKNOWN` target, the "any of" groups not found, as item ids; otherwise empty. |

A query without targets, or with a malformed target or requirement, is answered at once with
`reason` `INVALID`.

```java
Map<String, Object> data = new HashMap<>();
data.put("id", "dig-step");
data.put("target", dig);
eventBus.post(new PluginMessage("shortestpath", "query", data));

@Subscribe
public void onPluginMessage(PluginMessage event)
{
	if ("shortestpath".equals(event.getNamespace()) && "result".equals(event.getName())
		&& "dig-step".equals(event.getData().get("id")))
	{
		if ("UNKNOWN".equals(event.getData().get("targetStatus")))
		{
			// The player needs a spade first
		}
	}
}
```

## `getTarget` and `currentTarget`

`getTarget` asks what the shown path leads to. It changes nothing and starts no search. The answer
is a `currentTarget` message:

| `currentTarget` key | Type | Meaning |
| --- | --- | --- |
| `id` | any | The `getTarget` message's `id`. |
| `set` | `Boolean` | Whether a path is shown. |
| `start` | `WorldPoint` | Where it starts; only when `set`. |
| `target` | `List` | Its targets: a `WorldPoint` for a target without requirements, otherwise a target map whose `requirements` is a `List` with one `Map<Integer, Integer>` of item id to quantity per group. Sending this list back as a `path` or `query` target gives the same targets. Only when `set`. |
| `config` | `Map<String, Object>` | The active setting overrides, when there are any. |

## `clear`

Removes the shown path, its targets and their requirements, and every setting override.

```java
eventBus.post(new PluginMessage("shortestpath", "clear", new HashMap<>()));
```

## `transports`

With the `postTransports` setting (or override) on, Shortest Path posts a `transports` message
whenever the shown path finishes calculating. It has four lists of the same length, one entry per
transport on the path: `origin` and `destination` (`List<WorldPoint>`), and `objectInfo` and
`displayInfo` (`List<String>`).
