package shortestpath.transport;

import java.util.Arrays;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class TransportItems {
    // Each outer int[] holds item id requirements, e.g. AIR_RUNE, FIRE_RUNE, ...
    // Each inner int[] holds item id variations, e.g. AIR_RUNE, DUST_RUNE, ...
    private final int[][] items;
    private final int[][] staves;
    private final int[][] offhands;
    private final int[] quantities;

    public TransportItems(int[][] items, int[][] staves, int[][] offhands, int[] quantities) {
        this.items = items;
        this.staves = staves;
        this.offhands = offhands;
        this.quantities = quantities;
    }

    @Override
    public String toString() {
        return "[" +
            toString(items) + ", " +
            toString(staves) + ", " +
            toString(offhands) + ", " +
            Arrays.toString(quantities) + "]";
    }

    private String toString(int[][] array) {
        String text = "";
        for (int[] inner : array) {
            text += (text.isEmpty() ? "" : ", ") + Arrays.toString(inner);
        }
        return "[" + text + "]";
    }
}
