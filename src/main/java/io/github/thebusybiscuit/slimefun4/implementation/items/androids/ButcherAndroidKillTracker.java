package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.entity.Entity;

/**
 * Tracks the Android context for the synchronous damage/death event boundary.
 *
 * <p>Bukkit metadata used to hold a live {@link AndroidInstance}. A persistent data container
 * cannot safely serialize that object, so Legacy keeps the context in memory only for the
 * duration of the damage call. EntityDeathEvent is fired synchronously by the damage path;
 * surviving targets are cleared immediately afterwards.
 */
public final class ButcherAndroidKillTracker {

    private static final Map<UUID, AndroidInstance> ACTIVE_KILLS = new ConcurrentHashMap<>();

    private ButcherAndroidKillTracker() {}

    public static void mark(@Nonnull Entity entity, @Nonnull AndroidInstance instance) {
        ACTIVE_KILLS.put(entity.getUniqueId(), instance);
    }

    public static @Nullable AndroidInstance consume(@Nonnull Entity entity) {
        return ACTIVE_KILLS.remove(entity.getUniqueId());
    }

    public static void clear(@Nonnull Entity entity) {
        ACTIVE_KILLS.remove(entity.getUniqueId());
    }

    static int trackedEntities() {
        return ACTIVE_KILLS.size();
    }
}
