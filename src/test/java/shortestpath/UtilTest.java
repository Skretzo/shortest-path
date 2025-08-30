package shortestpath;

import org.junit.Test;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Unit tests for {@link Util} helper methods.
 */
public class UtilTest {

    // --- readAllBytes -----------------------------------------------------

    @Test
    public void readAllBytesEmptyStream() throws IOException {
        byte[] data = Util.readAllBytes(new ByteArrayInputStream(new byte[0]));
        Assert.assertNotNull(data);
        Assert.assertEquals(0, data.length);
    }

    @Test
    public void readAllBytesSmall() throws IOException {
        byte[] expected = {1,2,3,4,5};
        byte[] data = Util.readAllBytes(new ByteArrayInputStream(expected));
        Assert.assertArrayEquals(expected, data);
    }

    @Test
    public void readAllBytesLargerThanBuffer() throws IOException {
        // Create data larger than the internal 1024 byte buffer to exercise loop logic
        byte[] expected = new byte[4096];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte)(i * 31); // deterministic pattern
        }
        byte[] data = Util.readAllBytes(new ByteArrayInputStream(expected));
        Assert.assertArrayEquals(expected, data);
    }

    @Test(expected = IOException.class)
    public void readAllBytesPropagatesIOException() throws IOException {
        // InputStream that throws after first read
        InputStream failing = new FilterInputStream(new ByteArrayInputStream(new byte[]{1,2,3})) {
            private boolean first = true;
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (first) {
                    first = false;
                    return super.read(b, off, len);
                }
                throw new IOException("boom");
            }
        };
        // Should throw on second read attempt
        Util.readAllBytes(failing);
    }

    // --- concatenate ------------------------------------------------------

    @Test
    public void concatenateAllNullOrEmptyReturnsNull() {
        Assert.assertNull(Util.concatenate(new int[][]{null, new int[0], null}));
    }

    @Test
    public void concatenateSingleArrayIdentity() {
        int[] a = {1,2,3};
        Assert.assertArrayEquals(a, Util.concatenate(new int[][]{a}));
    }

    @Test
    public void concatenateSkipsNulls() {
        int[] a = {1,2};
        int[] b = null;
        int[] c = {3};
        int[] result = Util.concatenate(new int[][]{a,b,c});
        Assert.assertArrayEquals(new int[]{1,2,3}, result);
    }

    @Test
    public void concatenateMultiple() {
        int[] a = {1};
        int[] b = {2,3};
        int[] c = {4,5,6};
        int[] result = Util.concatenate(new int[][]{a,b,c});
        Assert.assertArrayEquals(new int[]{1,2,3,4,5,6}, result);
    }

    @Test
    public void concatenateLarge() {
        // Use random sizes to stress size accumulation & copy loop
        Random rnd = new Random(123);
        int[][] arrays = new int[50][];
        int total = 0;
        for (int i = 0; i < arrays.length; i++) {
            int len = rnd.nextInt(10); // small to keep test fast
            if (len == 0) {
                arrays[i] = new int[0];
            } else {
                arrays[i] = new int[len];
                for (int j = 0; j < len; j++) arrays[i][j] = i * 100 + j;
                total += len;
            }
        }
        int[] combined = Util.concatenate(arrays);
        if (total == 0) {
            Assert.assertNull(combined);
        } else {
            int count = 0;
            for (int i = 0; i < arrays.length; i++) {
                int[] arr = arrays[i];
                if (arr == null) continue;
                for (int v : arr) {
                    Assert.assertEquals(v, combined[count++]);
                }
            }
            Assert.assertEquals(total, combined.length);
        }
    }
}
