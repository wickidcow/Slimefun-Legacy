package io.github.thebusybiscuit.slimefun4.implementation.items.weapons;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.HeldRadioactive;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactivity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/** A late-game weapon infused with blistering alloy and a controlled radioactive payload. */
public final class IrradiatedWeapon extends SlimefunItem implements HeldRadioactive, NotPlaceable {

    private static final Map<String, IrradiatedWeapon> WEAPONS = new ConcurrentHashMap<>();

    private final int hitExposure;
    private final double bonusDamage;
    private final boolean projectileAmmunition;

    @ParametersAreNonnullByDefault
    public IrradiatedWeapon(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            int hitExposure,
            double bonusDamage,
            boolean projectileAmmunition) {
        this(itemGroup, item, recipeType, recipe, null, hitExposure, bonusDamage, projectileAmmunition);
    }

    @ParametersAreNonnullByDefault
    public IrradiatedWeapon(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            @Nullable ItemStack recipeOutput,
            int hitExposure,
            double bonusDamage,
            boolean projectileAmmunition) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
        this.hitExposure = Math.max(1, hitExposure);
        this.bonusDamage = Math.max(0.0D, bonusDamage);
        this.projectileAmmunition = projectileAmmunition;
        WEAPONS.put(getId(), this);
    }

    public int getHitExposure() {
        return hitExposure;
    }

    public double getBonusDamage() {
        return bonusDamage;
    }

    public boolean isProjectileAmmunition() {
        return projectileAmmunition;
    }

    @Override
    public @Nonnull Radioactivity getRadioactivity() {
        return Radioactivity.LOW;
    }

    @Override
    public long getHeldExposureIntervalMillis() {
        return 10_000L;
    }

    public static @Nullable IrradiatedWeapon getById(@Nullable String id) {
        return id == null ? null : WEAPONS.get(id);
    }

    public static @Nullable IrradiatedWeapon getByItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        return sfItem instanceof IrradiatedWeapon irradiated ? irradiated : null;
    }
}
