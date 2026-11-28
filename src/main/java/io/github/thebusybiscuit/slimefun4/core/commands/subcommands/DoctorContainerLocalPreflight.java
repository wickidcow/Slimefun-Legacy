package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.loot.Lootable;

/**
 * Read-only rejection gate for a future explicit physical-container repair.
 * Passing these local checks is not proof of isolation from plugins, hoppers,
 * cargo, shops or other inventory writers, and never grants write permission.
 */
final class DoctorContainerLocalPreflight {

    enum Result {
        UNSUPPORTED_LAYOUT,
        ACTIVE_VIEWERS,
        LOCKED,
        TILE_METADATA,
        LOOT_TABLE_OR_UNKNOWN,
        SLIMEFUN_BLOCK,
        SHOP_INTEGRATION_UNVERIFIED,
        ADJACENT_HOPPER,
        NEIGHBOR_UNAVAILABLE,
        LOCAL_CHECKS_ONLY,
        UNAVAILABLE
    }

    private static final BlockFace[] NEIGHBORS = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private DoctorContainerLocalPreflight() {}

    /** A null observation means the neighbor could not be checked safely. */
    static Result inspectAdjacentHoppers(Function<BlockFace, Material> materialAt) {
        boolean unavailable = false;
        for (BlockFace face : NEIGHBORS) {
            Material material = materialAt.apply(face);
            if (material == Material.HOPPER) {
                return Result.ADJACENT_HOPPER;
            }
            unavailable |= material == null;
        }
        return unavailable ? Result.NEIGHBOR_UNAVAILABLE : Result.LOCAL_CHECKS_ONLY;
    }

    // A stock read on a lootable container may cause loot materialization.
    // Check before obtaining or reading the live inventory.
    static boolean hasAttachedLootTable(Container state) {
        return state instanceof Lootable lootable && lootable.getLootTable() != null;
    }

    static Result inspect(Container state, Inventory inventory, boolean slimefunBlock, boolean shopPluginPresent) {
        if (state == null || inventory == null || inventory.getSize() != 27) {
            return Result.UNSUPPORTED_LAYOUT;
        }

        Material type = state.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
            return Result.UNSUPPORTED_LAYOUT;
        }

        if (type != Material.BARREL
                && (!(state.getBlockData() instanceof Chest chest) || chest.getType() != Chest.Type.SINGLE)) {
            return Result.UNSUPPORTED_LAYOUT;
        }

        if (!inventory.getViewers().isEmpty()) {
            return Result.ACTIVE_VIEWERS;
        }
        if (state.isLocked()) {
            return Result.LOCKED;
        }
        if (!state.getPersistentDataContainer().getKeys().isEmpty()) {
            return Result.TILE_METADATA;
        }
        if (!(state instanceof Lootable lootable) || lootable.getLootTable() != null) {
            return Result.LOOT_TABLE_OR_UNKNOWN;
        }
        if (slimefunBlock) {
            return Result.SLIMEFUN_BLOCK;
        }
        if (shopPluginPresent) {
            // Installation alone does not prove this block is a shop. Until
            // a verified adapter can exclude it, report uncertainty instead.
            return Result.SHOP_INTEGRATION_UNVERIFIED;
        }
        return Result.LOCAL_CHECKS_ONLY;
    }
}
