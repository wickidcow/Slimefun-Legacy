package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.attributes.DamageableItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.ArmorProtectionUtils;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.ElytraCap;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.SlimefunArmorPiece;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link Listener} for the {@link ElytraCap}.
 *
 * @author Seggan
 * @author J3fftw1
 *
 * @see ElytraCap
 */
public class ElytraImpactListener implements Listener {

    private final Set<UUID> gliding = ConcurrentHashMap.newKeySet();

    public ElytraImpactListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        gliding.add(uuid);

        if (!event.isGliding()) {
            // Keep a one-tick grace period because impact damage can be fired in the same tick as gliding stops.
            Slimefun.getSchedulerService()
                    .runForLater(player, () -> gliding.remove(uuid), () -> gliding.remove(uuid), 1L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gliding.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerCrash(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            // We only wanna handle damaged Players
            return;
        }

        if ((e.getCause() == DamageCause.FALL || e.getCause() == DamageCause.FLY_INTO_WALL)
                && (p.isGliding() || gliding.contains(p.getUniqueId()))) {
            if (PlayerProfile.find(p).isEmpty()) {
                PlayerProfile.request(p);
                return;
            }

            ItemStack helmetStack = p.getInventory().getHelmet();
            SlimefunItem helmetItem = SlimefunItem.getByItem(helmetStack);

            if (helmetItem instanceof SlimefunArmorPiece helmet
                    && helmet.canUse(p, true)
                    && ArmorProtectionUtils.hasFullProtectionAgainst(p, ProtectionType.FLYING_INTO_WALL)) {
                SoundEffect.ELYTRA_CAP_IMPACT_SOUND.playFor(p);
                e.setCancelled(true);

                if (helmet instanceof DamageableItem damageableItem) {
                    damageableItem.damageItem(p, helmetStack);
                }
            }
        }
    }
}
