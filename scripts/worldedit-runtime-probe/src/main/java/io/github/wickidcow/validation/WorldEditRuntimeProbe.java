package io.github.wickidcow.validation;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable-server probe only. Never distributed in the addon bundle. */
public final class WorldEditRuntimeProbe extends JavaPlugin {
    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, this::verify, 60L);
    }

    private void verify() {
        try {
            int enabled = 0;
            for (String line : Files.readAllLines(Path.of("expected-addons.txt"))) {
                String name = line.split("\t")[1];
                var plugin = getServer().getPluginManager().getPlugin(name);
                check(plugin != null && plugin.isEnabled(), "Addon is not enabled: " + name);
                enabled++;
            }
            check(enabled == 45, "Expected all 45 maintained addons");
            var worldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
            check(worldEdit != null && worldEdit.isEnabled(), "WorldEdit is not enabled");
            check(SlimefunItem.getById("WESF_WAND") != null, "SFWorldEdit item registration missing");
            check(getServer().getCommandMap().getCommand("wesf") != null, "SFWorldEdit command missing");
            verifyNativeAdapter();
            verifySchematicAndSidecar();
            getDataFolder().mkdirs();
            Files.writeString(getDataFolder().toPath().resolve("result.txt"),
                "PASS\nAll 45 addons enabled\nWorldEdit native edit and undo\nSponge v3 round trip\nSFWorldEdit sidecar item/ID/PDC round trip\n");
            getLogger().info("WORLDEDIT_COMPAT_PROBE PASS addons=45 native-edit undo sponge-v3 sidecar-item-state");
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE, "WORLDEDIT_COMPAT_PROBE FAIL", failure);
        }
    }

    private void verifyNativeAdapter() throws Exception {
        var world = getServer().getWorlds().getFirst();
        var block = world.getBlockAt(96, 96, 96);
        var original = block.getBlockData().clone();
        var position = BlockVector3.at(96, 96, 96);
        try (var edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            edit.setBlock(position, Objects.requireNonNull(BlockTypes.DIAMOND_BLOCK).getDefaultState());
            edit.flushSession();
            check(block.getType() == Material.DIAMOND_BLOCK, "Native WorldEdit edit was not applied");
            try (var undo = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                edit.undo(undo);
                undo.flushSession();
            }
            check(block.getBlockData().equals(original), "Native WorldEdit undo did not restore the block");
        } finally {
            block.setBlockData(original, false);
        }
    }

    @SuppressWarnings("unchecked")
    private void verifySchematicAndSidecar() throws Exception {
        var origin = BlockVector3.at(0, 0, 0);
        var clipboard = new BlockArrayClipboard(new CuboidRegion(origin, BlockVector3.at(1, 1, 1)));
        clipboard.setBlock(origin, Objects.requireNonNull(BlockTypes.DIAMOND_BLOCK).getDefaultState());
        var bytes = new ByteArrayOutputStream();
        try (var writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(bytes)) {
            writer.write(clipboard);
        }
        try (var reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = reader.read();
            check(restored.getBlock(origin).getBlockType().equals(BlockTypes.DIAMOND_BLOCK), "Sponge v3 lost block data");
        }

        var item = new ItemStack(Material.COAL, 37);
        var meta = item.getItemMeta();
        Slimefun.getItemDataService().setItemData(meta, "COMPRESSED_CARBON");
        meta.displayName(Component.text("Existing item").hoverEvent(Component.text("Keep this hover")));
        meta.lore(List.of(Component.text("Existing custom lore")));
        var ownerKey = Objects.requireNonNull(NamespacedKey.fromString("legacyaddon:owner"));
        var chargeKey = Objects.requireNonNull(NamespacedKey.fromString("slimefun:item_charge"));
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, "11111111-2222-3333-4444-555555555555");
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.FLOAT, 123.4567F);
        item.setItemMeta(meta);

        var plugin = getServer().getPluginManager().getPlugin("WorldEditSlimefun");
        var loader = Objects.requireNonNull(plugin).getClass().getClassLoader();
        var manager = Class.forName("dev.j3fftw.worldeditslimefun.utils.SlimefunSchematicManager", true, loader);
        var record = Class.forName(manager.getName() + "$SfRecord", true, loader);
        var constructor = record.getDeclaredConstructor(int.class, int.class, int.class,
            String.class, boolean.class, Map.class, Map.class);
        constructor.setAccessible(true);
        Map<String, String> fields = Map.of("energy-charge", "1200", "owner", "unchanged-owner");
        var stored = constructor.newInstance(1, 2, -3, "ELECTRIC_FURNACE", false, fields, Map.of(8, item));
        var write = manager.getDeclaredMethod("writeSidecar", File.class, BlockVector3.class, List.class);
        var read = manager.getDeclaredMethod("readSidecar", File.class);
        write.setAccessible(true);
        read.setAccessible(true);
        getDataFolder().mkdirs();
        File file = new File(getDataFolder(), "existing-items.wesf.yml");
        write.invoke(null, file, origin, List.of(stored));
        byte[] before = Files.readAllBytes(file.toPath());
        var restored = (List<?>) read.invoke(null, file);
        check(restored.size() == 1, "Sidecar lost a machine");
        Object actual = restored.getFirst();
        check(value(record, actual, "sfId").equals("ELECTRIC_FURNACE"), "Machine ID changed");
        check(value(record, actual, "data").equals(fields), "Machine custom fields changed");
        var inventory = (Map<Integer, ItemStack>) value(record, actual, "inventory");
        check(inventory.keySet().equals(Set.of(8)), "Inventory slot changed");
        var loaded = inventory.get(8);
        check(loaded.getAmount() == 37 && loaded.getType() == Material.COAL, "Stack changed");
        check(loaded.getItemMeta().equals(item.getItemMeta()), "Sidecar metadata/PDC changed");
        check(SlimefunItem.getByItem(loaded) == SlimefunItem.getById("COMPRESSED_CARBON"), "Old item identity not recognized");
        check(Arrays.equals(before, Files.readAllBytes(file.toPath())), "Read rewrote the sidecar");
    }

    private static Object value(Class<?> type, Object record, String field) throws Exception {
        Method accessor = type.getDeclaredMethod(field);
        accessor.setAccessible(true);
        return accessor.invoke(record);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
