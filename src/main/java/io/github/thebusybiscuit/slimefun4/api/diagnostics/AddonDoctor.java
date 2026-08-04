package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/**
 * Optional diagnostics service for Slimefun addons.
 *
 * <p>Addons register an implementation through Bukkit's {@code ServicesManager}. Slimefun Legacy
 * discovers registered providers for {@code /slimefun doctor addons}. Other Slimefun cores can
 * safely ignore this service, allowing an addon to isolate the Legacy bridge behind a runtime
 * class-presence check.</p>
 */
@SlimefunAPI
public interface AddonDoctor {

    /** Human-readable addon name shown in doctor output. */
    @Nonnull
    String getAddonName();

    /**
     * Scans this addon's loaded runtime state and optionally repairs issues that are safe to fix.
     * Implementations must not load chunks solely for a doctor pass.
     */
    @Nonnull
    AddonDoctorReport runDoctor(boolean repair);
}
