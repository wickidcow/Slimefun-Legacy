package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.events.SlimefunBlockPlaceEvent;
import io.github.thebusybiscuit.slimefun4.core.limiting.ChunkMachineLimitPolicy;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * HeadLimiter-style placement admission for Slimefun machines.
 *
 * <p>Checks the existing core ticker registrations for the player's target
 * chunk. That registry includes normal Slimefun and addon BlockTicker blocks,
 * including registered UniversalBlock tickers. The check runs only during a
 * Slimefun placement event, not for every machine tick or chunk scan.</p>
 *
 * <p>Placement is cancelled before BlockListener creates Slimefun block data,
 * writes an inventory, or runs the placed item's handler. Existing machines
 * are never removed or paused; vanilla/decorative blocks are not counted.</p>
 */
public final class ChunkMachineLimiter implements Listener {

    private static final String ENABLED = "machine-limiter.enabled";
    private static final String MAX_MACHINES = "machine-limiter.max-ticking-machines-per-chunk";
    private static final String ALLOW_BYPASS = "machine-limiter.allow-bypass-permission";
    private static final String BYPASS_PERMISSION = "slimefun.machine-limiter.bypass";

    public ChunkMachineLimiter(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSlimefunBlockPlace(@Nonnull SlimefunBlockPlaceEvent event) {
        // Disabled by default. Avoid a ticker lookup or chunk access entirely
        // unless the administrator explicitly enabled the limiter.
        if (!Slimefun.getCfg().getBoolean(ENABLED)) {
            return;
        }

        // Limiting actual ticker registrations is much closer to a machine
        // quota than counting guide items, cables and decorative materials.
        if (!Slimefun.getRegistry().getTickerBlocks().contains(event.getSlimefunItem().getId())) {
            return;
        }

        Player player = event.getPlayer();
        boolean bypass = Slimefun.getCfg().getBoolean(ALLOW_BYPASS) && player.hasPermission(BYPASS_PERMISSION);
        if (bypass) {
            return;
        }

        // A real player placement is in an already loaded chunk. This call
        // snapshots only that chunk's concurrent ticker registration count:
        // no database/world scan and no per-tick work.
        Chunk chunk = event.getBlockPlaced().getChunk();
        int existing = Slimefun.getTickerTask().getTickLocations(chunk).size();
        int maximum = ChunkMachineLimitPolicy.effectiveLimit(Slimefun.getCfg().getInt(MAX_MACHINES));

        if (ChunkMachineLimitPolicy.rejects(true, true, false, existing, maximum)) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "This chunk has " + existing + " ticking Slimefun machines (limit " + maximum
                            + "). Remove a machine or build in another chunk.",
                    NamedTextColor.RED));
        }
    }
}
