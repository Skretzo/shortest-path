package shortestpath;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TileCounter
{
	DISABLED("Disabled"),
	TRAVELLED("Travelled"),
	REMAINING("Remaining");

	private final String type;

	@Override
	public String toString()
	{
		return type;
	}
}
