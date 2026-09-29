package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import javax.annotation.Nonnull;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class VersionedMiddleClickListener implements Listener {

    public VersionedMiddleClickListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onMiddleClick(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();

        // There is no abilities API here, so creative mode remains the supported pick-block path.
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlock();
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(block.getLocation());
        if (sfItem == null) {
            return;
        }

        int targetSlot = event.getTargetSlot();
        if (targetSlot < 0 || targetSlot >= 9) {
            return;
        }

        event.setCancelled(true);

        // Prefer an existing hotbar copy instead of creating another item.
        for (int slot = 0; slot < 9; slot++) {
            ItemStack hotbarItem = player.getInventory().getItem(slot);
            if (hotbarItem != null && !hotbarItem.getType().isAir() && sfItem.isItem(hotbarItem)) {
                player.getInventory().setHeldItemSlot(slot);
                return;
            }
        }

        player.getInventory().setHeldItemSlot(targetSlot);
        player.getInventory().setItemInMainHand(sfItem.getItem().clone());
    }
}
