package shortestpath;

import org.junit.Assert;
import org.junit.Test;

public class PrimitiveIntListTests {
    @Test
    public void tryAddAndGet() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(10);
        intList.add(11);
        Assert.assertEquals(2, intList.size());
        Assert.assertEquals(10, intList.get(0));
        Assert.assertEquals(11, intList.get(1));
    }

    @Test
    public void tryGrow() {
        PrimitiveIntList intList = new PrimitiveIntList(4);
        intList.add(10);
        intList.add(11);
        intList.add(12);
        intList.add(13);
        intList.add(14);
        Assert.assertEquals(5, intList.size());
        int sum = 0;
        for (int i = 0; i < intList.size(); i++) {
            sum += intList.get(i);
        }
        Assert.assertEquals(10 + 11 + 12 + 13 + 14, sum);
    }

    @Test
    public void tryInitialize() {
        PrimitiveIntList intList = new PrimitiveIntList(3, true);
        Assert.assertEquals(3, intList.size());
        Assert.assertEquals(0, intList.get(0));
        Assert.assertEquals(0, intList.get(1));
        Assert.assertEquals(0, intList.get(2));
        intList.set(0, 10);
        intList.set(1, 11);
        intList.set(2, 12);
        Assert.assertEquals(10, intList.get(0));
        Assert.assertEquals(11, intList.get(1));
        Assert.assertEquals(12, intList.get(2));
    }

    @Test
    public void tryIterate() {
        int sum = 0;
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(10);
        intList.add(11);
        for (int i = 0; i < intList.size(); i++) {
            sum += intList.get(i);
        }
        Assert.assertEquals(10 + 11, sum);
    }

    @Test
    public void tryRemove() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(10);
        intList.add(11);
        Assert.assertEquals(2, intList.size());
        Assert.assertEquals(10, intList.removeAt(0));
        Assert.assertEquals(true, intList.remove(11));
        Assert.assertEquals(true, intList.isEmpty());
    }

    @Test
    public void defaultConstructor() {
        PrimitiveIntList intList = new PrimitiveIntList();
        Assert.assertEquals(0, intList.size());
        Assert.assertTrue(intList.isEmpty());
    }

    @Test
    public void capacityOnlyConstructor() {
        PrimitiveIntList intList = new PrimitiveIntList(5);
        // The list is not initialized so the size is 0
        Assert.assertEquals(0, intList.size());
        Assert.assertTrue(intList.isEmpty());
        intList.add(42);
        Assert.assertEquals(1, intList.size());
        Assert.assertEquals(42, intList.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeCapacityConstructor() {
        new PrimitiveIntList(-1);
    }

        @Test
    public void zeroCapacityConstructor() {
        PrimitiveIntList intList = new PrimitiveIntList(0);
        Assert.assertEquals(0, intList.size());
        Assert.assertTrue(intList.isEmpty());
    }
    
    @Test
    public void addAtIndex() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1);
        intList.add(2);
        intList.add(1, 99); // insert in middle
        Assert.assertEquals(3, intList.size());
        Assert.assertEquals(1, intList.get(0));
        Assert.assertEquals(99, intList.get(1));
        Assert.assertEquals(2, intList.get(2));
        intList.add(3, 100); // insert at end (index == size before add)
        Assert.assertEquals(4, intList.size());
        Assert.assertEquals(100, intList.get(3));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void addAtIndexOutOfBoundsHigh() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1, 5);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void addAtIndexOutOfBoundsNegative() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(-1, 5);
    }

    @Test
    public void setReturnsOldValue() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(10);
        int old = intList.set(0, 20);
        Assert.assertEquals(10, old);
        Assert.assertEquals(20, intList.get(0));
    }

    @Test
    public void containsAndIndexOf() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(5);
        intList.add(6);
        intList.add(7);
        Assert.assertTrue(intList.contains(6));
        Assert.assertEquals(1, intList.indexOf(6));
        Assert.assertFalse(intList.contains(8));
        Assert.assertEquals(-1, intList.indexOf(8));
    }

    @Test
    public void removeNotPresent() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1);
        Assert.assertFalse(intList.remove(2));
        Assert.assertEquals(1, intList.size());
    }

    @Test
    public void clearEmptiesList() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1);
        intList.add(2);
        intList.clear();
        Assert.assertEquals(0, intList.size());
        Assert.assertTrue(intList.isEmpty());
        intList.add(3); // ensure list still usable
        Assert.assertEquals(1, intList.size());
        Assert.assertEquals(3, intList.get(0));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void getOutOfBounds() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1);
        intList.get(1); // size == 1 so index 1 invalid
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void setOutOfBounds() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1);
        intList.set(1, 2);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void removeAtOutOfBounds() {
        PrimitiveIntList intList = new PrimitiveIntList();
        intList.add(1);
        intList.removeAt(1);
    }

    @Test
    public void ensureCapacityNoOpAndGrowth() {
        PrimitiveIntList intList = new PrimitiveIntList();
        // ensureCapacity(0) should be a no-op and size unchanged
        intList.ensureCapacity(0);
        Assert.assertEquals(0, intList.size());

        // Pre-grow to capacity for 500 elements
        intList.ensureCapacity(500);
        // Add 500 elements and assert size; this should work without errors
        for (int i = 0; i < 500; i++) {
            intList.add(i);
        }
        Assert.assertEquals(500, intList.size());
        // Spot check a few values
        Assert.assertEquals(0, intList.get(0));
        Assert.assertEquals(123, intList.get(123));
        Assert.assertEquals(499, intList.get(499));
    }

    @Test
    public void ensureCapacityIdempotentForSmaller() {
        PrimitiveIntList intList = new PrimitiveIntList(4);
        intList.add(1);
        intList.add(2);
        intList.ensureCapacity(2); // already at least 2; should not affect size
        Assert.assertEquals(2, intList.size());
        Assert.assertEquals(1, intList.get(0));
        Assert.assertEquals(2, intList.get(1));
    }
}
