package shortestpath;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.ClueScrollService;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;

@Slf4j
public class CluePathHandler
{
	@Inject
	private ShortestPathPlugin shortestPathPlugin;

	@Inject
	private ClueScrollPlugin clueScrollPlugin;

	@Inject
	private ClueScrollService clueScrollService;

	private ClueScroll clueScroll;
	private boolean wasLastPathSetByClue = false;
	private Set<WorldPoint> lastLocations = Sets.newHashSet();

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ClueScroll clueScroll = clueScrollService.getClue();
		if (clueScroll != null)
		{
			Set<WorldPoint> newLocationsSet = new HashSet<>();

			if (clueScroll instanceof LocationClueScroll)
			{
				// Should be a single location in an Array despite LocationClueScroll, but lets use #getLocations for consistency
				Set<WorldPoint> ls = Set.of(((LocationClueScroll) clueScroll).getLocations(clueScrollPlugin));
				// log.debug("[Shortest-Path #Clue-Instance-Location] Clue location(s) ({}): {}", ls.size(), ls);
				newLocationsSet = ls;
			}

			if (clueScroll instanceof LocationsClueScroll)
			{
				Set<WorldPoint> ls = Set.of(((LocationsClueScroll) clueScroll).getLocations(clueScrollPlugin));
				// log.debug("[Shortest-Path #Clue-Instance-Locations] Clue location(s) ({}): {}", ls.size(), ls);
				if (!ls.isEmpty() && newLocationsSet.isEmpty())
				{
					newLocationsSet = ls;
				}
			}

			if (newLocationsSet.size() != this.lastLocations.size() || !Sets.difference(newLocationsSet, this.lastLocations).isEmpty())
			{
				log.debug("[Shortest-Path #Clue-Instance-Location] Clue location(s) changed: {} -> {}", this.lastLocations, newLocationsSet);
				this.lastLocations = newLocationsSet;

				if (!newLocationsSet.isEmpty())
				{
					shortestPathPlugin.setTargets(newLocationsSet.stream().map(WorldPointUtil::packWorldPoint).collect(Collectors.toSet()), false);
					this.wasLastPathSetByClue = true;
				}
				else
				{
					log.debug("[Shortest-Path #Clue] No locations found in clue scroll");
					if (shortestPathPlugin.getPathfinder() != null && !shortestPathPlugin.getPathfinder().getTargets().isEmpty() && wasLastPathSetByClue)
					{
						log.debug("[Shortest-Path #Clue] Resetting target location(s) due to empty clue");
						shortestPathPlugin.setTarget(WorldPointUtil.UNDEFINED);
						wasLastPathSetByClue = false;
					}
				}
			}

			this.clueScroll = clueScroll;
		}
		else
		{
			// There's no active clue scroll, so lets check to see if we need to reset the pathfinder
			if (this.clueScroll != null)
			{
				log.debug("[Shortest-Path #Clue] No active clue scroll (NULL)");
				if (shortestPathPlugin.getPathfinder() != null && !shortestPathPlugin.getPathfinder().getTargets().isEmpty() && wasLastPathSetByClue)
				{
					log.debug("[Shortest-Path #Clue] Resetting target location(s) due to null clue");
					shortestPathPlugin.setTarget(WorldPointUtil.UNDEFINED);
					wasLastPathSetByClue = false;
				}
				this.clueScroll = null;
			}
		}
	}
}
