package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.events.CoolerFeedPlayerEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.Cooler;
import io.github.thebusybiscuit.slimefun4.implementation.items.food.Juice;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

/**
 * This {@link Listener} listens for a {@link FoodLevelChangeEvent} or an {@link EntityDamageEvent} for starvation
 * damage
 * and consumes a {@link Juice} from any {@link Cooler} that can be found in the {@link Inventory} of the given
 * {@link Player}.
 *
 * @author TheBusyBiscuit
 * @author Linox
 *
 * @see Cooler
 * @see Juice
 *
 */
public class CoolerListener implements Listener {

    private final Slimefun plugin;
    private final Cooler cooler;

    @ParametersAreNonnullByDefault
    public CoolerListener(Slimefun plugin, Cooler cooler) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.plugin = plugin;
        this.cooler = cooler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHungerLoss(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player player && e.getFoodLevel() < player.getFoodLevel()) {
            checkAndConsume(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHungerDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player && e.getCause() == DamageCause.STARVATION) {
            checkAndConsume(player);
        }
    }

    private void checkAndConsume(@Nonnull Player p) {
        if (cooler == null || cooler.isDisabled()) {
            // Do not proceed if the Cooler was disabled
            return;
        }

        List<ItemStack> coolers = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents()) {
            if (cooler.isItem(item)) {
                coolers.add(item);
            }
        }

        if (coolers.isEmpty() || !cooler.canUse(p, true)) {
            return;
        }

        tryConsumeFromCoolers(p, coolers, 0);
    }

    private void tryConsumeFromCoolers(@Nonnull Player p, @Nonnull List<ItemStack> coolers, int index) {
        if (!p.isOnline() || index >= coolers.size()) {
            return;
        }

        ItemStack coolerItem = coolers.get(index);
        if (!PlayerBackpack.isOwnerOnline(coolerItem.getItemMeta())) {
            tryConsumeFromCoolers(p, coolers, index + 1);
            return;
        }

        PlayerBackpack.getAsync(coolerItem).whenComplete((backpack, error) -> Slimefun.runSyncFor(p, () -> {
            if (!p.isOnline()) {
                return;
            }

            if (error != null || backpack == null) {
                tryConsumeFromCoolers(p, coolers, index + 1);
                return;
            }

            ItemStack currentCooler = findCurrentCooler(p, coolerItem);
            if (currentCooler == null || !cooler.canUse(p, false)) {
                tryConsumeFromCoolers(p, coolers, index + 1);
                return;
            }

            PlayerBackpack.migrateLegacyItem(currentCooler, backpack);
            if (!consumeJuice(p, currentCooler, backpack)) {
                tryConsumeFromCoolers(p, coolers, index + 1);
            }
        }));
    }

    private ItemStack findCurrentCooler(@Nonnull Player p, @Nonnull ItemStack expected) {
        var expectedUuid = expected.hasItemMeta()
                ? PlayerBackpack.getBackpackUUID(expected.getItemMeta())
                : java.util.Optional.<String>empty();

        for (ItemStack current : p.getInventory().getContents()) {
            if (!cooler.isItem(current)) {
                continue;
            }

            if (expectedUuid.isPresent()) {
                if (current.hasItemMeta()
                        && PlayerBackpack.getBackpackUUID(current.getItemMeta()).equals(expectedUuid)) {
                    return current;
                }
            } else if (current.equals(expected)) {
                // Legacy backpacks do not have a PDC UUID yet. Equality keeps the
                // async callback tied to the exact item representation that triggered it.
                return current;
            }
        }

        return null;
    }

    private boolean consumeJuice(@Nonnull Player p, @Nonnull ItemStack coolerItem, @Nonnull PlayerBackpack backpack) {
        Inventory inv = backpack.getInventory();
        int slot = -1;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);

            if (stack != null
                    && SlimefunItem.getByItem(stack) instanceof Juice
                    && stack.getItemMeta() instanceof PotionMeta) {
                slot = i;
                break;
            }
        }

        if (slot >= 0) {
            ItemStack item = inv.getItem(slot);
            CoolerFeedPlayerEvent event = new CoolerFeedPlayerEvent(p, cooler, coolerItem, item);
            plugin.getServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                PotionMeta im = (PotionMeta) event.getConsumedItem().getItemMeta();

                for (PotionEffect effect : im.getCustomEffects()) {
                    p.addPotionEffect(effect);
                }

                p.setSaturation(6F);
                SoundEffect.COOLER_CONSUME_SOUND.playFor(p);

                if (item.getAmount() <= 1) {
                    inv.setItem(slot, null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                }

                Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInventory(backpack);

                return true;
            }
        }

        return false;
    }
}
