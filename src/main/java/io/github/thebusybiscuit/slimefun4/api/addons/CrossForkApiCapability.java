package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Stable capability identifiers exposed by Slimefun Legacy's cross-fork API compatibility facade. */
@SlimefunAPI
public enum CrossForkApiCapability {
    COMMON_SLIMEFUN_ADDON_CONTRACT,
    ITEM_REGISTRATION,
    REGISTRY_FINALIZATION_EVENT,
    LATE_REGISTRATION,
    GUARDED_ADDON_CALLBACKS,
    COMPATIBILITY_DECLARATIONS,
    OPTIONAL_DEPENDENCIES,
    PLATFORM_REQUIREMENTS,
    RUNTIME_DIAGNOSTICS
}
