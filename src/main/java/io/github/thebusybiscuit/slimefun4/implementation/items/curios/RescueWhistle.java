package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** A bounded multiplayer rescue signal that reports nearby responders without teleporting anyone. */
public final class RescueWhistle extends SimpleSlimefunItem<ItemUseHandler> {

    private static final double RANGE = 128.0D;
    private static final long COOLDOWN_MILLIS = 20_000L;

    private final NamespacedKey cooldownKey = new NamespacedKey(Slimefun.instance(), "rescue_whistle_cooldown_until");

    @ParametersAreNonnullByDefault
    public RescueWhistle(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player caller = event.getPlayer();
            long remaining = remainingCooldown(caller);
            if (remaining > 0L) {
                caller.sendMessage(ChatColor.RED + "Rescue Whistle is recharging for " + seconds(remaining) + " more seconds.");
                return;
            }

            startCooldown(caller);
            caller.playSound(caller.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1.0F, 1.2F);
            caller.sendMessage(ChatColor.GOLD + "Rescue Whistle sounded. Listening for nearby players within 128 blocks...");

            UUID callerId = caller.getUniqueId();
            UUID worldId = caller.getWorld().getUID();
            Location origin = caller.getLocation().clone();
            Slimefun.getSchedulerService().run(() -> {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.getUniqueId().equals(callerId)) {
                        continue;
                    }
                    Slimefun.getSchedulerService().runFor(other, () -> inspectResponder(other, callerId, worldId, origin), () -> {});
                }
            });
        };
    }

    private void inspectResponder(Player responder, UUID callerId, UUID worldId, Location origin) {
        if (!responder.getWorld().getUID().equals(worldId)) {
            return;
        }

        Location responseLocation = responder.getLocation();
        double distanceSquared = responseLocation.distanceSquared(origin);
        if (distanceSquared > RANGE * RANGE) {
            return;
        }

        int distance = (int) Math.round(Math.sqrt(distanceSquared));
        String direction = cardinalDirection(origin, responseLocation);
        responder.playSound(responder.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 0.6F, 0.85F);
        responder.sendMessage(ChatColor.YELLOW + "A Rescue Whistle is calling nearby (about " + distance + " blocks away)." );

        Player caller = Bukkit.getPlayer(callerId);
        if (caller != null) {
            String responderName = responder.getName();
            Slimefun.getSchedulerService().runFor(caller, () -> caller.sendMessage(ChatColor.GREEN + responderName
                    + ChatColor.GRAY + " is about " + ChatColor.YELLOW + distance + ChatColor.GRAY + " blocks "
                    + ChatColor.AQUA + direction + ChatColor.GRAY + "."), () -> {});
        }
    }

    private long remainingCooldown(Player player) {
        Long until = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    private void startCooldown(Player player) {
        player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MILLIS);
    }

    private static long seconds(long millis) {
        return Math.max(1L, (millis + 999L) / 1_000L);
    }

    private static String cardinalDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int octant = Math.floorMod((int) Math.round(angle / 45.0D), 8);
        return switch (octant) {
            case 0 -> "south";
            case 1 -> "southwest";
            case 2 -> "west";
            case 3 -> "northwest";
            case 4 -> "north";
            case 5 -> "northeast";
            case 6 -> "east";
            default -> "southeast";
        };
    }
}
