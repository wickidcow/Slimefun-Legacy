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

    @Nonnull
    List<ExternalIntegrationStatus> getStatuses();

    default @Nonnull List<ExternalBlockIntegration> inspectBlock(@Nonnull Block block) {
        return List.of();
    }

    /** Returns currently tracked external-provider failures, newest first. */
    default @Nonnull List<ExternalIntegrationFailureSnapshot> getFailureSnapshots(int limit) {
        return List.of();
    }

    /** Returns the number of provider operations currently in a failing or isolated state. */
    default int getActiveFailureCount() {
        return 0;
    }

    /** Returns all external-provider failures observed since startup. */
    default long getObservedFailureCount() {
        return 0L;
    }

    /** Returns duplicate provider failure reports suppressed since startup. */
    default long getSuppressedFailureReportCount() {
        return 0L;
    }

    /** Clears isolated/failing state for one integration id. */
    default boolean retry(@Nonnull String integrationId) {
        return false;
    }

    /** Clears all external integration isolation/failure state. */
    default int retryAll() {
        return 0;
    }

    default @Nonnull Optional<ExternalIntegrationStatus> getStatus(@Nonnull String integrationId) {
        return getStatuses().stream()
                .filter(status -> status.getIntegrationId().equalsIgnoreCase(integrationId))
                .findFirst();
    }
}
