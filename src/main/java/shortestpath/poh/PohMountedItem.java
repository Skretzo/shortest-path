package shortestpath.poh;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * POH mounted-item labels and the fragment they match in the object-info
 * line, loaded from a bundled resource ({@code poh-mounted-items.tsv}).
 */
@ToString
@Getter
public final class PohMountedItem
{
	private static final Map<String, PohMountedItem> BY_NAME = new LinkedHashMap<>();

	static
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			PohMountedItem.class.getResourceAsStream("/poh-mounted-items.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				BY_NAME.put(c[0], new PohMountedItem(c[0], c[1], c[2]));
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load poh-mounted-items.tsv", e);
		}
	}

	@Getter @Accessors(fluent = true) private final String name;
	private final String label;
	private final String objectInfoFragment;

	private PohMountedItem(String name, String label, String objectInfoFragment)
	{
		this.name = name;
		this.label = label;
		this.objectInfoFragment = objectInfoFragment;
	}



	public static PohMountedItem fromName(String name)
	{
		return BY_NAME.get(name);
	}

	public static java.util.Collection<PohMountedItem> values()
	{
		return java.util.Collections.unmodifiableCollection(BY_NAME.values());
	}

	public static PohMountedItem fromObjectInfo(String objectInfo)
	{
		if (objectInfo == null)
		{
			return null;
		}
		for (PohMountedItem item : BY_NAME.values())
		{
			if (objectInfo.contains(item.objectInfoFragment))
			{
				return item;
			}
		}
		return null;
	}
	public static final PohMountedItem GLORY = fromName("GLORY");
	public static final PohMountedItem XERICS_TALISMAN = fromName("XERICS_TALISMAN");
	public static final PohMountedItem DIGSITE_PENDANT = fromName("DIGSITE_PENDANT");
	public static final PohMountedItem MYTHICAL_CAPE = fromName("MYTHICAL_CAPE");
}
