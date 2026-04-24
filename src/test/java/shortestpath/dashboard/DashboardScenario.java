package shortestpath.dashboard;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Immutable data bag representing one row from a dashboard CSV.
 * <p>
 * Build via {@link DashboardScenario.Builder}.
 */
public final class DashboardScenario {

    /** Item id + quantity pair used in inventory / equipment / bank columns. */
    public static final class ItemQuantity {
        public final int itemId;
        public final int quantity;

        public ItemQuantity(int itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    private final String name;
    private final String category;
    private final int startPoint;
    private final int endPoint;
    /** Preset name (e.g. {@code NONE}, {@code ALL}, {@code BANK}). Never null. */
    private final String preset;
    private final List<ItemQuantity> inventory;
    private final List<ItemQuantity> equipment;
    private final List<ItemQuantity> bank;
    /** Varbit overrides: varbitId → value. */
    private final Map<Integer, Integer> varbits;
    /** VarPlayer overrides: varPlayerId → value. */
    private final Map<Integer, Integer> varplayers;
    /** Skill level overrides: Skill name (upper-case) → level. */
    private final Map<String, Integer> skillLevels;
    /** Per-scenario config overrides: setter name → string value. */
    private final Map<String, String> configOverrides;
    /** Expected path length (absent → no assertion). */
    private final OptionalInt expectedLength;
    /** Minimum expected path length (absent → no assertion). */
    private final OptionalInt minimumLength;

    private DashboardScenario(Builder b) {
        this.name = b.name;
        this.category = b.category;
        this.startPoint = b.startPoint;
        this.endPoint = b.endPoint;
        this.preset = b.preset;
        this.inventory = Collections.unmodifiableList(b.inventory);
        this.equipment = Collections.unmodifiableList(b.equipment);
        this.bank = Collections.unmodifiableList(b.bank);
        this.varbits = Collections.unmodifiableMap(b.varbits);
        this.varplayers = Collections.unmodifiableMap(b.varplayers);
        this.skillLevels = Collections.unmodifiableMap(b.skillLevels);
        this.configOverrides = Collections.unmodifiableMap(b.configOverrides);
        this.expectedLength = b.expectedLength;
        this.minimumLength = b.minimumLength;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getStartPoint() { return startPoint; }
    public int getEndPoint() { return endPoint; }
    public String getPreset() { return preset; }
    public List<ItemQuantity> getInventory() { return inventory; }
    public List<ItemQuantity> getEquipment() { return equipment; }
    public List<ItemQuantity> getBank() { return bank; }
    public Map<Integer, Integer> getVarbits() { return varbits; }
    public Map<Integer, Integer> getVarplayers() { return varplayers; }
    public Map<String, Integer> getSkillLevels() { return skillLevels; }
    public Map<String, String> getConfigOverrides() { return configOverrides; }
    public OptionalInt getExpectedLength() { return expectedLength; }
    public OptionalInt getMinimumLength() { return minimumLength; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "";
        private String category = "";
        private int startPoint;
        private int endPoint;
        private String preset = "NONE";
        private List<ItemQuantity> inventory = new java.util.ArrayList<>();
        private List<ItemQuantity> equipment = new java.util.ArrayList<>();
        private List<ItemQuantity> bank = new java.util.ArrayList<>();
        private Map<Integer, Integer> varbits = new java.util.LinkedHashMap<>();
        private Map<Integer, Integer> varplayers = new java.util.LinkedHashMap<>();
        private Map<String, Integer> skillLevels = new java.util.LinkedHashMap<>();
        private Map<String, String> configOverrides = new java.util.LinkedHashMap<>();
        private OptionalInt expectedLength = OptionalInt.empty();
        private OptionalInt minimumLength = OptionalInt.empty();

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder startPoint(int startPoint) { this.startPoint = startPoint; return this; }
        public Builder endPoint(int endPoint) { this.endPoint = endPoint; return this; }
        public Builder preset(String preset) { this.preset = preset != null ? preset.toUpperCase() : "NONE"; return this; }
        public Builder inventory(List<ItemQuantity> inventory) { this.inventory = inventory; return this; }
        public Builder equipment(List<ItemQuantity> equipment) { this.equipment = equipment; return this; }
        public Builder bank(List<ItemQuantity> bank) { this.bank = bank; return this; }
        public Builder varbits(Map<Integer, Integer> varbits) { this.varbits = varbits; return this; }
        public Builder varplayers(Map<Integer, Integer> varplayers) { this.varplayers = varplayers; return this; }
        public Builder skillLevels(Map<String, Integer> skillLevels) { this.skillLevels = skillLevels; return this; }
        public Builder configOverrides(Map<String, String> configOverrides) { this.configOverrides = configOverrides; return this; }
        public Builder expectedLength(int len) { this.expectedLength = OptionalInt.of(len); return this; }
        public Builder minimumLength(int len) { this.minimumLength = OptionalInt.of(len); return this; }

        public DashboardScenario build() {
            return new DashboardScenario(this);
        }
    }
}
