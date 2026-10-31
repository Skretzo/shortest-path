package shortestpath.requirement.model;

import java.util.Collections;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Quest;

/**
 * Optional access requirements for a destination tile (e.g. bank booths). Empty requirements mean
 * the destination is always usable when reached.
 */
public final class DestinationRequirements
{
	/**
	 * All-zero skill array; no skill requirements.
	 */
	public static final DestinationRequirements EMPTY = new DestinationRequirements();

	@Getter
	private final SkillLevels skillLevels;
	@Getter
	private final Set<Quest> quests;
	@Getter
	private final Set<VarRequirement> varbits;
	@Getter
	private final Set<VarRequirement> varPlayers;

	private DestinationRequirements()
	{
		this.skillLevels = SkillLevels.NONE;
		this.quests = Collections.emptySet();
		this.varbits = Collections.emptySet();
		this.varPlayers = Collections.emptySet();
	}

	public DestinationRequirements(
		SkillLevels skillLevels,
		Set<Quest> quests,
		Set<VarRequirement> varbits,
		Set<VarRequirement> varPlayers)
	{
		this.skillLevels = skillLevels != null ? skillLevels : SkillLevels.NONE;
		this.quests = quests != null ? quests : Collections.emptySet();
		this.varbits = varbits != null ? varbits : Collections.emptySet();
		this.varPlayers = varPlayers != null ? varPlayers : Collections.emptySet();
	}

	public boolean isEmpty()
	{
		if (!quests.isEmpty())
		{
			return false;
		}
		if (!varbits.isEmpty() || !varPlayers.isEmpty())
		{
			return false;
		}
		return skillLevels.isEmpty();
	}
}
