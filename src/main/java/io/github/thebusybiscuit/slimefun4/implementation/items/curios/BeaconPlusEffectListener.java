package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Event-driven Resonance Beacon powers that do not belong in the periodic block pulse. */
final class BeaconPlusEffectListener implements Listener {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final long IMMORTALITY_COOLDOWN_MILLIS = 60_000L;
    private static final Map<UUID, Long> IMMORTALITY_COOLDOWNS = new ConcurrentHashMap<>();

    private BeaconPlusEffectListener() {}

    static void register(Plugin plugin) {
        if (REGISTERED.compareAndSet(false, true)) {
            plugin.getServer().getPluginManager().registerEvents(new BeaconPlusEffectListener(), plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) {
            return;
        }

        int tier = BeaconPlusRuntime.getTierForEffect(
                event.getPlayer().getLocation(), BeaconPlusEffect.EXPERIENCE_BOOSTER);
        if (tier > 0) {
            event.setAmount(event.getAmount() * (tier + 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCooldown(PlayerItemCooldownEvent event) {
        int tier = BeaconPlusRuntime.getTierForEffect(
                event.getPlayer().getLocation(), BeaconPlusEffect.COOLDOWN_REDUCTION);
        if (tier <= 0 || event.getCooldown() <= 1) {
            return;
        }

        double multiplier =
                switch (tier) {
                    case 1 -> 0.60D;
                    case 2 -> 0.40D;
                    default -> 0.25D;
                };
        event.setCooldown(Math.max(1, (int) Math.ceil(event.getCooldown() * multiplier)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null || !BeaconPlusRuntime.hasEffect(target.getLocation(), BeaconPlusEffect.PEACEFUL)) {
            return;
        }
        if (!(event.getEntity() instanceof Monster monster)) {
            return;
        }

        event.setCancelled(true);
        monster.setTarget(null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (!BeaconPlusRuntime.hasEffect(victim.getLocation(), BeaconPlusEffect.PEACEFUL)) {
            return;
        }

        Entity damager = event.getDamager();
        boolean hostile = damager instanceof Monster;
        if (!hostile && damager instanceof Projectile projectile) {
            hostile = projectile.getShooter() instanceof Monster;
        }
        if (hostile) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0.0D) {
            return;
        }

        int tier = BeaconPlusRuntime.getTierForEffect(player.getLocation(), BeaconPlusEffect.IMMORTALITY_FIELD);
        if (tier <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = IMMORTALITY_COOLDOWNS.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            return;
        }

        double chance =
                switch (tier) {
                    case 1 -> 0.25D;
                    case 2 -> 0.40D;
                    default -> 0.55D;
                };
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        IMMORTALITY_COOLDOWNS.put(player.getUniqueId(), now + IMMORTALITY_COOLDOWN_MILLIS);
        event.setCancelled(true);
        player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0D, player.getHealth())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null
                || (from.getWorld() == to.getWorld()
                        && from.getBlockX() == to.getBlockX()
                        && from.getBlockY() == to.getBlockY()
                        && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        BeaconPlusRuntime.refreshPlayerState(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        IMMORTALITY_COOLDOWNS.remove(event.getPlayer().getUniqueId());
        BeaconPlusRuntime.clearPlayerState(event.getPlayer());
    }
}
