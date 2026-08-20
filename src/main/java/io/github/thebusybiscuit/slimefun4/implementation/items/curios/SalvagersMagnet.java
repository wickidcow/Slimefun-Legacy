package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** A carried item magnet with an item-local blacklist/whitelist filter. */
public final class SalvagersMagnet extends SimpleSlimefunItem<ItemUseHandler> {

    private static final double RADIUS = 8.0D;
    private static final AtomicBoolean TASK_STARTED = new AtomicBoolean();

    private final NamespacedKey activeKey = new NamespacedKey(Slimefun.instance(), "salvagers_magnet_active");
    private final NamespacedKey modeKey = new NamespacedKey(Slimefun.instance(), "salvagers_magnet_mode");
    private final NamespacedKey filterKey = new NamespacedKey(Slimefun.instance(), "salvagers_magnet_filter");

    @ParametersAreNonnullByDefault
    public SalvagersMagnet(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack magnet = event.getItem();
            ItemMeta meta = magnet.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();

            if (player.isSneaking()) {
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (!offHand.getType().isAir() && SlimefunItem.getByItem(offHand) != this) {
                    toggleFilter(data, offHand.getType(), player);
                } else {
                    FilterMode next = mode(data).next();
                    data.set(modeKey, PersistentDataType.STRING, next.name());
                    player.sendMessage(ChatColor.GOLD + "Salvager's Magnet filter mode: " + ChatColor.AQUA
                            + next.displayName() + ChatColor.GRAY + ".");
                }
            } else {
                boolean active = !isActive(data);
                data.set(activeKey, PersistentDataType.BYTE, (byte) (active ? 1 : 0));
                player.sendMessage(ChatColor.GOLD + "Salvager's Magnet: "
                        + (active ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF") + ChatColor.GRAY + " • "
                        + mode(data).displayName() + " • " + filter(data).size() + " filtered material(s).");
            }

            magnet.setItemMeta(meta);
            player.playSound(player.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 0.45F, 1.55F);
        };
    }

    @Override
    public void postRegister() {
        if (!isDisabled() && TASK_STARTED.compareAndSet(false, true)) {
            Slimefun.getSchedulerService().runAtFixedRate(this::scanOnlinePlayers, 20L, 10L);
        }
    }

    private void scanOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Slimefun.getSchedulerService().runFor(player, () -> pullFor(player), () -> {});
        }
    }

    private void pullFor(Player player) {
        ItemStack magnet = findActiveMagnet(player);
        if (magnet == null) {
            return;
        }

        ItemMeta meta = magnet.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        FilterMode mode = mode(data);
        Set<Material> filter = filter(data);
        Location destination = player.getLocation().add(0.0D, 0.7D, 0.0D);
        double targetX = destination.getX();
        double targetY = destination.getY();
        double targetZ = destination.getZ();

        for (Entity entity : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (!(entity instanceof Item dropped)) {
                continue;
            }

            Slimefun.getSchedulerService().runFor(dropped, () -> {
                if (!dropped.isValid()) {
                    return;
                }
                Material material = dropped.getItemStack().getType();
                if (!mode.accepts(material, filter)) {
                    return;
                }

                Location source = dropped.getLocation();
                Vector pull = new Vector(targetX - source.getX(), targetY - source.getY(), targetZ - source.getZ());
                if (pull.lengthSquared() < 0.04D) {
                    return;
                }
                dropped.setVelocity(pull.normalize().multiply(0.42D));
            });
        }
    }

    private ItemStack findActiveMagnet(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir() || SlimefunItem.getByItem(stack) != this) {
                continue;
            }
            if (isActive(stack.getItemMeta().getPersistentDataContainer())) {
                return stack;
            }
        }
        return null;
    }

    private boolean isActive(PersistentDataContainer data) {
        Byte value = data.get(activeKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private FilterMode mode(PersistentDataContainer data) {
        String stored = data.get(modeKey, PersistentDataType.STRING);
        if (stored == null) {
            return FilterMode.BLACKLIST;
        }
        try {
            return FilterMode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return FilterMode.BLACKLIST;
        }
    }

    private Set<Material> filter(PersistentDataContainer data) {
        String stored = data.get(filterKey, PersistentDataType.STRING);
        Set<Material> materials = new TreeSet<>((a, b) -> a.name().compareTo(b.name()));
        if (stored == null || stored.isBlank()) {
            return materials;
        }

        Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> {
                    try {
                        materials.add(Material.valueOf(value));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore entries left behind by removed or renamed materials.
                    }
                });
        return materials;
    }

    private void toggleFilter(PersistentDataContainer data, Material material, Player player) {
        Set<Material> materials = filter(data);
        boolean added;
        if (materials.remove(material)) {
            added = false;
        } else {
            materials.add(material);
            added = true;
        }

        String serialized = materials.stream().map(Material::name).reduce((a, b) -> a + "," + b).orElse("");
        data.set(filterKey, PersistentDataType.STRING, serialized);
        player.sendMessage(ChatColor.GOLD + "Salvager's Magnet: " + ChatColor.WHITE
                + humanize(material) + ChatColor.GRAY + (added ? " added to " : " removed from ")
                + mode(data).displayName().toLowerCase(Locale.ROOT) + ChatColor.GRAY + ".");
    }

    private static String humanize(Material material) {
        String value = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private enum FilterMode {
        BLACKLIST("Blacklist"),
        WHITELIST("Whitelist");

        private final String displayName;

        FilterMode(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        FilterMode next() {
            return this == BLACKLIST ? WHITELIST : BLACKLIST;
        }

        boolean accepts(Material material, Set<Material> filter) {
            boolean contained = filter.contains(material);
            return this == BLACKLIST ? !contained : contained;
        }
    }
}
