package io.github.thebusybiscuit.slimefun4.implementation.items.weapons;

import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.ArmorProtectionUtils;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.plugin.Plugin;

/** Applies the radioactive payload shared by the Irradiated Arsenal weapon family. */
public final class IrradiatedWeaponListener implements Listener {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final int SELF_EXPOSURE_PER_HIT = 1;

    private final NamespacedKey projectileWeaponKey;

    private IrradiatedWeaponListener(@Nonnull Plugin plugin) {
        projectileWeaponKey = new NamespacedKey(plugin, "irradiated_projectile_weapon");
    }

    public static void register(@Nonnull Plugin plugin) {
        if (REGISTERED.compareAndSet(false, true)) {
            plugin.getServer().getPluginManager().registerEvents(new IrradiatedWeaponListener(plugin), plugin);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        IrradiatedWeapon weapon = IrradiatedWeapon.getByItem(event.getConsumable());
        if (weapon == null || !weapon.isProjectileAmmunition()) {
            return;
        }

        ItemStack projectileItem = event.getConsumable().clone();
        projectileItem.setAmount(1);
        arrow.setItemStack(projectileItem);
        markProjectile(arrow, weapon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        IrradiatedWeapon weapon = IrradiatedWeapon.getByItem(arrow.getItemStack());
        if (weapon != null) {
            markProjectile(arrow, weapon);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeaponDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        IrradiatedWeapon weapon = null;
        Player attacker = null;
        Entity damager = event.getDamager();

        if (damager instanceof Player player) {
            attacker = player;
            weapon = IrradiatedWeapon.getByItem(player.getInventory().getItemInMainHand());
        } else if (damager instanceof AbstractArrow arrow) {
            String weaponId = arrow.getPersistentDataContainer().get(projectileWeaponKey, PersistentDataType.STRING);
            weapon = IrradiatedWeapon.getById(weaponId);
            ProjectileSource shooter = arrow.getShooter();
            if (shooter instanceof Player player) {
                attacker = player;
            }
        }

        if (weapon == null) {
            return;
        }

        event.setDamage(event.getDamage() + weapon.getBonusDamage());
        showRadiationHit(victim);

        if (victim instanceof Player player) {
            addExposure(player, weapon.getHitExposure());
        }
        if (attacker != null && attacker != victim) {
            addExposure(attacker, SELF_EXPOSURE_PER_HIT);
        }
    }

    private void markProjectile(AbstractArrow arrow, IrradiatedWeapon weapon) {
        arrow.getPersistentDataContainer().set(projectileWeaponKey, PersistentDataType.STRING, weapon.getId());
    }

    private static void addExposure(Player player, int amount) {
        if (amount <= 0
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || ArmorProtectionUtils.hasFullProtectionAgainst(player, ProtectionType.RADIATION)) {
            return;
        }

        int before = RadiationUtils.getExposure(player);
        RadiationUtils.addExposure(player, amount);
        if (before == 0 && RadiationUtils.getExposure(player) > 0) {
            Slimefun.getLocalization().sendMessage(player, "messages.radiation");
        }
    }

    private static void showRadiationHit(LivingEntity victim) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.1F);
        victim.getWorld().spawnParticle(
                Particle.DUST,
                victim.getLocation().add(0.0D, Math.max(0.5D, victim.getHeight() * 0.55D), 0.0D),
                8,
                0.25D,
                0.35D,
                0.25D,
                0.0D,
                dust);
    }
}
