package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A configurable expedition beacon with bounded chunk loading and selectable player support.
 *
 * <p>Beacon Plus intentionally does not tick Networks or machines itself. Chunk tickets keep the selected chunks
 * resident and the normal Slimefun/addon runtimes remain responsible for all machine and network behavior.
 */
public final class BeaconPlus extends SlimefunItem {

    private static final int SUPPORT_RADIUS = 24;
    private static final long SUPPORT_REFRESH_MILLIS = 2_000L;
    private static final int SUPPORT_DURATION_TICKS = 70;
    private static final int NIGHT_VISION_DURATION_TICKS = 240;

    private final Map<String, Long> lastSupportPulse = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public BeaconPlus(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onPlace(), onUse(), onBreak(), createTicker());
    }

    @Override
    public void postRegister() {
        if (isDisabled()) {
            return;
        }

        BeaconPlusLifecycleListener.register(Slimefun.instance());
        Slimefun.getSchedulerService().runLater(() -> {
            if (BeaconPlusManager.getInstance() == null) {
                BeaconPlusManager.start(Slimefun.instance());
            }
        }, 1L);
    }

    private @Nonnull BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                Location location = event.getBlockPlaced().getLocation();
                UUID owner = event.getPlayer().getUniqueId();

                StorageCacheUtils.setData(location, BeaconPlusManager.OWNER_KEY, owner.toString());
                StorageCacheUtils.setData(location, BeaconPlusManager.CHUNK_MODE_KEY, BeaconPlusChunkMode.OFF.name());
                StorageCacheUtils.setData(location, BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());

                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.register(location, owner);
                }

                event.getPlayer().sendMessage(ChatColor.GOLD + "Beacon Plus placed. " + ChatColor.GRAY
                        + "Right click to choose support; sneak-right click to choose chunk loading.");
            }
        };
    }

    private @Nonnull BlockUseHandler onUse() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            Block block = event.getClickedBlock().orElse(null);
            if (block == null) {
                return;
            }

            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager == null) {
                player.sendMessage(ChatColor.RED + "Beacon Plus is not ready yet.");
                return;
            }

            UUID owner = manager.getOwner(block.getLocation());
            if (owner != null && !owner.equals(player.getUniqueId()) && !player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only this Beacon Plus owner can change its modes.");
                return;
            }

            BeaconPlusChunkMode chunkMode = manager.getChunkMode(block.getLocation());
            BeaconPlusSupportMode supportMode = manager.getSupportMode(block.getLocation());

            boolean updated;
            if (player.isSneaking()) {
                BeaconPlusChunkMode next = chunkMode.next();
                updated = manager.updateModes(block.getLocation(), ownerOrPlayer(owner, player), next, supportMode);
                if (updated) {
                    chunkMode = next;
                    player.sendMessage(ChatColor.AQUA + "Beacon Plus chunk loading: " + ChatColor.WHITE
                            + chunkMode.getDisplayName());
                }
            } else {
                BeaconPlusSupportMode next = supportMode.next();
                updated = manager.updateModes(block.getLocation(), ownerOrPlayer(owner, player), chunkMode, next);
                if (updated) {
                    supportMode = next;
                    player.sendMessage(ChatColor.GREEN + "Beacon Plus support: " + ChatColor.WHITE
                            + supportMode.getDisplayName());
                }
            }

            if (!updated) {
                player.sendMessage(ChatColor.RED
                        + "Beacon Plus could not enable that chunk profile because the server safety cap was reached.");
                return;
            }

            player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7F, 1.25F);
            sendStatus(player, manager, chunkMode, supportMode);
        };
    }

    private @Nonnull SimpleBlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block block) {
                lastSupportPulse.remove(locationKey(block.getLocation()));
                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.unregister(block.getLocation());
                }
            }
        };
    }

    private @Nonnull BlockTicker createTicker() {
        return new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block block, SlimefunItem item, ASlimefunDataContainer data) {
                BeaconPlusSupportMode mode =
                        BeaconPlusSupportMode.fromStored(data.getData(BeaconPlusManager.SUPPORT_MODE_KEY));
                PotionEffectType effectType = mode.getEffectType();
                if (effectType == null) {
                    return;
                }

                long now = System.currentTimeMillis();
                String key = locationKey(block.getLocation());
                Long previous = lastSupportPulse.putIfAbsent(key, now);
                if (previous != null && now - previous < SUPPORT_REFRESH_MILLIS) {
                    return;
                }
                lastSupportPulse.put(key, now);

                applySupport(block, mode, effectType);
            }
        };
    }

    private void applySupport(Block block, BeaconPlusSupportMode mode, PotionEffectType effectType) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        int duration = mode == BeaconPlusSupportMode.NIGHT_VISION
                ? NIGHT_VISION_DURATION_TICKS
                : SUPPORT_DURATION_TICKS;
        PotionEffect effect = new PotionEffect(effectType, duration, 0, true, false, true);

        if (Slimefun.getSchedulerService().isFolia()) {
            // Keep Folia entity access inside the beacon's owning chunk/region.
            for (Entity entity : block.getChunk().getEntities()) {
                if (entity instanceof Player player
                        && player.getLocation().distanceSquared(center) <= SUPPORT_RADIUS * SUPPORT_RADIUS) {
                    player.addPotionEffect(effect);
                }
            }
            return;
        }

        for (Entity entity : block.getWorld().getNearbyEntities(center, SUPPORT_RADIUS, SUPPORT_RADIUS, SUPPORT_RADIUS)) {
            if (entity instanceof Player player) {
                player.addPotionEffect(effect);
            }
        }
    }

    private static UUID ownerOrPlayer(UUID owner, Player player) {
        return owner == null ? player.getUniqueId() : owner;
    }

    private static void sendStatus(
            Player player,
            BeaconPlusManager manager,
            BeaconPlusChunkMode chunkMode,
            BeaconPlusSupportMode supportMode) {
        player.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.AQUA + chunkMode.getDisplayName() + ChatColor.DARK_GRAY
                + " | " + ChatColor.GREEN + supportMode.getDisplayName() + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY
                + manager.getLoadedChunkCount() + " Curios-loaded chunks server-wide");
    }

    private static String locationKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":"
                + location.getBlockZ();
    }
}
