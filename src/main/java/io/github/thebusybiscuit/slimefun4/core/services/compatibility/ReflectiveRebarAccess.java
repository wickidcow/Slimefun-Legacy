package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationCapability;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Reflection-only access to Rebar's block storage and capability marker interfaces.
 *
 * <p>This class deliberately contains no compile-time Rebar types. Rebar is experimental and its API can change
 * between versions, so an incompatible probe must degrade to diagnostics instead of preventing Slimefun from loading.
 */
final class ReflectiveRebarAccess {

    private static final String REBAR_BLOCK = "io.github.pylonmc.rebar.block.RebarBlock";
    private static final String[] STORAGE_TYPES = {
        "io.github.pylonmc.rebar.block.BlockStorage",
        "io.github.pylonmc.rebar.block.storage.BlockStorage",
        "io.github.pylonmc.rebar.block.RebarBlockStorage",
        "io.github.pylonmc.rebar.block.storage.RebarBlockStorage",
        REBAR_BLOCK
    };

    private static final Marker[] MARKERS = {
        new Marker(
                "io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock",
                ExternalIntegrationCapability.INVENTORY,
                ExternalIntegrationCapability.STORAGE),
        new Marker(
                "io.github.pylonmc.rebar.block.interfaces.VanillaInventoryRebarBlockHandler",
                ExternalIntegrationCapability.INVENTORY,
                ExternalIntegrationCapability.STORAGE),
        new Marker("io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock", ExternalIntegrationCapability.CARGO),
        new Marker("io.github.pylonmc.rebar.block.interfaces.CargoRebarBlock", ExternalIntegrationCapability.CARGO),
        new Marker(
                "io.github.pylonmc.rebar.block.interfaces.ProcessorRebarBlock", ExternalIntegrationCapability.MACHINE),
        new Marker(
                "io.github.pylonmc.rebar.block.interfaces.RecipeProcessorRebarBlock",
                ExternalIntegrationCapability.MACHINE),
        new Marker("io.github.pylonmc.rebar.block.interfaces.FluidRebarBlock", ExternalIntegrationCapability.FLUID),
        new Marker(
                "io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock", ExternalIntegrationCapability.FLUID),
        new Marker("io.github.pylonmc.rebar.block.interfaces.FluidTankRebarBlock", ExternalIntegrationCapability.FLUID)
    };

    private final Class<?> rebarBlockType;
    private final List<LoadedMarker> markers;
    private final Method resolver;
    private final Object resolverTarget;
    private final String statusDescription;

    private ReflectiveRebarAccess(
            Class<?> rebarBlockType,
            List<LoadedMarker> markers,
            @Nullable Method resolver,
            @Nullable Object resolverTarget,
            String statusDescription) {
        this.rebarBlockType = rebarBlockType;
        this.markers = List.copyOf(markers);
        this.resolver = resolver;
        this.resolverTarget = resolverTarget;
        this.statusDescription = statusDescription;
    }

    static @Nonnull ReflectiveRebarAccess create(@Nonnull Plugin rebarPlugin) throws ClassNotFoundException {
        ClassLoader loader = rebarPlugin.getClass().getClassLoader();
        Class<?> rebarBlockType = Class.forName(REBAR_BLOCK, false, loader);
        List<LoadedMarker> loadedMarkers = new ArrayList<>();
        for (Marker marker : MARKERS) {
            try {
                loadedMarkers.add(
                        new LoadedMarker(Class.forName(marker.className(), false, loader), marker.capabilities()));
            } catch (ClassNotFoundException ignored) {
                // Rebar's experimental API changes between versions. Missing marker types simply remove that
                // capability.
            }
        }

        ResolverMatch match = findResolver(loader, rebarBlockType);
        String detail;
        if (match == null) {
            detail =
                    "Rebar API markers detected, but no compatible BlockStorage resolver was found; block probing is disabled.";
        } else {
            detail = "Reflective Rebar adapter ready via "
                    + match.method().getDeclaringClass().getSimpleName()
                    + '#'
                    + match.method().getName()
                    + ". Energy exchange remains disabled because Rebar electricity uses different network semantics.";
        }
        return new ReflectiveRebarAccess(
                rebarBlockType,
                loadedMarkers,
                match == null ? null : match.method(),
                match == null ? null : match.target(),
                detail);
    }

    boolean isBlockProbeAvailable() {
        return resolver != null;
    }

    @Nonnull
    Set<ExternalIntegrationCapability> getSupportedCapabilities() {
        EnumSet<ExternalIntegrationCapability> result = EnumSet.noneOf(ExternalIntegrationCapability.class);
        for (LoadedMarker marker : markers) {
            for (ExternalIntegrationCapability capability : marker.capabilities()) {
                result.add(capability);
            }
        }
        return Set.copyOf(result);
    }

    @Nonnull
    String getStatusDescription() {
        return statusDescription;
    }

    @Nonnull
    Optional<RebarBlockInfo> inspect(@Nonnull Block block) {
        Object value = resolve(block);
        if (value == null || !rebarBlockType.isInstance(value)) {
            return Optional.empty();
        }

        EnumSet<ExternalIntegrationCapability> capabilities = EnumSet.noneOf(ExternalIntegrationCapability.class);
        for (LoadedMarker marker : markers) {
            if (marker.type().isInstance(value)) {
                for (ExternalIntegrationCapability capability : marker.capabilities()) {
                    capabilities.add(capability);
                }
            }
        }

        NamespacedKey key = findContentKey(value);
        String className = value.getClass().getName();
        boolean pylon = className.startsWith("io.github.pylonmc.pylon.")
                || key != null && key.getNamespace().equalsIgnoreCase("pylon");
        String detail = capabilities.isEmpty()
                ? "Rebar block found; no currently mapped inventory, cargo, machine, or fluid marker interface was detected."
                : "Mapped from Rebar marker interfaces. This is capability discovery only; Slimefun does not take ownership of Rebar network state.";
        return Optional.of(new RebarBlockInfo(className, key, Set.copyOf(capabilities), pylon, detail));
    }

    private @Nullable Object resolve(Block block) {
        if (resolver == null) {
            return null;
        }
        Object argument = resolver.getParameterTypes()[0] == Location.class ? block.getLocation() : block;
        try {
            Object result = resolver.invoke(resolverTarget, argument);
            if (result instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            return result;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private @Nullable NamespacedKey findContentKey(Object block) {
        for (Method method : block.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !NamespacedKey.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.equals("getkey")
                    && !name.equals("key")
                    && !name.contains("schemakey")
                    && !name.contains("blockkey")) {
                continue;
            }
            try {
                Object result = method.invoke(block);
                if (result instanceof NamespacedKey key) {
                    return key;
                }
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError ignored) {
                // The key is optional diagnostic metadata. Capability classification can continue without it.
            }
        }

        for (Method method : block.getClass().getMethods()) {
            if (method.getParameterCount() != 0
                    || !method.getName().toLowerCase(Locale.ROOT).contains("schema")) {
                continue;
            }
            try {
                Object schema = method.invoke(block);
                if (schema == null) {
                    continue;
                }
                for (Method keyMethod : schema.getClass().getMethods()) {
                    if (keyMethod.getParameterCount() == 0
                            && NamespacedKey.class.isAssignableFrom(keyMethod.getReturnType())
                            && (keyMethod.getName().equals("getKey")
                                    || keyMethod.getName().equals("key"))) {
                        Object result = keyMethod.invoke(schema);
                        if (result instanceof NamespacedKey key) {
                            return key;
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError ignored) {
                // Optional diagnostic metadata only.
            }
        }
        return null;
    }

    private static @Nullable ResolverMatch findResolver(ClassLoader loader, Class<?> rebarBlockType) {
        List<ResolverMatch> matches = new ArrayList<>();
        for (String className : STORAGE_TYPES) {
            try {
                Class<?> owner = Class.forName(className, false, loader);
                Object singleton = findSingleton(owner);
                for (Method method : owner.getMethods()) {
                    if (method.getParameterCount() != 1) {
                        continue;
                    }
                    Class<?> parameter = method.getParameterTypes()[0];
                    if (parameter != Block.class && parameter != Location.class) {
                        continue;
                    }
                    if (!isResolverReturnType(method.getReturnType(), rebarBlockType)) {
                        continue;
                    }
                    Object target = Modifier.isStatic(method.getModifiers()) ? null : singleton;
                    if (!Modifier.isStatic(method.getModifiers()) && target == null) {
                        continue;
                    }
                    matches.add(new ResolverMatch(method, target, resolverScore(method, rebarBlockType)));
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Try the next known storage location.
            }
        }
        return matches.stream()
                .max(Comparator.comparingInt(ResolverMatch::score))
                .orElse(null);
    }

    private static boolean isResolverReturnType(Class<?> returnType, Class<?> rebarBlockType) {
        return rebarBlockType.isAssignableFrom(returnType) || Optional.class.isAssignableFrom(returnType);
    }

    private static int resolverScore(Method method, Class<?> rebarBlockType) {
        int score = method.getParameterTypes()[0] == Block.class ? 50 : 30;
        String name = method.getName().toLowerCase(Locale.ROOT);
        if (name.equals("get")) {
            score += 100;
        } else if (name.equals("getblock") || name.equals("getrebarblock")) {
            score += 90;
        } else if (name.startsWith("get")) {
            score += 70;
        } else if (name.contains("find")) {
            score += 50;
        } else if (name.contains("block")) {
            score += 30;
        }
        if (rebarBlockType.isAssignableFrom(method.getReturnType())) {
            score += 40;
        } else if (Optional.class.isAssignableFrom(method.getReturnType())) {
            score += 20;
        }
        if (method.getDeclaringClass().getName().endsWith("BlockStorage")) {
            score += 40;
        }
        return score;
    }

    private static @Nullable Object findSingleton(Class<?> type) {
        try {
            Field field = type.getField("INSTANCE");
            if (Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                return field.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException | RuntimeException ignored) {
            // Not a Kotlin object; static methods can still be used.
        }
        return null;
    }

    record RebarBlockInfo(
            String className,
            @Nullable NamespacedKey contentKey,
            Set<ExternalIntegrationCapability> capabilities,
            boolean pylon,
            String detail) {}

    private record Marker(String className, ExternalIntegrationCapability... capabilities) {}

    private record LoadedMarker(Class<?> type, ExternalIntegrationCapability[] capabilities) {}

    private record ResolverMatch(Method method, @Nullable Object target, int score) {}
}
