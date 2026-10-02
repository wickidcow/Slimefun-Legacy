package audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only disposable-server probe; never included in the addon release archive. */
public final class BundleValidationProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                List<String> records = Files.readAllLines(Path.of("expected-addon-versions.tsv"), StandardCharsets.UTF_8);
                if (records.size() != 45) {
                    throw new IllegalStateException("Expected exactly 45 addon records, found " + records.size());
                }
                var seen = new HashSet<String>();
                Plugin guide = null;
                for (String record : records) {
                    String[] fields = record.split("\\t", -1);
                    if (fields.length != 2 || !seen.add(fields[0])) {
                        throw new IllegalStateException("Invalid or duplicate expected plugin record");
                    }
                    Plugin plugin = requireEnabled(fields[0]);
                    if (!fields[1].equals(plugin.getPluginMeta().getVersion())) {
                        throw new IllegalStateException("Unexpected version for " + fields[0] + ": "
                                + plugin.getPluginMeta().getVersion());
                    }
                    if (fields[0].equals("JustEnoughGuide")) {
                        guide = plugin;
                    }
                }
                if (!"4.1.64".equals(requireEnabled("Slimefun").getPluginMeta().getVersion())) {
                    throw new IllegalStateException("This addon-only check requires published core 4.1.64");
                }
                requireEnabled("WorldEdit");
                requireEnabled("WorldEditSlimefun");
                if (guide == null) {
                    throw new IllegalStateException("JustEnoughGuide was not among the 45 expected addons");
                }
                ClassLoader owner = guide.getClass().getClassLoader();
                Class<?> renderer = Class.forName("com.balugaq.jeg.utils.clickhandler.OnDisplay$ItemGroup", false, owner);
                if (renderer.getClassLoader() != owner) {
                    throw new IllegalStateException("JEG item-group renderer was loaded by the wrong plugin");
                }
                getLogger().info("R106_PLUGIN_STATE_PASS count=45 core=4.1.64 guideRenderer=linked");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "R106_PLUGIN_STATE_FAIL", error);
            }
        }, 60L);
    }

    private static Plugin requireEnabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("Required plugin is not enabled: " + name);
        }
        return plugin;
    }
}
