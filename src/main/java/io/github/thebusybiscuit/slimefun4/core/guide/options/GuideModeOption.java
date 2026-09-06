package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

class GuideModeOption implements SlimefunGuideOption<SlimefunGuideMode> {

    @Nonnull
    @Override
    public SlimefunAddon getAddon() {
        return Slimefun.instance();
    }

    @Nonnull
    @Override
    public NamespacedKey getKey() {
        return new NamespacedKey(Slimefun.instance(), "guide_mode");
    }

    @Nonnull
    @Override
    public Optional<ItemStack> getDisplayItem(Player p, ItemStack guide) {
        if (!p.hasPermission("slimefun.cheat.items")) {
            // Only Players with the appropriate permission can access the cheat sheet
            return Optional.empty();
        }

        Optional<SlimefunGuideMode> current = getSelectedOption(p, guide);

        if (current.isPresent()) {
            SlimefunGuideMode selectedMode = current.get();
            ItemStack item = new ItemStack(Material.AIR);

            if (selectedMode == SlimefunGuideMode.SURVIVAL_MODE) {
                item.setType(Material.CHEST);
            } else {
                item.setType(Material.COMMAND_BLOCK);
            }

            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(
                    ChatColor.GRAY + "Slimefun Guide style: " + ChatColor.YELLOW + selectedMode.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add((selectedMode == SlimefunGuideMode.SURVIVAL_MODE ? ChatColor.GREEN : ChatColor.GRAY)
                    + "Survival Mode");
            lore.add((selectedMode == SlimefunGuideMode.CHEAT_MODE ? ChatColor.GREEN : ChatColor.GRAY) + "Cheat Mode");

            lore.add("");
            lore.add(ChatColor.GRAY + "\u21E8 " + ChatColor.YELLOW + "Click to change the guide mode");
            meta.setLore(lore);
            item.setItemMeta(meta);

            return Optional.of(item);
        }

        return Optional.empty();
    }

    @Override
    public void onClick(@Nonnull Player p, @Nonnull ItemStack guide) {
        ItemStack targetGuide = getTargetGuide(p, guide);
        Optional<SlimefunGuideMode> current = getSelectedOption(p, targetGuide);

        if (current.isPresent()) {
            SlimefunGuideMode next = getNextMode(p, current.get());
            setSelectedOption(p, targetGuide, next);
        }

        SlimefunGuideSettings.openSettings(p, targetGuide);
    }

    @Nonnull
    private ItemStack getTargetGuide(@Nonnull Player p, @Nonnull ItemStack guide) {
        if (SlimefunGuide.isGuideItem(guide)) {
            return guide;
        }

        ItemStack offHand = p.getInventory().getItemInOffHand();
        if (SlimefunGuide.isGuideItem(offHand)) {
            return offHand;
        }

        // /sf open_guide and similar command paths can open the menu without a physical guide.
        // Never apply guide metadata to an arbitrary item in the player's hand. Use a temporary
        // guide for this menu session instead so the setting remains functional and safe.
        return SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE).clone();
    }

    @Nonnull
    private SlimefunGuideMode getNextMode(@Nonnull Player p, @Nonnull SlimefunGuideMode mode) {
        if (p.hasPermission("slimefun.cheat.items")) {
            if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
                return SlimefunGuideMode.CHEAT_MODE;
            } else {
                return SlimefunGuideMode.SURVIVAL_MODE;
            }
        } else {
            return SlimefunGuideMode.SURVIVAL_MODE;
        }
    }

    @Nonnull
    @Override
    public Optional<SlimefunGuideMode> getSelectedOption(@Nonnull Player p, @Nonnull ItemStack guide) {
        if (SlimefunUtils.isItemSimilar(guide, SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE), true, false)) {
            return Optional.of(SlimefunGuideMode.CHEAT_MODE);
        } else {
            return Optional.of(SlimefunGuideMode.SURVIVAL_MODE);
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void setSelectedOption(Player p, ItemStack guide, SlimefunGuideMode value) {
        guide.setItemMeta(SlimefunGuide.getItem(value).getItemMeta());
    }
}
