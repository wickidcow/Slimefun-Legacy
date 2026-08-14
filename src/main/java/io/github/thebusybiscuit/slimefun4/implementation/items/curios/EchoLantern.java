package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Sends out a short spectral pulse that reveals nearby hostile mobs.
 */
public final class EchoLantern extends SimpleSlimefunItem<ItemUseHandler> {

    private static final int RANGE = 20;
    private static final int GLOW_TICKS = 8 * 20;
    private static final int COOLDOWN_TICKS = 30 * 20;

    @ParametersAreNonnullByDefault
    public EchoLantern(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();

            Player player = event.getPlayer();
            if (player.hasCooldown(Material.SOUL_LANTERN)) {
                return;
            }

            int revealed = 0;
            PotionEffect glow = new PotionEffect(PotionEffectType.GLOWING, GLOW_TICKS, 0, false, false, false);

            for (Entity entity : player.getNearbyEntities(RANGE, RANGE, RANGE)) {
                if (entity instanceof Monster monster && monster.isValid() && !monster.isDead()) {
                    monster.addPotionEffect(glow);
                    revealed++;
                }
            }

            player.setCooldown(Material.SOUL_LANTERN, COOLDOWN_TICKS);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.65F, 1.55F);
            player.sendMessage(ChatColor.AQUA + "Echo Lantern pulse: " + ChatColor.WHITE + revealed
                    + ChatColor.GRAY + (revealed == 1 ? " hostile revealed." : " hostiles revealed."));
        };
    }
}
