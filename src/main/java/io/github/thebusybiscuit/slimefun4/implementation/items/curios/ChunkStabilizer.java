package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only chunk diagnostics that surface entity/block-entity concentrations before they become severe client or server issues.
 */
public final class ChunkStabilizer extends SimpleSlimefunItem<ItemUseHandler> {

    static final int BUSY_SCORE = 120;
    static final int HEAVY_SCORE = 300;
    static final int CRITICAL_SCORE = 800;

    @ParametersAreNonnullByDefault
    public ChunkStabilizer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            Location target = event.getClickedBlock()
                    .map(block -> block.getLocation())
                    .orElseGet(() -> player.getLocation().clone());
            boolean verbose = player.isSneaking();

            Slimefun.getSchedulerService().runAt(target, () -> {
                StabilityReport report = scan(target);
                Slimefun.getSchedulerService()
                        .runFor(
                                player,
                                () -> sendReport(player, report, verbose),
                                () -> {});
            });
        };
    }

    private static StabilityReport scan(Location target) {
        World world = target.getWorld();
        int chunkX = target.getBlockX() >> 4;
        int chunkZ = target.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return StabilityReport.unloaded(world.getName(), chunkX, chunkZ);
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        Entity[] entities = chunk.getEntities();
        BlockState[] blockStates = chunk.getTileEntities();

        int armorStands = 0;
        int droppedItems = 0;
        int monsters = 0;
        int minecarts = 0;
        int projectiles = 0;
        int experienceOrbs = 0;
        for (Entity entity : entities) {
            if (entity instanceof ArmorStand) {
                armorStands++;
            } else if (entity instanceof Item) {
                droppedItems++;
            } else if (entity instanceof Minecart) {
                minecarts++;
            } else if (entity instanceof Projectile) {
                projectiles++;
            } else if (entity instanceof ExperienceOrb) {
                experienceOrbs++;
            }
            if (entity instanceof Monster) {
                monsters++;
            }
        }

        int hoppers = 0;
        int containers = 0;
        for (BlockState state : blockStates) {
            if (state.getType() == Material.HOPPER) {
                hoppers++;
            }
            if (state instanceof Container) {
                containers++;
            }
        }

        int score = calculateScore(
                entities.length,
                armorStands,
                droppedItems,
                minecarts,
                projectiles,
                experienceOrbs,
                blockStates.length,
                hoppers);
        StabilityBand band = StabilityBand.fromScore(score);
        return new StabilityReport(
                world.getName(),
                chunkX,
                chunkZ,
                entities.length,
                armorStands,
                droppedItems,
                monsters,
                minecarts,
                projectiles,
                experienceOrbs,
                blockStates.length,
                hoppers,
                containers,
                score,
                band,
                true);
    }

    static int calculateScore(
            int totalEntities,
            int armorStands,
            int droppedItems,
            int minecarts,
            int projectiles,
            int experienceOrbs,
            int blockEntities,
            int hoppers) {
        return totalEntities
                + armorStands * 5
                + droppedItems * 2
                + minecarts * 3
                + projectiles
                + experienceOrbs
                + blockEntities * 2
                + hoppers * 5;
    }

    private static void sendReport(Player player, StabilityReport report, boolean verbose) {
        if (!report.loaded()) {
            player.sendMessage(ChatColor.RED + "Chunk Stabilizer: target chunk " + report.chunkX() + ", "
                    + report.chunkZ() + " is not loaded. No chunk was loaded for this scan.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Chunk Stabilizer " + ChatColor.GRAY + "• " + ChatColor.WHITE
                + report.worldName() + ChatColor.GRAY + " chunk " + ChatColor.YELLOW + report.chunkX() + ", "
                + report.chunkZ() + ChatColor.GRAY + " • " + report.band().color() + report.band().displayName()
                + ChatColor.GRAY + " • score " + ChatColor.WHITE + report.score());
        player.sendMessage(ChatColor.GRAY + "Entities: " + ChatColor.YELLOW + report.totalEntities() + ChatColor.GRAY
                + " • Armor stands: " + ChatColor.YELLOW + report.armorStands() + ChatColor.GRAY + " • Items: "
                + ChatColor.YELLOW + report.droppedItems() + ChatColor.GRAY + " • Mobs: " + ChatColor.YELLOW
                + report.monsters());
        player.sendMessage(ChatColor.GRAY + "Block entities: " + ChatColor.YELLOW + report.blockEntities()
                + ChatColor.GRAY + " • Hoppers: " + ChatColor.YELLOW + report.hoppers() + ChatColor.GRAY
                + " • Containers: " + ChatColor.YELLOW + report.containers());

        if (report.armorStands() >= 100) {
            player.sendMessage(ChatColor.RED + "Warning: very high armor-stand density can cause severe client FPS loss.");
        }
        if (report.droppedItems() >= 100) {
            player.sendMessage(ChatColor.RED + "Warning: large dropped-item concentrations can create entity pressure.");
        }
        if (report.totalEntities() >= 500) {
            player.sendMessage(ChatColor.RED + "Warning: this chunk has an unusually high total entity count.");
        }

        if (verbose) {
            player.sendMessage(ChatColor.DARK_GRAY + "Extra: minecarts=" + report.minecarts() + ", projectiles="
                    + report.projectiles() + ", XP orbs=" + report.experienceOrbs());
            player.sendMessage(ChatColor.DARK_GRAY + "This scan is read-only; the Chunk Stabilizer never deletes entities or blocks.");
        }
    }

    enum StabilityBand {
        STABLE("STABLE", ChatColor.GREEN),
        BUSY("BUSY", ChatColor.YELLOW),
        HEAVY("HEAVY", ChatColor.GOLD),
        CRITICAL("CRITICAL", ChatColor.RED);

        private final String displayName;
        private final ChatColor color;

        StabilityBand(String displayName, ChatColor color) {
            this.displayName = displayName;
            this.color = color;
        }

        String displayName() {
            return displayName;
        }

        ChatColor color() {
            return color;
        }

        static StabilityBand fromScore(int score) {
            if (score >= CRITICAL_SCORE) {
                return CRITICAL;
            }
            if (score >= HEAVY_SCORE) {
                return HEAVY;
            }
            if (score >= BUSY_SCORE) {
                return BUSY;
            }
            return STABLE;
        }
    }

    private record StabilityReport(
            String worldName,
            int chunkX,
            int chunkZ,
            int totalEntities,
            int armorStands,
            int droppedItems,
            int monsters,
            int minecarts,
            int projectiles,
            int experienceOrbs,
            int blockEntities,
            int hoppers,
            int containers,
            int score,
            StabilityBand band,
            boolean loaded) {

        static StabilityReport unloaded(String worldName, int chunkX, int chunkZ) {
            return new StabilityReport(
                    worldName,
                    chunkX,
                    chunkZ,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    StabilityBand.STABLE,
                    false);
        }
    }
}