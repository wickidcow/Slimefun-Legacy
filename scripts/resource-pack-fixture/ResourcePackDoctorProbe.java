package io.github.wickidcow.validation;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.attributes.UniversalDataTrait;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
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

/** Synthetic fixture on real Paper/Folia; never install on a production server. */
public final class ResourcePackDoctorProbe extends JavaPlugin {
    private static final String OWNER = "11111111-2222-3333-4444-555555555555";
    private static final String BACKPACK = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String UNIVERSAL = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff";
    private static final String LEGACY_UNIVERSAL = "cccccccc-dddd-eeee-ffff-111111111111";
    private static final String HELD_BACKPACK = "dddddddd-eeee-ffff-1111-222222222222";
    private static final String VIRTUAL_MENU = "eeeeeeee-ffff-1111-2222-333333333333";
    private static final float MODEL = 2200080F;
    private static final int FAR_CHUNK = 512;
    private static final int FAR_BLOCK = FAR_CHUNK << 4;
    private PlayerBackpack heldBackpack;

    @Override
    public void onEnable() {
        // Empty Paper servers need not retain spawn chunks. Keep only the near fixture
        // loaded before Doctor resumes; the far fixture stays unloaded until requested.
        at(0, 0, 1L, () -> Bukkit.getWorlds().getFirst().getChunkAt(0, 0).addPluginChunkTicket(this));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        at(0, 0, 20L, () -> {
            try {
                Files.createDirectories(getDataFolder().toPath());
                switch (args[0]) {
                    case "prepare" -> {
                        prepare();
                        at(FAR_CHUNK, FAR_CHUNK, 1L, () -> {
                            chest(FAR_BLOCK, FAR_BLOCK).getBlockInventory().setItem(0, expected("old-0"));
                            pass("prepare");
                        });
                        return;
                    }
                    case "preview" -> verifyPreview();
                    case "checkpoint" -> verifyCheckpoint();
                    case "installed" -> verifyInstalled();
                    case "menus-prepare" -> {
                        prepareMachine(10, 8);
                        prepareVirtualMenu();
                        at(FAR_CHUNK, FAR_CHUNK, 1L, () -> {
                            prepareMachine(FAR_BLOCK + 2, FAR_BLOCK);
                            pass("menus-prepare");
                        });
                        return;
                    }
                    case "menus-verified" -> {
                        idle();
                        verifyMachine(10, 8);
                        verifyVirtualMenu();
                        require(
                                Slimefun.getItemDoctorService().getLastReport().getFailures() == virtualDeferrals(),
                                "Unexpected failure count in loaded-menu cleanup");
                        at(FAR_CHUNK, FAR_CHUNK, 1L, () -> {
                            verifyMachine(FAR_BLOCK + 2, FAR_BLOCK);
                            pass("menus-verified");
                        });
                        return;
                    }
                    case "ownership-prepare" -> {
                        prepareOwnedBackpack();
                        return;
                    }
                    case "ownership-deferred" -> verifyOwnedBackpackDeferred();
                    case "uninstalled" -> verifyUninstalled();
                    case "restarted" -> {
                        idle();
                        verifyContents();
                        if (Files.exists(getDataFolder().toPath().resolve("ownership-prepare.pass"))) {
                            require(
                                    expected("clean-0")
                                            .equals(read(
                                                    Slimefun.getDatabaseManager()
                                                            .getProfileDataController(),
                                                    DataScope.BACKPACK_INVENTORY,
                                                    FieldKey.BACKPACK_ID,
                                                    HELD_BACKPACK)),
                                    "Restart did not safely repair the previously gameplay-owned backpack");
                            require(
                                    Slimefun.getItemDoctorService()
                                                    .getLastReport()
                                                    .getFailures()
                                            == virtualDeferrals(),
                                    "Restart still reports a deferred backpack");
                        }
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
                                !world.isChunkLoaded(FAR_CHUNK, FAR_CHUNK),
                                "Deferred vanilla fixture must start unloaded");
                        at(FAR_CHUNK, FAR_CHUNK, 1L, () -> {
                            world.getChunkAt(FAR_CHUNK, FAR_CHUNK).addPluginChunkTicket(this);
                            at(FAR_CHUNK, FAR_CHUNK, 20L, () -> {
                                try {
                                    require(
                                            expected("clean-0")
                                                    .equals(chest(FAR_BLOCK, FAR_BLOCK)
                                                            .getBlockInventory()
                                                            .getItem(0)),
                                            "Chunk-load cleanup did not repair the deferred chest");
                                    pass("deferred");
                                } catch (Throwable failure) {
                                    fail(failure);
                                }
                            });
                        });
                        return;
                    }
                    default -> throw new IllegalArgumentException("Unknown probe action");
                }
                pass(args[0]);
            } catch (Throwable failure) {
                fail(failure);
            }
        });
        return true;
    }

    private void at(int chunkX, int chunkZ, long delay, ProbeAction action) {
        var world = Bukkit.getWorlds().getFirst();
        Bukkit.getRegionScheduler()
                .runDelayed(
                        this,
                        world,
                        chunkX,
                        chunkZ,
                        task -> {
                            try {
                                require(
                                        Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ),
                                        "Probe ran outside its owning region");
                                if (Slimefun.getSchedulerService().isFolia()) {
                                    require(!Bukkit.isGlobalTickThread(), "Folia probe ran on the global thread");
                                    int other = chunkX == 0 ? FAR_CHUNK : 0;
                                    require(
                                            !Bukkit.isOwnedByCurrentRegion(world, other, other),
                                            "Distant fixtures share an owner");
                                }
                                action.run();
                            } catch (Throwable failure) {
                                fail(failure);
                            }
                        },
                        delay);
    }

    @FunctionalInterface
    private interface ProbeAction {
        void run() throws Exception;
    }

    private void prepare() throws Exception {
        require(!Files.exists(file("clean-0")), "Refusing to replace a previous probe fixture");
        var world = Bukkit.getWorlds().getFirst();
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
        Item dropped = world.dropItem(new Location(world, 2, 81, 2), old.clone());
        dropped.setGravity(false);
        dropped.setUnlimitedLifetime(true);
        Files.writeString(
                getDataFolder().toPath().resolve("drop.uuid"),
                dropped.getUniqueId().toString());
        // A normal stop saves both independent regions. The clean restart below is the
        // unload boundary; the deferred probe verifies the distant chest starts unloaded.

        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        RecordSet blockRecord = new RecordSet();
        blockRecord.put(FieldKey.LOCATION, machineOwner());
        blockRecord.put(FieldKey.CHUNK, world.getName() + ";128:128");
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
        if (Files.exists(getDataFolder().toPath().resolve("menus-prepare.pass"))) {
            verifyMachine(10, 8);
            verifyVirtualMenu();
            require(
                    expected("clean-0")
                            .equals(readSlot(
                                            Slimefun.getDatabaseManager().getBlockDataController(),
                                            DataScope.BLOCK_INVENTORY,
                                            FieldKey.LOCATION,
                                            machineLocation(FAR_BLOCK + 2, FAR_BLOCK),
                                            24)
                                    .getItemStack(FieldKey.INVENTORY_ITEM)),
                    "Distant machine contents did not remain durable");
        }
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
                Bukkit.getEntity(drop) != null && Bukkit.isOwnedByCurrentRegion(Bukkit.getEntity(drop)),
                "Dropped-item assertion ran outside its entity owner");
        require(
                Bukkit.getEntity(drop) instanceof Item dropped
                        && expected("clean-0").equals(dropped.getItemStack()),
                "Dropped stack mismatch");
    }

    private void verifyUninstalled() throws Exception {
        idle();
        verifyContents();
        if (heldBackpack != null) verifyOwnedBackpackDeferred();
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

    private void prepareOwnedBackpack() throws Exception {
        idle();
        require(Slimefun.getSchedulerService().isFolia(), "Ownership-deferral probe requires Folia");
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        RecordSet profile = new RecordSet();
        profile.put(FieldKey.BACKPACK_ID, HELD_BACKPACK);
        profile.put(FieldKey.PLAYER_UUID, OWNER);
        profile.put(FieldKey.BACKPACK_SIZE, "9");
        profile.put(FieldKey.BACKPACK_NUMBER, "2");
        profile.put(FieldKey.BACKPACK_NAME, "R2FtZXBsYXkgb3duZWQgYmFja3BhY2s=");
        setData(profiles, new RecordKey(DataScope.BACKPACK_PROFILE), profile);
        write(profiles, DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, HELD_BACKPACK, expected("old-0"), false);
        // The normal gameplay API claims this cache entry. Maintenance must not mutate
        // it globally merely because this synthetic server has no connected viewer.
        profiles.getBackpackAsync(HELD_BACKPACK).whenComplete((backpack, failure) -> {
            if (failure != null) {
                fail(failure);
                return;
            }
            at(0, 0, 1L, () -> {
                require(backpack != null, "Gameplay backpack did not load");
                heldBackpack = backpack;
                require(
                        !profiles.runWhileMaintenanceBackpackOwned(backpack, () -> {}),
                        "Gameplay backpack was incorrectly marked maintenance-owned");
                require(
                        expected("old-0").equals(backpack.getInventory().getItem(0)),
                        "Gameplay backpack changed before cleanup");
                pass("ownership-prepare");
            });
        });
    }

    private void verifyOwnedBackpackDeferred() throws Exception {
        idle();
        require(heldBackpack != null, "Missing gameplay-owned backpack fixture");
        require(
                expected("old-0").equals(heldBackpack.getInventory().getItem(0)),
                "Doctor mutated a gameplay-owned backpack without an entity owner");
        require(
                expected("old-0")
                        .equals(read(
                                Slimefun.getDatabaseManager().getProfileDataController(),
                                DataScope.BACKPACK_INVENTORY,
                                FieldKey.BACKPACK_ID,
                                HELD_BACKPACK)),
                "Doctor rewrote the gameplay-owned backpack row");
        require(
                Slimefun.getItemDoctorService().getLastReport().getFailures() == 1 + virtualDeferrals(),
                "Expected only the gameplay-backpack and ownerless-menu deferrals");
        require(
                Slimefun.getItemDoctorService().getLastReport().getItemModelRepairs() == 0,
                "Deferral pass unexpectedly repaired a stack");
    }

    private void idle() {
        require(!Slimefun.getItemDoctorService().getResourcePackDoctor().isSweepActive(), "Doctor pass still running");
    }

    private String machineLocation(int x, int z) {
        return LocationUtils.getLocKey(new Location(Bukkit.getWorlds().getFirst(), x, 80, z));
    }

    private void prepareMachine(int x, int z) throws Exception {
        var location = new Location(Bukkit.getWorlds().getFirst(), x, 80, z);
        require(Bukkit.isOwnedByCurrentRegion(location), "Machine seed is outside its region");
        location.getBlock().setType(Material.FURNACE);
        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        // An existing empty chunk may not have a loaded Slimefun data container on
        // Folia. Complete its normal load before claiming this is a ready live menu.
        require(blocks.getChunkData(location.getChunk()).isDataLoaded(), "Machine chunk data did not load");
        var data = blocks.createBlock(location, "ELECTRIC_FURNACE");
        require(data.isDataLoaded() && data.getBlockMenu() != null, "Machine menu did not initialize");
        // An output slot keeps the fixture independent of smelting and energy behavior.
        data.getBlockMenu().replaceExistingItem(24, expected("old-0"));
        blocks.saveBlockInventoryAsync(data).get(10, TimeUnit.SECONDS);
        require(
                expected("old-0")
                        .equals(readSlot(
                                        blocks, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, machineLocation(x, z), 24)
                                .getItemStack(FieldKey.INVENTORY_ITEM)),
                "Machine fixture was not persisted before cleanup");
    }

    private void verifyMachine(int x, int z) throws Exception {
        var location = new Location(Bukkit.getWorlds().getFirst(), x, 80, z);
        require(Bukkit.isOwnedByCurrentRegion(location), "Machine assertion is outside its region");
        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        var data = blocks.getBlockDataFromCache(location);
        require(
                data != null && data.isDataLoaded() && data.getBlockMenu() != null,
                "Loaded machine inventory is unavailable");
        require(
                expected("clean-0").equals(data.getBlockMenu().getItemInSlot(24)),
                "Loaded machine menu retained or lost item metadata");
        require(
                expected("clean-0")
                        .equals(readSlot(
                                        blocks, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, machineLocation(x, z), 24)
                                .getItemStack(FieldKey.INVENTORY_ITEM)),
                "Loaded machine cleanup was not saved");
    }

    private void prepareVirtualMenu() throws Exception {
        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        RecordSet record = new RecordSet();
        record.put(FieldKey.UNIVERSAL_UUID, VIRTUAL_MENU);
        record.put(FieldKey.SLIMEFUN_ID, "PROGRAMMABLE_ANDROID");
        record.put(FieldKey.UNIVERSAL_TRAITS, "INVENTORY");
        setData(blocks, new RecordKey(DataScope.UNIVERSAL_RECORD), record);
        RecordSet row = new RecordSet();
        row.put(FieldKey.UNIVERSAL_UUID, VIRTUAL_MENU);
        row.put(FieldKey.INVENTORY_SLOT, "20");
        byte[] original = DataUtils.serializeItemStackBytesForStorage(expected("old-0"));
        row.put(FieldKey.INVENTORY_ITEM, original);
        setData(blocks, new RecordKey(DataScope.UNIVERSAL_INVENTORY), row);
        Files.write(getDataFolder().toPath().resolve("virtual-original.bin"), original);
        // Use the existing Android menu preset without a block trait/location. This
        // exercises the production ownerless universal inventory path without a player.
        var data = blocks.getUniversalData(UUID.fromString(VIRTUAL_MENU));
        require(data != null, "Virtual menu record is missing");
        blocks.loadUniversalData(data);
        require(data.isDataLoaded() && data.getMenu() != null, "Virtual menu did not load");
        require(expected("old-0").equals(data.getMenu().getItemInSlot(20)), "Virtual menu changed while loading");
    }

    private int virtualDeferrals() {
        return Slimefun.getSchedulerService().isFolia() ? 1 : 0;
    }

    private void verifyVirtualMenu() throws Exception {
        var blocks = Slimefun.getDatabaseManager().getBlockDataController();
        var data = blocks.getUniversalDataFromCache(UUID.fromString(VIRTUAL_MENU));
        require(
                data != null && data.isDataLoaded() && data.getMenu() != null,
                "Virtual menu was dropped from the loaded cache");
        require(
                !data.hasTrait(UniversalDataTrait.BLOCK)
                        && data.getMenu().toInventory().getLocation() == null,
                "Virtual fixture unexpectedly acquired a block owner");
        ItemStack expected = expected(virtualDeferrals() == 1 ? "old-0" : "clean-0");
        require(expected.equals(data.getMenu().getItemInSlot(20)), "Virtual menu cleanup policy mismatch");
        RecordSet row = readSlot(blocks, DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, VIRTUAL_MENU, 20);
        require(expected.equals(row.getItemStack(FieldKey.INVENTORY_ITEM)), "Virtual menu row mismatch");
        if (virtualDeferrals() == 1) {
            Object value = row.getValue(FieldKey.INVENTORY_ITEM);
            require(
                    value instanceof byte[] bytes
                            && Arrays.equals(
                                    bytes,
                                    Files.readAllBytes(getDataFolder().toPath().resolve("virtual-original.bin"))),
                    "Ownerless Folia row was rewritten instead of deferred");
        }
    }

    private String machineOwner() {
        return machineLocation(2048, 2048);
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
        return readSlot(controller, scope, ownerField, owner, 0).getItemStack(FieldKey.INVENTORY_ITEM);
    }

    private RecordSet readSlot(
            com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController controller,
            DataScope scope,
            FieldKey ownerField,
            String owner,
            int slot)
            throws Exception {
        RecordKey key = new RecordKey(scope);
        key.addCondition(ownerField, owner);
        key.addCondition(FieldKey.INVENTORY_SLOT, Integer.toString(slot));
        key.addField(FieldKey.INVENTORY_ITEM);
        var method = com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController.class.getDeclaredMethod(
                "getData", RecordKey.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rows = (List<RecordSet>) method.invoke(controller, key);
        require(rows.size() == 1, "Expected one inventory fixture row");
        return rows.getFirst();
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
