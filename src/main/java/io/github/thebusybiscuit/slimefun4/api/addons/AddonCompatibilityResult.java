package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Immutable runtime compatibility result for one installed addon. */
@SlimefunAPI
public final class AddonCompatibilityResult {

    private final String pluginName;
    private final String pluginVersion;
    private final AddonCompatibilityStatus status;
    private final AddonCompatibilitySource source;
    private final AddonCompatibilityDeclaration declaration;
    private final List<String> messages;

    public AddonCompatibilityResult(
            @Nonnull String pluginName,
            @Nonnull String pluginVersion,
            @Nonnull AddonCompatibilityStatus status,
            @Nonnull AddonCompatibilitySource source,
            @Nullable AddonCompatibilityDeclaration declaration,
            @Nonnull List<String> messages) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.status = Objects.requireNonNull(status, "status");
        this.source = Objects.requireNonNull(source, "source");
        this.declaration = declaration;
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nonnull String getPluginVersion() {
        return pluginVersion;
    }

    public @Nonnull AddonCompatibilityStatus getStatus() {
        return status;
    }

    public @Nonnull AddonCompatibilitySource getSource() {
        return source;
    }

    public @Nonnull Optional<AddonCompatibilityDeclaration> getDeclaration() {
        return Optional.ofNullable(declaration);
    }

    public @Nonnull List<String> getMessages() {
        return messages;
    }

    public boolean isUsable() {
        return status != AddonCompatibilityStatus.DISABLED && status != AddonCompatibilityStatus.INCOMPATIBLE;
    }

    public @Nonnull String describe() {
        return messages.isEmpty()
                ? status.getDisplayName()
                : status.getDisplayName() + ": " + String.join("; ", messages);
    }
}
