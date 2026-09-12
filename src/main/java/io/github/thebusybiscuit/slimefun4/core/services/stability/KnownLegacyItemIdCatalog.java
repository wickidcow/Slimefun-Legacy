package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Read-only historical Slimefun item-ID knowledge used by Doctor diagnostics.
 *
 * <p>This catalog is deliberately separate from Slimefun's live legacy-ID registry. A catalog hit is diagnostic
 * evidence only and never authorizes a migration or makes an old ID resolve as a live Slimefun item. Executable
 * migrations must still be declared by the owning addon through the normal legacy-ID registry/provider workflow.
 */
public final class KnownLegacyItemIdCatalog {

    private static final String IE1_SOURCE = "InfinityExpansion v1 -> InfinityExpansion2";
    private static final Map<String, Hint> EXACT_HINTS = createExactHints();

    private KnownLegacyItemIdCatalog() {}

    /**
     * Looks up verified historical knowledge for an unknown ID.
     *
     * @param legacyId historical item ID observed by Doctor
     * @return a diagnostic-only hint when known
     */
    public static @Nonnull Optional<Hint> find(@Nonnull String legacyId) {
        if (legacyId.isBlank()) {
            return Optional.empty();
        }

        SlimefunItem currentOwner = SlimefunItem.getById(legacyId);
        if (currentOwner != null && legacyId.equals(currentOwner.getId())) {
            return Optional.empty();
        }

        Hint exact = EXACT_HINTS.get(legacyId);
        if (exact != null) {
            return Optional.of(exact);
        }

        if (legacyId.endsWith("_DATA_CARD") && !legacyId.equals("EMPTY_DATA_CARD")) {
            String mob = legacyId.substring(0, legacyId.length() - "_DATA_CARD".length());
            return Optional.of(new Hint(
                    legacyId,
                    "IE_MOB_DATA_CARD_" + mob,
                    IE1_SOURCE,
                    Evidence.VERIFIED_COMPATIBILITY_PATTERN));
        }

        if (legacyId.startsWith("QUARRY_OSCILLATOR_") && legacyId.length() > "QUARRY_OSCILLATOR_".length()) {
            String resource = legacyId.substring("QUARRY_OSCILLATOR_".length());
            return Optional.of(new Hint(
                    legacyId,
                    "IE_OSCILLATOR_" + resource,
                    IE1_SOURCE,
                    Evidence.VERIFIED_COMPATIBILITY_PATTERN));
        }

        // IE2 itself uses this post-registration compatibility rule for the common IE1 FOO -> IE_FOO transition.
        // Keep it diagnostic-only here and require the proposed IE2 target to actually be registered, which avoids
        // treating arbitrary unknown addon IDs as InfinityExpansion history.
        String prefixedTarget = "IE_" + legacyId;
        if (SlimefunItem.getById(prefixedTarget) != null) {
            return Optional.of(new Hint(
                    legacyId, prefixedTarget, IE1_SOURCE, Evidence.RUNTIME_VERIFIED_COMPATIBILITY_RULE));
        }

        return Optional.empty();
    }

    public static int getExactHintCount() {
        return EXACT_HINTS.size();
    }

    private static Map<String, Hint> createExactHints() {
        Map<String, Hint> hints = new LinkedHashMap<>();

        // InfinityExpansion v1 -> InfinityExpansion2 explicit mappings, mirrored from IE2's LegacyIdMapper.
        add(hints, "INFINITE_INGOT", "IE_INFINITY_INGOT");
        add(hints, "INFINITE_MACHINE_CIRCUIT", "IE_INFINITY_MACHINE_CIRCUIT");
        add(hints, "INFINITE_MACHINE_CORE", "IE_INFINITY_MACHINE_CORE");
        add(hints, "END_ESSENCE", "IE_ENDER_ESSENCE");
        add(hints, "INFINITY_FORGE", "IE_INFINITY_WORKBENCH");

        add(hints, "BASIC_STRAINER", "IE_STRAINER_1");
        add(hints, "ADVANCED_STRAINER", "IE_STRAINER_2");
        add(hints, "REINFORCED_STRAINER", "IE_STRAINER_3");

        add(hints, "BASIC_COBBLE_GEN", "IE_COBBLESTONE_GENERATOR");
        add(hints, "ADVANCED_COBBLE_GEN", "IE_COBBLESTONE_GENERATOR_2");
        add(hints, "INFINITY_COBBLE_GEN", "IE_COBBLESTONE_GENERATOR_4");
        add(hints, "BASIC_VIRTUAL_FARM", "IE_VIRTUAL_FARM");
        add(hints, "ADVANCED_VIRTUAL_FARM", "IE_VIRTUAL_FARM_2");
        add(hints, "INFINITY_VIRTUAL_FARM", "IE_VIRTUAL_FARM_4");
        add(hints, "BASIC_TREE_GROWER", "IE_TREE_GROWER");
        add(hints, "ADVANCED_TREE_GROWER", "IE_TREE_GROWER_2");
        add(hints, "INFINITY_TREE_GROWER", "IE_TREE_GROWER_4");

        add(hints, "BASIC_QUARRY", "IE_QUARRY");
        add(hints, "ADVANCED_QUARRY", "IE_QUARRY_2");
        add(hints, "VOID_QUARRY", "IE_QUARRY_3");
        add(hints, "INFINITY_QUARRY", "IE_QUARRY_4");

        add(hints, "INFINITE_VOID_HARVESTER", "IE_VOID_HARVESTER_3");
        add(hints, "INFINITY_CONSTRUCTOR", "IE_SINGULARITY_CONSTRUCTOR_2");
        add(hints, "INFINITY_DUST_EXTRACTOR", "IE_DUST_EXTRACTOR_4");
        add(hints, "INFINITY_INGOT_FORMER", "IE_INGOT_FORMER_4");
        add(hints, "BASIC_OBSIDIAN_GEN", "IE_OBSIDIAN_GENERATOR");
        add(hints, "POWERED_BEDROCK", "IE_POWERED_BEDROCK");

        add(hints, "HYDRO_GENERATOR", "IE_HYDRO_GENERATOR");
        add(hints, "REINFORCED_HYDRO_GENERATOR", "IE_HYDRO_GENERATOR_2");
        add(hints, "GEOTHERMAL_GENERATOR", "IE_GEOTHERMAL_GENERATOR");
        add(hints, "REINFORCED_GEOTHERMAL_GENERATOR", "IE_GEOTHERMAL_GENERATOR_2");
        add(hints, "BASIC_PANEL", "IE_SOLAR_PANEL");
        add(hints, "ADVANCED_PANEL", "IE_SOLAR_PANEL_2");
        add(hints, "CELESTIAL_PANEL", "IE_SOLAR_PANEL_3");
        add(hints, "VOID_PANEL", "IE_VOID_PANEL");
        add(hints, "INFINITE_PANEL", "IE_INFINITY_PANEL");

        add(hints, "EMPTY_DATA_CARD", "IE_MOB_DATA_CARD_EMPTY");
        add(hints, "DATA_INFUSER", "IE_MOB_DATA_INFUSER");

        add(hints, "BASIC_STORAGE", "IE_STORAGE_UNIT_2");
        add(hints, "ADVANCED_STORAGE", "IE_STORAGE_UNIT_3");
        add(hints, "REINFORCED_STORAGE", "IE_STORAGE_UNIT_4");
        add(hints, "VOID_STORAGE", "IE_STORAGE_UNIT_5");
        add(hints, "INFINITY_STORAGE", "IE_STORAGE_UNIT_6");

        return Collections.unmodifiableMap(hints);
    }

    private static void add(Map<String, Hint> hints, String legacyId, String targetId) {
        hints.put(legacyId, new Hint(legacyId, targetId, IE1_SOURCE, Evidence.VERIFIED_EXPLICIT_MAPPING));
    }

    public enum Evidence {
        VERIFIED_EXPLICIT_MAPPING("verified explicit mapping"),
        VERIFIED_COMPATIBILITY_PATTERN("verified compatibility pattern"),
        RUNTIME_VERIFIED_COMPATIBILITY_RULE("runtime-verified compatibility rule");

        private final String displayName;

        Evidence(String displayName) {
            this.displayName = displayName;
        }

        public @Nonnull String getDisplayName() {
            return displayName;
        }
    }

    public record Hint(@Nonnull String legacyId, @Nonnull String targetId, @Nonnull String source, @Nonnull Evidence evidence) {}
}
