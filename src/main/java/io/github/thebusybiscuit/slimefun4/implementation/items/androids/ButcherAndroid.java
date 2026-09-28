package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ButcherAndroid extends ProgrammableAndroid {

    @ParametersAreNonnullByDefault
    public ButcherAndroid(
            ItemGroup itemGroup, int tier, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, tier, item, recipeType, recipe);
    }

    @Override
    public AndroidType getAndroidType() {
        return AndroidType.FIGHTER;
    }

    @Override
    protected void attack(Block b, BlockFace face, Predicate<LivingEntity> predicate) {
        double damage = getTier() >= 3 ? 20D : 4D * getTier();
        double radius = 4.0 + getTier();

        for (Entity n : b.getWorld()
                .getNearbyEntities(
                        b.getLocation(),
                        radius,
                        radius,
                        radius,
                        n -> n instanceof LivingEntity livingEntity
                                && !(n instanceof ArmorStand)
                                && !(n instanceof Player)
                                && n.isValid()
                                && predicate.test(livingEntity))) {
            // Check if our android is facing this entity.
            boolean willAttack =
                    switch (face) {
                        case NORTH -> n.getLocation().getZ() < b.getZ();
                        case EAST -> n.getLocation().getX() > b.getX();
                        case SOUTH -> n.getLocation().getZ() > b.getZ();
                        case WEST -> n.getLocation().getX() < b.getX();
                        default -> false;
                    };

            if (willAttack) {
                ButcherAndroidKillTracker.clear(n);

                OfflinePlayer owner =
                        Bukkit.getOfflinePlayer(UUID.fromString(StorageCacheUtils.getData(b.getLocation(), "owner")));
                if (!Slimefun.getProtectionManager().hasPermission(owner, n.getLocation(), Interaction.ATTACK_ENTITY)) {
                    return;
                }

                ButcherAndroidKillTracker.mark(n, new AndroidInstance(this, b));
                try {
                    ((LivingEntity) n).damage(damage);
                } finally {
                    // EntityDeathEvent consumes the marker synchronously when this hit kills the target.
                    // Surviving entities must not retain a stale Android context.
                    ButcherAndroidKillTracker.clear(n);
                }
                break;
            }
        }
    }
}
