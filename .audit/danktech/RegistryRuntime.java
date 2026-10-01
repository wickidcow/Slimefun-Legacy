package io.github.wickidcow.regression;

import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.util.List;

public final class RegistryRuntime extends JavaPlugin {
    private static final long ID = 9007199254740993L;
    private static final int COUNT = Integer.MAX_VALUE - 7;
    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            String mode = System.getProperty("danktech.registryTest", "missing");
            try {
                var addon = Bukkit.getPluginManager().getPlugin("DankTech2");
                check(addon != null, "DankTech2 absent");
                if (mode.equals("refuse")) {
                    check(!addon.isEnabled(), "Unreadable registry must disable the addon");
                    check(SlimefunItem.getById("DK2_PACK_1") == null, "Items registered before load validation");
                    Class<?> manager = addon.getClass().getClassLoader().loadClass("io.github.sefiraat.danktech2.managers.ConfigManager");
                    check(manager.getMethod("getInstance").invoke(null) == null, "Incomplete manager was published");
                } else {
                    check(addon.isEnabled(), "Healthy addon did not enable");
                    if (mode.equals("control")) {
                        check(ConfigManager.getInstance().checkDankDeletion(42L), "Old loader must reproduce missing-pack classification");
                    } else if (mode.equals("seed")) {
                        seed();
                    } else if (mode.equals("verify")) {
                        verify();
                    } else {
                        throw new AssertionError("Unknown mode: " + mode);
                    }
                }
                getLogger().info("DANK_REGISTRY_" + mode.toUpperCase() + "_PASS");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "DANK_REGISTRY_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private void seed() throws Exception {
        var registered = SlimefunItem.getById("DK2_PACK_9");
        check(registered != null, "Original registered pack ID missing");
        ItemStack pack = registered.getItem().clone();
        var stored = new ItemStack(Material.DIAMOND, 37);
        var storedMeta = stored.getItemMeta();
        storedMeta.displayName(Component.text("Owner-written item name"));
        storedMeta.lore(List.of(Component.text("Keep the original custom lore")));
        storedMeta.getPersistentDataContainer().set(new NamespacedKey("olderaddon", "opaque_count"), PersistentDataType.LONG, Long.MAX_VALUE);
        stored.setItemMeta(storedMeta);
        var instance = new DankPackInstance(ID, 9);
        instance.setLastUser("Owner Before Upgrade");
        instance.setItem(0, stored);
        instance.setAmount(0, COUNT);
        var meta = pack.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, instance);
        meta.getPersistentDataContainer().set(new NamespacedKey("olderaddon", "charge"), PersistentDataType.FLOAT, 123.4567F);
        pack.setItemMeta(meta);
        Files.createDirectories(getDataFolder().toPath());
        Files.write(getDataFolder().toPath().resolve("expected-item.bin"), pack.serializeAsBytes());
        ConfigManager.getInstance().saveDankPack(pack);
        ConfigManager.getInstance().saveAll();
        check(!ConfigManager.getInstance().checkDankDeletion(ID), "Seed pack must be present");
    }

    private void verify() throws Exception {
        var packs = ConfigManager.getInstance().getAllPacks();
        check(packs.size() == 1, "Expected exactly the original pack");
        var actual = packs.getFirst();
        var expected = ItemStack.deserializeBytes(Files.readAllBytes(getDataFolder().toPath().resolve("expected-item.bin")));
        check(actual.equals(expected), "Pack amount, item identity or exact metadata changed");
        var instance = actual.getItemMeta().getPersistentDataContainer().get(Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
        check(instance != null && instance.getId() == ID && instance.getTier() == 9, "Pack identity/tier changed");
        check(instance.getAmount(0) == COUNT && instance.getItem(0).getAmount() == 37, "Stored counts changed");
        check(instance.getLastUser().equals("Owner Before Upgrade"), "Owner changed");
        check(!ConfigManager.getInstance().checkDankDeletion(ID), "Existing pack classified as deleted");
        check(ConfigManager.getInstance().checkDankDeletion(42L), "Genuine missing-pack rule changed");
        ConfigManager.getInstance().saveAll();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
