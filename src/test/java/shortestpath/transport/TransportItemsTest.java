package shortestpath.transport;

import static org.junit.Assert.*;

import org.junit.Test;

public class TransportItemsTest {

    @Test
    public void gettersAndToStringWork() {
        int[][] items = new int[][] { new int[] {1, 2}, new int[] {3} };
        int[][] staves = new int[][] { new int[] {10}, new int[] {20, 30} };
        int[][] offhands = new int[][] { new int[] {100}, new int[] {200} };
        int[] quantities = new int[] { 5, 10 };

        TransportItems ti = new TransportItems(items, staves, offhands, quantities);

        assertArrayEquals(items, ti.getItems());
        assertArrayEquals(staves, ti.getStaves());
        assertArrayEquals(offhands, ti.getOffhands());
        assertArrayEquals(quantities, ti.getQuantities());

        String s = ti.toString();
        assertTrue(s.contains("["));
        assertTrue(s.contains("]"));
        assertTrue("toString should include items", s.contains("[1, 2]"));
        assertTrue("toString should include staves", s.contains("[10]"));
        assertTrue("toString should include offhands", s.contains("[100]"));
        assertTrue("toString should include quantities", s.contains("[5, 10]"));
    }

    @Test
    public void equalsAndHashCodeByContents() {
        int[][] items1 = new int[][] { new int[] {1, 2}, new int[] {3} };
        int[][] staves1 = new int[][] { new int[] {10}, new int[] {20, 30} };
        int[][] offhands1 = new int[][] { new int[] {100}, new int[] {200} };
        int[] quantities1 = new int[] { 5, 10 };

        int[][] items2 = new int[][] { new int[] {1, 2}, new int[] {3} };
        int[][] staves2 = new int[][] { new int[] {10}, new int[] {20, 30} };
        int[][] offhands2 = new int[][] { new int[] {100}, new int[] {200} };
        int[] quantities2 = new int[] { 5, 10 };

        TransportItems a = new TransportItems(items1, staves1, offhands1, quantities1);
        TransportItems b = new TransportItems(items2, staves2, offhands2, quantities2);

        // Lombok @EqualsAndHashCode performs deep array equality; same contents should be equal
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Changing any content should make them not equal
        int[] quantitiesDifferent = new int[] { 5, 11 };
        TransportItems d = new TransportItems(items2, staves2, offhands2, quantitiesDifferent);
        assertNotEquals(a, d);
    }
}
