package shortestpath.transport;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class TransportVarPlayerTest {

    @Test
    public void testEqual() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(1, 5);
        TransportVarPlayer v = new TransportVarPlayer(1, 5, TransportVarCheck.EQUAL);
        assertTrue(v.check(values));
        values.put(1, 4);
        assertFalse(v.check(values));
    }

    @Test
    public void testGreater() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(2, 10);
        TransportVarPlayer v = new TransportVarPlayer(2, 5, TransportVarCheck.GREATER);
        assertTrue(v.check(values));
        values.put(2, 5);
        assertFalse(v.check(values));
    }

    @Test
    public void testSmaller() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(3, 3);
        TransportVarPlayer v = new TransportVarPlayer(3, 5, TransportVarCheck.SMALLER);
        assertTrue(v.check(values));
        values.put(3, 5);
        assertFalse(v.check(values));
    }

    @Test
    public void testBitSet() {
        Map<Integer, Integer> values = new HashMap<>();
        values.put(4, 0b1010);
        TransportVarPlayer v = new TransportVarPlayer(4, 0b0010, TransportVarCheck.BIT_SET);
        assertTrue(v.check(values));
        v = new TransportVarPlayer(4, 0b0100, TransportVarCheck.BIT_SET);
        assertFalse(v.check(values));
    }

    @Test
    public void testCooldownMinutes() {
        Map<Integer, Integer> values = new HashMap<>();
        long nowMinutes = System.currentTimeMillis() / 60000;
        values.put(5, (int)(nowMinutes - 10));

        TransportVarPlayer v = new TransportVarPlayer(5, 5, TransportVarCheck.COOLDOWN_MINUTES);
        assertTrue(v.check(values));

        values.put(5, (int)(nowMinutes - 3));
        assertFalse(v.check(values));
    }
}
