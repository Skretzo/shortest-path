package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import shortestpath.scheduler.RefreshCoordinator;

public class PortalNexusKeybindTest
{
	private PohService keybinds;
	private RefreshCoordinator coordinator;

	@Before
	public void setUp()
	{
		coordinator = mock(RefreshCoordinator.class);
		keybinds = PohService.forTesting(coordinator);
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
	public void testDiaryVariantsShareAKey()
	{
		keybinds.putFromDialogLine("4: Varrock");

		assertEquals("4: Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("4: Grand Exchange Portal", keybinds.apply("Grand Exchange Portal"));
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
		assertEquals("ardougne", PohService.normalize("East Ardougne"));
		assertEquals("kourend", PohService.normalize("Kourend Castle"));
		assertEquals("lassar", PohService.normalize("Lassar Portal"));
		assertEquals("lassar", PohService.normalize("Lassar (Ice Mountain)"));
		assertEquals("ghorrock", PohService.normalize("Frozen Waste Plateau"));
		assertEquals("kharyrll", PohService.normalize("Canifis"));
		assertEquals("fenkenstrain s castle", PohService.normalize("Fenken' Castle"));
		assertEquals("fenkenstrain s castle", PohService.normalize("Fenkenstrain's Castle Portal"));
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
		PohService.deserialize("ghorrock=K|harmony island=1|lassar=Y|waterbirth island=0", keys);

		assertEquals("K", keys.get("ghorrock"));
		assertEquals("1", keys.get("harmony island"));
		assertEquals("Y", keys.get("lassar"));
		assertEquals("0", keys.get("waterbirth island"));
		assertEquals("ghorrock=K|harmony island=1|lassar=Y|waterbirth island=0",
			PohService.serialize(keys));
	}

	@Test
	public void testDeserializeRejectsInvalidKeys()
	{
		Map<String, String> keys = new HashMap<>();
		PohService.deserialize("a=F11|b=F0|c=0|d=AB|e=Z|f=f5|g=F12", keys);

		assertEquals(Map.of("c", "0", "e", "Z"), keys);
	}

	@Test
	public void testLoadFromProfileAppliesSavedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", PohService.CONFIG_KEY))
			.thenReturn("lassar=Y|waterbirth island=Z");

		PohService persisted = new PohService(configManager, coordinator);
		persisted.loadFromProfile();

		// A consulted store always declares — the load is not conditional
		// on what it found.
		verify(coordinator).pohChanged(argThat(c -> "profile".equals(c.getKey())));
		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));
		assertEquals("Z: Waterbirth Island Portal", persisted.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testLoadFromProfileEvictsPersistedFunctionKeys()
	{
		// Profiles poisoned by the old position-derived bindings self-heal:
		// persisted function-key entries are dropped on load.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", PohService.CONFIG_KEY))
			.thenReturn("lassar=Y|waterbirth island=F3");

		PohService persisted = new PohService(configManager);
		persisted.loadFromProfile();

		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));
		assertEquals("Waterbirth Island Portal", persisted.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testLoadFromProfileClearsPreviousAccount()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", PohService.CONFIG_KEY))
			.thenReturn("lassar=Y")
			.thenReturn(null);

		PohService persisted = new PohService(configManager);
		persisted.loadFromProfile();
		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));

		persisted.loadFromProfile();
		assertEquals("Lassar Portal", persisted.apply("Lassar Portal"));
	}

	@Test
	public void testPersistIfDirtyWritesRsProfile()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		PohService persisted = new PohService(configManager);
		persisted.putFromDialogLine("Y: Lassar");
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq("shortestpath"),
			eq(PohService.CONFIG_KEY),
			contains("lassar=Y"));
	}

	@Test
	public void testKeyedConfigurationSlotsKeepExplicitKeys()
	{
		keybinds.replaceIfPresent(PohService.parseSlotLabels(
			Arrays.asList("3: Varrock", "5: Lumbridge")));

		assertEquals("3: Varrock Portal", keybinds.apply("Varrock Portal"));
		assertEquals("5: Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testUnkeyedConfigurationSlotsGetNoKeys()
	{
		// An unkeyed slot list carries no key information; nothing is learned.
		assertTrue(PohService.parseSlotLabels(
			Arrays.asList("Harmony Island", "Lumbridge")).isEmpty());

		assertEquals("Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testUnkeyedConfigurationSlotsGetNoFunctionKeys()
	{
		// Slots past the labelled range render their key as a sprite, so the
		// label has no readable prefix and no key is learned.
		String[] names = new String[38];
		Arrays.fill(names, "Lumbridge");
		names[35] = "Lunar Isle";
		names[36] = "Ourania";
		names[37] = "Waterbirth Island";

		assertTrue(PohService.parseSlotLabels(Arrays.asList(names)).isEmpty());

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
		PohService persisted = new PohService(configManager);
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
		keybinds.putFromDialogLine("F12: Lunar Isle");

		assertEquals("F3: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
		assertEquals("F12: Lunar Isle Portal", keybinds.apply("Lunar Isle Portal"));
	}

	@Test
	public void testFunctionKeyLineParsesExplicitPrefix()
	{
		// Other plugins can render binds inline as "F3</col>: name"; the
		// explicit prefix is the only source of the mapping.
		keybinds.putFromDialogLine("F3</col>: Waterbirth Island");
		keybinds.putFromDialogLine("<col=ffffff>Catherby");

		assertEquals("F3: Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
		assertEquals("Catherby Portal", keybinds.apply("Catherby Portal"));
	}

	@Test
	public void testKeyedLineParsesZero()
	{
		keybinds.putFromDialogLine("0: Lumbridge");

		assertEquals("0: Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testUnsupportedKeyFormsProduceNoMapping()
	{
		keybinds.putFromDialogLine("F13: Waterbirth Island");
		keybinds.putFromDialogLine("F20: Lunar Isle");
		keybinds.putFromDialogLine("Shift+F3: Catherby");

		assertEquals("Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
		assertEquals("Lunar Isle Portal", keybinds.apply("Lunar Isle Portal"));
		assertEquals("Catherby Portal", keybinds.apply("Catherby Portal"));
	}

	@Test
	public void testUnkeyedDialogLineLearnsNoKey()
	{
		// A line without an explicit "key : name" prefix is not a keybind
		// source, whatever its position in the dialog — and declares
		// nothing to the coordinator.
		keybinds.putFromDialogLine("<col=ffffff>Harmony Island");
		keybinds.putFromDialogLine("Lumbridge");
		keybinds.putFromDialogLine(":  Waterbirth Island");

		verify(coordinator, never()).pohChanged(any());
		assertEquals("Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
		assertEquals("Waterbirth Island Portal", keybinds.apply("Waterbirth Island Portal"));
	}
}
