package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidator;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaCandidateSummary;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedBlockIdMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.PersistedItemFormatMigrationService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Read-only orchestration for upgrading old Slimefun data through the existing guarded migration lanes. */
final class DoctorUpgradeWorkflow {

    private static final int MAX_PROVIDER_LINES = 20;

    private final Slimefun plugin;
    private final LegacyItemMigrationService migrationService;
    private final LegacyBlockMigrationService blockMigrationService;

    DoctorUpgradeWorkflow(@Nonnull Slimefun plugin, @Nonnull LegacyItemMigrationService migrationService) {
        this.plugin = plugin;
        this.migrationService = migrationService;
        blockMigrationService = new LegacyBlockMigrationService(plugin);
    }

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status" -> sendStatus(sender);
            case "scan" -> runScan(sender);
            case "plan", "dryrun", "dry-run" -> sendPlan(sender);
            case "providers", "provider" -> sendProviders(sender);
            default -> sendUsage(sender);
        }
    }

    private void sendStatus(@Nonnull CommandSender sender) {
        ItemDoctorReport report = latestReport();
        DoctorUpgradeSchemaCounts schemas = report == null ? DoctorUpgradeSchemaCounts.empty() : schemaCounts(report);

        send(sender, "&6Slimefun Legacy Upgrade Workflow");
        send(sender, "&7Legacy-ID migration providers: &e" + migrationService.getProviders().size());
        send(sender, "&7Same-ID schema providers: &e" + schemaProviderCapabilities().size());
        send(sender, "&7Exact placed-machine providers: &e" + blockMigrationService.getProviders().size());
        if (report == null) {
            send(sender, "&7Migration-aware server scan: &fNot run yet");
            send(sender, "&7Start with &e/sf doctor upgrade scan&7.");
        } else {
            send(sender, "&7Last/current server run: &e" + report.getModeName()
                    + (report.isComplete() ? " &a(complete)" : " &e(running)"));
            send(sender, "&7Declared legacy-ID candidates: &e" + report.getLegacyMigrationCandidates()
                    + " &8| &7distinct IDs: &e" + report.getLegacyMigrationCandidateCounts().size());
            send(sender, "&7Same-ID schema candidates: &e" + report.getSchemaMigrationCandidates()
                    + " &8| &7READY: &a" + schemas.ready()
                    + " &8| &7validation required: &e" + schemas.validationRequired()
                    + " &8| &7manual-only: &c" + schemas.manualOnly());
            send(sender, "&7Placed block IDs: legacy/alias &e" + report.getLegacyBlockIds()
                    + " &8| &7unknown &c" + report.getUnknownBlockIds());
            send(sender, "&7Unknown item IDs: &e" + report.getUnknownIds()
                    + " &8| &7unresolved templates: &e" + report.getUnresolvedTemplates()
                    + " &8| &7failures: &c" + report.getFailures());
        }
        send(sender, "&7Readiness snapshot: &e/sf doctor upgrade");
        send(sender, "&7Upgrade plan, including persisted storage audit: &e/sf doctor upgrade plan");
        send(sender, "&8The aggregate workflow never creates an execution fingerprint.");
    }

    private void runScan(@Nonnull CommandSender sender) {
        send(sender, "&6Slimefun Legacy Upgrade Discovery");
        send(sender, "&7Starting the existing migration-aware Doctor traversal.");
        send(sender, "&8Discovery is read-only and cannot authorize later mutation by itself.");
        DoctorScanWithLegacyCorrelation.run(plugin, sender);
    }

    private void sendPlan(@Nonnull CommandSender sender) {
        ItemDoctorReport report = latestReport();
        send(sender, "&6Slimefun Legacy Upgrade Plan");
        if (report == null || !report.isComplete() || report.isRepairMode()) {
            send(sender, "&eA completed read-only migration scan is required before planning.");
            send(sender, "&7Run &e/sf doctor upgrade scan &7and wait for its completion report.");
            send(sender, "&8No execution plan or fingerprint was created.");
            return;
        }

        Map<String, String> declaredMappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        Map<String, Long> legacyCandidates = report.getLegacyMigrationCandidateCounts();
        Set<String> providerCoveredIds = new HashSet<>();
        Set<String> safeProviderCoveredIds = new HashSet<>();
        long targetReadyLegacyStacks = 0L;
        long blockedLegacyStacks = 0L;

        for (Map.Entry<String, Long> entry : legacyCandidates.entrySet()) {
            String target = declaredMappings.get(entry.getKey());
            if (target != null && SlimefunItem.getById(target) != null) {
                targetReadyLegacyStacks += entry.getValue();
            } else {
                blockedLegacyStacks += entry.getValue();
            }
        }

        List<ProviderLane> legacyProviders = new ArrayList<>();
        for (RegisteredServiceProvider<LegacyItemMigrationProvider> registration : migrationService.getProviders()) {
            String providerId = migrationService.getProviderId(registration);
            Map<String, String> mappings = migrationService.getMappings(registration);
            List<String> problems = providerProblems(mappings);
            boolean safe = problems.isEmpty();
            long candidateStacks = 0L;
            for (String legacyId : mappings.keySet()) {
                Long count = legacyCandidates.get(legacyId);
                if (count != null) {
                    providerCoveredIds.add(legacyId);
                    if (safe) {
                        safeProviderCoveredIds.add(legacyId);
                    }
                    candidateStacks += count;
                }
            }
            if (candidateStacks > 0L) {
                legacyProviders.add(new ProviderLane(providerId, candidateStacks, safe));
            }
        }

        long actionableLegacyStacks = 0L;
        long needsProviderLegacyStacks = 0L;
        long providerCoveredStacks = 0L;
        for (Map.Entry<String, Long> entry : legacyCandidates.entrySet()) {
            String target = declaredMappings.get(entry.getKey());
            if (providerCoveredIds.contains(entry.getKey())) {
                providerCoveredStacks += entry.getValue();
            }
            if (target != null && SlimefunItem.getById(target) != null) {
                if (safeProviderCoveredIds.contains(entry.getKey())) {
                    actionableLegacyStacks += entry.getValue();
                } else {
                    needsProviderLegacyStacks += entry.getValue();
                }
            }
        }

        send(sender, "&eLane 1 - Legacy item IDs");
        send(sender, "&7Candidates: &e" + report.getLegacyMigrationCandidates()
                + " &8| &7target registered: &a" + targetReadyLegacyStacks
                + " &8| &7blocked target/mapping: &c" + blockedLegacyStacks);
        send(sender, "&7READY NOW: &a" + actionableLegacyStacks
                + " &8| &7NEEDS PROVIDER: &e" + needsProviderLegacyStacks
                + " &8| &7provider-owned: &f" + providerCoveredStacks);
        if (legacyProviders.isEmpty()) {
            send(sender, "&7No enabled legacy-ID provider currently owns a candidate found by the scan.");
        } else {
            int shown = 0;
            for (ProviderLane provider : legacyProviders) {
                if (shown++ >= MAX_PROVIDER_LINES) {
                    break;
                }
                send(sender, "&8- " + (provider.safe() ? "&a" : "&c") + provider.providerId()
                        + " &8| &7candidate stacks &e" + provider.candidateStacks()
                        + " &8| " + (provider.safe() ? "&aREADY NOW" : "&cBLOCKED PROVIDER"));
                if (provider.safe()) {
                    send(sender, "&8  &7Create its fresh fingerprint: &e/sf doctor migrations scan "
                            + provider.providerId());
                }
            }
        }
        send(sender, "&7Execute only the plugin-specific command printed by that native provider scan.");

        DoctorUpgradeSchemaCounts schemas = schemaCounts(report);
        Map<String, DoctorUpgradeSchemaCounts> schemaByProvider = schemaCountsByProvider(report);
        Map<String, DoctorUpgradeSchemaCapabilities> schemaCapabilities = schemaProviderCapabilities();
        DoctorUpgradeSchemaActionability schemaActionability = DoctorUpgradeSchemaActionability.empty();

        send(sender, "&eLane 2 - Same-ID item schemas");
        send(sender, "&7Candidates: &e" + report.getSchemaMigrationCandidates()
                + " &8| &7raw READY: &a" + schemas.ready()
                + " &8| &7claim-backed READY: &a" + schemas.readyClaimed()
                + " &8| &7diagnostic-only READY: &c" + schemas.readyDiagnosticOnly());
        send(sender, "&7Validation required: &e" + schemas.validationRequired()
                + " &8| &7manual-only: &c" + schemas.manualOnly());
        int shownSchemas = 0;
        for (Map.Entry<String, DoctorUpgradeSchemaCounts> entry : schemaByProvider.entrySet()) {
            DoctorUpgradeSchemaCapabilities capabilities =
                    schemaCapabilities.getOrDefault(entry.getKey(), DoctorUpgradeSchemaCapabilities.empty());
            DoctorUpgradeSchemaActionability actionability = DoctorUpgradePlanModel.classify(entry.getValue(), capabilities);
            schemaActionability = schemaActionability.add(actionability);

            if (shownSchemas++ >= MAX_PROVIDER_LINES) {
                continue;
            }
            send(sender, "&8- &f" + entry.getKey()
                    + " &8| &aREADY NOW " + actionability.readyNow()
                    + " &8| &eNEEDS VALIDATION " + actionability.needsValidation()
                    + " &8| &6NEEDS PROVIDER " + actionability.needsProvider()
                    + " &8| &cMANUAL " + actionability.manualOnly());
            String capabilityGap = schemaCapabilityGap(entry.getValue(), capabilities);
            if (!capabilityGap.isEmpty()) {
                send(sender, "&8  &7Missing execution capability: &e" + capabilityGap);
            }
            if (entry.getValue().readyDiagnosticOnly() > 0L) {
                send(sender, "&8  &7Claim-less READY candidates remain diagnostic-only and cannot be fingerprinted.");
            }
        }
        if (report.getSchemaMigrationCandidates() > 0L) {
            send(sender, "&7Create fresh schema fingerprints with: &e/sf doctor migrations schemas scan");
            send(sender, "&7Execute only the exact &e/sf doctor migrations schemas execute <plugin> <fingerprint>"
                    + " &7command printed by that scan.");
        }

        send(sender, "&eLane 3 - Exact placed-machine migrations");
        List<RegisteredServiceProvider<LegacyBlockMigrationProvider>> blockProviders = blockMigrationService.getProviders();
        if (blockProviders.isEmpty()) {
            send(sender, "&7No enabled addon has registered an exact placed-machine migration provider.");
        } else {
            int shownBlocks = 0;
            for (RegisteredServiceProvider<LegacyBlockMigrationProvider> registration : blockProviders) {
                if (shownBlocks++ >= MAX_PROVIDER_LINES) {
                    break;
                }
                String providerId = blockMigrationService.getProviderId(registration);
                Map<String, String> mappings = blockMigrationService.getMappings(registration);
                send(sender, "&8- &f" + providerId
                        + " &8| &7" + blockMigrationService.getProviderName(registration)
                        + " &8| &7declared mappings &e" + mappings.size());
                send(sender, "&8  &7Read-only loaded-scope authorization scan: &e/sf doctor migrations blocks scan "
                        + providerId);
            }
            send(sender, "&8Exact-machine scans do not force-load chunks; load old regions and re-scan when needed.");
        }

        PersistedBlockIdMigrationService.AuditResult blockAudit = new PersistedBlockIdMigrationService().audit();
        PersistedItemFormatMigrationService.AuditResult itemAudit = new PersistedItemFormatMigrationService().audit();
        boolean storageAuditIncomplete = blockAudit.busy() || itemAudit.busy();
        long storageReady = 0L;
        long storageDeferred = 0L;
        long storageManual = 0L;

        send(sender, "&eLane 4 - Persisted storage");
        if (blockAudit.busy()) {
            send(sender, "&ePersisted block-ID audit unavailable because storage is busy; this upgrade picture is incomplete.");
        } else {
            storageReady += blockAudit.immediatelyRewritableCandidates();
            storageDeferred += blockAudit.loadedCandidates();
            storageManual += blockAudit.unknownRecords() + blockAudit.missingTargetRecords();
            send(sender, "&7Block/universal identities scanned: &e" + blockAudit.scannedRecords()
                    + " &8| &7canonical &a" + blockAudit.canonicalRecords()
                    + " &8| &7rewrite candidates &e" + blockAudit.rewriteCandidates());
            send(sender, "&7Immediately rewritable: &a" + blockAudit.immediatelyRewritableCandidates()
                    + " &8| &7loaded/protected: &e" + blockAudit.loadedCandidates()
                    + " &8| &7unknown: &c" + blockAudit.unknownRecords()
                    + " &8| &7missing targets: &c" + blockAudit.missingTargetRecords());
            if (blockAudit.rewriteCandidates() > 0L) {
                send(sender, "&7Create the native persisted-ID fingerprint: &e/sf doctor migrations schemas blocks scan");
            }
        }

        if (itemAudit.busy()) {
            send(sender, "&ePersisted item-payload audit unavailable because storage is busy; this upgrade picture is incomplete.");
        } else {
            storageReady += itemAudit.rewriteCandidates();
            storageManual += itemAudit.unreadableLegacyRecords();
            send(sender, "&7Stored inventory payloads scanned: &e" + itemAudit.scannedRecords()
                    + " &8| &7current &a" + itemAudit.currentRecords()
                    + " &8| &7legacy rewrites &e" + itemAudit.rewriteCandidates()
                    + " &8| &7unreadable legacy &c" + itemAudit.unreadableLegacyRecords());
            if (itemAudit.rewriteCandidates() > 0L || itemAudit.unreadableLegacyRecords() > 0L) {
                send(sender, "&7Create the native stored-item fingerprint: &e/sf doctor migrations schemas storage scan");
            }
        }
        send(sender, "&8Storage audit is read-only and creates no execution fingerprint.");

        long manualBlocked = report.getUnknownIds()
                + report.getUnresolvedTemplates()
                + blockedLegacyStacks
                + schemaActionability.manualOnly()
                + report.getUnknownBlockIds();
        long readyNow = actionableLegacyStacks + schemaActionability.readyNow();
        long needsValidation = schemaActionability.needsValidation();
        long needsProvider = needsProviderLegacyStacks + schemaActionability.needsProvider();

        send(sender, "&eLane 5 - Manual/unresolved evidence");
        send(sender, "&7Placed block identity signals: legacy/alias &e" + report.getLegacyBlockIds()
                + " &8| &7unknown &c" + report.getUnknownBlockIds());
        if (report.getLegacyBlockIds() > 0L) {
            send(sender, "&7Legacy/alias placed-block IDs are migration signals, not automatically manual-only; use Lane 3/4 native scans.");
        }
        send(sender, "&7Traversal manual/blocked signals: &c" + manualBlocked
                + " &8| &7Doctor traversal failures: &c" + report.getFailures());
        if (manualBlocked > 0L || report.getFailures() > 0L) {
            send(sender, "&eDo not guess-convert these entries. Resolve addon ownership/schema evidence first.");
        } else {
            send(sender, "&aNo manual/unresolved evidence was counted by the completed traversal.");
        }

        send(sender, "&6Upgrade candidate summary - live/item traversal");
        send(sender, "&aREADY NOW: " + readyNow
                + " &8| &eNEEDS VALIDATION: " + needsValidation
                + " &8| &6NEEDS PROVIDER: " + needsProvider
                + " &8| &cMANUAL/BLOCKED: " + manualBlocked);
        send(sender, "&6Persisted storage summary - reported separately to avoid double-counting loaded records");
        if (storageAuditIncomplete) {
            send(sender, "&eStorage audit incomplete: run the plan again after pending storage work drains.");
        } else {
            send(sender, "&aREADY NOW: " + storageReady
                    + " &8| &eLOADED/DEFERRED: " + storageDeferred
                    + " &8| &cMANUAL/BLOCKED: " + storageManual);
            if (storageReady == 0L && storageDeferred == 0L && storageManual == 0L) {
                send(sender, "&aNo persisted block-ID or stored-item format migration work is currently detected.");
            }
        }
        if (report.getFailures() > 0L) {
            send(sender, "&cTraversal failures must be resolved before treating this scan as a complete upgrade picture.");
        }
        send(sender, "&8READY NOW still requires the native fingerprint scan and explicit execution command.");
        send(sender, "&8Read-only plan only: no provider repair, schema migrator, block-ID rewrite, registry rewrite, or storage mutation ran.");
        send(sender, "&8Legacy-ID, same-ID schema, exact-machine and persisted-storage fingerprints remain separate and single-use.");
    }

    private void sendProviders(@Nonnull CommandSender sender) {
        send(sender, "&6Slimefun Legacy Upgrade Providers");
        List<RegisteredServiceProvider<LegacyItemMigrationProvider>> legacyProviders = migrationService.getProviders();
        send(sender, "&7Legacy-ID providers: &e" + legacyProviders.size());
        for (int i = 0; i < Math.min(legacyProviders.size(), MAX_PROVIDER_LINES); i++) {
            RegisteredServiceProvider<LegacyItemMigrationProvider> registration = legacyProviders.get(i);
            Map<String, String> mappings = migrationService.getMappings(registration);
            List<String> problems = providerProblems(mappings);
            send(sender, "&8- &f" + migrationService.getProviderId(registration)
                    + " &8| &7" + migrationService.getProviderName(registration)
                    + " &8| &7mappings &e" + mappings.size()
                    + " &8| " + (problems.isEmpty() ? "&aREADY" : "&cBLOCKED"));
            if (!problems.isEmpty()) {
                send(sender, "&8  &7First problem: &c" + problems.getFirst());
            }
        }

        Map<String, DoctorUpgradeSchemaCapabilities> schemas = schemaProviderCapabilities();
        send(sender, "&7Same-ID schema plugins: &e" + schemas.size());
        int shown = 0;
        for (Map.Entry<String, DoctorUpgradeSchemaCapabilities> entry : schemas.entrySet()) {
            if (shown++ >= MAX_PROVIDER_LINES) {
                break;
            }
            DoctorUpgradeSchemaCapabilities capabilities = entry.getValue();
            send(sender, "&8- &f" + entry.getKey()
                    + " &8| &7probe " + yesNo(capabilities.probe())
                    + " &8| &7validator " + yesNo(capabilities.validator())
                    + " &8| &7migrator " + yesNo(capabilities.migrator()));
        }

        List<RegisteredServiceProvider<LegacyBlockMigrationProvider>> blockProviders = blockMigrationService.getProviders();
        send(sender, "&7Exact placed-machine providers: &e" + blockProviders.size());
        for (int i = 0; i < Math.min(blockProviders.size(), MAX_PROVIDER_LINES); i++) {
            RegisteredServiceProvider<LegacyBlockMigrationProvider> registration = blockProviders.get(i);
            send(sender, "&8- &f" + blockMigrationService.getProviderId(registration)
                    + " &8| &7" + blockMigrationService.getProviderName(registration)
                    + " &8| &7mappings &e" + blockMigrationService.getMappings(registration).size());
        }

        send(sender, "&7Legacy-ID native gate: &e/sf doctor migrations scan <plugin>");
        send(sender, "&7Same-ID native gate: &e/sf doctor migrations schemas scan");
        send(sender, "&7Exact-machine native gate: &e/sf doctor migrations blocks scan <plugin>");
        send(sender, "&7Persisted block-ID native gate: &e/sf doctor migrations schemas blocks scan");
        send(sender, "&7Persisted item-payload native gate: &e/sf doctor migrations schemas storage scan");
        send(sender, "&8Provider discovery and storage audit are read-only; no execution fingerprint was created.");
    }

    private List<String> providerProblems(@Nonnull Map<String, String> providerMappings) {
        List<String> problems = new ArrayList<>();
        Map<String, String> declared = Slimefun.getRegistry().getLegacySlimefunItemIds();
        if (providerMappings.isEmpty()) {
            problems.add("Provider published no legacy ID mappings.");
            return problems;
        }
        for (Map.Entry<String, String> entry : providerMappings.entrySet()) {
            String declaredTarget = declared.get(entry.getKey());
            if (declaredTarget == null) {
                problems.add(entry.getKey() + " is not registered in Slimefun's legacy-ID registry.");
            } else if (!declaredTarget.equals(entry.getValue())) {
                problems.add(entry.getKey() + " disagrees with registry target " + declaredTarget + ".");
            } else if (SlimefunItem.getById(entry.getValue()) == null) {
                problems.add(entry.getKey() + " targets missing item " + entry.getValue() + ".");
            }
        }
        return problems;
    }

    private Map<String, DoctorUpgradeSchemaCapabilities> schemaProviderCapabilities() {
        Map<String, DoctorUpgradeSchemaCapabilities> providers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (RegisteredServiceProvider<LegacyItemSchemaProbe> registration :
                plugin.getServer().getServicesManager().getRegistrations(LegacyItemSchemaProbe.class)) {
            providers.compute(registration.getPlugin().getName(), (ignored, value) ->
                    (value == null ? DoctorUpgradeSchemaCapabilities.empty() : value).withProbe());
        }
        for (RegisteredServiceProvider<LegacyItemSchemaValidator> registration :
                plugin.getServer().getServicesManager().getRegistrations(LegacyItemSchemaValidator.class)) {
            providers.compute(registration.getPlugin().getName(), (ignored, value) ->
                    (value == null ? DoctorUpgradeSchemaCapabilities.empty() : value).withValidator());
        }
        for (RegisteredServiceProvider<LegacyItemSchemaMigrator> registration :
                plugin.getServer().getServicesManager().getRegistrations(LegacyItemSchemaMigrator.class)) {
            providers.compute(registration.getPlugin().getName(), (ignored, value) ->
                    (value == null ? DoctorUpgradeSchemaCapabilities.empty() : value).withMigrator());
        }
        return providers;
    }

    private DoctorUpgradeSchemaCounts schemaCounts(@Nonnull ItemDoctorReport report) {
        DoctorUpgradeSchemaCounts counts = DoctorUpgradeSchemaCounts.empty();
        for (LegacyItemSchemaCandidateSummary summary : report.getSchemaMigrationCandidateSummaries()) {
            counts = counts.add(summary.getReadiness(), summary.hasItemLocalClaim(), summary.getCount());
        }
        return counts;
    }

    private Map<String, DoctorUpgradeSchemaCounts> schemaCountsByProvider(@Nonnull ItemDoctorReport report) {
        Map<String, DoctorUpgradeSchemaCounts> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (LegacyItemSchemaCandidateSummary summary : report.getSchemaMigrationCandidateSummaries()) {
            DoctorUpgradeSchemaCounts current =
                    counts.getOrDefault(summary.getProviderId(), DoctorUpgradeSchemaCounts.empty());
            counts.put(
                    summary.getProviderId(),
                    current.add(summary.getReadiness(), summary.hasItemLocalClaim(), summary.getCount()));
        }
        return counts;
    }

    private String schemaCapabilityGap(
            DoctorUpgradeSchemaCounts counts, DoctorUpgradeSchemaCapabilities capabilities) {
        Set<String> missing = new HashSet<>();
        if (counts.readyClaimed() > 0L) {
            if (!capabilities.probe()) {
                missing.add("probe");
            }
            if (!capabilities.migrator()) {
                missing.add("migrator");
            }
        }
        if (counts.validationRequired() > 0L) {
            if (!capabilities.probe()) {
                missing.add("probe");
            }
            if (!capabilities.validator()) {
                missing.add("validator");
            }
            if (!capabilities.migrator()) {
                missing.add("migrator");
            }
        }
        return String.join(", ", missing.stream().sorted().toList());
    }

    private ItemDoctorReport latestReport() {
        ItemDoctorReport current = Slimefun.getItemDoctorService().getCurrentReport();
        return current != null ? current : Slimefun.getItemDoctorService().getLastReport();
    }

    private void sendUsage(@Nonnull CommandSender sender) {
        send(sender, "&eUsage: /sf doctor upgrade <status|scan|plan|providers>");
        send(sender, "&7Plain &e/sf doctor upgrade &7still shows the existing runtime upgrade-readiness snapshot.");
    }

    private String yesNo(boolean value) {
        return value ? "&aYes" : "&7No";
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }

    private record ProviderLane(String providerId, long candidateStacks, boolean safe) {}
}
