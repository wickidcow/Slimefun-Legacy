package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockIdMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockIdMigrationService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockIdMigrationService.ExecutionOutcome;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockIdMigrationService.ScanResult;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Explicit fingerprint gate for persisted placed-block and universal Slimefun ID rewrites. */
final class DoctorBlockIdMigrationCommand {

    private static final int MAX_CANDIDATE_LINES = 20;

    private final LegacyBlockIdMigrationService migrationService = new LegacyBlockIdMigrationService();

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status", "plan", "plans" -> sendStatus(sender);
            case "scan" -> createPlan(sender);
            case "execute", "repair" -> executePlan(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void sendStatus(@Nonnull CommandSender sender) {
        ScanResult scan = migrationService.scan();
        send(sender, "&6Slimefun Persisted Block ID Migration");
        sendScanSummary(sender, scan);

        LegacyBlockIdMigrationPlan plan = migrationService.getPreparedPlan().orElse(null);
        if (plan == null) {
            send(sender, "&7Active fingerprint: &fNone");
            if (scan.ready() > 0L) {
                send(sender, "&7Create one with &e/sf doctor migrations blocks scan&7.");
            }
        } else {
            long secondsLeft = Math.max(0L, (plan.getExpiresAtMillis() - System.currentTimeMillis()) / 1000L);
            send(sender, "&7Active fingerprint: &b" + plan.getShortFingerprint()
                    + " &8| &7records &e" + plan.getCandidateCount()
                    + " &8| &7expires &e" + secondsLeft + "s");
        }
        send(sender, "&8Status is read-only and does not load worlds or chunks.");
    }

    private void createPlan(@Nonnull CommandSender sender) {
        migrationService.invalidatePreparedPlan();
        ScanResult discovery = migrationService.scan();
        send(sender, "&6Slimefun Persisted Block ID Migration Scan");
        sendScanSummary(sender, discovery);

        if (discovery.ready() == 0L) {
            send(sender, "&aNo safe persisted block-ID rewrites are currently required.");
            send(sender, "&7Live aliases and unknown/missing-target IDs remain untouched.");
            return;
        }

        LegacyBlockIdMigrationPlan plan = migrationService.preparePlan().orElse(null);
        if (plan == null) {
            send(sender, "&eCandidates changed while the plan was being prepared; no execution fingerprint was retained.");
            send(sender, "&7Run the block scan again.");
            return;
        }

        send(sender, "&7Fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Authorized records: &e" + plan.getCandidateCount());
        int shown = 0;
        for (LegacyBlockIdMigrationPlan.Candidate candidate : plan.getCandidates()) {
            if (shown++ >= MAX_CANDIDATE_LINES) {
                break;
            }
            send(sender, "&8- &f" + candidate.storageScope() + " &8| &7" + candidate.recordKey()
                    + " &8| &e" + candidate.legacyId() + " &8-> &a" + candidate.canonicalId());
        }
        if (plan.getCandidateCount() > MAX_CANDIDATE_LINES) {
            send(sender, "&8... " + (plan.getCandidateCount() - MAX_CANDIDATE_LINES) + " more record(s)");
        }

        long ttlMinutes = Math.max(1L, migrationService.getPlanTtlMillis() / 60_000L);
        send(sender, "&7Execute: &6/sf doctor migrations blocks execute " + plan.getShortFingerprint());
        send(sender, "&7The plan expires after &e" + ttlMinutes + " minute(s)&7 and is single-use.");
        send(sender, "&eMake an offline backup before executing any persisted-data rewrite.");
    }

    private void executePlan(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 5 || args[4].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations blocks execute <fingerprint>");
            return;
        }

        LegacyBlockIdMigrationPlan plan = migrationService.getPreparedPlan().orElse(null);
        if (plan == null) {
            send(sender, "&cNo active persisted block-ID plan exists, or it expired.");
            send(sender, "&7Run &e/sf doctor migrations blocks scan &7to create a fresh plan.");
            return;
        }
        if (!plan.matchesFingerprint(args[4])) {
            send(sender, "&cPersisted block-ID migration fingerprint missing or incorrect.");
            return;
        }

        send(sender, "&eConsuming the single-use plan and re-scanning exact storage identities...");
        ExecutionOutcome outcome = migrationService.execute(plan, args[4]);
        if (!outcome.attempted()) {
            send(sender, "&cNo persisted IDs were changed: &7" + outcome.detail());
            send(sender, "&7The plan is consumed. Run a fresh block scan before trying again.");
            return;
        }

        var result = outcome.result();
        if (result == null) {
            send(sender, "&cExecution ended without a storage result. Re-scan before retrying.");
            return;
        }
        if (!result.writesIdle() || !result.readsIdle()) {
            send(sender, "&eStorage was busy, so no block-ID rewrite was started.");
            send(sender, "&7Wait for pending storage work to drain, then create a fresh plan.");
            return;
        }

        send(sender, "&6Slimefun Persisted Block ID Migration Report");
        send(sender, "&7Authorized records: &e" + plan.getCandidateCount()
                + " &8| &7rewritten: &a" + result.rewritten()
                + " &8| &7skipped/drifted: &e" + result.skipped()
                + " &8| &7failures: &c" + result.failures());
        if (result.failures() > 0 || result.skipped() > 0) {
            send(sender, "&eSome records were left untouched. Run a new scan before any further migration.");
        } else {
            send(sender, "&aAll fingerprint-authorized persisted block identities were rewritten successfully.");
        }
        send(sender, "&7No worlds or chunks were force-loaded by this migration.");
    }

    private void sendScanSummary(CommandSender sender, ScanResult scan) {
        send(sender, "&7Persisted identities scanned: &e" + scan.persistedIdentities());
        send(sender, "&7Safe rewrites: &a" + scan.ready()
                + " &8| &7live aliases (read-only): &e" + scan.liveAliases()
                + " &8| &7missing targets: &c" + scan.missingTargets()
                + " &8| &7unknown/unmapped: &c" + scan.unknownIds());
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrations blocks <status|scan|execute>");
        send(sender, "&7Scan is read-only. Execute requires a fresh fingerprint and quiescent storage.");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
