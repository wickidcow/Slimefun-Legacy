package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Temporary direct routing surface for the persisted block-ID migration workflow. */
final class BlockMigrateCommand extends SubCommand {

    private final DoctorBlockIdMigrationCommand delegate = new DoctorBlockIdMigrationCommand();

    BlockMigrateCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "blockmigrate", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        String[] routed = new String[args.length + 2];
        routed[0] = "doctor";
        routed[1] = "migrations";
        routed[2] = "blocks";
        for (int i = 1; i < args.length; i++) {
            routed[i + 2] = args[i];
        }
        delegate.execute(sender, routed);
    }
}
