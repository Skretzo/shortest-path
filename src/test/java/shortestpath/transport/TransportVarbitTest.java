package shortestpath.transport;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TransportVarbitTest {

    @Test
    public void testEqual() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(1, 5);
        TransportVarbit v = new TransportVarbit(1, 5, TransportVarCheck.EQUAL);
        assertTrue(v.check(values));
        values.put(1, 4);
        assertFalse(v.check(values));
    }

    @Test
    public void testGreater() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(2, 10);
        TransportVarbit v = new TransportVarbit(2, 5, TransportVarCheck.GREATER);
        assertTrue(v.check(values));
        values.put(2, 5);
        assertFalse(v.check(values));
    }

    @Test
    public void testSmaller() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(3, 3);
        TransportVarbit v = new TransportVarbit(3, 5, TransportVarCheck.SMALLER);
        assertTrue(v.check(values));
        values.put(3, 5);
        assertFalse(v.check(values));
    }

    @Test
    public void testBitSet() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(4, 0b1010);
        TransportVarbit v = new TransportVarbit(4, 0b0010, TransportVarCheck.BIT_SET);
        assertTrue(v.check(values));
        v = new TransportVarbit(4, 0b0100, TransportVarCheck.BIT_SET);
        assertFalse(v.check(values));
    }

    @Test
    public void testCooldownMinutes() {
        Map<Integer, Integer> values = new HashMap<>();
        long nowMinutes = System.currentTimeMillis() / 60000;
        values.put(5, (int)(nowMinutes - 10)); // stored timestamp 10 minutes ago

        TransportVarbit v = new TransportVarbit(5, 5, TransportVarCheck.COOLDOWN_MINUTES);
        assertTrue(v.check(values)); // 10 > 5

        values.put(5, (int)(nowMinutes - 3)); // 3 minutes ago
        assertFalse(v.check(values)); // 3 > 5 is false
    }
}
