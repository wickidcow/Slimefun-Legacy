package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformRequirements;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Immutable compatibility declaration for a Slimefun addon.
 *
 * <p>Addons may provide this through {@link AddonCompatibilityProvider}, register it at runtime through
 * {@link AddonCompatibilityService}, or embed an equivalent {@code slimefun-compatibility.json} manifest.
 */
@SlimefunAPI
public final class AddonCompatibilityDeclaration {

    private final Set<SlimefunCoreVariant> testedCoreVariants;
    private final PlatformRequirements platformRequirements;
    private final Set<String> requiredPlugins;
    private final Set<String> optionalPlugins;
    private final String notes;

    private AddonCompatibilityDeclaration(Builder builder) {
        testedCoreVariants = builder.testedCoreVariants.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.testedCoreVariants));
        platformRequirements = builder.platformRequirements;
        requiredPlugins = immutablePluginNames(builder.requiredPlugins);
        optionalPlugins = immutablePluginNames(builder.optionalPlugins);
        notes = builder.notes;
    }

    public static @Nonnull Builder builder() {
        return new Builder();
    }

    public @Nonnull Set<SlimefunCoreVariant> getTestedCoreVariants() {
        return testedCoreVariants;
    }

    public @Nonnull PlatformRequirements getPlatformRequirements() {
        return platformRequirements;
    }

    public @Nonnull Set<String> getRequiredPlugins() {
        return requiredPlugins;
    }

    public @Nonnull Set<String> getOptionalPlugins() {
        return optionalPlugins;
    }

    public @Nonnull String getNotes() {
        return notes;
    }

    public boolean isTestedOn(@Nonnull SlimefunCoreVariant variant) {
        return testedCoreVariants.contains(Objects.requireNonNull(variant, "variant"));
    }

    private static Set<String> immutablePluginNames(Set<String> source) {
        return source.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    /** Builder for {@link AddonCompatibilityDeclaration}. */
    @SlimefunAPI
    public static final class Builder {

        private final EnumSet<SlimefunCoreVariant> testedCoreVariants =
                EnumSet.noneOf(SlimefunCoreVariant.class);
        private PlatformRequirements platformRequirements = PlatformRequirements.builder().build();
        private final LinkedHashSet<String> requiredPlugins = new LinkedHashSet<>();
        private final LinkedHashSet<String> optionalPlugins = new LinkedHashSet<>();
        private String notes = "";

        private Builder() {}

        public @Nonnull Builder testCore(@Nonnull SlimefunCoreVariant variant) {
            testedCoreVariants.add(Objects.requireNonNull(variant, "variant"));
            return this;
        }

        public @Nonnull Builder testCores(@Nonnull SlimefunCoreVariant... variants) {
            Objects.requireNonNull(variants, "variants");
            for (SlimefunCoreVariant variant : variants) {
                testCore(variant);
            }
            return this;
        }

        public @Nonnull Builder platformRequirements(@Nonnull PlatformRequirements requirements) {
            platformRequirements = Objects.requireNonNull(requirements, "requirements");
            return this;
        }

        public @Nonnull Builder requirePlugin(@Nonnull String pluginName) {
            requiredPlugins.add(normalizePluginName(pluginName));
            return this;
        }

        public @Nonnull Builder optionalPlugin(@Nonnull String pluginName) {
            optionalPlugins.add(normalizePluginName(pluginName));
            return this;
        }

        public @Nonnull Builder notes(@Nonnull String value) {
            notes = Objects.requireNonNull(value, "value").trim();
            return this;
        }

        public @Nonnull AddonCompatibilityDeclaration build() {
            LinkedHashSet<String> overlap = new LinkedHashSet<>();
            for (String requiredPlugin : requiredPlugins) {
                optionalPlugins.stream()
                        .filter(requiredPlugin::equalsIgnoreCase)
                        .findFirst()
                        .ifPresent(overlap::add);
            }
            if (!overlap.isEmpty()) {
                throw new IllegalStateException("Plugins cannot be both required and optional: " + overlap);
            }
            return new AddonCompatibilityDeclaration(this);
        }

        private static String normalizePluginName(String pluginName) {
            String normalized = Objects.requireNonNull(pluginName, "pluginName").trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Plugin name cannot be blank");
            }
            return normalized;
        }
    }
}
