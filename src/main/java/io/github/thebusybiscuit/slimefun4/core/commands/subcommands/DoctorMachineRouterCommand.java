package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Adds the exact-machine migration lane without replacing the existing Doctor router. */
final class DoctorMachineRouterCommand extends SubCommand {
    private final DoctorRouterCommand delegate;
    private final DoctorBlockMigrationCommand machines;

    DoctorMachineRouterCommand(
            @Nonnull Slimefun plugin,
            @Nonnull SlimefunCommand cmd,
            @Nonnull DoctorRouterCommand delegate) {
        super(plugin, cmd, "doctor", true);
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        machines = new DoctorBlockMigrationCommand(plugin);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 2
                && (args[1].equalsIgnoreCase("migrations") || args[1].equalsIgnoreCase("migration"))
                && (args[2].equalsIgnoreCase("blocks") || args[2].equalsIgnoreCase("block")
                        || args[2].equalsIgnoreCase("machines") || args[2].equalsIgnoreCase("machine"))) {
            if (!sender.hasPermission("slimefun.command.doctor")) {
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
                return;
            }
            machines.execute(sender, args);
            return;
        }
        delegate.onExecute(sender, args);
    }
}
