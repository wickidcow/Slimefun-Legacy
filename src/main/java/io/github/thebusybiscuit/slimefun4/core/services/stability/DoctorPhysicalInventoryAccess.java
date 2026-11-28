package io.github.thebusybiscuit.slimefun4.core.services.stability;

import javax.annotation.Nullable;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.Lootable;

/** Metadata-first access for background Doctor tile inventory traversal. */
final class DoctorPhysicalInventoryAccess {

    private DoctorPhysicalInventoryAccess() {}

    static boolean hasAttachedLootTable(BlockState state) {
        return state instanceof Lootable lootable && lootable.getLootTable() != null;
    }

    /**
     * The caller owns this tile's region. Never read stock from an unopened
     * loot table, or fetch a combined double chest that can touch its other
     * half. Each loaded chest half is collected independently.
     */
    @Nullable static Inventory getInventory(BlockState state) {
        if (!(state instanceof InventoryHolder holder) || hasAttachedLootTable(state)) {
            return null;
        }
        return state instanceof Chest chest ? chest.getBlockInventory() : holder.getInventory();
    }
}
