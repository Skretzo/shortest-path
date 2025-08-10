# Collision Map Extraction Workflow

This workflow automates extraction of the latest Old School RuneScape (OSRS) collision map using GitHub Actions.

## Steps Overview

1. **Download Latest Cache and Keys**  
   The script [`download-latest-cache.sh`](download-latest-cache.sh) fetches the most recent OSRS cache files and XTEA encryption keys from [archive.openrs2.org](https://archive.openrs2.org/). It determines the latest cache version by first downloading and sorting a list of available caches, then selecting the most recent one. The cache is then downloaded and unzipped to the `cache/` directory. The keys are also downloaded and saved as `keys.json` in the same directory.

2. **Patch Keys File**  
   After downloading, the `keys.json` file is patched to match the expected format for the dumper. Specifically, it replaces `"mapsquare"` with `"region"` and `"key"` with `"keys"` to ensure compatibility with the extraction tool.

3. **Prepare Runelite**  
   - The workflow clones the [runelite/runelite](https://github.com/runelite/runelite) repository, which provides the necessary codebase for cache reading and manipulation.
   - It then copies [`CollisionMapDumper.java`](CollisionMapDumper.java) into the Runelite cache module, adding custom logic for extracting collision data.
   - The [`pom.patch`](pom.patch) file is applied to the Runelite project’s Maven configuration, setting the correct main class and dependencies so the dumper can be built and run as a standalone JAR.

4. **Build Dumper JAR**  
   Using Maven, the workflow compiles the modified Runelite cache module and packages it into a single executable JAR file. This JAR includes all required dependencies, making it easy to run the dumper in the next step.

5. **Extract Collision Map**  
   The dumper JAR is executed, reading the downloaded cache and keys to extract collision data for all regions. The output is a compressed `collision-map.zip` file containing the processed collision map data.

6. **Store and Commit Output**  
   The resulting `collision-map.zip` is moved to `src/main/resources/`, replacing the previous version if it has changed. If the map was updated, the workflow commits and pushes the new file to the repository, ensuring the latest collision data is always available.

The workflow runs automatically every Wednesday at 23:20 UTC (after the usual game update) or can be triggered manually via the GitHub Actions tab.
