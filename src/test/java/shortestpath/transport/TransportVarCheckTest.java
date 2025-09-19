package shortestpath.transport;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TransportVarCheckTest {

    @Test
    public void codesMatch() {
        assertEquals("&", TransportVarCheck.BIT_SET.getCode());
        assertEquals("@", TransportVarCheck.COOLDOWN_MINUTES.getCode());
        assertEquals("=", TransportVarCheck.EQUAL.getCode());
        assertEquals(">", TransportVarCheck.GREATER.getCode());
        assertEquals("<", TransportVarCheck.SMALLER.getCode());
    }
}
