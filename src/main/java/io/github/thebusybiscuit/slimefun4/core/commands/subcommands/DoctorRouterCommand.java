package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Routes Slimefun Doctor while adding generic read-only legacy-id migration diagnostics. */
final class DoctorRouterCommand extends SubCommand {

    private static final int PAGE_SIZE = 20;

    private final DoctorCommand delegate;

    DoctorRouterCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        delegate = new DoctorCommand(plugin, cmd);
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
            default -> send(sender, "&eUsage: /sf doctor migrations <status|list|unknown> [page]");
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

        ItemDoctorReport report = latestReport();
        if (report == null) {
            send(sender, "&7Item Doctor correlation: &fNo server-wide scan has run yet.");
            send(sender, "&7Run &e/sf doctor scan &7then &e/sf doctor migrations unknown&7.");
        } else {
            List<String> samples = report.getUnknownIdSamples();
            long recognized = samples.stream().filter(mappings::containsKey).count();
            send(sender, "&7Last/current Doctor unknown IDs: &e" + report.getUnknownIds()
                    + " &8| &7sampled: &e" + samples.size()
                    + " &8| &7sampled legacy matches: &a" + recognized);
        }

        if (mappings.isEmpty()) {
            send(sender, "&eNo addon has published legacy item-ID mappings to Slimefun Legacy yet.");
        } else {
            send(sender, "&7Use &e/sf doctor migrations list &7to inspect the declared replacements.");
        }
        send(sender, "&8Read-only diagnostics. This command never rewrites items, blocks or registry IDs.");
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
        send(sender, "&8Correlation only. Generic migration/repair remains disabled at this stage.");
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
        sender.sendMessage(ChatColors.color(message));
    }
}
