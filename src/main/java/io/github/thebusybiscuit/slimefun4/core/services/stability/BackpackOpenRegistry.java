package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Atomically reserves backpack access by player and backing backpack identity.
 *
 * <p>A reservation starts in a pending state while the backpack is loading and is then promoted to the canonical
 * resolved UUID before the inventory is opened. The canonical reservation remains held for the entire open session and
 * through the close/quit persistence hand-off. It is released only after that save attempt finishes. This prevents two physical item representations,
 * including mixed legacy and UUID-backed items, from opening the same backing storage at the same time.
 */
public final class BackpackOpenRegistry {

    private final Set<String> backpackKeys = new HashSet<>();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Map<UUID, String> reservations = new HashMap<>();

    public synchronized boolean reserve(@Nonnull UUID playerId, @Nonnull String backpackKey) {
        if (reservations.containsKey(playerId) || backpackKeys.contains(backpackKey)) {
            return false;
        }

        reservations.put(playerId, backpackKey);
        backpackKeys.add(backpackKey);
        pendingPlayers.add(playerId);
        return true;
    }

    /**
     * Promotes a pending reservation to the canonical resolved backpack identity.
     *
     * <p>The transition is atomic. If another pending or active session already owns the canonical key, this method
     * fails without altering the caller's existing reservation so the caller can safely release it.
     */
    public synchronized boolean activate(
            @Nonnull UUID playerId, @Nonnull String expectedKey, @Nonnull String canonicalKey) {
        String reservedKey = reservations.get(playerId);
        if (!expectedKey.equals(reservedKey)) {
            return false;
        }

        if (!expectedKey.equals(canonicalKey) && backpackKeys.contains(canonicalKey)) {
            return false;
        }

        if (!expectedKey.equals(canonicalKey)) {
            backpackKeys.remove(expectedKey);
            backpackKeys.add(canonicalKey);
            reservations.put(playerId, canonicalKey);
        }

        pendingPlayers.remove(playerId);
        return true;
    }

    public synchronized void release(@Nonnull UUID playerId, @Nonnull String backpackKey) {
        if (reservations.remove(playerId, backpackKey)) {
            backpackKeys.remove(backpackKey);
            pendingPlayers.remove(playerId);
        }
    }

    public synchronized void release(@Nonnull UUID playerId) {
        String backpackKey = reservations.remove(playerId);
        if (backpackKey != null) {
            backpackKeys.remove(backpackKey);
        }
        pendingPlayers.remove(playerId);
    }

    /** Returns whether the player is still waiting for a backpack load to finish. */
    public synchronized boolean isOpening(@Nonnull UUID playerId) {
        return pendingPlayers.contains(playerId);
    }
}
