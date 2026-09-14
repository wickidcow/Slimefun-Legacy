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

/**
 * Short-lived authorization for rewriting persisted Slimefun block identities.
 *
 * <p>The plan is intentionally bound to exact storage record keys, source IDs and resolved canonical targets.
 * A later execution must re-scan storage and produce the same candidate set before any mutation is allowed.
 */
public final class LegacyBlockIdMigrationPlan {

    private static final int SHORT_FINGERPRINT_LENGTH = 12;

    private final long generation;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final List<Candidate> candidates;
    private final String fingerprint;

    public LegacyBlockIdMigrationPlan(
            long generation,
            long createdAtMillis,
            long ttlMillis,
            @Nonnull List<Candidate> candidates) {
        if (generation < 1L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (ttlMillis < 1L) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }

        this.generation = generation;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = Math.addExact(createdAtMillis, ttlMillis);

        List<Candidate> copy = new ArrayList<>(Objects.requireNonNull(candidates, "candidates"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("candidates cannot be empty");
        }
        copy.sort(Comparator.comparing(Candidate::storageScope)
                .thenComparing(Candidate::recordKey)
                .thenComparing(Candidate::legacyId)
                .thenComparing(Candidate::canonicalId));

        for (int i = 1; i < copy.size(); i++) {
            Candidate previous = copy.get(i - 1);
            Candidate current = copy.get(i);
            if (previous.storageScope().equals(current.storageScope())
                    && previous.recordKey().equals(current.recordKey())) {
                throw new IllegalArgumentException("one persisted identity record cannot have multiple migration targets");
            }
        }

        this.candidates = List.copyOf(copy);
        this.fingerprint = calculateFingerprint();
    }

    public long getGeneration() {
        return generation;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public int getCandidateCount() {
        return candidates.size();
    }

    public @Nonnull List<Candidate> getCandidates() {
        return candidates;
    }

    public @Nonnull String getFingerprint() {
        return fingerprint;
    }

    public @Nonnull String getShortFingerprint() {
        return fingerprint.substring(0, Math.min(SHORT_FINGERPRINT_LENGTH, fingerprint.length()));
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public boolean matchesFingerprint(@Nullable String supplied) {
        if (supplied == null) {
            return false;
        }
        String normalized = supplied.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(fingerprint) || normalized.equals(getShortFingerprint());
    }

    public boolean matchesCandidates(@Nonnull List<Candidate> currentCandidates) {
        List<Candidate> copy = new ArrayList<>(Objects.requireNonNull(currentCandidates, "currentCandidates"));
        copy.sort(Comparator.comparing(Candidate::storageScope)
                .thenComparing(Candidate::recordKey)
                .thenComparing(Candidate::legacyId)
                .thenComparing(Candidate::canonicalId));
        return candidates.equals(copy);
    }

    private String calculateFingerprint() {
        MessageDigest digest = sha256();
        update(digest, "generation", Long.toString(generation));
        for (Candidate candidate : candidates) {
            update(digest, "scope", candidate.storageScope());
            update(digest, "record", candidate.recordKey());
            update(digest, "source", candidate.legacyId());
            update(digest, "target", candidate.canonicalId());
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

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return text;
    }

    /** Exact persisted identity that was safe to rewrite at scan time. */
    public record Candidate(String storageScope, String recordKey, String legacyId, String canonicalId) {
        public Candidate {
            storageScope = requireText(storageScope, "storageScope");
            recordKey = requireText(recordKey, "recordKey");
            legacyId = requireText(legacyId, "legacyId");
            canonicalId = requireText(canonicalId, "canonicalId");
            if (legacyId.equals(canonicalId)) {
                throw new IllegalArgumentException("legacyId and canonicalId must differ");
            }
        }
    }
}
