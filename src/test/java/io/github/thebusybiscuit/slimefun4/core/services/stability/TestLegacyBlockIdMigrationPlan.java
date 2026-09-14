package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestLegacyBlockIdMigrationPlan {

    @Test
    void fingerprintIsStableAcrossCandidateOrder() {
        var first = new LegacyBlockIdMigrationPlan(
                4L,
                1000L,
                2000L,
                List.of(
                        candidate("UNIVERSAL_RECORD", "uuid-b", "OLD_B", "NEW_B"),
                        candidate("BLOCK_RECORD", "world;1;2;3", "OLD_A", "NEW_A")));
        var second = new LegacyBlockIdMigrationPlan(
                4L,
                1000L,
                2000L,
                List.of(
                        candidate("BLOCK_RECORD", "world;1;2;3", "OLD_A", "NEW_A"),
                        candidate("UNIVERSAL_RECORD", "uuid-b", "OLD_B", "NEW_B")));

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(first.getShortFingerprint(), second.getShortFingerprint());
        assertTrue(first.matchesCandidates(second.getCandidates()));
    }

    @Test
    void fingerprintDetectsTargetOrRecordDrift() {
        var base = new LegacyBlockIdMigrationPlan(
                9L, 1000L, 2000L, List.of(candidate("BLOCK_RECORD", "world;1;2;3", "OLD", "NEW")));
        var changedTarget = new LegacyBlockIdMigrationPlan(
                9L, 1000L, 2000L, List.of(candidate("BLOCK_RECORD", "world;1;2;3", "OLD", "NEWER")));
        var changedRecord = new LegacyBlockIdMigrationPlan(
                9L, 1000L, 2000L, List.of(candidate("BLOCK_RECORD", "world;4;5;6", "OLD", "NEW")));

        assertNotEquals(base.getFingerprint(), changedTarget.getFingerprint());
        assertNotEquals(base.getFingerprint(), changedRecord.getFingerprint());
        assertFalse(base.matchesCandidates(changedTarget.getCandidates()));
    }

    @Test
    void fingerprintIsBoundToGenerationAndExpires() {
        var first = new LegacyBlockIdMigrationPlan(
                1L, 1000L, 2000L, List.of(candidate("BLOCK_RECORD", "world;1;2;3", "OLD", "NEW")));
        var second = new LegacyBlockIdMigrationPlan(
                2L, 1000L, 2000L, List.of(candidate("BLOCK_RECORD", "world;1;2;3", "OLD", "NEW")));

        assertNotEquals(first.getFingerprint(), second.getFingerprint());
        assertTrue(first.matchesFingerprint(first.getFingerprint()));
        assertTrue(first.matchesFingerprint(first.getShortFingerprint()));
        assertFalse(first.matchesFingerprint("deadbeefdead"));
        assertFalse(first.isExpired(2999L));
        assertTrue(first.isExpired(3000L));
    }

    @Test
    void planRejectsAmbiguousOrNoOpCandidates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate("BLOCK_RECORD", "world;1;2;3", "SAME", "SAME"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyBlockIdMigrationPlan(
                        1L,
                        1000L,
                        2000L,
                        List.of(
                                candidate("BLOCK_RECORD", "world;1;2;3", "OLD", "NEW"),
                                candidate("BLOCK_RECORD", "world;1;2;3", "OLD", "OTHER"))));
    }

    private static LegacyBlockIdMigrationPlan.Candidate candidate(
            String scope, String key, String source, String target) {
        return new LegacyBlockIdMigrationPlan.Candidate(scope, key, source, target);
    }
}
