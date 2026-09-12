package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/** Adds the guided item-upgrade workflow while preserving the existing Doctor router unchanged. */
final class DoctorItemUpgradeRouterCommand extends SubCommand {

    private final DoctorRouterCommand delegate;
    private final ItemUpgradeDoctorCommand itemUpgrade;

    DoctorItemUpgradeRouterCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "doctor", true);
        delegate = new DoctorRouterCommand(plugin, cmd);
        itemUpgrade = new ItemUpgradeDoctorCommand(plugin, cmd);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 1
                && (args[1].equalsIgnoreCase("item-upgrade")
                        || args[1].equalsIgnoreCase("item-upgrades")
                        || args[1].equalsIgnoreCase("upgrade-items")
                        || args[1].equalsIgnoreCase("itemupgrade"))) {
            itemUpgrade.onExecute(sender, args);
            return;
        }

        if (args.length > 1 && args[1].equalsIgnoreCase("scan")) {
            sender.sendMessage(ChatColors.color(
                    "&7Looking specifically for old addon item IDs (IE1, DynaTech, renamed/translated items)? "
                            + "Use &e/sf doctor item-upgrade scan &7for the guided upgrade-only scan."));
        }
        delegate.onExecute(sender, args);
    }
}
