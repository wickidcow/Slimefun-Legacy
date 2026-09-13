package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;

/** Pure classification model for the read-only Doctor upgrade plan. */
final class DoctorUpgradePlanModel {

    private DoctorUpgradePlanModel() {}

    static DoctorUpgradeSchemaActionability classify(
            DoctorUpgradeSchemaCounts counts, DoctorUpgradeSchemaCapabilities capabilities) {
        long readyNow = capabilities.canExecuteReady() ? counts.readyClaimed() : 0L;
        long needsValidation = capabilities.canExecuteValidated() ? counts.validationRequired() : 0L;
        long needsProvider = counts.readyClaimed() - readyNow + counts.validationRequired() - needsValidation;
        long manualOnly = counts.manualOnly() + counts.readyDiagnosticOnly();
        return new DoctorUpgradeSchemaActionability(readyNow, needsValidation, needsProvider, manualOnly);
    }
}

record DoctorUpgradeSchemaCounts(long readyClaimed, long readyDiagnosticOnly, long validationRequired, long manualOnly) {

    static DoctorUpgradeSchemaCounts empty() {
        return new DoctorUpgradeSchemaCounts(0L, 0L, 0L, 0L);
    }

    long ready() {
        return readyClaimed + readyDiagnosticOnly;
    }

    DoctorUpgradeSchemaCounts add(
            LegacyItemSchemaCandidate.Readiness readiness, boolean itemLocalClaimPresent, long count) {
        return switch (readiness) {
            case READY -> itemLocalClaimPresent
                    ? new DoctorUpgradeSchemaCounts(readyClaimed + count, readyDiagnosticOnly, validationRequired, manualOnly)
                    : new DoctorUpgradeSchemaCounts(readyClaimed, readyDiagnosticOnly + count, validationRequired, manualOnly);
            case VALIDATION_REQUIRED ->
                new DoctorUpgradeSchemaCounts(readyClaimed, readyDiagnosticOnly, validationRequired + count, manualOnly);
            case MANUAL_ONLY ->
                new DoctorUpgradeSchemaCounts(readyClaimed, readyDiagnosticOnly, validationRequired, manualOnly + count);
        };
    }
}

record DoctorUpgradeSchemaCapabilities(boolean probe, boolean validator, boolean migrator) {

    static DoctorUpgradeSchemaCapabilities empty() {
        return new DoctorUpgradeSchemaCapabilities(false, false, false);
    }

    DoctorUpgradeSchemaCapabilities withProbe() {
        return new DoctorUpgradeSchemaCapabilities(true, validator, migrator);
    }

    DoctorUpgradeSchemaCapabilities withValidator() {
        return new DoctorUpgradeSchemaCapabilities(probe, true, migrator);
    }

    DoctorUpgradeSchemaCapabilities withMigrator() {
        return new DoctorUpgradeSchemaCapabilities(probe, validator, true);
    }

    boolean canExecuteReady() {
        return probe && migrator;
    }

    boolean canExecuteValidated() {
        return probe && validator && migrator;
    }
}

record DoctorUpgradeSchemaActionability(long readyNow, long needsValidation, long needsProvider, long manualOnly) {

    DoctorUpgradeSchemaActionability add(DoctorUpgradeSchemaActionability other) {
        return new DoctorUpgradeSchemaActionability(
                readyNow + other.readyNow,
                needsValidation + other.needsValidation,
                needsProvider + other.needsProvider,
                manualOnly + other.manualOnly);
    }

    static DoctorUpgradeSchemaActionability empty() {
        return new DoctorUpgradeSchemaActionability(0L, 0L, 0L, 0L);
    }
}
