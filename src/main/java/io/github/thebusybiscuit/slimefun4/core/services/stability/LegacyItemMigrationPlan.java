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
import javax.annotation.Nonnull;

/** Immutable, short-lived authorization plan for one addon-owned legacy migration provider. */
public final class LegacyItemMigrationPlan {

    private final String providerId;
    private final long generatedAtMillis;
    private final long expiresAtMillis;
    private final Map<String, String> mappings;
    private final String fingerprint;

    LegacyItemMigrationPlan(
            @Nonnull String providerId,
            long generatedAtMillis,
            long expiresAtMillis,
            @Nonnull Map<String, String> mappings) {
        this.providerId = providerId;
        this.generatedAtMillis = generatedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.mappings = sortedCopy(mappings);
        this.fingerprint = createFingerprint();
    }

    public @Nonnull String getProviderId() {
        return providerId;
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

    private Map<String, String> sortedCopy(Map<String, String> values) {
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
            update(digest, providerId);
            update(digest, Long.toString(generatedAtMillis));
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
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
