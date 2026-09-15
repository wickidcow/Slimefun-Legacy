package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationProvider;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockMigrationExecutionReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockMigrationPlan;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyBlockMigrationService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Operator gate for exact fingerprint-authorized legacy placed-machine migrations. */
final class DoctorBlockMigrationCommand {

    private final LegacyBlockMigrationService migrationService;

    DoctorBlockMigrationCommand(@Nonnull Slimefun plugin) {
        migrationService = new LegacyBlockMigrationService(plugin);
    }

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        String action = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status", "providers", "list" -> sendStatus(sender);
            case "scan", "plan", "dryrun", "dry-run" -> scan(sender, args);
            case "execute", "repair", "migrate" -> executePlan(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void sendStatus(@Nonnull CommandSender sender) {
        List<RegisteredServiceProvider<LegacyBlockMigrationProvider>> providers = migrationService.getProviders();
        send(sender, "&6Slimefun Doctor Legacy Machine Providers");
        if (providers.isEmpty()) {
            send(sender, "&7No enabled addon has registered an exact placed-machine migration provider.");
            send(sender, "&8Older broad legacy-item providers may still exist, but they are not treated as exact block authorization.");
            return;
        }

        for (RegisteredServiceProvider<LegacyBlockMigrationProvider> registration : providers) {
            String providerId = migrationService.getProviderId(registration);
            Map<String, String> mappings = migrationService.getMappings(registration);
            LegacyBlockMigrationPlan plan = migrationService.getPreparedPlan(providerId).orElse(null);
            send(sender, "&8- &f" + providerId
                    + " &8| &7" + migrationService.getProviderName(registration)
                    + " &8| &7mappings &e" + mappings.size()
                    + (plan == null ? "" : " &8| &bplan " + plan.getShortFingerprint()));
        }
        send(sender, "&7Plan exact loaded machines: &e/sf doctor migrations blocks scan <plugin>");
        send(sender, "&7Execute: &e/sf doctor migrations blocks execute <plugin> <fingerprint>");
    }

    private void scan(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 5 || args[4].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations blocks scan <plugin>");
            sendStatus(sender);
            return;
        }

        String providerId = args[4];
        RegisteredServiceProvider<LegacyBlockMigrationProvider> registration =
                migrationService.findProvider(providerId).orElse(null);
        if (registration == null) {
            migrationService.invalidatePreparedPlan(providerId);
            send(sender, "&cNo enabled exact machine migration provider is registered by plugin '&f" + providerId + "&c'.");
            return;
        }

        ItemDoctorService doctor = Slimefun.getItemDoctorService();
        if (doctor.isServerRunActive()) {
            send(sender, "&eA server-wide Doctor run is already active. Machine planning was not started.");
            return;
        }

        send(sender, "&6Slimefun Doctor Legacy Machine Plan");
        send(sender, "&7Provider: &e" + migrationService.getProviderId(registration)
                + " &8| &7" + migrationService.getProviderName(registration));
        send(sender, "&7Scanning the addon's currently loaded supported scope read-only...");

        LegacyBlockMigrationService.Preparation preparation = migrationService.preparePlan(registration);
        if (!preparation.problems().isEmpty()) {
            send(sender, "&cNo machine execution plan was created.");
            for (String problem : preparation.problems()) {
                send(sender, "&8- &c" + problem);
            }
            send(sender, "&7No placed blocks or machine data were changed.");
            return;
        }
        if (!preparation.isReady()) {
            send(sender, "&aNo executable legacy placed machines were found in the provider's loaded scope.");
            send(sender, "&8Unloaded chunks were not force-loaded and are not covered by this result.");
            return;
        }

        LegacyBlockMigrationPlan plan = preparation.plan();
        long ttlMinutes = Math.max(1L, migrationService.getPlanTtlMillis() / 60_000L);
        send(sender, "&7Exact authorized machines: &e" + plan.getCandidates().size()
                + " &8| &7mappings: &e" + plan.getMappings().size()
                + " &8| &7addon version: &e" + plan.getProviderVersion());
        send(sender, "&7Fingerprint: &b" + plan.getShortFingerprint());
        send(sender, "&7Execute: &6/sf doctor migrations blocks execute "
                + plan.getProviderId() + " " + plan.getShortFingerprint());
        send(sender, "&7Plan expires after &e" + ttlMinutes + " minute(s)&7 and is single-use.");
        send(sender, "&eAny machine state change before execution causes that exact candidate to be skipped.");
        send(sender, "&eMake an offline backup before executing placed-machine migrations.");
        send(sender, "&8Loaded-scope only: no chunk was force-loaded to create this authorization.");
    }

    private void executePlan(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 6 || args[4].isBlank() || args[5].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations blocks execute <plugin> <fingerprint>");
            return;
        }

        String providerId = args[4];
        RegisteredServiceProvider<LegacyBlockMigrationProvider> registration =
                migrationService.findProvider(providerId).orElse(null);
        if (registration == null) {
            migrationService.invalidatePreparedPlan(providerId);
            send(sender, "&cNo enabled exact machine migration provider is registered by plugin '&f" + providerId + "&c'.");
            return;
        }

        LegacyBlockMigrationPlan plan = migrationService.getPreparedPlan(providerId).orElse(null);
        if (plan == null) {
            send(sender, "&cNo active machine migration plan exists for '&f" + providerId + "&c', or it expired.");
            send(sender, "&7Create a fresh plan with &e/sf doctor migrations blocks scan " + providerId + "&7.");
            return;
        }
        if (!plan.matchesFingerprint(args[5])) {
            send(sender, "&cMachine migration fingerprint missing or incorrect.");
            send(sender, "&7No plan was consumed and no machine was changed.");
            return;
        }

        ItemDoctorService doctor = Slimefun.getItemDoctorService();
        if (doctor.isServerRunActive()) {
            send(sender, "&eA server-wide Doctor run is active. The machine plan was not consumed.");
            return;
        }

        send(sender, "&eConsuming the single-use machine plan and revalidating every exact candidate before mutation...");
        LegacyBlockMigrationExecutionReport report = migrationService.execute(registration, plan);
        send(sender, "&6Slimefun Doctor Legacy Machine Execution Report");
        send(sender, "&7Authorized: &e" + report.authorized()
                + " &8| &7revalidated: &a" + report.revalidated()
                + " &8| &7migrated: &a" + report.migrated());
        send(sender, "&7Changed/unloaded skipped: &e" + report.skippedChanged()
                + " &8| &7blocked: &c" + report.blocked()
                + " &8| &7failures: &c" + report.failures());
        for (String detail : report.details()) {
            send(sender, "&8- &7" + detail);
        }
        if (report.failures() > 0L) {
            send(sender, "&cOne or more addon migrations failed. Review console logs and verify rollback before continuing.");
        }
        if (report.skippedChanged() > 0L) {
            send(sender, "&eChanged or unloaded machines were left untouched; create a fresh plan before retrying them.");
        }
        send(sender, "&7The execution fingerprint is consumed regardless of outcome.");
        send(sender, "&8Run a fresh machine scan after loading additional old-server regions.");
    }

    private void sendUsage(@Nonnull CommandSender sender) {
        send(sender, "&eUsage: /sf doctor migrations blocks <status|scan|execute> [plugin] [fingerprint]");
        send(sender, "&7Aliases: machines, machine. Scan is read-only; execute requires the fresh exact fingerprint.");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
