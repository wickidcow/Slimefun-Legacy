package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunDeprecated;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.platform.MinecraftVersionNumber;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCompatibilityService;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformFamily;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformProfile;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformSupportLevel;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.Server;

/** Internal detector backing the public platform compatibility service. */
@SlimefunInternal
public final class DefaultPlatformCompatibilityService implements PlatformCompatibilityService {

    private volatile PlatformProfile profile = PlatformProfile.unknown();

    public void initialize(@Nonnull Server server) {
        initialize(server, RuntimePlatformDetector.isRegionOwnedExecution());
    }

    /**
     * Compatibility overload retained for 4.1.19-era internal callers and tests.
     *
     * @param server the running server
     * @param regionOwnedExecution an explicit regionized-runtime result
     * @deprecated use {@link #initialize(Server)} so all probes share one detector
     */
    @Deprecated(since = "4.1.20", forRemoval = false)
    @SlimefunDeprecated(
            since = "4.1.20",
            replacement = "initialize(org.bukkit.Server)")
    public void initialize(@Nonnull Server server, boolean regionOwnedExecution) {
        Set<PlatformCapability> detected = RuntimePlatformDetector.detectCapabilities(server);
        EnumSet<PlatformCapability> capabilities = detected.isEmpty()
                ? EnumSet.noneOf(PlatformCapability.class)
                : EnumSet.copyOf(detected);

        if (regionOwnedExecution) {
            capabilities.add(PlatformCapability.REGION_OWNED_EXECUTION);
        } else {
            capabilities.remove(PlatformCapability.REGION_OWNED_EXECUTION);
        }

        PlatformFamily family = RuntimePlatformDetector.detectFamily(
                server, capabilities, regionOwnedExecution);
        PlatformSupportLevel supportLevel = RuntimePlatformDetector.supportLevel(family);
        String rawMinecraftVersion = server.getMinecraftVersion();
        MinecraftVersionNumber minecraftVersion =
                MinecraftVersionNumber.parse(rawMinecraftVersion).orElse(null);

        profile = new PlatformProfile(
                RuntimePlatformDetector.safe(server.getName()),
                RuntimePlatformDetector.safe(server.getVersion()),
                RuntimePlatformDetector.safe(rawMinecraftVersion),
                minecraftVersion,
                Runtime.version().feature(),
                family,
                supportLevel,
                capabilities);
    }

    @Override
    public @Nonnull PlatformProfile getProfile() {
        return profile;
    }

    @Override
    public boolean supports(@Nonnull PlatformCapability capability) {
        return profile.supports(capability);
    }

    @Override
    public boolean isMinecraftVersionAtLeast(int major, int minor, int patch) {
        MinecraftVersionNumber minimum = new MinecraftVersionNumber(major, minor, patch);
        return profile.getMinecraftVersion().map(version -> version.isAtLeast(minimum)).orElse(false);
    }
}
