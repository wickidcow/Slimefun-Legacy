package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BackpackCache {
    private static volatile BackpackCache activeCache;

    private final Map<String, Map<Integer, PlayerBackpack>> numCache;
    private final Map<String, PlayerBackpack> uuidCache;
    private final Map<String, PlayerBackpack> maintenanceOwned;

    BackpackCache() {
        numCache = new HashMap<>();
        uuidCache = new HashMap<>();
        maintenanceOwned = new HashMap<>();
        activeCache = this;
    }

    /**
     * Adds a backpack for normal gameplay access and returns the canonical cached instance.
     * Access through this method promotes a maintenance-loaded backpack so a maintenance scan
     * cannot evict it while a player is using it.
     */
    synchronized PlayerBackpack put(PlayerBackpack backpack) {
        String uuid = backpack.getUniqueId().toString();
        PlayerBackpack canonical = uuidCache.get(uuid);
        if (canonical == null) {
            canonical = backpack;
            uuidCache.put(uuid, canonical);
        }

        maintenanceOwned.remove(uuid);
        putNumberReference(canonical);
        return canonical;
    }

    /** Adds a backpack only for a maintenance scan without replacing a gameplay instance. */
    synchronized MaintenanceResult putForMaintenance(PlayerBackpack backpack) {
        String uuid = backpack.getUniqueId().toString();
        PlayerBackpack existing = uuidCache.get(uuid);
        if (existing != null) {
            return new MaintenanceResult(existing, false);
        }

        uuidCache.put(uuid, backpack);
        putNumberReference(backpack);
        maintenanceOwned.put(uuid, backpack);
        return new MaintenanceResult(backpack, true);
    }

    public synchronized PlayerBackpack get(String pUuid, int num) {
        Map<Integer, PlayerBackpack> map = numCache.get(pUuid);
        PlayerBackpack backpack = map == null ? null : map.get(num);
        promote(backpack);
        return backpack;
    }

    public synchronized PlayerBackpack get(String uuid) {
        PlayerBackpack backpack = uuidCache.get(uuid);
        promote(backpack);
        return backpack;
    }

    /** Looks up a cached backpack without promoting maintenance ownership. */
    synchronized PlayerBackpack peek(String uuid) {
        return uuidCache.get(uuid);
    }

    /** Returns whether any gameplay or maintenance instance is currently cached for this backpack UUID. */
    synchronized boolean isCached(String uuid) {
        return uuidCache.containsKey(uuid);
    }

    /**
     * Runs a complete direct-storage maintenance batch only while every referenced backpack remains uncached.
     * The cache monitor is held for the whole batch so gameplay cannot install or promote one of these backpacks
     * between validation, mutation and rollback.
     */
    synchronized boolean runIfAllUncached(Collection<String> uuids, Runnable action) {
        for (String uuid : uuids) {
            if (uuidCache.containsKey(uuid)) {
                return false;
            }
        }
        action.run();
        return true;
    }

    /** Returns whether raw profile-storage maintenance has an authoritative cache guard available. */
    static boolean hasActiveControllerCache() {
        return activeCache != null;
    }

    /** Read-only cache check used by raw profile-storage maintenance. */
    static boolean isCachedInActiveController(String uuid) {
        BackpackCache cache = activeCache;
        return cache == null || cache.isCached(uuid);
    }

    /**
     * Executes a raw profile-storage batch only while the authoritative cache proves all UUIDs are uncached.
     * If the active cache is unavailable, maintenance fails closed and the action is not run.
     */
    static boolean runIfAllUncachedInActiveController(Collection<String> uuids, Runnable action) {
        BackpackCache cache = activeCache;
        return cache != null && cache.runIfAllUncached(uuids, action);
    }

    /**
     * Releases a backpack loaded only for maintenance. If normal gameplay accessed the instance
     * in the meantime, it was promoted and this method intentionally leaves it cached.
     */
    synchronized void releaseMaintenance(PlayerBackpack backpack) {
        String uuid = backpack.getUniqueId().toString();
        if (maintenanceOwned.get(uuid) != backpack) {
            return;
        }

        maintenanceOwned.remove(uuid);
        uuidCache.remove(uuid, backpack);
        String ownerUuid = backpack.getOwner().getUniqueId().toString();
        Map<Integer, PlayerBackpack> ownerCache = numCache.get(ownerUuid);
        if (ownerCache != null) {
            ownerCache.remove(backpack.getId(), backpack);
            if (ownerCache.isEmpty()) {
                numCache.remove(ownerUuid);
            }
        }
    }

    public synchronized void invalidate(String pUuid) {
        Map<Integer, PlayerBackpack> cache = numCache.remove(pUuid);
        if (cache == null) {
            return;
        }

        cache.values().forEach(backpack -> {
            String uuid = backpack.getUniqueId().toString();
            uuidCache.remove(uuid, backpack);
            maintenanceOwned.remove(uuid, backpack);
            backpack.markInvalid();
        });
    }

    synchronized void clean() {
        maintenanceOwned.clear();
        numCache.clear();
        uuidCache.clear();
        if (activeCache == this) {
            activeCache = null;
        }
    }

    private void putNumberReference(PlayerBackpack backpack) {
        numCache.computeIfAbsent(backpack.getOwner().getUniqueId().toString(), ignored -> new HashMap<>())
                .putIfAbsent(backpack.getId(), backpack);
    }

    private void promote(PlayerBackpack backpack) {
        if (backpack != null) {
            maintenanceOwned.remove(backpack.getUniqueId().toString(), backpack);
        }
    }

    record MaintenanceResult(PlayerBackpack backpack, boolean maintenanceOwned) {}
}
