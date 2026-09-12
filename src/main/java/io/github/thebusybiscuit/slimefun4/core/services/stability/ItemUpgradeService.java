package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ProfileDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunChunkData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalData;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Batched, operator-triggered item-stack upgrade scanner.
 *
 * <p>This service only mutates item stacks. It never rewrites placed Slimefun block IDs, block records, Cargo/Energy
 * state or addon-owned persistence schemas.
 */
public final class ItemUpgradeService {

    private final Slimefun plugin;
    private final ItemUpgradeInspector inspector = new ItemUpgradeInspector();
    private final AtomicBoolean serverRunActive = new AtomicBoolean();
    private volatile ItemUpgradeReport currentReport;
    private volatile ItemUpgradeReport lastReport;
    private volatile ServerRun activeRun;

    public ItemUpgradeService(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    public boolean isServerRunActive() {
        return serverRunActive.get();
    }

    public @Nullable ItemUpgradeReport getCurrentReport() {
        return currentReport;
    }

    public @Nullable ItemUpgradeReport getLastReport() {
        return lastReport;
    }

    /**
     * Starts a batched item-only scan or fix across online players, loaded inventories, dropped items and all Slimefun
     * backpacks.
     *
     * @return {@code false} when another item-upgrade run is active or the plugin is stopping
     */
    public boolean startServerRun(boolean repair, @Nonnull Consumer<ItemUpgradeReport> completion) {
        if (!plugin.isEnabled() || !serverRunActive.compareAndSet(false, true)) {
            return false;
        }

        ItemUpgradeReport report = new ItemUpgradeReport(repair);
        currentReport = report;
        ServerRun run = new ServerRun(report, completion);
        activeRun = run;
        try {
            run.collectLoadedInventories();
            run.startInventoryTask();
            run.startBackpackTask();
        } catch (RuntimeException ex) {
            report.failure();
            run.abort();
            plugin.getLogger().log(Level.SEVERE, "The Item Upgrade Doctor could not start safely.", ex);
            try {
                completion.accept(report);
            } catch (RuntimeException callbackError) {
                plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor completion callback failed.", callbackError);
            }
        }
        return true;
    }

    private final class ServerRun {
        private final ItemUpgradeReport report;
        private final Consumer<ItemUpgradeReport> completion;
        private final Queue<Player> players = new ConcurrentLinkedQueue<>();
        private final Queue<InventoryTarget> inventories = new ConcurrentLinkedQueue<>();
        private final Queue<Item> droppedItems = new ConcurrentLinkedQueue<>();
        private final Queue<SlimefunChunkData> slimefunChunks = new ConcurrentLinkedQueue<>();
        private final Queue<SlimefunUniversalData> universalData = new ConcurrentLinkedQueue<>();
        private final Queue<Chunk> physicalChunks = new ConcurrentLinkedQueue<>();
        private final Map<Inventory, InventoryTarget> inventoryTargets = new IdentityHashMap<>();
        private final AtomicInteger pendingOwnedWork = new AtomicInteger();
        private volatile boolean inventoriesDone;
        private volatile boolean backpacksDone;
        private volatile boolean aborted;
        private volatile TaskHandle inventoryTask;
        private Iterator<String> backpackIds = Collections.emptyIterator();

        private ServerRun(ItemUpgradeReport report, Consumer<ItemUpgradeReport> completion) {
            this.report = report;
            this.completion = completion;
        }

        private void collectLoadedInventories() {
            players.addAll(Bukkit.getOnlinePlayers());

            BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
            slimefunChunks.addAll(controller.getAllLoadedChunkData());
            universalData.addAll(controller.getAllLoadedUniversalData());

            for (World world : Bukkit.getWorlds()) {
                Collections.addAll(physicalChunks, world.getLoadedChunks());
            }
        }

        private void collectChunk(Chunk chunk) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof InventoryHolder holder) {
                    addInventory(holder.getInventory(), null, null, state.getLocation());
                }
            }
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof InventoryHolder holder) {
                    addInventory(holder.getInventory(), null, entity, null);
                } else if (entity instanceof Item itemEntity) {
                    droppedItems.add(itemEntity);
                }
            }
        }

        private void collectSlimefunChunk(SlimefunChunkData chunkData) {
            BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
            for (SlimefunBlockData blockData : chunkData.getAllBlockData()) {
                BlockMenu menu = blockData.getBlockMenu();
                if (menu != null) {
                    addInventory(
                            menu.toInventory(),
                            () -> controller.saveBlockInventory(blockData),
                            null,
                            blockData.getLocation());
                }
            }
        }

        private void collectUniversalData(SlimefunUniversalData data) {
            var menu = data.getMenu();
            if (menu != null) {
                BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
                addInventory(menu.toInventory(), () -> controller.saveUniversalInventory(data), null, null);
            }
        }

        private void addInventory(
                Inventory inventory,
                @Nullable Runnable saveAction,
                @Nullable Entity ownerEntity,
                @Nullable Location ownerLocation) {
            if (inventory == null) {
                return;
            }

            synchronized (inventoryTargets) {
                InventoryTarget existing = inventoryTargets.get(inventory);
                if (existing != null) {
                    existing.merge(saveAction, ownerEntity, ownerLocation);
                    return;
                }

                InventoryTarget target = new InventoryTarget(inventory, saveAction, ownerEntity, ownerLocation);
                inventoryTargets.put(inventory, target);
                inventories.add(target);
            }
        }

        private void startInventoryTask() {
            int perTick = Math.max(1, Slimefun.getCfg().getInt("stability.item-doctor.inventories-per-tick"));
            inventoryTask = Slimefun.getSchedulerService().runAtFixedRate(() -> processInventoryBatch(perTick), 1L, 1L);
        }

        private void processInventoryBatch(int perTick) {
            if (aborted || !plugin.isEnabled()) {
                cancelInventoryTask();
                return;
            }

            for (int i = 0; i < perTick; i++) {
                Player player = players.poll();
                if (player != null) {
                    dispatchFor(player, () -> {
                        if (player.isOnline()) {
                            addInventory(player.getInventory(), null, player, null);
                            addInventory(player.getEnderChest(), null, player, null);
                        }
                    });
                    continue;
                }

                InventoryTarget target = inventories.poll();
                if (target != null) {
                    scheduleInventoryTarget(target);
                    continue;
                }

                Item itemEntity = droppedItems.poll();
                if (itemEntity != null) {
                    dispatchFor(itemEntity, () -> inspectDroppedItem(itemEntity));
                    continue;
                }

                SlimefunChunkData slimefunChunk = slimefunChunks.poll();
                if (slimefunChunk != null) {
                    Location owner = slimefunChunk.getChunk().getBlock(0, 0, 0).getLocation();
                    dispatchAt(owner, () -> collectSlimefunChunk(slimefunChunk));
                    continue;
                }

                SlimefunUniversalData data = universalData.poll();
                if (data != null) {
                    dispatchGlobal(() -> collectUniversalData(data));
                    continue;
                }

                Chunk physicalChunk = physicalChunks.poll();
                if (physicalChunk != null) {
                    Location owner = physicalChunk.getBlock(0, 0, 0).getLocation();
                    dispatchAt(owner, () -> {
                        if (physicalChunk.isLoaded()) {
                            collectChunk(physicalChunk);
                        }
                    });
                    continue;
                }

                tryFinishInventoryPhase();
                return;
            }
        }

        private void cancelInventoryTask() {
            TaskHandle task = inventoryTask;
            if (task != null) {
                task.cancel();
            }
        }

        private void scheduleInventoryTarget(InventoryTarget target) {
            Entity ownerEntity = target.ownerEntity();
            if (ownerEntity != null) {
                dispatchFor(ownerEntity, () -> inspectInventoryTarget(target));
                return;
            }

            Location ownerLocation = target.ownerLocation();
            if (ownerLocation != null) {
                dispatchAt(ownerLocation, () -> inspectInventoryTarget(target));
                return;
            }

            dispatchGlobal(() -> inspectInventoryTarget(target));
        }

        private void dispatchGlobal(Runnable work) {
            dispatchOwnedWork(work, (task, retired) -> Slimefun.getSchedulerService().run(task));
        }

        private void dispatchAt(Location location, Runnable work) {
            dispatchOwnedWork(work, (task, retired) -> Slimefun.getSchedulerService().runAt(location, task));
        }

        private void dispatchFor(Entity entity, Runnable work) {
            dispatchOwnedWork(work, (task, retired) -> Slimefun.getSchedulerService().runFor(entity, task, retired));
        }

        private void dispatchOwnedWork(Runnable work, BiFunction<Runnable, Runnable, TaskHandle> scheduler) {
            pendingOwnedWork.incrementAndGet();
            AtomicBoolean completed = new AtomicBoolean();
            Runnable completionAction = () -> {
                if (completed.compareAndSet(false, true)) {
                    pendingOwnedWork.decrementAndGet();
                }
            };
            Runnable trackedWork = () -> {
                try {
                    if (!aborted && plugin.isEnabled()) {
                        work.run();
                    }
                } finally {
                    completionAction.run();
                }
            };

            try {
                TaskHandle scheduled = scheduler.apply(trackedWork, completionAction);
                if (scheduled.isCancelled()) {
                    completionAction.run();
                }
            } catch (RuntimeException | LinkageError ex) {
                completionAction.run();
                report.failure();
                plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor could not dispatch owned work.", ex);
            }
        }

        private void tryFinishInventoryPhase() {
            if (pendingOwnedWork.get() != 0
                    || !players.isEmpty()
                    || !inventories.isEmpty()
                    || !droppedItems.isEmpty()
                    || !slimefunChunks.isEmpty()
                    || !universalData.isEmpty()
                    || !physicalChunks.isEmpty()) {
                return;
            }

            inventoriesDone = true;
            cancelInventoryTask();
            finishIfReady();
        }

        private void inspectInventoryTarget(InventoryTarget target) {
            synchronized (inventoryTargets) {
                inventoryTargets.remove(target.inventory());
            }
            try {
                boolean changed = inspector.inspectInventory(target.inventory(), report.isRepairMode(), report);
                if (changed && report.isRepairMode() && target.saveAction() != null) {
                    target.saveAction().run();
                }
            } catch (RuntimeException | LinkageError ex) {
                report.failure();
                plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor failed to inspect an inventory.", ex);
            }
        }

        private void inspectDroppedItem(Item itemEntity) {
            try {
                if (!itemEntity.isValid()) {
                    return;
                }
                ItemStack item = itemEntity.getItemStack();
                if (inspector.inspectItem(item, report.isRepairMode(), report) && report.isRepairMode()) {
                    itemEntity.setItemStack(item);
                }
            } catch (RuntimeException | LinkageError ex) {
                report.failure();
                plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor failed to inspect a dropped item.", ex);
            }
        }

        private void startBackpackTask() {
            ProfileDataController controller = Slimefun.getDatabaseManager().getProfileDataController();
            controller.getAllBackpackIdsAsync().whenComplete((ids, error) -> {
                if (aborted || !plugin.isEnabled()) {
                    return;
                }
                Slimefun.getSchedulerService().run(() -> {
                    if (aborted || !plugin.isEnabled()) {
                        return;
                    }
                    if (error != null) {
                        report.failure();
                        plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor could not enumerate backpacks.", error);
                        backpacksDone = true;
                        finishIfReady();
                        return;
                    }
                    backpackIds = ids.iterator();
                    processNextBackpack();
                });
            });
        }

        private void processNextBackpack() {
            if (aborted || !plugin.isEnabled()) {
                return;
            }
            if (!backpackIds.hasNext()) {
                backpacksDone = true;
                finishIfReady();
                return;
            }

            String id = backpackIds.next();
            ProfileDataController controller = Slimefun.getDatabaseManager().getProfileDataController();
            controller.getBackpackForMaintenanceAsync(id).whenComplete((loadedBackpack, error) -> {
                if (aborted || !plugin.isEnabled()) {
                    releaseMaintenanceBackpack(controller, loadedBackpack);
                    return;
                }
                TaskHandle scheduled = Slimefun.getSchedulerService().run(() -> {
                    if (aborted || !plugin.isEnabled()) {
                        releaseMaintenanceBackpack(controller, loadedBackpack);
                        return;
                    }
                    if (error != null) {
                        report.failure();
                        plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor could not load backpack " + id + '.', error);
                    } else if (loadedBackpack != null) {
                        inspectBackpack(controller, loadedBackpack.backpack(), loadedBackpack.maintenanceOwned());
                    }
                    processNextBackpack();
                });
                if (scheduled.isCancelled() && !plugin.isEnabled()) {
                    releaseMaintenanceBackpack(controller, loadedBackpack);
                }
            });
        }

        private void releaseMaintenanceBackpack(
                ProfileDataController controller, @Nullable ProfileDataController.MaintenanceBackpack loadedBackpack) {
            if (loadedBackpack != null && loadedBackpack.maintenanceOwned()) {
                controller.releaseMaintenanceBackpack(loadedBackpack.backpack());
            }
        }

        private void inspectBackpack(ProfileDataController controller, PlayerBackpack backpack, boolean maintenanceLoaded) {
            try {
                if (backpack.isInvalid()) {
                    return;
                }
                report.backpackScanned();
                boolean changed = inspector.inspectInventory(backpack.getInventory(), report.isRepairMode(), report);
                if (changed && report.isRepairMode()) {
                    controller.saveBackpackInventory(backpack);
                }
            } catch (RuntimeException | LinkageError ex) {
                report.failure();
                plugin.getLogger()
                        .log(Level.WARNING, "Item Upgrade Doctor failed to inspect backpack " + backpack.getUniqueId() + '.', ex);
            } finally {
                if (maintenanceLoaded) {
                    controller.releaseMaintenanceBackpack(backpack);
                }
            }
        }

        private synchronized void abort() {
            if (aborted) {
                return;
            }
            aborted = true;
            cancelInventoryTask();
            if (!report.isComplete()) {
                report.markComplete();
            }
            currentReport = null;
            lastReport = report;
            activeRun = null;
            serverRunActive.set(false);
        }

        private synchronized void finishIfReady() {
            if (aborted || !inventoriesDone || !backpacksDone || report.isComplete()) {
                return;
            }

            report.markComplete();
            currentReport = null;
            lastReport = report;
            activeRun = null;
            serverRunActive.set(false);
            logCompletion(report);
            try {
                completion.accept(report);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Item Upgrade Doctor completion callback failed.", ex);
            }
        }
    }

    private void logCompletion(ItemUpgradeReport report) {
        plugin.getLogger()
                .info("Slimefun Item Upgrade Doctor " + (report.isRepairMode() ? "fix" : "scan") + " completed: "
                        + report.getScannedStacks() + " stacks scanned, "
                        + report.getReadyIdUpgrades() + " legacy-ID upgrade candidates, "
                        + report.getRewrittenIds() + " IDs rewritten, "
                        + report.getPresentationCandidates() + " translated presentation candidates, "
                        + report.getFailures() + " failures.");
    }

    private static final class InventoryTarget {
        private final Inventory inventory;
        private volatile Runnable saveAction;
        private volatile Entity ownerEntity;
        private volatile Location ownerLocation;

        private InventoryTarget(
                Inventory inventory,
                @Nullable Runnable saveAction,
                @Nullable Entity ownerEntity,
                @Nullable Location ownerLocation) {
            this.inventory = inventory;
            this.saveAction = saveAction;
            this.ownerEntity = ownerEntity;
            this.ownerLocation = ownerLocation;
        }

        private Inventory inventory() {
            return inventory;
        }

        private @Nullable Runnable saveAction() {
            return saveAction;
        }

        private @Nullable Entity ownerEntity() {
            return ownerEntity;
        }

        private @Nullable Location ownerLocation() {
            return ownerLocation;
        }

        private synchronized void merge(
                @Nullable Runnable additionalAction,
                @Nullable Entity additionalEntity,
                @Nullable Location additionalLocation) {
            if (additionalAction != null && additionalAction != saveAction) {
                if (saveAction == null) {
                    saveAction = additionalAction;
                } else {
                    Runnable previousAction = saveAction;
                    saveAction = () -> {
                        previousAction.run();
                        additionalAction.run();
                    };
                }
            }

            if (ownerEntity == null && additionalEntity != null) {
                ownerEntity = additionalEntity;
                ownerLocation = null;
            } else if (ownerEntity == null && ownerLocation == null && additionalLocation != null) {
                ownerLocation = additionalLocation;
            }
        }
    }
}
