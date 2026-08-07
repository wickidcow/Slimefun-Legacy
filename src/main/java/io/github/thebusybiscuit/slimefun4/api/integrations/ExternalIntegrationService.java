package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** Addon-facing registry and runtime status service for optional external-system bridges. */
@SlimefunAPI
public interface ExternalIntegrationService {

    void register(@Nonnull ExternalIntegrationProvider provider);

    void unregister(@Nonnull Plugin plugin);

    void refresh();

    @Nonnull List<ExternalIntegrationStatus> getStatuses();

    default @Nonnull List<ExternalBlockIntegration> inspectBlock(@Nonnull Block block) {
        return List.of();
    }

    default @Nonnull Optional<ExternalIntegrationStatus> getStatus(@Nonnull String integrationId) {
        return getStatuses().stream()
                .filter(status -> status.getIntegrationId().equalsIgnoreCase(integrationId))
                .findFirst();
    }
}
