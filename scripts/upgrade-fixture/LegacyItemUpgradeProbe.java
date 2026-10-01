package io.github.wickidcow.validation;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;

/** Test-only plugin compiled on the real 1.20.6 API, never shipped in the addon bundle. */
public final class LegacyItemUpgradeProbe extends JavaPlugin {
    private static final Gson JSON = new Gson();
    private static final String SOURCE = "1.20.6";
    private static final String[] IDS = {"COMPRESSED_CARBON", "DURALUMIN_MULTI_TOOL", "SMALL_BACKPACK", "BOUND_BACKPACK", "OLD_UNINSTALLED_ADDON_ITEM"};
    private static final Material[] MATERIALS = {Material.COAL, Material.DIAMOND_PICKAXE, Material.CHEST, Material.CHEST, Material.IRON_INGOT};
    private static final int[] AMOUNTS = {37, 1, 1, 1, 53};
    private static final byte[] HEADER = {'S', 'F', '2', 0};
    private static final String OWNER = "11111111-2222-3333-4444-555555555555";
    private static final String BACKPACK = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !List.of("write", "read", "automatic").contains(args[0])) return false;
        if (args[0].equals("automatic")) {
            // Accessing the old chest may load its chunk for the first time. Let
            // the actual chunk-load listener finish on subsequent server ticks.
            Bukkit.getWorlds().get(0).getChunkAt(0, 0);
            Bukkit.getScheduler().runTaskLater(this, () -> executeProbe("automatic"), 20L);
        } else {
            executeProbe(args[0]);
        }
        return true;
    }

    private void executeProbe(String action) {
        try {
            Path directory = getDataFolder().toPath();
            Files.createDirectories(directory);
            Path proof = directory.resolve(action + "-success.json");
            Files.deleteIfExists(proof);
            int comparisons = action.equals("write") ? write(directory) : read(directory, action.equals("automatic"));
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", "PASS");
            report.put("source_minecraft", SOURCE);
            report.put("runtime_minecraft", Bukkit.getMinecraftVersion());
            report.put("server", Bukkit.getVersion());
            report.put("comparisons", comparisons);
            report.put("automatic_presentation", action.equals("automatic"));
            report.put("scope", "Actual 1.20.6-written synthetic Slimefun-shaped items, codec views, Doctor and world chest; not an entire historical addon installation");
            Files.writeString(proof, JSON.toJson(report));
            getLogger().info("LEGACY_UPGRADE_PROBE_PASS " + JSON.toJson(report));
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "LEGACY_UPGRADE_PROBE_FAIL", failure);
        }
    }

    @SuppressWarnings("deprecation") // Deliberately generate the historical representation using its actual old API.
    private int write(Path directory) throws Exception {
        require(SOURCE.equals(Bukkit.getMinecraftVersion()), "Fixture must be written on real Paper 1.20.6");
        require(Bukkit.getPluginManager().getPlugin("Slimefun") == null, "New core must not be installed on the historical writer");
        // A flag with no matching component is not necessarily persisted by the
        // historical server itself. Keep an explicit source-version control,
        // rather than treating that old writer behavior as an upgrade defect.
        ItemStack control = new ItemStack(Material.COAL);
        ItemMeta controlMeta = control.getItemMeta();
        controlMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        control.setItemMeta(controlMeta);
        ItemStack persistedControl = ItemStack.deserializeBytes(control.serializeAsBytes());
        require(control.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES), "Source flag control was not set");
        require(!persistedControl.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES),
                "Recheck source flag behavior before changing the fixture contract");
        Files.writeString(directory.resolve("source-flag-control.json"), JSON.toJson(Map.of(
                "source_minecraft", SOURCE, "ephemeral", snapshot(control), "persisted", snapshot(persistedControl))));
        var block = Bukkit.getWorlds().get(0).getBlockAt(8, 70, 8);
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chest.getBlockInventory().clear();
        for (int index = 0; index < IDS.length; index++) {
            ItemStack item = new ItemStack(MATERIALS[index], AMOUNTS[index]);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("\u65e7 item " + index, NamedTextColor.GREEN).decorate(TextDecoration.ITALIC));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("\u65e7 description", NamedTextColor.AQUA));
            if (index == 2) lore.add(Component.text("ID: " + OWNER + "#42", NamedTextColor.GRAY));
            else lore.add(Component.text("Retained existing state " + index));
            meta.lore(lore);
            meta.setCustomModelData(12345 + index);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            if (meta instanceof Damageable damage) damage.setDamage(127);
            if (index == 1) meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
            var data = meta.getPersistentDataContainer();
            data.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, IDS[index]);
            data.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
            data.set(key("slimefun:soulbound"), PersistentDataType.BYTE, (byte) 1);
            data.set(key("oldaddon:stored_count"), PersistentDataType.LONG, 9_000_000_001L);
            data.set(key("oldaddon:payload"), PersistentDataType.STRING, "  opaque \u03a9 \\ payload\nold data  ");
            data.set(key("oldaddon:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
            data.set(key("oldaddon:integers"), PersistentDataType.INTEGER_ARRAY, new int[] {0, -1, 42});
            if (index == 3) {
                data.set(key("slimefun:b_uuid"), PersistentDataType.STRING, BACKPACK);
                data.set(key("slimefun:owner_uuid"), PersistentDataType.STRING, OWNER);
            }
            item.setItemMeta(meta);
            Files.writeString(directory.resolve(index + ".expected.json"), JSON.toJson(snapshot(item)));
            byte[] raw = item.serializeAsBytes();
            require(JSON.toJson(snapshot(item)).equals(JSON.toJson(snapshot(ItemStack.deserializeBytes(raw)))),
                    "Historical native round trip failed before any upgrade at " + index);
            byte[] nativeRecord = Arrays.copyOf(HEADER, HEADER.length + raw.length);
            System.arraycopy(raw, 0, nativeRecord, HEADER.length, raw.length);
            Files.write(directory.resolve(index + ".sf2"), nativeRecord);
            Files.write(directory.resolve(index + ".base64"), Base64.getEncoder().encode(nativeRecord));
            try (var bytes = new ByteArrayOutputStream(); var stream = new BukkitObjectOutputStream(bytes)) {
                stream.writeObject(item);
                stream.flush();
                byte[] serialized = bytes.toByteArray();
                try (var input = new BukkitObjectInputStream(new ByteArrayInputStream(serialized))) {
                    ItemStack restored = (ItemStack) input.readObject();
                    require(JSON.toJson(snapshot(item)).equals(JSON.toJson(snapshot(restored))),
                            "Historical object-stream round trip failed before any upgrade at " + index);
                }
                Files.write(directory.resolve(index + ".legacy"), Base64.getEncoder().encode(serialized));
            }
            chest.getBlockInventory().setItem(index, item);
        }
        Bukkit.getWorlds().get(0).save();
        return IDS.length;
    }

    private int read(Path directory, boolean automatic) throws Exception {
        var oldProof = JSON.fromJson(Files.readString(directory.resolve("write-success.json")), Map.class);
        require(SOURCE.equals(oldProof.get("runtime_minecraft")), "Historical writer provenance is absent");
        require(!SOURCE.equals(Bukkit.getMinecraftVersion()), "Reader must run on the upgraded server");
        var slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        require(slimefun != null && slimefun.isEnabled(), "Candidate Slimefun did not enable");
        ClassLoader loader = slimefun.getClass().getClassLoader();
        Method decode = Class.forName("com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec", true, loader).getMethod("deserialize", byte[].class);
        Class<?> registry = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem", true, loader);
        Class<?> doctorType = Class.forName("io.github.thebusybiscuit.slimefun4.core.services.stability.ItemPresentationDoctor", true, loader);
        Class<?> reportType = Class.forName("io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport", true, loader);
        Object doctor = doctorType.getConstructor().newInstance();
        Method inspect = doctorType.getMethod("inspectItem", ItemStack.class, boolean.class, reportType);
        Method failures = reportType.getMethod("getFailures");
        Object cfg = slimefun.getClass().getMethod("getCfg").invoke(null);
        boolean configuredAutomatic = (boolean) cfg.getClass().getMethod("getBoolean", String.class)
                .invoke(cfg, "stability.item-doctor.repair-chunks-on-load");
        require(configuredAutomatic == automatic, "Unexpected automatic-Doctor fixture configuration");
        var block = Bukkit.getWorlds().get(0).getBlockAt(8, 70, 8);
        require(block.getState() instanceof Chest, "Actual old-world chest was lost");
        Chest chest = (Chest) block.getState();
        int count = 0;
        for (int index = 0; index < IDS.length; index++) {
            String storedExpected = Files.readString(directory.resolve(index + ".expected.json"));
            for (String representation : List.of("sf2", "base64", "legacy", "world")) {
                String expected = storedExpected;
                boolean automaticWorld = automatic && representation.equals("world");
                if (automaticWorld) {
                    // Compare automatic repair with the explicit real Doctor result from
                    // the unchanged historical bytes, not with an arbitrary relaxed name check.
                    ItemStack reference = (ItemStack) decode.invoke(null, (Object) Files.readAllBytes(directory.resolve(index + ".sf2")));
                    require(storedExpected.equals(JSON.toJson(snapshot(reference))), "Original automatic-control fixture changed");
                    Object referenceReport = reportType.getConstructor(boolean.class).newInstance(true);
                    inspect.invoke(doctor, reference, true, referenceReport);
                    require(((Number) failures.invoke(referenceReport)).longValue() == 0, "Automatic reference repair failed");
                    expected = JSON.toJson(snapshot(reference));
                }
                byte[] input = representation.equals("world") ? null : Files.readAllBytes(directory.resolve(index + "." + representation));
                ItemStack item = input == null ? chest.getBlockInventory().getItem(index) : (ItemStack) decode.invoke(null, (Object) input.clone());
                require(expected.equals(JSON.toJson(snapshot(item))), "Item mismatch at " + index + "/" + representation + ": expected=" + expected + ", actual=" + JSON.toJson(snapshot(item)));
                Object registered = registry.getMethod("getByItem", ItemStack.class).invoke(null, item);
                if (index < IDS.length - 1) {
                    require(registered != null, "Existing registered item no longer resolves: " + IDS[index]);
                    require(IDS[index].equals(registry.getMethod("getId").invoke(registered)), "Existing item ID changed");
                } else require(registered == null, "Unknown addon item must remain unresolved, not reassigned");
                Object scan = reportType.getConstructor(boolean.class).newInstance(false);
                inspect.invoke(doctor, item, false, scan);
                require(((Number) failures.invoke(scan)).longValue() == 0, "Doctor scan failed");
                require(expected.equals(JSON.toJson(snapshot(item))), "Read-only Doctor mutated an old item");
                ItemStack repaired = item.clone();
                Map<String, Object> before = snapshot(repaired);
                Object repair = reportType.getConstructor(boolean.class).newInstance(true);
                boolean changed = (boolean) inspect.invoke(doctor, repaired, true, repair);
                require(((Number) failures.invoke(repair)).longValue() == 0, "Doctor repair failed");
                if (index == 0 && !automaticWorld) require(changed, "Static old translated item was not repaired");
                if (automaticWorld) require(!changed, "Automatic Doctor result was not idempotent");
                Map<String, Object> after = snapshot(repaired);
                before.remove("name"); before.remove("lore");
                after.remove("name"); after.remove("lore");
                require(JSON.toJson(before).equals(JSON.toJson(after)), "Doctor changed protected old item state at " + index);
                if (input != null) require(Arrays.equals(input, Files.readAllBytes(directory.resolve(index + "." + representation))), "Stored fixture was rewritten");
                count++;
            }
        }
        require(chest.getBlockInventory().getItem(5) == null, "Empty old slot gained an item");
        Bukkit.getWorlds().get(0).save();
        return count;
    }

    @SuppressWarnings("deprecation") // Read the same old integer model/enchantment contract on both server generations.
    private static Map<String, Object> snapshot(ItemStack item) {
        require(item != null, "Expected a retained item, not an empty slot");
        Map<String, Object> state = new LinkedHashMap<>();
        ItemMeta meta = item.getItemMeta();
        state.put("type", item.getType().name());
        state.put("amount", item.getAmount());
        state.put("name", meta.displayName() == null ? null : GsonComponentSerializer.gson().serialize(meta.displayName()));
        state.put("lore", meta.lore() == null ? null : meta.lore().stream().map(c -> GsonComponentSerializer.gson().serialize(c)).toList());
        state.put("damage", meta instanceof Damageable damage ? damage.getDamage() : 0);
        state.put("unbreakable", meta.isUnbreakable());
        state.put("model", meta.hasCustomModelData() ? meta.getCustomModelData() : null);
        state.put("flags", meta.getItemFlags().stream().map(Enum::name).sorted().toList());
        Map<String, Integer> enchantments = new TreeMap<>();
        meta.getEnchants().forEach((enchantment, level) -> enchantments.put(enchantment.getKey().toString(), level));
        state.put("enchantments", enchantments);
        Map<String, Object> values = new TreeMap<>();
        var data = meta.getPersistentDataContainer();
        for (NamespacedKey key : data.getKeys()) {
            String name = key.toString();
            if (data.has(key, PersistentDataType.STRING)) values.put(name, "STRING:" + data.get(key, PersistentDataType.STRING));
            else if (data.has(key, PersistentDataType.FLOAT)) values.put(name, "FLOAT_BITS:" + Float.floatToIntBits(data.get(key, PersistentDataType.FLOAT)));
            else if (data.has(key, PersistentDataType.LONG)) values.put(name, "LONG:" + data.get(key, PersistentDataType.LONG));
            else if (data.has(key, PersistentDataType.BYTE)) values.put(name, "BYTE:" + data.get(key, PersistentDataType.BYTE));
            else if (data.has(key, PersistentDataType.BYTE_ARRAY)) values.put(name, "BYTE_ARRAY:" + Arrays.toString(data.get(key, PersistentDataType.BYTE_ARRAY)));
            else if (data.has(key, PersistentDataType.INTEGER_ARRAY)) values.put(name, "INTEGER_ARRAY:" + Arrays.toString(data.get(key, PersistentDataType.INTEGER_ARRAY)));
            else throw new IllegalStateException("Unexpected persistent data type at " + name);
        }
        state.put("persistent_data", values);
        return state;
    }

    private static NamespacedKey key(String value) { return Objects.requireNonNull(NamespacedKey.fromString(value)); }
    private static void require(boolean condition, String description) { if (!condition) throw new IllegalStateException(description); }
}
