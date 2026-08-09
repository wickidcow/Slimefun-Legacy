package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Read-only resolution result for one plugin dependency declaration. */
@SlimefunInternal
public final class PluginDependencyResolution {

    public enum State {
        ENABLED,
        DISABLED,
        MISSING
    }

    private final String declaredName;
    private final boolean required;
    private final State state;
    private final String resolvedPluginName;
    private final String resolvedPluginVersion;
    private final boolean providerAlias;

    PluginDependencyResolution(
            @Nonnull String declaredName,
            boolean required,
            @Nonnull State state,
            @Nullable String resolvedPluginName,
            @Nullable String resolvedPluginVersion,
            boolean providerAlias) {
        this.declaredName = Objects.requireNonNull(declaredName, "declaredName");
        this.required = required;
        this.state = Objects.requireNonNull(state, "state");
        this.resolvedPluginName = resolvedPluginName;
        this.resolvedPluginVersion = resolvedPluginVersion;
        this.providerAlias = providerAlias;
    }

    public @Nonnull String getDeclaredName() {
        return declaredName;
    }

    public boolean isRequired() {
        return required;
    }

    public @Nonnull State getState() {
        return state;
    }

    public @Nullable String getResolvedPluginName() {
        return resolvedPluginName;
    }

    public @Nullable String getResolvedPluginVersion() {
        return resolvedPluginVersion;
    }

    public boolean isProviderAlias() {
        return providerAlias;
    }

    public boolean isSatisfied() {
        return state == State.ENABLED;
    }

    public boolean isProblem() {
        return required && state != State.ENABLED;
    }
}
