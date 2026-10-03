package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ResourcePackDoctorService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Defaults to read-only preview; confirm authorizes persistent install/uninstall maintenance. */
final class DoctorResourcePackCommand {
    private DoctorResourcePackCommand() {}

    static void run(Slimefun plugin, CommandSender sender, String[] args) {
        String mode;
        String action;
        ResourcePackDoctorService service = Slimefun.getItemDoctorService().getResourcePackDoctor();
        String string = action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("status")) {
            DoctorResourcePackCommand.send(sender, "&6Slimefun Resource-Pack Doctor");
            DoctorResourcePackCommand.send(sender, "&7" + service.status());
            ExternalResourcePackService delivery = new ExternalResourcePackService(plugin);
            DoctorResourcePackCommand.send(
                    sender,
                    "&7Delivery owner: &e" + delivery.getOwnershipMode().configValue() + " &8| &7Legacy sender: &e"
                            + delivery.isDeliveryEnabled());
            if (service.backupPath() != null) {
                DoctorResourcePackCommand.send(sender, "&7Backup: &f" + String.valueOf(service.backupPath()));
            }
            DoctorResourcePackCommand.usage(sender);
            return;
        }
        if (action.equals("resume")) {
            DoctorResourcePackCommand.showBegin(
                    sender, service.resume(result -> DoctorResourcePackCommand.showResult(sender, result)));
            return;
        }
        if (!action.equals("install") && !action.equals("uninstall")) {
            DoctorResourcePackCommand.usage(sender);
            return;
        }
        String string2 = mode = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "scan";
        if (!mode.equals("scan") && !mode.equals("confirm")) {
            DoctorResourcePackCommand.usage(sender);
            return;
        }
        DoctorResourcePackCommand.send(sender, "&6Resource-pack " + action + " using existing Slimefun IDs");
        DoctorResourcePackCommand.send(
                sender, "&7Exact bundled numeric mappings are reset to 0; known stale first model floats are removed.");
        DoctorResourcePackCommand.send(
                sender, "&7Custom models, IDs, charge, backpack identities, names and lore are preserved.");
        if (action.equals("install")) {
            DoctorResourcePackCommand.send(
                    sender,
                    "&7Pack: &ehttps://github.com/wickidcow/SFL_RP_Official/releases/download/v4.1.0-id-preview.1/SlimefunLegacyRP.zip");
            DoctorResourcePackCommand.send(
                    sender, "&eThis is the ID-pack preview release. Test your client and combined ItemsAdder pack.");
            DoctorResourcePackCommand.send(
                    sender, "&7An external pack owner stays external; merge the ID pack through that manager.");
        }
        DoctorResourcePackCommand.coverage(sender);
        if (mode.equals("confirm")) {
            DoctorResourcePackCommand.showBegin(
                    sender, service.begin(action, result -> DoctorResourcePackCommand.showResult(sender, result)));
        } else if (!service.scan(result -> {
            DoctorResourcePackCommand.showResult(sender, result);
            DoctorResourcePackCommand.send(
                    sender, "&7Apply this operation: &6/sf doctor resource-pack " + action + " confirm");
        })) {
            DoctorResourcePackCommand.send(
                    sender, "&eDoctor is busy or stopping. Use resource-pack status and retry when idle.");
        } else {
            DoctorResourcePackCommand.send(
                    sender, "&aStarted a read-only preview; pack settings and items are unchanged.");
        }
    }

    private static void showBegin(CommandSender sender, ResourcePackDoctorService.BeginResult result) {
        DoctorResourcePackCommand.send(sender, (result.accepted() ? "&a" : "&c") + result.message());
        if (result.backup() != null) {
            DoctorResourcePackCommand.send(
                    sender, "&7Configuration and affected-item backup: &f" + String.valueOf(result.backup()));
        }
    }

    private static void showResult(CommandSender sender, ResourcePackDoctorService.SweepResult result) {
        DoctorResourcePackCommand.send(sender, "&7" + result.description());
        DoctorResourcePackCommand.send(
                sender,
                "&7Model cleanup candidates: loaded/backpacks &e"
                        + result.loaded().getItemModelCandidates() + " &8| &7unloaded Slimefun inventories &e"
                        + result.persisted().getItemModelCandidates());
        DoctorResourcePackCommand.send(
                sender,
                "&7Template/model conflicts preserved: &e"
                        + (result.loaded().getItemModelConflicts()
                                + result.persisted().getItemModelConflicts()));
        if (result.storageBusy()
                || result.deferredRows() != 0L
                || result.loaded().getFailures() != 0L
                || result.persisted().getFailures() != 0L
                || result.loaded().getItemModelConflicts() != 0L
                || result.persisted().getItemModelConflicts() != 0L) {
            DoctorResourcePackCommand.send(
                    sender, "&eSome items remain deferred or failed. Review the log, then run resource-pack resume.");
        }
        DoctorResourcePackCommand.coverage(sender);
    }

    private static void coverage(CommandSender sender) {
        DoctorResourcePackCommand.send(
                sender, "&7Covers online inventories/ender chests, loaded containers/drops, all database backpacks,");
        DoctorResourcePackCommand.send(
                sender,
                "&7and unloaded Slimefun block/universal inventory rows, including nested bundles/shulkers (depth 4).");
        DoctorResourcePackCommand.send(
                sender, "&eOffline players wait for join; unloaded vanilla containers wait for chunk load.");
        DoctorResourcePackCommand.send(
                sender,
                "&eAddon-private storage and unavailable item IDs need their owning addon; this pass is not a full-world wipe.");
    }

    private static void usage(CommandSender sender) {
        DoctorResourcePackCommand.send(
                sender, "&e/sf doctor resource-pack <status|install [scan|confirm]|uninstall [scan|confirm]|resume>");
    }

    private static void send(CommandSender sender, String message) {
        Runnable send = () -> sender.sendMessage(
                (Component) LegacyComponentSerializer.legacyAmpersand().deserialize("&6[Slimefun Doctor] " + message));
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Slimefun.getSchedulerService().runFor((Entity) player, send);
        } else {
            send.run();
        }
    }
}
