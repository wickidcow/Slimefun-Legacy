package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyMachineMigrationCandidate;
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
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable, short-lived authorization plan for exact addon-owned live-machine migrations. */
public final class LegacyMachineMigrationPlan {

    private static final Comparator<LegacyMachineMigrationCandidate> CANDIDATE_ORDER = Comparator
            .comparing((LegacyMachineMigrationCandidate candidate) -> candidate.worldId().toString())
            .thenComparingInt(LegacyMachineMigrationCandidate::x)
            .thenComparingInt(LegacyMachineMigrationCandidate::y)
            .thenComparingInt(LegacyMachineMigrationCandidate::z)
            .thenComparing(LegacyMachineMigrationCandidate::sourceId)
            .thenComparing(LegacyMachineMigrationCandidate::targetId)
            .thenComparing(LegacyMachineMigrationCandidate::stateClaim);

    private final String providerId;
    private final String providerVersion;
    private final long generatedAtMillis;
    private final long expiresAtMillis;
    private final Map<String, String> mappings;
    private final List<LegacyMachineMigrationCandidate> candidates;
    private final String fingerprint;

    LegacyMachineMigrationPlan(
            @Nonnull String providerId,
            @Nonnull String providerVersion,
            long generatedAtMillis,
            long expiresAtMillis,
            @Nonnull Map<String, String> mappings,
            @Nonnull Collection<LegacyMachineMigrationCandidate> candidates) {
        this.providerId = requireText(providerId, "providerId");
        this.providerVersion = requireText(providerVersion, "providerVersion");
        if (expiresAtMillis <= generatedAtMillis) {
            throw new IllegalArgumentException("expiresAtMillis must be after generatedAtMillis");
        }
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

    public @Nonnull List<LegacyMachineMigrationCandidate> getCandidates() {
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
        Objects.requireNonNull(suppliedFingerprint, "suppliedFingerprint");
        return fingerprint.equalsIgnoreCase(suppliedFingerprint)
                || getShortFingerprint().equalsIgnoreCase(suppliedFingerprint);
    }

    public boolean matchesProviderSnapshot(@Nonnull String version, @Nonnull Map<String, String> currentMappings) {
        return providerVersion.equals(version) && mappings.equals(sortedMappings(currentMappings));
    }

    private static Map<String, String> sortedMappings(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        List<Map.Entry<String, String>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        Map<String, String> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            sorted.put(requireText(entry.getKey(), "mapping source"), requireText(entry.getValue(), "mapping target"));
        }
        return Collections.unmodifiableMap(sorted);
    }

    private static List<LegacyMachineMigrationCandidate> sortedCandidates(
            Collection<LegacyMachineMigrationCandidate> values) {
        Objects.requireNonNull(values, "values");
        List<LegacyMachineMigrationCandidate> sorted = new ArrayList<>(values);
        sorted.sort(CANDIDATE_ORDER);
        return Collections.unmodifiableList(sorted);
    }

    private String createFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, providerId);
            update(digest, providerVersion);
            update(digest, Long.toString(generatedAtMillis));
            update(digest, Long.toString(expiresAtMillis));
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
            }
            for (LegacyMachineMigrationCandidate candidate : candidates) {
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

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
