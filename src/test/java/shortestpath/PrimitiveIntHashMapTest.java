package shortestpath;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class PrimitiveIntHashMapTest {

    // Copy of the map's private hash to synthesize collisions in tests
    private static int testHash(int value) {
        return value ^ (value >>> 5) ^ (value >>> 25);
    }

    @Test
    public void sizeAndBasicPutGet() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(8);
        assertEquals(0, map.size());
        assertNull(map.get(42));

        assertNull(map.put(1, "a"));
        assertNull(map.put(2, "b"));
        assertEquals(2, map.size());
        assertEquals("a", map.get(1));
        assertEquals("b", map.get(2));

        // overwrite existing non-collection
        String prev = map.put(1, "a2");
        assertEquals("a", prev);
        assertEquals("a2", map.get(1));
        assertEquals(2, map.size()); // size unchanged on replace
    }

    @Test
    public void getOrDefaultWorks() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(4);
        assertEquals(Integer.valueOf(99), map.getOrDefault(123, 99));
        map.put(123, 7);
        assertEquals(Integer.valueOf(7), map.getOrDefault(123, 99));
    }

    @Test(expected = IllegalArgumentException.class)
    public void putNullDisallowed() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(4);
        map.put(5, null);
    }

    @Test
    public void negativeKeysSupported() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(4);
        map.put(-1, "neg1");
        map.put(Integer.MIN_VALUE, "min");
        assertEquals("neg1", map.get(-1));
        assertEquals("min", map.get(Integer.MIN_VALUE));
    }

    @Test
    public void clearResetsState() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(16);
        for (int i = 0; i < 10; i++) {
            map.put(i, "v" + i);
        }
        assertTrue(map.size() > 0);
        map.clear();
        assertEquals(0, map.size());
        for (int i = 0; i < 10; i++) {
            assertNull(map.get(i));
        }
    }

    @Test
    public void collectionValuesAppendOnDuplicateKey() {
        PrimitiveIntHashMap<List<Integer>> map = new PrimitiveIntHashMap<>(8);
        List<Integer> a = new ArrayList<>(Arrays.asList(1, 2));
        List<Integer> b = new ArrayList<>(Arrays.asList(3, 4, 5));

        assertNull(map.put(7, a));
        List<Integer> prev = map.put(7, b);

        // Returned value is the previous instance and it has been mutated by addAll
        assertSame(a, prev);
        assertSame(a, map.get(7));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), map.get(7));
        assertEquals(1, map.size());
    }

    @Test
    public void rehashPreservesEntries() {
        // Use default load factor so rehashes will happen as size grows
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(8);

        int n = 10_000;
        for (int i = 0; i < n; i++) {
            map.put(i, "v" + i);
        }
        assertEquals(n, map.size());

        // verify random subset
        Random rnd = new Random(123);
        for (int i = 0; i < 2000; i++) {
            int k = rnd.nextInt(n);
            assertEquals("v" + k, map.get(k));
        }
    }

    @Test
    public void heavyCollisionsGrowBucketWithoutRehash() {
        // With loadFactor=1.0 and initial size < MINIMUM_SIZE, maxSize becomes 8 and
        // capacity is 8.
        // We'll place 6 entries into the SAME bucket so the internal bucket needs to
        // grow beyond DEFAULT_BUCKET_SIZE (4)
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(1, 1.0f);
        int mask = 8 - 1; // maxSize for initial map (given MINIMUM_SIZE=8)

        int targetBucket = 0; // any bucket works; pick 0
        List<Integer> collidingKeys = new ArrayList<>();
        for (int k = 0; collidingKeys.size() < 6 && k < 100_000; k++) {
            int bucket = (testHash(k) & 0x7FFFFFFF) & mask;
            if (bucket == targetBucket)
                collidingKeys.add(k);
        }
        assertEquals(6, collidingKeys.size());

        int i = 0;
        for (int key : collidingKeys) {
            assertNull(map.put(key, "v" + (i++)));
        }
        assertEquals(6, map.size());
        for (int idx = 0; idx < collidingKeys.size(); idx++) {
            int key = collidingKeys.get(idx);
            assertEquals("v" + idx, map.get(key));
        }
    }

    @Test
    public void calculateFullnessWithinBounds() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8);
        double f0 = map.calculateFullness();
        assertTrue("empty map fullness should be NaN", Double.isNaN(f0));

        for (int i = 0; i < 100; i++)
            map.put(i, i);
        double f1 = map.calculateFullness();
        assertTrue("fullness in [0,100]", f1 >= 0.0 && f1 <= 100.0);
    }

    @Test
    public void constructorRejectsInvalidLoadFactor() {
        try {
            new PrimitiveIntHashMap<>(8, -0.1f);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new PrimitiveIntHashMap<>(8, 1.1f);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        // Edge values allowed
        new PrimitiveIntHashMap<>(8, 0.0f);
        new PrimitiveIntHashMap<>(8, 1.0f);
    }

    @Test
    public void replacingDoesNotIncreaseSize() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(8);
        assertNull(map.put(10, "a"));
        int s1 = map.size();
        String prev = map.put(10, "b");
        assertEquals("a", prev);
        assertEquals(s1, map.size());
        assertEquals("b", map.get(10));
    }

    @Test
    public void manyRandomInsertionsAndLookups() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(32);
        Random rnd = new Random(42);
        Set<Integer> keys = new HashSet<>();

        for (int i = 0; i < 50_000; i++) {
            int k = rnd.nextInt();
            // avoid null values
            int v = i;
            Integer prev = map.put(k, v);
            if (!keys.add(k)) {
                // replacement path: previous value must be non-null
                assertNotNull(prev);
            }
            assertEquals(Integer.valueOf(v), map.get(k));
        }

        assertEquals(keys.size(), map.size());

        // sample lookups
        int checked = 0;
        for (int k : keys) {
            assertNotNull(map.get(k));
            if (++checked > 2000)
                break;
        }
    }

    @Test
    public void zeroLoadFactorCreatesMinimumCapacity() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(4, 0.0f);
        // With loadFactor 0.0, capacity should be 0, but map should still be usable
        assertNull(map.put(1, "test"));
        assertEquals("test", map.get(1));
        assertEquals(1, map.size());
    }

    @Test
    public void emptyCollectionAppending() {
        PrimitiveIntHashMap<List<Integer>> map = new PrimitiveIntHashMap<>(8);
        List<Integer> originalList = new ArrayList<>(Arrays.asList(1, 2, 3));
        List<Integer> emptyList = new ArrayList<>();

        map.put(10, originalList);
        List<Integer> prev = map.put(10, emptyList);

        // Original list should be returned and remain unchanged since empty list adds
        // nothing
        assertSame(originalList, prev);
        assertSame(originalList, map.get(10));
        assertEquals(Arrays.asList(1, 2, 3), map.get(10));
    }

    @Test
    public void mixedCollectionAndNonCollectionReplace() {
        PrimitiveIntHashMap<Object> map = new PrimitiveIntHashMap<>(8);
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2));

        // Start with non-collection value
        map.put(5, "string");

        // Replace with collection - should replace, not append
        Object prev = map.put(5, list);
        assertEquals("string", prev);
        assertSame(list, map.get(5));

        // Replace collection with non-collection - should replace, not append
        prev = map.put(5, "another string");
        assertSame(list, prev);
        assertEquals("another string", map.get(5));
    }

    @Test
    public void calculateFullnessEdgeCases() {
        // Test with completely empty map
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8);
        assertTrue("Empty map fullness should be NaN", Double.isNaN(map.calculateFullness()));

        // Test with single element
        map.put(1, 1);
        double fullness = map.calculateFullness();
        assertTrue("Single element fullness should be valid", fullness >= 0.0 && fullness <= 100.0);

        // Test with all buckets having exactly one element
        map.clear();
        // Fill exactly one slot in each bucket if possible
        for (int i = 0; i < 8; i++) {
            map.put(i * 1000, i); // Use keys that likely hash to different buckets
        }
        fullness = map.calculateFullness();
        assertTrue("Evenly distributed fullness should be valid", fullness >= 0.0 && fullness <= 100.0);
    }

    @Test
    public void integerOverflowKeys() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(16);

        // Test keys near integer overflow boundaries
        map.put(Integer.MAX_VALUE, "max");
        map.put(Integer.MAX_VALUE - 1, "max-1");
        map.put(Integer.MIN_VALUE, "min");
        map.put(Integer.MIN_VALUE + 1, "min+1");
        map.put(0, "zero");
        map.put(-1, "neg-one");

        assertEquals("max", map.get(Integer.MAX_VALUE));
        assertEquals("max-1", map.get(Integer.MAX_VALUE - 1));
        assertEquals("min", map.get(Integer.MIN_VALUE));
        assertEquals("min+1", map.get(Integer.MIN_VALUE + 1));
        assertEquals("zero", map.get(0));
        assertEquals("neg-one", map.get(-1));
        assertEquals(6, map.size());
    }

    @Test
    public void extremeCollisionScenario() {
        // Force many collisions by using a small initial size and load factor 1.0
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(2, 1.0f);

        // Add many items that will definitely cause collisions and bucket growth
        for (int i = 0; i < 100; i++) {
            map.put(i, "value" + i);
        }

        // Verify all values are still accessible
        for (int i = 0; i < 100; i++) {
            assertEquals("value" + i, map.get(i));
        }
        assertEquals(100, map.size());
    }

    @Test
    public void largeCollectionAppending() {
        PrimitiveIntHashMap<List<Integer>> map = new PrimitiveIntHashMap<>(8);
        List<Integer> list1 = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list1.add(i);
        }

        List<Integer> list2 = new ArrayList<>();
        for (int i = 10000; i < 20000; i++) {
            list2.add(i);
        }

        map.put(1, list1);
        List<Integer> prev = map.put(1, list2);

        assertSame(list1, prev);
        assertSame(list1, map.get(1));
        assertEquals(20000, map.get(1).size());
        assertEquals(Integer.valueOf(0), map.get(1).get(0));
        assertEquals(Integer.valueOf(19999), map.get(1).get(19999));
    }

    @Test
    public void rehashDuringCollectionAppend() {
        // Create a map that will trigger rehash when a specific key is added
        PrimitiveIntHashMap<List<Integer>> map = new PrimitiveIntHashMap<>(4, 0.75f);

        // Add items until we're close to the rehash threshold
        map.put(1, new ArrayList<>(Arrays.asList(1)));
        map.put(2, new ArrayList<>(Arrays.asList(2)));

        // This should trigger a rehash when the collection is appended
        List<Integer> appendList = new ArrayList<>(Arrays.asList(100, 200));
        List<Integer> prev = map.put(1, appendList);

        assertNotNull(prev);
        assertEquals(Arrays.asList(1, 100, 200), map.get(1));
        assertEquals(Arrays.asList(2), map.get(2));
        assertEquals(2, map.size());
    }

    @Test
    public void sameKeyDifferentHashBehavior() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(8);

        // Test that same key always returns same value regardless of hash collisions
        map.put(42, "first");
        assertEquals("first", map.get(42));

        String prev = map.put(42, "second");
        assertEquals("first", prev);
        assertEquals("second", map.get(42));
        assertEquals(1, map.size()); // Size should not change
    }

    @Test
    public void clearAfterComplexOperations() {
        PrimitiveIntHashMap<List<String>> map = new PrimitiveIntHashMap<>(4);

        // Perform complex operations that cause growth and collection appending
        for (int i = 0; i < 50; i++) {
            List<String> list = new ArrayList<>(Arrays.asList("item" + i));
            map.put(i % 10, list); // This will cause appending for repeated keys
        }

        assertTrue(map.size() > 0);
        double fullness = map.calculateFullness();
        assertTrue(!Double.isNaN(fullness));

        // Clear should reset everything
        map.clear();
        assertEquals(0, map.size());
        assertTrue(Double.isNaN(map.calculateFullness()));

        // Should be able to use normally after clear
        map.put(1, new ArrayList<>(Arrays.asList("after clear")));
        assertEquals(1, map.size());
        assertEquals(Arrays.asList("after clear"), map.get(1));
    }

    @Test
    public void incompatibleCollectionTypes() {
        PrimitiveIntHashMap<Object> map = new PrimitiveIntHashMap<>(8);

        // Put a list of strings
        List<String> stringList = new ArrayList<>(Arrays.asList("hello", "world"));
        map.put(10, stringList);

        // Try to append a list of integers - this should handle the ClassCastException
        // gracefully
        List<Integer> intList = new ArrayList<>(Arrays.asList(1, 2, 3));
        try {
            Object prev = map.put(10, intList);
            // If we get here, the implementation should have handled the type mismatch
            assertSame(stringList, prev);
        } catch (ClassCastException e) {
            // This is also acceptable behavior - the implementation throws when types don't
            // match
            // In this case, verify the original value is still there
            assertSame(stringList, map.get(10));
        }
    }

    @Test
    public void bucketGrowthIntegerOverflow() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(8);

        // This test ensures that bucket growth doesn't cause integer overflow
        // We can't easily test the actual overflow case, but we can test large bucket
        // growth
        for (int i = 0; i < 1000; i++) {
            map.put(i, "value" + i);
        }

        // Verify all values are still accessible after many bucket growth operations
        for (int i = 0; i < 1000; i++) {
            assertEquals("value" + i, map.get(i));
        }
        assertEquals(1000, map.size());
    }

    @Test
    public void constructorHandlesNegativeInitialSize() {
        PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(-5);
        assertNull(map.get(1));
        map.put(1, "a");
        map.put(2, "b");
        assertEquals("a", map.get(1));
        assertEquals("b", map.get(2));
        assertEquals(2, map.size());
    }

    @Test
    public void appendToUnmodifiableCollectionReplaces() {
        PrimitiveIntHashMap<List<String>> map = new PrimitiveIntHashMap<>(8);
        List<String> fixed = Arrays.asList("x", "y"); // fixed-size list => addAll throws UnsupportedOperationException
        List<String> replacement = new ArrayList<>(Arrays.asList("a", "b", "c"));

        assertNull(map.put(42, fixed));
        List<String> prev = map.put(42, replacement);

        // Should have replaced rather than appended due to
        // UnsupportedOperationException
        assertSame(fixed, prev);
        assertSame(replacement, map.get(42));
        assertEquals(Arrays.asList("a", "b", "c"), map.get(42));
        assertEquals(1, map.size());
    }

    @Test
    public void getOrDefaultAllowsNullDefault() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8);
        assertNull(map.getOrDefault(999, null));
        map.put(999, 123);
        assertEquals(Integer.valueOf(123), map.getOrDefault(999, null));
    }

    @Test
    public void selfCollectionAppendDuplicatesSafely() {
        PrimitiveIntHashMap<List<Integer>> map = new PrimitiveIntHashMap<>(8);
        List<Integer> list = new ArrayList<>(Arrays.asList(1, 2));
        map.put(7, list);
        // Appending the same instance to itself should not crash and should duplicate
        // elements
        List<Integer> prev = map.put(7, list);
        assertSame(list, prev);
        assertSame(list, map.get(7));
        assertEquals(4, map.get(7).size());
        assertEquals(Arrays.asList(1, 2, 1, 2), map.get(7));
        assertEquals(1, map.size());
    }

    @Test
    public void zeroLoadFactorStress() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8, 0.0f);
        // Keep this small to avoid exponential rehash growth with loadFactor=0.0
        for (int i = 0; i < 16; i++) {
            map.put(i, i);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals(Integer.valueOf(i), map.get(i));
        }
        assertEquals(16, map.size());
    }
}
