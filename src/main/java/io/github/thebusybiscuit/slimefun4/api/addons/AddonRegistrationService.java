package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/**
 * Compatibility service for addon work that must run after Slimefun's initial item registry has finalized.
 *
 * <p>This does not freeze registration. Addons may continue registering content at runtime exactly as before.
 */
@SlimefunAPI
public interface AddonRegistrationService {

    @Nonnull
    AddonRegistrationRuntimeSnapshot getSnapshot();

    @Nonnull
    List<AddonRegistrationSnapshot> getAddonSnapshots();

    /**
     * Runs the callback immediately when initial registration is already finalized, otherwise queues it until
     * {@code SlimefunItemRegistryFinalizedEvent}. The callback is always protected by the standard addon failure
     * boundary and a failure never disables the plugin.
     *
     * @param plugin owning plugin
     * @param operation short diagnostic operation name
     * @param callback callback to execute
     * @return how the callback was handled at submission time
     */
    @Nonnull
    AddonRegistrationDisposition runAfterInitialRegistration(
            @Nonnull Plugin plugin, @Nonnull String operation, @Nonnull Runnable callback);

    default @Nonnull AddonRegistrationDisposition runAfterInitialRegistration(
            @Nonnull SlimefunAddon addon, @Nonnull String operation, @Nonnull Runnable callback) {
        Objects.requireNonNull(addon, "addon");
        return runAfterInitialRegistration(addon.getJavaPlugin(), operation, callback);
    }

    default @Nonnull Optional<AddonRegistrationSnapshot> getAddonSnapshot(@Nonnull String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        return getAddonSnapshots().stream()
                .filter(snapshot -> snapshot.getPluginName().equalsIgnoreCase(pluginName))
                .findFirst();
    }
}
