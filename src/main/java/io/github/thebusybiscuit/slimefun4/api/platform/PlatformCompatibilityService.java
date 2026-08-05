package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
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
}
