package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.addons.AddonApiCompatibilityFacade;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonApiCompatibilitySnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityService;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRegistrationService;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeHealthService;
import io.github.thebusybiscuit.slimefun4.api.addons.CrossForkApiCapability;
import io.github.thebusybiscuit.slimefun4.api.addons.SlimefunCoreVariant;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/** Internal immutable-target implementation of the cross-fork addon API facade. */
@SlimefunInternal
public final class DefaultAddonApiCompatibilityFacade implements AddonApiCompatibilityFacade {

    private static final Set<SlimefunCoreVariant> TARGETS = Collections.unmodifiableSet(
            EnumSet.of(SlimefunCoreVariant.ORIGINAL, SlimefunCoreVariant.GUGU, SlimefunCoreVariant.UNITED, SlimefunCoreVariant.LEGACY));
    private static final Set<CrossForkApiCapability> CAPABILITIES = Collections.unmodifiableSet(EnumSet.allOf(CrossForkApiCapability.class));

    private final RegistryRuntimeService registryRuntime;
    private final AddonRegistrationService registrationService;
    private final AddonCompatibilityService compatibilityService;
    private final AddonRuntimeHealthService runtimeHealthService;

    public DefaultAddonApiCompatibilityFacade(
            @Nonnull RegistryRuntimeService registryRuntime,
            @Nonnull AddonRegistrationService registrationService,
            @Nonnull AddonCompatibilityService compatibilityService,
            @Nonnull AddonRuntimeHealthService runtimeHealthService) {
        this.registryRuntime = Objects.requireNonNull(registryRuntime, "registryRuntime");
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService");
        this.compatibilityService = Objects.requireNonNull(compatibilityService, "compatibilityService");
        this.runtimeHealthService = Objects.requireNonNull(runtimeHealthService, "runtimeHealthService");
    }

    @Override
    public @Nonnull SlimefunCoreVariant getRunningCoreVariant() {
        return compatibilityService.getRunningCoreVariant();
    }

    @Override
    public @Nonnull Set<SlimefunCoreVariant> getCompatibilityTargets() {
        return TARGETS;
    }

    @Override
    public @Nonnull Set<CrossForkApiCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public @Nonnull AddonRegistrationService getRegistrationService() {
        return registrationService;
    }

    @Override
    public @Nonnull RegistryRuntimeService getRegistryRuntimeService() {
        return registryRuntime;
    }

    @Override
    public @Nonnull AddonCompatibilityService getCompatibilityService() {
        return compatibilityService;
    }

    @Override
    public @Nonnull AddonRuntimeHealthService getRuntimeHealthService() {
        return runtimeHealthService;
    }

    @Override
    public @Nonnull AddonApiCompatibilitySnapshot getSnapshot() {
        RegistryRuntimeSnapshot registry = registryRuntime.getSnapshot();
        AddonRegistrationRuntimeSnapshot registration = registrationService.getSnapshot();
        return new AddonApiCompatibilitySnapshot(
                getRunningCoreVariant(),
                TARGETS,
                CAPABILITIES,
                registry.isInitialRegistrationFinalized(),
                registration.getPendingCallbacks(),
                registry.getRuntimeRegisteredItems(),
                runtimeHealthService.getObservedFailureCount());
    }
}
