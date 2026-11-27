package io.github.thebusybiscuit.slimefun4.implementation.tasks.armor;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.SolarHelmet;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link SolarHelmetTask} is responsible for handling {@link SolarHelmet} for generating power
 * and adding checks for if it has sunlight and if the player is actually wearing it.
 *
 * @author TheBusyBiscuit
 * @author martinbrom
 * @author Semisol
 */
public class SolarHelmetTask extends AbstractArmorTask {

    @Override
    @ParametersAreNonnullByDefault
    protected void onPlayerTick(Player p, PlayerProfile profile) {
        ItemStack helmet = p.getInventory().getHelmet();
        if (helmet == null || helmet.getType().isAir() || !hasDaylight(p)) {
            return;
        }

        SlimefunItem item = SlimefunItem.getByItem(helmet);
        // Most players do not wear a solar helmet. Avoid their eye-block
        // lookup and skylight read; preserve the current-item permission gate.
        if (item instanceof SolarHelmet solarHelmet
                && p.getEyeLocation().getBlock().getLightFromSky() == 15
                && item.canUse(p, true)) {
            solarHelmet.rechargeItems(p);
        }
    }

    private boolean hasDaylight(@Nonnull Player p) {
        World world = p.getWorld();

        if (world.getEnvironment() != Environment.NORMAL) {
            // The End and Nether have no sunlight
            return false;
        }

        long time = world.getTime();
        return time < 12300 || time > 23850;
    }
}
