package io.github.thebusybiscuit.slimefun4.core.services.scheduling;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunDeprecated;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.RuntimePlatformDetector;

/**
 * Legacy compatibility bridge for Folia runtime detection.
 *
 * <p>New code should consume Slimefun's platform compatibility service or scheduler abstraction. This class remains
 * available so existing addons and internal callers continue to link.
 */
@Deprecated(since = "4.1.20", forRemoval = false)
@SlimefunDeprecated(
        since = "4.1.20",
        replacement = "Slimefun.getPlatformCompatibilityService().isRegionOwnedExecution()")
@SlimefunInternal
public final class FoliaSupport {

    private FoliaSupport() {}

    /**
     * Returns whether the current server uses Folia's regionized ticking model.
     *
     * @return whether Folia scheduler and ownership semantics are active
     * @deprecated use the platform compatibility service or {@link SlimefunScheduler#isFolia()}
     */
    @Deprecated(since = "4.1.20", forRemoval = false)
    @SlimefunDeprecated(
            since = "4.1.20",
            replacement = "Slimefun.getPlatformCompatibilityService().isRegionOwnedExecution()")
    public static boolean isFolia() {
        return RuntimePlatformDetector.isRegionOwnedExecution();
    }
}
