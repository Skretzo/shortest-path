package shortestpath.transport.requirement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Represents all item requirements for a transport.
 * All requirements must be satisfied.
 */
@Getter
public class TransportItems {
    private final List<ItemRequirement> requirements;

    public TransportItems(List<ItemRequirement> requirements) {
        this.requirements = Collections.unmodifiableList(new ArrayList<>(requirements));
    }

    /**
     * Creates TransportItems from legacy array format for backwards compatibility.
     */
    public TransportItems(int[][] items, int[][] staves, int[][] offhands, int[] quantities) {
        List<ItemRequirement> reqs = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            reqs.add(new ItemRequirement(
                    items[i],
                    staves != null && i < staves.length ? staves[i] : new int[0],
                    offhands != null && i < offhands.length ? offhands[i] : new int[0],
                    quantities[i]
            ));
        }
        this.requirements = Collections.unmodifiableList(reqs);
    }

    /**
     * Gets the number of item requirements.
     */
    public int size() {
        return requirements.size();
    }

    /**
     * Gets item IDs at the specified index (for backwards compatibility).
     */
    public int[] getItems(int index) {
        return requirements.get(index).getItemIds();
    }

    /**
     * Gets staff IDs at the specified index (for backwards compatibility).
     */
    public int[] getStaves(int index) {
        return requirements.get(index).getStaffIds();
    }

    /**
     * Gets offhand IDs at the specified index (for backwards compatibility).
     */
    public int[] getOffhands(int index) {
        return requirements.get(index).getOffhandIds();
    }

    /**
     * Gets the quantity at the specified index (for backwards compatibility).
     */
    public int getQuantity(int index) {
        return requirements.get(index).getQuantity();
    }

    // Legacy getters for backwards compatibility
    public int[][] getItems() {
        int[][] items = new int[requirements.size()][];
        for (int i = 0; i < requirements.size(); i++) {
            items[i] = requirements.get(i).getItemIds();
        }
        return items;
    }

    public int[][] getStaves() {
        int[][] staves = new int[requirements.size()][];
        for (int i = 0; i < requirements.size(); i++) {
            staves[i] = requirements.get(i).getStaffIds();
        }
        return staves;
    }

    public int[][] getOffhands() {
        int[][] offhands = new int[requirements.size()][];
        for (int i = 0; i < requirements.size(); i++) {
            offhands[i] = requirements.get(i).getOffhandIds();
        }
        return offhands;
    }

    public int[] getQuantities() {
        int[] quantities = new int[requirements.size()];
        for (int i = 0; i < requirements.size(); i++) {
            quantities[i] = requirements.get(i).getQuantity();
        }
        return quantities;
    }

    @Override
    public String toString() {
        return "[" +
                toString(getItems()) + ", " +
                toString(getStaves()) + ", " +
                toString(getOffhands()) + ", " +
                Arrays.toString(getQuantities()) + "]";
    }

    private String toString(int[][] array) {
        StringBuilder text = new StringBuilder();
        for (int[] inner : array) {
            text.append((text.length() == 0) ? "" : ", ").append(Arrays.toString(inner));
        }
        return "[" + text + "]";
    }

    /**
     * Merges two TransportItems, combining all requirements from both.
     * If either is null, returns the other.
     */
    public static TransportItems merge(TransportItems first, TransportItems second) {
        if (first == null) return second;
        if (second == null) return first;
        List<ItemRequirement> merged = new ArrayList<>();
        merged.addAll(first.requirements);
        merged.addAll(second.requirements);
        return new TransportItems(merged);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        TransportItems that = (TransportItems) o;
        if (requirements.size() != that.requirements.size()) return false;
        for (int i = 0; i < requirements.size(); i++) {
            if (!requirements.get(i).equals(that.requirements.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return requirements.hashCode();
    }
}

