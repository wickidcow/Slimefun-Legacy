package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyMachineMigrationCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestLegacyMachineMigrationPlan {

    private static final UUID WORLD_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID WORLD_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void fingerprintIsStableAcrossMappingAndCandidateOrder() {
        Map<String, String> firstMappings = new LinkedHashMap<>();
        firstMappings.put("OLD_B", "NEW_B");
        firstMappings.put("OLD_A", "NEW_A");

        Map<String, String> secondMappings = new LinkedHashMap<>();
        secondMappings.put("OLD_A", "NEW_A");
        secondMappings.put("OLD_B", "NEW_B");

        LegacyMachineMigrationCandidate a = candidate(WORLD_A, 10, 64, 20, "OLD_A", "NEW_A", "claim-a");
        LegacyMachineMigrationCandidate b = candidate(WORLD_A, -5, 70, 4, "OLD_B", "NEW_B", "claim-b");

        LegacyMachineMigrationPlan first = new LegacyMachineMigrationPlan(
                "DynaTech", "1.0.0", 1000L, 2000L, firstMappings, List.of(a, b));
        LegacyMachineMigrationPlan second = new LegacyMachineMigrationPlan(
                "DynaTech", "1.0.0", 1000L, 2000L, secondMappings, List.of(b, a));

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(first.getShortFingerprint(), second.getShortFingerprint());
    }

    @Test
    void fingerprintBindsMachineStateAndExactLocation() {
        Map<String, String> mappings = Map.of("OLD", "NEW");
        LegacyMachineMigrationPlan base = plan(candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim-1"), mappings);
        LegacyMachineMigrationPlan changedState = plan(
                candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim-2"), mappings);
        LegacyMachineMigrationPlan changedCoordinate = plan(
                candidate(WORLD_A, 2, 64, 2, "OLD", "NEW", "claim-1"), mappings);
        LegacyMachineMigrationPlan changedWorld = plan(
                candidate(WORLD_B, 1, 64, 2, "OLD", "NEW", "claim-1"), mappings);

        assertNotEquals(base.getFingerprint(), changedState.getFingerprint());
        assertNotEquals(base.getFingerprint(), changedCoordinate.getFingerprint());
        assertNotEquals(base.getFingerprint(), changedWorld.getFingerprint());
    }

    @Test
    void providerVersionMappingsAndExpiryArePartOfAuthorization() {
        LegacyMachineMigrationCandidate candidate = candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim");
        LegacyMachineMigrationPlan base = new LegacyMachineMigrationPlan(
                "DynaTech", "1.0.0", 1000L, 2000L, Map.of("OLD", "NEW"), List.of(candidate));
        LegacyMachineMigrationPlan changedVersion = new LegacyMachineMigrationPlan(
                "DynaTech", "1.0.1", 1000L, 2000L, Map.of("OLD", "NEW"), List.of(candidate));

        assertNotEquals(base.getFingerprint(), changedVersion.getFingerprint());
        assertTrue(base.matchesProviderSnapshot("1.0.0", Map.of("OLD", "NEW")));
        assertFalse(base.matchesProviderSnapshot("1.0.1", Map.of("OLD", "NEW")));
        assertFalse(base.matchesProviderSnapshot("1.0.0", Map.of("OLD", "OTHER")));
        assertFalse(base.isExpired(1999L));
        assertTrue(base.isExpired(2000L));
    }

    @Test
    void snapshotsAreReadOnlyAndFingerprintAcceptsOnlyExactOrShortValue() {
        LegacyMachineMigrationCandidate candidate = candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim");
        LegacyMachineMigrationPlan plan = plan(candidate, Map.of("OLD", "NEW"));

        assertThrows(UnsupportedOperationException.class, () -> plan.getMappings().put("A", "B"));
        assertThrows(UnsupportedOperationException.class, () -> plan.getCandidates().add(candidate));
        assertTrue(plan.matchesFingerprint(plan.getFingerprint()));
        assertTrue(plan.matchesFingerprint(plan.getShortFingerprint()));
        assertFalse(plan.matchesFingerprint("deadbeefdead"));
    }

    @Test
    void candidateRejectsSelfMigration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyMachineMigrationCandidate(WORLD_A, 1, 64, 2, "SAME", "SAME", "claim"));
    }

    private LegacyMachineMigrationPlan plan(
            LegacyMachineMigrationCandidate candidate, Map<String, String> mappings) {
        return new LegacyMachineMigrationPlan("DynaTech", "1.0.0", 1000L, 2000L, mappings, List.of(candidate));
    }

    private LegacyMachineMigrationCandidate candidate(
            UUID world,
            int x,
            int y,
            int z,
            String source,
            String target,
            String claim) {
        return new LegacyMachineMigrationCandidate(world, x, y, z, source, target, claim);
    }
}
