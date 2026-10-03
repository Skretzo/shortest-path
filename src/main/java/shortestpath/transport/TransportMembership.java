package shortestpath.transport;

/**
 * Which world types a transport can be used on, from the {@code F2P} column of a
 * transport TSV: a blank cell (or no column) is {@link #MEMBERS}, {@code f2p} is
 * {@link #F2P} and {@code f2p-only} is {@link #F2P_ONLY}.
 */
public enum TransportMembership
{
	/**
	 * Usable on members worlds only. The default for a blank cell.
	 */
	MEMBERS(""),
	/**
	 * Usable on both free-to-play and members worlds.
	 */
	F2P("f2p"),
	/**
	 * Usable on free-to-play worlds only, e.g. the Castle Wars lobby doors to the Ferox Enclave.
	 */
	F2P_ONLY("f2p-only");

	private final String value;

	TransportMembership(String value)
	{
		this.value = value;
	}

	/**
	 * Parses an {@code F2P} column value, or returns {@code null} if it is not recognised.
	 */
	public static TransportMembership fromValue(String value)
	{
		String normalized = value == null ? "" : value.trim();
		for (TransportMembership membership : values())
		{
			if (membership.value.equals(normalized))
			{
				return membership;
			}
		}
		return null;
	}

	public boolean isUsableOn(boolean membersWorld)
	{
		switch (this)
		{
			case F2P:
				return true;
			case F2P_ONLY:
				return !membersWorld;
			default:
				return membersWorld;
		}
	}

	/**
	 * Membership of a transport joined from an origin-only and a destination-only row:
	 * usable only where both are, or {@code null} if no world type allows both.
	 */
	public static TransportMembership combine(TransportMembership first, TransportMembership second)
	{
		if (first == second)
		{
			return first;
		}
		if (first == F2P)
		{
			return second;
		}
		if (second == F2P)
		{
			return first;
		}
		return null;
	}
}
