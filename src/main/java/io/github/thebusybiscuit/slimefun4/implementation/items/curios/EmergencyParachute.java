package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * A reusable emergency fall saver with no repeating task.
 *
 * <p>The parachute activates from the player's carried inventory when a dangerous
 * or lethal fall would land. Small falls are intentionally ignored so the safety
 * cooldown is not wasted.</p>
 */
public final class EmergencyParachute extends SlimefunItem implements Listener {

    private static final double DANGEROUS_FALL_DAMAGE = 6.0D;
    private static final int DEPLOY_COOLDOWN_TICKS = 20 * 60;

    @ParametersAreNonnullByDefault
    public EmergencyParachute(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    public void registerListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage < DANGEROUS_FALL_DAMAGE && finalDamage < player.getHealth()) {
            return;
        }

        ItemStack parachute = findCarriedParachute(player);
        if (parachute == null || player.hasCooldown(Material.PHANTOM_MEMBRANE)) {
            return;
        }

        event.setCancelled(true);
        player.setFallDistance(0.0F);
        player.setCooldown(Material.PHANTOM_MEMBRANE, DEPLOY_COOLDOWN_TICKS);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0F, 1.45F);
        player.sendMessage(ChatColor.AQUA + "Emergency Parachute " + ChatColor.GRAY
                + "• Deployed just before impact! Fall damage prevented.");
    }

    private ItemStack findCarriedParachute(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isItem(offHand)) {
            return offHand;
        }

        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isItem(stack)) {
                return stack;
            }
        }

        return null;
    }
}
