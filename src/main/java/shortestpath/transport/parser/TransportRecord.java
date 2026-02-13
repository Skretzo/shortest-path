package shortestpath.transport.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single row from a TSV transport file.
 * Provides a clean interface to access field values by name.
 */
public class TransportRecord {

    private final Map<String, String> fields;

    public TransportRecord(Map<String, String> fields) {
        this.fields = Collections.unmodifiableMap(new HashMap<>(fields));
    }

    /**
     * Gets a field value by name, or null if not present.
     */
    public String get(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Gets a field value by name, or the default value if not present or empty.
     */
    public String getOrDefault(String fieldName, String defaultValue) {
        String value = fields.get(fieldName);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * Checks if a field is present and non-empty.
     */
    public boolean has(String fieldName) {
        String value = fields.get(fieldName);
        return value != null && !value.isEmpty();
    }

    /**
     * Checks if a field key exists in the record (may have empty value).
     */
    public boolean hasKey(String fieldName) {
        return fields.containsKey(fieldName);
    }

    /**
     * Gets the origin field value.
     */
    public String getOrigin() {
        return get(Fields.ORIGIN);
    }

    /**
     * Gets the destination field value.
     */
    public String getDestination() {
        return get(Fields.DESTINATION);
    }

    /**
     * Gets the skills field value.
     */
    public String getSkills() {
        return get(Fields.SKILLS);
    }

    /**
     * Gets the items field value.
     */
    public String getItems() {
        return get(Fields.ITEMS);
    }

    /**
     * Gets the quests field value.
     */
    public String getQuests() {
        return get(Fields.QUESTS);
    }

    /**
     * Gets the duration field value.
     */
    public String getDuration() {
        return get(Fields.DURATION);
    }

    /**
     * Gets the display info field value.
     */
    public String getDisplayInfo() {
        return get(Fields.DISPLAY_INFO);
    }

    /**
     * Gets the consumable field value.
     */
    public String getConsumable() {
        return get(Fields.CONSUMABLE);
    }

    /**
     * Gets the wilderness level field value.
     */
    public String getWildernessLevel() {
        return get(Fields.WILDERNESS_LEVEL);
    }

    /**
     * Gets the object info field value.
     */
    public String getObjectInfo() {
        return get(Fields.OBJECT_INFO);
    }

    /**
     * Gets the varbits field value.
     */
    public String getVarbits() {
        return get(Fields.VARBITS);
    }

    /**
     * Gets the var players field value.
     */
    public String getVarPlayers() {
        return get(Fields.VAR_PLAYERS);
    }

    /**
     * Gets the underlying field map.
     */
    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * Standard field names used across TSV files
     */
    public static final class Fields {
        public static final String ORIGIN = "Origin";
        public static final String DESTINATION = "Destination";
        public static final String SKILLS = "Skills";
        public static final String ITEMS = "Items";
        public static final String QUESTS = "Quests";
        public static final String DURATION = "Duration";
        public static final String DISPLAY_INFO = "Display info";
        public static final String CONSUMABLE = "Consumable";
        public static final String WILDERNESS_LEVEL = "Wilderness level";
        public static final String OBJECT_INFO = "menuOption menuTarget objectID";
        public static final String VARBITS = "Varbits";
        public static final String VAR_PLAYERS = "VarPlayers";

        private Fields() {
        }
    }
}

