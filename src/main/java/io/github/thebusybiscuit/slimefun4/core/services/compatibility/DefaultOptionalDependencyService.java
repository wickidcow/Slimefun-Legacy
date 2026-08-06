package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.addons.CompatibilityInvocation;
import io.github.thebusybiscuit.slimefun4.api.addons.OptionalDependencyService;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Internal implementation of the optional-dependency lookup boundary. */
@SlimefunInternal
public final class DefaultOptionalDependencyService implements OptionalDependencyService {

    private final Plugin owner;

    public DefaultOptionalDependencyService(@Nonnull Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    @Override
    public boolean isPluginAvailable(@Nonnull String pluginName) {
        return findPlugin(pluginName).isPresent();
    }

    @Override
    public @Nonnull Optional<Plugin> findPlugin(@Nonnull String pluginName) {
        String normalized = normalize(pluginName);
        PluginManager pluginManager = owner.getServer().getPluginManager();
        Plugin plugin = pluginManager.getPlugin(normalized);
        if (plugin == null) {
            for (Plugin installed : pluginManager.getPlugins()) {
                if (installed.getName().equalsIgnoreCase(normalized)) {
                    plugin = installed;
                    break;
                }
            }
        }
        return plugin != null && pluginManager.isPluginEnabled(plugin) ? Optional.of(plugin) : Optional.empty();
    }

    @Override
    public @Nonnull Optional<Class<?>> findClass(@Nonnull String pluginName, @Nonnull String className) {
        String normalizedClassName = normalize(className);
        return findPlugin(pluginName).flatMap(plugin -> {
            try {
                return Optional.of(Class.forName(
                        normalizedClassName, false, plugin.getClass().getClassLoader()));
            } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
                return Optional.empty();
            }
        });
    }

    @Override
    public @Nonnull Optional<Method> findPublicMethod(
            @Nonnull String pluginName,
            @Nonnull String className,
            @Nonnull String methodName,
            @Nonnull Class<?>... parameterTypes) {
        String normalizedMethodName = normalize(methodName);
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        return findClass(pluginName, className).flatMap(type -> {
            try {
                return Optional.of(type.getMethod(normalizedMethodName, parameterTypes));
            } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
                return Optional.empty();
            }
        });
    }

    @Override
    public @Nonnull CompatibilityInvocation<Object> invoke(
            @Nonnull Method method, @Nullable Object target, @Nullable Object... arguments) {
        Objects.requireNonNull(method, "method");
        Object[] safeArguments = arguments == null ? new Object[0] : arguments;
        try {
            return CompatibilityInvocation.success(method.invoke(target, safeArguments));
        } catch (IllegalAccessException | IllegalArgumentException | ExceptionInInitializerError error) {
            return CompatibilityInvocation.failed(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            return CompatibilityInvocation.failed(cause == null ? error : cause);
        } catch (LinkageError error) {
            return CompatibilityInvocation.failed(error);
        }
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Value cannot be blank");
        }
        return normalized;
    }
}
