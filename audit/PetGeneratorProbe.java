package audit;

import io.github.thebusybiscuit.hotbarpets.HotbarPets;
import io.github.thebusybiscuit.hotbarpets.PetEntityData;
import io.github.thebusybiscuit.hotbarpets.pets.WorkbenchPet;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.waleks.simplematerialgenerators.items.MaterialGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable real-server probe. The crafting lane requires an actual protocol-connected player. */
public final class PetGeneratorProbe extends JavaPlugin implements Listener {
    private final List<String> results = new ArrayList<>();
    private boolean ready;
    private boolean finished;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            require(Bukkit.getPluginManager().isPluginEnabled("Slimefun"), "Core not enabled");
            require(Bukkit.getPluginManager().isPluginEnabled("HotbarPets"), "HotbarPets not enabled");
            require(Bukkit.getPluginManager().isPluginEnabled("SimpleMaterialGenerators"), "SMG not enabled");
            generators();
            entityData();
            ready = true;
            getLogger().info("PET_GENERATOR_READY");
            if (!Boolean.getBoolean("pet.audit.connected")) finish();
        }), 40L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!finished) guarded(() -> { throw new AssertionError("Timed out before all required checks"); });
        }, 2400L);
    }

    private void generators() {
        SlimefunItem registered = SlimefunItem.getById("SMG_GENERATOR_COBBLESTONE");
        require(registered instanceof MaterialGenerator, "Original generator ID did not resolve");
        MaterialGenerator generator = (MaterialGenerator) registered;
        Block block = Bukkit.getWorlds().getFirst().getBlockAt(-17, 220, -17);
        block.setType(Material.STONE);
        block.getRelative(BlockFace.UP).setType(Material.CHEST);
        Inventory target = ((InventoryHolder) block.getRelative(BlockFace.UP).getState()).getInventory();
        target.clear();
        MaterialGenerator.clearProgress(block);
        for (int tick = 0; tick < 3; tick++) generator.tick(block);
        require(count(target) == 0, "Generator produced early");
        generator.tick(block);
        require(count(target) == 1 && target.getItem(0).getType() == Material.COBBLESTONE, "Original rate/output changed");
        for (int tick = 0; tick < 996; tick++) generator.tick(block);
        require(count(target) == 250, "One thousand actual ticks did not produce exactly 250 items");
        results.add("registered_generator:1000_ticks_exact_250_output");
        target.clear();
        for (int slot = 0; slot < target.getSize(); slot++) target.setItem(slot, new ItemStack(Material.DIRT, 64));
        for (int tick = 0; tick < 12; tick++) generator.tick(block);
        require(count(target) == target.getSize() * 64, "Full inventory changed");
        target.setItem(0, null);
        generator.tick(block);
        require(target.getItem(0).getType() == Material.COBBLESTONE && target.getItem(0).getAmount() == 1,
                "Ready generator did not retry exactly one output");
        results.add("full_target:unchanged_then_exact_retry");
        target.clear();
        MaterialGenerator.clearProgress(block);
        for (int tick = 0; tick < 3; tick++) generator.tick(block);
        MaterialGenerator.clearProgress(block.getChunk());
        generator.tick(block);
        require(count(target) == 0, "Negative-coordinate chunk cleanup failed");
        results.add("negative_chunk_cleanup:preserved");
        MaterialGenerator.clearProgress(block);
        block.getRelative(BlockFace.UP).setType(Material.AIR);
        block.setType(Material.AIR);
    }

    @SuppressWarnings("deprecation") // Exercise the retained legacy reader, not a new production writer.
    private void entityData() {
        HotbarPets plugin = (HotbarPets) Bukkit.getPluginManager().getPlugin("HotbarPets");
        ArmorStand entity = Bukkit.getWorlds().getFirst().spawn(Bukkit.getWorlds().getFirst().getSpawnLocation(), ArmorStand.class);
        entity.setGravity(false);
        UUID owner = UUID.fromString("fedabcde-1234-4abc-8123-123456789abc");
        NamespacedKey marker = new NamespacedKey(plugin, "hotbarpets_projectile");
        NamespacedKey ownerKey = new NamespacedKey(plugin, "hotbarpets_player");
        try {
            entity.setMetadata("hotbarpets_player", new FixedMetadataValue(plugin, owner));
            require(owner.equals(PetEntityData.getTntOwner(plugin, entity)), "Legacy owner not readable");
            require(!entity.getPersistentDataContainer().has(ownerKey), "Legacy read rewrote data");
            entity.removeMetadata("hotbarpets_player", plugin);
            PetEntityData.markProjectile(plugin, entity);
            PetEntityData.setTntOwner(plugin, entity, owner);
            require(entity.getPersistentDataContainer().has(marker, PersistentDataType.BYTE), "Marker type changed");
            require(owner.toString().equals(entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING)), "Owner type changed");
            PetEntityData.clearProjectileMarker(plugin, entity);
            require(!PetEntityData.isPetProjectile(plugin, entity) && owner.equals(PetEntityData.getTntOwner(plugin, entity)), "Clearing marker erased owner");
            PetEntityData.clearTntOwner(plugin, entity);
            require(PetEntityData.getTntOwner(plugin, entity) == null, "Owner clear failed");
            results.add("real_entity:legacy_read_and_typed_keys_preserved");
        } finally { entity.remove(); }
    }

    @EventHandler public void joined(PlayerJoinEvent event) {
        if (!Boolean.getBoolean("pet.audit.connected")) return;
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            require(ready, "Player arrived before addon initialization");
            Player player = event.getPlayer();
            SlimefunItem item = SlimefunItem.getById("HOTBAR_PET_WORKBENCH");
            require(item instanceof WorkbenchPet, "Workbench pet identity not registered");
            ((WorkbenchPet) item).onUseItem(player);
            require(player.getOpenInventory().getType() == InventoryType.WORKBENCH, "Pet opened the wrong view");
            require(player.getOpenInventory().getTopInventory() instanceof CraftingInventory, "No crafting inventory");
            CraftingInventory inventory = (CraftingInventory) player.getOpenInventory().getTopInventory();
            require(inventory.getMatrix().length == 9, "Not a 3x3 workbench");
            player.teleport(player.getLocation().add(40, 0, 40));
            Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                require(player.isOnline(), "Connected test player left before assertions");
                require(player.getOpenInventory().getType() == InventoryType.WORKBENCH, "Forced workbench closed out of range");
                require(player.getOpenInventory().getTopInventory() == inventory || player.getOpenInventory().getTopInventory().equals(inventory), "Workbench changed unexpectedly");
                results.add("connected_player:virtual_3x3_stays_open_after_40_block_move");
                player.closeInventory();
                finish();
            }), 8L);
        }), 5L);
    }

    private static int count(Inventory inventory) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) if (stack != null && !stack.getType().isAir()) total += stack.getAmount();
        return total;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void guarded(Checked action) {
        try { action.run(); }
        catch (Throwable failure) {
            finished = true;
            getLogger().log(java.util.logging.Level.SEVERE, "PET_GENERATOR_FAIL", failure);
            Bukkit.shutdown();
        }
    }
    private void finish() throws Exception {
        require(results.size() == (Boolean.getBoolean("pet.audit.connected") ? 5 : 4), "Incomplete scenario count");
        Files.writeString(Path.of("pet-generator-results.txt"), "PET_GENERATOR_PASS\n" + String.join("\n", results) + "\n");
        getLogger().info("PET_GENERATOR_PASS cases=" + results.size());
        finished = true;
        Bukkit.shutdown();
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
