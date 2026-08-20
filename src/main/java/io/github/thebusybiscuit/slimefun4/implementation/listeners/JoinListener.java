package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.HashedArmorpiece;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.SlimefunArmorPiece;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.RadiationTask;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link Listener} caches the armor of the player on join.
 * This is mainly for the {@link RadiationTask}.
 *
 * @author iTwins
 */
public class JoinListener implements Listener {

    public JoinListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(@Nonnull PlayerJoinEvent e) {
        final var p = e.getPlayer();

        PlayerProfile.get(p, playerProfile -> {
            final ItemStack[] armorContents = p.getInventory().getArmorContents();
            final HashedArmorpiece[] hashedArmorpieces = playerProfile.getArmor();
            for (int i = 0; i < 4; i++) {
                final ItemStack armorPiece = armorContents[i];
                if (armorPiece != null
                        && armorPiece.getType() != Material.AIR
                        && SlimefunItem.getByItem(armorPiece) instanceof SlimefunArmorPiece sfArmorPiece) {
                    hashedArmorpieces[i].update(armorPiece, sfArmorPiece);
                }
            }
        });

        Slimefun.getGitHubService().notifyUpdateIfAvailable(p);
    }
}
