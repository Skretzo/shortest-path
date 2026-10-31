package shortestpath.transport;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Skill;
import shortestpath.Util;
import shortestpath.requirement.model.SkillLevels;

@Getter
public enum TransportType
{
	TRANSPORT,
	AGILITY_SHORTCUT
		{
			@Override
			public TransportType refine(SkillLevels skillLevels)
			{
				if (skillLevels.get(Skill.RANGED) > 1 || skillLevels.get(Skill.STRENGTH) > 1)
				{
					return GRAPPLE_SHORTCUT;
				}
				return this;
			}
		},
	GRAPPLE_SHORTCUT,
	BOAT,
	CANOE,
	CHARTER_SHIP,
	SHIP,
	FAIRY_RING,
	GNOME_GLIDER,
	HOT_AIR_BALLOON,
	MAGIC_CARPET,
	MAGIC_MUSHTREE,
	MINECART,
	QUETZAL,
	QUETZAL_WHISTLE,
	SEASONAL_TRANSPORTS,
	SPIRIT_TREE,
	TELEPORTATION_BOX,
	TELEPORTATION_ITEM,
	TELEPORTATION_LEVER,
	TELEPORTATION_MINIGAME,
	TELEPORTATION_PORTAL,
	TELEPORTATION_PORTAL_POH,
	TELEPORTATION_SPELL,
	TELEPORTATION_SPELL_HOME,
	WILDERNESS_OBELISK,
	;

	static
	{
		// The ctor fails loudly on a constant with no TSV row; these passes
		// fail loudly on a row naming no constant and on a shares-with
		// column naming a constant that does not exist.
		for (String name : Rows.BY_NAME.keySet())
		{
			valueOf(name);
		}
		for (TransportType type : values())
		{
			if (type.sharedTypeName != null)
			{
				valueOf(type.sharedTypeName);
			}
		}
	}

	private final String resourcePath;
	private final String enabledKey;
	private final String costKey;
	private final Integer radiusThreshold;
	/**
	 * The {@code teleport} column; read through {@link #isTeleport()}.
	 * Levers, portals and wilderness obelisks are considered transports
	 * and not teleports because they have a pre-defined origin and no
	 * wilderness level limit.
	 */
	private final boolean teleport;
	/**
	 * The enum name of the {@link #sharesDestinationsWith()} pairing,
	 * stored as a name because the paired constant is not reliably
	 * constructed yet while rows initialise; resolved at call time.
	 */
	@Getter(AccessLevel.NONE)
	private final String sharedTypeName;
	/** The {@code differential-cost-key} column; read through {@link #differentialCostKey()}. */
	@Getter(AccessLevel.NONE)
	private final String differentialCostKey;

	/**
	 * Row backed by a {@code transport-types.tsv} line: every data column
	 * resolves out of {@link Rows}. Fails loudly on a missing row or a
	 * malformed literal; the enum's static block fails loudly on surplus
	 * rows and unresolvable shares-with names.
	 */
	TransportType()
	{
		String[] cols = Rows.BY_NAME.get(name());
		if (cols == null)
		{
			throw new IllegalStateException("no transport-types.tsv row for " + name());
		}
		this.resourcePath = emptyToNull(cols[1]);
		this.enabledKey = emptyToNull(cols[2]);
		this.costKey = emptyToNull(cols[3]);
		this.radiusThreshold = parseRadius(name(), cols[4]);
		this.teleport = parseTeleport(name(), cols[5]);
		this.sharedTypeName = emptyToNull(cols[6]);
		this.differentialCostKey = emptyToNull(cols[7]);
	}

	/** Column count every {@code transport-types.tsv} line must carry. */
	private static final int ROW_COLUMNS = 8;

	static String emptyToNull(String token)
	{
		return token.isEmpty() ? null : token;
	}

	static Integer parseRadius(String name, String token)
	{
		if (token.isEmpty())
		{
			return null;
		}
		try
		{
			return Integer.valueOf(token);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalStateException("unparseable radius threshold '" + token + "' for " + name, e);
		}
	}

	/**
	 * The {@code teleport} column parsed strictly: an empty or
	 * {@code false} cell is a transport, {@code true} is a teleport, and
	 * anything else throws — a silently wrong flag would change
	 * wilderness routing behaviour without failing the load.
	 */
	static boolean parseTeleport(String name, String token)
	{
		if (token.isEmpty() || "false".equals(token))
		{
			return false;
		}
		if ("true".equals(token))
		{
			return true;
		}
		throw new IllegalStateException("unparseable teleport flag '" + token + "' for " + name);
	}

	/**
	 * Load a {@code transport-types.tsv}-shaped resource into its
	 * name-keyed row map — the same shape {@link Rows} binds at class
	 * init. Package-visible so the TSV test can exercise the load's loud
	 * posture against fixture resources.
	 */
	static Map<String, String[]> loadRows(String path)
	{
		Map<String, String[]> rows = new HashMap<>();
		try
		{
			Util.each(path, (line, cols) ->
			{
				if (cols.length != ROW_COLUMNS)
				{
					throw new IllegalStateException("malformed transport-types.tsv row: " + line);
				}
				if (rows.put(cols[0], cols) != null)
				{
					throw new IllegalStateException("duplicate transport-types.tsv row: " + cols[0]);
				}
			});
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load " + path.substring(1), e);
		}
		return Collections.unmodifiableMap(rows);
	}

	/**
	 * Row table loaded from the bundled {@code transport-types.tsv} — a
	 * nested holder so the load happens on first constant construction;
	 * the enum's own static initializers would run too late (after every
	 * constant is already built).
	 */
	private static final class Rows
	{
		private static final Map<String, String[]> BY_NAME = loadRows("/transport-types.tsv");
	}

	public boolean hasResourcePath()
	{
		return resourcePath != null;
	}

	public boolean hasRadiusThreshold()
	{
		return radiusThreshold != null;
	}

	public boolean hasEnabledKey()
	{
		return enabledKey != null;
	}

	public boolean hasCostKey()
	{
		return costKey != null;
	}

	/**
	 * Stores which transport type this transport shares destinations with, if any.
	 * Used for delayed visit pathfinding so both types can compete in the priority queue.
	 */
	public TransportType sharesDestinationsWith()
	{
		return sharedTypeName == null ? null : valueOf(sharedTypeName);
	}

	/**
	 * The config key carrying the additional cost applied on top of the base
	 * cost when this transport type shares destinations with another type
	 * (e.g. how many extra tiles the whistle must save over a landing site
	 * to justify using a charge). {@code null} when the type has none.
	 */
	public String differentialCostKey()
	{
		return differentialCostKey;
	}


	/**
	 * Refines the TransportType based on the required skill levels.
	 */
	public TransportType refine(SkillLevels skillLevels)
	{
		return this;
	}
}
