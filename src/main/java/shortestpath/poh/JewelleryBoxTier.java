package shortestpath.poh;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum JewelleryBoxTier
{
	NONE("None"),
	BASIC("Basic"),
	FANCY("Fancy"),
	ORNATE("Ornate"),
	;

	private final String type;

	@Override
	public String toString()
	{
		return type;
	}
}
