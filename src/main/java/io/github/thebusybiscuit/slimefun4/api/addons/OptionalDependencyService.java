package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.lang.reflect.Method;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.plugin.Plugin;

/**
 * Central optional-dependency lookup and guarded reflection boundary.
 *
 * <p>This keeps addon integrations from scattering plugin-manager lookups and class probes throughout their code.
 */
@SlimefunAPI
public interface OptionalDependencyService {

    boolean isPluginAvailable(@Nonnull String pluginName);

    @Nonnull
    Optional<Plugin> findPlugin(@Nonnull String pluginName);

    @Nonnull
    Optional<Class<?>> findClass(@Nonnull String pluginName, @Nonnull String className);

    @Nonnull
    Optional<Method> findPublicMethod(
            @Nonnull String pluginName,
            @Nonnull String className,
            @Nonnull String methodName,
            @Nonnull Class<?>... parameterTypes);

    @Nonnull
    CompatibilityInvocation<Object> invoke(
            @Nonnull Method method, @Nullable Object target, @Nullable Object... arguments);
}
