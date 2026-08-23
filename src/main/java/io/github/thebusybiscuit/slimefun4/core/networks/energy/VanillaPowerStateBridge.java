package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;

/**
 * Mirrors Slimefun's live energy state into vanilla {@link Powerable} block data when the
 * physical block supports it.
 *
 * <p>This bridge never treats vanilla redstone as the source of truth for Slimefun energy.
 * It only exposes Slimefun's state to Minecraft and other Bukkit consumers. Physics updates
 * are deliberately suppressed to avoid creating redstone-update storms from energy ticks.
 */
final class VanillaPowerStateBridge {

    private VanillaPowerStateBridge() {}

    static void sync(@Nonnull Location location, boolean powered) {
        Block block = location.getBlock();
        BlockData data = block.getBlockData();

        if (data instanceof Powerable powerable && powerable.isPowered() != powered) {
            powerable.setPowered(powered);
            block.setBlockData(powerable, false);
        }
    }
}
