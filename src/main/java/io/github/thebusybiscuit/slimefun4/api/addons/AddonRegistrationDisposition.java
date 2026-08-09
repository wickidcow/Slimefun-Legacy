package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Describes how a callback submitted through {@link AddonRegistrationService} was handled. */
@SlimefunAPI
public enum AddonRegistrationDisposition {
    /** The initial registry was already finalized and the callback completed successfully. */
    EXECUTED,

    /** The callback was accepted and will run after initial item registration is finalized. */
    QUEUED,

    /** The callback was not run because its owning plugin was disabled. */
    SKIPPED_DISABLED,

    /** The callback ran behind the guarded addon boundary and failed. */
    FAILED
}
