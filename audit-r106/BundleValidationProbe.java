package audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
                Class<?> clipboard = Class.forName("com.balugaq.jeg.utils.ClipboardUtil", true, owner);
                for (String value : List.of("slimefun:OLD_ITEM_ID", "", "owner's text & literal")) {
                    Component label = Component.text("Existing item");
                    Component hover = Component.text("Existing owner lore");
                    Object simple = clipboard.getMethod("makeComponentPaper", Component.class, String.class)
                            .invoke(null, label, value);
                    Object detailed = clipboard.getMethod("makeComponentPaper", Component.class, Component.class, String.class)
                            .invoke(null, label, hover, value);
                    for (Object result : List.of(simple, detailed)) {
                        if (!(result instanceof Component component)
                                || component.clickEvent() == null
                                || component.clickEvent().action() != ClickEvent.Action.COPY_TO_CLIPBOARD
                                || !value.equals(component.clickEvent().value())
                                || component.hoverEvent() == null) {
                            throw new AssertionError("Packaged guide clipboard payload or hover changed");
                        }
                    }
                }
                getLogger().info("R106_PLUGIN_STATE_PASS count=45 core=4.1.64 guideRenderer=linked clipboard=6");
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
