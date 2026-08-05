package city.norain.slimefun4;

import city.norain.slimefun4.compatibillty.VersionedEvent;
import city.norain.slimefun4.listener.SlimefunMigrateListener;
import city.norain.slimefun4.utils.EnvUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import io.github.bakedlibs.dough.versions.MinecraftVersion;
import io.github.bakedlibs.dough.versions.UnknownServerVersionException;
import io.github.thebusybiscuit.slimefun4.api.platform.MinecraftVersionNumber;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import lombok.Getter;
import net.guizhanss.guizhanlib.minecraft.utils.MinecraftVersionUtil;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.Server;

public final class SlimefunExtended {
    private static SlimefunMigrateListener migrateListener = new SlimefunMigrateListener();

    @Getter
    private static boolean databaseDebugMode = false;

    @Deprecated(since = "2026.1.1", forRemoval = true)
    private static MinecraftVersion minecraftVersion;

    @Deprecated(since = "2026.1.1", forRemoval = true)
    public static MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Returns the numeric Minecraft version reported by the server.
     *
     * <p>Examples: {@code 26.1.2} becomes {@code (26, 1, 2)} and {@code 26.1} becomes
     * {@code (26, 1, 0)}. Snapshot identifiers are not guessed and return {@code null}.
     *
     * @param server the current server
     * @return the parsed server version, or {@code null} when it is not a numeric release
     * @since 2026.1
     */
    public static ServerVersion getServerVerDetail(Server server) {
        return MinecraftVersionNumber.parse(server.getMinecraftVersion())
                .map(version -> new ServerVersion(version.getMajor(), version.getMinor(), version.getPatch()))
                .orElse(null);
    }

    /**
     * @since 2026.1
     * @param major the major version number (e.g., 26 for Minecraft 26.1)
     * @param minor
     * @return
     */
    public static boolean isAtLeast(int major, int minor) {
        return MinecraftVersionUtil.isAtLeast(major, minor);
    }

    /**
     * @since 2026.1
     * @param major the major version number (e.g., 26 for Minecraft 26.1)
     * @param minor
     * @return
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        return MinecraftVersionUtil.isAtLeast(major, minor, patch);
    }

    private static void checkDebug() {
        if ("true".equals(System.getProperty("slimefun.database.debug"))) {
            databaseDebugMode = true;

            Slimefun.getSQLProfiler().start();
            Slimefun.logger().log(Level.INFO, "Database debug mode enabled");
        } else {
            Configurator.setLevel(HikariConfig.class.getName(), org.apache.logging.log4j.Level.OFF);
            Configurator.setLevel(HikariDataSource.class.getName(), org.apache.logging.log4j.Level.OFF);
            Configurator.setLevel(HikariPool.class.getName(), org.apache.logging.log4j.Level.OFF);
        }
    }

    public static boolean checkEnvironment(@Nonnull Slimefun sf) {
        try {
            minecraftVersion = MinecraftVersion.of(sf.getServer());
        } catch (UnknownServerVersionException ignored) {
            // sf.getLogger().log(Level.WARNING, "Unable to recognize your server version :(");
            // return false;
        }

        if (EnvironmentChecker.checkHybridServer()) {
            sf.getLogger().log(Level.WARNING, "#######################################################");
            sf.getLogger().log(Level.WARNING, "");
            sf.getLogger().log(Level.WARNING, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            sf.getLogger().log(Level.WARNING, "Hybrid server detected, Slimefun will be disabled!");
            sf.getLogger().log(Level.WARNING, "Hybrid servers have been reported to have issues by multiple users.");
            sf.getLogger().log(Level.WARNING, "Forced environment check bypass will not receive support.");
            sf.getLogger().log(Level.WARNING, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            sf.getLogger().log(Level.WARNING, "");
            sf.getLogger().log(Level.WARNING, "#######################################################");
            return false;
        }

        if (Slimefun.getConfigManager().isBypassEnvironmentCheck()) {
            sf.getLogger().log(Level.WARNING, "#######################################################");
            sf.getLogger().log(Level.WARNING, "");
            sf.getLogger().log(Level.WARNING, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            sf.getLogger().log(Level.WARNING, "Environment compatibility check disabled detected!");
            sf.getLogger().log(Level.WARNING, "Failing compatibility checks will not receive support.");
            sf.getLogger().log(Level.WARNING, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            sf.getLogger().log(Level.WARNING, "");
            sf.getLogger().log(Level.WARNING, "#######################################################");
            return true;
        } else {
            return !EnvironmentChecker.checkIncompatiblePlugins(sf.getLogger());
        }
    }

    public static void init(@Nonnull Slimefun sf) {
        EnvironmentChecker.scheduleSlimeGlueCheck(sf);

        EnvUtil.init();

        checkDebug();

        VaultIntegration.register(sf);

        migrateListener.register(sf);

        VersionedEvent.init();
    }

    public static void shutdown() {
        migrateListener = null;

        VaultIntegration.cleanup();

        databaseDebugMode = false;
    }
}
