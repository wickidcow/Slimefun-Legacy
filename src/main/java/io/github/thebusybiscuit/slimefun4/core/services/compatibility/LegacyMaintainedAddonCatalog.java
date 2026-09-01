package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityDeclaration;
import io.github.thebusybiscuit.slimefun4.api.addons.SlimefunCoreVariant;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Compatibility declarations for addons maintained as part of the Slimefun Legacy fork collection.
 *
 * <p>This catalog is deliberately keyed by Bukkit plugin name rather than JAR filename. Addons may still provide
 * their own runtime, provider, or embedded-manifest declaration; those declarations take precedence over this
 * fallback catalog in {@link DefaultAddonCompatibilityService}.
 */
@SlimefunInternal
final class LegacyMaintainedAddonCatalog {

    private static final Set<String> MAINTAINED_ADDONS = Set.of(
            "slimetinker",
            "slimetinkerie2",
            "betterchests",
            "fastmachines",
            "fluffymachines",
            "supreme",
            "magicexpansion",
            "networks",
            "networksexp",
            "networksexpansion",
            "infinityexpansion2",
            "exoticgarden",
            "cultivation",
            "cultivationlegacy",
            "dynatech",
            "danktech2",
            "foxymachines",
            "gastronomicon",
            "litexpansion",
            "slimefunadvancements",
            "electricspawners",
            "mobdrops",
            "alchimiavitae",
            "luckyblocks",
            "idreamofeasy",
            "slimefunwarfare",
            "militaryarsenal",
            "slimeglue",
            "worldeditslimefun",
            "mobcapturer",
            "slimytreetaps",
            "worldtaste",
            "rykenslimecustomizer",
            "flowerpower",
            "slimeeasy",
            "extraheads");

    private static final AddonCompatibilityDeclaration LEGACY_DECLARATION = AddonCompatibilityDeclaration.builder()
            .testCore(SlimefunCoreVariant.LEGACY)
            .notes("Maintained and tested as part of the Slimefun Legacy addon collection")
            .build();

    private LegacyMaintainedAddonCatalog() {}

    static @Nullable AddonCompatibilityDeclaration find(String pluginName) {
        String normalized = normalize(pluginName);
        if (MAINTAINED_ADDONS.contains(normalized)) {
            return LEGACY_DECLARATION;
        }

        // Some fork builds intentionally retain an SF_ prefix in the Bukkit project name.
        if (normalized.startsWith("sf") && MAINTAINED_ADDONS.contains(normalized.substring(2))) {
            return LEGACY_DECLARATION;
        }

        return null;
    }

    private static String normalize(String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
