package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.JetBoots;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class JetBootsTask extends AbstractPlayerTask {

    private static final float COST = 0.075F;

    private final JetBoots boots;

    public JetBootsTask(@Nonnull Player p, @Nonnull JetBoots boots) {
        super(p);
        this.boots = boots;
    }

    @Override
    protected void executeTask() {
        ItemStack equippedBoots = p.getInventory().getBoots();
        if (equippedBoots == null || SlimefunItem.getByItem(equippedBoots) != boots) {
            cancel();
            return;
        }

        double accuracy = NumberUtils.reparseDouble(boots.getSpeed() - 0.7);

        if (boots.removeItemCharge(equippedBoots, COST)) {
            SoundEffect.JETBOOTS_THRUST_SOUND.playAt(p.getLocation(), SoundCategory.PLAYERS);
            VisualEffectUtils.spawnSmoke(p.getLocation());
            p.setFallDistance(0F);
            double gravity = 0.04;
            double offset = ThreadLocalRandom.current().nextBoolean() ? accuracy : -accuracy;
            Vector vector = new Vector(
                    p.getEyeLocation().getDirection().getX() * boots.getSpeed() + offset,
                    gravity,
                    p.getEyeLocation().getDirection().getZ() * boots.getSpeed() - offset);

            p.setVelocity(vector);
        } else {
            cancel();
        }
    }
}
