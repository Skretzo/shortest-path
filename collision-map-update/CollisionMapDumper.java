/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2022, Skretzo <https://github.com/Skretzo>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.Collection;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.KeyProvider;
import net.runelite.cache.util.XteaKeyManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Collision map dumper
 *
 * Cache and XTEA keys can be downloaded from:
 * https://archive.openrs2.org/caches
 * and replace "mapsquare" with "region" and "key" with "keys".
 * Compile the client with "mvn install -f pom.xml -DskipTests -U".
 * Run "java net.runelite.cache.CollisionMapDumper --cachedir ../cache --xteapath ../keys.json --outputdir ../output"
 */
public class CollisionMapDumper
{
	// ========================================
	// Object Type Constants
	// ========================================
	
	/**
	 * Straight wall types (types 0-3)
	 */
	private static final int OBJECT_TYPE_WALL_STRAIGHT = 0;
	private static final int OBJECT_TYPE_WALL_DIAGONAL_CORNER = 1;
	private static final int OBJECT_TYPE_WALL_ENTIRE_CORNER = 2;
	private static final int OBJECT_TYPE_WALL_SQUARE_CORNER = 3;
	private static final int OBJECT_TYPE_WALL_MAX = 3;
	
	/**
	 * Diagonal wall type
	 */
	private static final int OBJECT_TYPE_WALL_DIAGONAL = 9;
	
	/**
	 * Interactable diagonal objects (types 10-11)
	 */
	private static final int OBJECT_TYPE_DIAGONAL_INTERACTABLE_MIN = 10;
	private static final int OBJECT_TYPE_DIAGONAL_INTERACTABLE_MAX = 11;
	
	/**
	 * Game objects / scenery (types 12-21)
	 */
	private static final int OBJECT_TYPE_GAME_OBJECT_MIN = 12;
	private static final int OBJECT_TYPE_GAME_OBJECT_MAX = 21;
	
	/**
	 * Ground decoration type
	 */
	private static final int OBJECT_TYPE_GROUND_DECORATION = 22;

	// ========================================
	// Orientation Constants
	// ========================================
	
	private static final int ORIENTATION_WEST = 0;
	private static final int ORIENTATION_NORTH = 1;
	private static final int ORIENTATION_EAST = 2;
	private static final int ORIENTATION_SOUTH = 3;

	// ========================================
	// Tile Setting Constants
	// ========================================
	
	/**
	 * Tile setting indicating blocked movement (water, rooftop walls)
	 */
	private static final int TILE_SETTING_BLOCKED = 1;
	
	/**
	 * Tile setting flag for bridge tiles
	 */
	private static final int TILE_SETTING_BRIDGE_FLAG = 2;
	
	/**
	 * Tile setting indicating bridge wall
	 */
	private static final int TILE_SETTING_BRIDGE_WALL = 3;
	
	/**
	 * Tile setting indicating house wall/roof
	 */
	private static final int TILE_SETTING_HOUSE_ROOF = 5;
	
	/**
	 * Tile setting indicating house wall
	 */
	private static final int TILE_SETTING_HOUSE_WALL = 7;

	// ========================================
	// Object Definition Constants
	// ========================================
	
	/**
	 * InteractType value indicating no interaction/blocking
	 */
	private static final int INTERACT_TYPE_NONE = 0;
	
	/**
	 * InteractType value indicating the object blocks movement
	 */
	private static final int INTERACT_TYPE_BLOCKS_MOVEMENT = 1;
	
	/**
	 * InteractType default value - interactable but walkable
	 */
	private static final int INTERACT_TYPE_DEFAULT = 2;
	
	/**
	 * MapSceneID value indicating no map scene
	 */
	private static final int MAP_SCENE_ID_NONE = -1;
	
	/**
	 * WallOrDoor value indicating not a wall/door (passable)
	 */
	private static final int WALL_OR_DOOR_NONE = 0;
	
	/**
	 * WallOrDoor value indicating a wall or door (may block)
	 */
	private static final int WALL_OR_DOOR_PRESENT = 1;

	// ========================================
	// Plane Constants
	// ========================================
	
	/**
	 * The plane used for checking bridge tiles
	 */
	private static final int BRIDGE_CHECK_PLANE = 1;
	
	/**
	 * Maximum plane for applying tileZ adjustments
	 */
	private static final int MAX_PLANE_FOR_TILE_Z_ADJUSTMENT = 3;

	// ========================================
	// Underlay/Overlay Constants
	// ========================================
	
	/**
	 * Underlay/Overlay ID indicating no floor
	 */
	private static final int NO_UNDERLAY_OVERLAY_ID = 0;
	
	private final RegionLoader regionLoader;
	private final ObjectManager objectManager;

	public CollisionMapDumper(Store store, KeyProvider keyProvider)
	{
		this(store, new RegionLoader(store, keyProvider));
	}

	public CollisionMapDumper(Store store, RegionLoader regionLoader)
	{
		this.regionLoader = regionLoader;
		this.objectManager = new ObjectManager(store);
	}

	public CollisionMapDumper load() throws IOException
	{
		objectManager.load();
		regionLoader.loadRegions();
		regionLoader.calculateBounds();

		return this;
	}

	private ObjectDefinition findObject(int id)
	{
		return objectManager.getObject(id);
	}

	public static void main(String[] args) throws IOException
	{
		Options options = new Options();
		options.addOption(Option.builder().longOpt("cachedir").hasArg().required().build());
		options.addOption(Option.builder().longOpt("xteapath").hasArg().required().build());
		options.addOption(Option.builder().longOpt("outputdir").hasArg().required().build());

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		try
		{
			cmd = parser.parse(options, args);
		}
		catch (ParseException ex)
		{
			System.err.println("Error parsing command line options: " + ex.getMessage());
			System.exit(-1);
			return;
		}

		final String cacheDirectory = cmd.getOptionValue("cachedir");
		final String xteaJSONPath = cmd.getOptionValue("xteapath");
		final String outputDirectory = cmd.getOptionValue("outputdir");

		XteaKeyManager xteaKeyManager = new XteaKeyManager();
		try (FileInputStream fin = new FileInputStream(xteaJSONPath))
		{
			xteaKeyManager.loadKeys(fin);
		}

		File base = new File(cacheDirectory);
		File outDir = new File(outputDirectory);
		outDir.mkdirs();

		try (Store store = new Store(base))
		{
			store.load();

			CollisionMapDumper dumper = new CollisionMapDumper(store, xteaKeyManager);
			dumper.load();

			Collection<Region> regions = dumper.regionLoader.getRegions();

			int n = 0;
			int total = regions.size();

			for (Region region : regions)
			{
				dumper.makeCollisionMap(region, outputDirectory, ++n, total);
			}
		}
	}

	private void makeCollisionMap(Region region, String outputDirectory, int n, int total)
	{
		int baseX = region.getBaseX();
		int baseY = region.getBaseY();

		FlagMap flagMap = new FlagMap(baseX, baseY, baseX + Region.X - 1, baseY + Region.Y - 1);

		addCollisions(flagMap, region);
		addNeighborCollisions(flagMap, region, -1, -1);
		addNeighborCollisions(flagMap, region, -1, 0);
		addNeighborCollisions(flagMap, region, -1, 1);
		addNeighborCollisions(flagMap, region, 0, -1);
		addNeighborCollisions(flagMap, region, 0, 1);
		addNeighborCollisions(flagMap, region, 1, -1);
		addNeighborCollisions(flagMap, region, 1, 0);
		addNeighborCollisions(flagMap, region, 1, 1);

		String name = region.getRegionX() + "_" + region.getRegionY();

		byte[] buf = flagMap.toBytes();
		if (buf.length > 0)
		{
			try (FileOutputStream out = new FileOutputStream(outputDirectory + "/" + name))
			{
				out.write(buf, 0, buf.length);
				System.out.println("Exporting region " + name + " (" + n + " / " + total + ")");
			}
			catch (IOException e)
			{
				System.out.println("Unable to write compressed output bytes for " + name + ". " + e);
			}
		}
	}

	private void addNeighborCollisions(FlagMap flagMap, Region region, int dx, int dy)
	{
		Region neighbor = regionLoader.findRegionForRegionCoordinates(region.getRegionX() + dx, region.getRegionY() + dy);
		if (neighbor == null)
		{
			return;
		}
		addCollisions(flagMap, neighbor);
	}

	private void addCollisions(FlagMap flagMap, Region region)
	{
		int baseX = region.getBaseX();
		int baseY = region.getBaseY();

		for (int z = 0; z < Region.Z; z++)
		{
			for (int localX = 0; localX < Region.X; localX++)
			{
				int regionX = baseX + localX;
				for (int localY = 0; localY < Region.Y; localY++)
				{
					int regionY = baseY + localY;

					boolean isBridge = (region.getTileSetting(BRIDGE_CHECK_PLANE, localX, localY) & TILE_SETTING_BRIDGE_FLAG) != 0;
					int tileZ = z + (isBridge ? 1 : 0);

					for (Location loc : region.getLocations())
					{
						Position pos = loc.getPosition();
						if (pos.getX() != regionX || pos.getY() != regionY || pos.getZ() != tileZ)
						{
							continue;
						}

						boolean tile = FlagMap.TILE_BLOCKED;
						Boolean exclusion = Exclusion.matches(loc.getId());

						int X = loc.getPosition().getX();
						int Y = loc.getPosition().getY();
						int Z = z;

						int type = loc.getType();
						int orientation = loc.getOrientation();

						ObjectDefinition object = findObject(loc.getId());

						int sizeX = (orientation == 1 || orientation == 3) ? object.getSizeY() : object.getSizeX();
						int sizeY = (orientation == 1 || orientation == 3) ? object.getSizeX() : object.getSizeY();

						// Walls
						if (type >= OBJECT_TYPE_WALL_STRAIGHT && type <= OBJECT_TYPE_WALL_MAX)
						{
							Z = z != tileZ ? z : loc.getPosition().getZ();

							if (object.getMapSceneID() != MAP_SCENE_ID_NONE)
							{
								if (exclusion != null)
								{
									tile = exclusion;
								}
								else if (object.getInteractType() == INTERACT_TYPE_NONE)
								{
									continue;
								}
								for (int sx = 0; sx < sizeX; sx++)
								{
									for (int sy = 0; sy < sizeY; sy++)
									{
										flagMap.set(X + sx, Y + sy, Z, FlagMap.FLAG_NORTH, tile);
										flagMap.set(X + sx, Y + sy, Z, FlagMap.FLAG_EAST, tile);
										flagMap.set(X + sx, Y + sy - 1, Z, FlagMap.FLAG_NORTH, tile);
										flagMap.set(X + sx - 1, Y + sy, Z, FlagMap.FLAG_EAST, tile);
									}
								}
							}
							else
							{
								boolean door = object.getWallOrDoor() != WALL_OR_DOOR_NONE;
								boolean doorway = !door && object.getInteractType() == INTERACT_TYPE_NONE && type == OBJECT_TYPE_WALL_STRAIGHT;
								tile = door ? FlagMap.TILE_DEFAULT : FlagMap.TILE_BLOCKED;
								if (exclusion != null)
								{
									tile = exclusion;
								}
								else if (doorway)
								{
									continue;
								}

								if (type == OBJECT_TYPE_WALL_STRAIGHT || type == OBJECT_TYPE_WALL_ENTIRE_CORNER)
								{
									if (orientation == ORIENTATION_WEST) // wall on west
									{
										flagMap.set(X - 1, Y, Z, FlagMap.FLAG_WEST, tile);
									}
									else if (orientation == ORIENTATION_NORTH) // wall on north
									{
										flagMap.set(X, Y, Z, FlagMap.FLAG_NORTH, tile);
									}
									else if (orientation == ORIENTATION_EAST) // wall on east
									{
										flagMap.set(X, Y, Z, FlagMap.FLAG_EAST, tile);
									}
									else if (orientation == ORIENTATION_SOUTH) // wall on south
									{
										flagMap.set(X, Y - 1, Z, FlagMap.FLAG_SOUTH, tile);
									}
								}

								/*
								if (type == OBJECT_TYPE_WALL_SQUARE_CORNER)
								{
									if (orientation == ORIENTATION_WEST) // corner north-west
									{
										flagMap.set(X - 1, Y, Z, FlagMap.FLAG_WEST, tile);
									}
									else if (orientation == ORIENTATION_NORTH) // corner north-east
									{
										flagMap.set(X, Y, Z, FlagMap.FLAG_NORTH, tile);
									}
									else if (orientation == ORIENTATION_EAST) // corner south-east
									{
										flagMap.set(X, Y, Z, FlagMap.FLAG_EAST, tile);
									}
									else if (orientation == ORIENTATION_SOUTH) // corner south-west
									{
										flagMap.set(X, Y - 1, Z, FlagMap.FLAG_SOUTH, tile);
									}
								}
								*/

								if (type == OBJECT_TYPE_WALL_ENTIRE_CORNER) // double walls
								{
									if (orientation == ORIENTATION_SOUTH)
									{
										flagMap.set(X - 1, Y, Z, FlagMap.FLAG_WEST, tile);
									}
									else if (orientation == ORIENTATION_WEST)
									{
										flagMap.set(X, Y, Z, FlagMap.FLAG_NORTH, tile);
									}
									else if (orientation == ORIENTATION_NORTH)
									{
										flagMap.set(X, Y, Z, FlagMap.FLAG_EAST, tile);
									}
									else if (orientation == ORIENTATION_EAST)
									{
										flagMap.set(X, Y - 1, Z, FlagMap.FLAG_SOUTH, tile);
									}
								}
							}
						}

						// Diagonal walls
						if (type == OBJECT_TYPE_WALL_DIAGONAL)
						{
							if (object.getMapSceneID() != MAP_SCENE_ID_NONE)
							{
								if (exclusion != null)
								{
									tile = exclusion;
								}
								for (int sx = 0; sx < sizeX; sx++)
								{
									for (int sy = 0; sy < sizeY; sy++)
									{
										flagMap.set(X + sx, Y + sy, Z, FlagMap.FLAG_NORTH, tile);
										flagMap.set(X + sx, Y + sy, Z, FlagMap.FLAG_EAST, tile);
										flagMap.set(X + sx, Y + sy - 1, Z, FlagMap.FLAG_NORTH, tile);
										flagMap.set(X + sx - 1, Y + sy, Z, FlagMap.FLAG_EAST, tile);
									}
								}
							}
							else
							{
								boolean door = object.getWallOrDoor() != WALL_OR_DOOR_NONE;
								tile = door ? FlagMap.TILE_DEFAULT : FlagMap.TILE_BLOCKED;
								if (exclusion != null)
								{
									tile = exclusion;
								}

								if (orientation != ORIENTATION_WEST && orientation != ORIENTATION_EAST) // diagonal wall pointing north-east
								{
									flagMap.set(X, Y, Z, FlagMap.FLAG_NORTH, tile);
									flagMap.set(X, Y, Z, FlagMap.FLAG_EAST, tile);
									flagMap.set(X, Y - 1, Z, FlagMap.FLAG_NORTH, tile);
									flagMap.set(X - 1, Y, Z, FlagMap.FLAG_EAST, tile);
								}
								else // diagonal wall pointing north-west
								{
									flagMap.set(X, Y, Z, FlagMap.FLAG_NORTH, tile);
									flagMap.set(X, Y, Z, FlagMap.FLAG_WEST, tile);
									flagMap.set(X, Y - 1, Z, FlagMap.FLAG_NORTH, tile);
									flagMap.set(X - 1, Y, Z, FlagMap.FLAG_WEST, tile);
								}
							}
						}

						// Remaining objects
						if (type == OBJECT_TYPE_GROUND_DECORATION || (type >= OBJECT_TYPE_WALL_DIAGONAL && type <= OBJECT_TYPE_DIAGONAL_INTERACTABLE_MAX) || (type >= OBJECT_TYPE_GAME_OBJECT_MIN && type <= OBJECT_TYPE_GAME_OBJECT_MAX))
						{
							if (object.getInteractType() != INTERACT_TYPE_NONE && (object.getWallOrDoor() == WALL_OR_DOOR_PRESENT || (type >= OBJECT_TYPE_DIAGONAL_INTERACTABLE_MIN && type <= OBJECT_TYPE_GAME_OBJECT_MAX)))
							{
								if (exclusion != null)
								{
									tile = exclusion;
								}

								for (int sx = 0; sx < sizeX; sx++)
								{
									for (int sy = 0; sy < sizeY; sy++)
									{
										flagMap.set(X + sx, Y + sy, Z, FlagMap.FLAG_NORTH, tile);
										flagMap.set(X + sx, Y + sy, Z, FlagMap.FLAG_EAST, tile);
										flagMap.set(X + sx, Y + sy - 1, Z, FlagMap.FLAG_NORTH, tile);
										flagMap.set(X + sx - 1, Y + sy, Z, FlagMap.FLAG_EAST, tile);
									}
								}
							}
						}
					}

					// Tile without floor / floating in the air ("noclip" tiles, typically found where z > 0)
					int underlayId = region.getUnderlayId(z < MAX_PLANE_FOR_TILE_Z_ADJUSTMENT ? tileZ : z, localX, localY);
					int overlayId = region.getOverlayId(z < MAX_PLANE_FOR_TILE_Z_ADJUSTMENT ? tileZ : z, localX, localY);
					boolean noFloor = underlayId == NO_UNDERLAY_OVERLAY_ID && overlayId == NO_UNDERLAY_OVERLAY_ID;

					// Nomove
					int floorType = region.getTileSetting(z < MAX_PLANE_FOR_TILE_Z_ADJUSTMENT ? tileZ : z, localX, localY);
					if (floorType == TILE_SETTING_BLOCKED || // water, rooftop wall
						floorType == TILE_SETTING_BRIDGE_WALL || // bridge wall
						floorType == TILE_SETTING_HOUSE_ROOF || // house wall/roof
						floorType == TILE_SETTING_HOUSE_WALL || // house wall
						noFloor)
					{
						flagMap.set(regionX, regionY, z, FlagMap.FLAG_NORTH, FlagMap.TILE_BLOCKED);
						flagMap.set(regionX, regionY, z, FlagMap.FLAG_EAST, FlagMap.TILE_BLOCKED);
						flagMap.set(regionX, regionY - 1, z, FlagMap.FLAG_NORTH, FlagMap.TILE_BLOCKED);
						flagMap.set(regionX - 1, regionY, z, FlagMap.FLAG_EAST, FlagMap.TILE_BLOCKED);
					}
				}
			}
		}
	}

	private static class FlagMap
	{
		/**
		 * The default value of a tile in the compressed collision map
		 */
		public static final boolean TILE_DEFAULT = true;

		/**
		 * The value of a blocked tile in the compressed collision map
		 */
		public static final boolean TILE_BLOCKED = false;

		/**
		 * Number of possible z-planes: 0, 1, 2, 3
		 */
		private static final int PLANE_COUNT = 4;

		/**
		 * Number of possible flags: 0 = north/south, 1 = east/west
		 */
		private static final int FLAG_COUNT = 2;
		public static final int FLAG_NORTH = 0;
		public static final int FLAG_SOUTH = 0;
		public static final int FLAG_EAST = 1;
		public static final int FLAG_WEST = 1;

		public final BitSet flags;
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;
		private final int width;
		private final int height;

		public FlagMap(int minX, int minY, int maxX, int maxY)
		{
			this(minX, minY, maxX, maxY, TILE_DEFAULT);
		}

		public FlagMap(int minX, int minY, int maxX, int maxY, boolean value)
		{
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			width = (maxX - minX + 1);
			height = (maxY - minY + 1);
			flags = new BitSet(width * height * PLANE_COUNT * FLAG_COUNT);
			flags.set(0, flags.size(), value);
		}

		public byte[] toBytes()
		{
			return flags.toByteArray();
		}

		public void set(int x, int y, int z, int flag, boolean value)
		{
			if (isValidIndex(x, y, z, flag))
			{
				flags.set(index(x, y, z, flag), value);
			}
		}

		private boolean isValidIndex(int x, int y, int z, int flag)
		{
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= 0 && z <= PLANE_COUNT - 1 && flag >= 0 && flag <= FLAG_COUNT - 1;
		}

		private int index(int x, int y, int z, int flag)
		{
			if (isValidIndex(x, y, z, flag))
			{
				return (z * width * height + (y - minY) * width + (x - minX)) * FLAG_COUNT + flag;
			}
			throw new IndexOutOfBoundsException(x + " " + y + " " + z);
		}
	}

	private enum Exclusion
	{
		AMETHYST_CRYSTALS_EMPTY_WALL_11393(11393),

		APE_ATOLL_JAIL_DOOR_4800(4800),
		APE_ATOLL_JAIL_DOOR_4801(4801),

		ARDOUGNE_BASEMENT_CELL_DOOR_35795(35795),

		BRIMHAVEN_DUNGEON_EXIT_20878(20878),

		CELL_DOOR_9562(9562),

		COOKING_GUILD_DOOR_10045(10045),
		COOKING_GUILD_DOOR_24958(24958),

		CRAFTING_GUILD_DOOR_14910(14910),

		CRANDOR_WALL_2606(2606),

		DARKMEYER_CELL_DOOR_38014(38014),

		DESERT_MINING_CAMP_PRISON_DOOR_2689(2689),

		DIGSITE_GATE_24560(24560),
		DIGSITE_GATE_24561(24561),

		DRAYNOR_MANOR_LARGE_DOOR_134(134),
		DRAYNOR_MANOR_LARGE_DOOR_135(135),

		DRUIDS_ROBES_4035(4035),
		DRUIDS_ROBES_4036(4036),

		DWARF_CANNON_RAILING_15601(15601), // type = 9 is full blocked diagonal, type = 0 is wall, type = 1 is corner

		EDGEVILLE_DUNGEON_DOOR_1804(1804),

		FALADOR_GRAPPLE_WALL_17049(17049),
		FALADOR_GRAPPLE_WALL_17050(17050),
		FALADOR_GRAPPLE_WALL_17051(17051),
		FALADOR_GRAPPLE_WALL_17052(17052),

		FEROX_ENCLAVE_BARRIER_39652(39652),
		FEROX_ENCLAVE_BARRIER_39653(39653),

		FIGHT_ARENA_PRISON_DOOR_79(79),
		FIGHT_ARENA_PRISON_DOOR_80(80),

		FISHING_TRAWLER_RAIL_41400(41400),

		FORTHOS_DUNGEON_TEMPLE_DOOR_34843(34843),
		FORTHOS_DUNGEON_WALL_34854(34854),

		GOBLIN_TEMPLE_PRISON_DOOR_43457(43457),

		GRAND_EXCHANGE_BOOTH_10060(10060),
		GRAND_EXCHANGE_BOOTH_10061(10061),
		GRAND_EXCHANGE_BOOTH_30390(30390),

		GREAT_KOUREND_CELL_DOOR_41801(41801),

		GRIM_TALES_DOOR_24759(24759),

		HARDWOOD_GROVE_DOORS_9038(9038),
		HARDWOOD_GROVE_DOORS_9039(9039),

		HAUNTED_MINE_DOOR_4963(4963),
		HAUNTED_MINE_DOOR_4964(4964),

		HOSIDIUS_VINES_41814(41814),
		HOSIDIUS_VINES_41815(41815),
		HOSIDIUS_VINES_41816(41816),
		HOSIDIUS_VINES_46380(46380),
		HOSIDIUS_VINES_46381(46381),
		HOSIDIUS_VINES_46382(46382),

		KENDAL_STANDING_SPEARS_5860(5860),

		KRUKS_DUNGEON_WALL_28681(28681),
		KRUKS_DUNGEON_WALL_28798(28798),

		LUMBRIDGE_RECIPE_FOR_DISASTER_DOOR_12348(12348),
		LUMBRIDGE_RECIPE_FOR_DISASTER_DOOR_12349(12349),
		LUMBRIDGE_RECIPE_FOR_DISASTER_DOOR_12350(12350),

		MAGIC_AXE_HUT_DOOR_11726(11726),

		MCGRUBORS_WOOD_GATE_52(52),
		MCGRUBORS_WOOD_GATE_53(53),

		MEIYERDITCH_DOOR_17973(17973),

		MELZARS_MAZE_BLUE_DOOR_2599(2599),
		MELZARS_MAZE_DOOR_2595(2595),
		MELZARS_MAZE_EXIT_DOOR_2602(2602),
		MELZARS_MAZE_GREEN_DOOR_2601(2601),
		MELZARS_MAZE_MAGENTA_DOOR_2600(2600),
		MELZARS_MAZE_ORANGE_DOOR_2597(2597),
		MELZARS_MAZE_RED_DOOR_2596(2596),
		MELZARS_MAZE_YELLOW_DOOR_2598(2598),

		// MEMBERS_GATE_1727(1727), // Taverley, Falador, Brimhaven, Wilderness, Edgeville Dungeon
		// MEMBERS_GATE_1728(1728), // Taverley, Falador, Brimhaven, Wilderness, Edgeville Dungeon

		OLD_SCHOOL_MUSEUM_CURTAIN_31885(31885), // type = 9 is full blocked diagonal, type = 0 is wall

		PATERDOMUS_TEMPLE_CELL_DOOR_3463(3463),

		PEST_CONTROL_WALL_14216(14216),
		PEST_CONTROL_WALL_14217(14217),
		PEST_CONTROL_WALL_14218(14218),
		PEST_CONTROL_WALL_14219(14219),
		PEST_CONTROL_WALL_14225(14225),
		PEST_CONTROL_WALL_14226(14226),
		PEST_CONTROL_WALL_14228(14228),
		PEST_CONTROL_WALL_14229(14229),
		PEST_CONTROL_WALL_25636(25636), // type = 9 is full blocked diagonal, type = 0 is wall

		PORT_SARIM_PRISON_DOOR_9563(9563),
		PORT_SARIM_PRISON_DOOR_9565(9565),

		PRINCE_ALI_RESCUE_PRISON_GATE_2881(2881),

		RANGING_GUILD_DOOR_11665(11665),

		RAT_PITS_RAT_WALL_10335(10335), // type = 9 is full blocked diagonal, type = 2 is wall
		RAT_PITS_RAT_WALL_10337(10337), // type = 9 is full blocked diagonal, type = 2 is wall
		RAT_PITS_RAT_WALL_10342(10342), // type = 9 is full blocked diagonal, type = 2 is wall
		RAT_PITS_RAT_WALL_10344(10344), // type = 9 is full blocked diagonal, type = 2 is wall

		SCRUBFOOTS_CAVE_CREVICE_40889(40889),

		SHANTAY_PASS_PRISON_DOOR_2692(2692),

		TAI_BWO_WANNAI_ROTTEN_VILLAGE_FENCE_9025(9025),
		TAI_BWO_WANNAI_PARTIAL_FENCE_9026(9026),
		TAI_BWO_WANNAI_SHORT_FENCE_9027(9027),
		TAI_BWO_WANNAI_MEDIUM_FENCE_9028(9028),
		TAI_BWO_WANNAI_VILLAGE_FENCE_9029(9029),

		TAVERLEY_DUNGEON_PRISON_DOOR_2143(2143),
		TAVERLEY_DUNGEON_PRISON_DOOR_2144(2144),
		TAVERLEY_DUNGEON_DUSTY_KEY_DOOR_2623(2623),
		TAVERLEY_DUNGEON_LOOSE_RAILING_28849(28849),

		TEMPLE_OF_IKOV_DOOR_102(102),

		TEMPLE_OF_MARIMBO_DUNGEON_EXIT_16061(16061),
		TEMPLE_OF_MARIMBO_DUNGEON_EXIT_16100(16100),

		TREE_GNOME_STRONGHOLD_PRISON_DOOR_3367(3367),

		TROLL_STRONGHOLD_CELL_DOOR_3763(3763),
		TROLL_STRONGHOLD_CELL_DOOR_3765(3765),
		TROLL_STRONGHOLD_CELL_DOOR_3767(3767),
		TROLL_STRONGHOLD_EXIT_3772(3772),
		TROLL_STRONGHOLD_EXIT_3773(3773),
		TROLL_STRONGHOLD_EXIT_3774(3774),
		TROLL_STRONGHOLD_PRISON_DOOR_3780(3780),

		VARROCK_FENCE_SHORTCUT_16518(16518),

		VIYELDI_CAVES_CREVICE_2918(2918),

		WATERFALL_DUNGEON_DOOR_2002(2002),

		WEISS_BROKEN_FENCE_46815(46815),
		WEISS_BROKEN_FENCE_46816(46816),
		WEISS_BROKEN_FENCE_46817(46817),

		WILDERNESS_RESOURCE_AREA_GATE_26760(26760),

		YANILLE_DUNGEON_DOOR_11728(11728),
		YANILLE_GRAPPLE_WALL_17047(17047),
		YANILLE_GRAPPLE_WALL_17048(17058),
		YANILLE_MAGIC_GUILD_DOOR_1732(1732),
		YANILLE_MAGIC_GUILD_DOOR_1733(1733),

		ZANARIS_SHED_DOOR_2406(2406),
		;

		/**
		 * The object ID to be excluded
		 */
		private final int id;

		/**
		 * Whether the exclusion tile should be blocked or empty
		 */
		private final boolean tile;

		Exclusion(int id)
		{
			this(id, FlagMap.TILE_BLOCKED);
		}

		Exclusion(int id, boolean tile)
		{
			this.id = id;
			this.tile = tile;
		}

		public static Boolean matches(int id)
		{
			for (Exclusion exclusion : values())
			{
				if (exclusion.id == id)
				{
					return exclusion.tile;
				}
			}
			return null;
		}
	}
}
