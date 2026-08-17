package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.enchanting;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Runtime diagnostics and transaction helpers shared by the core enchantment machines. */
final class EnchantmentMachineRuntime {

    private static final long FAILURE_COOLDOWN_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final Map<String, Long> LAST_FAILURE = new ConcurrentHashMap<>();

    private EnchantmentMachineRuntime() {}

    static int processingTicks(int baseTicks, int enchantmentCount, int speed) {
        return Math.max(1, baseTicks * Math.max(1, enchantmentCount) / Math.max(1, speed));
    }

    static @Nonnull ItemStack one(@Nonnull ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    private static @Nullable boolean[] matchInputSlots(
            @Nonnull BlockMenu menu, @Nonnull int[] slots, @Nonnull ItemStack[] expectedInputs) {
        if (slots.length != expectedInputs.length || slots.length == 0) {
            return null;
        }

        boolean[] matchedSlots = new boolean[slots.length];
        for (ItemStack expected : expectedInputs) {
            if (expected == null || expected.getType().isAir()) {
                return null;
            }

            boolean matched = false;
            for (int index = 0; index < slots.length; index++) {
                if (matchedSlots[index]) {
                    continue;
                }

                ItemStack current = menu.getItemInSlot(slots[index]);
                if (current != null
                        && !current.getType().isAir()
                        && current.getAmount() >= 1
                        && current.isSimilar(expected)) {
                    matchedSlots[index] = true;
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return null;
            }
        }

        for (boolean matched : matchedSlots) {
            if (!matched) {
                return null;
            }
        }

        return matchedSlots;
    }

    static boolean inputsMatchSnapshots(
            @Nonnull BlockMenu menu, @Nonnull int[] slots, @Nonnull ItemStack[] expectedInputs) {
        return matchInputSlots(menu, slots, expectedInputs) != null;
    }

    static boolean consumeOneEachIfUnchanged(
            @Nonnull BlockMenu menu, @Nonnull int[] slots, @Nonnull ItemStack[] expectedInputs) {
        if (matchInputSlots(menu, slots, expectedInputs) == null) {
            return false;
        }

        for (int slot : slots) {
            menu.consumeItem(slot, 1);
        }
        return true;
    }

    static void status(
            @Nonnull BlockMenu menu, @Nonnull Material material, @Nonnull String title, @Nonnull String... lore) {
        try {
            if (menu.toInventory().getViewers().isEmpty()) {
                return;
            }

            ItemStack status = new ItemStack(material);
            ItemMeta meta = status.getItemMeta();
            meta.setDisplayName(color(title));
            if (lore.length > 0) {
                List<String> coloredLore = new ArrayList<>(lore.length);
                for (String line : lore) {
                    coloredLore.add(color(line));
                }
                meta.setLore(coloredLore);
            }
            status.setItemMeta(meta);
            ItemStack current = menu.getItemInSlot(22);
            if (current == null || !current.isSimilar(status) || current.getAmount() != status.getAmount()) {
                menu.replaceExistingItem(22, status);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A diagnostic icon must never stop a machine tick.
        }
    }

    static void reportFailure(
            @Nonnull AbstractEnchantmentMachine machine,
            @Nonnull BlockMenu menu,
            @Nonnull String operation,
            @Nonnull Throwable failure) {
        String location = safeLocation(menu.getLocation());
        String key =
                machine.getId() + '|' + operation + '|' + failure.getClass().getName() + '|' + location;
        long now = System.currentTimeMillis();
        Long previous = LAST_FAILURE.put(key, now);
        if (previous != null && now - previous < FAILURE_COOLDOWN_MILLIS) {
            return;
        }

        Slimefun.logger()
                .log(
                        Level.WARNING,
                        "Enchantment machine operation failed [item=" + machine.getId()
                                + ", operation=" + operation
                                + ", location=" + location
                                + "]. Inputs were left untouched.",
                        failure);
    }

    private static @Nonnull String safeLocation(@Nullable Location location) {
        if (location == null) {
            return "<unknown>";
        }

        try {
            return (location.getWorld() == null
                            ? "<unloaded-world>"
                            : location.getWorld().getName())
                    + ':'
                    + location.getBlockX()
                    + ':'
                    + location.getBlockY()
                    + ':'
                    + location.getBlockZ();
        } catch (RuntimeException | LinkageError ignored) {
            return "<unreadable>";
        }
    }

    private static @Nonnull String color(@Nonnull String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
