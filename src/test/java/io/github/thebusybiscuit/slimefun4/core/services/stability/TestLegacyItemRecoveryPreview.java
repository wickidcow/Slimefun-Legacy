package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog.Evidence;
import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog.Hint;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TestLegacyItemRecoveryPreview {

    private static Optional<Hint> known(String id) {
        String source = switch (id) {
            case "SHULKER_HELMET", "PANDA_HELMET" -> "EnderPanda (balugaq/EnderPanda) historical item";
            case "LUCKY_BLOCK" -> "Slimefun LuckyBlocks (community) historical block";
            case "DIGITAL_MINER" -> "Slimefun4 Digital Miner -> Industrial Miner replacement";
            default -> null;
        };
        return source == null ? Optional.empty() : Optional.of(
                new Hint(id, id, source, Evidence.DOCUMENTED_ITEM_ID));
    }

    @Test
    void groupsByOriginalAddonWithoutInventingExactSampleCounts() {
        var results = LegacyItemRecoveryPreview.build(
                Map.of("DECLARED_OLD", 3L),
                List.of("LUCKY_BLOCK", "SHULKER_HELMET", "PANDA_HELMET", "MYSTERY_ID",
                        "DECLARED_OLD", "SHULKER_HELMET"),
                Map.of("DECLARED_OLD", "NEW_ITEM"),
                TestLegacyItemRecoveryPreview::known,
                "NEW_ITEM"::equals);

        assertEquals(5, results.size());
        assertEquals(List.of("EnderPanda", "EnderPanda", "LuckyBlocks",
                        "Unattributed declared mapping", "Unknown addon"),
                results.stream().map(LegacyItemRecoveryPreview.Entry::addon).toList());

        var declared = results.stream().filter(e -> e.itemId().equals("DECLARED_OLD")).findFirst().orElseThrow();
        assertTrue(declared.exactCount());
        assertEquals(3L, declared.stackCount());
        assertEquals("DECLARED TARGET READY", declared.status());
        assertEquals("NEW_ITEM", declared.targetId());

        var shulker = results.stream().filter(e -> e.itemId().equals("SHULKER_HELMET")).findFirst().orElseThrow();
        assertFalse(shulker.exactCount());
        assertEquals(0L, shulker.stackCount());
        assertEquals("HISTORICAL ONLY", shulker.status());
        assertTrue(shulker.nextStep().contains("restored helmet ID"));

        var lucky = results.stream().filter(e -> e.itemId().equals("LUCKY_BLOCK")).findFirst().orElseThrow();
        assertTrue(lucky.nextStep().contains("SF_LuckyBlocks v1.0.4+"));

        var mystery = results.stream().filter(e -> e.itemId().equals("MYSTERY_ID")).findFirst().orElseThrow();
        assertEquals("UNIDENTIFIED", mystery.status());
        assertTrue(mystery.nextStep().contains("do not guess"));

        assertThrows(UnsupportedOperationException.class, () -> results.clear());
    }

    @Test
    void skipsAlreadyRegisteredIdsFromAnOldUnknownSample() {
        var results = LegacyItemRecoveryPreview.build(
                Map.of(),
                List.of("SHULKER_HELMET", "PANDA_HELMET"),
                Map.of(),
                TestLegacyItemRecoveryPreview::known,
                id -> id.equals("SHULKER_HELMET"));

        assertEquals(1, results.size());
        assertEquals("PANDA_HELMET", results.getFirst().itemId());
        assertTrue(results.getFirst().nextStep().contains("compatible EnderPanda addon"));
    }

    @Test
    void refusesToInferAnExecutableMigrationFromHistoricalKnowledge() {
        var results = LegacyItemRecoveryPreview.build(
                Map.of("DIGITAL_MINER", 2L, "RETired_ID", 1L),
                List.of(),
                Map.of(),
                TestLegacyItemRecoveryPreview::known,
                id -> false);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(LegacyItemRecoveryPreview.Entry::exactCount));
        assertTrue(results.stream().allMatch(e -> e.status().equals("MAPPING CHANGED")));
        assertTrue(results.stream().allMatch(e -> e.targetId() == null));
        assertTrue(results.stream().allMatch(e -> e.nextStep().contains("Re-scan")));
    }

    @Test
    void identifiesMissingDeclaredTargetsWithoutPerformingRepair() {
        var results = LegacyItemRecoveryPreview.build(
                Map.of("OLD", 17L),
                List.of("OLD"),
                Map.of("OLD", "GONE"),
                ignored -> Optional.empty(),
                id -> false);

        assertEquals(1, results.size());
        var entry = results.getFirst();
        assertEquals("DECLARED TARGET MISSING", entry.status());
        assertEquals(17L, entry.stackCount());
        assertEquals("GONE", entry.targetId());
        assertTrue(entry.nextStep().contains("do not rewrite"));
    }
}
