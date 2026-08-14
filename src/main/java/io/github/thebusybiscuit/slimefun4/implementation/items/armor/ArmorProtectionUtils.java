package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectiveArmor;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves passive armor protection from the armor a player is wearing right now.
 *
 * <p>The regular armor task intentionally caches armor for periodic potion-effect work.
 * Instantaneous damage and radiation checks must not rely on that cache because the
 * configured armor refresh interval can be several seconds.</p>
 */
public final class ArmorProtectionUtils {

    private ArmorProtectionUtils() {}

    public static boolean hasFullProtectionAgainst(@Nonnull Player player, @Nonnull ProtectionType type) {
        Validate.notNull(player, "Player must not be null.");
        Validate.notNull(type, "ProtectionType must not be null.");

        int armorCount = 0;
        NamespacedKey setId = null;

        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            SlimefunItem item = SlimefunItem.getByItem(stack);
            if (!(item instanceof SlimefunArmorPiece) || !(item instanceof ProtectiveArmor protectiveArmor)) {
                continue;
            }

            for (ProtectionType protectionType : protectiveArmor.getProtectionTypes()) {
                if (protectionType != type) {
                    continue;
                }

                if (!protectiveArmor.isFullSetRequired()) {
                    return true;
                }

                NamespacedKey armorSetId = protectiveArmor.getArmorSetId();
                if (setId == null || setId.equals(armorSetId)) {
                    armorCount++;
                    setId = armorSetId;
                }

                break;
            }
        }

        return armorCount == 4;
    }
}
