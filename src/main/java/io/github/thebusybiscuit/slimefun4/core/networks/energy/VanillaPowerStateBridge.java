package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;

/**
 * Mirrors Slimefun's live EnergyNet transport state into vanilla player-head
 * {@link Powerable} block data.
 *
 * <p>This bridge never treats vanilla redstone as the source of truth for Slimefun energy.
 * It only exposes Slimefun's state on player heads, where Minecraft already provides the
 * {@code powered} block-state property. Physics updates are deliberately suppressed to avoid
 * creating redstone-update storms from energy ticks.
 */
final class VanillaPowerStateBridge {

    private VanillaPowerStateBridge() {}

    static void sync(@Nonnull Location location, boolean powered) {
        Block block = location.getBlock();
        Material type = block.getType();
        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) {
            return;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Powerable powerable && powerable.isPowered() != powered) {
            powerable.setPowered(powered);
            block.setBlockData(powerable, false);
        }
    }
}
