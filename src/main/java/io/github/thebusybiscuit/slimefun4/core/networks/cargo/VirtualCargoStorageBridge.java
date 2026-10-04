package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Internal compatibility bridge for addon-owned virtual storage.
 *
 * <p>The maintained Networks addon must remain source-compatible with Slimefun Legacy, United and
 * Gugu, so it cannot directly implement a Legacy-only API type. Instead, Legacy Cargo recognizes
 * this small duck-typed contract on a {@link SlimefunItem}:
 *
 * <ul>
 *   <li>{@code List<ItemStack> getCargoItemSnapshot(Location)}</li>
 *   <li>{@code ItemStack withdrawCargoItem(Location, ItemStack, int)}</li>
 *   <li>{@code ItemStack insertCargoItem(Location, ItemStack)}</li>
 * </ul>
 *
 * <p>Method discovery is cached per runtime class. Missing or broken bridges fail closed and leave
 * the original Cargo stack untouched.
 */
final class VirtualCargoStorageBridge {

    private static final ClassValue<Optional<Methods>> METHODS = new ClassValue<>() {
        @Override
        protected Optional<Methods> computeValue(Class<?> type) {
            try {
                Method snapshot = type.getMethod("getCargoItemSnapshot", Location.class);
                Method withdraw = type.getMethod(
                        "withdrawCargoItem", Location.class, ItemStack.class, int.class);
                Method insert = type.getMethod("insertCargoItem", Location.class, ItemStack.class);
                return Optional.of(new Methods(snapshot, withdraw, insert));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            } catch (RuntimeException | LinkageError exception) {
                Slimefun.logger().log(
                        Level.WARNING,
                        "Could not inspect virtual Cargo storage bridge on " + type.getName(),
                        exception);
                return Optional.empty();
            }
        }
    };

    private final SlimefunItem item;
    private final Methods methods;
    private final Location location;

    private VirtualCargoStorageBridge(
            @Nonnull SlimefunItem item, @Nonnull Methods methods, @Nonnull Location location) {
        this.item = item;
        this.methods = methods;
        this.location = location;
    }

    static @Nullable VirtualCargoStorageBridge find(@Nonnull Block target) {
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(target.getLocation());
        if (item == null) {
            return null;
        }

        Optional<Methods> methods = METHODS.get(item.getClass());
        return methods.map(value -> new VirtualCargoStorageBridge(item, value, target.getLocation()))
                .orElse(null);
    }

    @Nonnull
    List<ItemStack> snapshot() {
        Object value = invoke(methods.snapshot(), location);
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }

        List<ItemStack> snapshot = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            if (entry instanceof ItemStack stack && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                snapshot.add(stack.clone());
            }
        }
        return snapshot;
    }

    @Nullable
    ItemStack withdraw(@Nonnull ItemStack template, int amount) {
        if (template.getType() == Material.AIR || amount <= 0) {
            return null;
        }

        Object value = invoke(methods.withdraw(), location, template.clone(), amount);
        if (!(value instanceof ItemStack stack) || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return null;
        }

        if (stack.getAmount() > amount) {
            Slimefun.logger().warning(
                    "Virtual Cargo storage " + item.getId() + " returned more items than requested at " + location);
            stack = stack.clone();
            stack.setAmount(amount);
        }
        return stack;
    }

    /**
     * Offers a detached stack and returns the detached remainder. A null result means the full stack
     * was accepted.
     */
    @Nullable
    ItemStack insert(@Nonnull ItemStack stack) {
        if (stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return null;
        }

        Object value = invoke(methods.insert(), location, stack.clone());
        if (value == null) {
            return null;
        }
        if (!(value instanceof ItemStack remainder)) {
            return stack.clone();
        }
        if (remainder.getType() == Material.AIR || remainder.getAmount() <= 0) {
            return null;
        }

        ItemStack safeRemainder = remainder.clone();
        safeRemainder.setAmount(Math.min(stack.getAmount(), safeRemainder.getAmount()));
        return safeRemainder;
    }

    @Nullable
    private Object invoke(@Nonnull Method method, Object... args) {
        try {
            return method.invoke(item, args);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause()
                    : exception;
            Slimefun.logger().log(
                    Level.WARNING,
                    "Virtual Cargo storage bridge failed for " + item.getId() + " at " + location,
                    cause);
            return null;
        }
    }

    private record Methods(Method snapshot, Method withdraw, Method insert) {}
}
