package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory recovery indexes only. No world access, persistence writes, or identity conversions. */
final class InventoryRecoveryLocations {
    private final Map<String, Set<String>> locationsByOwner = new HashMap<>();
    private final Map<String, Set<String>> ownersByLocation = new HashMap<>();

    synchronized void remember(String owner, String location) {
        if (location == null) {
            return; // An unknown position does not remove the UUID-level guard.
        }
        locationsByOwner.computeIfAbsent(owner, ignored -> new HashSet<>()).add(location);
        ownersByLocation.computeIfAbsent(location, ignored -> new HashSet<>()).add(owner);
    }

    synchronized void clear(String owner) {
        var locations = locationsByOwner.remove(owner);
        if (locations == null) {
            return;
        }
        for (String location : locations) {
            var owners = ownersByLocation.get(location);
            owners.remove(owner);
            if (owners.isEmpty()) {
                ownersByLocation.remove(location);
            }
        }
    }

    synchronized boolean isEmpty() {
        return ownersByLocation.isEmpty();
    }

    synchronized boolean containsLocation(String location) {
        return ownersByLocation.containsKey(location);
    }

    synchronized Set<String> locationKeys() {
        return Set.copyOf(ownersByLocation.keySet());
    }

    /** Recognizes only the two location representations already read by UniversalBlockData. */
    static String canonicalLocationKey(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            String world;
            String[] coordinates;
            boolean historical = stored.startsWith("[world=") && stored.endsWith("]");
            if (historical) {
                String[] parts = stored.substring(1, stored.length() - 1).split(",", -1);
                if (parts.length != 4
                        || !parts[1].startsWith("x=")
                        || !parts[2].startsWith("y=")
                        || !parts[3].startsWith("z=")) {
                    return null;
                }
                world = parts[0].substring("world=".length());
                coordinates = new String[] {parts[1].substring(2), parts[2].substring(2), parts[3].substring(2)};
            } else {
                int separator = stored.lastIndexOf(';');
                if (separator <= 0) {
                    return null;
                }
                world = stored.substring(0, separator);
                coordinates = stored.substring(separator + 1).split(":", -1);
            }
            if (world.isEmpty() || coordinates.length != 3) {
                return null;
            }
            int[] values = new int[3];
            for (int i = 0; i < 3; i++) {
                double coordinate = Double.parseDouble(coordinates[i]);
                if (!Double.isFinite(coordinate) || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
                    return null;
                }
                // Match the old human-readable reader's integer cast; canonical Location
                // coordinates use Bukkit's block-coordinate floor, including negative values.
                values[i] = historical ? (int) coordinate : (int) Math.floor(coordinate);
            }
            return world + ";" + values[0] + ":" + values[1] + ":" + values[2];
        } catch (IllegalArgumentException malformed) {
            return null; // Keep the original record and UUID guard; do not guess a position.
        }
    }

    static boolean isInChunk(String key, String worldPrefix, int chunkX, int chunkZ) {
        String[] coordinates = key.substring(worldPrefix.length()).split(":", -1);
        try {
            if (coordinates.length != 3) {
                return true;
            }
            return (Integer.parseInt(coordinates[0]) >> 4) == chunkX
                    && (Integer.parseInt(coordinates[2]) >> 4) == chunkZ;
        } catch (NumberFormatException malformed) {
            return true; // A malformed same-world recovery key must not authorize bulk deletion.
        }
    }
}
