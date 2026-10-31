package shortestpath.requirement.model;

import lombok.Value;

/** Counts only released, visible, non-holiday tracks required for the music cape. */
@Value
public class MusicTracks
{
	int unlocked;
	int required;
}
