package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationCandidate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Immutable, short-lived authorization plan for exact addon-owned placed-block migrations. */
public final class LegacyBlockMigrationPlan {

    private static final Comparator<LegacyBlockMigrationCandidate> CANDIDATE_ORDER = Comparator
            .comparing((LegacyBlockMigrationCandidate candidate) -> candidate.worldId().toString())
            .thenComparingInt(LegacyBlockMigrationCandidate::x)
            .thenComparingInt(LegacyBlockMigrationCandidate::y)
            .thenComparingInt(LegacyBlockMigrationCandidate::z)
            .thenComparing(LegacyBlockMigrationCandidate::sourceId)
            .thenComparing(LegacyBlockMigrationCandidate::targetId)
            .thenComparing(LegacyBlockMigrationCandidate::stateClaim);

    private final String providerId;
    private final String providerVersion;
    private final long generatedAtMillis;
    private final long expiresAtMillis;
    private final Map<String, String> mappings;
    private final List<LegacyBlockMigrationCandidate> candidates;
    private final String fingerprint;

    LegacyBlockMigrationPlan(
            @Nonnull String providerId,
            @Nonnull String providerVersion,
            long generatedAtMillis,
            long expiresAtMillis,
            @Nonnull Map<String, String> mappings,
            @Nonnull Collection<LegacyBlockMigrationCandidate> candidates) {
        this.providerId = providerId;
        this.providerVersion = providerVersion;
        this.generatedAtMillis = generatedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.mappings = sortedMappings(mappings);
        this.candidates = sortedCandidates(candidates);
        this.fingerprint = createFingerprint();
    }

    public @Nonnull String getProviderId() {
        return providerId;
    }

    public @Nonnull String getProviderVersion() {
        return providerVersion;
    }

    public long getGeneratedAtMillis() {
        return generatedAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public @Nonnull Map<String, String> getMappings() {
        return mappings;
    }

    public @Nonnull List<LegacyBlockMigrationCandidate> getCandidates() {
        return candidates;
    }

    public @Nonnull String getFingerprint() {
        return fingerprint;
    }

    public @Nonnull String getShortFingerprint() {
        return fingerprint.substring(0, 12);
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public boolean matchesFingerprint(@Nonnull String suppliedFingerprint) {
        return fingerprint.equalsIgnoreCase(suppliedFingerprint)
                || getShortFingerprint().equalsIgnoreCase(suppliedFingerprint);
    }

    public boolean matchesProviderSnapshot(@Nonnull String version, @Nonnull Map<String, String> currentMappings) {
        return providerVersion.equals(version) && mappings.equals(sortedMappings(currentMappings));
    }

    private Map<String, String> sortedMappings(Map<String, String> values) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        Map<String, String> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(sorted);
    }

    private List<LegacyBlockMigrationCandidate> sortedCandidates(Collection<LegacyBlockMigrationCandidate> values) {
        List<LegacyBlockMigrationCandidate> sorted = new ArrayList<>(values);
        sorted.sort(CANDIDATE_ORDER);
        return Collections.unmodifiableList(sorted);
    }

    private String createFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, providerId);
            update(digest, providerVersion);
            update(digest, Long.toString(generatedAtMillis));
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
            }
            for (LegacyBlockMigrationCandidate candidate : candidates) {
                update(digest, candidate.worldId().toString());
                update(digest, Integer.toString(candidate.x()));
                update(digest, Integer.toString(candidate.y()));
                update(digest, Integer.toString(candidate.z()));
                update(digest, candidate.sourceId());
                update(digest, candidate.targetId());
                update(digest, candidate.stateClaim());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
