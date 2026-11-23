package networksprobe.receipt;

import com.google.gson.JsonObject;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Metadata-only companion; the pinned storage fixture retains every operation and assertion. */
public final class PairedStorageReceipt extends JavaPlugin {
    private JsonObject started;
    private String phase;
    private String token;

    @Override
    public void onEnable() {
        try {
            phase = System.getProperty("sfl.paired.phase", "");
            token = System.getProperty("sfl.paired.token", "");
            if (!"disposable".equals(System.getProperty("networks.paired.probe"))
                    || !phase.matches("seed|verify") || !token.matches("[0-9a-f-]{36}")) {
                throw new IllegalStateException("Disposable paired receipt identity is required");
            }
            Plugin core = enabled("Slimefun");
            Plugin networks = enabled("Networks");
            Plugin ie2 = enabled("InfinityExpansion2");
            enabled("PairedIE2StorageProbe");
            Properties properties = new Properties();
            try (InputStream input = ((JavaPlugin) core).getResource("git.properties")) {
                if (input == null) {
                    throw new IllegalStateException("Loaded core source properties are missing");
                }
                properties.load(input);
            }
            started = new JsonObject();
            started.addProperty("schema", 1);
            started.addProperty("phase", phase);
            started.addProperty("token", token);
            started.addProperty("pid", ProcessHandle.current().pid());
            started.addProperty("software", Bukkit.getName());
            started.addProperty("minecraft", Bukkit.getMinecraftVersion());
            started.addProperty("server_version", Bukkit.getVersion());
            started.addProperty("core_source", properties.getProperty("git.source.commit"));
            started.addProperty("core_commit", properties.getProperty("git.commit.id"));
            started.addProperty("core_version", core.getPluginMeta().getVersion());
            started.addProperty("networks_version", networks.getPluginMeta().getVersion());
            started.addProperty("ie2_version", ie2.getPluginMeta().getVersion());
            started.addProperty("primary_thread", Bukkit.isPrimaryThread());
            started.add("registered_style_audit", verifyAddonRegisteredStyle());
            started.addProperty("status", "READY");
            write("startup.json", started);
            getLogger().info("PAIRED_STORAGE_RECEIPT READY phase=" + phase + " token=" + token);
        } catch (Exception failure) {
            getLogger().severe("PAIRED_STORAGE_RECEIPT FAIL " + failure);
            Bukkit.shutdown();
        }
    }

    @Override
    public void onDisable() {
        if (started == null) {
            return;
        }
        try {
            JsonObject completion = started.deepCopy();
            completion.addProperty("status", "STOPPED");
            completion.addProperty("server_stopping", Bukkit.isStopping());
            write("shutdown.json", completion);
            getLogger().info("PAIRED_STORAGE_RECEIPT STOPPED phase=" + phase + " token=" + token);
        } catch (Exception failure) {
            getLogger().severe("PAIRED_STORAGE_RECEIPT FAIL " + failure);
        }
    }

    private Plugin enabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("Required fixture plugin is not enabled: " + name);
        }
        return plugin;
    }

    private void write(String name, JsonObject value) throws Exception {
        Files.createDirectories(getDataFolder().toPath());
        Files.writeString(getDataFolder().toPath().resolve(name), value + "\n");
    }
    /**
     * This native fixture loads ACTUAL Networks + IE2 plugin JARs and inspects
     * their registered items. Recreating the legacy Bukkit text setter path
     * from current templates is NOT the same as old-world addon item evidence.
     * Never mutate a plugin, world inventory, storage balance, or real player.
     */
    @SuppressWarnings("deprecation")
    private JsonObject verifyAddonRegisteredStyle() throws Exception {
        Class<?> update = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Method lookup = update.getDeclaredMethod("registeredTemplate", ItemStack.class);
        lookup.setAccessible(true);
        Class<?> repair = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method prepare = repair.getDeclaredMethod(
                "prepare", ItemStack.class, ItemStack.class, ItemStack.class);
        prepare.setAccessible(true);
        Method matchesOriginal = repair.getDeclaredMethod(
                "matchesSavedBackup", ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
        matchesOriginal.setAccessible(true);
        var parser = LegacyComponentSerializer.legacySection();
        JsonObject perAddon = new JsonObject();
        int totalCandidates = 0;
        int totalRegistered = 0;
        for (String expectedAddon : List.of("Networks", "InfinityExpansion2")) {
            int registered = 0;
            int matching = 0;
            int repairable = 0;
            int refused = 0;
            int unsupported = 0;
            int typedGuards = 0;
            for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
                if (!expectedAddon.equals(item.getAddon().getName())) {
                    continue;
                }
                registered++;
                ItemStack template = item.getItem();
                if (template == null || template.getType().isAir()) {
                    unsupported++;
                    continue;
                }
                byte[] reference = template.serializeAsBytes();
                ItemStack fresh = ItemStack.deserializeBytes(reference);
                var meta = fresh.getItemMeta();
                if (meta == null || meta.displayName() == null
                        || meta.getPersistentDataContainer().get(
                                new NamespacedKey("slimefun", "slimefun_item"),
                                PersistentDataType.STRING) == null) {
                    unsupported++;
                    assertUnchanged(template, reference, item.getId());
                    continue;
                }
                ItemStack old = ItemStack.deserializeBytes(reference);
                int count = Math.max(1, Math.min(31, old.getMaxStackSize()));
                old.setAmount(count);
                String rawName = parser.serialize(meta.displayName());
                var modernLore = meta.lore();
                List<String> legacyLore = modernLore == null
                        ? null : modernLore.stream().map(parser::serialize).toList();
                old.editMeta(oldMeta -> {
                    oldMeta.setDisplayName(rawName);
                    oldMeta.setLore(legacyLore);
                });
                byte[] oldBytes = old.serializeAsBytes();

                ItemStack resolved = (ItemStack) lookup.invoke(null, old);
                if (resolved == null) {
                    // Production refuses this source even if a lower-level
                    // shape-only repair method would accept two arbitrary items.
                    refused++;
                    assertUnchanged(template, reference, item.getId());
                    continue;
                }
                ItemStack replacement = (ItemStack) prepare.invoke(null, old, fresh, resolved);
                if (old.isSimilar(fresh)) {
                    require(replacement == null,
                            "Doctor tried to re-create already matching addon item " + item.getId());
                    matching++;
                } else if (replacement == null) {
                    // Meaningful text or functional addon metadata cannot be
                    // automatically normalized. Refusal is the safe result.
                    refused++;
                } else {
                    boolean rollbackSafe = (Boolean) matchesOriginal.invoke(
                            null, old, replacement, fresh, resolved);
                    require(replacement.getAmount() == count
                                    && replacement.isSimilar(fresh)
                                    && old.getType() == replacement.getType()
                                    && rollbackSafe,
                            "Doctor addon style replacement broke strict identity: " + item.getId());
                    repairable++;
                    if (typedGuards < 8) {
                        ItemStack modified = ItemStack.deserializeBytes(oldBytes);
                        modified.editMeta(modifiedMeta -> modifiedMeta.getPersistentDataContainer().set(
                                new NamespacedKey("sfl_addon_style_probe", "typed_owner"),
                                PersistentDataType.LONG, 777L));
                        ItemStack disallowed = (ItemStack) prepare.invoke(null, modified, fresh, resolved);
                        require(disallowed == null,
                                "Doctor accepted unauthorized typed addon owner data: " + item.getId());
                        typedGuards++;
                    }
                }
                require(Arrays.equals(oldBytes, old.serializeAsBytes()),
                        "Addon style audit mutated source item: " + item.getId());
                assertUnchanged(template, reference, item.getId());
            }
            require(registered > 0,
                    "Required installed addon did not register its real item IDs: " + expectedAddon);
            require(registered == matching + repairable + refused + unsupported,
                    "Lost an actual registered addon item in the style audit: " + expectedAddon);
            JsonObject summary = new JsonObject();
            summary.addProperty("registered", registered);
            summary.addProperty("already_matching", matching);
            summary.addProperty("strict_style_repairable", repairable);
            summary.addProperty("refused", refused);
            summary.addProperty("unsupported", unsupported);
            summary.addProperty("typed_pdc_guards", typedGuards);
            perAddon.add(expectedAddon, summary);
            totalCandidates += repairable;
            totalRegistered += registered;
        }
        JsonObject audit = new JsonObject();
        audit.addProperty("schema", 1);
        audit.addProperty("scope", "actual_loaded_published_addons_reconstructed_legacy_setters");
        audit.addProperty("genuine_old_saved_addon_items", "UNTESTED");
        audit.addProperty("registered", totalRegistered);
        audit.addProperty("strict_style_repairable", totalCandidates);
        audit.add("addons", perAddon);
        getLogger().info("PAIRED_ADDON_REGISTERED_STYLE_AUDIT_PASS plugins=2"
                + " registered=" + totalRegistered + " repairable=" + totalCandidates);
        return audit;
    }

    private static void assertUnchanged(ItemStack template, byte[] original, String itemId) {
        require(Arrays.equals(original, template.serializeAsBytes()),
                "Addon registry template was changed during style classification: " + itemId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }


}
