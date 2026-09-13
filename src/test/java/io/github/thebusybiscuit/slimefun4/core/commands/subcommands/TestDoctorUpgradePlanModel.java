package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestDoctorUpgradePlanModel {

    @Test
    void classifiesCompleteProviderCapabilities() {
        DoctorUpgradeSchemaCounts counts = new DoctorUpgradeSchemaCounts(3L, 4L, 2L);
        DoctorUpgradeSchemaCapabilities capabilities = new DoctorUpgradeSchemaCapabilities(true, true, true);

        DoctorUpgradeSchemaActionability result = DoctorUpgradePlanModel.classify(counts, capabilities);

        assertEquals(3L, result.readyNow());
        assertEquals(4L, result.needsValidation());
        assertEquals(0L, result.needsProvider());
        assertEquals(2L, result.manualOnly());
    }

    @Test
    void readyCandidatesNeedProbeAndMigrator() {
        DoctorUpgradeSchemaCounts counts = new DoctorUpgradeSchemaCounts(5L, 0L, 0L);

        DoctorUpgradeSchemaActionability noMigrator = DoctorUpgradePlanModel.classify(
                counts, new DoctorUpgradeSchemaCapabilities(true, true, false));
        DoctorUpgradeSchemaActionability noProbe = DoctorUpgradePlanModel.classify(
                counts, new DoctorUpgradeSchemaCapabilities(false, true, true));

        assertEquals(0L, noMigrator.readyNow());
        assertEquals(5L, noMigrator.needsProvider());
        assertEquals(0L, noProbe.readyNow());
        assertEquals(5L, noProbe.needsProvider());
    }

    @Test
    void validationCandidatesRequireValidatorAndMigrator() {
        DoctorUpgradeSchemaCounts counts = new DoctorUpgradeSchemaCounts(0L, 7L, 0L);

        DoctorUpgradeSchemaActionability noValidator = DoctorUpgradePlanModel.classify(
                counts, new DoctorUpgradeSchemaCapabilities(true, false, true));
        DoctorUpgradeSchemaActionability noMigrator = DoctorUpgradePlanModel.classify(
                counts, new DoctorUpgradeSchemaCapabilities(true, true, false));

        assertEquals(0L, noValidator.needsValidation());
        assertEquals(7L, noValidator.needsProvider());
        assertEquals(0L, noMigrator.needsValidation());
        assertEquals(7L, noMigrator.needsProvider());
    }

    @Test
    void manualOnlyCandidatesNeverBecomeActionable() {
        DoctorUpgradeSchemaCounts counts = new DoctorUpgradeSchemaCounts(0L, 0L, 9L);
        DoctorUpgradeSchemaCapabilities capabilities = new DoctorUpgradeSchemaCapabilities(true, true, true);

        DoctorUpgradeSchemaActionability result = DoctorUpgradePlanModel.classify(counts, capabilities);

        assertEquals(0L, result.readyNow());
        assertEquals(0L, result.needsValidation());
        assertEquals(0L, result.needsProvider());
        assertEquals(9L, result.manualOnly());
    }

    @Test
    void actionabilityTotalsCombineWithoutChangingCategories() {
        DoctorUpgradeSchemaActionability first = new DoctorUpgradeSchemaActionability(2L, 3L, 4L, 5L);
        DoctorUpgradeSchemaActionability second = new DoctorUpgradeSchemaActionability(7L, 11L, 13L, 17L);

        DoctorUpgradeSchemaActionability total = first.add(second);

        assertEquals(9L, total.readyNow());
        assertEquals(14L, total.needsValidation());
        assertEquals(17L, total.needsProvider());
        assertEquals(22L, total.manualOnly());
    }
}
