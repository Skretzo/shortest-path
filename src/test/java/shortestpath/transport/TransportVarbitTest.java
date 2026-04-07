package shortestpath.transport;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import shortestpath.transport.parser.VarCheckType;
import shortestpath.transport.parser.VarRequirement;

public class TransportVarbitTest {

    @Test
    public void testEqual() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(1, 5);
        VarRequirement v = VarRequirement.varbit(1, 5, VarCheckType.EQUAL);
        assertTrue(v.check(values));
        values.put(1, 4);
        assertFalse(v.check(values));
    }

    @Test
    public void testGreater() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(2, 10);
        VarRequirement v = VarRequirement.varbit(2, 5, VarCheckType.GREATER);
        assertTrue(v.check(values));
        values.put(2, 5);
        assertFalse(v.check(values));
    }

    @Test
    public void testSmaller() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(3, 3);
        VarRequirement v = VarRequirement.varbit(3, 5, VarCheckType.SMALLER);
        assertTrue(v.check(values));
        values.put(3, 5);
        assertFalse(v.check(values));
    }

    @Test
    public void testBitSet() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(4, 0b1010);
        VarRequirement v = VarRequirement.varbit(4, 0b0010, VarCheckType.BIT_SET);
        assertTrue(v.check(values));
        v = VarRequirement.varbit(4, 0b0100, VarCheckType.BIT_SET);
        assertFalse(v.check(values));
    }

    @Test
    public void testCooldownMinutes() {
        Map<Integer, Integer> values = new HashMap<>();
        long nowMinutes = System.currentTimeMillis() / 60000;
        values.put(5, (int)(nowMinutes - 10)); // stored timestamp 10 minutes ago

        VarRequirement v = VarRequirement.varbit(5, 5, VarCheckType.COOLDOWN_MINUTES);
        assertTrue(v.check(values)); // 10 > 5

        values.put(5, (int)(nowMinutes - 3)); // 3 minutes ago
        assertFalse(v.check(values)); // 3 > 5 is false
    }
}
