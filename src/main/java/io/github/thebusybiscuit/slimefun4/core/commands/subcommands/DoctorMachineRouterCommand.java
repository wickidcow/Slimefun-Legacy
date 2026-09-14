package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

final class DoctorMachineRouterCommand extends SubCommand {
    private final DoctorRouterCommand delegate;
    private final DoctorBlockMigrationCommand machines;

    DoctorMachineRouterCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        delegate = new DoctorRouterCommand(plugin, cmd);
        machines = new DoctorBlockMigrationCommand(plugin);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 2
                && (args[1].equalsIgnoreCase("migrations") || args[1].equalsIgnoreCase("migration"))
                && (args[2].equalsIgnoreCase("blocks") || args[2].equalsIgnoreCase("block")
                        || args[2].equalsIgnoreCase("machines") || args[2].equalsIgnoreCase("machine"))) {
            machines.execute(sender, args);
        } else {
            delegate.onExecute(sender, args);
        }
    }
}
