package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/** A deliberately inefficient field repair tool that consumes normal repair materials. */
public final class FieldRepairKit extends SimpleSlimefunItem<ItemUseHandler> {

    @ParametersAreNonnullByDefault
    public FieldRepairKit(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack target = player.getInventory().getItemInOffHand();

            if (target.getType().isAir()) {
                player.sendMessage(ChatColor.YELLOW + "Put the damaged item in your off-hand, then use the Field Repair Kit.");
                return;
            }

            ItemMeta meta = target.getItemMeta();
            if (!(meta instanceof Damageable damageable)) {
                player.sendMessage(ChatColor.RED + "That item cannot be repaired by the Field Repair Kit.");
                return;
            }

            int damage = damageable.getDamage();
            int maxDurability = target.getType().getMaxDurability();
            if (maxDurability <= 0 || damage <= 0) {
                player.sendMessage(ChatColor.GRAY + "That item does not need field repairs.");
                return;
            }

            Material repairMaterial = repairMaterial(target.getType());
            if (repairMaterial == null) {
                player.sendMessage(ChatColor.RED + "The Field Repair Kit has no compatible repair material for that item.");
                return;
            }

            Map<Integer, ItemStack> missing = player.getInventory().removeItem(new ItemStack(repairMaterial, 1));
            if (!missing.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Field repair requires 1 " + humanize(repairMaterial) + ".");
                return;
            }

            int repaired = Math.max(1, maxDurability / 5);
            int newDamage = Math.max(0, damage - repaired);
            damageable.setDamage(newDamage);
            target.setItemMeta(meta);

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.55F, 1.35F);
            player.sendMessage(ChatColor.GREEN + "Field repair restored " + (damage - newDamage)
                    + ChatColor.GRAY + " durability using 1 " + humanize(repairMaterial) + ".");
        };
    }

    @Nullable private static Material repairMaterial(Material type) {
        String name = type.name();
        if (name.startsWith("NETHERITE_")) {
            return Material.NETHERITE_INGOT;
        }
        if (name.startsWith("DIAMOND_")) {
            return Material.DIAMOND;
        }
        if (name.startsWith("GOLDEN_")) {
            return Material.GOLD_INGOT;
        }
        if (name.startsWith("IRON_") || name.startsWith("CHAINMAIL_")) {
            return Material.IRON_INGOT;
        }
        if (name.startsWith("LEATHER_")) {
            return Material.LEATHER;
        }
        return switch (type) {
            case ELYTRA -> Material.PHANTOM_MEMBRANE;
            case TRIDENT -> Material.PRISMARINE_SHARD;
            case SHIELD -> Material.IRON_INGOT;
            case BOW, CROSSBOW, FISHING_ROD -> Material.STRING;
            case MACE -> Material.BREEZE_ROD;
            default -> null;
        };
    }

    private static String humanize(Material material) {
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
