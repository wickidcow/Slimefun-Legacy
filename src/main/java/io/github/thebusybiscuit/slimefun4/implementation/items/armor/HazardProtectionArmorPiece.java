package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectiveArmor;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * A full-set protective armor piece for hazardous environments.
 *
 * <p>This is used by the Advanced Hazmat Gear and Netherite Containment Armor sets so each set
 * keeps an independent armor-set identity while providing Slimefun radiation protection.
 */
public final class HazardProtectionArmorPiece extends SlimefunArmorPiece implements ProtectiveArmor {

    private static final ProtectionType[] PROTECTION_TYPES = {
        ProtectionType.BEES, ProtectionType.RADIATION
    };

    private final NamespacedKey armorSetId;

    @ParametersAreNonnullByDefault
    public HazardProtectionArmorPiece(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            PotionEffect[] effects,
            String armorSetId) {
        super(itemGroup, item, recipeType, recipe, effects);
        this.armorSetId = new NamespacedKey(Slimefun.instance(), armorSetId);
    }

    @Override
    public @Nonnull ProtectionType[] getProtectionTypes() {
        return PROTECTION_TYPES.clone();
    }

    @Override
    public boolean isFullSetRequired() {
        return true;
    }

    @Override
    public @Nonnull NamespacedKey getArmorSetId() {
        return armorSetId;
    }
}
