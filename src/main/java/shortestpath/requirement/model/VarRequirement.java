package shortestpath.requirement.model;

import java.util.Map;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

/**
 * Represents a variable-based requirement for a transport.
 * This can be either a varbit or a varplayer requirement.
 *
 * <p>
 * Consolidates the previously separate TransportVarbit and TransportVarPlayer
 * classes which had identical logic.
 * </p>
 */
@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class VarRequirement
{

	private final VarType varType;
	private final int id;
	private final int value;
	private final VarCheckType checkType;



	/**
	 * Creates a varbit requirement.
	 */
	public static VarRequirement varbit(int id, int value, VarCheckType checkType)
	{
		return new VarRequirement(VarType.VARBIT, id, value, checkType);
	}

	/**
	 * Creates a varplayer requirement.
	 */
	public static VarRequirement varPlayer(int id, int value, VarCheckType checkType)
	{
		return new VarRequirement(VarType.VARPLAYER, id, value, checkType);
	}

	/**
	 * Checks if this requirement is satisfied given the current variable values.
	 *
	 * @param values A map of variable IDs to their current values
	 * @param currentTimeMinutes current game time in minutes
	 * @return true if the requirement is satisfied
	 */
	public boolean check(Map<Integer, Integer> values, long currentTimeMinutes)
	{
		Integer currentValue = values.get(id);
		if (currentValue == null)
		{
			return false;
		}
		return checkValue(currentValue, currentTimeMinutes);
	}

	/**
	 * Same logic as {@link #check(Map, long)} but with the variable value already resolved (e.g. from the client).
	 *
	 * @param currentTimeMinutes current game time in minutes
	 */
	public boolean checkValue(int currentValue, long currentTimeMinutes)
	{
		switch (checkType)
		{
			case EQUAL:
				return currentValue == value;
			case GREATER:
				return currentValue > value;
			case SMALLER:
				return currentValue < value;
			case BIT_SET:
				return (currentValue & value) > 0;
			case COOLDOWN_MINUTES:
				return (currentTimeMinutes - (long) currentValue) > value;
			default:
				return false;
		}
	}

	public boolean isVarbit()
	{
		return varType == VarType.VARBIT;
	}

	public boolean isVarPlayer()
	{
		return varType == VarType.VARPLAYER;
	}







	/**
	 * The type of variable this requirement checks.
	 */
	public enum VarType
	{
		VARBIT,
		VARPLAYER
	}
}
