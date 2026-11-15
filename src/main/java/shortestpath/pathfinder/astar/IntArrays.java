package shortestpath.pathfinder.astar;

/** Unsigned-ordered primitive-array lookups shared by the astar structures. */
final class IntArrays
{
	private IntArrays()
	{
	}

	static boolean contains(int[] values, int target)
	{
		return contains(values, 0, values.length, target);
	}

	static boolean contains(int[] values, int start, int end, int target)
	{
		for (int i = start; i < end; i++)
			if (values[i] == target)
				return true;
		return false;
	}

	/** The values of two unsigned-ordered, duplicate-free arrays, likewise. */
	static int[] union(int[] a, int[] b)
	{
		int[] result = new int[a.length + b.length];
		int i = 0, j = 0, count = 0;
		while (i < a.length || j < b.length)
		{
			int compare = i == a.length ? 1 : j == b.length ? -1 : Integer.compareUnsigned(a[i], b[j]);
			if (compare <= 0) result[count++] = a[i++];
			else result[count++] = b[j++];
			if (compare == 0) j++;
		}
		return count == result.length ? result : java.util.Arrays.copyOf(result, count);
	}

	static int binarySearch(int[] values, int target)
	{
		int low = 0, high = values.length - 1;
		while (low <= high)
		{
			int middle = low + (high - low) / 2;
			int compare = Integer.compareUnsigned(values[middle], target);
			if (compare == 0) return middle;
			if (compare < 0) low = middle + 1;
			else high = middle - 1;
		}
		return -1;
	}

	static int lowerBound(int[] values, int target)
	{
		int low = 0, high = values.length;
		while (low < high)
		{
			int middle = low + (high - low) / 2;
			if (Integer.compareUnsigned(values[middle], target) < 0) low = middle + 1;
			else high = middle;
		}
		return low;
	}
}
