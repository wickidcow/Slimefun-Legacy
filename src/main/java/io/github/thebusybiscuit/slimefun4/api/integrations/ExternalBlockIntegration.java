package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.NamespacedKey;

/** Immutable capability snapshot for a block discovered through an external integration provider. */
@SlimefunAPI
public final class ExternalBlockIntegration {

    private final String integrationId;
    private final String displayName;
    private final String pluginName;
    private final String blockType;
    private final NamespacedKey contentKey;
    private final Set<ExternalIntegrationCapability> capabilities;
    private final String detail;

    public ExternalBlockIntegration(
            @Nonnull String integrationId,
            @Nonnull String displayName,
            @Nonnull String pluginName,
            @Nonnull String blockType,
            @Nullable NamespacedKey contentKey,
            @Nonnull Set<ExternalIntegrationCapability> capabilities,
            @Nonnull String detail) {
        this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.blockType = Objects.requireNonNull(blockType, "blockType");
        this.contentKey = contentKey;
        EnumSet<ExternalIntegrationCapability> copied = capabilities.isEmpty()
                ? EnumSet.noneOf(ExternalIntegrationCapability.class)
                : EnumSet.copyOf(capabilities);
        this.capabilities = Collections.unmodifiableSet(copied);
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public @Nonnull String getIntegrationId() {
        return integrationId;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nonnull String getBlockType() {
        return blockType;
    }

    public @Nullable NamespacedKey getContentKey() {
        return contentKey;
    }

    public @Nonnull Set<ExternalIntegrationCapability> getCapabilities() {
        return capabilities;
    }

    public @Nonnull String getDetail() {
        return detail;
    }
}
