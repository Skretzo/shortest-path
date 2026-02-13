package shortestpath.transport;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import shortestpath.transport.parser.VarCheckType;

public class TransportVarCheckTest {

    @Test
    public void codesMatch() {
        assertEquals("&", VarCheckType.BIT_SET.getCode());
        assertEquals("@", VarCheckType.COOLDOWN_MINUTES.getCode());
        assertEquals("=", VarCheckType.EQUAL.getCode());
        assertEquals(">", VarCheckType.GREATER.getCode());
        assertEquals("<", VarCheckType.SMALLER.getCode());
    }
}
