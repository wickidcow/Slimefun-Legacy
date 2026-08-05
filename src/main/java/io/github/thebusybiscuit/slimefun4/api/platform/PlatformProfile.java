package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

/** Immutable snapshot of the server platform and the APIs available to Slimefun and its addons. */
@SlimefunAPI
public final class PlatformProfile {

    private final String softwareName;
    private final String serverVersion;
    private final String rawMinecraftVersion;
    private final MinecraftVersionNumber minecraftVersion;
    private final int javaFeatureVersion;
    private final PlatformFamily family;
    private final PlatformSupportLevel supportLevel;
    private final Set<PlatformCapability> capabilities;

    public PlatformProfile(
            @Nonnull String softwareName,
            @Nonnull String serverVersion,
            @Nonnull String rawMinecraftVersion,
            MinecraftVersionNumber minecraftVersion,
            int javaFeatureVersion,
            @Nonnull PlatformFamily family,
            @Nonnull PlatformSupportLevel supportLevel,
            @Nonnull Set<PlatformCapability> capabilities) {
        this.softwareName = Objects.requireNonNull(softwareName, "softwareName");
        this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
        this.rawMinecraftVersion = Objects.requireNonNull(rawMinecraftVersion, "rawMinecraftVersion");
        this.minecraftVersion = minecraftVersion;
        this.javaFeatureVersion = javaFeatureVersion;
        this.family = Objects.requireNonNull(family, "family");
        this.supportLevel = Objects.requireNonNull(supportLevel, "supportLevel");

        EnumSet<PlatformCapability> copy = capabilities.isEmpty()
                ? EnumSet.noneOf(PlatformCapability.class)
                : EnumSet.copyOf(capabilities);
        this.capabilities = Collections.unmodifiableSet(copy);
    }

    public static @Nonnull PlatformProfile unknown() {
        return new PlatformProfile(
                "Unknown",
                "Unknown",
                "Unknown",
                null,
                Runtime.version().feature(),
                PlatformFamily.UNKNOWN,
                PlatformSupportLevel.UNKNOWN,
                EnumSet.noneOf(PlatformCapability.class));
    }

    public @Nonnull String getSoftwareName() {
        return softwareName;
    }

    public @Nonnull String getServerVersion() {
        return serverVersion;
    }

    public @Nonnull String getRawMinecraftVersion() {
        return rawMinecraftVersion;
    }

    public @Nonnull Optional<MinecraftVersionNumber> getMinecraftVersion() {
        return Optional.ofNullable(minecraftVersion);
    }

    public int getJavaFeatureVersion() {
        return javaFeatureVersion;
    }

    public @Nonnull PlatformFamily getFamily() {
        return family;
    }

    public @Nonnull PlatformSupportLevel getSupportLevel() {
        return supportLevel;
    }

    public @Nonnull Set<PlatformCapability> getCapabilities() {
        return capabilities;
    }

    public boolean supports(@Nonnull PlatformCapability capability) {
        return capabilities.contains(Objects.requireNonNull(capability, "capability"));
    }

    public boolean isFamily(@Nonnull PlatformFamily expectedFamily) {
        return family == Objects.requireNonNull(expectedFamily, "expectedFamily");
    }

    public boolean isPaperCompatible() {
        return supports(PlatformCapability.PAPER_API);
    }

    public boolean isRegionOwnedExecution() {
        return supports(PlatformCapability.REGION_OWNED_EXECUTION);
    }

    public @Nonnull String getDisplayName() {
        return softwareName + " " + serverVersion;
    }
}
