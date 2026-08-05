package io.github.thebusybiscuit.slimefun4.implementation.guide;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Guards Slimefun Guide rendering and navigation against recursive or broken addon calls.
 *
 * <p>This class deliberately catches only failures that can reasonably originate from guide implementations:
 * {@link RuntimeException}, {@link LinkageError}, and {@link StackOverflowError}. Fatal JVM errors are not swallowed.
 */
public final class GuideRuntimeGuard {

    private static final int MAX_NESTED_GUIDE_CALLS = 12;
    private static final long SLOW_GUIDE_CALL_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long WARNING_COOLDOWN_MILLIS = Duration.ofMinutes(1).toMillis();

    private static final ThreadLocal<Deque<GuideCall>> ACTIVE_CALLS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<String, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private GuideRuntimeGuard() {}

    /** Executes a player-facing guide action with recursion, failure, and slow-call diagnostics. */
    @ParametersAreNonnullByDefault
    public static void run(
            PlayerProfile profile,
            SlimefunGuideMode mode,
            String operation,
            @Nullable ItemGroup itemGroup,
            Runnable action) {
        execute(profile, mode, operation, itemGroup, true, null, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Executes a guide rendering lookup and returns the fallback value if an addon category fails.
     *
     * <p>Unlike {@link #run(PlayerProfile, SlimefunGuideMode, String, ItemGroup, Runnable)}, this method does not close
     * the player's inventory. It is intended for visibility checks, icons, names, and other individual render values
     * where one broken addon entry should be skipped without destroying the entire menu.
     */
    @ParametersAreNonnullByDefault
    public static <T> T getOrDefault(
            PlayerProfile profile,
            SlimefunGuideMode mode,
            String operation,
            @Nullable ItemGroup itemGroup,
            @Nullable T fallback,
            Supplier<T> supplier) {
        return execute(profile, mode, operation, itemGroup, false, fallback, supplier);
    }

    @ParametersAreNonnullByDefault
    private static <T> T execute(
            PlayerProfile profile,
            SlimefunGuideMode mode,
            String operation,
            @Nullable ItemGroup itemGroup,
            boolean playerFacing,
            @Nullable T fallback,
            Supplier<T> supplier) {
        Player player = profile.getPlayer();
        if (player == null) {
            return fallback;
        }

        GuideCall call = new GuideCall(operation, mode, safeGroupKey(itemGroup));
        Deque<GuideCall> stack = ACTIVE_CALLS.get();
        int depth = stack.size() + 1;

        if (stack.size() >= MAX_NESTED_GUIDE_CALLS || stack.contains(call)) {
            reportRecursiveCall(player, call, itemGroup, stack, playerFacing);
            return fallback;
        }

        stack.addLast(call);
        long started = System.nanoTime();

        try {
            return supplier.get();
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            reportFailure(player, call, itemGroup, stack, failure, playerFacing);
            return fallback;
        } finally {
            long elapsed = System.nanoTime() - started;
            if (elapsed >= SLOW_GUIDE_CALL_NANOS) {
                reportSlowCall(player, call, itemGroup, stack, depth, elapsed);
            }

            if (!stack.isEmpty() && call.equals(stack.peekLast())) {
                stack.removeLast();
            } else {
                stack.clear();
            }

            if (stack.isEmpty()) {
                ACTIVE_CALLS.remove();
            }
        }
    }

    private static void reportRecursiveCall(
            @Nonnull Player player,
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            @Nonnull Deque<GuideCall> stack,
            boolean playerFacing) {
        if (playerFacing) {
            player.closeInventory();
            player.sendMessage(ChatColor.DARK_RED
                    + "Slimefun blocked a recursive guide menu. Please tell an administrator to check the console.");
        }

        String warningKey = "recursive|" + call;
        warnOnce(
                warningKey,
                "Blocked recursive Slimefun Guide call"
                        + describeContext(player, call, itemGroup, stack, null),
                null);
    }

    private static void reportFailure(
            @Nonnull Player player,
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            @Nonnull Deque<GuideCall> stack,
            @Nonnull Throwable failure,
            boolean playerFacing) {
        if (playerFacing) {
            player.closeInventory();
            player.sendMessage(ChatColor.DARK_RED
                    + "Slimefun blocked a broken guide menu. Please tell an administrator to check the console.");
        }

        String warningKey = "failure|" + call + '|' + failure.getClass().getName();
        warnOnce(
                warningKey,
                "Slimefun Guide action failed"
                        + describeContext(player, call, itemGroup, stack, failure.getClass().getName()),
                failure);
    }

    private static void reportSlowCall(
            @Nonnull Player player,
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            @Nonnull Deque<GuideCall> stack,
            int depth,
            long elapsedNanos) {
        long elapsedMillis = Duration.ofNanos(elapsedNanos).toMillis();
        String warningKey = "slow|" + call;
        warnOnce(
                warningKey,
                "Slow Slimefun Guide action took " + elapsedMillis + " ms"
                        + describeContext(player, call, itemGroup, stack, "elapsed=" + elapsedMillis + "ms, depth=" + depth),
                null);
    }

    private static @Nonnull String describeContext(
            @Nonnull Player player,
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            @Nonnull Deque<GuideCall> stack,
            @Nullable String extra) {
        String chain = stack.stream().map(GuideCall::toString).collect(Collectors.joining(" -> "));
        StringBuilder builder = new StringBuilder(256)
                .append(" [player=")
                .append(player.getName())
                .append(", uuid=")
                .append(player.getUniqueId())
                .append(", mode=")
                .append(call.mode())
                .append(", operation=")
                .append(call.operation())
                .append(", depth=")
                .append(stack.size())
                .append(", activeChain=")
                .append(chain.isEmpty() ? "<empty>" : chain)
                .append(']')
                .append(describeOwner(itemGroup));

        if (extra != null && !extra.isBlank()) {
            builder.append(" [").append(extra).append(']');
        }

        return builder.toString();
    }

    private static void warnOnce(@Nonnull String key, @Nonnull String message, @Nullable Throwable failure) {
        long now = System.currentTimeMillis();
        Long previous = LAST_WARNING.put(key, now);
        if (previous != null && now - previous < WARNING_COOLDOWN_MILLIS) {
            return;
        }

        if (failure == null) {
            Slimefun.logger().warning(message);
        } else {
            Slimefun.logger().log(Level.SEVERE, message, failure);
        }
    }

    private static @Nonnull String safeGroupKey(@Nullable ItemGroup itemGroup) {
        if (itemGroup == null) {
            return "<none>";
        }

        try {
            NamespacedKey key = itemGroup.getKey();
            return key == null ? "<null-key>" : key.toString();
        } catch (RuntimeException | LinkageError failure) {
            return "<unreadable-key:" + itemGroup.getClass().getName() + '>';
        }
    }

    private static @Nonnull String describeOwner(@Nullable ItemGroup itemGroup) {
        if (itemGroup == null) {
            return "";
        }

        String addonName = "unregistered";
        try {
            SlimefunAddon addon = itemGroup.getAddon();
            if (addon != null) {
                addonName = addon.getName();
            }
        } catch (RuntimeException | LinkageError ignored) {
            addonName = "unreadable-addon";
        }

        return " [group=" + safeGroupKey(itemGroup)
                + ", class=" + itemGroup.getClass().getName()
                + ", addon=" + addonName + ']';
    }

    private record GuideCall(String operation, SlimefunGuideMode mode, String groupKey) {
        @Override
        public @Nonnull String toString() {
            return operation + " [mode=" + mode + ", group=" + groupKey + ']';
        }
    }
}
