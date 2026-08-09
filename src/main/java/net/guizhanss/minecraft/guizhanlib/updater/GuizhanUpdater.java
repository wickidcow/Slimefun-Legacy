package net.guizhanss.minecraft.guizhanlib.updater;

import java.io.File;
import net.guizhanss.guizhanlib.updater.GuizhanBuildsUpdater;
import net.guizhanss.guizhanlib.updater.UpdaterConfig;
import org.bukkit.plugin.Plugin;

/**
 * Binary-compatibility shim for older GuizhanLib addons.
 *
 * <p>Older addons link against this historical class name. GuizhanLibPlugin 2.5.0 routes this
 * entry point through its plugin-owned universal updater, which cannot be reused when Slimefun
 * Legacy is acting only as a dependency provider. This shim preserves the historical method
 * descriptors while delegating to the standalone public GuizhanBuildsUpdater API instead.
 */
public final class GuizhanUpdater {

    private GuizhanUpdater() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void start(Plugin plugin, File file, String owner, String repository, String branch) {
        GuizhanBuildsUpdater.start(plugin, file, owner, repository, branch);
    }

    public static void start(
            Plugin plugin,
            File file,
            String owner,
            String repository,
            String branch,
            UpdaterConfig updaterConfig) {
        GuizhanBuildsUpdater.start(plugin, file, owner, repository, branch, updaterConfig);
    }
}
