package shortestpath.transport.requirement;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.PathfinderConfig;

/**
 * Combination staves, tomes and leftover runes for {@link TransportItems#isSatisfiedBy}.
 */
public class TransportItemsSatisfactionTest
{
	@Test
	public void mistStaffAndLawUnlocksFalador()
	{
		Assert.assertTrue(satisfied(faladorTeleport(),
			ItemID.MIST_BATTLESTAFF, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void mysticMistStaffAndLawUnlocksFalador()
	{
		Assert.assertTrue(satisfied(faladorTeleport(),
			ItemID.MYSTIC_MIST_BATTLESTAFF, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void lawRunesAloneDoNotUnlockFalador()
	{
		Assert.assertFalse(satisfied(faladorTeleport(),
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void mistStaffStillWorksWhenAirRunesAreAlsoPresent()
	{
		Assert.assertTrue(satisfied(faladorTeleport(),
			ItemID.MIST_BATTLESTAFF, 1,
			ItemID.AIRRUNE, 3,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void mistStaffAndWaterRunesUnlockFalador()
	{
		Assert.assertTrue(satisfied(faladorTeleport(),
			ItemID.MIST_BATTLESTAFF, 1,
			ItemID.WATERRUNE, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void twoSingleElementStavesCannotCoverFalador()
	{
		Assert.assertFalse(satisfied(faladorTeleport(),
			ItemID.STAFF_OF_AIR, 1,
			ItemID.STAFF_OF_WATER, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void dustStaffAndLawUnlocksHouseTeleport()
	{
		Assert.assertTrue(satisfied(houseTeleport(),
			ItemID.DUST_BATTLESTAFF, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void mysticDustStaffAndLawUnlocksHouseTeleport()
	{
		Assert.assertTrue(satisfied(houseTeleport(),
			ItemID.MYSTIC_DUST_BATTLESTAFF, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void dustStaffPlusTomeOfFireCoversEarthAndFire()
	{
		Assert.assertTrue(satisfied(earthAndFire(),
			ItemID.DUST_BATTLESTAFF, 1,
			ItemID.TOME_OF_FIRE, 1));
	}

	@Test
	public void dustStaffAloneDoesNotCoverEarthAndFire()
	{
		Assert.assertFalse(satisfied(earthAndFire(),
			ItemID.DUST_BATTLESTAFF, 1));
	}

	@Test
	public void dustAndLavaStavesPreferLavaForEarthAndFire()
	{
		Assert.assertTrue(satisfied(earthAndFire(),
			ItemID.DUST_BATTLESTAFF, 1,
			ItemID.LAVA_BATTLESTAFF, 1));
	}

	@Test
	public void smokeStaffAndLawUnlocksVarrock()
	{
		Assert.assertTrue(satisfied(varrockTeleport(),
			ItemID.SMOKE_BATTLESTAFF, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void mysticAirStaffWithFireAndLawUnlocksVarrock()
	{
		Assert.assertTrue(satisfied(varrockTeleport(),
			ItemID.MYSTIC_AIR_STAFF, 1,
			ItemID.FIRERUNE, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void airStaffWithTomeOfFireAndLawUnlocksVarrock()
	{
		Assert.assertTrue(satisfied(varrockTeleport(),
			ItemID.STAFF_OF_AIR, 1,
			ItemID.TOME_OF_FIRE, 1,
			ItemID.LAWRUNE, 1));
	}

	@Test
	public void twoTomesCannotCoverEarthAndFire()
	{
		Assert.assertFalse(satisfied(earthAndFire(),
			ItemID.TOME_OF_EARTH, 1,
			ItemID.TOME_OF_FIRE, 1));
	}

	private static boolean satisfied(TransportItems items, int... idAndQuantity)
	{
		return items.isSatisfiedBy(counts(idAndQuantity), PathfinderConfig.CURRENCIES, Integer.MAX_VALUE);
	}

	private static Map<Integer, Integer> counts(int... idAndQuantity)
	{
		Map<Integer, Integer> itemCounts = new HashMap<>();
		for (int i = 0; i < idAndQuantity.length; i += 2)
		{
			itemCounts.put(idAndQuantity[i], idAndQuantity[i + 1]);
		}
		return itemCounts;
	}

	private static TransportItems faladorTeleport()
	{
		return new TransportItems(
			new int[][]{
				ItemVariations.AIR_RUNE.getIds(),
				ItemVariations.WATER_RUNE.getIds(),
				ItemVariations.LAW_RUNE.getIds()},
			new int[][]{
				ItemVariations.STAFF_OF_AIR.getIds(),
				ItemVariations.STAFF_OF_WATER.getIds(),
				ItemVariations.staves(ItemVariations.LAW_RUNE)},
			new int[][]{
				ItemVariations.offhands(ItemVariations.AIR_RUNE),
				ItemVariations.offhands(ItemVariations.WATER_RUNE),
				ItemVariations.offhands(ItemVariations.LAW_RUNE)},
			new int[]{3, 1, 1});
	}

	private static TransportItems houseTeleport()
	{
		return new TransportItems(
			new int[][]{
				ItemVariations.AIR_RUNE.getIds(),
				ItemVariations.EARTH_RUNE.getIds(),
				ItemVariations.LAW_RUNE.getIds()},
			new int[][]{
				ItemVariations.STAFF_OF_AIR.getIds(),
				ItemVariations.STAFF_OF_EARTH.getIds(),
				ItemVariations.staves(ItemVariations.LAW_RUNE)},
			new int[][]{
				ItemVariations.offhands(ItemVariations.AIR_RUNE),
				ItemVariations.offhands(ItemVariations.EARTH_RUNE),
				ItemVariations.offhands(ItemVariations.LAW_RUNE)},
			new int[]{1, 1, 1});
	}

	private static TransportItems varrockTeleport()
	{
		return new TransportItems(
			new int[][]{
				ItemVariations.AIR_RUNE.getIds(),
				ItemVariations.FIRE_RUNE.getIds(),
				ItemVariations.LAW_RUNE.getIds()},
			new int[][]{
				ItemVariations.STAFF_OF_AIR.getIds(),
				ItemVariations.STAFF_OF_FIRE.getIds(),
				ItemVariations.staves(ItemVariations.LAW_RUNE)},
			new int[][]{
				ItemVariations.offhands(ItemVariations.AIR_RUNE),
				ItemVariations.offhands(ItemVariations.FIRE_RUNE),
				ItemVariations.offhands(ItemVariations.LAW_RUNE)},
			new int[]{3, 1, 1});
	}

	private static TransportItems earthAndFire()
	{
		return new TransportItems(
			new int[][]{
				ItemVariations.EARTH_RUNE.getIds(),
				ItemVariations.FIRE_RUNE.getIds()},
			new int[][]{
				ItemVariations.STAFF_OF_EARTH.getIds(),
				ItemVariations.STAFF_OF_FIRE.getIds()},
			new int[][]{
				ItemVariations.offhands(ItemVariations.EARTH_RUNE),
				ItemVariations.offhands(ItemVariations.FIRE_RUNE)},
			new int[]{1, 1});
	}
}
