package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Getter;

/**
 * Base container for Slimefun-specific data, including its item identity and pending-removal state.
 *
 * @author NoRainCity
 * @see ADataController
 */
public abstract class ASlimefunDataContainer extends ADataContainer {
    @Getter
    private final String sfId;

    @Getter
    private volatile boolean pendingRemove = false;

    // Weak ownership avoids retaining containers that are permanently removed. An empty value
    // represents a pending deletion, not an absent change or an empty persisted string.
    private static final WeakHashMap<ASlimefunDataContainer, Map<String, Optional<String>>> capturedPendingRemoveData =
            new WeakHashMap<>();

    protected void setWhilePendingRemove(String key, @Nullable String value) {
        if (pendingRemove) {
            Map<String, Optional<String>> map;
            synchronized (capturedPendingRemoveData) {
                map = capturedPendingRemoveData.computeIfAbsent(this, (k) -> new ConcurrentHashMap<>());
            }
            map.put(key, Optional.ofNullable(value));
        }
    }

    public void setPendingRemove(boolean val) {
        if (val) {
            pendingRemove = true;
        } else {
            // Replay changes only when removal is cancelled. Permanently removed containers
            // stay weakly owned and never write their captured changes back to storage.
            pendingRemove = false;
            Map<String, Optional<String>> map;
            synchronized (capturedPendingRemoveData) {
                map = capturedPendingRemoveData.remove(this);
            }
            if (map != null) {
                for (var entry : map.entrySet()) {
                    if (entry.getValue().isPresent()) {
                        setData(entry.getKey(), entry.getValue().get());
                    } else {
                        // removeData already removed the cached value. Calling it again could
                        // skip the database update, so explicitly submit the retained deletion.
                        // Clear any value supplied by a load that completed while removal waited.
                        removeCacheInternal(entry.getKey());
                        scheduleUpdateData(entry.getKey());
                    }
                }
            }
        }
    }

    @ParametersAreNonnullByDefault
    public void setData(String key, String val) {
        checkData();
        setCacheInternal(key, val, true);
        if (isPendingRemove()) {
            // Do not save removed or virtual block data unless removal is later cancelled.
            setWhilePendingRemove(key, val);
            return;
        } else {
            scheduleUpdateData(key);
        }
    }

    @ParametersAreNonnullByDefault
    public void removeData(String key) {
        if (removeCacheInternal(key) != null || !isDataLoaded()) {
            if (isPendingRemove()) {
                setWhilePendingRemove(key, null);
            } else {
                scheduleUpdateData(key);
            }
        }
    }

    @ParametersAreNonnullByDefault
    public abstract void scheduleUpdateData(String key);

    public ASlimefunDataContainer(String key, String sfId) {
        super(key);
        this.sfId = sfId;
    }

    public ASlimefunDataContainer(String key, ADataContainer other, String sfId) {
        super(key, other);
        this.sfId = sfId;
    }
}
