package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaMigrationExecutor;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemSchemaValidationRunner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Explicit operator gate for fingerprint-authorized same-ID item schema migration. */
final class DoctorSchemaMigrationCommand {

    private final Slimefun plugin;
    private final LegacyItemSchemaMigrationService migrationService;
    private final DoctorBlockIdMigrationCommand blockIdMigrations;
    private final DoctorStoredItemMigrationCommand storedItemMigrations;

    DoctorSchemaMigrationCommand(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        migrationService = new LegacyItemSchemaMigrationService(plugin);
        blockIdMigrations = new DoctorBlockIdMigrationCommand();
        storedItemMigrations = new DoctorStoredItemMigrationCommand();
    }

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        String action = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status", "plans", "list" -> sendPlans(sender);
            case "scan", "plan" -> runPlanScan(sender);
            case "execute", "repair" -> executePlan(sender, args);
            case "blocks", "block-ids", "blockids" -> blockIdMigrations.execute(sender, args);
            case "storage", "stored-items", "storeditems" -> storedItemMigrations.execute(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void runPlanScan(@Nonnull CommandSender sender) {
        ItemDoctorService doctor = Slimefun.getItemDoctorService();
        if (!doctor.isEnabled()) {
            send(sender, "&cThe item doctor is disabled in config.yml.");
            return;
        }

        migrationService.invalidateAllPreparedPlans();
        boolean started = doctor.startMigrationAwareServerRun(report -> {
            send(sender, "&7Schema-plan traversal complete. Validating addon-owned persistent-state claims...");
            LegacyItemSchemaValidationRunner.validate(report).whenComplete((ignored, error) ->
                    Slimefun.getSchedulerService().run(() -> finishPlanScan(sender, report, error)));
        });

        if (!started) {
            send(sender, "&eA server-wide Doctor run is already active. No schema plan was created.");
            return;
        }

        send(sender, "&aStarted a read-only same-ID schema migration scan.");
        send(sender, "&7No items are changed during this phase. Existing schema plans were invalidated before scanning.");
        send(sender, "&7The scan covers online inventories, loaded storage/machines, nested containers and all backpacks.");
        send(sender, "&7Unloaded chunks and offline player inventories are not force-loaded.");
    }

    private void finishPlanScan(
            @Nonnull CommandSender sender, @Nonnull ItemDoctorReport report, Throwable validationError) {
        if (validationError != null) {
            migrationService.invalidateAllPreparedPlans();
            plugin.getLogger().log(
                    Level.WARNING,
                    "Same-ID schema validation ended unexpectedly; no execution plan was retained.",
                    validationError);
            send(sender, "&cSchema validation failed unexpectedly. No execution fingerprint was created.");
            return;
        }

        List<LegacyItemSchemaMigrationPlan> plans = migrationService.preparePlans(report);
        send(sender, "&6Slimefun Doctor Same-ID Schema Plan");
        send(sender, "&7Stacks scanned: &e" + report.getScannedStacks()
                + " &8| &7schema candidates: &e" + report.getSchemaMigrationCandidates()
                + " &8| &7validated: &e" + report.getSchemaValidatedCandidates()
                + " &8| &7failures: &c" + report.getFailures());

        if (plans.isEmpty()) {
            send(sender, "&eNo executable same-ID schema plan was created.");
            send(sender, "&7Only clean, completed scans with VERIFIED addon validation and a private migration payload qualify.");
            return;
        }

        long ttlMinutes = Math.max(1L, migrationService.getPlanTtlMillis() / 60_000L);
        for (LegacyItemSchemaMigrationPlan plan : plans) {
            send(sender, "&8- &f" + plan.getProviderId() + " &8| &7" + plan.getMigrationName());
            send(sender, "&8  &7Authorized stacks: &e" + plan.getAuthorizedStackCount()
                    + " &8| &7claims: &e" + plan.getAuthorizedClaimCount()
                    + " &8| &7addon version: &e" + plan.getProviderVersion());
            send(sender, "&8  &7Fingerprint: &b" + plan.getShortFingerprint());
            send(sender, "&8  &7Execute: &6/sf doctor migrations schemas execute "
                    + plan.getProviderId() + " " + plan.getShortFingerprint());
        }
        send(sender, "&7Plans expire after &e" + ttlMinutes + " minute(s)&7 and are single-use.");
        send(sender, "&eExecuting one provider invalidates sibling schema plans; re-scan between addon migrations.");
        send(sender, "&eMake an offline backup before executing a schema migration plan.");
    }

    private void sendPlans(@Nonnull CommandSender sender) {
        List<LegacyItemSchemaMigrationPlan> plans = migrationService.getPreparedPlans();
        send(sender, "&6Slimefun Doctor Same-ID Schema Plans");
        if (plans.isEmpty()) {
            send(sender, "&7No active same-ID schema migration plans exist.");
            send(sender, "&7Create one with &e/sf doctor migrations schemas scan&7.");
            send(sender, "&7Persisted block IDs: &e/sf doctor migrations schemas blocks scan");
            send(sender, "&7Persisted item payloads: &e/sf doctor migrations schemas storage scan");
            return;
        }

        for (LegacyItemSchemaMigrationPlan plan : plans) {
            long secondsLeft = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
            send(sender, "&8- &f" + plan.getProviderId()
                    + " &8| &7stacks &e" + plan.getAuthorizedStackCount()
                    + " &8| &7claims &e" + plan.getAuthorizedClaimCount()
                    + " &8| &7expires &e" + secondsLeft + "s"
                    + " &8| &b" + plan.getShortFingerprint());
        }
        send(sender, "&8Opaque validation claims and migration payloads are never shown in operator output.");
        send(sender, "&7Persisted block IDs: &e/sf doctor migrations schemas blocks scan");
        send(sender, "&7Persisted item payloads: &e/sf doctor migrations schemas storage scan");
    }

    private void executePlan(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 6 || args[4].isBlank() || args[5].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations schemas execute <plugin> <fingerprint>");
            return;
        }

        String providerId = args[4];
        LegacyItemSchemaMigrationPlan plan = migrationService.getPreparedPlan(providerId).orElse(null);
        if (plan == null) {
            send(sender, "&cNo active same-ID schema plan exists for plugin '&f" + providerId + "&c', or it expired.");
            send(sender, "&7Run &e/sf doctor migrations schemas scan &7to create a fresh plan.");
            return;
        }

        if (!plan.matchesFingerprint(args[5])) {
            send(sender, "&cSchema migration fingerprint missing or incorrect.");
            send(sender, "&7Run a fresh &e/sf doctor migrations schemas scan &7and use its fingerprint.");
            return;
        }

        ItemDoctorService doctor = Slimefun.getItemDoctorService();
        if (!doctor.isEnabled()) {
            send(sender, "&cThe item doctor is disabled in config.yml.");
            return;
        }
        if (doctor.isServerRunActive()) {
            send(sender, "&eA server-wide Doctor run is already active. The schema plan was not consumed.");
            return;
        }

        migrationService.invalidateAllPreparedPlans();
        send(sender, "&eUsing a single-use schema migration plan. This fingerprint and sibling schema plans are now consumed.");
        send(sender, "&7Revalidating addon-owned backing state before any live item mutation...");
        migrationService.revalidatePlan(plan).whenComplete((verified, error) ->
                Slimefun.getSchedulerService().run(() -> finishExecutionRevalidation(sender, doctor, plan, verified, error)));
    }

    private void finishExecutionRevalidation(
            @Nonnull CommandSender sender,
            @Nonnull ItemDoctorService doctor,
            @Nonnull LegacyItemSchemaMigrationPlan plan,
            Boolean verified,
            Throwable error) {
        if (error != null) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Same-ID schema execution revalidation ended unexpectedly; no live mutation was started.",
                    error);
            send(sender, "&cBacking-state revalidation failed unexpectedly. No items were changed.");
            send(sender, "&7The plan remains consumed; run a fresh schema scan before trying again.");
            return;
        }
        if (!Boolean.TRUE.equals(verified)) {
            send(sender, "&cSchema migration blocked because validated backing state changed or no longer matches.");
            send(sender, "&7No live mutation was started. The consumed plan cannot be reused; run a fresh schema scan.");
            return;
        }
        if (doctor.isServerRunActive()) {
            send(sender, "&cAnother Doctor run started during backing-state revalidation. No schema mutation was started.");
            send(sender, "&7The consumed plan cannot be reused; run a fresh schema scan.");
            return;
        }

        Optional<LegacyItemSchemaMigrationExecutor> executorResult = migrationService.createExecutor(plan);
        if (executorResult.isEmpty()) {
            send(sender, "&cSchema migration blocked because the addon version or probe/migrator registrations changed.");
            send(sender, "&7No live mutation was started. Run a fresh schema scan.");
            return;
        }

        LegacyItemSchemaMigrationExecutor executor = executorResult.get();
        boolean started = doctor.startSchemaMigrationRun(executor, report -> sendExecutionReport(sender, executor, report));
        if (!started) {
            send(sender, "&cThe schema migration run could not start. No items were changed by this execution request.");
            send(sender, "&7The consumed plan will not be reused; run a fresh schema scan before trying again.");
            return;
        }

        send(sender, "&aStarted fingerprint-authorized same-ID schema migration for &f" + plan.getProviderId() + "&a.");
        send(sender, "&7Backing state was revalidated immediately before traversal.");
        send(sender, "&7Only exact live candidates from the approved plan may be mutated by that addon's migrator.");
        send(sender, "&7Automatic Doctor listeners do not participate in this execution pass.");
    }

    private void sendExecutionReport(
            @Nonnull CommandSender sender,
            @Nonnull LegacyItemSchemaMigrationExecutor executor,
            @Nonnull ItemDoctorReport report) {
        LegacyItemSchemaMigrationPlan plan = executor.getPlan();
        send(sender, "&6Slimefun Doctor Same-ID Schema Execution Report");
        send(sender, "&7Provider: &e" + plan.getProviderId() + " &8| &7" + plan.getMigrationName());
        send(sender, "&7Plan authorized stacks: &e" + plan.getAuthorizedStackCount()
                + " &8| &7live authorized attempts: &e" + executor.getAuthorizedCandidates());
        send(sender, "&7Migrated: &a" + executor.getMigrated()
                + " &8| &7skipped/unmatched: &e" + executor.getSkipped()
                + " &8| &7executor failures: &c" + executor.getFailures()
                + " &8| &7traversal failures: &c" + report.getFailures());
        if (executor.getAuthorizedCandidates() < plan.getAuthorizedStackCount()) {
            send(sender, "&eSome approved stacks were no longer reachable in the execution traversal and were left untouched.");
        }
        send(sender, "&7The plan is consumed regardless of outcome. Re-scan before any further schema migration.");
    }

    private void sendUsage(@Nonnull CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrations schemas <status|scan|execute|blocks|storage>");
        send(sender, "&7Scan is read-only. Execute requires a fresh plugin-specific fingerprint.");
        send(sender, "&7Persisted block IDs: &e/sf doctor migrations schemas blocks <status|scan|execute>");
        send(sender, "&7Persisted item payloads: &e/sf doctor migrations schemas storage <status|scan|execute>");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
