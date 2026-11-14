package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import shortestpath.ShortestPathConfig;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;

public class PerCharacterSettingsTest
{
	private static final String GROUP = Settings.CONFIG_GROUP;

	private static Settings settings(ConfigManager configManager)
	{
		return new Settings(configManager, mock(ShortestPathConfig.class));
	}

	@Test
	public void houseKeysArePerCharacter()
	{
		assertTrue(ConfigKey.USE_POH.isPerCharacter());
		assertTrue(ConfigKey.USE_POH_FAIRY_RING.isPerCharacter());
		assertTrue(ConfigKey.POH_NEXUS_PORTALS.isPerCharacter());
		assertTrue(ConfigKey.POH_JEWELLERY_BOX_TIER.isPerCharacter());
		assertTrue(ConfigKey.POH_MOUNTED_ITEMS.isPerCharacter());
		assertFalse(ConfigKey.DETECT_POH_SETTINGS.isPerCharacter());
		assertFalse(ConfigKey.AVOID_WILDERNESS.isPerCharacter());
	}

	@Test
	public void theCharactersOwnValueWins()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(GROUP), eq("usePohFairyRing"), any(Type.class))).thenReturn(false);
		when(configManager.getRSProfileConfiguration(eq(GROUP), eq("usePohFairyRing"), any(Type.class)))
			.thenReturn(true);
		when(configManager.getRSProfileConfiguration(GROUP, "pohMountedItems")).thenReturn("[\"GLORY\"]");
		when(configManager.getConfiguration(GROUP, "pohMountedItems")).thenReturn("[]");

		Settings settings = settings(configManager);
		assertTrue(settings.bool(ConfigKey.USE_POH_FAIRY_RING));
		assertEquals(EnumSet.of(PohMountedItem.GLORY), settings.get(ConfigKey.POH_MOUNTED_ITEMS));
	}

	@Test
	public void aCharacterWithoutItsOwnValueReadsTheSharedOne()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(GROUP), eq("pohJewelleryBoxTier"), any(Type.class)))
			.thenReturn(JewelleryBoxTier.FANCY);
		assertEquals(JewelleryBoxTier.FANCY, settings(configManager).get(ConfigKey.POH_JEWELLERY_BOX_TIER));
	}

	@Test
	public void panelWritesGoToTheCharacterWhenLoggedIn()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		Settings settings = settings(configManager);

		settings.write("usePohObelisk", true);
		verify(configManager).setRSProfileConfiguration(GROUP, "usePohObelisk", true);
		verify(configManager, never()).setConfiguration(GROUP, "usePohObelisk", (Object) true);

		settings.write("avoidWilderness", false);
		verify(configManager).setConfiguration(GROUP, "avoidWilderness", (Object) false);
	}

	@Test
	public void panelWritesAreSharedWhenLoggedOut()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		// RuneLite's per-account write does nothing while logged out, so the shared value is written instead.
		settings(configManager).write("usePohObelisk", true);
		verify(configManager).setConfiguration(GROUP, "usePohObelisk", (Object) true);
	}

	@Test
	public void aCharacterWithoutAProfileYetGetsOne()
	{
		// Logged in for the first time on this character: no profile key until the first per-account write.
		ConfigManager configManager = mock(ConfigManager.class);
		boolean[] made = {false};
		when(configManager.getRSProfileKey()).thenAnswer(inv -> made[0] ? "rsprofile.new" : null);
		org.mockito.Mockito.doAnswer(inv ->
		{
			made[0] = true;
			return null;
		}).when(configManager).setRSProfileConfiguration(anyString(), anyString(), any());
		Settings settings = settings(configManager);

		assertTrue(settings.writeForCharacter(ConfigKey.USE_POH_FAIRY_RING, true));
		verify(configManager).setRSProfileConfiguration(GROUP, "usePohFairyRing", true);
		settings.write("usePohObelisk", true);
		verify(configManager).setRSProfileConfiguration(GROUP, "usePohObelisk", true);
		verify(configManager, never()).setConfiguration(GROUP, "usePohObelisk", (Object) true);
	}

	@Test
	public void detectedValuesAreWrittenOnlyWhenTheyChange()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.abc");
		when(configManager.getRSProfileConfiguration(eq(GROUP), eq("usePohSpiritTree"), any(Type.class)))
			.thenReturn(true);
		Settings settings = settings(configManager);

		assertFalse(settings.writeForCharacter(ConfigKey.USE_POH_SPIRIT_TREE, true));
		assertTrue(settings.writeForCharacter(ConfigKey.USE_POH_FAIRY_RING, true));
		verify(configManager).setRSProfileConfiguration(GROUP, "usePohFairyRing", true);
		assertFalse(settings.writeForCharacter(ConfigKey.AVOID_WILDERNESS, true));
	}

	@Test
	public void nothingIsDetectedWhileLoggedOut()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		assertFalse(settings(configManager).writeForCharacter(ConfigKey.USE_POH_FAIRY_RING, true));
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void aCharacterChangeRereadsAndReroutes()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(GROUP), eq("usePohObelisk"), any(Type.class))).thenReturn(false);
		Settings settings = settings(configManager);
		assertFalse(settings.bool(ConfigKey.USE_POH_OBELISK));

		when(configManager.getRSProfileConfiguration(eq(GROUP), eq("usePohObelisk"), any(Type.class)))
			.thenReturn(true);
		ConfigChange change = settings.characterChanged();
		assertTrue(settings.bool(ConfigKey.USE_POH_OBELISK));
		assertTrue(change.getEffects().contains(Effect.ROUTE_INVALIDATING));
	}

	@Test
	public void aVisitedHouseRoutesWithoutTouchingTheSettings()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(GROUP), eq("usePohFairyRing"), any(Type.class))).thenReturn(false);
		Settings settings = settings(configManager);

		ConfigChange change = settings.visitHouse(java.util.Map.of(ConfigKey.USE_POH_FAIRY_RING, true));
		assertTrue(change.getEffects().contains(Effect.ROUTE_INVALIDATING));
		assertTrue(settings.isVisitingHouse());
		assertTrue(settings.bool(ConfigKey.USE_POH_FAIRY_RING));
		// The panel still shows the player's own value.
		assertEquals(false, settings.configured("usePohFairyRing"));
		// The same house again changes nothing.
		assertEquals(null, settings.visitHouse(java.util.Map.of(ConfigKey.USE_POH_FAIRY_RING, true)));

		assertTrue(settings.visitHouse(java.util.Map.of()) != null);
		assertFalse(settings.bool(ConfigKey.USE_POH_FAIRY_RING));
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void everyPerCharacterKeyIsKnown()
	{
		Set<ConfigKey> keys = ConfigKey.perCharacterKeys();
		for (ConfigKey key : keys)
		{
			assertEquals(key, ConfigKey.forKey(key.getKey()));
		}
	}
}
