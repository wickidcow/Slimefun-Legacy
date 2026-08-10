package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformFamily;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformSupportLevel;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * Single internal source of truth for runtime platform probes.
 *
 * <p>All reflection and implementation-class checks belong here. Core code and addons should consume the public
 * platform compatibility service instead of repeating these probes.
 */
@SlimefunInternal
public final class RuntimePlatformDetector {

    private static final String FOLIA_RUNTIME_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String PAPER_CONFIGURATION_CLASS = "io.papermc.paper.configuration.Configuration";
    private static final String ADVENTURE_COMPONENT_CLASS = "net.kyori.adventure.text.Component";
    private static final String DATA_COMPONENT_TYPE_CLASS = "io.papermc.paper.datacomponent.DataComponentType";
    private static final String PLAYER_PICK_BLOCK_EVENT_CLASS = "io.papermc.paper.event.player.PlayerPickBlockEvent";

    private RuntimePlatformDetector() {}

    public static boolean isRegionOwnedExecution() {
        return isClassPresent(FOLIA_RUNTIME_CLASS);
    }

    public static @Nonnull Set<PlatformCapability> detectCapabilities(@Nonnull Server server) {
        EnumSet<PlatformCapability> capabilities = EnumSet.noneOf(PlatformCapability.class);

        boolean regionScheduler = hasMethod(server.getClass(), "getRegionScheduler");
        boolean globalRegionScheduler = hasMethod(server.getClass(), "getGlobalRegionScheduler");
        boolean asyncScheduler = hasMethod(server.getClass(), "getAsyncScheduler");
        boolean regionOwnedExecution = isRegionOwnedExecution();
        String identity = identity(server);

        if (regionScheduler
                || globalRegionScheduler
                || asyncScheduler
                || isClassPresent(PAPER_CONFIGURATION_CLASS)
                || identity.contains("paper")
                || identity.contains("purpur")
                || identity.contains("folia")) {
            capabilities.add(PlatformCapability.PAPER_API);
        }
        if (regionScheduler) {
            capabilities.add(PlatformCapability.REGION_SCHEDULER_API);
        }
        if (globalRegionScheduler) {
            capabilities.add(PlatformCapability.GLOBAL_REGION_SCHEDULER_API);
        }
        if (asyncScheduler) {
            capabilities.add(PlatformCapability.ASYNC_SCHEDULER_API);
        }
        if (regionOwnedExecution) {
            capabilities.add(PlatformCapability.REGION_OWNED_EXECUTION);
        }
        if (hasMethod(World.class, "getChunkAtAsync")) {
            capabilities.add(PlatformCapability.ASYNC_CHUNK_LOADING);
        }

        Class<?> adventureComponent = findClass(ADVENTURE_COMPONENT_CLASS);
        if (adventureComponent != null && hasMethod(CommandSender.class, "sendMessage", adventureComponent)) {
            capabilities.add(PlatformCapability.ADVENTURE_COMPONENT_MESSAGES);
        }
        if (isClassPresent(DATA_COMPONENT_TYPE_CLASS)) {
            capabilities.add(PlatformCapability.DATA_COMPONENT_API);
        }
        if (isClassPresent(PLAYER_PICK_BLOCK_EVENT_CLASS)) {
            capabilities.add(PlatformCapability.PLAYER_PICK_BLOCK_EVENT);
        }

        return capabilities;
    }

    public static @Nonnull PlatformFamily detectFamily(
            @Nonnull Server server, @Nonnull Set<PlatformCapability> capabilities) {
        return detectFamily(server, capabilities, isRegionOwnedExecution());
    }

    public static @Nonnull PlatformFamily detectFamily(
            @Nonnull Server server, @Nonnull Set<PlatformCapability> capabilities, boolean regionOwnedExecution) {
        String identity = identity(server);

        if (regionOwnedExecution || identity.contains("folia")) {
            return PlatformFamily.FOLIA;
        }
        if (identity.contains("purpur")) {
            return PlatformFamily.PURPUR;
        }
        if ("paper".equalsIgnoreCase(server.getName()) || identity.contains("paper")) {
            return PlatformFamily.PAPER;
        }
        if (capabilities.contains(PlatformCapability.PAPER_API)) {
            return PlatformFamily.PAPER_DERIVATIVE;
        }
        return PlatformFamily.UNKNOWN;
    }

    public static @Nonnull PlatformSupportLevel supportLevel(@Nonnull PlatformFamily family) {
        return switch (family) {
            case PAPER, PURPUR -> PlatformSupportLevel.SUPPORTED;
            case FOLIA -> PlatformSupportLevel.EXPERIMENTAL;
            case PAPER_DERIVATIVE -> PlatformSupportLevel.BEST_EFFORT;
            case UNKNOWN -> PlatformSupportLevel.UNSUPPORTED;
        };
    }

    public static boolean isClassPresent(@Nonnull String className) {
        return findClass(className) != null;
    }

    public static @Nullable Class<?> findClass(@Nonnull String className) {
        try {
            return Class.forName(className, false, RuntimePlatformDetector.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    public static @Nonnull String identity(@Nonnull Server server) {
        return (safe(server.getName()) + ' ' + safe(server.getVersion())).toLowerCase(Locale.ROOT);
    }

    public static boolean hasMethod(@Nonnull Class<?> type, @Nonnull String name, Class<?>... parameterTypes) {
        if (parameterTypes.length > 0) {
            try {
                type.getMethod(name, parameterTypes);
                return true;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        }

        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static @Nonnull String safe(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
