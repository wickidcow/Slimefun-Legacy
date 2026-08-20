package io.github.thebusybiscuit.slimefun4.implementation.items.tools;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * A directional excavation pickaxe that cuts a short rectangular tunnel section in front of the player.
 *
 * <p>The primary broken block still uses normal Minecraft breaking. Additional terrain is filtered through Slimefun's
 * protection manager and never force-loads chunks. Slimefun blocks, custom blocks and tile entities are deliberately
 * left untouched so a large bore cannot wipe machines or storage by accident.</p>
 */
public final class TunnelingPickaxe extends ExplosiveTool {

    private static final int TUNNEL_DEPTH = 3;
    private static final int EXTRA_BLOCKS_PER_DURABILITY = 12;

    private final NamespacedKey modeKey = new NamespacedKey(Slimefun.instance(), "tunnel_borer_mode");
    private final Map<UUID, Long> lastBoreUse = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public TunnelingPickaxe(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public void preRegister() {
        super.preRegister();
        addItemHandler(modeSwitchHandler());
    }

    @Override
    public @Nonnull ToolUseHandler getItemHandler() {
        return (event, tool, fortune, drops) -> {
            Player player = event.getPlayer();
            if (player.isSneaking()) {
                return;
            }

            BoreMode mode = getMode(tool);
            if (!cooldownReady(player, mode)) {
                return;
            }

            Block primary = event.getBlock();
            List<Block> candidates = findTunnelVolume(player, primary, mode);
            int broken = 0;

            for (Block block : candidates) {
                if (!canTunnelBreak(player, block)) {
                    continue;
                }

                VisualEffectUtils.playBlockBreakEffect(block);
                if (block.breakNaturally(tool)) {
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

    private @Nonnull ItemUseHandler modeSwitchHandler() {
        return event -> {
            event.cancel();
            ItemStack tool = event.getItem();
            BoreMode next = getMode(tool).next();
            ItemMeta meta = tool.getItemMeta();
            meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, next.name());
            tool.setItemMeta(meta);

            Player player = event.getPlayer();
            player.sendMessage(ChatColor.GOLD + "Deepcore Tunnel Borer: " + ChatColor.AQUA + next.displayName
                    + ChatColor.GRAY + " bore selected.");
            player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.55F, next.pitch);
        };
    }

    private boolean cooldownReady(Player player, BoreMode mode) {
        long now = System.currentTimeMillis();
        long previous = lastBoreUse.getOrDefault(player.getUniqueId(), 0L);
        if (previous > 0L && now - previous < mode.cooldownMillis) {
            return false;
        }
        lastBoreUse.put(player.getUniqueId(), now);
        return true;
    }

    private List<Block> findTunnelVolume(Player player, Block primary, BoreMode mode) {
        World world = primary.getWorld();
        BlockFace facing = player.getFacing();
        int halfWidth = mode.width / 2;
        int floorY = player.getLocation().getBlockY();
        List<Block> blocks = new ArrayList<>(mode.width * mode.height * TUNNEL_DEPTH - 1);

        for (int depth = 0; depth < TUNNEL_DEPTH; depth++) {
            int baseX = primary.getX() + facing.getModX() * depth;
            int baseZ = primary.getZ() + facing.getModZ() * depth;

            for (int yOffset = 0; yOffset < mode.height; yOffset++) {
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
        if (!canBreak(player, block)) {
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

    private BoreMode getMode(ItemStack tool) {
        ItemMeta meta = tool.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String stored = data.get(modeKey, PersistentDataType.STRING);
        if (stored == null) {
            return BoreMode.SERVICE;
        }
        try {
            return BoreMode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return BoreMode.SERVICE;
        }
    }

    private enum BoreMode {
        SERVICE("3x5", 3, 5, 250L, 1.35F),
        FREIGHT("5x7", 5, 7, 450L, 1.10F),
        SHELTER("9x11", 9, 11, 800L, 0.80F);

        private final String displayName;
        private final int width;
        private final int height;
        private final long cooldownMillis;
        private final float pitch;

        BoreMode(String displayName, int width, int height, long cooldownMillis, float pitch) {
            this.displayName = displayName;
            this.width = width;
            this.height = height;
            this.cooldownMillis = cooldownMillis;
            this.pitch = pitch;
        }

        private BoreMode next() {
            return switch (this) {
                case SERVICE -> FREIGHT;
                case FREIGHT -> SHELTER;
                case SHELTER -> SERVICE;
            };
        }
    }
}
