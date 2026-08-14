package io.github.thebusybiscuit.slimefun4.implementation.tasks.armor;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.RainbowArmorPiece;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * The {@link RainbowArmorTask} is responsible for handling the change in color of any Rainbow Armor piece.
 *
 * @author martinbrom
 */
public class RainbowArmorTask extends AbstractArmorTask {

    private long currentColorIndex = 0;

    @Override
    protected void onTick() {
        currentColorIndex++;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void onPlayerTick(Player p, PlayerProfile profile) {
        for (ItemStack item : p.getInventory().getArmorContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }

            SlimefunItem currentItem = SlimefunItem.getByItem(item);
            if (currentItem instanceof RainbowArmorPiece rainbowArmorPiece && rainbowArmorPiece.canUse(p, true)) {
                updateRainbowArmor(item, rainbowArmorPiece);
            }
        }
    }

    @ParametersAreNonnullByDefault
    private void updateRainbowArmor(ItemStack itemStack, RainbowArmorPiece armorPiece) {
        Color[] colors = armorPiece.getColors();
        Color newColor = colors[(int) (currentColorIndex % colors.length)];

        if (itemStack.getItemMeta() instanceof LeatherArmorMeta leatherArmorMeta) {
            leatherArmorMeta.setColor(newColor);
            itemStack.setItemMeta(leatherArmorMeta);
        }
    }
}
