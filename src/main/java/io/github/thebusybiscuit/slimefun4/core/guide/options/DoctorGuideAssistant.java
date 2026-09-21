package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.PluginDependencyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Read-only routing logic for the Guide's Slimefun Doctor console.
 *
 * <p>This helper never changes stored data, plugin state, item models, machine isolation, or resource-pack settings.
 * It only summarizes existing diagnostic state and identifies the safest next Doctor lane for an operator.</p>
 */
final class DoctorGuideAssistant {

    enum Action {
        RUN_SCAN,
        SHOW_STATUS,
        UPGRADE_PACK_ITEMS,
        REVIEW_MODEL_CLEANUP,
        REPAIR_PRESENTATION,
        SCHEMA_MIGRATION,
        LEGACY_MIGRATION,
        ITEM_MODEL_SCAN,
        RUNTIME_HEALTH,
        DEPENDENCY_HEALTH,
        INTEGRATION_HEALTH,
        SUPPORT_SUMMARY,
        NONE
    }

    record Recommendation(@Nonnull Action action, @Nonnull String title, @Nonnull String detail, long issueCount) {}

    private static final List<String> KNOWN_PACK_MANAGERS = List.of("ItemsAdder", "Oraxen", "Nexo", "CraftEngine");

    private DoctorGuideAssistant() {}

    static @Nonnull Recommendation recommend() {
        var itemDoctor = Slimefun.getItemDoctorService();
        ItemDoctorReport current = itemDoctor.getCurrentReport();
        if (current != null) {
            return new Recommendation(
                    Action.SHOW_STATUS,
                    "Doctor Scan Running",
                    "A server-wide " + current.getModeName() + " is already running. Review progress before starting another repair.",
                    1);
        }

        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        if (storage.getPendingWrites() > 0) {
            return new Recommendation(
                    Action.SHOW_STATUS,
                    "Wait for Database Writes",
                    storage.getPendingWrites() + " Slimefun database write(s) are still pending. Do not stop or restart yet.",
                    storage.getPendingWrites());
        }

        ItemDoctorReport last = itemDoctor.getLastReport();
        ExternalResourcePackService packs = new ExternalResourcePackService(Slimefun.instance());
        int packEnableCandidates = Slimefun.getItemTextureService().getHostedPackEnableCandidateCount();

        if (packs.isDeliveryEnabled() && packEnableCandidates > 0) {
            return new Recommendation(
                    Action.UPGRADE_PACK_ITEMS,
                    "Upgrade Items for Resource Pack",
                    "Legacy pack delivery is enabled, but " + packEnableCandidates
                            + " bundled item mapping(s) are still disabled. Review the guarded adoption flow.",
                    packEnableCandidates);
        }

        if (last == null) {
            return new Recommendation(
                    Action.RUN_SCAN,
                    "Run Full Doctor Scan",
                    "No completed server-wide Doctor scan is available yet. Start with the read-only scan.",
                    0);
        }

        if (!last.isComplete()) {
            return new Recommendation(
                    Action.SHOW_STATUS,
                    "Doctor Work Still Running",
                    "The latest Doctor traversal has not completed yet. Review status before taking another action.",
                    1);
        }

        if (last.getFailures() > 0) {
            return new Recommendation(
                    Action.SUPPORT_SUMMARY,
                    "Review Doctor Failures",
                    last.getFailures() + " traversal failure(s) were recorded. Review the support summary before repairing.",
                    last.getFailures());
        }

        if (last.getSchemaMigrationCandidates() > 0) {
            return new Recommendation(
                    Action.SCHEMA_MIGRATION,
                    "Review Addon Schema Migrations",
                    last.getSchemaMigrationCandidates()
                            + " addon-owned same-ID schema candidate(s) need their provider-specific migration lane.",
                    last.getSchemaMigrationCandidates());
        }

        if (last.getItemModelCandidates() > 0) {
            return new Recommendation(
                    Action.ITEM_MODEL_SCAN,
                    "Review Stale Item Models",
                    last.getItemModelCandidates()
                            + " stored Slimefun item(s) match the safe stale bundled-model recovery criteria.",
                    last.getItemModelCandidates());
        }

        long identityCandidates = last.getLegacyMigrationCandidates()
                + last.getUnknownIds()
                + last.getLegacyBlockIds()
                + last.getUnknownBlockIds();
        if (identityCandidates > 0) {
            return new Recommendation(
                    Action.LEGACY_MIGRATION,
                    "Review Legacy / Unknown IDs",
                    identityCandidates
                            + " item or block identity finding(s) require migration-provider review instead of generic repair.",
                    identityCandidates);
        }

        long presentationFindings = last.getCjkStacks() + last.getCjkBlocks();
        if (!last.isRepairMode() && presentationFindings > 0) {
            return new Recommendation(
                    Action.REPAIR_PRESENTATION,
                    "Repair Names & Lore",
                    presentationFindings
                            + " safely classifiable presentation finding(s) are available for the confirmed Doctor repair.",
                    presentationFindings);
        }

        if (last.isRepairMode()
                && (last.getUnresolvedTemplates() > 0
                        || last.getUnknownIds() > 0
                        || last.getUnknownBlockIds() > 0)) {
            return new Recommendation(
                    Action.RUN_SCAN,
                    "Re-scan Remaining Protected Data",
                    "The previous repair left protected or unresolved findings. Re-scan to classify the correct specialist lane.",
                    last.getUnresolvedTemplates() + last.getUnknownIds() + last.getUnknownBlockIds());
        }

        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        if (machines.getActiveMachineFailures() > 0 || machines.getPausedMachineCircuits() > 0) {
            long count = (long) machines.getActiveMachineFailures() + machines.getPausedMachineCircuits();
            return new Recommendation(
                    Action.RUNTIME_HEALTH,
                    "Review Machine Runtime Failures",
                    "Machine isolation/failure state is active. Inspect it before retrying any machine.",
                    count);
        }

        long dependencyProblems = dependencyProblemCount();
        if (dependencyProblems > 0) {
            return new Recommendation(
                    Action.DEPENDENCY_HEALTH,
                    "Review Plugin Dependencies",
                    dependencyProblems + " required plugin dependency problem(s) are currently visible.",
                    dependencyProblems);
        }

        long integrationFailures = Slimefun.getExternalIntegrationService().getActiveFailureCount();
        if (integrationFailures > 0) {
            return new Recommendation(
                    Action.INTEGRATION_HEALTH,
                    "Review Integration Failures",
                    integrationFailures + " external integration operation(s) are degraded or isolated.",
                    integrationFailures);
        }

        if (!storage.wasPreviousShutdownClean()) {
            return new Recommendation(
                    Action.SUPPORT_SUMMARY,
                    "Review Previous Unclean Shutdown",
                    "Slimefun did not observe a clean previous shutdown. Review storage/runtime health before migrations.",
                    1);
        }

        int removalCandidates = Slimefun.getItemTextureService().getHostedPackRemovalCandidateCount();
        if (Slimefun.getItemTextureService().wasHostedPackModelMigrationApplied() && removalCandidates > 0) {
            return new Recommendation(
                    Action.REVIEW_MODEL_CLEANUP,
                    "Review Legacy Model Cleanup",
                    removalCandidates
                            + " exact bundled mapping(s) remain from a historical migration. Remove them only if that mapping is unwanted.",
                    removalCandidates);
        }

        return new Recommendation(
                Action.NONE,
                "No Immediate Doctor Fix Recommended",
                "Current Doctor, storage, machine, dependency, integration, and resource-pack signals do not require a repair action.",
                0);
    }

    static long dependencyProblemCount() {
        return new PluginDependencyDiagnosticsService(Slimefun.instance()).getSnapshots().stream()
                .mapToLong(snapshot -> snapshot.getRequiredDependencyProblemCount())
                .sum();
    }

    static @Nonnull String detectedPackManagers() {
        List<String> detected = new ArrayList<>();
        for (String name : KNOWN_PACK_MANAGERS) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null) {
                detected.add(plugin.getName() + (plugin.isEnabled() ? "" : " (disabled)"));
            }
        }
        return detected.isEmpty() ? "None detected" : String.join(", ", detected);
    }

    static long addonRuntimeFailureCount() {
        return Slimefun.getAddonRuntimeHealthService().getFailures().size();
    }
}
