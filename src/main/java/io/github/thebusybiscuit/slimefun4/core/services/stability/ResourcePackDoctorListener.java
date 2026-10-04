package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunChunkData;
import com.xzavier0722.mc.plugin.slimefun4.storage.event.SlimefunChunkDataLoadEvent;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Deferred model cleanup exists only after a saved, explicit install/uninstall authorization. */
final class ResourcePackDoctorListener implements Listener {
    private final Slimefun plugin;
    private final ResourcePackDoctorService service;
    private final ItemDoctorReport report = new ItemDoctorReport(true);

    ResourcePackDoctorListener(Slimefun plugin, ResourcePackDoctorService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (this.service.deferredExecutor() == null) {
            return;
        }
        Player player = event.getPlayer();
        Slimefun.getSchedulerService()
                .runForLater(
                        (Entity) player,
                        () -> {
                            if (player.isOnline()) {
                                this.inventory(
                                        (Inventory) player.getInventory(),
                                        "player:" + String.valueOf(player.getUniqueId()) + ":inventory");
                                this.inventory(
                                        player.getEnderChest(),
                                        "player:" + String.valueOf(player.getUniqueId()) + ":ender-chest");
                            }
                        },
                        20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (this.service.deferredExecutor() == null) {
            return;
        }
        Inventory opened = event.getInventory();
        InventoryHolder holder = opened.getHolder();
        ResourcePackModelExecutor executor = this.service.deferredExecutor();
        if (executor == null) {
            return;
        }
        Runnable repair = () -> {
            Object object;
            if (holder instanceof PlayerBackpack) {
                PlayerBackpack backpack = (PlayerBackpack) holder;
                object = "backpack:" + String.valueOf(backpack.getUniqueId());
            } else {
                object = executor.inventoryIdentity(opened);
            }
            this.inventory(opened, (String) object);
        };
        if (holder instanceof Entity) {
            Entity entity = (Entity) holder;
            Slimefun.getSchedulerService().runFor(entity, repair);
        } else if (opened.getLocation() != null) {
            Slimefun.getSchedulerService().runAt(opened.getLocation(), repair);
        } else if (holder instanceof PlayerBackpack
                || !Slimefun.getSchedulerService().isFolia()) {
            Slimefun.getSchedulerService().runFor((Entity) event.getPlayer(), repair);
        }
        Slimefun.getSchedulerService().runFor((Entity) event.getPlayer(), () -> {
            this.inventory(
                    (Inventory) event.getPlayer().getInventory(),
                    "player:" + String.valueOf(event.getPlayer().getUniqueId()) + ":inventory");
            ResourcePackModelExecutor currentExecutor = this.service.deferredExecutor();
            if (currentExecutor == null) {
                return;
            }
            ItemStack cursor = event.getPlayer().getItemOnCursor();
            if (currentExecutor.inspectItem(
                    cursor,
                    this.report,
                    "player:" + String.valueOf(event.getPlayer().getUniqueId()) + ":cursor")) {
                event.getPlayer().setItemOnCursor(cursor);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ResourcePackModelExecutor executor = this.service.deferredExecutor();
        if (executor == null || !(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (executor.inspectItem(
                item, this.report, "entity:" + String.valueOf(event.getItem().getUniqueId()))) {
            event.getItem().setItemStack(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (this.service.deferredExecutor() == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        Slimefun.getSchedulerService().runAt(chunk.getBlock(0, 0, 0).getLocation(), () -> {
            if (!chunk.isLoaded() || this.service.deferredExecutor() == null) {
                return;
            }
            for (BlockState blockState : chunk.getTileEntities()) {
                if (!(blockState instanceof InventoryHolder)) continue;
                InventoryHolder holder = (InventoryHolder) blockState;
                this.inventory(
                        holder.getInventory(),
                        "block:" + String.valueOf(chunk.getWorld().getUID()) + ":" + blockState.getX() + ":"
                                + blockState.getY() + ":" + blockState.getZ());
            }
            for (Entity entity : chunk.getEntities()) {
                Slimefun.getSchedulerService().runFor(entity, () -> inspectEntity(entity));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlimefunChunkLoad(SlimefunChunkDataLoadEvent event) {
        if (this.service.deferredExecutor() != null) {
            this.repairMenus(event.getChunkData(), 20);
        }
    }

    private void repairMenus(SlimefunChunkData chunk, int attempts) {
        Slimefun.getSchedulerService()
                .runAtLater(
                        chunk.getChunk().getBlock(0, 0, 0).getLocation(),
                        () -> {
                            if (this.service.deferredExecutor() == null
                                    || !chunk.getChunk().isLoaded()) {
                                return;
                            }
                            for (SlimefunBlockData data : chunk.getAllBlockData()) {
                                if (data.isDataLoaded()) continue;
                                if (attempts > 0) {
                                    this.repairMenus(chunk, attempts - 1);
                                } else {
                                    this.plugin
                                            .getLogger()
                                            .warning(
                                                    "Resource-pack Doctor deferred a chunk with unfinished inventory reads.");
                                }
                                return;
                            }
                            BlockDataController controller =
                                    Slimefun.getDatabaseManager().getBlockDataController();
                            for (SlimefunBlockData data : chunk.getAllBlockData()) {
                                BlockMenu menu = data.getBlockMenu();
                                if (menu == null
                                        || data.isPendingRemove()
                                        || controller.isInventoryMutationBlocked(data.getLocation())) continue;
                                this.inventory(menu.toInventory(), "BLOCK_INVENTORY:" + data.getKey());
                                controller.saveBlockInventoryAsync(data).whenComplete((ignored, failure) -> {
                                    if (failure != null) {
                                        this.plugin
                                                .getLogger()
                                                .log(
                                                        Level.WARNING,
                                                        "Could not save deferred model cleanup.",
                                                        (Throwable) failure);
                                    }
                                });
                            }
                        },
                        2L);
    }

    private void inventory(Inventory inventory, String identity) {
        InventoryHolder inventoryHolder;
        ResourcePackModelExecutor executor = this.service.deferredExecutor();
        if (executor != null
                && executor.inspectInventory(inventory, this.report, identity)
                && (inventoryHolder = inventory.getHolder()) instanceof PlayerBackpack) {
            PlayerBackpack backpack = (PlayerBackpack) inventoryHolder;
            Slimefun.getDatabaseManager()
                    .getProfileDataController()
                    .saveBackpackInventoryAsync(backpack)
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            this.plugin
                                    .getLogger()
                                    .log(Level.WARNING, "Could not persist deferred backpack cleanup.", (Throwable)
                                            failure);
                        }
                    });
        }
    }

    private void inspectEntity(Entity entity) {
        if (entity instanceof InventoryHolder) {
            InventoryHolder holder = (InventoryHolder) entity;
            this.inventory(holder.getInventory(), "entity:" + String.valueOf(entity.getUniqueId()));
        } else if (entity instanceof Item) {
            Item dropped = (Item) entity;
            ResourcePackModelExecutor executor = this.service.deferredExecutor();
            ItemStack item = dropped.getItemStack();
            if (executor != null
                    && executor.inspectItem(item, this.report, "entity:" + String.valueOf(dropped.getUniqueId()))) {
                dropped.setItemStack(item);
            }
        }
    }
}
