package com.wickidcow.doctorfixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Source items are minted by the ACTUAL published v4.1.55 plugin and then
 * observed, without writes, on v4.1.81 in a second JVM with the saved world.
 * This plugin has no compile-time dependency on any Slimefun API.
 */
public final class HistoricalCoreProbe extends JavaPlugin {
    private static final String[] IDS = {
            "CARBONADO", "CARBON_CHUNK", "RAW_CARBONADO", "STEEL_INGOT"
    };
    private static final int[] AMOUNTS = {63, 8, 7, 12};

    @Override
    public void onEnable() {
        PluginCommand command = getCommand("sflhistoricprobe");
        if (command == null) {
            throw new IllegalStateException("Historical handoff command is missing");
        }
        command.setExecutor(this::command);
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !(args[0].equals("seed") || args[0].equals("verify"))) {
            sender.sendMessage("sflhistoricprobe seed|verify");
            return true;
        }
        String phase = args[0];
        try {
            if (phase.equals("seed")) {
                seed();
            } else {
                verify();
            }
            getLogger().info("SFL_HISTORICAL_CORE_" + phase.toUpperCase() + "_PASS");
        } catch (Exception | LinkageError error) {
            try {
                Files.writeString(folder().resolve(phase + ".fail"),
                        error.getClass().getSimpleName() + ": " + error.getMessage());
            } catch (IOException ignored) {
                // The Python native harness still fails on a missing PASS marker.
            }
            getLogger().severe("SFL_HISTORICAL_CORE_FAIL " + phase + ": " + error);
        }
        return true;
    }

    private Path folder() throws IOException {
        Path path = getDataFolder().toPath();
        Files.createDirectories(path);
        return path;
    }

    private static void require(boolean valid, String issue) throws IOException {
        if (!valid) {
            throw new IOException(issue);
        }
    }

    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String version() throws IOException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Slimefun");
        require(plugin != null && plugin.isEnabled(), "Published Slimefun was not enabled");
        return plugin.getDescription().getVersion();
    }

    private static ItemStack fresh(String id) throws Exception {
        Class<?> cls = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
        Object entry = cls.getMethod("getById", String.class).invoke(null, id);
        if (entry == null) {
            return null;
        }
        Object item = entry.getClass().getMethod("getItem").invoke(entry);
        require(item instanceof ItemStack, "Non-Bukkit registered item: " + id);
        ItemStack result = (ItemStack) item;
        byte[] bytes = result.serializeAsBytes();
        require(bytes.length > 0 && bytes.length <= 256 * 1024
                        && Arrays.equals(bytes, ItemStack.deserializeBytes(bytes).serializeAsBytes()),
                "Unstable native registered item: " + id);
        return ItemStack.deserializeBytes(bytes);
    }

    private static Inventory chest(boolean first) throws IOException {
        var block = Bukkit.getWorlds().getFirst().getBlockAt(8, 180, 8);
        if (first) {
            require(block.getType().isAir(), "Historical test-only chest site is occupied");
            block.setType(Material.CHEST);
        }
        require(block.getType() == Material.CHEST && block.getState() instanceof Chest,
                "Independent JVM did not reopen test-only physical chest");
        Inventory inv = ((Chest) block.getState()).getBlockInventory();
        require(inv.getSize() == 27, "Historical handoff requires single chest");
        return inv;
    }

    private void seed() throws Exception {
        require(version().contains("4.1.55"), "First JVM did not load published v4.1.55");
        Path data = folder();
        require(!Files.exists(data.resolve("original.tsv")), "Original receipt already exists");
        Inventory inv = chest(true);
        StringBuilder receipt = new StringBuilder("v4.1.55\n");
        int count = 0;
        for (int i = 0; i < IDS.length; i++) {
            ItemStack old = fresh(IDS[i]);
            if (old == null) {
                require(i > 1, "Missing mandatory historical registered ID: " + IDS[i]);
                receipt.append(IDS[i]).append("\tABSENT\n");
                continue;
            }
            count++;
            old.setAmount(AMOUNTS[i]);
            byte[] bytes = old.serializeAsBytes();
            inv.setItem(i, old);
            require(inv.getItem(i) != null
                            && Arrays.equals(bytes, inv.getItem(i).serializeAsBytes()),
                    "Old registered stack failed to seed physical chest");
            Files.write(data.resolve(IDS[i] + ".bin"), bytes);
            receipt.append(IDS[i]).append("\t").append(i).append("\t")
                    .append(AMOUNTS[i]).append("\t").append(hash(bytes)).append("\n");
        }
        require(count >= 2, "Insufficient published historical source items");
        inv.setItem(26, new ItemStack(Material.STONE, 17));
        Files.writeString(data.resolve("original.tsv"), receipt.toString());
        Files.writeString(data.resolve("seed.pass"), "PASS published v4.1.55 original world chest\n");
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plainLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null ? List.of() : lore.stream().map(HistoricalCoreProbe::plain).toList();
    }

    private static boolean customModelSame(ItemMeta oldMeta, ItemMeta newMeta) {
        return oldMeta.hasCustomModelData() == newMeta.hasCustomModelData()
                && (!oldMeta.hasCustomModelData()
                        || oldMeta.getCustomModelData() == newMeta.getCustomModelData());
    }

    private void verify() throws Exception {
        require(version().contains("4.1.81"), "Second JVM did not load published v4.1.81");
        Path data = folder();
        String[] lines = Files.readString(data.resolve("original.tsv")).trim().split("\n");
        require(lines.length == IDS.length + 1 && lines[0].equals("v4.1.55"),
                "Historical source manifest missing or changed");
        Inventory inv = chest(false);
        require(inv.getItem(26) != null && inv.getItem(26).getType() == Material.STONE
                        && inv.getItem(26).getAmount() == 17,
                "Vanilla world stock changed between genuine released cores");
        int count = 0;
        StringBuilder report = new StringBuilder(
                "id\told_sha256\tfresh_sha256\tis_similar\tmaterial_same\tquantity"
                        + "\tname_component_same\tlore_component_same\tplain_name_same"
                        + "\tplain_lore_same\tlegacy_name_same\tlegacy_lore_same"
                        + "\tpdc_key_set_same\tcustom_model_data_same\titem_model_same"
                        + "\tenchantments_same\told_lore_lines\tnew_lore_lines\n");
        for (int i = 0; i < IDS.length; i++) {
            String id = IDS[i];
            if (lines[i + 1].equals(id + "\tABSENT")) {
                require(inv.getItem(i) == null, "Missing v55 item was created unexpectedly");
                continue;
            }
            String[] fields = lines[i + 1].split("\t", -1);
            require(fields.length == 4 && fields[0].equals(id)
                            && Integer.parseInt(fields[1]) == i
                            && Integer.parseInt(fields[2]) == AMOUNTS[i],
                    "Invalid historical item manifest row");
            byte[] original = Files.readAllBytes(data.resolve(id + ".bin"));
            require(hash(original).equals(fields[3]), "Old native receipt digest changed");
            ItemStack stored = inv.getItem(i);
            require(stored != null && stored.getAmount() == AMOUNTS[i]
                            && Arrays.equals(original, stored.serializeAsBytes()),
                    "Real historical world stack changed across core upgrade");
            ItemStack current = fresh(id);
            require(current != null, "New version lost historical registered ID: " + id);
            count++;
            ItemMeta oldMeta = stored.getItemMeta();
            ItemMeta newMeta = current.getItemMeta();
            require(oldMeta != null && newMeta != null, "Missing old or current item metadata");
            List<Component> oldLore = oldMeta.lore();
            List<Component> freshLore = newMeta.lore();
            report.append(id).append("\t").append(hash(original)).append("\t")
                    .append(hash(current.serializeAsBytes())).append("\t")
                    .append(stored.isSimilar(current)).append("\t")
                    .append(stored.getType() == current.getType()).append("\t")
                    .append(AMOUNTS[i]).append("\t")
                    .append(Objects.equals(oldMeta.displayName(), newMeta.displayName())).append("\t")
                    .append(Objects.equals(oldLore, freshLore)).append("\t")
                    .append(plain(oldMeta.displayName()).equals(plain(newMeta.displayName()))).append("\t")
                    .append(plainLore(oldMeta).equals(plainLore(newMeta))).append("\t")
                    .append(Objects.equals(oldMeta.getDisplayName(), newMeta.getDisplayName())).append("\t")
                    .append(Objects.equals(oldMeta.getLore(), newMeta.getLore())).append("\t")
                    .append(oldMeta.getPersistentDataContainer().getKeys()
                            .equals(newMeta.getPersistentDataContainer().getKeys())).append("\t")
                    .append(customModelSame(oldMeta, newMeta)).append("\t")
                    .append(Objects.equals(oldMeta.getItemModel(), newMeta.getItemModel())).append("\t")
                    .append(oldMeta.getEnchants().equals(newMeta.getEnchants())).append("\t")
                    .append(oldLore == null ? -1 : oldLore.size()).append("\t")
                    .append(freshLore == null ? -1 : freshLore.size()).append("\n");
        }
        require(count >= 2, "Not enough source-to-current item pairs");
        for (int i = IDS.length; i < 26; i++) {
            require(inv.getItem(i) == null, "Unrelated empty physical chest slot changed");
        }
        Files.writeString(data.resolve("comparison.tsv"), report.toString());
        Files.writeString(data.resolve("verify.pass"), "PASS published old/new JVM native world conservation\n");
    }
}
