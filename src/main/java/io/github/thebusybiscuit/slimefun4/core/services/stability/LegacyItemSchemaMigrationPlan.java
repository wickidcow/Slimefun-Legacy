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
 * Short-lived authorization for addon-owned same-ID item-schema migration.
 *
 * <p>Opaque validation claims and migration payloads remain private in memory. The public fingerprint is derived
 * from SHA-256 digests of those values, never from their plaintext representation.</p>
 */
public final class LegacyItemSchemaMigrationPlan {

    private static final int SHORT_FINGERPRINT_LENGTH = 12;

    private final String providerId;
    private final String migrationName;
    private final String providerVersion;
    private final long generation;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final List<Authorization> authorizations;
    private final long authorizedStackCount;
    private final String fingerprint;

    LegacyItemSchemaMigrationPlan(
            @Nonnull String providerId,
            @Nonnull String migrationName,
            @Nonnull String providerVersion,
            long generation,
            long createdAtMillis,
            long ttlMillis,
            @Nonnull List<Authorization> authorizations) {
        this.providerId = requireText(providerId, "providerId");
        this.migrationName = requireText(migrationName, "migrationName");
        this.providerVersion = requireText(providerVersion, "providerVersion");
        if (generation < 1L) throw new IllegalArgumentException("generation must be positive");
        if (ttlMillis < 1L) throw new IllegalArgumentException("ttlMillis must be positive");
        this.generation = generation;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = Math.addExact(createdAtMillis, ttlMillis);

        List<Authorization> copy = new ArrayList<>(Objects.requireNonNull(authorizations, "authorizations"));
        if (copy.isEmpty()) throw new IllegalArgumentException("authorizations cannot be empty");
        copy.sort(Comparator.comparing(Authorization::slimefunId)
                .thenComparing(Authorization::candidateType)
                .thenComparing(Authorization::validationClaim)
                .thenComparing(Authorization::migrationPayload));

        List<Authorization> canonical = new ArrayList<>();
        for (Authorization authorization : copy) {
            if (!canonical.isEmpty()) {
                Authorization previous = canonical.getLast();
                if (sameAuthorizationClaim(previous, authorization)) {
                    if (!previous.migrationPayload().equals(authorization.migrationPayload())) {
                        throw new IllegalArgumentException(
                                "one schema authorization claim produced conflicting migration payloads");
                    }
                    canonical.set(
                            canonical.size() - 1,
                            new Authorization(
                                    previous.slimefunId(),
                                    previous.candidateType(),
                                    previous.validationClaim(),
                                    previous.migrationPayload(),
                                    Math.addExact(previous.candidateCount(), authorization.candidateCount())));
                    continue;
                }
            }
            canonical.add(authorization);
        }

        this.authorizations = List.copyOf(canonical);
        this.authorizedStackCount = canonical.stream().mapToLong(Authorization::candidateCount).sum();
        this.fingerprint = calculateFingerprint();
    }

    public @Nonnull String getProviderId() { return providerId; }
    public @Nonnull String getMigrationName() { return migrationName; }
    public @Nonnull String getProviderVersion() { return providerVersion; }
    public long getGeneration() { return generation; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public int getAuthorizedClaimCount() { return authorizations.size(); }
    public long getAuthorizedStackCount() { return authorizedStackCount; }
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

    boolean matchesProviderVersion(@Nonnull String version) {
        return providerVersion.equals(version);
    }

    @Nonnull
    List<Authorization> authorizations() {
        return authorizations;
    }

    @Nullable
    Authorization findAuthorization(
            @Nonnull String slimefunId, @Nonnull String candidateType, @Nonnull String validationClaim) {
        for (Authorization authorization : authorizations) {
            if (authorization.slimefunId().equals(slimefunId)
                    && authorization.candidateType().equals(candidateType)
                    && authorization.validationClaim().equals(validationClaim)) {
                return authorization;
            }
        }
        return null;
    }

    private String calculateFingerprint() {
        MessageDigest digest = sha256();
        update(digest, "provider", providerId);
        update(digest, "version", providerVersion);
        update(digest, "generation", Long.toString(generation));
        for (Authorization authorization : authorizations) {
            update(digest, "item", authorization.slimefunId());
            update(digest, "type", authorization.candidateType());
            update(digest, "claim", digest(authorization.validationClaim()));
            update(digest, "payload", digest(authorization.migrationPayload()));
            update(digest, "count", Long.toString(authorization.candidateCount()));
        }
        return toHex(digest.digest());
    }

    private static boolean sameAuthorizationClaim(Authorization left, Authorization right) {
        return left.slimefunId().equals(right.slimefunId())
                && left.candidateType().equals(right.candidateType())
                && left.validationClaim().equals(right.validationClaim());
    }

    private static String digest(String value) {
        MessageDigest digest = sha256();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
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
        if (text.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return text;
    }

    static record Authorization(
            String slimefunId,
            String candidateType,
            String validationClaim,
            String migrationPayload,
            long candidateCount) {
        Authorization {
            slimefunId = requireText(slimefunId, "slimefunId");
            candidateType = requireText(candidateType, "candidateType");
            validationClaim = requireText(validationClaim, "validationClaim");
            migrationPayload = requireText(migrationPayload, "migrationPayload");
            if (candidateCount < 1L) throw new IllegalArgumentException("candidateCount must be positive");
        }
    }
}
