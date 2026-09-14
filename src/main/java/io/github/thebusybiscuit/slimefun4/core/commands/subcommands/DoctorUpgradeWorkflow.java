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
        this.blockMigrationService = new LegacyBlockMigrationService(plugin);
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
        send(sender, "&7Legacy-ID item migration providers: &e" + migrationService.getProviders().size());
        send(sender, "&7Legacy machine migration providers: &e" + blockMigrationService.getProviders().size());
        send(sender, "&7Same-ID schema providers: &e" + schemaProviderCapabilities().size());
        if (report == null) {
            send(sender, "&7Migration-aware server scan: &fNot run yet");
            send(sender, "&7Start with &e/sf doctor upgrade scan&7.");
        } else {
            send(sender, "&7Last/current server run: &e" + report.getModeName()
                    + (report.isComplete() ? " &a(complete)" : " &e(running)"));
            send(sender, "&7Declared legacy-ID item candidates: &e" + report.getLegacyMigrationCandidates()
                    + " &8| &7distinct IDs: &e" + report.getLegacyMigrationCandidateCounts().size());
            send(sender, "&7Same-ID schema candidates: &e" + report.getSchemaMigrationCandidates()
                    + " &8| &7READY: &a" + schemas.ready()
                    + " &8| &7validation required: &e" + schemas.validationRequired()
                    + " &8| &7manual-only: &c" + schemas.manualOnly());
            send(sender, "&7Placed block IDs: legacy/alias &e" + report.getLegacyBlockIds()
                    + " &8| &7distinct legacy IDs &e" + report.getLegacyBlockIdCounts().size()
                    + " &8| &7unknown &c" + report.getUnknownBlockIds());
            send(sender, "&7Unknown item IDs: &e" + report.getUnknownIds()
                    + " &8| &7unresolved templates: &e" + report.getUnresolvedTemplates()
                    + " &8| &7failures: &c" + report.getFailures());
        }
        send(sender, "&7Readiness snapshot: &e/sf doctor upgrade");
        send(sender, "&7Upgrade plan: &e/sf doctor upgrade plan");
        send(sender, "&8This workflow never creates a combined authorization or execution fingerprint.");
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
            send(sender, "&7No enabled legacy-ID provider currently owns an item candidate found by the scan.");
        } else {
            int shown = 0;
            for (ProviderLane provider : legacyProviders) {
                if (shown++ >= MAX_PROVIDER_LINES) {
                    break;
                }
                send(sender, "&8- " + (provider.safe() ? "&a" : "&c") + provider.providerId()
                        + " &8| &7candidate stacks &e" + provider.candidates()
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

        Map<String, Long> legacyBlocks = report.getLegacyBlockIdCounts();
        Set<String> blockProviderCoveredIds = new HashSet<>();
        Set<String> safeBlockProviderCoveredIds = new HashSet<>();
        List<ProviderLane> blockProviders = new ArrayList<>();
        for (RegisteredServiceProvider<LegacyBlockMigrationProvider> registration : blockMigrationService.getProviders()) {
            String providerId = blockMigrationService.getProviderId(registration);
            Map<String, String> mappings = blockMigrationService.getMappings(registration);
            List<String> problems = providerProblems(mappings);
            boolean safe = problems.isEmpty();
            long candidateBlocks = 0L;
            for (String legacyId : mappings.keySet()) {
                Long count = legacyBlocks.get(legacyId);
                if (count != null) {
                    blockProviderCoveredIds.add(legacyId);
                    if (safe) {
                        safeBlockProviderCoveredIds.add(legacyId);
                    }
                    candidateBlocks += count;
                }
            }
            if (candidateBlocks > 0L) {
                blockProviders.add(new ProviderLane(providerId, candidateBlocks, safe));
            }
        }

        long actionableLegacyBlocks = 0L;
        long needsProviderLegacyBlocks = 0L;
        long blockedLegacyBlocks = 0L;
        long providerCoveredBlocks = 0L;
        for (Map.Entry<String, Long> entry : legacyBlocks.entrySet()) {
            long count = entry.getValue();
            String target = declaredMappings.get(entry.getKey());
            if (blockProviderCoveredIds.contains(entry.getKey())) {
                providerCoveredBlocks += count;
            }
            if (target == null || SlimefunItem.getById(target) == null) {
                blockedLegacyBlocks += count;
            } else if (safeBlockProviderCoveredIds.contains(entry.getKey())) {
                actionableLegacyBlocks += count;
            } else {
                needsProviderLegacyBlocks += count;
            }
        }

        send(sender, "&eLane 3 - Legacy placed machines");
        send(sender, "&7Legacy/alias block candidates: &e" + report.getLegacyBlockIds()
                + " &8| &7distinct IDs: &e" + legacyBlocks.size()
                + " &8| &7unknown blocks: &c" + report.getUnknownBlockIds());
        send(sender, "&7READY NOW: &a" + actionableLegacyBlocks
                + " &8| &7NEEDS PROVIDER: &e" + needsProviderLegacyBlocks
                + " &8| &7BLOCKED TARGET/MAPPING: &c" + blockedLegacyBlocks
                + " &8| &7provider-owned: &f" + providerCoveredBlocks);
        if (blockProviders.isEmpty()) {
            if (!legacyBlocks.isEmpty()) {
                send(sender, "&7No enabled exact machine provider owns a legacy placed block found by this scan.");
            }
        } else {
            int shown = 0;
            for (ProviderLane provider : blockProviders) {
                if (shown++ >= MAX_PROVIDER_LINES) {
                    break;
                }
                send(sender, "&8- " + (provider.safe() ? "&a" : "&c") + provider.providerId()
                        + " &8| &7candidate blocks &e" + provider.candidates()
                        + " &8| " + (provider.safe() ? "&aREADY NOW" : "&cBLOCKED PROVIDER"));
                if (provider.safe()) {
                    send(sender, "&8  &7Create its exact machine fingerprint: &e/sf doctor migrations blocks scan "
                            + provider.providerId());
                }
            }
        }
        if (report.getUnknownBlockIds() > 0L) {
            send(sender, "&eUnknown placed block IDs stay manual. Doctor will not guess their addon or replacement.");
        }

        long manualBlocked = report.getUnknownIds()
                + report.getUnresolvedTemplates()
                + blockedLegacyStacks
                + schemaActionability.manualOnly()
                + blockedLegacyBlocks
                + report.getUnknownBlockIds();
        long readyNow = actionableLegacyStacks + schemaActionability.readyNow() + actionableLegacyBlocks;
        long needsValidation = schemaActionability.needsValidation();
        long needsProvider = needsProviderLegacyStacks
                + schemaActionability.needsProvider()
                + needsProviderLegacyBlocks;

        send(sender, "&eLane 4 - Manual/unresolved evidence");
        send(sender, "&7Unknown placed block IDs: &c" + report.getUnknownBlockIds()
                + " &8| &7unknown item IDs: &c" + report.getUnknownIds()
                + " &8| &7unresolved templates: &e" + report.getUnresolvedTemplates());
        send(sender, "&7Manual/blocked candidate signals: &c" + manualBlocked
                + " &8| &7Doctor traversal failures: &c" + report.getFailures());
        if (manualBlocked > 0L || report.getFailures() > 0L) {
            send(sender, "&eDo not guess-convert these entries. Resolve addon ownership/schema evidence first.");
        } else {
            send(sender, "&aNo manual/unresolved evidence was counted by this completed scan.");
        }

        send(sender, "&6Upgrade candidate summary");
        send(sender, "&aREADY NOW: " + readyNow
                + " &8| &eNEEDS VALIDATION: " + needsValidation
                + " &8| &6NEEDS PROVIDER: " + needsProvider
                + " &8| &cMANUAL/BLOCKED: " + manualBlocked);
        if (report.getFailures() > 0L) {
            send(sender, "&cTraversal failures must be resolved before treating this scan as a complete upgrade picture.");
        }
        send(sender, "&8READY NOW still requires the native fingerprint scan and explicit execution command.");
        send(sender, "&8Read-only plan only: no provider repair, schema migrator, block-ID rewrite, registry rewrite, or storage mutation ran.");
        send(sender, "&8Item-ID, machine-ID and same-ID schema fingerprints remain separate, short-lived and single-use.");
    }

    private void sendProviders(@Nonnull CommandSender sender) {
        send(sender, "&6Slimefun Legacy Upgrade Providers");
        List<RegisteredServiceProvider<LegacyItemMigrationProvider>> legacyProviders = migrationService.getProviders();
        send(sender, "&7Legacy-ID item providers: &e" + legacyProviders.size());
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

        List<RegisteredServiceProvider<LegacyBlockMigrationProvider>> blockProviders = blockMigrationService.getProviders();
        send(sender, "&7Exact placed-machine providers: &e" + blockProviders.size());
        for (int i = 0; i < Math.min(blockProviders.size(), MAX_PROVIDER_LINES); i++) {
            RegisteredServiceProvider<LegacyBlockMigrationProvider> registration = blockProviders.get(i);
            Map<String, String> mappings = blockMigrationService.getMappings(registration);
            List<String> problems = providerProblems(mappings);
            send(sender, "&8- &f" + blockMigrationService.getProviderId(registration)
                    + " &8| &7" + blockMigrationService.getProviderName(registration)
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
        send(sender, "&7Legacy-ID item gate: &e/sf doctor migrations scan <plugin>");
        send(sender, "&7Legacy machine gate: &e/sf doctor migrations blocks scan <plugin>");
        send(sender, "&7Same-ID native gate: &e/sf doctor migrations schemas scan");
        send(sender, "&8Provider discovery is read-only; no execution fingerprint was created.");
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

    private record ProviderLane(String providerId, long candidates, boolean safe) {}
}
