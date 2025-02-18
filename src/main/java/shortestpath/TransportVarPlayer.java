package shortestpath;

import java.util.Map;

import lombok.Getter;

public class TransportVarPlayer {
    @Getter
    private final int id;
    private final int value;
    private final TransportVarCheck check;

    public TransportVarPlayer(int id, int value, TransportVarCheck check) {
        this.id = id;
        this.value = value;
        this.check = check;
    }

    public boolean check(Map<Integer, Integer> values) {
        switch (check) {
            case EQUAL:
                return values.get(id) == value;
            case GREATER:
                return values.get(id) > value;
            case SMALLER:
                return values.get(id) < value;
            case TIME_EXCEEDS:
                // Check if the current time in minutes minus the value in the map is greater
                // than the value
                return (System.currentTimeMillis() / 60000) - values.get(id) > value;
        }
        return false;
    }
}
