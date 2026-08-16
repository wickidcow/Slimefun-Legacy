package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * A reusable visual distress/rally marker with three player-selectable flare modes.
 */
public final class EmergencyFlare extends SimpleSlimefunItem<ItemUseHandler> {

    static final int FLARE_PULSES = 40;
    static final long PULSE_DELAY_TICKS = 10L;
    static final long COOLDOWN_MILLIS = 45_000L;

    private final NamespacedKey modeKey = new NamespacedKey(Slimefun.instance(), "emergency_flare_mode");
    private final NamespacedKey cooldownKey = new NamespacedKey(Slimefun.instance(), "emergency_flare_cooldown_until");

    @ParametersAreNonnullByDefault
    public EmergencyFlare(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            ItemMeta meta = item.getItemMeta();
            FlareMode mode = FlareMode.fromStored(meta.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING));

            if (player.isSneaking()) {
                FlareMode next = mode.next();
                meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, next.name());
                item.setItemMeta(meta);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.65F, 1.45F);
                player.sendMessage(ChatColor.GOLD + "Emergency Flare mode: " + next.chatColor() + next.displayName());
                return;
            }

            long remaining = getCooldownRemainingMillis(player);
            if (remaining > 0L) {
                player.sendMessage(ChatColor.RED + "Emergency Flare is recharging for "
                        + formatSeconds(remaining) + " more seconds.");
                return;
            }

            launch(player, mode);
            startCooldown(player);
        };
    }

    private void launch(Player player, FlareMode mode) {
        Location playerLocation = player.getLocation();
        World world = player.getWorld();
        int x = playerLocation.getBlockX();
        int z = playerLocation.getBlockZ();

        double flareY;
        if (world.getEnvironment() == World.Environment.NETHER) {
            flareY = Math.min(world.getMaxHeight() - 4.0D, playerLocation.getY() + 5.0D);
        } else {
            int highest = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            flareY = Math.min(world.getMaxHeight() - 4.0D, Math.max(playerLocation.getY() + 3.0D, highest + 4.0D));
        }

        Location flareLocation = new Location(world, x + 0.5D, flareY, z + 0.5D);
        Firework firework = world.spawn(flareLocation, Firework.class);
        FireworkMeta fireworkMeta = firework.getFireworkMeta();
        fireworkMeta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(mode.color())
                .withFade(Color.WHITE)
                .trail(true)
                .flicker(true)
                .build());
        fireworkMeta.setPower(2);
        firework.setFireworkMeta(fireworkMeta);

        player.playSound(playerLocation, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
        player.sendMessage(ChatColor.GOLD + "Emergency Flare: " + mode.chatColor() + mode.displayName() + ChatColor.GRAY
                + " launched at " + ChatColor.YELLOW + x + ", " + z + ChatColor.GRAY
                + ". The marker remains visible for about 20 seconds.");
        emitPulse(flareLocation, mode, FLARE_PULSES);
    }

    private void emitPulse(Location origin, FlareMode mode, int remainingPulses) {
        if (remainingPulses <= 0 || origin.getWorld() == null) {
            return;
        }

        World world = origin.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(mode.color(), 1.7F);
        double maxY = world.getMaxHeight() - 1.0D;
        for (int step = 0; step < 9; step++) {
            double y = Math.min(maxY, origin.getY() + step * 2.5D);
            Location point = new Location(world, origin.getX(), y, origin.getZ());
            world.spawnParticle(Particle.DUST, point, 2, 0.15D, 0.15D, 0.15D, 0.0D, dust);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.05D, 0.2D, 0.05D, 0.0D);
        }

        Slimefun.getSchedulerService()
                .runAtLater(origin, () -> emitPulse(origin, mode, remainingPulses - 1), PULSE_DELAY_TICKS);
    }

    private long getCooldownRemainingMillis(Player player) {
        Long until = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    private void startCooldown(Player player) {
        player.getPersistentDataContainer()
                .set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MILLIS);
    }

    private static long formatSeconds(long millis) {
        return Math.max(1L, (millis + 999L) / 1_000L);
    }

    enum FlareMode {
        HELP("Help", Color.RED, ChatColor.RED),
        RALLY("Rally Point", Color.LIME, ChatColor.GREEN),
        DANGER("Danger", Color.ORANGE, ChatColor.GOLD);

        private final String displayName;
        private final Color color;
        private final ChatColor chatColor;

        FlareMode(String displayName, Color color, ChatColor chatColor) {
            this.displayName = displayName;
            this.color = color;
            this.chatColor = chatColor;
        }

        String displayName() {
            return displayName;
        }

        Color color() {
            return color;
        }

        ChatColor chatColor() {
            return chatColor;
        }

        FlareMode next() {
            FlareMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static FlareMode fromStored(String value) {
            if (value == null || value.isBlank()) {
                return HELP;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return HELP;
            }
        }
    }
}