package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestLegacyItemSchemaMigrationPlan {

    @Test
    void fingerprintIsStableAcrossAuthorizationInsertionOrder() {
        var first = authorization("DOLLY", "legacy-dolly", "owner-a#1", "uuid-a", 2L);
        var second = authorization("OTHER", "legacy-other", "claim-b", "payload-b", 1L);

        LegacyItemSchemaMigrationPlan left = plan("1.2.3", 7L, List.of(first, second));
        LegacyItemSchemaMigrationPlan right = plan("1.2.3", 7L, List.of(second, first));

        assertEquals(left.getFingerprint(), right.getFingerprint());
        assertEquals(3L, left.getAuthorizedStackCount());
        assertEquals(2, left.getAuthorizedClaimCount());
    }

    @Test
    void duplicateEquivalentAuthorizationsAreCanonicalized() {
        var first = authorization("DOLLY", "legacy-dolly", "owner#1", "uuid-a", 2L);
        var duplicate = authorization("DOLLY", "legacy-dolly", "owner#1", "uuid-a", 3L);

        LegacyItemSchemaMigrationPlan plan = plan("1.2.3", 8L, List.of(first, duplicate));

        assertEquals(1, plan.getAuthorizedClaimCount());
        assertEquals(5L, plan.getAuthorizedStackCount());
        assertEquals(5L, plan.authorizations().getFirst().candidateCount());
    }

    @Test
    void fingerprintDoesNotExposePrivateClaimOrPayload() {
        String claim = "private-owner#42";
        String payload = "private-backpack-uuid";
        LegacyItemSchemaMigrationPlan plan = plan(
                "1.2.3", 2L, List.of(authorization("DOLLY", "legacy-dolly", claim, payload, 1L)));

        assertFalse(plan.getFingerprint().contains(claim));
        assertFalse(plan.getFingerprint().contains(payload));
        assertTrue(plan.matchesFingerprint(plan.getFingerprint()));
        assertTrue(plan.matchesFingerprint(plan.getShortFingerprint()));
    }

    @Test
    void providerVersionAndGenerationBindFingerprint() {
        var authorization = authorization("DOLLY", "legacy-dolly", "owner#3", "uuid", 1L);
        LegacyItemSchemaMigrationPlan versionOne = plan("1.0", 1L, List.of(authorization));
        LegacyItemSchemaMigrationPlan versionTwo = plan("2.0", 1L, List.of(authorization));
        LegacyItemSchemaMigrationPlan generationTwo = plan("1.0", 2L, List.of(authorization));

        assertFalse(versionOne.getFingerprint().equals(versionTwo.getFingerprint()));
        assertFalse(versionOne.getFingerprint().equals(generationTwo.getFingerprint()));
        assertTrue(versionOne.matchesProviderVersion("1.0"));
        assertFalse(versionOne.matchesProviderVersion("2.0"));
    }

    @Test
    void authorizationRequiresExactItemTypeAndClaim() {
        LegacyItemSchemaMigrationPlan plan = plan(
                "1.0",
                1L,
                List.of(authorization("DOLLY", "legacy-dolly", "owner#4", "uuid", 1L)));

        assertNotNull(plan.findAuthorization("DOLLY", "legacy-dolly", "owner#4"));
        assertNull(plan.findAuthorization("OTHER", "legacy-dolly", "owner#4"));
        assertNull(plan.findAuthorization("DOLLY", "other-type", "owner#4"));
        assertNull(plan.findAuthorization("DOLLY", "legacy-dolly", "owner#5"));
    }

    @Test
    void expiryBoundaryIsStrict() {
        LegacyItemSchemaMigrationPlan plan = new LegacyItemSchemaMigrationPlan(
                "FluffyMachines",
                "Dolly migration",
                "1.0",
                1L,
                1_000L,
                500L,
                List.of(authorization("DOLLY", "legacy-dolly", "owner#4", "uuid", 1L)));

        assertFalse(plan.isExpired(1_499L));
        assertTrue(plan.isExpired(1_500L));
    }

    private static LegacyItemSchemaMigrationPlan plan(
            String version, long generation, List<LegacyItemSchemaMigrationPlan.Authorization> authorizations) {
        return new LegacyItemSchemaMigrationPlan(
                "FluffyMachines",
                "Dolly migration",
                version,
                generation,
                10_000L,
                600_000L,
                authorizations);
    }

    private static LegacyItemSchemaMigrationPlan.Authorization authorization(
            String itemId, String type, String claim, String payload, long count) {
        return new LegacyItemSchemaMigrationPlan.Authorization(itemId, type, claim, payload, count);
    }
}
