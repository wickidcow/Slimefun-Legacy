package io.github.thebusybiscuit.slimefun4.implementation.items.tools;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ToolUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A fixed-size directional excavation tool used by the Deepcore tunnel tool family.
 *
 * <p>The tunnel face is anchored to the player's feet and extends upward. Each use
 * reaches three blocks forward from the mined block. Additional terrain is filtered
 * through Slimefun protection checks and never force-loads chunks. Slimefun blocks,
 * custom blocks and tile entities are deliberately left untouched.</p>
 */
public final class DeepcoreTunnelTool extends ExplosiveTool {

    private static final int TUNNEL_DEPTH = 3;
    private static final int EXTRA_BLOCKS_PER_DURABILITY = 12;
    private static final Map<UUID, Long> LAST_BORE_USE = new ConcurrentHashMap<>();

    private final int size;
    private final ExcavationType excavationType;
    private final long cooldownMillis;

    @ParametersAreNonnullByDefault
    public DeepcoreTunnelTool(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            int size,
            ExcavationType excavationType) {
        super(itemGroup, item, recipeType, recipe);

        if (size != 3 && size != 5 && size != 9) {
            throw new IllegalArgumentException("Deepcore tunnel size must be 3, 5 or 9");
        }

        this.size = size;
        this.excavationType = excavationType;
        this.cooldownMillis = switch (size) {
            case 3 -> 250L;
            case 5 -> 450L;
            case 9 -> 800L;
            default -> throw new IllegalStateException("Unexpected Deepcore tunnel size: " + size);
        };
    }

    @Override
    public @Nonnull ToolUseHandler getItemHandler() {
        return (event, tool, fortune, drops) -> {
            Player player = event.getPlayer();
            if (player.isSneaking()) {
                return;
            }

            Block primary = event.getBlock();
            if (!excavationType.canExcavate(primary.getType()) || !cooldownReady(player)) {
                return;
            }

            List<Block> candidates = findTunnelVolume(player, primary);
            int broken = 0;

            for (Block block : candidates) {
                if (!canTunnelBreak(player, block)) {
                    continue;
                }

                VisualEffectUtils.playBlockBreakEffect(block);
                if (block.breakNaturally(effectiveToolFor(block, tool))) {
                    broken++;
                }
            }

            int extraDamage = (broken + EXTRA_BLOCKS_PER_DURABILITY - 1) / EXTRA_BLOCKS_PER_DURABILITY;
            for (int i = 0; i < extraDamage; i++) {
                damageItem(player, tool);
                if (tool.getAmount() <= 0) {
                    break;
                }
            }
        };
    }

    private boolean cooldownReady(Player player) {
        long now = System.currentTimeMillis();
        long previous = LAST_BORE_USE.getOrDefault(player.getUniqueId(), 0L);
        if (previous > 0L && now - previous < cooldownMillis) {
            return false;
        }

        LAST_BORE_USE.put(player.getUniqueId(), now);
        return true;
    }

    private List<Block> findTunnelVolume(Player player, Block primary) {
        World world = primary.getWorld();
        BlockFace facing = player.getFacing();
        int halfWidth = size / 2;
        int floorY = player.getLocation().getBlockY();
        List<Block> blocks = new ArrayList<>(size * size * TUNNEL_DEPTH - 1);

        for (int depth = 0; depth < TUNNEL_DEPTH; depth++) {
            int baseX = primary.getX() + facing.getModX() * depth;
            int baseZ = primary.getZ() + facing.getModZ() * depth;

            for (int yOffset = 0; yOffset < size; yOffset++) {
                int y = floorY + yOffset;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    continue;
                }

                for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
                    int x = baseX;
                    int z = baseZ;

                    if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
                        x += lateral;
                    } else {
                        z += lateral;
                    }

                    if (x == primary.getX() && y == primary.getY() && z == primary.getZ()) {
                        continue;
                    }

                    int chunkX = Math.floorDiv(x, 16);
                    int chunkZ = Math.floorDiv(z, 16);
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }

                    Location location = new Location(world, x, y, z);
                    if (Slimefun.getSchedulerService().isFolia()
                            && !Slimefun.getSchedulerService().isOwnedByCurrentRegion(location)) {
                        continue;
                    }

                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }

        return blocks;
    }

    private boolean canTunnelBreak(Player player, Block block) {
        if (!excavationType.canExcavate(block.getType()) || !canBreak(player, block)) {
            return false;
        }
        if (block.getState() instanceof TileState) {
            return false;
        }
        if (StorageCacheUtils.getSlimefunItem(block.getLocation()) != null) {
            return false;
        }
        return !Slimefun.getIntegrations().isCustomBlock(block);
    }

    private ItemStack effectiveToolFor(Block block, ItemStack tool) {
        if (excavationType == ExcavationType.PAXEL
                && Tag.MINEABLE_SHOVEL.isTagged(block.getType())
                && !Tag.MINEABLE_PICKAXE.isTagged(block.getType())) {
            ItemStack shovel = tool.clone();
            shovel.setType(Material.NETHERITE_SHOVEL);
            return shovel;
        }

        return tool;
    }

    public enum ExcavationType {
        PICKAXE,
        SHOVEL,
        PAXEL;

        private boolean canExcavate(Material material) {
            boolean pickaxe = Tag.MINEABLE_PICKAXE.isTagged(material);
            boolean shovel = Tag.MINEABLE_SHOVEL.isTagged(material);

            return switch (this) {
                case PICKAXE -> pickaxe;
                case SHOVEL -> shovel;
                case PAXEL -> pickaxe || shovel;
            };
        }
    }
}
