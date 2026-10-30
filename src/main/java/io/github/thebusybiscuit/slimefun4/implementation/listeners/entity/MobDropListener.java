package io.github.thebusybiscuit.slimefun4.implementation.listeners.entity;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RandomMobDrop;
import io.github.thebusybiscuit.slimefun4.core.handlers.EntityKillHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.misc.BasicCircuitBoard;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link Listener} is responsible for handling any custom mob drops.
 * These drops can also be randomized using the interface {@link RandomMobDrop}, otherwise
 * they will be handled via {@link RecipeType}.
 *
 * @author TheBusyBiscuit
 *
 * @see RandomMobDrop
 *
 */
public class MobDropListener implements Listener {

    public MobDropListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            ItemStack item = p.getInventory().getItemInMainHand();

            Set<ItemStack> customDrops = Slimefun.getRegistry().getMobDrops().get(e.getEntityType());

            if (customDrops != null && !customDrops.isEmpty()) {
                for (ItemStack drop : customDrops) {
                    if (canDrop(p, drop)) {
                        e.getDrops().add(drop.clone());
                    }
                }
            }

            if (item.getType() != Material.AIR) {
                SlimefunItem sfItem = SlimefunItem.getByItem(item);

                if (sfItem != null && sfItem.canUse(p, true)) {
                    sfItem.callItemHandler(
                            EntityKillHandler.class, handler -> handler.onKill(e, e.getEntity(), p, item));
                }
            }
        }
    }

    private boolean canDrop(@Nonnull Player p, @Nonnull ItemStack item) {
        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        if (sfItem == null) {
            return true;
        } else if (sfItem.canUse(p, false)) {
            // Silent check: this runs for every registered drop of the mob on every kill, so sending the
            // not-researched / no-permission / disabled message here spams chat. The drop is still withheld.
            if (sfItem instanceof RandomMobDrop randomMobDrop) {
                int random = ThreadLocalRandom.current().nextInt(100);

                if (randomMobDrop.getMobDropChance() <= random) {
                    return false;
                }
            }

            if (sfItem instanceof BasicCircuitBoard basicCircuitBoard) {
                return basicCircuitBoard.isDroppedFromGolems();
            }

            return true;
        } else {
            return false;
        }
    }
}
