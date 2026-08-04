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
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Guards public Slimefun Guide entry points against recursive or broken addon guide calls.
 *
 * <p>This class deliberately catches only runtime failures that can reasonably originate from guide implementations:
 * {@link RuntimeException}, {@link LinkageError}, and {@link StackOverflowError}. Fatal JVM errors are not swallowed.
 */
public final class GuideRuntimeGuard {

    private static final int MAX_NESTED_GUIDE_CALLS = 12;
    private static final long SLOW_GUIDE_CALL_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long WARNING_COOLDOWN_MILLIS = Duration.ofMinutes(1).toMillis();

    private static final ThreadLocal<Deque<GuideCall>> ACTIVE_CALLS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<String, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private GuideRuntimeGuard() {}

    /**
     * Executes one guide action with recursion, failure, and slow-call diagnostics.
     *
     * @param profile
     *            Player profile that owns the guide session
     * @param mode
     *            Guide mode being opened
     * @param operation
     *            Human-readable operation name for diagnostics
     * @param itemGroup
     *            Item group involved in the action, or {@code null}
     * @param action
     *            Guide action to execute
     */
    @ParametersAreNonnullByDefault
    public static void run(
            PlayerProfile profile,
            SlimefunGuideMode mode,
            String operation,
            @Nullable ItemGroup itemGroup,
            Runnable action) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        GuideCall call = new GuideCall(operation, mode, safeGroupKey(itemGroup));
        Deque<GuideCall> stack = ACTIVE_CALLS.get();

        if (stack.size() >= MAX_NESTED_GUIDE_CALLS || stack.contains(call)) {
            blockRecursiveCall(player, call, itemGroup, stack.size());
            return;
        }

        stack.addLast(call);
        long started = System.nanoTime();

        try {
            action.run();
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            reportFailure(player, call, itemGroup, failure);
        } finally {
            long elapsed = System.nanoTime() - started;
            if (elapsed >= SLOW_GUIDE_CALL_NANOS) {
                reportSlowCall(call, itemGroup, elapsed);
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

    private static void blockRecursiveCall(
            @Nonnull Player player,
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            int currentDepth) {
        player.closeInventory();
        player.sendMessage(ChatColor.DARK_RED
                + "Slimefun blocked a recursive guide menu. Please tell an administrator to check the console.");

        String warningKey = "recursive|" + call;
        warnOnce(
                warningKey,
                "Blocked recursive Slimefun Guide call " + call
                        + " at depth " + currentDepth
                        + describeOwner(itemGroup),
                null);
    }

    private static void reportFailure(
            @Nonnull Player player,
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            @Nonnull Throwable failure) {
        player.closeInventory();
        player.sendMessage(ChatColor.DARK_RED
                + "Slimefun blocked a broken guide menu. Please tell an administrator to check the console.");

        String warningKey = "failure|" + call + '|' + failure.getClass().getName();
        warnOnce(
                warningKey,
                "Slimefun Guide action failed: " + call + describeOwner(itemGroup),
                failure);
    }

    private static void reportSlowCall(
            @Nonnull GuideCall call,
            @Nullable ItemGroup itemGroup,
            long elapsedNanos) {
        long elapsedMillis = Duration.ofNanos(elapsedNanos).toMillis();
        String warningKey = "slow|" + call;
        warnOnce(
                warningKey,
                "Slow Slimefun Guide action took " + elapsedMillis + " ms: " + call + describeOwner(itemGroup),
                null);
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
