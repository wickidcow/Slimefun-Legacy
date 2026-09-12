package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Adds the public-safe support report while preserving the existing Doctor router behavior. */
final class DoctorReportRouterCommand extends SubCommand {

    private final DoctorRouterCommand delegate;

    DoctorReportRouterCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        delegate = new DoctorRouterCommand(plugin, cmd);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 1 && (args[1].equalsIgnoreCase("report") || args[1].equalsIgnoreCase("support"))) {
            if (!sender.hasPermission("slimefun.command.doctor")) {
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
                return;
            }

            DoctorSupportReport.send(plugin, sender);
            return;
        }

        delegate.onExecute(sender, args);
    }
}
