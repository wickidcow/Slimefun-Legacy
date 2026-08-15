package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectiveArmor;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * Lead-lined upgrade of the classic Hazmat Suit.
 *
 * <p>The full four-piece set participates in Slimefun's native radiation and bee protection checks while retaining
 * the individual helmet/chestplate utility effects supplied by the registration recipe.
 */
public final class AdvancedHazmatArmorPiece extends SlimefunArmorPiece implements ProtectiveArmor {

    private static final ProtectionType[] PROTECTION_TYPES = {ProtectionType.BEES, ProtectionType.RADIATION};

    private final NamespacedKey armorSetId;

    @ParametersAreNonnullByDefault
    public AdvancedHazmatArmorPiece(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            PotionEffect[] effects) {
        super(itemGroup, item, recipeType, recipe, effects);
        armorSetId = new NamespacedKey(Slimefun.instance(), "advanced_hazmat_suit");
    }

    @Override
    public ProtectionType[] getProtectionTypes() {
        return PROTECTION_TYPES.clone();
    }

    @Override
    public boolean isFullSetRequired() {
        return true;
    }

    @Override
    public NamespacedKey getArmorSetId() {
        return armorSetId;
    }
}
