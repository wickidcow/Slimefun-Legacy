package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A portable bed which behaves like a normal bed without replacing the player's saved respawn point.
 */
public final class TravelersBedroll extends SlimefunItem implements Listener {

    private static final long BEDROLL_ATTEMPT_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final Map<UUID, Long> recentBedrollAttempts = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public TravelersBedroll(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                Block otherHalf = getOtherHalf(event.getBlock());
                if (otherHalf != null && !StorageCacheUtils.isBlock(otherHalf.getLocation(), getId())) {
                    Slimefun.getDatabaseManager()
                            .getBlockDataController()
                            .createBlock(otherHalf.getLocation(), getId());
                }
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(
                    org.bukkit.event.block.BlockBreakEvent event, ItemStack item, java.util.List<ItemStack> drops) {
                removeOtherHalfData(event.getBlock());
            }

            @Override
            public void onExplode(Block block, java.util.List<ItemStack> drops) {
                removeOtherHalfData(block);
            }
        });
    }

    @Override
    public void postRegister() {
        if (!isDisabled()) {
            Bukkit.getPluginManager().registerEvents(this, Slimefun.instance());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (isBedroll(event.getBed())) {
            recentBedrollAttempts.put(event.getPlayer().getUniqueId(), System.nanoTime());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getCause() != PlayerSetSpawnEvent.Cause.BED) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        Long attemptStarted = recentBedrollAttempts.remove(playerId);
        Location spawnLocation = event.getLocation();

        boolean bedrollLocation = spawnLocation != null && isBedroll(spawnLocation.getBlock());
        boolean recentBedrollAttempt = attemptStarted != null
                && System.nanoTime() - attemptStarted <= BEDROLL_ATTEMPT_WINDOW_NANOS;

        if (bedrollLocation || recentBedrollAttempt) {
            event.setNotifyPlayer(false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        recentBedrollAttempts.remove(event.getPlayer().getUniqueId());
    }

    private boolean isBedroll(Block block) {
        if (StorageCacheUtils.isBlock(block.getLocation(), getId())) {
            return true;
        }

        Block otherHalf = getOtherHalf(block);
        return otherHalf != null && StorageCacheUtils.isBlock(otherHalf.getLocation(), getId());
    }

    private void removeOtherHalfData(Block block) {
        Block otherHalf = getOtherHalf(block);
        if (otherHalf != null && StorageCacheUtils.isBlock(otherHalf.getLocation(), getId())) {
            Slimefun.getDatabaseManager().getBlockDataController().removeBlock(otherHalf.getLocation());
        }
    }

    private Block getOtherHalf(Block block) {
        if (!(block.getBlockData() instanceof Bed bed)) {
            return null;
        }

        return bed.getPart() == Bed.Part.FOOT
                ? block.getRelative(bed.getFacing())
                : block.getRelative(bed.getFacing().getOppositeFace());
    }
}
