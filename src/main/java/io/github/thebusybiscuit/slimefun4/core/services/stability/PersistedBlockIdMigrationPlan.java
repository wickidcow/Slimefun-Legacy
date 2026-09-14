package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Short-lived authorization for exact persisted Slimefun block-id replacements. */
public final class PersistedBlockIdMigrationPlan {

    private static final int SHORT_FINGERPRINT_LENGTH = 12;

    private final long generation;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final List<Entry> entries;
    private final long scannedRecords;
    private final long canonicalRecords;
    private final long unknownRecords;
    private final long missingTargetRecords;
    private final String fingerprint;

    PersistedBlockIdMigrationPlan(
            long generation,
            long createdAtMillis,
            long ttlMillis,
            @Nonnull List<Entry> entries,
            long scannedRecords,
            long canonicalRecords,
            long unknownRecords,
            long missingTargetRecords) {
        if (generation < 1L) throw new IllegalArgumentException("generation must be positive");
        if (ttlMillis < 1L) throw new IllegalArgumentException("ttlMillis must be positive");
        this.generation = generation;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = Math.addExact(createdAtMillis, ttlMillis);
        List<Entry> copy = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
        copy.sort(Comparator.comparing(Entry::locationKey)
                .thenComparing(Entry::legacyId)
                .thenComparing(Entry::canonicalId));
        this.entries = List.copyOf(copy);
        this.scannedRecords = scannedRecords;
        this.canonicalRecords = canonicalRecords;
        this.unknownRecords = unknownRecords;
        this.missingTargetRecords = missingTargetRecords;
        this.fingerprint = calculateFingerprint();
    }

    public long getGeneration() { return generation; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public long getScannedRecords() { return scannedRecords; }
    public long getCanonicalRecords() { return canonicalRecords; }
    public long getUnknownRecords() { return unknownRecords; }
    public long getMissingTargetRecords() { return missingTargetRecords; }
    public int getRewriteCount() { return entries.size(); }
    public @Nonnull List<Entry> entries() { return entries; }
    public @Nonnull String getFingerprint() { return fingerprint; }
    public @Nonnull String getShortFingerprint() {
        return fingerprint.substring(0, Math.min(SHORT_FINGERPRINT_LENGTH, fingerprint.length()));
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public boolean matchesFingerprint(@Nullable String supplied) {
        if (supplied == null) return false;
        String normalized = supplied.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(fingerprint) || normalized.equals(getShortFingerprint());
    }

    private String calculateFingerprint() {
        MessageDigest digest = sha256();
        update(digest, "generation", Long.toString(generation));
        update(digest, "scanned", Long.toString(scannedRecords));
        for (Entry entry : entries) {
            update(digest, "location", entry.locationKey());
            update(digest, "legacy", entry.legacyId());
            update(digest, "canonical", entry.canonicalId());
        }
        return toHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String key, String value) {
        digest.update(key.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '=');
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    public record Entry(String locationKey, String legacyId, String canonicalId) {
        public Entry {
            locationKey = requireText(locationKey, "locationKey");
            legacyId = requireText(legacyId, "legacyId");
            canonicalId = requireText(canonicalId, "canonicalId");
            if (legacyId.equals(canonicalId)) {
                throw new IllegalArgumentException("legacyId and canonicalId must differ");
            }
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return text;
    }
}
