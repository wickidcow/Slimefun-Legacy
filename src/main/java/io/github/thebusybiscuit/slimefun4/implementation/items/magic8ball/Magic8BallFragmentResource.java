package io.github.thebusybiscuit.slimefun4.implementation.items.magic8ball;

import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;

/** The GEO Miner source for Magic 8 Ball fragments. */
public final class Magic8BallFragmentResource implements GEOResource {

    private static final NamespacedKey KEY = Objects.requireNonNull(
            NamespacedKey.fromString("magic8ball:magic8ball_fragment_geo_resource"));

    @Override
    public @Nonnull NamespacedKey getKey() {
        return KEY;
    }

    @Override
    public @Nonnull ItemStack getItem() {
        return Magic8BallItems.MAGIC_8_BALL_FRAGMENT.clone();
    }

    @Override
    public @Nonnull String getName() {
        return "Magic 8 Ball Fragment";
    }

    @Override
    public boolean isObtainableFromGEOMiner() {
        return true;
    }

    @Override
    public int getDefaultSupply(@Nonnull World.Environment environment, @Nonnull Biome biome) {
        return switch (environment) {
            case NORMAL, NETHER -> 1;
            case THE_END -> 3;
            default -> 0;
        };
    }

    @Override
    public int getMaxDeviation() {
        return 4;
    }
}
