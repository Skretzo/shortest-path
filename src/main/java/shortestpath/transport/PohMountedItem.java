package shortestpath.transport;

public enum PohMountedItem
{
	GLORY("Glory", "Amulet of Glory"),
	XERICS_TALISMAN("Xeric's Talisman", "Xeric's Talisman"),
	DIGSITE_PENDANT("Digsite Pendant", "Digsite Pendant"),
	MYTHICAL_CAPE("Mythical Cape", "Mythical cape");

	private final String label;
	private final String objectInfoFragment;

	PohMountedItem(String label, String objectInfoFragment)
	{
		this.label = label;
		this.objectInfoFragment = objectInfoFragment;
	}

	public static PohMountedItem fromObjectInfo(String objectInfo)
	{
		if (objectInfo == null)
		{
			return null;
		}
		for (PohMountedItem item : values())
		{
			if (objectInfo.contains(item.objectInfoFragment))
			{
				return item;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
