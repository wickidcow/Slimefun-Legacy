package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;

/** Immutable, short-lived authorization plan for one item-only upgrade run. */
public final class ItemUpgradePlan {

    public static final long DEFAULT_TTL_MILLIS = 10L * 60L * 1000L;

    private final long generatedAtMillis;
    private final long expiresAtMillis;
    private final Map<String, String> mappings;
    private final Set<String> candidateLegacyIds;
    private final long presentationCandidates;
    private final String fingerprint;

    private ItemUpgradePlan(
            long generatedAtMillis,
            long expiresAtMillis,
            @Nonnull Map<String, String> mappings,
            @Nonnull Set<String> candidateLegacyIds,
            long presentationCandidates) {
        this.generatedAtMillis = generatedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.mappings = sortedCopy(mappings);
        this.candidateLegacyIds = Collections.unmodifiableSet(new TreeSet<>(candidateLegacyIds));
        this.presentationCandidates = presentationCandidates;
        fingerprint = createFingerprint();
    }

    public static @Nonnull ItemUpgradePlan create(
            @Nonnull Map<String, String> mappings,
            @Nonnull Set<String> candidateLegacyIds,
            long presentationCandidates) {
        long now = System.currentTimeMillis();
        return new ItemUpgradePlan(now, now + DEFAULT_TTL_MILLIS, mappings, candidateLegacyIds, presentationCandidates);
    }

    static @Nonnull ItemUpgradePlan createForTest(
            long generatedAtMillis,
            long expiresAtMillis,
            @Nonnull Map<String, String> mappings,
            @Nonnull Set<String> candidateLegacyIds,
            long presentationCandidates) {
        return new ItemUpgradePlan(
                generatedAtMillis, expiresAtMillis, mappings, candidateLegacyIds, presentationCandidates);
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

    public @Nonnull Set<String> getCandidateLegacyIds() {
        return candidateLegacyIds;
    }

    public long getPresentationCandidates() {
        return presentationCandidates;
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

    public boolean matchesMappings(@Nonnull Map<String, String> currentMappings) {
        return mappings.equals(currentMappings);
    }

    public boolean matchesFingerprint(@Nonnull String suppliedFingerprint) {
        return fingerprint.equalsIgnoreCase(suppliedFingerprint)
                || getShortFingerprint().equalsIgnoreCase(suppliedFingerprint);
    }

    private static Map<String, String> sortedCopy(Map<String, String> values) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        Map<String, String> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(sorted);
    }

    private String createFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "slimefun-item-upgrade-v1");
            update(digest, Long.toString(generatedAtMillis));
            update(digest, Long.toString(presentationCandidates));
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
            }
            for (String candidate : candidateLegacyIds) {
                update(digest, candidate);
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
}
