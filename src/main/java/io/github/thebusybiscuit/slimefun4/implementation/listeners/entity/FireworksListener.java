package io.github.thebusybiscuit.slimefun4.implementation.listeners.entity;

import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;

/**
 * This {@link Listener} makes sure that any {@link Firework} caused by a {@link Player}
 * unlocking a {@link Research} does not cause damage to be dealt.
 *
 * @author TheBusyBiscuit
 *
 */
public class FireworksListener implements Listener {

    public FireworksListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onResearchFireworkDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework firework) {
            FireworkMeta meta = firework.getFireworkMeta();

            /*
            We could use Peristent Data for this in the future, but ItemMeta display names
            work pretty reliably too and they don't cause any memory leaks like metadata.

            Entity display names do not work either as Firework cannot be named.
            */
            Component expected = Component.text("Slimefun Research", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false);
            if (expected.equals(meta.displayName())) {
                e.setCancelled(true);
            }
        }
    }
}
