package io.github.thebusybiscuit.slimefun4.implementation.tasks.player;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.Jetpack;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import javax.annotation.Nonnull;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class JetpackTask extends AbstractPlayerTask {

    private static final float COST = 0.08F;

    private final Jetpack jetpack;

    public JetpackTask(@Nonnull Player p, @Nonnull Jetpack jetpack) {
        super(p);
        this.jetpack = jetpack;
    }

    @Override
    protected void executeTask() {
        ItemStack chestplate = p.getInventory().getChestplate();
        if (chestplate == null || SlimefunItem.getByItem(chestplate) != jetpack) {
            cancel();
            return;
        }

        if (jetpack.removeItemCharge(chestplate, COST)) {
            SoundEffect.JETPACK_THRUST_SOUND.playAt(p.getLocation(), SoundCategory.PLAYERS);
            VisualEffectUtils.spawnSmoke(p.getLocation());
            p.setFallDistance(0F);
            Vector vector = new Vector(0, 1, 0);
            vector.multiply(jetpack.getThrust());
            vector.add(p.getEyeLocation().getDirection().multiply(0.2F));

            p.setVelocity(vector);
        } else {
            cancel();
        }
    }
}
