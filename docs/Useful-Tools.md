# Useful Tools

## maps

### Explv's Map

<https://explv.github.io/>

Especially handy for creating lines and areas that can then be copied and pasted into code.

## Mejrs's Old School RuneScape Map

<https://mejrs.github.io/osrs>

Contains beautiful object search functions

In the bottom right you will find an icon for a map where you can search by object name and ID.

In the bottom left there is a menu to add crowdsourced data layers to the map like agility shortcuts, transports, and teleports.
Although many of these transports might still be valid, the data was collected years ago and might be outdated.

A special mention goes to nomove layer which shows where the player cannot walk and the layer with pins for objects.

## Chisel

The tool server for the RuneScape Wiki with loads of useful information about the game.

<https://chisel.weirdgloop.org/>

### Minimal OSRS Item DB

<https://chisel.weirdgloop.org/moid/index.html>

### OSRS Varbits DB

<https://chisel.weirdgloop.org/varbs/index>

## Abextm cache viewer

<https://abextm.github.io/cache2/#/viewer>

## OSRS world

This site renders the cache data as close as possible to how the game does it.
You can see all the npcs models and animations.
This is great for finding game objects and their ids.

<https://osrs.world/>

## Oldschool Wiki

<https://oldschool.runescape.wiki/>

Over at the [Preferences -> Gadgets](https://oldschool.runescape.wiki/w/Special:Preferences#mw-prefsection-gadgets) you can find the option to `Display advanced data in infoboxes, such as item IDs.`
This shows you item IDs in the info boxes.

## Runelite

### RuneLite developer mode

Runelites developer mode gives you access to some debug tools. this includes:

- A wiget with the current player location in `x y plane` format
- A tile marker tool that shows location of the time in `x y plane` format when you hover over a tile
- A overlay that shows object ids of game-, floor- and wall objects
- A logger that shows varbit and varplayer changes

If you already have the shortest-path plugin checked out then you can run `./gradlew runelite` to launch a RuneLite instance with developer mode enabled.

### RuneLite Source files

The RuneLite source files are a great resource for finding IDs, names of quests, varbits, varplayers, and more.

<https://github.com/runelite/runelite/tree/master/runelite-api/src/main/java/net/runelite/api/gameval>

### Runelite report button plugin

The RuneLite report button plugin lets you select what to show on the report button next to the chat filter buttons. Normally it shows the `login timer`, but you can change it to show the current game tick. This is useful while estimating the duration of actions.

## Spreadsheet Editors

Editing the TSV files is a precise endeavor. Using a spreadsheet editor (such as LibreOffice Calc, Excel, or Google Sheets) fixes many common TSV errors and makes editing much less error-prone. Spreadsheet editors handle tab delimiters and quoting automatically, reducing the likelihood of formatting mistakes.

Just open the TSV file in your spreadsheet editor, make your edits, and export it back to TSV.

### LibreOffice Calc

When you use LibreOffice Calc, you can sort the files by a specific column, e.g. sorting by `Skills` to make it easier to find duplicates or missing entries. By default, the sorting is lexicographical, meaning `10 Agility` comes before `9 Agility`. To fix this, do the following:

- select the column you want to sort by
- go to `Data -> Sort...`
- Extend the selection
- in the window that pops up go to the `Options` tab
- check `enable natural sort`
