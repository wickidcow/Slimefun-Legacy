package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
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

/** Short-lived authorization for exact legacy persisted item payload conversion. */
public final class PersistedItemFormatMigrationPlan {

    private static final int SHORT_FINGERPRINT_LENGTH = 12;

    private final long generation;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final List<Entry> entries;
    private final long scannedRecords;
    private final long currentRecords;
    private final long unreadableLegacyRecords;
    private final List<String> unreadableSamples;
    private final String fingerprint;

    PersistedItemFormatMigrationPlan(
            long generation,
            long createdAtMillis,
            long ttlMillis,
            @Nonnull List<Entry> entries,
            long scannedRecords,
            long currentRecords,
            long unreadableLegacyRecords,
            @Nonnull List<String> unreadableSamples) {
        if (generation < 1L) throw new IllegalArgumentException("generation must be positive");
        if (ttlMillis < 1L) throw new IllegalArgumentException("ttlMillis must be positive");
        this.generation = generation;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = Math.addExact(createdAtMillis, ttlMillis);
        List<Entry> copy = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
        copy.sort(Comparator.comparing(Entry::identity).thenComparing(Entry::expectedValueHash));
        this.entries = List.copyOf(copy);
        this.scannedRecords = scannedRecords;
        this.currentRecords = currentRecords;
        this.unreadableLegacyRecords = unreadableLegacyRecords;
        this.unreadableSamples = List.copyOf(Objects.requireNonNull(unreadableSamples, "unreadableSamples"));
        this.fingerprint = calculateFingerprint();
    }

    public long getGeneration() { return generation; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public long getScannedRecords() { return scannedRecords; }
    public long getCurrentRecords() { return currentRecords; }
    public long getUnreadableLegacyRecords() { return unreadableLegacyRecords; }
    public int getRewriteCount() { return entries.size(); }
    public @Nonnull List<Entry> entries() { return entries; }
    public @Nonnull List<String> getUnreadableSamples() { return unreadableSamples; }
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
        update(digest, "current", Long.toString(currentRecords));
        update(digest, "unreadable", Long.toString(unreadableLegacyRecords));
        for (Entry entry : entries) {
            update(digest, "scope", entry.scope().name());
            update(digest, "owner", entry.ownerKey());
            update(digest, "slot", entry.slotKey());
            update(digest, "stored", entry.expectedValueHash());
        }
        return toHex(digest.digest());
    }

    static @Nonnull String hashStoredValue(boolean binary, @Nullable byte[] bytes, @Nullable String text) {
        MessageDigest digest = sha256();
        digest.update((byte) (binary ? 1 : 0));
        if (binary && bytes != null) {
            digest.update(bytes);
        } else if (!binary && text != null) {
            digest.update(text.getBytes(StandardCharsets.UTF_8));
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

    public record Entry(DataScope scope, String ownerKey, String slotKey, String expectedValueHash) {
        public Entry {
            Objects.requireNonNull(scope, "scope");
            if (scope != DataScope.BLOCK_INVENTORY && scope != DataScope.UNIVERSAL_INVENTORY) {
                throw new IllegalArgumentException("Unsupported persisted item scope: " + scope);
            }
            ownerKey = requireText(ownerKey, "ownerKey");
            slotKey = requireText(slotKey, "slotKey");
            expectedValueHash = requireText(expectedValueHash, "expectedValueHash").toLowerCase(Locale.ROOT);
        }

        public String identity() {
            return scope.name() + ':' + ownerKey + ':' + slotKey;
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return text;
    }
}
