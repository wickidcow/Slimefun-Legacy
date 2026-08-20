package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A detailed field survey instrument for block, chunk, region and local-density diagnostics.
 */
public final class SurveyorsRod extends SimpleSlimefunItem<ItemUseHandler> {

    @ParametersAreNonnullByDefault
    public SurveyorsRod(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            Block clicked = event.getClickedBlock().orElse(null);
            Location target = clicked == null ? player.getLocation().clone() : clicked.getLocation();
            boolean detailedBlock = player.isSneaking() && clicked != null;

            Slimefun.getSchedulerService().runAt(target, () -> {
                SurveyReport report = detailedBlock ? inspectBlock(target) : inspectChunk(target);
                Slimefun.getSchedulerService().runFor(player, () -> sendReport(player, report), () -> {});
            });
        };
    }

    private static SurveyReport inspectChunk(Location target) {
        World world = target.getWorld();
        int chunkX = target.getBlockX() >> 4;
        int chunkZ = target.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return new SurveyReport(
                    ChatColor.GOLD + "Surveyor's Rod " + ChatColor.GRAY + "• chunk " + chunkX + ", " + chunkZ,
                    ChatColor.RED + "Target chunk is not currently loaded; no chunk was loaded to perform the survey.",
                    ChatColor.DARK_GRAY + "Move into the chunk and scan again.");
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        Entity[] entities = chunk.getEntities();
        BlockState[] tileEntities = chunk.getTileEntities();
        Block sample = target.getBlock();
        int regionX = Math.floorDiv(chunk.getX(), 32);
        int regionZ = Math.floorDiv(chunk.getZ(), 32);
        int surfaceY =
                world.getHighestBlockYAt(target.getBlockX(), target.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);

        String first = ChatColor.GOLD + "Surveyor's Rod " + ChatColor.GRAY + "• " + ChatColor.WHITE + world.getName()
                + ChatColor.GRAY + " • XYZ " + ChatColor.YELLOW + target.getBlockX() + ", " + target.getBlockY() + ", "
                + target.getBlockZ();
        String second = ChatColor.GRAY + "Biome: " + ChatColor.AQUA
                + humanize(sample.getBiome().getKey().getKey())
                + ChatColor.GRAY + " • Chunk: " + ChatColor.WHITE + chunkX + ", " + chunkZ + ChatColor.GRAY
                + " • Region: " + ChatColor.WHITE + regionX + ", " + regionZ;
        String third = ChatColor.GRAY + "Surface Y: " + ChatColor.WHITE + surfaceY + ChatColor.GRAY + " • Entities: "
                + ChatColor.YELLOW + entities.length + ChatColor.GRAY + " • Block entities: " + ChatColor.YELLOW
                + tileEntities.length + ChatColor.GRAY + " • Force loaded: "
                + (chunk.isForceLoaded() ? ChatColor.GREEN + "YES" : ChatColor.DARK_GRAY + "NO");
        return new SurveyReport(first, second, third);
    }

    private static SurveyReport inspectBlock(Location target) {
        Block block = target.getBlock();
        String first = ChatColor.GOLD + "Surveyor's Rod " + ChatColor.GRAY + "• detailed block survey";
        String second = ChatColor.GRAY + "Block: " + ChatColor.WHITE
                + humanize(block.getType().getKey().getKey())
                + ChatColor.GRAY + " • XYZ " + ChatColor.YELLOW + block.getX() + ", " + block.getY() + ", "
                + block.getZ();
        String third = ChatColor.GRAY + "Biome: " + ChatColor.AQUA
                + humanize(block.getBiome().getKey().getKey())
                + ChatColor.GRAY + " • Block light: " + ChatColor.YELLOW + block.getLightFromBlocks() + ChatColor.GRAY
                + " • Sky light: " + ChatColor.YELLOW + block.getLightFromSky() + ChatColor.GRAY + " • Total: "
                + ChatColor.YELLOW + block.getLightLevel();
        return new SurveyReport(first, second, third);
    }

    private static void sendReport(Player player, SurveyReport report) {
        player.sendMessage(report.first());
        player.sendMessage(report.second());
        player.sendMessage(report.third());
    }

    private static String humanize(String key) {
        StringBuilder result = new StringBuilder();
        for (String part : key.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private record SurveyReport(String first, String second, String third) {}
}
