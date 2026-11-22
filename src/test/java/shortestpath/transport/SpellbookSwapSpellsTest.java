package shortestpath.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.*;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.requirement.model.*;

/**
 * The Spellbook Swap twins generated into {@code teleportation_spells_swapped.tsv}
 * by {@code scripts/generate_spellbook_swap_spells.py} load as teleport spells
 * and cost what casting both spells costs. CI checks the file is current.
 */
public class SpellbookSwapSpellsTest
{
	private static final String PREFIX = "Spellbook Swap (";
	private static final Map<Integer, String> BOOKS = Map.of(0, "Standard", 1, "Ancient", 3, "Arceuus");
	private static final VarRequirement LUNAR =
		new VarRequirement(VarRequirement.VarType.VARBIT, VarbitID.SPELLBOOK, 2, VarCheckType.EQUAL);

	private static List<Transport> spells;

	@BeforeClass
	public static void load()
	{
		spells = new ArrayList<>();
		for (Transport transport : ResourceLoader.transports().get(Transport.UNDEFINED_ORIGIN))
		{
			if (TransportType.TELEPORTATION_SPELL.equals(transport.getType())
				|| TransportType.TELEPORTATION_SPELL_HOME.equals(transport.getType()))
			{
				spells.add(transport);
			}
		}
	}

	private static Transport byDisplay(String displayInfo)
	{
		for (Transport spell : spells)
		{
			if (displayInfo.equals(spell.getDisplayInfo()))
			{
				return spell;
			}
		}
		return null;
	}

	private static Integer spellbook(Transport spell)
	{
		for (VarRequirement requirement : spell.getVarRequirements())
		{
			if (requirement.isVarbit() && requirement.getId() == VarbitID.SPELLBOOK)
			{
				return requirement.getValue();
			}
		}
		return null;
	}

	@Test
	public void everyOtherSpellbookTeleportHasALunarTwin()
	{
		for (Transport spell : spells)
		{
			Integer book = spellbook(spell);
			if (spell.getDisplayInfo().startsWith(PREFIX))
			{
				assertEquals(spell.getDisplayInfo(), Integer.valueOf(2), book);
				assertTrue(spell.getDisplayInfo(), spell.getQuests().contains(Quest.DREAM_MENTOR));
				assertTrue(spell.getDisplayInfo(), spell.getSkillLevels().get(Skill.MAGIC) >= 96);
			}
			else if (book != null && book != 2)
			{
				assertNotNull("no Spellbook Swap twin for " + spell.getDisplayInfo(),
					byDisplay(PREFIX + BOOKS.get(book) + "): " + spell.getDisplayInfo()));
			}
		}
	}

	@Test
	public void twinCarriesTheSpellsOtherRequirements()
	{
		Transport twin = byDisplay(PREFIX + "Standard): Varrock Teleport: Grand Exchange");

		assertEquals(byDisplay("Varrock Teleport: Grand Exchange").getDestination(), twin.getDestination());
		assertEquals(Set.of(LUNAR, new VarRequirement(VarRequirement.VarType.VARBIT, 4480, 1, VarCheckType.EQUAL)),
			twin.getVarRequirements());
		assertEquals(8, twin.getDuration());
	}

	@Test
	public void arceuusTwinNeedsTheSpellbookUnlocked()
	{
		Transport twin = byDisplay(PREFIX + "Arceuus): Arceuus Library Teleport");

		assertEquals(Set.of(LUNAR,
			new VarRequirement(VarRequirement.VarType.VARBIT, VarbitID.ARCEUUS_SPELLBOOK_UNLOCKED, 1, VarCheckType.EQUAL)),
			twin.getVarRequirements());
	}

	@Test
	public void homeTeleportTwinKeepsTheCooldown()
	{
		Transport home = byDisplay("Lumbridge Home Teleport");
		Transport twin = byDisplay(PREFIX + "Standard): Lumbridge Home Teleport");

		assertEquals(TransportType.TELEPORTATION_SPELL_HOME, twin.getType());
		assertEquals(home.getDuration() + 4, twin.getDuration());
		assertTrue(twin.getVarRequirements().containsAll(home.getVarRequirements().stream()
			.filter(VarRequirement::isVarPlayer).collect(java.util.stream.Collectors.toSet())));
		assertEquals(4, twin.getItemRequirements().size());
	}

	@Test
	public void twinNeedsBothSpellsRunes()
	{
		Transport twin = byDisplay(PREFIX + "Standard): Varrock Teleport");
		Map<Integer, Integer> items = new HashMap<>(Map.of(
			ItemID.ASTRALRUNE, 3, ItemID.COSMICRUNE, 2, ItemID.AIRRUNE, 3, ItemID.FIRERUNE, 1, ItemID.LAWRUNE, 1));

		assertFalse("one law rune pays only one of the two casts",
			twin.getItemRequirements().isSatisfiedBy(items, Set.of(), 0, Set.of(Unlock.SPELLBOOK_SWAP)));
		items.put(ItemID.LAWRUNE, 2);
		assertTrue(twin.getItemRequirements().isSatisfiedBy(items, Set.of(), 0, Set.of(Unlock.SPELLBOOK_SWAP)));
	}
}
