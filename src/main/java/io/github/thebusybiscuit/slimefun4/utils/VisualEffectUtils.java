package io.github.thebusybiscuit.slimefun4.utils;

import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Utility methods for visual effects that used to rely on legacy {@code Effect} constants.
 */
public final class VisualEffectUtils {

    private static final int BLOCK_BREAK_PARTICLE_COUNT = 24;
    private static final int SMOKE_PARTICLE_COUNT = 10;

    private VisualEffectUtils() {}

    /**
     * Plays the modern equivalent of the old block-break effect for the supplied block.
     *
     * @param block the block whose data and sound should be represented
     */
    public static void playBlockBreakEffect(@Nonnull Block block) {
        playBlockBreakEffect(block.getLocation(), block.getBlockData());
    }

    /**
     * Plays the modern equivalent of the old block-break effect for the supplied material.
     *
     * @param location the effect location
     * @param material the block material to represent
     */
    public static void playBlockBreakEffect(@Nonnull Location location, @Nonnull Material material) {
        if (material.isBlock()) {
            playBlockBreakEffect(location, material.createBlockData());
        }
    }

    /**
     * Plays block debris and the material's break sound using the modern particle and sound APIs.
     *
     * @param location the block-aligned effect location
     * @param blockData the block data to represent
     */
    public static void playBlockBreakEffect(@Nonnull Location location, @Nonnull BlockData blockData) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Location center = location.clone().add(0.5, 0.5, 0.5);
        SoundGroup sounds = blockData.getSoundGroup();

        world.spawnParticle(
                Particle.BLOCK,
                center,
                BLOCK_BREAK_PARTICLE_COUNT,
                0.35,
                0.35,
                0.35,
                0.05,
                blockData);
        world.playSound(
                center,
                sounds.getBreakSound(),
                SoundCategory.BLOCKS,
                (sounds.getVolume() + 1.0F) / 2.0F,
                sounds.getPitch() * 0.8F);
    }

    /**
     * Spawns the modern smoke particle equivalent used by jet equipment.
     *
     * @param location the player location
     */
    public static void spawnSmoke(@Nonnull Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(
                Particle.SMOKE,
                location.clone().add(0, 0.5, 0),
                SMOKE_PARTICLE_COUNT,
                0.25,
                0.2,
                0.25,
                0.01);
    }
}
