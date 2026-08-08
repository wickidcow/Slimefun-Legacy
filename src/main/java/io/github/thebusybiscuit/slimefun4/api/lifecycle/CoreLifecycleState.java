package io.github.thebusybiscuit.slimefun4.api.lifecycle;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** High-level state of the Slimefun core lifecycle. */
@SlimefunAPI
public enum CoreLifecycleState {
    NEW,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
