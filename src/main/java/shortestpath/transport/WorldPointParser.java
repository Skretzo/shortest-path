package shortestpath.transport;

import shortestpath.WorldPointUtil;

public class WorldPointParser implements FieldParser<Integer> {
    private static final String DELIM_SPACE = " ";

    @Override
    public Integer parse(String value) {
        if (value == null || value.isEmpty()) {
            return Transport.LOCATION_PERMUTATION;
        }
        String[] parts = value.split(DELIM_SPACE);
        return parts.length == 3 ? WorldPointUtil.packWorldPoint(
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])) : Transport.LOCATION_PERMUTATION;
    }
}

