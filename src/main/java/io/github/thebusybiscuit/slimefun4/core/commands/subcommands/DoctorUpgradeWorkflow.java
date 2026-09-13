package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidator;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
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

    DoctorUpgradeWorkflow(@Nonnull Slimefun plugin, @Nonnull LegacyItemMigrationService migrationService) {
        this.plugin = plugin;
        this.migrationService = migrationService;
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
        SchemaCounts schemas = report == null ? SchemaCounts.empty() : schemaCounts(report);

        send(sender, "&6Slimefun Legacy Upgrade Workflow");
        send(sender, "&7Legacy-ID migration providers: &e" + migrationService.getProviders().size());
        send(sender, "&7Same-ID schema providers: &e" + schemaProviderCapabilities().size());
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
            send(sender, "&7Unknown IDs: &e" + report.getUnknownIds()
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
        long readyLegacyStacks = 0L;
        long blockedLegacyStacks = 0L;
        long providerCoveredStacks = 0L;

        for (Map.Entry<String, Long> entry : legacyCandidates.entrySet()) {
            String target = declaredMappings.get(entry.getKey());
            if (target != null && SlimefunItem.getById(target) != null) {
                readyLegacyStacks += entry.getValue();
            } else {
                blockedLegacyStacks += entry.getValue();
            }
        }

        List<ProviderLane> legacyProviders = new ArrayList<>();
        for (RegisteredServiceProvider<LegacyItemMigrationProvider> registration : migrationService.getProviders()) {
            String providerId = migrationService.getProviderId(registration);
            Map<String, String> mappings = migrationService.getMappings(registration);
            long candidateStacks = 0L;
            for (String legacyId : mappings.keySet()) {
                Long count = legacyCandidates.get(legacyId);
                if (count != null && providerCoveredIds.add(legacyId)) {
                    providerCoveredStacks += count;
                }
                if (count != null) {
                    candidateStacks += count;
                }
            }
            if (candidateStacks > 0L) {
                legacyProviders.add(new ProviderLane(providerId, candidateStacks, providerProblems(mappings).isEmpty()));
            }
        }

        long uncoveredLegacyStacks = Math.max(0L, readyLegacyStacks - providerCoveredStacks);
        send(sender, "&eLane 1 - Legacy item IDs");
        send(sender, "&7Candidates: &e" + report.getLegacyMigrationCandidates()
                + " &8| &7registered target ready: &a" + readyLegacyStacks
                + " &8| &7blocked target/mapping: &c" + blockedLegacyStacks);
        send(sender, "&7Provider-covered stacks: &e" + providerCoveredStacks
                + " &8| &7ready but no migration provider: &c" + uncoveredLegacyStacks);
        if (legacyProviders.isEmpty()) {
            send(sender, "&7No enabled legacy-ID provider currently owns a candidate found by the scan.");
        } else {
            int shown = 0;
            for (ProviderLane provider : legacyProviders) {
                if (shown++ >= MAX_PROVIDER_LINES) break;
                send(sender, "&8- " + (provider.safe() ? "&a" : "&c") + provider.providerId()
                        + " &8| &7candidate stacks &e" + provider.candidateStacks()
                        + " &8| " + (provider.safe() ? "&aREADY" : "&cBLOCKED"));
                if (provider.safe()) {
                    send(sender, "&8  &7Create its fresh fingerprint: &e/sf doctor migrations scan "
                            + provider.providerId());
                }
            }
        }
        send(sender, "&7Execute only the plugin-specific command printed by that native provider scan.");

        SchemaCounts schemas = schemaCounts(report);
        Map<String, SchemaCounts> schemaByProvider = schemaCountsByProvider(report);
        send(sender, "&eLane 2 - Same-ID item schemas");
        send(sender, "&7Candidates: &e" + report.getSchemaMigrationCandidates()
                + " &8| &7READY: &a" + schemas.ready()
                + " &8| &7validation required: &e" + schemas.validationRequired()
                + " &8| &7manual-only: &c" + schemas.manualOnly());
        int shownSchemas = 0;
        for (Map.Entry<String, SchemaCounts> entry : schemaByProvider.entrySet()) {
            if (shownSchemas++ >= MAX_PROVIDER_LINES) break;
            SchemaCounts counts = entry.getValue();
            send(sender, "&8- &f" + entry.getKey() + " &8| &7READY &a" + counts.ready()
                    + " &8| &7validate &e" + counts.validationRequired()
                    + " &8| &7manual &c" + counts.manualOnly());
        }
        if (report.getSchemaMigrationCandidates() > 0L) {
            send(sender, "&7Create fresh schema fingerprints with: &e/sf doctor migrations schemas scan");
            send(sender, "&7Execute only the exact &e/sf doctor migrations schemas execute <plugin> <fingerprint>"
                    + " &7command printed by that scan.");
        }

        long unresolved = report.getUnknownIds()
                + report.getUnresolvedTemplates()
                + blockedLegacyStacks
                + uncoveredLegacyStacks
                + schemas.manualOnly();
        send(sender, "&eLane 3 - Manual/unresolved evidence");
        send(sender, "&7Manual/unresolved signals: &c" + unresolved
                + " &8| &7Doctor traversal failures: &c" + report.getFailures());
        if (unresolved > 0L || report.getFailures() > 0L) {
            send(sender, "&eDo not guess-convert these entries. Resolve addon ownership/schema evidence first.");
        } else {
            send(sender, "&aNo manual/unresolved evidence was counted by this completed scan.");
        }

        send(sender, "&8Read-only plan only: no provider repair, schema migrator, registry rewrite, or storage mutation ran.");
        send(sender, "&8Legacy-ID and same-ID schema fingerprints remain separate, short-lived and single-use.");
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

        Map<String, SchemaCapabilities> schemas = schemaProviderCapabilities();
        send(sender, "&7Same-ID schema plugins: &e" + schemas.size());
        int shown = 0;
        for (Map.Entry<String, SchemaCapabilities> entry : schemas.entrySet()) {
            if (shown++ >= MAX_PROVIDER_LINES) break;
            SchemaCapabilities capabilities = entry.getValue();
            send(sender, "&8- &f" + entry.getKey()
                    + " &8| &7probe " + yesNo(capabilities.probe())
                    + " &8| &7validator " + yesNo(capabilities.validator())
                    + " &8| &7migrator " + yesNo(capabilities.migrator()));
        }
        send(sender, "&7Legacy-ID native gate: &e/sf doctor migrations scan <plugin>");
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

    private Map<String, SchemaCapabilities> schemaProviderCapabilities() {
        Map<String, SchemaCapabilities> providers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (RegisteredServiceProvider<LegacyItemSchemaProbe> registration :
                plugin.getServer().getServicesManager().getRegistrations(LegacyItemSchemaProbe.class)) {
            providers.compute(registration.getPlugin().getName(), (ignored, value) ->
                    (value == null ? SchemaCapabilities.empty() : value).withProbe());
        }
        for (RegisteredServiceProvider<LegacyItemSchemaValidator> registration :
                plugin.getServer().getServicesManager().getRegistrations(LegacyItemSchemaValidator.class)) {
            providers.compute(registration.getPlugin().getName(), (ignored, value) ->
                    (value == null ? SchemaCapabilities.empty() : value).withValidator());
        }
        for (RegisteredServiceProvider<LegacyItemSchemaMigrator> registration :
                plugin.getServer().getServicesManager().getRegistrations(LegacyItemSchemaMigrator.class)) {
            providers.compute(registration.getPlugin().getName(), (ignored, value) ->
                    (value == null ? SchemaCapabilities.empty() : value).withMigrator());
        }
        return providers;
    }

    private SchemaCounts schemaCounts(@Nonnull ItemDoctorReport report) {
        long ready = 0L;
        long validationRequired = 0L;
        long manualOnly = 0L;
        for (LegacyItemSchemaCandidateSummary summary : report.getSchemaMigrationCandidateSummaries()) {
            switch (summary.getReadiness()) {
                case READY -> ready += summary.getCount();
                case VALIDATION_REQUIRED -> validationRequired += summary.getCount();
                case MANUAL_ONLY -> manualOnly += summary.getCount();
            }
        }
        return new SchemaCounts(ready, validationRequired, manualOnly);
    }

    private Map<String, SchemaCounts> schemaCountsByProvider(@Nonnull ItemDoctorReport report) {
        Map<String, SchemaCounts> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (LegacyItemSchemaCandidateSummary summary : report.getSchemaMigrationCandidateSummaries()) {
            SchemaCounts current = counts.getOrDefault(summary.getProviderId(), SchemaCounts.empty());
            counts.put(summary.getProviderId(), current.add(summary.getReadiness(), summary.getCount()));
        }
        return counts;
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

    private record SchemaCounts(long ready, long validationRequired, long manualOnly) {
        static SchemaCounts empty() {
            return new SchemaCounts(0L, 0L, 0L);
        }

        SchemaCounts add(LegacyItemSchemaCandidate.Readiness readiness, long count) {
            return switch (readiness) {
                case READY -> new SchemaCounts(ready + count, validationRequired, manualOnly);
                case VALIDATION_REQUIRED -> new SchemaCounts(ready, validationRequired + count, manualOnly);
                case MANUAL_ONLY -> new SchemaCounts(ready, validationRequired, manualOnly + count);
            };
        }
    }

    private record SchemaCapabilities(boolean probe, boolean validator, boolean migrator) {
        static SchemaCapabilities empty() {
            return new SchemaCapabilities(false, false, false);
        }

        SchemaCapabilities withProbe() {
            return new SchemaCapabilities(true, validator, migrator);
        }

        SchemaCapabilities withValidator() {
            return new SchemaCapabilities(probe, true, migrator);
        }

        SchemaCapabilities withMigrator() {
            return new SchemaCapabilities(probe, validator, true);
        }
    }
}
