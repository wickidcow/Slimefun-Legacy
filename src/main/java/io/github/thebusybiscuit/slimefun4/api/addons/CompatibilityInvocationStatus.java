package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Outcome of a guarded optional-dependency reflection call. */
@SlimefunAPI
public enum CompatibilityInvocationStatus {
    SUCCESS,
    UNAVAILABLE,
    FAILED
}
