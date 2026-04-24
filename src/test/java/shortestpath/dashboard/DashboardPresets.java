package shortestpath.dashboard;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import shortestpath.TeleportationItem;

/**
 * Static registry of named dashboard presets.
 * <p>
 * A preset is a {@link Consumer} of {@link DashboardPathfinderConfig} that configures the
 * {@code useTeleportationItems} and {@code includeBankPath} fields to a well-known shape.
 * All other transport toggles are left at the {@link DashboardPathfinderConfig} defaults
 * (which mirror the {@link shortestpath.ShortestPathConfig} interface defaults).
 * <p>
 * The {@code BANK} preset additionally disables teleportation minigames and is paired with
 * a Lumbridge diary stub of 0 in {@link DashboardScenarioRunner} to match the former
 * {@code BankMode} behaviour.
 * <p>
 * Preset names are case-insensitive. The old CSV column name {@code teleports} is an alias
 * for {@code preset}; the loader maps the column value through here.
 */
public final class DashboardPresets {

    /** Lumbrige diary elite stub value for presets that allow wilderness / fairy-ring access. */
    public static final int DIARY_ENABLED = 1;
    /** Lumbridge diary elite stub value for the BANK preset (matches legacy BankMode). */
    public static final int DIARY_DISABLED = 0;

    private static final Map<String, Consumer<DashboardPathfinderConfig>> PRESETS = new HashMap<>();

    static {
        PRESETS.put("NONE", cfg -> {
            cfg.setUseTeleportationItems(TeleportationItem.NONE);
            cfg.setIncludeBankPath(false);
        });

        PRESETS.put("ALL", cfg -> {
            cfg.setUseTeleportationItems(TeleportationItem.ALL);
            cfg.setIncludeBankPath(false);
        });

        PRESETS.put("BANK", cfg -> {
            cfg.setUseTeleportationItems(TeleportationItem.INVENTORY_AND_BANK);
            cfg.setIncludeBankPath(true);
            cfg.setUseTeleportationMinigames(false);
        });

        PRESETS.put("INVENTORY", cfg -> {
            cfg.setUseTeleportationItems(TeleportationItem.INVENTORY);
            cfg.setIncludeBankPath(false);
        });

        PRESETS.put("INVENTORY_NON_CONSUMABLE", cfg -> {
            cfg.setUseTeleportationItems(TeleportationItem.INVENTORY_NON_CONSUMABLE);
            cfg.setIncludeBankPath(false);
        });
    }

    private DashboardPresets() {
    }

    /**
     * Apply the named preset to {@code config}.
     *
     * @param presetName case-insensitive preset id (e.g. {@code "ALL"}, {@code "bank"})
     * @param config     the config object to mutate
     * @throws IllegalArgumentException if the preset name is unknown
     */
    public static void apply(String presetName, DashboardPathfinderConfig config) {
        String key = presetName == null ? "NONE" : presetName.toUpperCase(Locale.ROOT);
        Consumer<DashboardPathfinderConfig> preset = PRESETS.get(key);
        if (preset == null) {
            throw new IllegalArgumentException("Unknown dashboard preset: '" + presetName + "'");
        }
        preset.accept(config);
    }

    /**
     * Returns the Lumbridge diary elite stub value appropriate for the preset.
     * The BANK preset uses 0 (diary NOT complete) to match legacy {@code BankMode} behaviour.
     * All other presets use 1.
     */
    public static int lumbridgeDiaryEliteStub(String presetName) {
        String key = presetName == null ? "NONE" : presetName.toUpperCase(Locale.ROOT);
        if ("BANK".equals(key)) {
            return DIARY_DISABLED;
        }
        return DIARY_ENABLED;
    }

    /** Returns true if the given preset name is known. */
    public static boolean isKnown(String presetName) {
        if (presetName == null) {
            return false;
        }
        return PRESETS.containsKey(presetName.toUpperCase(Locale.ROOT));
    }
}
