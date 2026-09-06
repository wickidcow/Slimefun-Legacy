package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StorageIntegrityRepairExecutionTest {

    @Test
    void repairedResultReportsExactRowCountsAndBackup() {
        Path backup = Path.of("storage-repair-backups", "repair-test.sfbak");
        var result = new StorageIntegrityRepairExecution(
                StorageIntegrityRepairExecution.Status.REPAIRED,
                "a".repeat(64),
                backup,
                2,
                3,
                5,
                7,
                0,
                null);

        assertTrue(result.isRepaired());
        assertEquals(2, result.getBlockDataRows());
        assertEquals(3, result.getBlockInventoryRows());
        assertEquals(5, result.getUniversalDataRows());
        assertEquals(7, result.getUniversalInventoryRows());
        assertEquals(17, result.getTotalRows());
        assertEquals(backup, result.getBackupPath());
    }

    @Test
    void refusedResultPreservesFailureDetail() {
        var result = new StorageIntegrityRepairExecution(
                StorageIntegrityRepairExecution.Status.CACHED_CANDIDATE,
                "b".repeat(64),
                null,
                0,
                0,
                0,
                0,
                4,
                "loaded candidates");

        assertFalse(result.isRepaired());
        assertEquals(4, result.getCachedCandidateOwners());
        assertEquals("loaded candidates", result.getDetail());
        assertEquals(0, result.getTotalRows());
    }
}
