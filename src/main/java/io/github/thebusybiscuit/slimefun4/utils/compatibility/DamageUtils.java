package io.github.thebusybiscuit.slimefun4.utils.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Utilities for applying attributed damage through the supported Paper/Bukkit damage API.
 *
 * <p>Plugins must not construct {@link EntityDamageByEntityEvent} directly. Paper marks those constructors as
 * internal and subject to removal. Calling {@link LivingEntity#damage(double, DamageSource)} lets the server create
 * and dispatch the correct event while preserving the direct and causing entities.
 */
@SlimefunInternal
public final class DamageUtils {

    private DamageUtils() {}

    /**
     * Applies damage with an explicit modern {@link DamageSource}.
     *
     * @param target the entity receiving damage
     * @param amount the requested damage amount
     * @param damageType the vanilla damage type to report
     * @param directEntity the entity that directly inflicted the damage
     * @param causingEntity the entity ultimately responsible for the damage, or {@code null}
     * @return whether the server accepted a new, non-cancelled damage event from the supplied direct entity
     */
    public static boolean damage(
            @Nonnull LivingEntity target,
            double amount,
            @Nonnull DamageType damageType,
            @Nonnull Entity directEntity,
            @Nullable Entity causingEntity) {
        EntityDamageEvent previousEvent = target.getLastDamageCause();

        DamageSource.Builder source = DamageSource.builder(damageType).withDirectEntity(directEntity);
        if (causingEntity != null) {
            source.withCausingEntity(causingEntity);
        }

        target.damage(amount, source.build());

        EntityDamageEvent currentEvent = target.getLastDamageCause();
        if (currentEvent == null || currentEvent == previousEvent || currentEvent.isCancelled()) {
            return false;
        }

        return !(currentEvent instanceof EntityDamageByEntityEvent byEntity)
                || byEntity.getDamager().getUniqueId().equals(directEntity.getUniqueId());
    }
}
