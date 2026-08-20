package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/**
 * Marks a radioactive item whose passive exposure should only apply while the item is held.
 *
 * <p>This is intended for usable equipment where carrying the item in a backpack or normal inventory slot should not
 * constantly irradiate the player. Implementations still participate in Slimefun's normal radioactive-item registry.
 */
@SlimefunAPI
public interface HeldRadioactive extends Radioactive {

    /**
     * Returns the minimum delay between passive held-item exposure pulses.
     *
     * @return exposure interval in milliseconds
     */
    default long getHeldExposureIntervalMillis() {
        return 10_000L;
    }
}
