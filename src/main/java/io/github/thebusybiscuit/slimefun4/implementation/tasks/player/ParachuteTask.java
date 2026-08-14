package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.Parachute;
import javax.annotation.Nonnull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class ParachuteTask extends AbstractPlayerTask {

    private final Parachute parachute;

    public ParachuteTask(@Nonnull Player p) {
        super(p);
        ItemStack chestplate = p.getInventory().getChestplate();
        SlimefunItem equipped = chestplate == null ? null : SlimefunItem.getByItem(chestplate);
        parachute = equipped instanceof Parachute equippedParachute ? equippedParachute : null;
    }

    @Override
    protected void executeTask() {
        ItemStack chestplate = p.getInventory().getChestplate();
        if (parachute == null || chestplate == null || SlimefunItem.getByItem(chestplate) != parachute) {
            cancel();
            return;
        }

        Vector vector = new Vector(0, 1, 0);
        vector.multiply(-0.1);
        p.setVelocity(vector);
        p.setFallDistance(0F);
    }
}
