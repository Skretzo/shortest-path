package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.SkillLevels;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** What a locked transport asks the player to get, and how those bundles rank and read. */
public class CapabilityTest
{
	private static final int FALADOR = WorldPointUtil.packWorldPoint(2965, 3378, 0);
	private static final int VARROCK = WorldPointUtil.packWorldPoint(3213, 3424, 0);

	private static Transport.TransportBuilder transport()
	{
		return new Transport.TransportBuilder().type(TransportType.TRANSPORT).origin(FALADOR).destination(VARROCK);
	}

	/** A level 1 account with no quests done, carrying nothing. */
	private static Requirements novice()
	{
		int[] levels = new int[SkillLevels.LENGTH];
		Arrays.fill(levels, 0, Skill.values().length, 1);
		return RequirementTestFixtures.requirements(
			RequirementTestFixtures.context().boostedSkillLevelsAndMore(levels).build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
	}

	private static SkillLevels levels(Skill skill, int level, Object... more)
	{
		int[] levels = new int[SkillLevels.LENGTH];
		levels[skill.ordinal()] = level;
		for (int i = 0; i < more.length; i += 2)
		{
			levels[((Skill) more[i]).ordinal()] = (Integer) more[i + 1];
		}
		return SkillLevels.of(levels);
	}

	private static TransportItems carry(int itemId)
	{
		return new TransportItems(List.of(new ItemRequirement(new int[] {itemId}, null, null, 1)));
	}

	@Test
	public void aTransportMissingAQuestAndLevelsNeedsOneBundleOfBoth()
	{
		Transport gate = transport().quests(Set.of(Quest.SONG_OF_THE_ELVES)).skillLevels("70 Agility").build();

		assertEquals(List.of(Capability.of(Set.of(Quest.SONG_OF_THE_ELVES), levels(Skill.AGILITY, 70), Map.of())),
			novice().capabilities(gate));
	}

	@Test
	public void theItemsToCarryComeWithTheQuestTheyAlsoNeed()
	{
		// As for a quest reward teleport whose row names the quest that gives it.
		Transport lyre = transport().quests(Set.of(Quest.THE_FREMENNIK_TRIALS))
			.itemRequirements(carry(ItemID.MAGIC_STRUNG_LYRE)).build();

		assertEquals(List.of(Capability.of(Set.of(Quest.THE_FREMENNIK_TRIALS), SkillLevels.NONE,
			Map.of(ItemID.MAGIC_STRUNG_LYRE, 1))), novice().capabilities(lyre));
	}

	@Test
	public void aLockNoCapabilityNamesYetMeansNoSuggestion()
	{
		// A declared unlock isn't something a capability can grant, so even the quest isn't offered.
		Transport gate = transport().quests(Set.of(Quest.SONG_OF_THE_ELVES))
			.itemRequirements(new TransportItems(List.of(new ItemRequirement(List.of(
				new ItemRequirement.Branch(null, null, null, 1, Unlock.XERICS_HONOUR)))))).build();

		assertTrue(novice().capabilities(gate).isEmpty());
		assertTrue(novice().capabilities(transport().build()).isEmpty());
	}

	@Test
	public void cheapestFirstRanksABundleByItsHardestPart()
	{
		Capability coins = Capability.items(Map.of(ItemID.COINS, 20));
		Capability rope = Capability.items(Map.of(ItemID.ROPE, 1));
		Capability quest = Capability.quests(Set.of(Quest.THE_FREMENNIK_TRIALS));
		Capability questAndItem = quest.plus(rope);
		Capability agility = Capability.skills(levels(Skill.AGILITY, 2));
		List<Capability> ranked = new ArrayList<>(List.of(agility, questAndItem, rope, quest, coins));

		ranked.sort(novice().cheapestFirst());

		assertEquals(List.of(coins, rope, quest, questAndItem, agility), ranked);
	}

	@Test
	public void describeNamesEveryPart()
	{
		assertEquals("Carry 20 Coins", Capability.items(Map.of(ItemID.COINS, 20)).describe(id -> "Coins"));
		assertEquals("Carry Bronze axe and Machete", Capability.items(Map.of(ItemID.BRONZE_AXE, 1, ItemID.MACHETTE, 1))
			.describe(id -> id == ItemID.BRONZE_AXE ? "Bronze axe" : "Machete"));
		assertEquals("Complete Song of the Elves, reach 70 Agility and 70 Construction",
			Capability.of(Set.of(Quest.SONG_OF_THE_ELVES), levels(Skill.AGILITY, 70, Skill.CONSTRUCTION, 70), Map.of())
				.describe(id -> "?"));
	}

	@Test
	public void plusNeedsEverythingEitherNeeds()
	{
		Capability fare = Capability.quests(Set.of(Quest.THE_FREMENNIK_TRIALS)).plus(Capability.items(Map.of(ItemID.COINS, 20)));
		Capability climb = Capability.items(Map.of(ItemID.COINS, 30)).plus(Capability.skills(levels(Skill.AGILITY, 50)));

		Capability both = fare.plus(climb);

		assertEquals(Set.of(Quest.THE_FREMENNIK_TRIALS), both.getQuests());
		assertEquals(Map.of(ItemID.COINS, 50), both.getItems());
		assertEquals(50, both.getSkillLevels().get(Skill.AGILITY));
		assertTrue(both.covers(fare));
		assertTrue(both.covers(climb));
		assertFalse(fare.covers(both));
	}
}
