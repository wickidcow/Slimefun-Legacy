package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.miner;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.bakedlibs.dough.scheduling.TaskQueue;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * This represents a running instance of an {@link IndustrialMiner}.
 *
 * @author TheBusyBiscuit
 *
 * @see IndustrialMiner
 * @see AdvancedIndustrialMiner
 *
 */
class MiningTask implements Runnable {

    private final IndustrialMiner miner;
    private final UUID owner;

    private final Block chest;
    private final Block[] pistons;

    private final BlockPosition start;
    private final BlockPosition end;
    private final int height;

    private boolean running = false;
    private int fuelLevel = 0;
    private int ores = 0;

    private int x;
    private int z;

    @ParametersAreNonnullByDefault
    MiningTask(IndustrialMiner miner, UUID owner, Block chest, Block[] pistons, Block start, Block end) {
        this.miner = miner;
        this.owner = owner;

        this.chest = chest;
        this.pistons = pistons;

        this.start = new BlockPosition(start);
        this.end = new BlockPosition(end);

        this.height = start.getY();
        this.x = start.getX();
        this.z = start.getZ();
    }

    void start(@Nonnull Block b) {
        miner.activeMiners.put(b.getLocation(), this);
        running = true;
        warmUp();
    }

    void stop() {
        running = false;
        miner.activeMiners.remove(chest.getRelative(BlockFace.DOWN).getLocation());
    }

    void stop(@Nonnull MinerStoppingReason reason) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            Slimefun.getLocalization().sendMessage(p, reason.getErrorMessage());
        }
        stop();
    }

    private void warmUp() {
        TaskQueue queue = new TaskQueue();
        queue.thenRun(4, () -> setPistonState(pistons[0], true));
        queue.thenRun(10, () -> setPistonState(pistons[0], false));
        queue.thenRun(8, () -> setPistonState(pistons[1], true));
        queue.thenRun(10, () -> setPistonState(pistons[1], false));
        queue.thenRun(() -> {
            consumeFuel();
            if (fuelLevel <= 0) {
                stop(MinerStoppingReason.NO_FUEL);
                return;
            }
        });
        queue.thenRun(6, () -> setPistonState(pistons[0], true));
        queue.thenRun(9, () -> setPistonState(pistons[0], false));
        queue.thenRun(4, () -> setPistonState(pistons[1], true));
        queue.thenRun(7, () -> setPistonState(pistons[1], false));
        queue.thenRun(3, () -> setPistonState(pistons[0], true));
        queue.thenRun(5, () -> setPistonState(pistons[0], false));
        queue.thenRun(2, () -> setPistonState(pistons[1], true));
        queue.thenRun(4, () -> setPistonState(pistons[1], false));
        queue.thenRun(1, () -> setPistonState(pistons[0], true));
        queue.thenRun(3, () -> setPistonState(pistons[0], false));
        queue.thenRun(1, () -> setPistonState(pistons[1], true));
        queue.thenRun(2, () -> setPistonState(pistons[1], false));
        queue.thenRun(1, this);
        queue.execute(Slimefun.instance());
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }

        TaskQueue queue = new TaskQueue();
        queue.thenRun(1, () -> setPistonState(pistons[0], true));
        queue.thenRun(3, () -> setPistonState(pistons[0], false));
        queue.thenRun(1, () -> setPistonState(pistons[1], true));
        queue.thenRun(3, () -> setPistonState(pistons[1], false));

        queue.thenRun(() -> {
            try {
                Block furnace = chest.getRelative(BlockFace.DOWN);
                VisualEffectUtils.playBlockBreakEffect(furnace.getLocation(), Material.STONE);

                World world = start.getWorld();
                for (int y = height; y > world.getMinHeight(); y--) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!Slimefun.getProtectionManager()
                            .hasPermission(Bukkit.getOfflinePlayer(owner), b, Interaction.BREAK_BLOCK)) {
                        stop(MinerStoppingReason.NO_PERMISSION);
                        return;
                    }

                    if (miner.canMine(b) && push(miner.getOutcome(b.getType()))) {
                        VisualEffectUtils.playBlockBreakEffect(furnace.getLocation(), b.getType());
                        SoundEffect.MINING_TASK_SOUND.playAt(furnace);
                        b.setType(Material.AIR);
                        fuelLevel--;
                        ores++;
                        Slimefun.runSyncAt(furnace.getLocation(), this, 4L);
                        return;
                    }
                }
                nextColumn();
            } catch (Exception e) {
                Slimefun.logger()
                        .log(
                                Level.SEVERE,
                                e,
                                () -> "An Error occurred while running an Industrial Miner at "
                                        + new BlockPosition(chest));
                stop();
            }
        });

        queue.execute(Slimefun.instance());
    }

    private void nextColumn() {
        if (x < end.getX()) {
            x++;
        } else if (z < end.getZ()) {
            x = start.getX();
            z++;
        } else {
            stop();
            Player p = Bukkit.getPlayer(owner);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.4F, 1F);
                Slimefun.getLocalization()
                        .sendMessage(
                                p,
                                "machines.INDUSTRIAL_MINER.finished",
                                msg -> msg.replace("%ores%", String.valueOf(ores)));
            }
            return;
        }
        Slimefun.runSyncAt(chest.getRelative(BlockFace.DOWN).getLocation(), this, 5L);
    }

    private boolean push(@Nonnull ItemStack item) {
        if (fuelLevel < 1) {
            consumeFuel();
        }
        if (fuelLevel > 0) {
            if (chest.getType() == Material.CHEST) {
                BlockState state = chest.getState(false);
                if (state instanceof Chest chestState) {
                    Inventory inv = chestState.getBlockInventory();
                    if (Slimefun.getItemStackService().fits(inv, item, InventoryContext.OUTPUT_CHEST)) {
                        Slimefun.getItemStackService().addItem(inv, item, InventoryContext.OUTPUT_CHEST);
                        return true;
                    } else {
                        stop(MinerStoppingReason.CHEST_FULL);
                    }
                } else {
                    stop(MinerStoppingReason.STRUCTURE_DESTROYED);
                }
            } else {
                stop(MinerStoppingReason.STRUCTURE_DESTROYED);
            }
        } else {
            stop(MinerStoppingReason.NO_FUEL);
        }
        return false;
    }

    private void consumeFuel() {
        if (chest.getType() == Material.CHEST) {
            BlockState state = chest.getState(false);
            if (state instanceof Chest chestState) {
                Inventory inv = chestState.getBlockInventory();
                this.fuelLevel = grabFuelFrom(inv);
            }
        }
    }

    private int grabFuelFrom(@Nonnull Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            for (MachineFuel fuelType : miner.fuelTypes) {
                ItemStack item = inv.getContents()[i];
                if (fuelType.test(item) && running) {
                    ItemUtils.consumeItem(item, false);
                    if (miner instanceof AdvancedIndustrialMiner) {
                        inv.addItem(new ItemStack(Material.BUCKET));
                    }
                    return fuelType.getTicks();
                }
            }
        }
        return 0;
    }

    private void setPistonState(@Nonnull Block block, boolean extended) {
        if (!running) {
            return;
        }
        try {
            Location particleLoc = chest.getLocation().clone().add(0, -1, 0);
            block.getWorld().spawnParticle(VersionedParticle.SMOKE, particleLoc, 20, 0.7, 0.7, 0.7, 0);

            if (block.getType() == Material.MOVING_PISTON) {
                block.getRelative(BlockFace.UP).setType(Material.AIR);
            } else if (block.getType() == Material.PISTON) {
                Block above = block.getRelative(BlockFace.UP);
                if (above.isEmpty() || above.getType() == Material.PISTON_HEAD) {
                    Piston piston = (Piston) block.getBlockData();
                    if (piston.getFacing() == BlockFace.UP) {
                        setExtended(block, piston, extended);
                    } else {
                        stop(MinerStoppingReason.PISTON_WRONG_DIRECTION);
                    }
                } else {
                    stop(MinerStoppingReason.PISTON_NO_SPACE);
                }
            } else {
                stop(MinerStoppingReason.STRUCTURE_DESTROYED);
            }
        } catch (Exception e) {
            Slimefun.logger()
                    .log(
                            Level.SEVERE,
                            e,
                            () -> "An Error occurred while moving a Piston for an Industrial Miner at "
                                    + new BlockPosition(block));
            stop();
        }
    }

    private void setExtended(@Nonnull Block block, @Nonnull Piston piston, boolean extended) {
        piston.setExtended(extended);
        block.setBlockData(piston, false);
        if (extended) {
            PistonHead head = (PistonHead) Material.PISTON_HEAD.createBlockData();
            head.setFacing(BlockFace.UP);
            block.getRelative(BlockFace.UP).setBlockData(head, false);
        } else {
            block.getRelative(BlockFace.UP).setType(Material.AIR);
        }
        block.getWorld()
                .playSound(
                        block.getLocation(),
                        extended ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT,
                        0.1F,
                        1F);
    }
}
