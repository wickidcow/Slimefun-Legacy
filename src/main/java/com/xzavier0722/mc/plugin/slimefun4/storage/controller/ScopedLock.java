package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

class ScopedLock {
    private final Map<ScopeKey, LockEntry> locks;

    ScopedLock() {
        locks = new ConcurrentHashMap<>();
    }

    void lock(ScopeKey scopeKey) {
        // Reserve the entry before waiting: an unlocked lock can still have callers
        // that already obtained its reference and must not switch to another lock.
        var entry = locks.compute(scopeKey, (key, current) -> {
            var retained = current == null ? new LockEntry() : current;
            retained.users++;
            return retained;
        });
        // Never wait for the scope lock inside a map computation. Equal-hash scopes
        // must remain independent, including when the current owner re-enters.
        entry.lock.lock();
    }

    void unlock(ScopeKey scopeKey) {
        locks.computeIfPresent(scopeKey, (key, entry) -> {
            // A non-owner still throws without releasing anyone else's reservation.
            entry.lock.unlock();
            return --entry.users == 0 ? null : entry;
        });
    }

    boolean hasLock(ScopeKey scopeKey) {
        return locks.containsKey(scopeKey);
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        // Accessed only inside this key's atomic map computations. Counts both
        // held acquisitions (including re-entry) and callers waiting to acquire.
        private int users;
    }
}
