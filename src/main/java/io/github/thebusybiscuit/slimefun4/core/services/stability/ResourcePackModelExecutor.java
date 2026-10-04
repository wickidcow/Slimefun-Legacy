package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Stages changes on clones and journals the original stack before publishing metadata. */
final class ResourcePackModelExecutor extends ItemDoctorTraversalExecutor {
    private final boolean repair;
    private final ItemModelRepairExecutor models;
    private final BeforeChange backup;

    ResourcePackModelExecutor(boolean repair, BeforeChange backup) {
        this.repair = repair;
        this.backup = backup;
        this.models = new ItemModelRepairExecutor(repair, !repair, true);
    }

    @Override
    boolean inspectInventory(Inventory inventory, ItemDoctorReport report) {
        return this.inspectInventory(inventory, report, this.inventoryIdentity(inventory));
    }

    boolean inspectInventory(Inventory inventory, ItemDoctorReport report, String identity) {
        report.inventoryScanned();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); ++slot) {
            ItemStack item = inventory.getItem(slot);
            if (!this.inspectItem(item, report, identity + ":" + slot)) continue;
            inventory.setItem(slot, item);
            changed = true;
        }
        return changed;
    }

    @Override
    boolean inspectItem(@Nullable ItemStack item, ItemDoctorReport report) {
        return this.inspectItem(item, report, "detached-item");
    }

    boolean inspectItem(@Nullable ItemStack item, ItemDoctorReport report, String identity) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        ItemStack original = item.clone();
        ItemStack staged = item.clone();
        ItemDoctorReport stagedReport = new ItemDoctorReport(this.repair);
        boolean changed = this.models.inspectItem(staged, stagedReport);
        boolean applied = false;
        try {
            if (this.repair && changed && stagedReport.getFailures() == 0L) {
                this.backup.save(identity, item.clone());
                if (!item.setItemMeta(staged.getItemMeta())) {
                    throw new IOException("The server rejected repaired item metadata");
                }
                applied = true;
            }
        } catch (IOException | LinkageError | RuntimeException failure) {
            item.setItemMeta(original.getItemMeta());
            report.failure();
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Resource-pack Doctor preserved an item after a failed backup/repair.",
                            failure);
        }
        report.mergeItemModels(stagedReport, applied);
        return applied;
    }

    String inventoryIdentity(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Player) {
            Player player = (Player) holder;
            return "player:" + String.valueOf(player.getUniqueId()) + ":" + String.valueOf(inventory.getType());
        }
        if (holder instanceof Entity) {
            Entity entity = (Entity) holder;
            return "entity:" + String.valueOf(entity.getUniqueId()) + ":" + String.valueOf(inventory.getType());
        }
        Location location = inventory.getLocation();
        if (location != null && location.getWorld() != null) {
            return "block:" + String.valueOf(location.getWorld().getUID()) + ":" + location.getBlockX() + ":"
                    + location.getBlockY() + ":" + location.getBlockZ();
        }
        return "inventory:" + String.valueOf(inventory.getType()) + ":" + System.identityHashCode(inventory);
    }

    @FunctionalInterface
    static interface BeforeChange {
        public void save(String var1, ItemStack var2) throws IOException;
    }
}
