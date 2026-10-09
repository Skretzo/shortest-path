package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

public class PortalNexusKeybindTest
{
	private PortalNexusKeybinds keybinds;

	@Before
	public void setUp()
	{
		keybinds = new PortalNexusKeybinds();
	}

	@Test
	public void testNexusMenuLabelFormat()
	{
		keybinds.putFromDialogLine(0, "<col=ffffff>1 : Harmony Island");
		keybinds.putFromDialogLine(0, "<col=ffffff>Y : Lassar (Ice Mountain)");

		assertEquals("1: Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("Y: Lassar Portal", keybinds.apply("Lassar Portal"));
	}

	@Test
	public void testParentheticalLocationMatchesSpellName()
	{
		keybinds.putFromDialogLine(0, "<col=ffffff>K : Frozen Waste Plateau (Ghorrock)");
		keybinds.putFromDialogLine(0, "<col=ffffff>A : Canifis (Kharyrll)");
		keybinds.putFromDialogLine(0, "<col=ffffff>B : Demonic Ruins (Annakarl)");
		keybinds.putFromDialogLine(0, "<col=ffffff>C : Graveyard of Shadows (Carrallanger)");
		keybinds.putFromDialogLine(0, "<col=ffffff>D : Edgeville Dungeon (Paddewwa)");

		assertEquals("K: Ghorrock Portal", keybinds.apply("Ghorrock Portal"));
		assertEquals("A: Kharyrll Portal", keybinds.apply("Kharyrll Portal"));
		assertEquals("B: Annakarl Portal", keybinds.apply("Annakarl Portal"));
		assertEquals("C: Carrallanger Portal", keybinds.apply("Carrallanger Portal"));
		assertEquals("D: Paddewwa Portal", keybinds.apply("Paddewwa Portal"));
	}

	@Test
	public void testLiveDialogOrderOverridesDefault()
	{
		keybinds.putFromDialogLine(0, "<col=735a28>Y:</col> Lassar");
		keybinds.putFromDialogLine(0, "<col=735a28>S:</col> Catherby");

		assertEquals("Y: Lassar Portal", keybinds.apply("Lassar Portal"));
		assertEquals("S: Catherby Portal", keybinds.apply("Catherby Portal"));
	}

	@Test
	public void testApplyLeavesNameUnprefixedUntilDialogIsRead()
	{
		assertEquals("Lassar Portal", keybinds.apply("Lassar Portal"));
	}

	@Test
	public void testApplyReplacesStaleKeyPrefix()
	{
		keybinds.putFromDialogLine(0, "Y: Lassar");

		assertEquals("Y: Lassar Portal", keybinds.apply("S: Lassar Portal"));
	}

	@Test
	public void testDiaryVariantsShareAKey()
	{
		keybinds.putFromDialogLine(0, "4: Varrock");

		assertEquals("4: Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("4: Grand Exchange Portal", keybinds.apply("Grand Exchange Portal"));
	}

	@Test
	public void testRespawnVariantsShareAKey()
	{
		keybinds.putFromDialogLine(0, "7: Respawn point");

		assertEquals("7: Respawn Portal (Lumbridge)", keybinds.apply("Respawn Portal (Lumbridge)"));
		assertEquals("7: Respawn Portal (Falador)", keybinds.apply("Respawn Portal (Falador)"));
	}

	@Test
	public void testTruncatedFenkenstrainName()
	{
		keybinds.putFromDialogLine(0, "<col=ffffff>8 : Fenken' Castle");

		assertEquals("8: Fenkenstrain's Castle Portal", keybinds.apply("Fenkenstrain's Castle Portal"));
	}

	@Test
	public void testOverflowDestinationHasNoKey()
	{
		keybinds.putFromDialogLine(0, "Z: Catherby");

		assertEquals("Weiss Portal", keybinds.apply("Weiss Portal"));
	}

	@Test
	public void testNormalizeAliases()
	{
		assertEquals("ardougne", PortalNexusKeybinds.normalize("East Ardougne"));
		assertEquals("kourend", PortalNexusKeybinds.normalize("Kourend Castle"));
		assertEquals("lassar", PortalNexusKeybinds.normalize("Lassar Portal"));
		assertEquals("lassar", PortalNexusKeybinds.normalize("Lassar (Ice Mountain)"));
		assertEquals("ghorrock", PortalNexusKeybinds.normalize("Frozen Waste Plateau"));
		assertEquals("kharyrll", PortalNexusKeybinds.normalize("Canifis"));
		assertEquals("fenkenstrain s castle", PortalNexusKeybinds.normalize("Fenken' Castle"));
		assertEquals("fenkenstrain s castle", PortalNexusKeybinds.normalize("Fenkenstrain's Castle Portal"));
	}

	@Test
	public void testApplyNull()
	{
		assertNull(keybinds.apply(null));
	}

	@Test
	public void testSerializeRoundTrip()
	{
		Map<String, String> keys = new HashMap<>();
		PortalNexusKeybinds.deserialize("ghorrock=K|harmony island=1|lassar=Y|waterbirth island=F3", keys);

		assertEquals("K", keys.get("ghorrock"));
		assertEquals("1", keys.get("harmony island"));
		assertEquals("Y", keys.get("lassar"));
		assertEquals("F3", keys.get("waterbirth island"));
		assertEquals("ghorrock=K|harmony island=1|lassar=Y|waterbirth island=F3",
			PortalNexusKeybinds.serialize(keys));
	}

	@Test
	public void testDeserializeRejectsInvalidKeys()
	{
		Map<String, String> keys = new HashMap<>();
		PortalNexusKeybinds.deserialize("a=F11|b=F0|c=0|d=AB|e=Z|f=f5", keys);

		assertEquals(Map.of("e", "Z", "f", "F5"), keys);
	}

	@Test
	public void testLoadFromProfileAppliesSavedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", PortalNexusKeybinds.CONFIG_KEY))
			.thenReturn("lassar=Y|waterbirth island=F3");

		PortalNexusKeybinds persisted = new PortalNexusKeybinds(configManager);
		persisted.loadFromProfile();

		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));
		assertEquals("F3: Waterbirth Island Portal", persisted.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testLoadFromProfileClearsPreviousAccount()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", PortalNexusKeybinds.CONFIG_KEY))
			.thenReturn("lassar=Y")
			.thenReturn(null);

		PortalNexusKeybinds persisted = new PortalNexusKeybinds(configManager);
		persisted.loadFromProfile();
		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));

		persisted.loadFromProfile();
		assertEquals("Lassar Portal", persisted.apply("Lassar Portal"));
	}

	@Test
	public void testPersistIfDirtyWritesRsProfile()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		PortalNexusKeybinds persisted = new PortalNexusKeybinds(configManager);
		persisted.putFromDialogLine(0, "Y: Lassar");
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq("shortestpath"),
			eq(PortalNexusKeybinds.CONFIG_KEY),
			contains("lassar=Y"));
	}

	@Test
	public void testKeyedConfigurationSlotsKeepExplicitKeys()
	{
		keybinds.replaceIfPresent(PortalNexusKeybinds.parseSlotLabels(
			Arrays.asList("3: Varrock", "5: Lumbridge")));

		assertEquals("3: Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("5: Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testUnkeyedConfigurationSlotsUseFilledOrder()
	{
		keybinds.replaceIfPresent(PortalNexusKeybinds.parseSlotLabels(
			Arrays.asList("Harmony Island", "Lumbridge")));

		assertEquals("1: Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("2: Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testUnkeyedConfigurationSlotsAssignFKeys()
	{
		String[] names = new String[38];
		Arrays.fill(names, "Lumbridge");
		names[35] = "Lunar Isle";
		names[36] = "Ourania";
		names[37] = "Waterbirth Island";

		keybinds.replaceIfPresent(PortalNexusKeybinds.parseSlotLabels(Arrays.asList(names)));

		assertEquals("F1: Lunar Isle Portal", keybinds.apply("Lunar Isle Portal"));
		assertEquals("F2: Ourania Portal", keybinds.apply("Ourania Portal"));
		assertEquals("F3: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testPartialDialogDoesNotWipeCachedKeys()
	{
		keybinds.putFromDialogLine(0, "1: Harmony Island");
		keybinds.putFromDialogLine(0, "Y: Lassar");

		Map<String, String> partial = new HashMap<>();
		partial.put("harmony island", "1");
		keybinds.replaceIfPresent(partial);

		assertEquals("1: Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("Y: Lassar Portal", keybinds.apply("Lassar Portal"));
	}

	@Test
	public void testPartialDialogDoesNotPersistAShrink()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		PortalNexusKeybinds persisted = new PortalNexusKeybinds(configManager);
		persisted.putFromDialogLine(0, "1: Harmony Island");
		persisted.putFromDialogLine(0, "Y: Lassar");
		persisted.persistIfDirty();
		clearInvocations(configManager);

		Map<String, String> partial = new HashMap<>();
		partial.put("harmony island", "1");
		persisted.replaceIfPresent(partial);

		verifyNoMoreInteractions(configManager);
		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));
	}

	@Test
	public void testKeyedLineParsesFunctionKeys()
	{
		keybinds.putFromDialogLine(0, "F3: Waterbirth Island");

		assertEquals("F3: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testFunctionKeyLineAssignsPositionally()
	{
		// Lines past slot 35 render their key as a sprite, so the text has no
		// key prefix; the position in the build sequence determines the key.
		for (int i = 0; i < 37; i++)
		{
			keybinds.putFromDialogLine(0, i + ": destination " + i);
		}
		keybinds.putFromDialogLine(0, ":  Waterbirth Island");

		assertEquals("F3: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testDialogLineIndexResetsOnNewTick()
	{
		keybinds.putFromDialogLine(0, ":  Lassar");
		keybinds.putFromDialogLine(1, ":  Waterbirth Island");

		assertEquals("1: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testExplicitKeyAdvancesLineIndex()
	{
		// A keyed line still consumes a menu position, so a following keyless
		// line counts it when deriving its own key.
		for (int i = 0; i < 35; i++)
		{
			keybinds.putFromDialogLine(0, (i + 1) + ": destination " + i);
		}
		keybinds.putFromDialogLine(0, ":  Lunar Isle");
		keybinds.putFromDialogLine(0, ":  Ourania");

		assertEquals("F1: Lunar Isle Portal", keybinds.apply("Lunar Isle Portal"));
		assertEquals("F2: Ourania Portal", keybinds.apply("Ourania Portal"));
	}

	@Test
	public void testKeyForIndexBoundaries()
	{
		assertEquals("1", PortalNexusKeybinds.keyForIndex(0));
		assertEquals("9", PortalNexusKeybinds.keyForIndex(8));
		assertEquals("A", PortalNexusKeybinds.keyForIndex(9));
		assertEquals("Z", PortalNexusKeybinds.keyForIndex(34));
		assertEquals("F1", PortalNexusKeybinds.keyForIndex(35));
		assertEquals("F10", PortalNexusKeybinds.keyForIndex(44));
		assertEquals("", PortalNexusKeybinds.keyForIndex(45));
		assertEquals("", PortalNexusKeybinds.keyForIndex(-1));
	}
}
