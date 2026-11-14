package shortestpath.poh;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.runelite.api.TileObject;

/** One house feature where it stands in the loaded house, as the last look found it. */
@Getter
@Accessors(fluent = true)
public final class HouseObject
{
	private final TileObject object;
	/** The id the object shows now (a multiloc's current form), which names it. */
	private final int id;
	/** The route targets it can take ({@link HouseScan#serves}). */
	private final Set<String> serves;

	public HouseObject(TileObject object, int id, Set<String> serves)
	{
		this.object = object;
		this.id = id;
		this.serves = serves;
	}
}
