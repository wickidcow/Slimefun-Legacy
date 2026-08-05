package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Immutable addon requirements that can be evaluated by {@link PlatformCompatibilityService}.
 *
 * <p>Addons can declare the APIs they actually need instead of hard-coding Paper, Purpur, Folia, Java, or Minecraft
 * version checks.
 */
@SlimefunAPI
public final class PlatformRequirements {

    private final MinecraftVersionNumber minimumMinecraftVersion;
    private final int minimumJavaFeatureVersion;
    private final Set<PlatformCapability> requiredCapabilities;
    private final Set<PlatformFamily> acceptedFamilies;

    private PlatformRequirements(Builder builder) {
        minimumMinecraftVersion = builder.minimumMinecraftVersion;
        minimumJavaFeatureVersion = builder.minimumJavaFeatureVersion;
        requiredCapabilities = immutableCopy(builder.requiredCapabilities);
        acceptedFamilies = immutableCopy(builder.acceptedFamilies);
    }

    public static @Nonnull Builder builder() {
        return new Builder();
    }

    public @Nonnull Optional<MinecraftVersionNumber> getMinimumMinecraftVersion() {
        return Optional.ofNullable(minimumMinecraftVersion);
    }

    public int getMinimumJavaFeatureVersion() {
        return minimumJavaFeatureVersion;
    }

    public @Nonnull Set<PlatformCapability> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    public @Nonnull Set<PlatformFamily> getAcceptedFamilies() {
        return acceptedFamilies;
    }

    private static <E extends Enum<E>> Set<E> immutableCopy(Set<E> source) {
        if (source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(source));
    }

    /** Builder for {@link PlatformRequirements}. */
    @SlimefunAPI
    public static final class Builder {

        private MinecraftVersionNumber minimumMinecraftVersion;
        private int minimumJavaFeatureVersion;
        private final EnumSet<PlatformCapability> requiredCapabilities =
                EnumSet.noneOf(PlatformCapability.class);
        private final EnumSet<PlatformFamily> acceptedFamilies = EnumSet.noneOf(PlatformFamily.class);

        private Builder() {}

        public @Nonnull Builder minimumMinecraftVersion(int major, int minor, int patch) {
            return minimumMinecraftVersion(new MinecraftVersionNumber(major, minor, patch));
        }

        public @Nonnull Builder minimumMinecraftVersion(@Nonnull MinecraftVersionNumber version) {
            minimumMinecraftVersion = Objects.requireNonNull(version, "version");
            return this;
        }

        public @Nonnull Builder minimumJavaVersion(int featureVersion) {
            if (featureVersion < 1) {
                throw new IllegalArgumentException("Java feature version must be positive");
            }
            minimumJavaFeatureVersion = featureVersion;
            return this;
        }

        public @Nonnull Builder requireCapability(@Nonnull PlatformCapability capability) {
            requiredCapabilities.add(Objects.requireNonNull(capability, "capability"));
            return this;
        }

        public @Nonnull Builder requireCapabilities(@Nonnull PlatformCapability... capabilities) {
            Objects.requireNonNull(capabilities, "capabilities");
            for (PlatformCapability capability : capabilities) {
                requireCapability(capability);
            }
            return this;
        }

        public @Nonnull Builder acceptFamily(@Nonnull PlatformFamily family) {
            acceptedFamilies.add(Objects.requireNonNull(family, "family"));
            return this;
        }

        public @Nonnull Builder acceptFamilies(@Nonnull PlatformFamily... families) {
            Objects.requireNonNull(families, "families");
            for (PlatformFamily family : families) {
                acceptFamily(family);
            }
            return this;
        }

        public @Nonnull PlatformRequirements build() {
            return new PlatformRequirements(this);
        }
    }
}
