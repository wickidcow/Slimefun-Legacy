package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/** Immutable snapshot of the cross-fork addon API compatibility facade. */
@SlimefunAPI
public final class AddonApiCompatibilitySnapshot {

    private final SlimefunCoreVariant runningCoreVariant;
    private final Set<SlimefunCoreVariant> compatibilityTargets;
    private final Set<CrossForkApiCapability> capabilities;
    private final boolean initialRegistrationFinalized;
    private final int pendingRegistrationCallbacks;
    private final int runtimeRegisteredItems;
    private final long observedAddonFailures;

    public AddonApiCompatibilitySnapshot(
            @Nonnull SlimefunCoreVariant runningCoreVariant,
            @Nonnull Set<SlimefunCoreVariant> compatibilityTargets,
            @Nonnull Set<CrossForkApiCapability> capabilities,
            boolean initialRegistrationFinalized,
            int pendingRegistrationCallbacks,
            int runtimeRegisteredItems,
            long observedAddonFailures) {
        this.runningCoreVariant = Objects.requireNonNull(runningCoreVariant, "runningCoreVariant");
        this.compatibilityTargets = immutableEnumSet(compatibilityTargets);
        this.capabilities = immutableEnumSet(capabilities);
        this.initialRegistrationFinalized = initialRegistrationFinalized;
        this.pendingRegistrationCallbacks = pendingRegistrationCallbacks;
        this.runtimeRegisteredItems = runtimeRegisteredItems;
        this.observedAddonFailures = observedAddonFailures;
    }

    public @Nonnull SlimefunCoreVariant getRunningCoreVariant() {
        return runningCoreVariant;
    }

    public @Nonnull Set<SlimefunCoreVariant> getCompatibilityTargets() {
        return compatibilityTargets;
    }

    public @Nonnull Set<CrossForkApiCapability> getCapabilities() {
        return capabilities;
    }

    public boolean isInitialRegistrationFinalized() {
        return initialRegistrationFinalized;
    }

    public int getPendingRegistrationCallbacks() {
        return pendingRegistrationCallbacks;
    }

    public int getRuntimeRegisteredItems() {
        return runtimeRegisteredItems;
    }

    public long getObservedAddonFailures() {
        return observedAddonFailures;
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}
