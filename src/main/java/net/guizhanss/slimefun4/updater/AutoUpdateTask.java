package net.guizhanss.slimefun4.updater;

import java.io.File;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.guizhanss.guizhanlib.updater.GuizhanBuildsUpdater;
import net.guizhanss.guizhanlib.updater.UpdaterConfig;
import org.bukkit.plugin.Plugin;

/**
 * 自动更新任务.
 *
 * @author ybw0014
 */
public class AutoUpdateTask implements Runnable {

    private static final String GITHUB_USER = "SlimefunGuguProject";
    private static final String GITHUB_REPO = "Slimefun4";
    private static final String GITHUB_BRANCH_BETA = "master";
    private static final String GITHUB_BRANCH_RELEASE = "release";

    private final Plugin plugin;
    private final File file;
    private final String version;

    @ParametersAreNonnullByDefault
    public AutoUpdateTask(Plugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.version = plugin.getPluginMeta().getVersion();
    }

    @Override
    public void run() {
        String branch = getBranch();
        if (branch == null) {
            return;
        }
        try {
            // use updater in lib plugin
            char[] pluginPackage = {
                'n', 'e', 't', '.', 'g', 'u', 'i', 'z', 'h', 'a', 'n', 's', 's', '.', 'g', 'u', 'i', 'z', 'h', 'a', 'n',
                'l', 'i', 'b', 'p', 'l', 'u', 'g', 'i', 'n'
            };
            Class<?> clazz = Class.forName(new String(pluginPackage) + ".updater.GuizhanUpdater");
            Method updaterStart = clazz.getDeclaredMethod(
                    "start", Plugin.class, File.class, String.class, String.class, String.class);
            updaterStart.invoke(null, plugin, file, GITHUB_USER, GITHUB_REPO, branch);
        } catch (Exception ignored) {
            // use updater in lib
            GuizhanBuildsUpdater.start(
                    plugin,
                    file,
                    GITHUB_USER,
                    GITHUB_REPO,
                    branch,
                    UpdaterConfig.builder()
                            .baseUrl("https://builds.guizhanss.cn/")
                            .build());
        }
    }

    @Nullable private String getBranch() {
        if (version.endsWith("release")) {
            return GITHUB_BRANCH_RELEASE;
        } else if (version.endsWith("Beta")) {
            return GITHUB_BRANCH_BETA;
        } else {
            return null;
        }
    }
}
