package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** High-level operational readiness of the Slimefun core. */
@SlimefunAPI
public enum CoreReadinessState {
    STARTING,
    READY,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}
