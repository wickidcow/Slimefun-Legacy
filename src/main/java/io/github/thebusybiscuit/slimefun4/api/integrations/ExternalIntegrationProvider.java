package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Optional bridge registration for an external machine, storage, cargo, energy, or fluid system.
 *
 * <p>Providers are deliberately capability-based so Slimefun does not need a compile-time dependency on rapidly
 * changing third-party frameworks such as Rebar/Pylon.
 */
@SlimefunAPI
public interface ExternalIntegrationProvider {

    @Nonnull String getIntegrationId();

    @Nonnull Plugin getPlugin();

    @Nonnull Set<ExternalIntegrationCapability> getCapabilities();

    default @Nonnull Optional<ExternalBlockIntegration> inspectBlock(@Nonnull Block block) {
        return Optional.empty();
    }

    default @Nonnull String getDisplayName() {
        return getPlugin().getName();
    }

    default @Nonnull String getStatusDescription() {
        return "Bridge provider registered";
    }
}
