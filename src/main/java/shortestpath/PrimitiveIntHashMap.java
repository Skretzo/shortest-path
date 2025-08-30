package shortestpath;

import java.util.Arrays;
import java.util.Collection;

/**
 * A lightweight hash map keyed by primitive {@code int} values with open addressed bucket arrays.
 * <p>
 * Each top-level slot in {@link #buckets} references an array (the "bucket") of entries. Buckets grow
 * individually when full, avoiding the cost of rehashing the entire map on every local expansion. When the
 * aggregate {@linkplain #size entry count} reaches the configured {@linkplain #capacity load threshold}, the
 * map resizes and rehashes all entries into a larger bucket array whose length is always a power of two.
 * <p>
 * This implementation is intentionally minimal and tailored for the plugin's pathfinding needs:
 * <ul>
 *   <li>No support for removing entries.</li>
 *   <li>No iteration views (keys, values, or entry set) are exposed.</li>
 *   <li>Collisions within a bucket are handled by linear probing inside the bucket array.</li>
 *   <li>Duplicate key insertion replaces the previous value, or appends collection contents when both the
 *       old and new values are {@link Collection}s (best effort; falls back to replacement on errors).</li>
 * </ul>
 * @param <V> the value type stored for each primitive {@code int} key. Must be non-null.
 */
public class PrimitiveIntHashMap<V> {
    private static final int MINIMUM_SIZE = 8;

    // Unless the hash function is really unbalanced, most things should fit within at least 8-element buckets
    // Buckets will grow as needed without forcing a rehash of the whole map
    private static final int DEFAULT_BUCKET_SIZE = 4;

    // How full the map should get before growing it again. Smaller values speed up lookup times at the expense of space
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Entry node storing a primitive key and associated value. */
    private static class IntNode<V> {
        private int key;
        private V value;

        private IntNode(int key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // If buckets become too large then it may be worth converting large buckets into an array-backed binary tree
    private IntNode<V>[][] buckets;
    private int size;
    private int capacity;
    private int maxSize;
    private int mask;
    private final float loadFactor;

    /**
     * Creates a new map with the specified initial size and the default load factor (0.75).
     *
     * @param initialSize initial expected number of elements; rounded to the next power of two internally.
     */
    public PrimitiveIntHashMap(int initialSize) {
        this(initialSize, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new map with the given initial size and load factor.
     *
     * @param initialSize initial expected number of elements; rounded up to maintain a power-of-two capacity.
     * @param loadFactor a value in the range {@code [0.0, 1.0]} determining when the map rehashes. Higher values
     *                   reduce space overhead but increase average lookup cost.
     * @throws IllegalArgumentException if {@code loadFactor} is outside the inclusive range 0..1.
     */
    public PrimitiveIntHashMap(int initialSize, float loadFactor) {
        if (loadFactor < 0.0f || loadFactor > 1.0f) {
            throw new IllegalArgumentException("Load factor must be between 0 and 1");
        }

        this.loadFactor = loadFactor;
        size = 0;
        setNewSize(initialSize);
        recreateArrays();
    }

    /**
     * Returns the number of key/value pairs currently stored.
     *
     * @return current entry count (always {@code >= 0}).
     */
    public int size() {
        return size;
    }

    /**
     * Retrieves the value mapped to the provided key, or {@code null} if absent.
     *
     * @param key primitive key to look up.
     * @return the mapped value, or {@code null} if the key does not exist.
     */
    public V get(int key) {
        return getOrDefault(key, null);
    }

    /**
     * Retrieves the value mapped to the provided key.
     *
     * @param key primitive key to look up.
     * @param defaultValue value to return if the key is not present.
     * @return the mapped value, or {@code defaultValue} when absent.
     */
    public V getOrDefault(int key, V defaultValue) {
        int bucket = getBucket(key);
        int index = bucketIndex(key, bucket);
        if (index == -1) {
            return defaultValue;
        }
        return buckets[bucket][index].value;
    }

    /**
     * Associates the specified value with the given key.
     * <p>
     * If a mapping already exists and both the existing and new values implement {@link Collection}, the method
     * attempts to append all elements of the new collection into the existing one. If the append fails (e.g., due
     * to incompatible element types or an unsupported operation) the existing value is replaced entirely.
     * Otherwise the existing value is simply replaced.
     *
     * @param key primitive key to insert or update.
     * @param value non-null value to associate.
     * @param <E> inferred element type if both values are collections.
     * @return the previous value mapped to {@code key} (if any), or {@code null} if inserting a new entry.
     * @throws IllegalArgumentException if {@code value} is {@code null}.
     */
    @SuppressWarnings("unchecked")
    public <E> V put(int key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot insert a null value");
        }

        int bucketIndex = getBucket(key);
        IntNode<V>[] bucket = buckets[bucketIndex];

        if (bucket == null) {
            buckets[bucketIndex] = createBucket(DEFAULT_BUCKET_SIZE);
            buckets[bucketIndex][0] = new IntNode<>(key, value);
            incrementSize();
            return null;
        }

        for (int i = 0; i < bucket.length; ++i) {
            if (bucket[i] == null) {
                bucket[i] = new IntNode<>(key, value);
                incrementSize();
                return null;
            } else if (bucket[i].key == key) {
                V previous = bucket[i].value;
                if (previous instanceof Collection<?> && value instanceof Collection<?>) { // append
                    try {
                        Collection<E> prevCollection = (Collection<E>) bucket[i].value;
                        Collection<E> newCollection = (Collection<E>) value;
                        prevCollection.addAll(newCollection);
                    } catch (ClassCastException | UnsupportedOperationException e) {
                        // If the collections contain incompatible types or the operation is not supported,
                        // just replace instead of append
                        bucket[i].value = value;
                    }
                } else { // replace
                    bucket[i].value = value;
                }
                return previous;
            }
        }

        // No space in the bucket, grow it
        growBucket(bucketIndex)[bucket.length] = new IntNode<>(key, value);
        incrementSize();
        return null;
    }

    /**
     * Hash function tuned for packed world point integer encodings. Mixes higher bits downward to
     * reduce clustering while remaining inexpensive.
     */
    private static int hash(int value) {
        return value ^ (value >>> 5) ^ (value >>> 25);
    }

    private int getBucket(int key) {
        return (hash(key) & 0x7FFFFFFF) & mask;
    }

    private int bucketIndex(int key, int bucketIndex) {
        IntNode<V>[] bucket = buckets[bucketIndex];
        if (bucket == null) {
            return -1;
        }

        for (int i = 0; i < bucket.length; ++i) {
            if (bucket[i] == null) {
                break;
            }
            if (bucket[i].key == key) {
                return i;
            }
        }

        // Searched the bucket and found nothing
        return -1;
    }

    private void incrementSize() {
        size++;
        if (size >= capacity) {
            rehash();
        }
    }

    private IntNode<V>[] growBucket(int bucketIndex) {
        IntNode<V>[] oldBucket = buckets[bucketIndex];
        IntNode<V>[] newBucket = createBucket(Math.min(oldBucket.length, Integer.MAX_VALUE / 2 - 4) * 2);
        System.arraycopy(oldBucket, 0, newBucket, 0, oldBucket.length);
        buckets[bucketIndex] = newBucket;
        return newBucket;
    }

    private int getNewMaxSize(int size) {
        int nextPow2 = -1 >>> Integer.numberOfLeadingZeros(size);
        if (nextPow2 >= (Integer.MAX_VALUE >>> 1)) {
            return (Integer.MAX_VALUE >>> 1) + 1;
        }
        return nextPow2 + 1;
    }

    private void setNewSize(int size) {
        if (size < MINIMUM_SIZE) {
            size = MINIMUM_SIZE - 1;
        }

        maxSize = getNewMaxSize(size);
        mask = maxSize - 1;
        capacity = (int)(maxSize * loadFactor);
    }

    private void growCapacity() {
        setNewSize(maxSize);
    }

    // Grow the bucket array then rehash all the values into new buckets and discard the old ones
    private void rehash() {
        growCapacity();

        IntNode<V>[][] oldBuckets = buckets;
        recreateArrays();

        for (int i = 0; i < oldBuckets.length; ++i) {
            IntNode<V>[] oldBucket = oldBuckets[i];
            if (oldBucket == null) {
                continue;
            }

            for (int ind = 0; ind < oldBucket.length; ++ind) {
                if (oldBucket[ind] == null) {
                    break;
                }

                int bucketIndex = getBucket(oldBucket[ind].key);
                IntNode<V>[] newBucket = buckets[bucketIndex];
                if (newBucket == null) {
                    newBucket = createBucket(DEFAULT_BUCKET_SIZE);
                    newBucket[0] = oldBucket[ind];
                    buckets[bucketIndex] = newBucket;
                } else {
                    int bInd;
                    for (bInd = 0; bInd < newBucket.length; ++bInd) {
                        if (newBucket[bInd] == null) {
                            newBucket[bInd] = oldBucket[ind];
                            break;
                        }
                    }

                    if (bInd >= newBucket.length) {
                        // No space in the target bucket; grow it and append the entry,
                        // but continue rehashing remaining entries instead of returning early.
                        IntNode<V>[] grown = growBucket(bucketIndex);
                        grown[newBucket.length] = oldBucket[ind];
                        continue;
                    }
                }
            }
        }
    }

    private void recreateArrays() {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        IntNode<V>[][] temp = (IntNode<V>[][])new IntNode[maxSize][];
        buckets = temp;
    }

    private IntNode<V>[] createBucket(int size) {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        IntNode<V>[] temp = (IntNode<V>[])new IntNode[size];
        return temp;
    }

    /**
     * Calculates the percentage of bucket capacity used prior to first null sentinel entries across all buckets.
     * <p>
     * For each non-null bucket, usage counts entries until the first {@code null} slot (which indicates the end
     * of populated entries for that bucket). The resulting percentage is: {@code usedEntrySlots / totalAllocatedSlots * 100}.
     *
     * @return approximate fullness percentage in the range {@code [0.0, 100.0]}.
     */
    public double calculateFullness() {
        int size = 0;
        int usedSize = 0;
        for (int i = 0; i < buckets.length; ++i) {
            if (buckets[i] == null) continue;
            size += buckets[i].length;
            for (int j = 0; j < buckets[i].length; ++j) {
                if (buckets[i][j] == null) {
                    usedSize += j;
                    break;
                }
            }
        }
        return 100.0 * (double)usedSize / (double)size;
    }

    /**
     * Removes all entries from the map. Bucket arrays are discarded and recreated lazily on subsequent inserts.
     */
    public void clear() {
        size = 0;
        Arrays.fill(buckets, null);
    }
}
