package shortestpath.sailing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The restricted sea areas: water a boat enters only with a part fitted. Everywhere else at sea is open water. Each
 * area is a family of sailing water textures in the game cache, which the collision map's {@code sea} layer
 * records per tile (written by the collision map dumper). Seas have no entry level: only the boat's parts decide where
 * it may go.
 */
public enum SeaArea
{
	/** Tempor Tantrum's stormy seas: oak and linen sails or better. */
	TEMPOR_TANTRUM,
	/** Fetid water: an inoculation station. */
	FETID,
	/** Crystal-flecked water: an adamant keel or better. */
	CRYSTALLINE,
	/** Tangled kelp: an adamant helm or better. */
	KELP,
	/** The northern icy waters: an eternal brazier. */
	NORTHERN;

	/** No restricted area: open water only. */
	public static final Set<SeaArea> NONE = Collections.unmodifiableSet(EnumSet.noneOf(SeaArea.class));
	/** Every restricted area. */
	public static final Set<SeaArea> ALL = Collections.unmodifiableSet(EnumSet.allOf(SeaArea.class));

	/** The areas a transport file's field names, separated by {@code ;}. */
	public static Set<SeaArea> parse(String field)
	{
		if (field == null || field.trim().isEmpty()) return NONE;
		Set<SeaArea> areas = EnumSet.noneOf(SeaArea.class);
		for (String name : field.split(";"))
		{
			if (!name.trim().isEmpty()) areas.add(valueOf(name.trim()));
		}
		return Collections.unmodifiableSet(areas);
	}
}
