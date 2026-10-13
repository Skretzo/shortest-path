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

`path` and `query` take a `target`: a `WorldPoint`, a packed `Integer` point
(`WorldPointUtil.packWorldPoint`), or a `Set` of them. With several targets, one search finds
whichever target is cheapest to reach.

## `path`

Shows a path, replacing the current one.

| Key | Type | Meaning |
| --- | --- | --- |
| `start` | `WorldPoint` or packed `Integer` | Where the path starts. Defaults to the player's location. |
| `target` | [target or `Set` of targets](#targets) | Where the path goes. |
| `config` | `Map<String, Object>` | Optional [setting overrides](#setting-overrides). |

- Only `config`, without `start` or `target`: the overrides are applied and nothing else changes.
- No `target`, or an empty `Set`, while a path is shown: the path is recalculated from `start`
  towards the same targets.
- A new `target` replaces the shown targets.
- If any point in `target` is invalid, the path is left as it was. Setting overrides in the same
  message still apply.

```java
Map<String, Object> data = new HashMap<>();
data.put("start", new WorldPoint(3164, 3483, 0));
data.put("target", new WorldPoint(2834, 3271, 0));
eventBus.post(new PluginMessage("shortestpath", "path", data));
```

### Setting overrides

`config` overrides the user's settings until the next `clear`. Each `path` message with a non-empty
`config` replaces the previous overrides. Valid keys:

- `boolean`: `avoidWilderness`, `collisionAwareBlockedTargets`, `drawClickPoints`,
  `drawCollisionMap`, `drawMap`, `drawMinimap`, `drawTiles`, `drawTransports`,
  `highlightBankPickupItems`, `highlightInventoryItems`, `highlightSpellbookSpells`,
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
| `target` | [target or `Set` of targets](#targets) | Required. |

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
A query without targets, or with an invalid target, is answered at once with `reason` `INVALID`.

```java
Map<String, Object> data = new HashMap<>();
data.put("id", "falador");
data.put("target", new WorldPoint(2965, 3380, 0));
eventBus.post(new PluginMessage("shortestpath", "query", data));

@Subscribe
public void onPluginMessage(PluginMessage event)
{
	if ("shortestpath".equals(event.getNamespace()) && "result".equals(event.getName())
		&& "falador".equals(event.getData().get("id")))
	{
		int cost = (Integer) event.getData().get("cost");
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
| `target` | `List<WorldPoint>` | Its targets; only when `set`. |
| `config` | `Map<String, Object>` | The active setting overrides, when there are any. |

## `clear`

Removes the shown path and every setting override.

```java
eventBus.post(new PluginMessage("shortestpath", "clear", new HashMap<>()));
```

## `transports`

With the `postTransports` setting (or override) on, Shortest Path posts a `transports` message
whenever the shown path finishes calculating. It has four lists of the same length, one entry per
transport on the path: `origin` and `destination` (`List<WorldPoint>`), and `objectInfo` and
`displayInfo` (`List<String>`).
