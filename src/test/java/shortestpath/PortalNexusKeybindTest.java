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
		Map<String, Character> keys = new HashMap<>();
		PortalNexusKeybinds.deserialize("ghorrock=K|harmony island=1|lassar=Y", keys);

		assertEquals(Character.valueOf('K'), keys.get("ghorrock"));
		assertEquals(Character.valueOf('1'), keys.get("harmony island"));
		assertEquals(Character.valueOf('Y'), keys.get("lassar"));
		assertEquals("ghorrock=K|harmony island=1|lassar=Y", PortalNexusKeybinds.serialize(keys));
	}

	@Test
	public void testLoadFromProfileAppliesSavedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, PortalNexusKeybinds.CONFIG_KEY))
			.thenReturn("lassar=Y");

		PortalNexusKeybinds persisted = new PortalNexusKeybinds(configManager);
		persisted.loadFromProfile();

		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));
	}

	@Test
	public void testLoadFromProfileClearsPreviousAccount()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, PortalNexusKeybinds.CONFIG_KEY))
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
	public void testUnkeyedConfigurationSlotsUseFilledOrder()
	{
		keybinds.replaceIfPresent(PortalNexusKeybinds.parseSlotLabels(
			Arrays.asList("Harmony Island", "Lumbridge")));

		assertEquals("1: Harmony Island Portal", keybinds.apply("Harmony Island Portal"));
		assertEquals("2: Lumbridge Portal", keybinds.apply("Lumbridge Portal"));
	}

	@Test
	public void testPartialDialogDoesNotWipeCachedKeys()
	{
		keybinds.putFromDialogLine("1: Harmony Island");
		keybinds.putFromDialogLine("Y: Lassar");

		Map<String, Character> partial = new HashMap<>();
		partial.put("harmony island", '1');
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

		Map<String, Character> partial = new HashMap<>();
		partial.put("harmony island", '1');
		persisted.replaceIfPresent(partial);

		verifyNoMoreInteractions(configManager);
		assertEquals("Y: Lassar Portal", persisted.apply("Lassar Portal"));
	}
}
