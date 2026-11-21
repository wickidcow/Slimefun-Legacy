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
import java.util.Map;
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
    private ResourcePackPlayerProbe playerProbe;

    @Override
    public void onEnable() {
        if (Files.exists(getDataFolder().toPath().resolve("players.enabled"))) {
            playerProbe = new ResourcePackPlayerProbe(this);
            Bukkit.getPluginManager().registerEvents(playerProbe, this);
        }
        // Empty Paper servers need not retain spawn chunks. Keep only the near fixture
        // loaded before Doctor resumes; the far fixture stays unloaded until requested.
        at(0, 0, 1L, () -> Bukkit.getWorlds().getFirst().getChunkAt(0, 0).addPluginChunkTicket(this));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        if (args[0].startsWith("player-")) {
            return playerProbe != null && playerProbe.command(args[0]);
        }
        at(0, 0, 20L, () -> {
            try {
                Files.createDirectories(getDataFolder().toPath());
                switch (args[0]) {
                    case "quickshop-api" -> verifyQuickShopPublicRuntime();
                    case "quickshop-db-prepare" -> {
                        prepareQuickShopDisposableSqlFixture();
                        return;
                    }
                    case "quickshop-db-restart" -> verifyQuickShopDisposableSqlFixture();
                    case "identity" -> {
                        CraftingRestrictionProbe.verify();
                        ItemIdentityProbe.verify(getDataFolder().toPath(), false);
                    }
                    case "identity-restart" -> ItemIdentityProbe.verify(getDataFolder().toPath(), true);
                    case "identity-output-restart" -> ItemIdentityProbe.verifyOutputRestart(getDataFolder().toPath());
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

    ItemStack read(
            com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController controller,
            DataScope scope,
            FieldKey ownerField,
            String owner)
            throws Exception {
        return readSlot(controller, scope, ownerField, owner, 0).getItemStack(FieldKey.INVENTORY_ITEM);
    }

    RecordSet readSlot(
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


    /**
     * Optional real QuickShop-Hikari runtime activation test. This is an
     * actual plugin/service lookup on a disposable native Paper server, not
     * an item trade, configured shop matcher or persisted sale-record test.
     */
    private void verifyQuickShopPublicRuntime() throws Exception {
        var plugin = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        require(plugin != null && plugin.isEnabled(), "Official QuickShop-Hikari did not enable");
        var loader = plugin.getClass().getClassLoader();
        Class<?> apiClass = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, loader);
        Class<?> managerClass = Class.forName("com.ghostchu.quickshop.api.shop.ShopManager", true, loader);
        Class<?> shopClass = Class.forName("com.ghostchu.quickshop.api.shop.Shop", true, loader);
        Class<?> userClass = Class.forName("com.ghostchu.quickshop.api.obj.QUser", true, loader);
        Object api = apiClass.getMethod("getInstance").invoke(null);
        require(api != null, "QuickShop public service was not registered");
        Object manager = apiClass.getMethod("getShopManager").invoke(api);
        require(manager != null && managerClass.isInstance(manager), "QuickShop manager was not initialized");
        for (String method : List.of("getItem", "itemSerializeString", "getPrice", "getOwner", "bukkitLocation")) {
            require(Arrays.stream(shopClass.getMethods()).anyMatch(candidate -> candidate.getName().equals(method)),
                    "QuickShop shop public inherited method missing: " + method);
        }
        Class<?> storageClass = Class.forName("com.ghostchu.quickshop.api.shop.ShopInfoStorage", true, loader);
        require(shopClass.getMethod("saveToInfoStorage").getReturnType() == storageClass,
                "QuickShop in-memory prospective storage projection missing");
        require(storageClass.getMethod("getItem").getReturnType() == String.class,
                "QuickShop prospective encoded item getter missing");
        require(storageClass.getMethod("getOwner").getReturnType() == String.class,
                "QuickShop prospective owner getter missing");
        require(storageClass.getMethod("getPrice").getReturnType() == double.class,
                "QuickShop prospective price getter missing");
        require(shopClass.getMethod("matches", ItemStack.class).getReturnType() == boolean.class,
                "QuickShop matcher method is not public boolean");
        require(userClass.getMethod("serialize").getReturnType() == String.class,
                "QuickShop owner serializer method missing");
        var world = Bukkit.getWorlds().getFirst();
        var block = world.getBlockAt(3, 180, 3);
        require(block.getType().isAir(), "QuickShop API smoke test world block already occupied");
        // Keep this activation probe strictly read-only on both boots. No
        // chest or shop fixture is created here; true trade/DB tests are separate.
        Object shop = managerClass.getMethod("getShop", Location.class).invoke(manager, block.getLocation());
        require(shop == null, "Fresh disposable chest unexpectedly has a persisted QuickShop");
        verifyRealQuickShopMatcher(apiClass, api, loader, plugin);
        verifyRealQuickShopInMemoryProjection(api, loader, manager, managerClass, shopClass, storageClass, userClass, block.getLocation());
        Bukkit.getLogger().info("SFL_REAL_QUICKSHOP_API_ACTIVATION_PASS");
    }


    /**
     * Observes the real, configured QuickShop matcher on detached, synthetic
     * Slimefun-like items on a native Paper server. A matcher accepting
     * mismatched PDC/legacy presentation NEVER authorizes Doctor repair.
     * The Python runner compares these redacted outcomes across two JVM boots.
     */
    private void verifyRealQuickShopMatcher(
            Class<?> apiClass, Object api, ClassLoader loader, org.bukkit.plugin.Plugin quickShop)
            throws Exception {
        Class<?> matcherClass = Class.forName("com.ghostchu.quickshop.api.shop.ItemMatcher", true, loader);
        Object matcher = apiClass.getMethod("getItemMatcher").invoke(api);
        require(matcher != null && matcherClass.isInstance(matcher),
                "Real QuickShop item matcher was not initialized");
        var compare = matcherClass.getMethod("matches", ItemStack.class, ItemStack.class);
        var old = new ItemStack(Material.PAPER);
        var originalMeta = old.getItemMeta();
        originalMeta.displayName(Component.text("Carbonado")
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        originalMeta.lore(List.of(Component.text("Old paper shop item")));
        originalMeta.getPersistentDataContainer().set(
                key("slimefun:slimefun_item"), PersistentDataType.STRING, "CARBONADO");
        originalMeta.getPersistentDataContainer().set(
                key("addon:stored_owner"), PersistentDataType.LONG, 72527L);
        old.setItemMeta(originalMeta);
        var fresh = old.clone();
        var freshMeta = fresh.getItemMeta();
        freshMeta.displayName(Component.text("Carbonado")
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        fresh.setItemMeta(freshMeta);
        var wrongId = old.clone();
        var wrongMeta = wrongId.getItemMeta();
        wrongMeta.getPersistentDataContainer().set(
                key("slimefun:slimefun_item"), PersistentDataType.STRING, "CARBON_CHUNK");
        wrongId.setItemMeta(wrongMeta);
        var typedOwner = old.clone();
        var typedMeta = typedOwner.getItemMeta();
        typedMeta.getPersistentDataContainer().set(
                key("addon:stored_owner"), PersistentDataType.INTEGER, 72527);
        typedOwner.setItemMeta(typedMeta);
        byte[][] originals = {
            old.serializeAsBytes(), fresh.serializeAsBytes(),
            wrongId.serializeAsBytes(), typedOwner.serializeAsBytes()
        };

        boolean same = (Boolean) compare.invoke(matcher, old.clone(), old.clone());
        boolean oldFresh = (Boolean) compare.invoke(matcher, old.clone(), fresh.clone());
        boolean oldWrongId = (Boolean) compare.invoke(matcher, old.clone(), wrongId.clone());
        boolean oldTypedOwner = (Boolean) compare.invoke(matcher, old.clone(), typedOwner.clone());
        require(same, "Real QuickShop matcher rejected an identical detached stack");
        require(Arrays.equals(originals[0], old.serializeAsBytes())
                        && Arrays.equals(originals[1], fresh.serializeAsBytes())
                        && Arrays.equals(originals[2], wrongId.serializeAsBytes())
                        && Arrays.equals(originals[3], typedOwner.serializeAsBytes()),
                "Real QuickShop matcher mutated a source item");
        // Hikari can expose the active matcher while Bukkit JavaPlugin.getConfig()
        // is null on a first fresh disposable boot. The matcher verdicts are the
        // authoritative observation; an unavailable config is recorded UNKNOWN.
        var bukkitConfig = ((JavaPlugin) quickShop).getConfig();
        int workType = bukkitConfig == null ? -1 : bukkitConfig.getInt("matcher.work-type", -1);
        String outcomes = "work-type=" + workType
                + ";same=" + same
                + ";old-current=" + oldFresh
                + ";different-slimefun-id=" + oldWrongId
                + ";different-owner-pdc-type=" + oldTypedOwner + "\\n";
        Files.writeString(getDataFolder().toPath().resolve("quickshop-matcher-observed.txt"), outcomes);
        getLogger().info("SFL_REAL_QUICKSHOP_MATCHER_OBSERVATION_PASS "
                + outcomes.trim() + ";diagnostic-only=true");
    }


    /**
     * Native upstream QuickShop ContainerShop save projection on synthetic
     * in-memory fixtures. Never call createShop, registerShop, loadShop,
     * database APIs or trade; zero production records are involved.
     */
    private void verifyRealQuickShopInMemoryProjection(
            Object api, ClassLoader loader, Object manager, Class<?> managerClass,
            Class<?> shopClass, Class<?> storageClass, Class<?> userClass,
            Location location) throws Exception {
        Class<?> qsClass = Class.forName("com.ghostchu.quickshop.QuickShop", true, loader);
        Class<?> container = Class.forName("com.ghostchu.quickshop.shop.ContainerShop", true, loader);
        Class<?> userImpl = Class.forName("com.ghostchu.quickshop.obj.QUserImpl", true, loader);
        Class<?> managerImpl = Class.forName("com.ghostchu.quickshop.shop.SimpleShopManager", true, loader);
        Class<?> shopKind = Class.forName("com.ghostchu.quickshop.api.shop.IShopType", true, loader);
        Class<?> shopState = Class.forName("com.ghostchu.quickshop.api.shop.state.ShopState", true, loader);
        Class<?> benefit = Class.forName("com.ghostchu.quickshop.api.economy.benefit.BenefitProvider", true, loader);
        Class<?> benefitImpl = Class.forName("com.ghostchu.quickshop.economy.QSBenefitProvider", true, loader);
        Class<?> cacheImpl = Class.forName("com.ghostchu.quickshop.shop.cache.SimpleShopInventoryCountCache", true, loader);
        // The already-initialized public API singleton is the actual
        // QuickShop implementation. Avoid reflecting QuickShop's full public
        // method table: optional PlaceholderAPI method signatures are absent
        // on the minimal disposable server.
        require(qsClass.isInstance(api), "QuickShop public API singleton is not the implementation");
        Object qs = api;
        Object owner = userImpl.getMethod("createFullFilled", UUID.class, String.class, boolean.class)
                .invoke(null, UUID.fromString(OWNER), "DoctorSynthetic", false);
        String ownerKey = (String) userClass.getMethod("serialize").invoke(owner);
        require(ownerKey != null && !ownerKey.isBlank(), "QuickShop synthetic owner cannot serialize");
        Object sell = managerImpl.getField("SELLING_TYPE").get(null);
        Object active = managerImpl.getField("ACTIVE_STATE").get(null);
        var make = container.getConstructor(
                qsClass, long.class, Location.class, double.class, ItemStack.class,
                userClass, boolean.class, shopKind, shopState, Map.class, String.class,
                boolean.class, userClass, String.class, String.class, String.class,
                Map.class, benefit, cacheImpl);
        var save = shopClass.getMethod("saveToInfoStorage");
        var encodedGetter = storageClass.getMethod("getItem");
        var ownerGetter = storageClass.getMethod("getOwner");
        var priceGetter = storageClass.getMethod("getPrice");
        var cachedGetter = shopClass.getMethod("itemSerializeString");
        var old = new ItemStack(Material.PAPER);
        var oldMeta = old.getItemMeta();
        oldMeta.displayName(Component.text("Carbonado")
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        oldMeta.getPersistentDataContainer().set(
                key("slimefun:slimefun_item"), PersistentDataType.STRING, "CARBONADO");
        oldMeta.getPersistentDataContainer().set(
                key("addon:stored_owner"), PersistentDataType.LONG, 72527L);
        old.setItemMeta(oldMeta);
        var fresh = old.clone();
        var freshMeta = fresh.getItemMeta();
        freshMeta.displayName(Component.text("Carbonado")
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        fresh.setItemMeta(freshMeta);
        var wrongId = old.clone();
        var idMeta = wrongId.getItemMeta();
        idMeta.getPersistentDataContainer().set(
                key("slimefun:slimefun_item"), PersistentDataType.STRING, "CARBON_CHUNK");
        wrongId.setItemMeta(idMeta);
        var wrongType = old.clone();
        var typedMeta = wrongType.getItemMeta();
        typedMeta.getPersistentDataContainer().set(
                key("addon:stored_owner"), PersistentDataType.INTEGER, 72527);
        wrongType.setItemMeta(typedMeta);
        ItemStack[] originals = {old, fresh, wrongId, wrongType};
        byte[][] source = Arrays.stream(originals).map(ItemStack::serializeAsBytes).toArray(byte[][]::new);
        String[] representations = new String[originals.length];
        for (int i = 0; i < originals.length; i++) {
            // The upstream ContainerShop constructor explicitly forbids
            // persistent storage writes. No shop registration follows.
            Object shop = make.newInstance(qs, -1L, location, 27.25d, originals[i].clone(),
                    owner, false, sell, active, Map.of(), null, true, null, "QuickShop-Hikari",
                    "disposable-in-memory-only", null, Map.of(),
                    benefitImpl.getConstructor().newInstance(),
                    cacheImpl.getConstructor().newInstance());
            String cached = (String) cachedGetter.invoke(shop);
            require(cached != null && !cached.isBlank(),
                    "QuickShop synthetic internal cache is absent");
            Object first = save.invoke(shop);
            Object second = save.invoke(shop);
            require(first != null && second != null, "QuickShop prospective save DTO absent");
            String serialized = (String) encodedGetter.invoke(first);
            require(serialized != null && !serialized.isBlank() && serialized.length() <= 1_048_576,
                    "QuickShop prospective item encoding is missing or too large");
            // Official SHA-pinned PaperPlatform 6.3.0.4 uses native item
            // bytes inside a Base64 envelope. Prove the prospective DB
            // payload contains the precise ORIGINAL item, including PDC
            // types, display components and Slimefun ID. Comparing merely
            // distinct Base64 strings is insufficient to prove preservation.
            byte[] encodedNative = Base64.getDecoder().decode(serialized);
            require(Arrays.equals(source[i], encodedNative),
                    "Real QuickShop prospective save altered original native item bytes");
            ItemStack reloadedNative = ItemStack.deserializeBytes(encodedNative);
            require(Arrays.equals(source[i], reloadedNative.serializeAsBytes()),
                    "Real QuickShop prospective save failed native item round-trip");
            require(serialized.equals(encodedGetter.invoke(second)),
                    "QuickShop save projection changed across two reads");
            require(ownerKey.equals(ownerGetter.invoke(first))
                            && ownerKey.equals(ownerGetter.invoke(second)),
                    "QuickShop prospective shop owner was not preserved");
            require(Double.doubleToLongBits(27.25d)
                            == Double.doubleToLongBits(((Number) priceGetter.invoke(first)).doubleValue())
                            && Double.doubleToLongBits(27.25d)
                            == Double.doubleToLongBits(((Number) priceGetter.invoke(second)).doubleValue()),
                    "QuickShop prospective shop price was not preserved");
            require(Arrays.equals(source[i], originals[i].serializeAsBytes()),
                    "QuickShop in-memory projection mutated a source item");
            representations[i] = serialized;
        }
        require(!representations[0].equals(representations[1]),
                "QuickShop save projection collapsed legacy/current style");
        require(!representations[0].equals(representations[2]),
                "QuickShop save projection collapsed Slimefun ID");
        require(!representations[0].equals(representations[3]),
                "QuickShop save projection collapsed typed metadata");
        require(managerClass.getMethod("getShop", Location.class).invoke(manager, location) == null,
                "QuickShop in-memory fixture was unexpectedly registered");
        Files.writeString(getDataFolder().toPath().resolve("quickshop-prospective-observed.txt"),
                "in-memory-only=true;unregistered=true;encoding-stable=true;"
                        + "owner-price-stable=true;style-distinct=true;slimefun-id-distinct=true;"
                        + "typed-pdc-distinct=true;source-unchanged=true;"
                        + "saved-native-bytes-identical=true\\n");
        getLogger().info("SFL_QUICKSHOP_IN_MEMORY_PROJECTION_PASS "
                + "no-sql=true;no-registration=true;no-trades=true");
    }


    /**
     * This is a TEST PLUGIN ONLY, loaded exclusively in a brand-new disposable
     * server directory. It deliberately creates one synthetic shop in that
     * server's isolated QuickShop SQLite database; production Doctor never
     * calls registration or any database writer.
     */
    private void prepareQuickShopDisposableSqlFixture() throws Exception {
        var loc = Bukkit.getWorlds().getFirst().getBlockAt(6, 180, 6).getLocation();
        require(loc.getBlock().getType().isAir(), "QuickShop disposable SQL test site was already used");
        require(!Files.exists(getDataFolder().toPath().resolve("quickshop-db-prepare.pass")),
                "Refusing duplicate disposable shop insertion");
        var quickShop = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        require(quickShop != null && quickShop.isEnabled(), "Official QuickShop is absent");
        var loader = quickShop.getClass().getClassLoader();
        var apiType = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, loader);
        var managerType = Class.forName("com.ghostchu.quickshop.api.shop.ShopManager", true, loader);
        var shopType = Class.forName("com.ghostchu.quickshop.api.shop.Shop", true, loader);
        var userType = Class.forName("com.ghostchu.quickshop.api.obj.QUser", true, loader);
        var qsImpl = Class.forName("com.ghostchu.quickshop.QuickShop", true, loader);
        var containerType = Class.forName("com.ghostchu.quickshop.shop.ContainerShop", true, loader);
        var userImpl = Class.forName("com.ghostchu.quickshop.obj.QUserImpl", true, loader);
        var managerImpl = Class.forName("com.ghostchu.quickshop.shop.SimpleShopManager", true, loader);
        var kind = Class.forName("com.ghostchu.quickshop.api.shop.IShopType", true, loader);
        var state = Class.forName("com.ghostchu.quickshop.api.shop.state.ShopState", true, loader);
        var benefit = Class.forName("com.ghostchu.quickshop.api.economy.benefit.BenefitProvider", true, loader);
        var benefitImpl = Class.forName("com.ghostchu.quickshop.economy.QSBenefitProvider", true, loader);
        var countCache = Class.forName("com.ghostchu.quickshop.shop.cache.SimpleShopInventoryCountCache", true, loader);
        var blockPos = Class.forName("com.ghostchu.quickshop.api.serialize.BlockPos", true, loader);
        Object api = apiType.getMethod("getInstance").invoke(null);
        require(qsImpl.isInstance(api), "Official QuickShop implementation singleton unavailable");
        Object manager = apiType.getMethod("getShopManager").invoke(api);
        require(managerType.getMethod("getShop", Location.class).invoke(manager, loc) == null,
                "Refusing to replace an existing shop");
        var old = quickShopPersistedItem();
        var stock = old.clone();
        stock.setAmount(23);
        byte[] stockBytes = stock.serializeAsBytes();
        loc.getBlock().setType(Material.CHEST);
        var chest = (Chest) loc.getBlock().getState();
        require(chest.getBlockInventory().getItem(0) == null,
                "Disposable QuickShop chest must begin empty");
        chest.getBlockInventory().setItem(0, stock);
        Object owner = userImpl.getMethod("createFullFilled", UUID.class, String.class, boolean.class)
                .invoke(null, UUID.fromString(OWNER), "DoctorSynthetic", true);
        Object symbol = blockPos.getConstructor(Location.class).newInstance(loc);
        String link = (String) blockPos.getMethod("serialize").invoke(symbol);
        Object shop = containerType.getConstructor(
                        qsImpl, long.class, Location.class, double.class, ItemStack.class,
                        userType, boolean.class, kind, state, Map.class, String.class,
                        boolean.class, userType, String.class, String.class, String.class,
                        Map.class, benefit, countCache)
                .newInstance(api, -1L, loc, 27.25d, old.clone(), owner, false,
                        managerImpl.getField("SELLING_TYPE").get(null),
                        managerImpl.getField("ACTIVE_STATE").get(null),
                        Map.of(), null, true, null, quickShop.getName(),
                        link, null, Map.of(),
                        benefitImpl.getConstructor().newInstance(),
                        countCache.getConstructor().newInstance());
        require(Arrays.equals(stockBytes, chest.getBlockInventory().getItem(0).serializeAsBytes()),
                "Creating in-memory shop changed physical chest stock");
        // registerShop(..., true) is used ONLY in this disposable fixture:
        // unlike prospective saveToInfoStorage, it deliberately writes a
        // synthetic row to a fresh isolated QuickShop test database.
        var registration = (java.util.concurrent.CompletableFuture<?>) managerType
                .getMethod("registerShop", shopType, boolean.class).invoke(manager, shop, true);
        registration.whenComplete((result, failure) -> at(0, 0, 1L, () -> {
            try {
                require(failure == null, "QuickShop test database registration failed");
                require(((Number) shopType.getMethod("getShopId").invoke(shop)).longValue() > 0,
                        "QuickShop assigned no persisted shop ID");
                verifyQuickShopDisposableSqlFixture();
                pass("quickshop-db-prepare");
            } catch (Throwable problem) {
                fail(problem);
            }
        }));
    }

    private ItemStack quickShopPersistedItem() {
        var old = new ItemStack(Material.PAPER, 1);
        var meta = old.getItemMeta();
        meta.displayName(Component.text("Carbonado")
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        meta.lore(List.of(Component.text("Original shop stock")));
        meta.getPersistentDataContainer().set(
                key("slimefun:slimefun_item"), PersistentDataType.STRING, "CARBONADO");
        meta.getPersistentDataContainer().set(
                key("addon:stored_owner"), PersistentDataType.LONG, 72527L);
        old.setItemMeta(meta);
        return old;
    }

    /**
     * Read the actual Hikari database row, not a projected in-memory DTO,
     * then check a physical-world chest after an independent JVM restart.
     * All item, owner and price data are synthetic and never logged.
     */
    private void verifyQuickShopDisposableSqlFixture() throws Exception {
        var loc = Bukkit.getWorlds().getFirst().getBlockAt(6, 180, 6).getLocation();
        require(loc.getBlock().getType() == Material.CHEST,
                "Persisted QuickShop fixture chest missing after restart");
        ItemStack sale = quickShopPersistedItem();
        ItemStack expectedStock = sale.clone();
        expectedStock.setAmount(23);
        ItemStack stock = ((Chest) loc.getBlock().getState()).getBlockInventory().getItem(0);
        require(stock != null && Arrays.equals(expectedStock.serializeAsBytes(), stock.serializeAsBytes()),
                "QuickShop test chest stock quantity or metadata changed");
        var quickShop = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        require(quickShop != null && quickShop.isEnabled(), "Official QuickShop unavailable");
        var loader = quickShop.getClass().getClassLoader();
        var api = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, loader);
        var dbType = Class.forName("com.ghostchu.quickshop.api.database.DatabaseHelper", true, loader);
        var recordType = Class.forName("com.ghostchu.quickshop.api.database.bean.ShopRecord", true, loader);
        var dataType = Class.forName("com.ghostchu.quickshop.api.database.bean.DataRecord", true, loader);
        var infoType = Class.forName("com.ghostchu.quickshop.api.database.bean.InfoRecord", true, loader);
        var userType = Class.forName("com.ghostchu.quickshop.api.obj.QUser", true, loader);
        Object instance = api.getMethod("getInstance").invoke(null);
        Object database = api.getMethod("getDatabaseHelper").invoke(instance);
        @SuppressWarnings("unchecked")
        var records = (List<Object>) dbType.getMethod("listShops", String.class, boolean.class)
                .invoke(database, loc.getWorld().getName(), false);
        require(records.size() == 1, "QuickShop disposable database must contain exactly one shop row");
        Object record = records.getFirst();
        Object info = recordType.getMethod("getInfoRecord").invoke(record);
        require(((Number) infoType.getMethod("getShopId").invoke(info)).longValue() > 0,
                "Persisted QuickShop database record lost its shop ID");
        require(loc.getWorld().getName().equals(infoType.getMethod("getWorld").invoke(info))
                        && ((Number) infoType.getMethod("getX").invoke(info)).intValue() == loc.getBlockX()
                        && ((Number) infoType.getMethod("getY").invoke(info)).intValue() == loc.getBlockY()
                        && ((Number) infoType.getMethod("getZ").invoke(info)).intValue() == loc.getBlockZ(),
                "QuickShop persisted the wrong physical shop location");
        Object data = recordType.getMethod("getDataRecord").invoke(record);
        String persistedItem = (String) dataType.getMethod("getEncoded").invoke(data);
        require(persistedItem != null && !persistedItem.isBlank()
                        && Arrays.equals(sale.serializeAsBytes(), Base64.getDecoder().decode(persistedItem)),
                "QuickShop persisted SQL item bytes differ from the original legacy shop item");
        Object owner = dataType.getMethod("getOwner").invoke(data);
        require(owner != null && ((String) userType.getMethod("serialize").invoke(owner))
                        .equals(OWNER),
                "QuickShop persisted the wrong real-player UUID owner");
        require(Double.doubleToLongBits(27.25d)
                        == Double.doubleToLongBits(
                                ((Number) dataType.getMethod("getPrice").invoke(data)).doubleValue()),
                "QuickShop persisted the wrong exact shop price");
        var managerType = Class.forName("com.ghostchu.quickshop.api.shop.ShopManager", true, loader);
        Object manager = api.getMethod("getShopManager").invoke(instance);
        var shopType = Class.forName("com.ghostchu.quickshop.api.shop.Shop", true, loader);
        Object loaded = managerType.getMethod("getShop", Location.class).invoke(manager, loc);
        require(loaded != null, "QuickShop persisted row was not restored to runtime shop lookup");
        Object loadedOwner = shopType.getMethod("getOwner").invoke(loaded);
        require(loadedOwner != null && OWNER.equals(userType.getMethod("serialize").invoke(loadedOwner)),
                "QuickShop loaded shop lost its real-player UUID owner");
        var view = (ItemStack) shopType.getMethod("getItem").invoke(loaded);
        require(view != null && Arrays.equals(sale.serializeAsBytes(), view.serializeAsBytes()),
                "QuickShop loaded shop sale item does not equal original native item bytes");
        Files.writeString(getDataFolder().toPath().resolve("quickshop-db-observed.txt"),
                "synthetic-only=true;isolated-sql-row=true;shop-loaded=true;"
                        + "native-sale-bytes-identical=true;stock-count-preserved=true;"
                        + "stock-metadata-identical=true;real-owner-uuid-stable=true;price-stable=true\\n");
        getLogger().info("SFL_QUICKSHOP_DISPOSABLE_SQL_PROOF_PASS "
                + "synthetic-only=true;no-trades=true;not-production=true");
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

    ItemStack expected(String name) throws Exception {
        return ItemStack.deserializeBytes(Files.readAllBytes(file(name)));
    }

    private static NamespacedKey key(String key) {
        return NamespacedKey.fromString(key);
    }

    static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    void pass(String phase) throws Exception {
        String proof = "RESOURCE_PACK_DOCTOR_PROBE_PASS " + phase + " | " + Bukkit.getVersion();
        Files.writeString(getDataFolder().toPath().resolve(phase + ".pass"), proof + '\n');
        getLogger().info(proof);
    }

    void fail(Throwable failure) {
        getLogger().log(java.util.logging.Level.SEVERE, "RESOURCE_PACK_DOCTOR_PROBE_FAIL", failure);
    }
}
