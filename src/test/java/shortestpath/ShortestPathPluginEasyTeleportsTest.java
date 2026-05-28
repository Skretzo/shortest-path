package shortestpath;

import java.util.Collections;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.teleports.EasyTeleportLabelFormatter;
import shortestpath.transport.Transport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ShortestPathPluginEasyTeleportsTest
{
	@Test
	public void fallsBackWhenEasyTeleportsPluginIsDisabled() throws Exception
	{
		ShortestPathPlugin plugin = createPlugin(false, true);
		Transport transport = createTransport();

		Assert.assertEquals("Pharaoh's sceptre: Jalsavrah", plugin.formatTransportDisplayInfo(transport));
	}

	@Test
	public void formatsWhenEasyTeleportsPluginIsActive() throws Exception
	{
		ShortestPathPlugin plugin = createPlugin(true, true);
		Transport transport = createTransport();

		Assert.assertEquals("Pharaoh's sceptre: Pyramid Plunder", plugin.formatTransportDisplayInfo(transport));
	}

	@Test
	public void fallsBackWhenEasyTeleportNamesOptionIsDisabled() throws Exception
	{
		ShortestPathPlugin plugin = createPlugin(true, false);
		Transport transport = createTransport();

		Assert.assertEquals("Pharaoh's sceptre: Jalsavrah", plugin.formatTransportDisplayInfo(transport));
	}

	private static ShortestPathPlugin createPlugin(boolean easyTeleportsActive, boolean easyTeleportNames) throws Exception
	{
		ShortestPathPlugin plugin = new ShortestPathPlugin();
		Plugin easyTeleportsPlugin = new TestEasyTeleportsPlugin();
		plugin.easyTeleportNames = easyTeleportNames;

		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(EasyTeleportLabelFormatter.CONFIG_GROUP, "enablePharaohSceptre")).thenReturn("true");
		when(configManager.getConfiguration(EasyTeleportLabelFormatter.CONFIG_GROUP, "replacementJalsavrah")).thenReturn("Pyramid Plunder");
		plugin.configManager = configManager;

		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPlugins()).thenReturn(Collections.singleton(easyTeleportsPlugin));
		when(pluginManager.isPluginActive(easyTeleportsPlugin)).thenReturn(easyTeleportsActive);
		plugin.pluginManager = pluginManager;

		plugin.onPluginChanged(new PluginChanged(easyTeleportsPlugin, easyTeleportsActive));
		return plugin;
	}

	private static Transport createTransport()
	{
		Transport transport = mock(Transport.class);
		when(transport.getDisplayInfo()).thenReturn("Pharaoh's sceptre: Jalsavrah");
		return transport;
	}

	private static final class TestEasyTeleportsPlugin extends Plugin
	{
		@Override
		public String getName()
		{
			return "Easy Teleports";
		}
	}
}
