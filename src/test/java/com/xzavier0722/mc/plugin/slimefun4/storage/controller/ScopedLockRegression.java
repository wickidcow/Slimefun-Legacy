package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Real-thread scope-lock regressions; no database, Bukkit or scheduler substitute. */
final class ScopedLockRegression {
    @FunctionalInterface
    interface Checked {
        void run() throws Exception;
    }

    record Case(String name, Checked body) {}

    static List<Case> cases() {
        return List.of(
                new Case("reserved entrant retains the registered lock", ScopedLockRegression::reservedEntry),
                new Case("same-scope critical sections cannot split", ScopedLockRegression::splitCriticalSection),
                new Case("multiple reservations survive the last owner", ScopedLockRegression::multipleReservations),
                new Case("last release removes the unused entry", ScopedLockRegression::simpleRelease),
                new Case("reentrant holds retain their entry", ScopedLockRegression::reentrantRelease),
                new Case("missing unlock remains a no-op", ScopedLockRegression::missingUnlock),
                new Case("non-owner unlock preserves reservations", ScopedLockRegression::nonOwnerUnlock),
                new Case("equivalent keys share exclusion", ScopedLockRegression::equalKeys),
                new Case("equal-hash different scopes progress independently", ScopedLockRegression::hashCollision),
                new Case("interrupt status and uninterruptible acquisition survive", ScopedLockRegression::interruption),
                new Case("finished scopes do not accumulate", ScopedLockRegression::retirement),
                new Case("contended reentrant updates stay exclusive", ScopedLockRegression::contention));
    }

    public static void main(String[] args) throws Exception {
        List<Case> selected = args.length == 1 && args[0].equals("--control") ? cases().subList(0, 2) : cases();
        int failures = 0;
        for (Case test : selected) {
            try {
                test.body.run();
                System.out.println("PASS " + test.name);
            } catch (Throwable failure) {
                failures++;
                System.out.println("FAIL " + test.name + ": " + failure);
                failure.printStackTrace(System.out);
            }
        }
        System.out.println("SUMMARY cases=" + selected.size() + " failures=" + failures);
        if (failures != 0) {
            throw new AssertionError(failures + " regression case(s) failed");
        }
    }

    private static void reservedEntry() throws Exception {
        var scoped = new ScopedLock();
        var map = installMap(scoped);
        var key = key("reserved");
        scoped.lock(key);
        var errors = new ConcurrentLinkedQueue<Throwable>();
        Thread entrant = worker("reserved-entrant", errors, () -> {
            scoped.lock(key);
            try {
                check(scoped.hasLock(key), "Acquired lock lost its registry entry");
            } finally {
                scoped.unlock(key);
            }
        });
        map.pauseThread = entrant;
        Object original = map.get(key);
        entrant.start();
        try {
            await(map.reserved);
            scoped.unlock(key);
            check(map.get(key) == original, "Cleanup retired the entry while a caller retained it");
        } finally {
            map.proceed.countDown();
            join(entrant);
        }
        clean(errors);
        check(map.isEmpty(), "Finished reservation leaked its entry");
    }

    private static void splitCriticalSection() throws Exception {
        var scoped = new ScopedLock();
        var map = installMap(scoped);
        var key = key("critical-section");
        var active = new AtomicInteger();
        var overlap = new AtomicBoolean();
        var errors = new ConcurrentLinkedQueue<Throwable>();
        var outsiderInside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        scoped.lock(key);
        Thread reserved = worker("reserved-entrant", errors, () -> {
            scoped.lock(key);
            try {
                if (active.incrementAndGet() != 1) {
                    overlap.set(true);
                }
                try {
                    await(release);
                } finally {
                    active.decrementAndGet();
                }
            } finally {
                scoped.unlock(key);
            }
        });
        map.pauseThread = reserved;
        Thread outsider = worker("later-entrant", errors, () -> {
            scoped.lock(key);
            try {
                if (active.incrementAndGet() != 1) {
                    overlap.set(true);
                }
                outsiderInside.countDown();
                try {
                    await(release);
                } finally {
                    active.decrementAndGet();
                }
            } finally {
                scoped.unlock(key);
            }
        });
        reserved.start();
        try {
            await(map.reserved);
            scoped.unlock(key);
            outsider.start();
            await(outsiderInside);
            map.proceed.countDown();
            await(map.resumed);
            awaitWaiting(reserved);
            check(!overlap.get(), "Two threads entered one scope through different lock objects");
        } finally {
            map.proceed.countDown();
            release.countDown();
            join(reserved);
            if (outsider.getState() != Thread.State.NEW) {
                join(outsider);
            }
        }
        clean(errors);
        check(active.get() == 0 && map.isEmpty(), "Scope did not finish cleanly");
    }

    private static void multipleReservations() throws Exception {
        var scoped = new ScopedLock();
        var map = installMap(scoped);
        var key = key("many-reservations");
        var errors = new ConcurrentLinkedQueue<Throwable>();
        var threads = new ArrayList<Thread>();
        scoped.lock(key);
        Object original = map.get(key);
        for (int index = 0; index < 8; index++) {
            Thread thread = worker("waiting-" + index, errors, () -> {
                scoped.lock(key);
                try {
                    check(scoped.hasLock(key), "Waiter acquired an unregistered lock");
                } finally {
                    scoped.unlock(key);
                }
            });
            threads.add(thread);
            thread.start();
            awaitWaiting(thread);
        }
        Thread paused = worker("reserved-entrant", errors, () -> {
            scoped.lock(key);
            scoped.unlock(key);
        });
        map.pauseThread = paused;
        paused.start();
        try {
            await(map.reserved);
            scoped.unlock(key);
            for (Thread thread : threads) {
                join(thread);
            }
            check(map.get(key) == original, "The final active owner dropped a remaining reservation");
        } finally {
            map.proceed.countDown();
            for (Thread thread : threads) {
                join(thread);
            }
            join(paused);
        }
        clean(errors);
        check(map.isEmpty(), "Final waiter did not retire the entry");
    }

    private static void simpleRelease() throws Exception {
        var scoped = new ScopedLock();
        var key = key("simple");
        check(!scoped.hasLock(key), "Unexpected initial entry");
        scoped.lock(key);
        check(scoped.hasLock(key), "Missing owned entry");
        scoped.unlock(key);
        check(!scoped.hasLock(key), "Unused lock retained");
    }

    private static void reentrantRelease() throws Exception {
        var scoped = new ScopedLock();
        var key = key("recursive");
        for (int i = 0; i < 100; i++) {
            scoped.lock(key);
        }
        for (int i = 0; i < 99; i++) {
            scoped.unlock(key);
            check(scoped.hasLock(key), "Partially released reentrant lock disappeared");
        }
        scoped.unlock(key);
        check(!scoped.hasLock(key), "Reentrant entry leaked");
    }

    private static void missingUnlock() {
        var scoped = new ScopedLock();
        var key = key("absent");
        scoped.unlock(key);
        scoped.unlock(key);
        check(!scoped.hasLock(key), "Missing unlock created an entry");
    }

    private static void nonOwnerUnlock() throws Exception {
        var scoped = new ScopedLock();
        var key = key("owner");
        var errors = new ConcurrentLinkedQueue<Throwable>();
        var observed = new AtomicReference<Throwable>();
        scoped.lock(key);
        Thread other = worker("non-owner", errors, () -> {
            try {
                scoped.unlock(key);
            } catch (Throwable failure) {
                observed.set(failure);
            }
        });
        other.start();
        try {
            join(other);
            check(observed.get() instanceof IllegalMonitorStateException, "Non-owner unlock was accepted");
            check(scoped.hasLock(key), "Non-owner unlock removed the owner's entry");
        } finally {
            scoped.unlock(key);
        }
        clean(errors);
        check(!scoped.hasLock(key), "Failed unlock changed the reservation count");
    }

    private static void equalKeys() throws Exception {
        var scoped = new ScopedLock();
        var first = key("same-value");
        var second = key("same-value");
        var errors = new ConcurrentLinkedQueue<Throwable>();
        var acquired = new AtomicBoolean();
        check(first != second && first.equals(second) && first.hashCode() == second.hashCode(), "Invalid key fixture");
        scoped.lock(first);
        Thread waiter = worker("equivalent-key", errors, () -> {
            scoped.lock(second);
            try {
                acquired.set(true);
            } finally {
                scoped.unlock(second);
            }
        });
        waiter.start();
        try {
            awaitWaiting(waiter);
            check(!acquired.get(), "Equivalent key bypassed the owner");
        } finally {
            scoped.unlock(first);
            join(waiter);
        }
        clean(errors);
        check(acquired.get() && !scoped.hasLock(first), "Equivalent key did not finish");
    }

    private static void hashCollision() throws Exception {
        var scoped = new ScopedLock();
        ScopeKey first = new CollisionKey(DataScope.BLOCK_DATA);
        ScopeKey second = new CollisionKey(DataScope.CHUNK_DATA);
        var errors = new ConcurrentLinkedQueue<Throwable>();
        scoped.lock(first);
        Thread sameScopeWaiter = worker("same-bin-waiter", errors, () -> {
            scoped.lock(first);
            scoped.unlock(first);
        });
        Thread independent = worker("same-bin-other-key", errors, () -> {
            scoped.lock(second);
            scoped.unlock(second);
        });
        sameScopeWaiter.start();
        try {
            awaitWaiting(sameScopeWaiter);
            independent.start();
            join(independent);
            check(scoped.hasLock(first), "Unrelated completion removed first scope");
        } finally {
            scoped.unlock(first);
            join(sameScopeWaiter);
        }
        clean(errors);
    }

    private static void interruption() throws Exception {
        var scoped = new ScopedLock();
        var key = key("interrupt");
        var errors = new ConcurrentLinkedQueue<Throwable>();
        var preserved = new AtomicBoolean();
        scoped.lock(key);
        Thread thread = worker("interrupted-waiter", errors, () -> {
            Thread.currentThread().interrupt();
            scoped.lock(key);
            try {
                preserved.set(Thread.currentThread().isInterrupted());
            } finally {
                scoped.unlock(key);
            }
        });
        thread.start();
        try {
            awaitWaiting(thread);
        } finally {
            scoped.unlock(key);
            join(thread);
        }
        clean(errors);
        check(preserved.get() && !scoped.hasLock(key), "Uninterruptible lock behavior changed");
    }

    private static void retirement() throws Exception {
        var scoped = new ScopedLock();
        var map = installMap(scoped);
        for (int i = 0; i < 4000; i++) {
            var key = key("location-" + i);
            scoped.lock(key);
            scoped.unlock(key);
        }
        check(map.isEmpty(), "Idle scope registry grew with visited locations");
    }

    private static void contention() throws Exception {
        var scoped = new ScopedLock();
        var key = key("contended");
        var errors = new ConcurrentLinkedQueue<Throwable>();
        var start = new CountDownLatch(1);
        var active = new AtomicInteger();
        var overlap = new AtomicBoolean();
        int[] updates = {0};
        var threads = new ArrayList<Thread>();
        for (int index = 0; index < 8; index++) {
            Thread thread = worker("contender-" + index, errors, () -> {
                await(start);
                for (int iteration = 0; iteration < 2000; iteration++) {
                    scoped.lock(key);
                    try {
                        if (active.incrementAndGet() != 1) {
                            overlap.set(true);
                        }
                        try {
                            scoped.lock(key);
                            try {
                                updates[0]++;
                            } finally {
                                scoped.unlock(key);
                            }
                        } finally {
                            active.decrementAndGet();
                        }
                    } finally {
                        scoped.unlock(key);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            join(thread);
        }
        clean(errors);
        check(!overlap.get() && updates[0] == 16000, "Contended updates lost mutual exclusion");
        check(!scoped.hasLock(key), "Contended lock leaked after completion");
    }

    private static RecordKey key(String value) {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.LOCATION, value);
        return key;
    }

    private static Thread worker(String name, ConcurrentLinkedQueue<Throwable> errors, Checked action) {
        Thread thread = new Thread(() -> {
            try {
                action.run();
            } catch (Throwable failure) {
                errors.add(failure);
            }
        }, name);
        // Even an intentionally broken original-code control must not keep the test JVM alive.
        thread.setDaemon(true);
        return thread;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        check(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for controlled interleaving");
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
            check(thread.isAlive() && System.nanoTime() < deadline, "Thread did not reach a blocked state: " + thread);
            Thread.yield();
        }
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(5000);
        check(!thread.isAlive(), "Worker did not complete: " + thread.getName());
    }

    private static void clean(ConcurrentLinkedQueue<Throwable> errors) {
        check(errors.isEmpty(), "Worker failure(s): " + errors);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static PausingMap installMap(ScopedLock scoped) throws ReflectiveOperationException {
        var field = ScopedLock.class.getDeclaredField("locks");
        field.setAccessible(true);
        var map = new PausingMap();
        field.set(scoped, map);
        return map;
    }

    /** Pauses after atomic registration returns, before lock acquisition, in old and fixed implementations. */
    private static final class PausingMap extends ConcurrentHashMap<ScopeKey, Object> {
        private static final long serialVersionUID = 1L;
        private transient volatile Thread pauseThread;
        private final transient CountDownLatch reserved = new CountDownLatch(1);
        private final transient CountDownLatch proceed = new CountDownLatch(1);
        private final transient CountDownLatch resumed = new CountDownLatch(1);
        private final AtomicBoolean paused = new AtomicBoolean();

        @Override
        public Object computeIfAbsent(ScopeKey key, Function<? super ScopeKey, ?> mapping) {
            return pause(super.computeIfAbsent(key, mapping));
        }

        @Override
        public Object compute(ScopeKey key, BiFunction<? super ScopeKey, ? super Object, ?> remapping) {
            return pause(super.compute(key, remapping));
        }

        private Object pause(Object entry) {
            if (Thread.currentThread() == pauseThread && paused.compareAndSet(false, true)) {
                reserved.countDown();
                try {
                    await(proceed);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                resumed.countDown();
            }
            return entry;
        }
    }

    private static final class CollisionKey extends ScopeKey {
        private CollisionKey(DataScope scope) {
            super(scope);
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }
}
