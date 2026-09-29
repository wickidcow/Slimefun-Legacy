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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
                    "&6" + "Surveyor's Rod " + "&7" + "• chunk " + chunkX + ", " + chunkZ,
                    "&c" + "Target chunk is not currently loaded; no chunk was loaded to perform the survey.",
                    "&8" + "Move into the chunk and scan again.");
        }

        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        Entity[] entities = chunk.getEntities();
        BlockState[] tileEntities = chunk.getTileEntities();
        Block sample = target.getBlock();
        int regionX = Math.floorDiv(chunk.getX(), 32);
        int regionZ = Math.floorDiv(chunk.getZ(), 32);
        int surfaceY =
                world.getHighestBlockYAt(target.getBlockX(), target.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);

        String first = "&6" + "Surveyor's Rod " + "&7" + "• " + "&f" + world.getName()
                + "&7" + " • XYZ " + "&e" + target.getBlockX() + ", " + target.getBlockY() + ", "
                + target.getBlockZ();
        String second = "&7" + "Biome: " + "&b"
                + humanize(sample.getBiome().getKey().getKey())
                + "&7" + " • Chunk: " + "&f" + chunkX + ", " + chunkZ + "&7"
                + " • Region: " + "&f" + regionX + ", " + regionZ;
        String third = "&7" + "Surface Y: " + "&f" + surfaceY + "&7" + " • Entities: "
                + "&e" + entities.length + "&7" + " • Block entities: " + "&e"
                + tileEntities.length + "&7" + " • Force loaded: "
                + (chunk.isForceLoaded() ? "&a" + "YES" : "&8" + "NO");
        return new SurveyReport(first, second, third);
    }

    private static SurveyReport inspectBlock(Location target) {
        Block block = target.getBlock();
        String first = "&6" + "Surveyor's Rod " + "&7" + "• detailed block survey";
        String second = "&7" + "Block: " + "&f"
                + humanize(block.getType().getKey().getKey())
                + "&7" + " • XYZ " + "&e" + block.getX() + ", " + block.getY() + ", "
                + block.getZ();
        String third = "&7" + "Biome: " + "&b"
                + humanize(block.getBiome().getKey().getKey())
                + "&7" + " • Block light: " + "&e" + block.getLightFromBlocks() + "&7"
                + " • Sky light: " + "&e" + block.getLightFromSky() + "&7" + " • Total: "
                + "&e" + block.getLightLevel();
        return new SurveyReport(first, second, third);
    }

    private static void sendReport(Player player, SurveyReport report) {
        message(player, report.first());
        message(player, report.second());
        message(player, report.third());
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
    private static void message(Player player, String value) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }

}
