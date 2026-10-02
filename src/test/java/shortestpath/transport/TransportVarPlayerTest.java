package shortestpath.transport;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import shortestpath.transport.parser.VarCheckType;
import shortestpath.transport.parser.VarRequirement;

public class TransportVarPlayerTest
{

	@Test
	public void testEqual()
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(1, 5);
		VarRequirement v = VarRequirement.varPlayer(1, 5, VarCheckType.EQUAL);
		assertTrue(v.check(values, 0));
		values.put(1, 4);
		assertFalse(v.check(values, 0));
	}

	@Test
	public void testGreater()
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(2, 10);
		VarRequirement v = VarRequirement.varPlayer(2, 5, VarCheckType.GREATER);
		assertTrue(v.check(values, 0));
		values.put(2, 5);
		assertFalse(v.check(values, 0));
	}

	@Test
	public void testSmaller()
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(3, 3);
		VarRequirement v = VarRequirement.varPlayer(3, 5, VarCheckType.SMALLER);
		assertTrue(v.check(values, 0));
		values.put(3, 5);
		assertFalse(v.check(values, 0));
	}

	@Test
	public void testBitSet()
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(4, 0b1010);
		VarRequirement v = VarRequirement.varPlayer(4, 0b0010, VarCheckType.BIT_SET);
		assertTrue(v.check(values, 0));
		v = VarRequirement.varPlayer(4, 0b0100, VarCheckType.BIT_SET);
		assertFalse(v.check(values, 0));
	}

	@Test
	public void testCooldownMinutes()
	{
		Map<Integer, Integer> values = new HashMap<>();
		long nowMinutes = 100000000L;
		values.put(5, 99999979);

		VarRequirement v = VarRequirement.varPlayer(5, 20, VarCheckType.COOLDOWN_MINUTES);
		assertTrue(v.check(values, nowMinutes));

		values.put(5, 99999981);
		assertFalse(v.check(values, nowMinutes));
		values.put(5, 99999980);
		assertFalse(v.check(values, nowMinutes));
	}

	@Test
	public void ordinaryRequirementIgnoresTime()
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(6, 5);
		VarRequirement v = VarRequirement.varPlayer(6, 5, VarCheckType.EQUAL);
		assertTrue(v.check(values, 0));
		assertTrue(v.check(values, 100000000));
	}
}
