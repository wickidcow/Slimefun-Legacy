package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Legacy registration for EnderPanda's orphaned SHULKER_HELMET.
 *
 * Reuses the exact Slimefun ID and original five-shulker-shell recipe so
 * existing item stacks can be resolved again. This is not a migration:
 * no player item data is rewritten.
 */
public final class LegacyShulkerHelmet {
    private static final String ID = "SHULKER_HELMET";

    private LegacyShulkerHelmet() {}

    public static void register(Slimefun plugin, ItemGroup group) {
        // Avoid duplicate IDs when a maintained EnderPanda addon registers first.
        if (SlimefunItem.getById(ID) != null) {
            return;
        }

        SlimefunItemStack helmet = new SlimefunItemStack(
                ID,
                Material.IRON_HELMET,
                "&5Shulker Helmet",
                "",
                "&7Immune to Levitation while worn");
        helmet.addUnsafeEnchantment(Enchantment.RESPIRATION, 1);
        helmet.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        helmet.addUnsafeEnchantment(Enchantment.PROTECTION, 3);

        ItemStack shell = new ItemStack(Material.SHULKER_SHELL);
        new SlimefunItem(group, helmet, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[] {
                shell, shell, shell,
                shell, null, shell,
                null, null, null
        }).register(plugin);

        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            private boolean wearing(Player player) {
                SlimefunItem item = SlimefunItem.getByItem(player.getInventory().getHelmet());
                return item != null && ID.equals(item.getId());
            }

            @EventHandler(ignoreCancelled = true)
            public void onShulkerBullet(EntityDamageByEntityEvent event) {
                if (event.getDamager().getType() == EntityType.SHULKER_BULLET
                        && event.getEntity() instanceof Player player
                        && wearing(player)) {
                    event.setCancelled(true);
                }
            }

            @EventHandler(ignoreCancelled = true)
            public void onLevitation(EntityPotionEffectEvent event) {
                if (event.getEntity() instanceof Player player
                        && wearing(player)
                        && event.getNewEffect() != null
                        && event.getNewEffect().getType().equals(PotionEffectType.LEVITATION)) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
    }
}
