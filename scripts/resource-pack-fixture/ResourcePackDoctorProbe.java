package io.github.wickidcow.validation;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Synthetic fixture on real Paper; never install on a production server. */
public final class ResourcePackDoctorProbe extends JavaPlugin {
    private static final String OWNER = "11111111-2222-3333-4444-555555555555";
    private static final String BACKPACK = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String UNIVERSAL = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff";
    private static final String LEGACY_UNIVERSAL = "cccccccc-dddd-eeee-ffff-111111111111";
    private static final float MODEL = 2200080F;

    @Override
    public void onEnable() {
        // Empty Paper servers need not retain spawn chunks. Keep only the near fixture
        // loaded before Doctor resumes; the far fixture stays unloaded until requested.
        Bukkit.getWorlds().getFirst().getChunkAt(0, 0).addPluginChunkTicket(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            try {
                                Files.createDirectories(getDataFolder().toPath());
                                switch (args[0]) {
                                    case "prepare" -> prepare();
                                    case "preview" -> verifyPreview();
                                    case "checkpoint" -> verifyCheckpoint();
                                    case "installed" -> verifyInstalled();
                                    case "uninstalled" -> verifyUninstalled();
                                    case "restarted" -> {
                                        idle();
                                        verifyContents();
                                        require(
                                                !CuriositiesConfig.getConfig().getBoolean("resource-pack.enabled"),
                                                "Restart re-enabled Legacy delivery");
                                        require(
                                                "external"
                                                        .equals(CuriositiesConfig.getConfig()
                                                                .getString("resource-pack.ownership-mode")),
                                                "Restart replaced the external pack owner");
                                    }
                                    case "deferred" -> {
                                        var world = Bukkit.getWorlds().getFirst();
                                        require(
                                                !world.isChunkLoaded(64, 64),
                                                "Deferred vanilla fixture must start unloaded");
                                        world.getChunkAt(64, 64);
                                        Bukkit.getScheduler()
                                                .runTaskLater(
                                                        this,
                                                        () -> {
                                                            try {
                                                                require(
                                                                        expected("clean-0")
                                                                                .equals(chest(1024, 1024)
                                                                                        .getBlockInventory()
                                                                                        .getItem(0)),
                                                                        "Chunk-load cleanup did not repair the deferred chest");
                                                                pass("deferred");
                                                            } catch (Throwable failure) {
                                                                fail(failure);
                                                            }
                                                        },
                                                        20L);
                                        return;
                                    }
                                    default -> throw new IllegalArgumentException("Unknown probe action");
                                }
                                pass(args[0]);
                            } catch (Throwable failure) {
                                fail(failure);
                            }
                        },
                        20L);
        return true;
    }

    private void prepare() throws Exception {
        require(!Files.exists(file("clean-0")), "Refusing to replace a previous probe fixture");
        var world = Bukkit.getWorlds().getFirst();
        world.setSpawnLocation(0, 80, 0);
        ItemStack clean = item("STEEL_INGOT");
        ItemStack old = modeled(clean, List.of(MODEL), false);
        var near = chest(8, 8).getBlockInventory();
        near.clear();
        near.setItem(0, old);
        save("clean-0", clean);
        save("old-0", old);
        ItemStack external = modeled(clean, List.of(99123F), false);
        near.setItem(1, external);
        save("clean-1", external);
        ItemStack mixed = modeled(clean, List.of(MODEL, 99123F), true);
        near.setItem(2, mixed);
        save("clean-2", modeled(clean, List.of(99123F), true));
        near.setItem(3, bundle(old));
        save("clean-3", bundle(clean));
        near.setItem(4, shulker(old));
        save("clean-4", shulker(clean));
        ItemStack unknown = modeled(item("UNAVAILABLE_ADDON_ID"), List.of(MODEL), false);
        near.setItem(5, unknown);
        save("clean-5", unknown);
        ItemStack guide = new ItemStack(Material.ENCHANTED_BOOK);
        var guideMeta = guide.getItemMeta();
        guideMeta
                .getPersistentDataContainer()
                .set(key("slimefun:slimefun_guide_mode"), PersistentDataType.STRING, "SURVIVAL_MODE");
        guide.setItemMeta(guideMeta);
        near.setItem(6, modeled(guide, List.of(2200001F), false));
        save("clean-6", guide);
        Item dropped = world.dropItem(world.getSpawnLocation().clone().add(2, 1, 2), old.clone());
        dropped.setGravity(false);
        dropped.setUnlimitedLifetime(true);
        Files.writeString(
                getDataFolder().toPath().resolve("drop.uuid"),
                dropped.getUniqueId().toString());
        chest(1024, 1024).getBlockInventory().setItem(0, old.clone());
        world.save();
        // Paper may retain a newly generated chunk's ticket until a later tick. The clean
        // restart below is the unload boundary; the deferred probe verifies it is unloaded.
        world.unloadChunkRequest(64, 64);

        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        RecordSet blockRecord = new RecordSet();
        blockRecord.put(FieldKey.LOCATION, machineOwner());
        blockRecord.put(FieldKey.CHUNK, world.getName() + ";128;128");
        blockRecord.put(FieldKey.SLIMEFUN_ID, "ELECTRIC_FURNACE");
        setData(blocks, new RecordKey(DataScope.BLOCK_RECORD), blockRecord);
        write(blocks, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, machineOwner(), old, true);
        write(blocks, DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, UNIVERSAL, old, false);
        // Retained Bukkit object-stream envelope, distinct from today's SF2 writer.
        RecordSet legacy = new RecordSet();
        legacy.put(FieldKey.UNIVERSAL_UUID, LEGACY_UNIVERSAL);
        legacy.put(FieldKey.INVENTORY_SLOT, "0");
        byte[] oldEnvelope = legacyBytes(old);
        Files.write(getDataFolder().toPath().resolve("legacy-envelope.bin"), oldEnvelope);
        legacy.put(FieldKey.INVENTORY_ITEM, oldEnvelope);
        setData(blocks, new RecordKey(DataScope.UNIVERSAL_INVENTORY), legacy);
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        RecordSet playerRecord = new RecordSet();
        playerRecord.put(FieldKey.PLAYER_UUID, OWNER);
        playerRecord.put(FieldKey.PLAYER_NAME, "ResourcePackDoctorProbe");
        playerRecord.put(FieldKey.PLAYER_BACKPACK_NUM, "1");
        setData(profiles, new RecordKey(DataScope.PLAYER_PROFILE), playerRecord);
        RecordSet profile = new RecordSet();
        profile.put(FieldKey.BACKPACK_ID, BACKPACK);
        profile.put(FieldKey.PLAYER_UUID, OWNER);
        profile.put(FieldKey.BACKPACK_SIZE, "9");
        profile.put(FieldKey.BACKPACK_NUMBER, "1");
        profile.put(FieldKey.BACKPACK_NAME, "RXhpc3RpbmcgYmFja3BhY2s=");
        setData(profiles, new RecordKey(DataScope.BACKPACK_PROFILE), profile);
        write(profiles, DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, BACKPACK, old, false);
    }

    private void verifyPreview() throws Exception {
        idle();
        require(Slimefun.getItemTextureService().getModelData("STEEL_INGOT") == (int) MODEL, "Scan changed mappings");
        require(expected("old-0").equals(chest(8, 8).getBlockInventory().getItem(0)), "Scan changed a stored stack");
        require(
                !Files.exists(Slimefun.instance().getDataFolder().toPath().resolve("resource-pack-doctor.yml")),
                "Scan saved authorization");
    }

    private void verifyCheckpoint() throws Exception {
        idle();
        require(
                Slimefun.getItemDoctorService().getResourcePackDoctor().status().contains("awaiting-restart"),
                "Missing restart checkpoint");
        require(Slimefun.getItemTextureService().getModelData("STEEL_INGOT") == 0, "Mappings were not reset");
        require(
                expected("old-0").equals(chest(8, 8).getBlockInventory().getItem(0)),
                "Items changed before template restart");
        require(!CuriositiesConfig.getConfig().getBoolean("resource-pack.enabled"), "Sender enabled before restart");
    }

    private void verifyInstalled() throws Exception {
        idle();
        require(
                Slimefun.getItemDoctorService().getResourcePackDoctor().status().contains("active"),
                "Cleanup is not active");
        require(
                CuriositiesConfig.getConfig().getBoolean("resource-pack.enabled"),
                "Install did not enable Legacy delivery");
        require(
                "legacy".equals(CuriositiesConfig.getConfig().getString("resource-pack.ownership-mode")),
                "Install did not retain the chosen delivery owner");
        require(
                io.github.thebusybiscuit.slimefun4.core.services.stability.ResourcePackDoctorService.ID_PACK_URL.equals(
                        CuriositiesConfig.getConfig().getString("resource-pack.url")),
                "Install did not pin the ID pack URL");
        require(
                io.github.thebusybiscuit.slimefun4.core.services.stability.ResourcePackDoctorService.ID_PACK_SHA1
                        .equals(CuriositiesConfig.getConfig().getString("resource-pack.sha1")),
                "Install did not pin the matching pack hash");
        verifyContents();
        var backup = Slimefun.getItemDoctorService().getResourcePackDoctor().backupPath();
        require(
                backup != null && Files.isRegularFile(backup.resolve("item-models-current.yml")),
                "Config backup missing");
        boolean oldStackBackedUp = false;
        for (String line : Files.readAllLines(backup.resolve("items.tsv"))) {
            String[] fields = line.split("\t");
            ItemStack original =
                    DataUtils.deserializeItemStack(Base64.getDecoder().decode(fields[2]));
            require(original != null, "Unreadable item journal entry");
            oldStackBackedUp |= expected("old-0").equals(original);
        }
        require(oldStackBackedUp, "Original native model component missing from before-image journal");
        List<String> rows = Files.readAllLines(backup.resolve("rows.tsv"));
        require(rows.size() >= 3, "Unloaded database before-images missing");
        String legacyPayload = Base64.getEncoder()
                .encodeToString(Files.readAllBytes(getDataFolder().toPath().resolve("legacy-envelope.bin")));
        require(
                rows.stream().anyMatch(line -> line.endsWith("\tB\t" + legacyPayload)),
                "Original legacy object-stream envelope was not backed up byte-for-byte");
        require(Slimefun.getItemDoctorService().getLastReport().getFailures() == 0, "Doctor reported failures");
    }

    private void verifyContents() throws Exception {
        for (int slot = 0; slot < 7; slot++) {
            require(
                    expected("clean-" + slot)
                            .equals(chest(8, 8).getBlockInventory().getItem(slot)),
                    "Chest metadata mismatch at slot " + slot);
        }
        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        require(
                expected("clean-0").equals(read(blocks, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, machineOwner())),
                "Unloaded machine row mismatch");
        require(
                expected("clean-0")
                        .equals(read(blocks, DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, UNIVERSAL)),
                "Universal row mismatch");
        require(
                expected("clean-0")
                        .equals(read(blocks, DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, LEGACY_UNIVERSAL)),
                "Historical object-stream row mismatch");
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        require(
                expected("clean-0")
                        .equals(read(profiles, DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, BACKPACK)),
                "Backpack row mismatch");
        UUID drop = UUID.fromString(Files.readString(getDataFolder().toPath().resolve("drop.uuid")));
        require(
                Bukkit.getEntity(drop) instanceof Item dropped
                        && expected("clean-0").equals(dropped.getItemStack()),
                "Dropped stack mismatch");
    }

    private void verifyUninstalled() throws Exception {
        idle();
        verifyContents();
        require(
                !CuriositiesConfig.getConfig().getBoolean("resource-pack.enabled"),
                "Uninstall left the sender enabled");
        require(
                expected("clean-0").equals(chest(8, 8).getBlockInventory().getItem(0)),
                "Uninstall changed clean item data");
        require(
                Slimefun.getItemDoctorService().getLastReport().getItemModelRepairs() == 0,
                "Repeated cleanup was not idempotent");
    }

    private void idle() {
        require(!Slimefun.getItemDoctorService().getResourcePackDoctor().isSweepActive(), "Doctor pass still running");
    }

    private String machineOwner() {
        return Bukkit.getWorlds().getFirst().getName() + ";2048;80;2048";
    }

    private void write(
            com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController controller,
            DataScope scope,
            FieldKey ownerField,
            String owner,
            ItemStack item,
            boolean text)
            throws Exception {
        RecordSet row = new RecordSet();
        row.put(ownerField, owner);
        row.put(FieldKey.INVENTORY_SLOT, "0");
        if (text) row.put(FieldKey.INVENTORY_ITEM, DataUtils.serializeItemStack(item));
        else row.put(FieldKey.INVENTORY_ITEM, DataUtils.serializeItemStackBytesForStorage(item));
        setData(controller, new RecordKey(scope), row);
    }

    private ItemStack read(
            com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController controller,
            DataScope scope,
            FieldKey ownerField,
            String owner)
            throws Exception {
        RecordKey key = new RecordKey(scope);
        key.addCondition(ownerField, owner);
        key.addField(FieldKey.INVENTORY_ITEM);
        var method = com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController.class.getDeclaredMethod(
                "getData", RecordKey.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rows = (List<RecordSet>) method.invoke(controller, key);
        require(rows.size() == 1, "Expected one inventory fixture row");
        return rows.getFirst().getItemStack(FieldKey.INVENTORY_ITEM);
    }

    private void setData(
            com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController controller,
            RecordKey key,
            RecordSet row)
            throws Exception {
        var method = com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController.class.getDeclaredMethod(
                "setData", RecordKey.class, RecordSet.class);
        method.setAccessible(true);
        method.invoke(controller, key, row);
    }

    private Chest chest(int x, int z) {
        var block = Bukkit.getWorlds().getFirst().getBlockAt(x, 80, z);
        if (block.getType() != Material.CHEST) block.setType(Material.CHEST);
        return (Chest) block.getState();
    }

    private ItemStack item(String id) {
        ItemStack item = new ItemStack(Material.IRON_INGOT, 32);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing item"));
        meta.lore(List.of(Component.text("Original lore")));
        var data = meta.getPersistentDataContainer();
        data.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, id);
        data.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        data.set(key("slimefun:b_uuid"), PersistentDataType.STRING, BACKPACK);
        data.set(key("slimefun:owner_uuid"), PersistentDataType.STRING, OWNER);
        data.set(key("itemsadder:opaque"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack modeled(ItemStack original, List<Float> floats, boolean external) {
        ItemStack item = original.clone();
        var meta = item.getItemMeta();
        var component = meta.getCustomModelDataComponent();
        component.setFloats(floats);
        if (external) {
            component.setFlags(List.of(true, false));
            component.setStrings(List.of("itemsadder:custom"));
            component.setColors(List.of(Color.RED));
        }
        meta.setCustomModelDataComponent(component);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack bundle(ItemStack child) {
        ItemStack item = new ItemStack(Material.BUNDLE);
        var meta = (BundleMeta) item.getItemMeta();
        meta.setItems(List.of(child.clone()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack shulker(ItemStack child) {
        ItemStack item = new ItemStack(Material.SHULKER_BOX);
        var meta = (BlockStateMeta) item.getItemMeta();
        Container state = (Container) meta.getBlockState();
        state.getInventory().setItem(0, child.clone());
        meta.setBlockState(state);
        item.setItemMeta(meta);
        return item;
    }

    private Path file(String name) {
        return getDataFolder().toPath().resolve(name + ".bin");
    }

    private void save(String name, ItemStack item) throws Exception {
        Files.write(file(name), item.serializeAsBytes());
    }

    private byte[] legacyBytes(ItemStack item) throws Exception {
        try (var bytes = new ByteArrayOutputStream();
                var output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encode(bytes.toByteArray());
        }
    }

    private ItemStack expected(String name) throws Exception {
        return ItemStack.deserializeBytes(Files.readAllBytes(file(name)));
    }

    private static NamespacedKey key(String key) {
        return NamespacedKey.fromString(key);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private void pass(String phase) throws Exception {
        String proof = "RESOURCE_PACK_DOCTOR_PROBE_PASS " + phase + " | " + Bukkit.getVersion();
        Files.writeString(getDataFolder().toPath().resolve(phase + ".pass"), proof + '\n');
        getLogger().info(proof);
    }

    private void fail(Throwable failure) {
        getLogger().log(java.util.logging.Level.SEVERE, "RESOURCE_PACK_DOCTOR_PROBE_FAIL", failure);
    }
}
