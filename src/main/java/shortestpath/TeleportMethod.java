package shortestpath;

import lombok.Getter;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * A stable, human-meaningful identity for a single teleport/transport option, used by the
 * alternative-routes feature both as the row shown in the side panel and as the key that the user
 * can exclude from the next search.
 * <p>
 * Identity is {@code (type, displayInfo, destination)}: the {@link TransportType} groups options
 * into a category (Spells, Fairy Rings, Items, ...), {@code displayInfo} is the per-option label the
 * data files carry (e.g. "Varrock Teleport", "Ardougne cloak: Monastery", or a fairy-ring code), and
 * the packed {@code destination} disambiguates options that carry no display info.
 */
@Getter
public final class TeleportMethod
{
	private final TransportType type;
	private final String displayInfo;
	private final int destination;

	public TeleportMethod(TransportType type, String displayInfo, int destination)
	{
		this.type = type;
		this.displayInfo = (displayInfo == null || displayInfo.isEmpty()) ? null : displayInfo;
		this.destination = destination;
	}

	public static TeleportMethod fromTransport(Transport transport)
	{
		return new TeleportMethod(transport.getType(), transport.getDisplayInfo(), transport.getDestination());
	}

	/**
	 * The grouping bucket shown as a section header in the panel.
	 */
	public String category()
	{
		return categoryOf(type);
	}

	/**
	 * The per-option label shown in the panel row. Falls back to the category plus the destination
	 * tile when the data file carries no display info (common for spells whose info is the spell name).
	 */
	public String label()
	{
		if (displayInfo != null)
		{
			return displayInfo;
		}
		int x = WorldPointUtil.unpackWorldX(destination);
		int y = WorldPointUtil.unpackWorldY(destination);
		return category() + " (" + x + ", " + y + ")";
	}

	/**
	 * Whether a transport type counts as a travel "method" worth listing and excluding. Plain local
	 * connectors (doors/ladders/stairs and agility/grapple shortcuts) are walking, not a method.
	 */
	public static boolean isMethodType(TransportType type)
	{
		return type != null
			&& type != TransportType.TRANSPORT
			&& type != TransportType.AGILITY_SHORTCUT
			&& type != TransportType.GRAPPLE_SHORTCUT;
	}

	public static String categoryOf(TransportType type)
	{
		if (type == null)
		{
			return "Other";
		}
		switch (type)
		{
			case TELEPORTATION_SPELL:
				return "Spells";
			case TELEPORTATION_ITEM:
				return "Items";
			case TELEPORTATION_BOX:
				return "Jewellery box";
			case TELEPORTATION_LEVER:
				return "Levers";
			case TELEPORTATION_MINIGAME:
				return "Minigame teleports";
			case TELEPORTATION_PORTAL:
			case TELEPORTATION_PORTAL_POH:
				return "Portals";
			case FAIRY_RING:
				return "Fairy rings";
			case SPIRIT_TREE:
				return "Spirit trees";
			case GNOME_GLIDER:
				return "Gnome gliders";
			case HOT_AIR_BALLOON:
				return "Hot air balloons";
			case MAGIC_CARPET:
				return "Magic carpets";
			case MAGIC_MUSHTREE:
				return "Mushtrees";
			case MINECART:
				return "Minecarts";
			case QUETZAL:
			case QUETZAL_WHISTLE:
				return "Quetzals";
			case WILDERNESS_OBELISK:
				return "Obelisks";
			case BOAT:
			case CHARTER_SHIP:
			case SHIP:
				return "Boats & ships";
			case CANOE:
				return "Canoes";
			case SEASONAL_TRANSPORTS:
				return "Seasonal";
			default:
				return "Other";
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof TeleportMethod))
		{
			return false;
		}
		TeleportMethod other = (TeleportMethod) o;
		return destination == other.destination
			&& type == other.type
			&& (displayInfo == null ? other.displayInfo == null : displayInfo.equals(other.displayInfo));
	}

	@Override
	public int hashCode()
	{
		int result = type == null ? 0 : type.hashCode();
		result = 31 * result + (displayInfo == null ? 0 : displayInfo.hashCode());
		result = 31 * result + destination;
		return result;
	}

	@Override
	public String toString()
	{
		return category() + ": " + label();
	}
}
