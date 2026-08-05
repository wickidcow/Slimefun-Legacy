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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
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
 * Guards Slimefun Guide rendering and navigation against recursive, broken, or excessively slow addon calls.
 *
 * <p>Slimefun Legacy 4.1.18 adds cumulative runtime diagnostics and suppressed-warning accounting while preserving the
 * Phase 1A/1B public entry points used by the classic, enhanced, nested, search, bookmark, and history guide paths.
 *
 * <p>This class deliberately catches only failures that can reasonably originate from guide implementations:
 * {@link RuntimeException}, {@link LinkageError}, and {@link StackOverflowError}. Fatal JVM errors are not swallowed.
 */
public final class GuideRuntimeGuard {

    private static final int MAX_NESTED_GUIDE_CALLS = 12;
    private static final long SLOW_GUIDE_CALL_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long WARNING_COOLDOWN_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final long SUMMARY_INTERVAL_MILLIS = Duration.ofMinutes(5).toMillis();

    private static final ThreadLocal<Deque<GuideCall>> ACTIVE_CALLS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<String, WarningState> WARNINGS = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_SUMMARY = new AtomicLong();

    private static final LongAdder TOTAL_CALLS = new LongAdder();
    private static final LongAdder FAILED_CALLS = new LongAdder();
    private static final LongAdder RECURSIVE_CALLS = new LongAdder();
    private static final LongAdder SLOW_CALLS = new LongAdder();
    private static final LongAdder FALLBACKS_USED = new LongAdder();
    private static final LongAdder SUPPRESSED_WARNINGS = new LongAdder();

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

        TOTAL_CALLS.increment();
        GuideCall call = new GuideCall(operation, mode, safeGroupKey(itemGroup));
        Deque<GuideCall> stack = ACTIVE_CALLS.get();
        int depth = stack.size() + 1;

        if (stack.size() >= MAX_NESTED_GUIDE_CALLS || stack.contains(call)) {
            RECURSIVE_CALLS.increment();
            FALLBACKS_USED.increment();
            reportRecursiveCall(player, call, itemGroup, stack, playerFacing);
            maybeReportSummary();
            return fallback;
        }

        stack.addLast(call);
        long started = System.nanoTime();

        try {
            return supplier.get();
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            FAILED_CALLS.increment();
            FALLBACKS_USED.increment();
            reportFailure(player, call, itemGroup, stack, failure, playerFacing);
            return fallback;
        } finally {
            long elapsed = System.nanoTime() - started;
            if (elapsed >= SLOW_GUIDE_CALL_NANOS) {
                SLOW_CALLS.increment();
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

            maybeReportSummary();
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
                        + describeContext(
                                player,
                                call,
                                itemGroup,
                                stack,
                                "elapsed=" + elapsedMillis + "ms, depth=" + depth),
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
        WarningState state = WARNINGS.computeIfAbsent(key, ignored -> new WarningState());
        long suppressed;

        synchronized (state) {
            if (state.lastEmitted != 0L && now - state.lastEmitted < WARNING_COOLDOWN_MILLIS) {
                state.suppressed++;
                SUPPRESSED_WARNINGS.increment();
                return;
            }

            suppressed = state.suppressed;
            state.suppressed = 0L;
            state.lastEmitted = now;
        }

        String completeMessage = suppressed == 0L ? message : message + " [suppressedSinceLast=" + suppressed + ']';
        if (failure == null) {
            Slimefun.logger().warning(completeMessage);
        } else {
            Slimefun.logger().log(Level.SEVERE, completeMessage, failure);
        }
    }

    private static void maybeReportSummary() {
        long incidents = FAILED_CALLS.sum() + RECURSIVE_CALLS.sum() + SLOW_CALLS.sum();
        if (incidents == 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = LAST_SUMMARY.get();
        if (previous != 0L && now - previous < SUMMARY_INTERVAL_MILLIS) {
            return;
        }
        if (!LAST_SUMMARY.compareAndSet(previous, now)) {
            return;
        }

        Slimefun.logger()
                .warning("Slimefun Guide runtime summary [calls=" + TOTAL_CALLS.sum()
                        + ", failures=" + FAILED_CALLS.sum()
                        + ", recursive=" + RECURSIVE_CALLS.sum()
                        + ", slow=" + SLOW_CALLS.sum()
                        + ", fallbacks=" + FALLBACKS_USED.sum()
                        + ", suppressedWarnings=" + SUPPRESSED_WARNINGS.sum() + ']');
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

    private static final class WarningState {
        private long lastEmitted;
        private long suppressed;
    }

    private record GuideCall(String operation, SlimefunGuideMode mode, String groupKey) {
        @Override
        public @Nonnull String toString() {
            return operation + " [mode=" + mode + ", group=" + groupKey + ']';
        }
    }
}
