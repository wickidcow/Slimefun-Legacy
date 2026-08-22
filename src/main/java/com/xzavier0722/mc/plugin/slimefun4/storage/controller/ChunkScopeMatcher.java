package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;

/**
 * Matches storage write scopes to a normalized Slimefun chunk identity without resolving Bukkit world objects.
 */
final class ChunkScopeMatcher {

    private ChunkScopeMatcher() {}

    static boolean matches(ScopeKey scopeKey, String chunkKey) {
        if (scopeKey == null || chunkKey == null) {
            return false;
        }

        if (scopeKey instanceof ChunkKey chunkScope) {
            return chunkKey.equals(chunkScope.getChunkKey());
        }

        if (scopeKey instanceof LocationKey locationScope) {
            return chunkKey.equals(chunkKeyFromLocation(locationScope.getLocationKey()));
        }

        if (scopeKey instanceof RecordKey recordKey) {
            for (var condition : recordKey.getConditions()) {
                FieldKey field = condition.getFirstValue();
                String value = condition.getSecondValue();

                if (field == FieldKey.CHUNK && chunkKey.equals(value)) {
                    return true;
                }

                if (field == FieldKey.LOCATION && chunkKey.equals(chunkKeyFromLocation(value))) {
                    return true;
                }
            }
        }

        return false;
    }

    static String chunkKeyFromLocation(String locationKey) {
        if (locationKey == null) {
            throw new IllegalArgumentException("Location key must not be null");
        }

        int separator = locationKey.indexOf(';');
        if (separator <= 0 || separator == locationKey.length() - 1) {
            throw new IllegalArgumentException("Malformed Slimefun location key: " + locationKey);
        }

        String world = locationKey.substring(0, separator);
        String[] coordinates = locationKey.substring(separator + 1).split(":", -1);
        if (coordinates.length != 3) {
            throw new IllegalArgumentException("Malformed Slimefun location key: " + locationKey);
        }

        try {
            int blockX = Integer.parseInt(coordinates[0]);
            int blockZ = Integer.parseInt(coordinates[2]);
            return world + ";" + (blockX >> 4) + ":" + (blockZ >> 4);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Malformed Slimefun location key: " + locationKey, failure);
        }
    }
}
