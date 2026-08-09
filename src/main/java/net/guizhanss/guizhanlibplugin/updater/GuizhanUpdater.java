package net.guizhanss.guizhanlibplugin.updater;

import java.io.File;
import net.guizhanss.guizhanlib.updater.GuizhanBuildsUpdater;
import net.guizhanss.guizhanlib.updater.UpdaterConfig;
import org.bukkit.plugin.Plugin;

/**
 * Binary-compatibility shim for addons compiled against the GuizhanLibPlugin updater package.
 *
 * <p>The concrete GuizhanLibPlugin JavaPlugin is intentionally not emulated. Only the historical
 * updater entry points are provided, and they delegate to GuizhanLib's standalone updater API.
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

    public static void start(
            Plugin plugin, File file, String owner, String repository, String branch, boolean checkOnly) {
        GuizhanBuildsUpdater.start(
                plugin,
                file,
                owner,
                repository,
                branch,
                UpdaterConfig.builder().checkOnly(checkOnly).build());
    }
}
