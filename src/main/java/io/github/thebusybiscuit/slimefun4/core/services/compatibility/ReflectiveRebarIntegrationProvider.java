package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalBlockIntegration;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationCapability;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** Built-in reflection-only Rebar/Pylon block capability adapter. */
final class ReflectiveRebarIntegrationProvider implements ExternalIntegrationProvider {

    private final String integrationId;
    private final String displayName;
    private final Plugin plugin;
    private final ReflectiveRebarAccess access;
    private final boolean pylonOnly;

    ReflectiveRebarIntegrationProvider(
            String integrationId, String displayName, Plugin plugin, ReflectiveRebarAccess access, boolean pylonOnly) {
        this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.access = Objects.requireNonNull(access, "access");
        this.pylonOnly = pylonOnly;
    }

    @Override
    public @Nonnull String getIntegrationId() {
        return integrationId;
    }

    @Override
    public @Nonnull Plugin getPlugin() {
        return plugin;
    }

    @Override
    public @Nonnull Set<ExternalIntegrationCapability> getCapabilities() {
        return access.getSupportedCapabilities();
    }

    @Override
    public @Nonnull Optional<ExternalBlockIntegration> inspectBlock(@Nonnull Block block) {
        return access.inspect(block).flatMap(info -> {
            if (info.pylon() != pylonOnly) {
                return Optional.empty();
            }
            return Optional.of(new ExternalBlockIntegration(
                    integrationId,
                    displayName,
                    plugin.getName(),
                    info.className(),
                    info.contentKey(),
                    info.capabilities(),
                    info.detail()));
        });
    }

    @Override
    public @Nonnull String getDisplayName() {
        return displayName;
    }

    @Override
    public @Nonnull String getStatusDescription() {
        String scope = pylonOnly
                ? "Pylon blocks are classified through Rebar BlockStorage and marker interfaces. "
                : "Non-Pylon Rebar blocks are classified through BlockStorage and marker interfaces. ";
        return scope + access.getStatusDescription();
    }
}
