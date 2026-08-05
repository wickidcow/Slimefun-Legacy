package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.platform.MinecraftVersionNumber;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCompatibilityService;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformFamily;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformProfile;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformSupportLevel;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/** Internal detector backing the public platform compatibility service. */
@SlimefunInternal
public final class DefaultPlatformCompatibilityService implements PlatformCompatibilityService {

    private volatile PlatformProfile profile = PlatformProfile.unknown();

    public void initialize(@Nonnull Server server, boolean regionOwnedExecution) {
        Set<PlatformCapability> capabilities = detectCapabilities(server, regionOwnedExecution);
        PlatformFamily family = detectFamily(server, regionOwnedExecution, capabilities);
        PlatformSupportLevel supportLevel = supportLevel(family);
        String rawMinecraftVersion = server.getMinecraftVersion();
        MinecraftVersionNumber minecraftVersion =
                MinecraftVersionNumber.parse(rawMinecraftVersion).orElse(null);

        profile = new PlatformProfile(
                safe(server.getName()),
                safe(server.getVersion()),
                safe(rawMinecraftVersion),
                minecraftVersion,
                Runtime.version().feature(),
                family,
                supportLevel,
                capabilities);
    }

    @Override
    public @Nonnull PlatformProfile getProfile() {
        return profile;
    }

    @Override
    public boolean supports(@Nonnull PlatformCapability capability) {
        return profile.supports(capability);
    }

    @Override
    public boolean isMinecraftVersionAtLeast(int major, int minor, int patch) {
        MinecraftVersionNumber minimum = new MinecraftVersionNumber(major, minor, patch);
        return profile.getMinecraftVersion().map(version -> version.isAtLeast(minimum)).orElse(false);
    }

    static @Nonnull PlatformFamily detectFamily(
            @Nonnull Server server,
            boolean regionOwnedExecution,
            @Nonnull Set<PlatformCapability> capabilities) {
        String identity = (safe(server.getName()) + ' ' + safe(server.getVersion())).toLowerCase(Locale.ROOT);

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

    private static @Nonnull PlatformSupportLevel supportLevel(@Nonnull PlatformFamily family) {
        return switch (family) {
            case PAPER, PURPUR -> PlatformSupportLevel.SUPPORTED;
            case FOLIA -> PlatformSupportLevel.EXPERIMENTAL;
            case PAPER_DERIVATIVE -> PlatformSupportLevel.BEST_EFFORT;
            case UNKNOWN -> PlatformSupportLevel.UNSUPPORTED;
        };
    }

    private static @Nonnull Set<PlatformCapability> detectCapabilities(
            @Nonnull Server server, boolean regionOwnedExecution) {
        EnumSet<PlatformCapability> capabilities = EnumSet.noneOf(PlatformCapability.class);

        boolean regionScheduler = hasMethod(server.getClass(), "getRegionScheduler");
        boolean globalRegionScheduler = hasMethod(server.getClass(), "getGlobalRegionScheduler");
        boolean asyncScheduler = hasMethod(server.getClass(), "getAsyncScheduler");
        String serverIdentity =
                (safe(server.getName()) + ' ' + safe(server.getVersion())).toLowerCase(Locale.ROOT);

        if (regionScheduler
                || globalRegionScheduler
                || asyncScheduler
                || serverIdentity.contains("paper")
                || serverIdentity.contains("purpur")
                || serverIdentity.contains("folia")) {
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
        Class<?> adventureComponent = findClass("net.kyori.adventure.text.Component");
        if (adventureComponent != null && hasMethod(CommandSender.class, "sendMessage", adventureComponent)) {
            capabilities.add(PlatformCapability.ADVENTURE_COMPONENT_MESSAGES);
        }
        if (findClass("io.papermc.paper.datacomponent.DataComponentType") != null) {
            capabilities.add(PlatformCapability.DATA_COMPONENT_API);
        }

        return capabilities;
    }

    private static boolean hasMethod(@Nonnull Class<?> type, @Nonnull String name, Class<?>... parameterTypes) {
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

    private static Class<?> findClass(@Nonnull String className) {
        try {
            return Class.forName(className, false, DefaultPlatformCompatibilityService.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static @Nonnull String safe(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
