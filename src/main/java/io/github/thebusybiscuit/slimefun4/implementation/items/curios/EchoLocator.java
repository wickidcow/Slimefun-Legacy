package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Gives deliberately vague underground activity readings without exposing exact coordinates. */
public final class EchoLocator extends SimpleSlimefunItem<ItemUseHandler> {

    private static final double RANGE = 48.0D;

    @ParametersAreNonnullByDefault
    public EchoLocator(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            Location origin = player.getLocation();

            int monsters = 0;
            double sumX = 0.0D;
            double sumZ = 0.0D;
            for (Entity entity : player.getNearbyEntities(RANGE, 24.0D, RANGE)) {
                if (entity instanceof Monster) {
                    monsters++;
                    sumX += entity.getLocation().getX();
                    sumZ += entity.getLocation().getZ();
                }
            }

            int spawners = 0;
            for (BlockState state : player.getChunk().getTileEntities()) {
                if (state instanceof CreatureSpawner) {
                    spawners++;
                }
            }

            int skyLight = origin.getBlock().getLightFromSky();
            int signal = monsters * 2 + spawners * 8 + (skyLight <= 1 ? 2 : 0);
            String intensity = signal >= 20 ? "strong" : signal >= 8 ? "noticeable" : signal > 0 ? "faint" : "quiet";

            player.playSound(origin, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.65F, signal >= 20 ? 0.75F : 1.25F);
            message(player, "&3" + "Echo Locator " + "&7" + "• " + "&b"
                    + intensity + "&7" + " underground activity");
            message(player, "&7" + "Hostile echoes: " + "&e" + monsters + "&7"
                    + " • Spawner resonance in this chunk: " + "&e" + spawners);

            if (monsters > 0) {
                Location center = new Location(origin.getWorld(), sumX / monsters, origin.getY(), sumZ / monsters);
                message(player, "&7" + "The strongest living echoes seem to come from the "
                        + "&b" + cardinalDirection(origin, center) + "&7" + ".");
            } else if (spawners > 0) {
                message(player, "&7" + "A mechanical echo is present somewhere in this chunk.");
            } else {
                message(player, "&8" + "No exact coordinates are revealed by the locator.");
            }
        };
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
    private static void message(Player player, String value) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }

}
