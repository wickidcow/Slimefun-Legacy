package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeService;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Stable addon-facing entry point for Legacy's compatibility services.
 *
 * <p>The facade describes API families Legacy intentionally preserves; it does not claim that an arbitrary exact
 * addon JAR has been verified on every listed fork.
 */
@SlimefunAPI
public interface AddonApiCompatibilityFacade {

    @Nonnull
    SlimefunCoreVariant getRunningCoreVariant();

    @Nonnull
    Set<SlimefunCoreVariant> getCompatibilityTargets();

    @Nonnull
    Set<CrossForkApiCapability> getCapabilities();

    default boolean targets(@Nonnull SlimefunCoreVariant variant) {
        return getCompatibilityTargets().contains(variant);
    }

    @Nonnull
    AddonRegistrationService getRegistrationService();

    @Nonnull
    RegistryRuntimeService getRegistryRuntimeService();

    @Nonnull
    AddonCompatibilityService getCompatibilityService();

    @Nonnull
    AddonRuntimeHealthService getRuntimeHealthService();

    @Nonnull
    AddonApiCompatibilitySnapshot getSnapshot();
}
