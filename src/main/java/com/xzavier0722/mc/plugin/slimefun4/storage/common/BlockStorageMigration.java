package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, already-decoded source snapshot for the existing block-to-universal migration.
 * Stored payloads stay opaque: this plan does not re-encode items or change a database schema.
 */
public final class BlockStorageMigration {
    private final String location;
    private final String chunk;
    private final String slimefunId;
    private final UUID destination;
    private final Map<String, String> sourceData;
    private final Map<String, String> destinationData;
    private final Map<Integer, byte[]> inventory;

    public BlockStorageMigration(
            String location,
            String chunk,
            String slimefunId,
            UUID destination,
            String encodedLocation,
            Map<String, String> sourceData,
            Map<Integer, byte[]> inventory) {
        this.location = nonBlank(location, "location");
        this.chunk = nonBlank(chunk, "chunk");
        this.slimefunId = nonBlank(slimefunId, "slimefunId");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.sourceData = Collections.unmodifiableMap(new HashMap<>(sourceData));
        this.sourceData.forEach((key, value) -> {
            if (key == null || key.isEmpty() || value == null) {
                throw new IllegalArgumentException("Migration source contains an invalid custom-data entry");
            }
        });
        var target = new HashMap<>(this.sourceData);
        String previous = target.putIfAbsent("location", Objects.requireNonNull(encodedLocation, "encodedLocation"));
        if (previous != null && !previous.equals(encodedLocation)) {
            throw new IllegalArgumentException("Migration source conflicts with the reserved universal location key");
        }
        this.destinationData = Collections.unmodifiableMap(target);
        this.inventory = Collections.unmodifiableMap(copyInventory(inventory));
    }

    public String location() {
        return location;
    }

    public String chunk() {
        return chunk;
    }

    public String slimefunId() {
        return slimefunId;
    }

    public UUID destination() {
        return destination;
    }

    public String traits() {
        return "BLOCK,INVENTORY";
    }

    public Map<String, String> sourceData() {
        return sourceData;
    }

    public Map<String, String> destinationData() {
        return destinationData;
    }

    public int inventorySize() {
        return inventory.size();
    }

    public Map<Integer, byte[]> inventory() {
        return Collections.unmodifiableMap(copyInventory(inventory));
    }

    public boolean matchesInventory(Map<Integer, byte[]> other) {
        if (!inventory.keySet().equals(other.keySet())) return false;
        for (var slot : inventory.keySet()) {
            if (!Arrays.equals(inventory.get(slot), other.get(slot))) return false;
        }
        return true;
    }

    private static Map<Integer, byte[]> copyInventory(Map<Integer, byte[]> source) {
        Map<Integer, byte[]> copy = new HashMap<>();
        source.forEach((slot, bytes) -> {
            if (slot == null || slot < 0 || slot >= 54) {
                throw new IllegalArgumentException("Migration source contains an invalid inventory slot");
            }
            copy.put(slot, bytes == null ? null : bytes.clone());
        });
        return copy;
    }

    private static String nonBlank(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) throw new IllegalArgumentException(name + " is blank");
        return value;
    }

    /** A failed commit can be ambiguous; its destination identity must be retained for an exact retry. */
    public static final class Failure extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final boolean restagingSafe;

        public Failure(String message, Throwable cause, boolean restagingSafe) {
            super(message, cause);
            this.restagingSafe = restagingSafe;
        }

        public boolean isRestagingSafe() {
            return restagingSafe;
        }
    }
}
