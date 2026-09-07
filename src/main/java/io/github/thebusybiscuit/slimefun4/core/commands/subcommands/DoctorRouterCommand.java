package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.LegacyItemMigrationService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Routes Slimefun Doctor while adding generic legacy-id migration diagnostics and safe addon delegation. */
final class DoctorRouterCommand extends SubCommand {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PROVIDER_DETAIL_LINES = 20;

    private final DoctorCommand delegate;
    private final LegacyItemMigrationService migrationService;

    DoctorRouterCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        delegate = new DoctorCommand(plugin, cmd);
        migrationService = new LegacyItemMigrationService(plugin);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 1 && (args[1].equalsIgnoreCase("migrations") || args[1].equalsIgnoreCase("migration"))) {
            runMigrations(sender, args);
            return;
        }

        delegate.onExecute(sender, args);
    }

    private void runMigrations(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status" -> sendMigrationStatus(sender);
            case "list" -> sendMigrationList(sender, parsePage(args));
            case "unknown", "unknowns" -> sendUnknownIds(sender);
            case "plan", "dryrun", "dry-run" -> sendMigrationPlan(sender);
            case "providers", "provider" -> sendMigrationProviders(sender);
            case "scan" -> runMigrationProvider(sender, args, false);
            case "execute" -> runMigrationProvider(sender, args, true);
            default -> send(sender, "&eUsage: /sf doctor migrations <status|list|unknown|plan|providers|scan|execute>");
        }
    }

    private void sendMigrationStatus(@Nonnull CommandSender sender) {
        Map<String, String> mappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        long validTargets = mappings.values().stream().filter(id -> SlimefunItem.getById(id) != null).count();
        long liveAliases = mappings.keySet().stream().filter(id -> SlimefunItem.getById(id) != null).count();

        send(sender, "&6Slimefun Legacy ID Migrations");
        send(sender, "&7Declared mappings: &e" + mappings.size());
        send(sender, "&7Registered targets: &a" + validTargets + " &8| &7Missing targets: &c"
                + (mappings.size() - validTargets));
        send(sender, "&7Legacy IDs currently live-resolvable: &e" + liveAliases
                + " &8(&7temporary addon aliases may cause this&8)");
        send(sender, "&7Addon migration providers: &e" + migrationService.getProviders().size());

        ItemDoctorReport report = latestReport();
        if (report == null) {
            send(sender, "&7Item Doctor correlation: &fNo server-wide scan has run yet.");
            send(sender, "&7Run &e/sf doctor scan &7then &e/sf doctor migrations unknown&7.");
        } else {
            List<String> samples = report.getUnknownIdSamples();
            long recognized = samples.stream().filter(mappings::containsKey).count();
            send(sender, "&7Last/current Doctor unknown stacks: &e" + report.getUnknownIds()
                    + " &8| &7sampled IDs: &e" + samples.size()
                    + " &8| &7sampled legacy matches: &a" + recognized);
        }

        if (mappings.isEmpty()) {
            send(sender, "&eNo addon has published legacy item-ID mappings to Slimefun Legacy yet.");
        } else {
            send(sender, "&7Use &e/sf doctor migrations list &7to inspect the declared replacements.");
        }
        send(sender, "&8Core diagnostics never rewrite addon persistence directly.");
    }

    private void sendMigrationList(@Nonnull CommandSender sender, int requestedPage) {
        Map<String, String> mappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        if (mappings.isEmpty()) {
            send(sender, "&6Slimefun Legacy ID Migrations");
            send(sender, "&7No legacy ID mappings are currently registered.");
            return;
        }

        List<Map.Entry<String, String>> entries = new ArrayList<>(mappings.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));

        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage, pages));
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        send(sender, "&6Slimefun Legacy ID Migrations &7- page &e" + page + "&7/&e" + pages);
        for (int i = from; i < to; i++) {
            Map.Entry<String, String> entry = entries.get(i);
            boolean targetPresent = SlimefunItem.getById(entry.getValue()) != null;
            boolean sourceAliasPresent = SlimefunItem.getById(entry.getKey()) != null;
            send(sender, "&8- &f" + entry.getKey() + " &8-> "
                    + (targetPresent ? "&a" : "&c") + entry.getValue()
                    + (sourceAliasPresent ? " &8[&ealias live&8]" : ""));
        }

        if (pages > 1) {
            send(sender, "&7Page with &e/sf doctor migrations list <page>&7.");
        }
        send(sender, "&8Green targets are registered. Red targets are missing. No data was changed.");
    }

    private void sendUnknownIds(@Nonnull CommandSender sender) {
        ItemDoctorReport report = latestReport();
        send(sender, "&6Slimefun Doctor Unknown ID Correlation");
        if (report == null) {
            send(sender, "&7No server-wide Doctor scan has run yet. Use &e/sf doctor scan&7 first.");
            return;
        }

        Map<String, String> mappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        List<String> samples = report.getUnknownIdSamples();
        send(sender, "&7Unknown stacks observed: &e" + report.getUnknownIds() + " &8| &7sampled IDs: &e" + samples.size());
        if (samples.isEmpty()) {
            send(sender, "&aNo unknown Slimefun item IDs were sampled by the latest/current run.");
            return;
        }

        for (String id : samples) {
            String target = mappings.get(id);
            if (target == null) {
                send(sender, "&8- &c" + id + " &8-> &7no declared migration target");
                continue;
            }

            boolean targetPresent = SlimefunItem.getById(target) != null;
            send(sender, "&8- &e" + id + " &8-> " + (targetPresent ? "&a" : "&c") + target
                    + (targetPresent ? " &7(ready)" : " &7(target missing)"));
        }
        send(sender, "&8Correlation only. Core does not migrate addon persistence itself.");
    }

    private void sendMigrationPlan(@Nonnull CommandSender sender) {
        ItemDoctorReport report = latestReport();
        send(sender, "&6Slimefun Doctor Migration Dry-Run Plan");
        if (report == null) {
            send(sender, "&7No server-wide Doctor scan is available. Run &e/sf doctor scan&7 first.");
            send(sender, "&8No data was changed.");
            return;
        }

        Map<String, String> mappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        List<String> samples = report.getUnknownIdSamples();
        long ready = 0;
        long missingTargets = 0;
        long unmapped = 0;
        long liveAliases = 0;

        for (String id : samples) {
            String target = mappings.get(id);
            if (target == null) {
                unmapped++;
                continue;
            }

            if (SlimefunItem.getById(target) == null) {
                missingTargets++;
                continue;
            }

            ready++;
            if (SlimefunItem.getById(id) != null) {
                liveAliases++;
            }
        }

        send(sender, "&7Source scan: &e" + report.getModeName()
                + (report.isComplete() ? " &a(complete)" : " &e(running)"));
        send(sender, "&7Unknown stacks observed: &e" + report.getUnknownIds()
                + " &8| &7sampled distinct IDs: &e" + samples.size());
        send(sender, "&7Sample plan: ready &a" + ready + " &8| &7target missing &c" + missingTargets
                + " &8| &7no mapping &c" + unmapped);
        send(sender, "&7Ready sample IDs with temporary live aliases: &e" + liveAliases);

        if (samples.isEmpty()) {
            send(sender, "&aNo unknown Slimefun item IDs were sampled, so there is nothing to plan from this scan.");
        } else {
            for (String id : samples) {
                String target = mappings.get(id);
                if (target == null) {
                    send(sender, "&8- &c[NO MAPPING] &f" + id);
                    continue;
                }

                if (SlimefunItem.getById(target) == null) {
                    send(sender, "&8- &c[TARGET MISSING] &f" + id + " &8-> &c" + target);
                    continue;
                }

                send(sender, "&8- &a[READY] &f" + id + " &8-> &a" + target);
            }
        }

        send(sender, "&eThis plan is sample-based, not an exact count of migratable stacks.");
        send(sender, "&7Actual migration remains addon-owned through a registered migration provider.");
        send(sender, "&8Dry-run only. No items, blocks, storage, registry IDs, Cargo or Energy data were changed.");
    }

    private void sendMigrationProviders(@Nonnull CommandSender sender) {
        List<RegisteredServiceProvider<LegacyItemMigrationProvider>> providers = migrationService.getProviders();
        send(sender, "&6Slimefun Addon Migration Providers");
        if (providers.isEmpty()) {
            send(sender, "&7No enabled addon has registered a legacy migration provider.");
            return;
        }

        for (RegisteredServiceProvider<LegacyItemMigrationProvider> provider : providers) {
            Map<String, String> mappings = migrationService.getMappings(provider);
            List<String> problems = validateProviderMappings(mappings);
            send(sender, "&8- &f" + migrationService.getProviderId(provider) + " &8| &7"
                    + migrationService.getProviderName(provider) + " &8| &7mappings &e" + mappings.size()
                    + " &8| " + (problems.isEmpty() ? "&aREADY" : "&cBLOCKED"));
            if (!problems.isEmpty()) {
                send(sender, "&8  &7First problem: &c" + problems.getFirst());
            }
        }
        send(sender, "&7Read-only scan: &e/sf doctor migrations scan <plugin>");
        send(sender, "&7Repair requires explicit confirmation: &e/sf doctor migrations execute <plugin> confirm");
    }

    private void runMigrationProvider(@Nonnull CommandSender sender, @Nonnull String[] args, boolean repair) {
        if (args.length < 4 || args[3].isBlank()) {
            send(sender, "&eUsage: /sf doctor migrations " + (repair ? "execute" : "scan") + " <plugin>"
                    + (repair ? " confirm" : ""));
            sendMigrationProviders(sender);
            return;
        }

        String providerId = args[3];
        RegisteredServiceProvider<LegacyItemMigrationProvider> provider =
                migrationService.findProvider(providerId).orElse(null);
        if (provider == null) {
            send(sender, "&cNo enabled migration provider is registered by plugin '&f" + providerId + "&c'.");
            send(sender, "&7Use &e/sf doctor migrations providers &7to list available providers.");
            return;
        }

        Map<String, String> mappings = migrationService.getMappings(provider);
        List<String> problems = validateProviderMappings(mappings);
        if (repair && !problems.isEmpty()) {
            send(sender, "&cMigration blocked: provider mappings are not safe to execute.");
            for (int i = 0; i < Math.min(problems.size(), MAX_PROVIDER_DETAIL_LINES); i++) {
                send(sender, "&8- &c" + problems.get(i));
            }
            send(sender, "&7No provider repair method was called and no data was changed by Slimefun core.");
            return;
        }

        if (repair && (args.length < 5 || !args[4].equalsIgnoreCase("confirm"))) {
            send(sender, "&eThis delegates real migration to addon '&6" + migrationService.getProviderId(provider) + "&e'.");
            send(sender, "&eMake an offline backup first. Then run:");
            send(sender, "&6/sf doctor migrations execute " + migrationService.getProviderId(provider) + " confirm");
            return;
        }

        if (!repair && !problems.isEmpty()) {
            send(sender, "&eProvider mapping validation has " + problems.size() + " problem(s); scan is still read-only.");
        }

        AddonDoctorReport report = migrationService.run(provider, repair);
        sendMigrationProviderReport(sender, provider, report, problems);
    }

    private List<String> validateProviderMappings(@Nonnull Map<String, String> providerMappings) {
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

    private void sendMigrationProviderReport(
            @Nonnull CommandSender sender,
            @Nonnull RegisteredServiceProvider<LegacyItemMigrationProvider> provider,
            @Nonnull AddonDoctorReport report,
            @Nonnull List<String> mappingProblems) {
        send(sender, "&6Migration Provider " + (report.isRepairMode() ? "Repair" : "Scan") + " Report");
        send(sender, "&7Provider: &e" + migrationService.getProviderId(provider) + " &8| &7" + report.getAddonName());
        send(sender, "&7Scanned: &e" + report.getScannedEntries() + " &8| &7issues: &e" + report.getIssuesFound()
                + " &8| &7repaired: &a" + report.getRepairedEntries() + " &8| &7failures: &c" + report.getFailures());
        if (!mappingProblems.isEmpty()) {
            send(sender, "&eMapping validation warnings: " + mappingProblems.size());
        }
        List<String> details = report.getDetails();
        for (int i = 0; i < Math.min(details.size(), MAX_PROVIDER_DETAIL_LINES); i++) {
            send(sender, "&8- &7" + details.get(i));
        }
        if (details.size() > MAX_PROVIDER_DETAIL_LINES) {
            send(sender, "&8... " + (details.size() - MAX_PROVIDER_DETAIL_LINES) + " more provider detail line(s)");
        }
        send(sender, report.isRepairMode()
                ? "&8Repair was performed only by the selected addon provider; Slimefun core did not rewrite addon persistence."
                : "&8Read-only provider scan; no repair was requested.");
    }

    private ItemDoctorReport latestReport() {
        ItemDoctorReport current = Slimefun.getItemDoctorService().getCurrentReport();
        return current != null ? current : Slimefun.getItemDoctorService().getLastReport();
    }

    private int parsePage(String[] args) {
        if (args.length < 4) {
            return 1;
        }

        try {
            return Integer.parseInt(args[3]);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }
}
