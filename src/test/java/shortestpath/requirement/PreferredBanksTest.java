package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;

public class PreferredBanksTest
{
	@Test
	public void storedNamesRoundTrip()
	{
		Set<String> names = PreferredBanks.parse(" Ferox Enclave,Edgeville ,,");
		assertEquals(Set.of("Ferox Enclave", "Edgeville"), names);
		assertEquals("Edgeville,Ferox Enclave", PreferredBanks.format(names));
		assertTrue(PreferredBanks.parse(null).isEmpty());
		assertTrue(PreferredBanks.parse("").isEmpty());
	}

	@Test
	public void namesFitTheStoredList()
	{
		List<String> names = PreferredBanks.names();
		assertFalse(names.isEmpty());
		for (String name : names)
		{
			assertFalse(name + " would split the stored list", name.contains(","));
			assertEquals(name + " would not survive a round trip", name, name.trim());
		}
	}

	/** A name is one bank a player recognises, so towns with two banks name each. */
	@Test
	public void townsWithTwoBanksNameEach()
	{
		Set<Integer> west = PreferredBanks.tiles(Set.of("Varrock West"), PreferredBanks.namesByTile());
		assertEquals(5, west.size());
		assertTrue(west.contains(WorldPointUtil.packWorldPoint(3185, 3436, 0)));
		assertEquals(6, PreferredBanks.tiles(Set.of("Varrock East"), PreferredBanks.namesByTile()).size());
		for (String town : List.of("Varrock", "Falador", "Ardougne"))
		{
			assertFalse(town + " is split", PreferredBanks.names().contains(town));
		}
	}

	@Test
	public void noPreferenceHasNoTiles()
	{
		assertTrue(PreferredBanks.tiles(Set.of(), PreferredBanks.namesByTile()).isEmpty());
		assertTrue(PreferredBanks.tiles(Set.of("Nowhere"), PreferredBanks.namesByTile()).isEmpty());
	}
}
