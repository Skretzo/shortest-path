package shortestpath;

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
		keybinds.putFromDialogLine("<col=ffffff>1 : Harmony Island");
		keybinds.putFromDialogLine("<col=ffffff>Y : Lassar (Ice Mountain)");

		assertEquals("1: Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("Y: Lassar Portal", keybinds.apply("Lassar Portal"));
	}

	@Test
	public void testParentheticalLocationMatchesSpellName()
	{
		keybinds.putFromDialogLine("<col=ffffff>K : Frozen Waste Plateau (Ghorrock)");
		keybinds.putFromDialogLine("<col=ffffff>A : Canifis (Kharyrll)");
		keybinds.putFromDialogLine("<col=ffffff>B : Demonic Ruins (Annakarl)");
		keybinds.putFromDialogLine("<col=ffffff>C : Graveyard of Shadows (Carrallanger)");
		keybinds.putFromDialogLine("<col=ffffff>D : Edgeville Dungeon (Paddewwa)");

		assertEquals("K: Ghorrock Portal", keybinds.apply("Ghorrock Portal"));
		assertEquals("A: Kharyrll Portal", keybinds.apply("Kharyrll Portal"));
		assertEquals("B: Annakarl Portal", keybinds.apply("Annakarl Portal"));
		assertEquals("C: Carrallanger Portal", keybinds.apply("Carrallanger Portal"));
		assertEquals("D: Paddewwa Portal", keybinds.apply("Paddewwa Portal"));
	}

	@Test
	public void testLiveDialogOrderOverridesDefault()
	{
		keybinds.putFromDialogLine("<col=735a28>Y:</col> Lassar");
		keybinds.putFromDialogLine("<col=735a28>S:</col> Catherby");

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
		keybinds.putFromDialogLine("Y: Lassar");

		assertEquals("Y: Lassar Portal", keybinds.apply("S: Lassar Portal"));
	}

	@Test
	public void testDiaryVariantDoesNotInheritObservedKey()
	{
		// "Grand Exchange" is a separate menu line with its own key; copying
		// Varrock's key to it would show a hint the game did not bind.
		keybinds.putFromDialogLine("4: Varrock");

		assertEquals("4: Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("Grand Exchange Portal", keybinds.apply("Grand Exchange Portal"));
	}

	@Test
	public void testDiaryVariantParsesOwnKey()
	{
		keybinds.putFromDialogLine("4: Varrock");
		keybinds.putFromDialogLine("5: Grand Exchange");

		assertEquals("4: Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("5: Grand Exchange Portal", keybinds.apply("Grand Exchange Portal"));
	}

	@Test
	public void testRespawnVariantsShareAKey()
	{
		keybinds.putFromDialogLine("7: Respawn point");

		assertEquals("7: Respawn Portal (Lumbridge)", keybinds.apply("Respawn Portal (Lumbridge)"));
		assertEquals("7: Respawn Portal (Falador)", keybinds.apply("Respawn Portal (Falador)"));
	}

	@Test
	public void testTruncatedFenkenstrainName()
	{
		keybinds.putFromDialogLine("<col=ffffff>8 : Fenken' Castle");

		assertEquals("8: Fenkenstrain's Castle Portal", keybinds.apply("Fenkenstrain's Castle Portal"));
	}

	@Test
	public void testOverflowDestinationHasNoKey()
	{
		keybinds.putFromDialogLine("Z: Catherby");

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
		PortalNexusKeybinds.deserialize("2|ghorrock=K|harmony island=1|lassar=Y|waterbirth island=F3", keys);

		assertEquals("K", keys.get("ghorrock"));
		assertEquals("1", keys.get("harmony island"));
		assertEquals("Y", keys.get("lassar"));
		assertEquals("F3", keys.get("waterbirth island"));
		assertEquals("2|ghorrock=K|harmony island=1|lassar=Y|waterbirth island=F3",
			PortalNexusKeybinds.serialize(keys));
	}

	@Test
	public void testDeserializeRejectsInvalidKeys()
	{
		Map<String, String> keys = new HashMap<>();
		PortalNexusKeybinds.deserialize("2|a=F13|b=F0|c=0|d=AB|e=Z|f=f5", keys);

		assertEquals(Map.of("e", "Z", "f", "F5"), keys);
	}

	@Test
	public void testDeserializeAdmitsFunctionKeys()
	{
		Map<String, String> keys = new HashMap<>();
		PortalNexusKeybinds.deserialize("2|a=F1|b=F10|c=F11|d=F12", keys);

		assertEquals(Map.of("a", "F1", "b", "F10", "c", "F11", "d", "F12"), keys);
	}

	@Test
	public void testLoadFromProfileAppliesSavedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, PortalNexusKeybinds.CONFIG_KEY))
			.thenReturn("2|lassar=Y|waterbirth island=F3");

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
			ShortestPathPlugin.CONFIG_GROUP, PortalNexusKeybinds.CONFIG_KEY))
			.thenReturn("2|lassar=Y")
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
		persisted.putFromDialogLine("Y: Lassar");
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq(ShortestPathPlugin.CONFIG_GROUP),
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
	public void testUnkeyedConfigurationSlotsProduceNoKeys()
	{
		// Slot list position is not a keybind; unkeyed labels must not
		// manufacture one.
		keybinds.replaceIfPresent(PortalNexusKeybinds.parseSlotLabels(
			Arrays.asList("Harmony Island", "Lumbridge")));

		assertEquals("Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testUnkeyedConfigurationSlotsProduceNoFKeys()
	{
		String[] names = new String[38];
		Arrays.fill(names, "Lumbridge");
		names[35] = "Lunar Isle";
		names[36] = "Ourania";
		names[37] = "Waterbirth Island";

		keybinds.replaceIfPresent(PortalNexusKeybinds.parseSlotLabels(Arrays.asList(names)));

		assertEquals("Lunar Isle Portal", keybinds.apply("Lunar Isle Portal"));
		assertEquals("Ourania Portal", keybinds.apply("Ourania Portal"));
		assertEquals("Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testPartialDialogDoesNotWipeCachedKeys()
	{
		keybinds.putFromDialogLine("1: Harmony Island");
		keybinds.putFromDialogLine("Y: Lassar");

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
		persisted.putFromDialogLine("1: Harmony Island");
		persisted.putFromDialogLine("Y: Lassar");
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
		keybinds.putFromDialogLine("F3: Waterbirth Island");

		assertEquals("F3: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testUnkeyedDialogLineProducesNoKey()
	{
		// Lines whose hint is drawn as a sprite have no readable prefix and no
		// key can be assigned to them from their position.
		for (int i = 0; i < 37; i++)
		{
			keybinds.putFromDialogLine(i + ": destination " + i);
		}
		keybinds.putFromDialogLine(":  Waterbirth Island");

		assertEquals("Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testBareDialogLineProducesNoKey()
	{
		// A plugin rewriting the menu can strip the key prefix entirely; a
		// bare destination name must not be assigned a positional key.
		keybinds.putFromDialogLine("Lassar");
		keybinds.putFromDialogLine("Waterbirth Island");

		assertEquals("Lassar Portal", keybinds.apply("Lassar Portal"));
		assertEquals("Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testCompositeKeyPrefixProducesNoKey()
	{
		// User-defined binds can be multi-key combos the single-key pattern
		// cannot express; showing nothing beats showing an invented key.
		keybinds.putFromDialogLine("Ctrl+G: Varrock");
		keybinds.putFromDialogLine("Shift+F3: Waterbirth Island");

		assertEquals("Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testUnkeyedLinesDoNotDisturbKeyedNeighbours()
	{
		keybinds.putFromDialogLine("1: Harmony Island");
		keybinds.putFromDialogLine("<img=42>");
		keybinds.putFromDialogLine("");
		keybinds.putFromDialogLine("4: Varrock");

		assertEquals("1: Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("4: Varrock Portal", keybinds.apply("Varrock Portal"));
	}

	@Test
	public void testLegacyStoredDataIsDiscarded()
	{
		// Pre-versioning payloads can hold position-derived keys that were
		// never bound; they must not load.
		Map<String, String> keys = new HashMap<>();
		PortalNexusKeybinds.deserialize("ghorrock=K|waterbirth island=F3", keys);

		assertEquals(Map.of(), keys);
	}
}
