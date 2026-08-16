package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * A reusable field journal that records biomes when the player chooses to make an entry.
 */
public final class ExpeditionJournal extends SimpleSlimefunItem<ItemUseHandler> {

    private static final int MAX_RECORDED_BIOMES = 128;
    private final NamespacedKey biomesKey = new NamespacedKey(Slimefun.instance(), "expedition_journal_biomes");

    @ParametersAreNonnullByDefault
    public ExpeditionJournal(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            ItemMeta meta = item.getItemMeta();
            String raw = meta.getPersistentDataContainer().get(biomesKey, PersistentDataType.STRING);
            Set<String> biomes = new LinkedHashSet<>();
            if (raw != null && !raw.isBlank()) {
                biomes.addAll(Arrays.asList(raw.split("\\|")));
                biomes.removeIf(String::isBlank);
            }

            String current = player.getLocation().getBlock().getBiome().getKey().getKey();
            boolean discovered = false;
            if (biomes.size() < MAX_RECORDED_BIOMES) {
                discovered = biomes.add(current);
            }

            if (discovered) {
                meta.getPersistentDataContainer().set(biomesKey, PersistentDataType.STRING, String.join("|", biomes));
                item.setItemMeta(meta);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7F, 1.15F);
                player.sendMessage(ChatColor.GOLD + "New journal entry: " + ChatColor.AQUA + humanize(current));
            }

            player.sendMessage(ChatColor.GRAY + "Expedition Journal: " + ChatColor.WHITE + biomes.size() + ChatColor.GRAY
                    + " biomes recorded • current: " + ChatColor.AQUA + humanize(current));

            if (player.isSneaking() && !biomes.isEmpty()) {
                String recent = biomes.stream()
                        .skip(Math.max(0, biomes.size() - 6L))
                        .map(ExpeditionJournal::humanize)
                        .reduce((left, right) -> left + ChatColor.GRAY + ", " + ChatColor.AQUA + right)
                        .orElse("");
                player.sendMessage(ChatColor.GRAY + "Recent discoveries: " + ChatColor.AQUA + recent);
            }
        };
    }

    private static String humanize(String key) {
        StringBuilder result = new StringBuilder();
        for (String part : key.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
