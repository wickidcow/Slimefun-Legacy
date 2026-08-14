package io.github.thebusybiscuit.slimefun4.implementation.items.magical.runes;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemDropHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link SlimefunItem} allows you to enchant any enchantable {@link ItemStack} with a random
 * {@link Enchantment}. It is also one of the very few utilisations of {@link ItemDropHandler}.
 *
 * @author Linox
 *
 * @see ItemDropHandler
 *
 */
public class EnchantmentRune extends SimpleSlimefunItem<ItemDropHandler> {

    private static final double RANGE = 1.5;
    private final Map<Material, List<Enchantment>> applicableEnchantments = new EnumMap<>(Material.class);

    @ParametersAreNonnullByDefault
    public EnchantmentRune(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        Slimefun.runSync(() -> {
            // Fix: Resolves race condition with third-party enchantment plugins that register custom enchantments
            // asynchronously during server startup.
            // By running this code synchronously after server startup, we ensure all enchantments (including those from
            // other plugins) are registered before populating applicableEnchantments.
            // This prevents missing enchantments due to late registration by other plugins.
            for (Material mat : Material.values()) {
                if (!mat.isItem()) {
                    continue;
                }

                List<Enchantment> enchantments = new ArrayList<>();

                for (Enchantment enchantment : Enchantment.values()) {

                    if (enchantment.equals(Enchantment.BINDING_CURSE)
                            || enchantment.equals(Enchantment.VANISHING_CURSE)) {
                        continue;
                    }

                    if (enchantment.canEnchantItem(new ItemStack(mat))) {
                        enchantments.add(enchantment);
                    }
                }

                applicableEnchantments.put(mat, enchantments);
            }
        });
    }

    @Override
    public ItemDropHandler getItemHandler() {
        return (e, p, item) -> {
            if (isItem(item.getItemStack())) {
                if (canUse(p, true)) {
                    Slimefun.runSyncFor(
                            item,
                            () -> {
                                try {
                                    addRandomEnchantment(p, item);
                                } catch (Exception x) {
                                    error("An Exception occurred while trying to apply an Enchantment Rune", x);
                                }
                            },
                            20L);
                }

                return true;
            }

            return false;
        };
    }

    private void addRandomEnchantment(@Nonnull Player p, @Nonnull Item rune) {
        // Being sure the entity is still valid and not picked up or whatsoever.
        if (!rune.isValid()) {
            return;
        }

        Location l = rune.getLocation();
        Collection<Entity> entites = l.getWorld().getNearbyEntities(l, RANGE, RANGE, RANGE, this::findCompatibleItem);
        Optional<Entity> optional = entites.stream().findFirst();

        if (optional.isPresent()) {
            Item item = (Item) optional.get();
            ItemStack itemStack = item.getItemStack();

            List<Enchantment> potentialEnchantments = applicableEnchantments.get(itemStack.getType());

            if (potentialEnchantments == null) {
                Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.fail", true);
                return;
            } else {
                potentialEnchantments = new ArrayList<>(potentialEnchantments);
            }

            SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);

            // Fixes #2878 - Respect enchantability config setting.
            if (slimefunItem != null && !slimefunItem.isEnchantable()) {
                Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.fail", true);
                return;
            }

            /*
             * Removing the enchantments that the item already has from enchantmentSet.
             * This also removes any conflicting enchantments
             */
            removeIllegalEnchantments(itemStack, potentialEnchantments);

            if (potentialEnchantments.isEmpty()) {
                Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.no-enchantment", true);
                return;
            }

            Enchantment enchantment =
                    potentialEnchantments.get(ThreadLocalRandom.current().nextInt(potentialEnchantments.size()));
            int level = getRandomlevel(enchantment);

            if (itemStack.getAmount() == 1) {
                // This lightning is just an effect, it deals no damage.
                l.getWorld().strikeLightningEffect(l);

                Slimefun.runSyncAt(
                        l,
                        () -> commitEnchantment(p, rune, item, enchantment, level, l),
                        10L);
            } else {
                Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.fail", true);
            }
        }
    }

    @ParametersAreNonnullByDefault
    private void commitEnchantment(
            Player p, Item rune, Item item, Enchantment enchantment, int level, Location location) {
        // Re-read both entities at commit time. They may have merged, been modified or been picked up during the delay.
        if (!rune.isValid() || !item.isValid()) {
            return;
        }

        ItemStack liveTarget = item.getItemStack();
        if (liveTarget.getAmount() != 1 || !canApplyEnchantment(liveTarget, enchantment)) {
            Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.fail", true);
            return;
        }

        SlimefunItem liveSlimefunItem = SlimefunItem.getByItem(liveTarget);
        if (liveSlimefunItem != null && !liveSlimefunItem.isEnchantable()) {
            Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.fail", true);
            return;
        }

        ItemStack enchanted = liveTarget.clone();
        try {
            enchanted.addEnchantment(enchantment, level);
        } catch (RuntimeException x) {
            error("An Exception occurred while committing an Enchantment Rune", x);
            Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.fail", true);
            return;
        }

        // Only commit entity/item mutations after the enchanted output has been prepared successfully.
        item.remove();
        consumeOneRune(rune);

        location.getWorld().spawnParticle(VersionedParticle.ENCHANTED_HIT, location, 1);
        SoundEffect.ENCHANTMENT_RUNE_ADD_ENCHANT_SOUND.playAt(location, SoundCategory.PLAYERS);
        location.getWorld().dropItemNaturally(location, enchanted);
        Slimefun.getLocalization().sendMessage(p, "messages.enchantment-rune.success", true);
    }

    private boolean canApplyEnchantment(@Nonnull ItemStack target, @Nonnull Enchantment enchantment) {
        if (!enchantment.canEnchantItem(target)) {
            return false;
        }

        for (Enchantment existing : target.getEnchantments().keySet()) {
            if (existing.equals(enchantment)
                    || existing.conflictsWith(enchantment)
                    || enchantment.conflictsWith(existing)) {
                return false;
            }
        }

        return true;
    }

    private void consumeOneRune(@Nonnull Item rune) {
        ItemStack liveRune = rune.getItemStack();
        if (liveRune.getAmount() > 1) {
            ItemStack remaining = liveRune.clone();
            remaining.setAmount(liveRune.getAmount() - 1);
            rune.setItemStack(remaining);
        } else {
            rune.remove();
        }
    }

    private int getRandomlevel(@Nonnull Enchantment enchantment) {
        int level = 1;

        if (enchantment.getMaxLevel() != 1) {
            level = ThreadLocalRandom.current().nextInt(enchantment.getMaxLevel()) + 1;
        }

        return level;
    }

    private void removeIllegalEnchantments(
            @Nonnull ItemStack target, @Nonnull List<Enchantment> potentialEnchantments) {
        for (Enchantment enchantment : target.getEnchantments().keySet()) {
            Iterator<Enchantment> iterator = potentialEnchantments.iterator();

            while (iterator.hasNext()) {
                Enchantment possibleEnchantment = iterator.next();

                // Duplicate or conflict
                if (possibleEnchantment.equals(enchantment) || possibleEnchantment.conflictsWith(enchantment)) {
                    iterator.remove();
                }
            }
        }
    }

    private boolean findCompatibleItem(@Nonnull Entity n) {
        if (n instanceof Item item) {
            return !isItem(item.getItemStack());
        }

        return false;
    }
}
