package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Addon-facing access to Slimefun Legacy's detected platform capabilities.
 *
 * <p>This service provides one stable compatibility boundary so addons do not need to repeat server-name checks,
 * brittle class probes, or hard-coded Minecraft-version comparisons.
 */
@SlimefunAPI
public interface PlatformCompatibilityService {

    @Nonnull
    PlatformProfile getProfile();

    boolean supports(@Nonnull PlatformCapability capability);

    boolean isMinecraftVersionAtLeast(int major, int minor, int patch);

    /**
     * Returns the parsed Minecraft version when the server reports a numeric release.
     *
     * @return the parsed Minecraft version, or an empty optional for snapshots and unknown formats
     */
    default @Nonnull Optional<MinecraftVersionNumber> getMinecraftVersion() {
        return getProfile().getMinecraftVersion();
    }

    /**
     * Checks whether the running Minecraft version is before the supplied version.
     *
     * @return {@code false} when the running version could not be parsed
     */
    default boolean isMinecraftVersionBefore(int major, int minor, int patch) {
        MinecraftVersionNumber target = new MinecraftVersionNumber(major, minor, patch);
        return getMinecraftVersion().map(version -> version.isBefore(target)).orElse(false);
    }

    /**
     * Checks the Java feature version detected at startup.
     *
     * @param featureVersion the minimum Java feature version
     * @return whether the current runtime meets the minimum
     */
    default boolean isJavaVersionAtLeast(int featureVersion) {
        return featureVersion > 0 && getProfile().getJavaFeatureVersion() >= featureVersion;
    }

    /**
     * Checks the detected platform family.
     *
     * @param family the expected platform family
     * @return whether the family matches
     */
    default boolean isFamily(@Nonnull PlatformFamily family) {
        return getProfile().getFamily() == Objects.requireNonNull(family, "family");
    }

    /**
     * Returns whether Paper-compatible APIs were detected.
     *
     * @return whether the server exposes the required Paper API surface
     */
    default boolean isPaperCompatible() {
        return supports(PlatformCapability.PAPER_API);
    }

    /**
     * Returns whether Folia-style region ownership semantics are active.
     *
     * @return whether location and entity work must run on owning regions
     */
    default boolean isRegionOwnedExecution() {
        return supports(PlatformCapability.REGION_OWNED_EXECUTION);
    }

    /**
     * Evaluates declarative addon requirements against the current platform profile.
     *
     * <p>This is a default method so third-party compatibility-service implementations compiled against 4.1.19 remain
     * binary compatible.
     *
     * @param requirements the addon requirements to evaluate
     * @return a compatibility report containing every failed requirement
     */
    default @Nonnull PlatformCompatibilityReport check(@Nonnull PlatformRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        PlatformProfile profile = getProfile();
        List<String> incompatibilities = new ArrayList<>();

        requirements.getMinimumMinecraftVersion().ifPresent(minimum -> {
            Optional<MinecraftVersionNumber> current = profile.getMinecraftVersion();
            if (current.isEmpty()) {
                incompatibilities.add("Minecraft version could not be parsed");
            } else if (current.orElseThrow().isBefore(minimum)) {
                incompatibilities.add(
                        "Requires Minecraft " + minimum + " or newer (found " + current.orElseThrow() + ")");
            }
        });

        int minimumJava = requirements.getMinimumJavaFeatureVersion();
        if (minimumJava > 0 && profile.getJavaFeatureVersion() < minimumJava) {
            incompatibilities.add(
                    "Requires Java " + minimumJava + " or newer (found Java " + profile.getJavaFeatureVersion() + ")");
        }

        for (PlatformCapability capability : requirements.getRequiredCapabilities()) {
            if (!profile.supports(capability)) {
                incompatibilities.add("Missing capability: " + capability.getDisplayName());
            }
        }

        if (!requirements.getAcceptedFamilies().isEmpty()
                && !requirements.getAcceptedFamilies().contains(profile.getFamily())) {
            incompatibilities.add(
                    "Unsupported platform family: " + profile.getFamily().getDisplayName());
        }

        return PlatformCompatibilityReport.incompatible(incompatibilities);
    }
}
