package shortestpath;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TileStyle
{
	TILES("Tiles"),
	LINES("Lines"),
	ARROW_LINE("Arrow line"),
	TURN_MARKERS("Turn markers"),
	TRACER("Tracer"),
	SPRITE_MARKERS("Sprite markers");

	private final String type;

	@Override
	public String toString()
	{
		return type;
	}
}
