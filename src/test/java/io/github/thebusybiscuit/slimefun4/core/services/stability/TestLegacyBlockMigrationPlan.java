package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationCandidate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestLegacyBlockMigrationPlan {

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

        LegacyBlockMigrationCandidate a = candidate(WORLD_A, 10, 64, 20, "OLD_A", "NEW_A", "claim-a");
        LegacyBlockMigrationCandidate b = candidate(WORLD_A, -5, 70, 4, "OLD_B", "NEW_B", "claim-b");

        LegacyBlockMigrationPlan first = new LegacyBlockMigrationPlan(
                "DynaTech", "1.0.0", 1000L, 2000L, firstMappings, List.of(a, b));
        LegacyBlockMigrationPlan second = new LegacyBlockMigrationPlan(
                "DynaTech", "1.0.0", 1000L, 2000L, secondMappings, List.of(b, a));

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(first.getShortFingerprint(), second.getShortFingerprint());
    }

    @Test
    void fingerprintBindsMachineStateAndExactLocation() {
        Map<String, String> mappings = Map.of("OLD", "NEW");
        LegacyBlockMigrationPlan base = plan(candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim-1"), mappings);
        LegacyBlockMigrationPlan changedState = plan(
                candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim-2"), mappings);
        LegacyBlockMigrationPlan changedCoordinate = plan(
                candidate(WORLD_A, 2, 64, 2, "OLD", "NEW", "claim-1"), mappings);
        LegacyBlockMigrationPlan changedWorld = plan(
                candidate(WORLD_B, 1, 64, 2, "OLD", "NEW", "claim-1"), mappings);

        assertNotEquals(base.getFingerprint(), changedState.getFingerprint());
        assertNotEquals(base.getFingerprint(), changedCoordinate.getFingerprint());
        assertNotEquals(base.getFingerprint(), changedWorld.getFingerprint());
    }

    @Test
    void providerVersionAndMappingsArePartOfAuthorization() {
        LegacyBlockMigrationCandidate candidate = candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim");
        LegacyBlockMigrationPlan base = new LegacyBlockMigrationPlan(
                "DynaTech", "1.0.0", 1000L, 2000L, Map.of("OLD", "NEW"), List.of(candidate));
        LegacyBlockMigrationPlan changedVersion = new LegacyBlockMigrationPlan(
                "DynaTech", "1.0.1", 1000L, 2000L, Map.of("OLD", "NEW"), List.of(candidate));

        assertNotEquals(base.getFingerprint(), changedVersion.getFingerprint());
        assertTrue(base.matchesProviderSnapshot("1.0.0", Map.of("OLD", "NEW")));
        assertFalse(base.matchesProviderSnapshot("1.0.1", Map.of("OLD", "NEW")));
        assertFalse(base.matchesProviderSnapshot("1.0.0", Map.of("OLD", "OTHER")));
    }

    @Test
    void snapshotsAreReadOnlyAndFingerprintIsSinglePlanSafe() {
        LegacyBlockMigrationCandidate candidate = candidate(WORLD_A, 1, 64, 2, "OLD", "NEW", "claim");
        LegacyBlockMigrationPlan plan = plan(candidate, Map.of("OLD", "NEW"));

        assertThrows(UnsupportedOperationException.class, () -> plan.getMappings().put("A", "B"));
        assertThrows(UnsupportedOperationException.class, () -> plan.getCandidates().add(candidate));
        assertTrue(plan.matchesFingerprint(plan.getFingerprint()));
        assertTrue(plan.matchesFingerprint(plan.getShortFingerprint()));
        assertFalse(plan.matchesFingerprint("deadbeefdead"));
        assertFalse(plan.isExpired(1999L));
        assertTrue(plan.isExpired(2000L));
    }

    private LegacyBlockMigrationPlan plan(LegacyBlockMigrationCandidate candidate, Map<String, String> mappings) {
        return new LegacyBlockMigrationPlan("DynaTech", "1.0.0", 1000L, 2000L, mappings, List.of(candidate));
    }

    private LegacyBlockMigrationCandidate candidate(
            UUID world,
            int x,
            int y,
            int z,
            String source,
            String target,
            String claim) {
        return new LegacyBlockMigrationCandidate(world, x, y, z, source, target, claim);
    }
}
