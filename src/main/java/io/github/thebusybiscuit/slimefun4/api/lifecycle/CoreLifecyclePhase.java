package io.github.thebusybiscuit.slimefun4.api.lifecycle;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Observable startup/shutdown phase of the Slimefun core. */
@SlimefunAPI
public enum CoreLifecyclePhase {
    BOOTSTRAP,
    CONFIGURATION,
    STORAGE,
    CONTENT,
    RUNTIME,
    INTEGRATIONS,
    RUNNING,
    SHUTDOWN,
    COMPLETE
}
