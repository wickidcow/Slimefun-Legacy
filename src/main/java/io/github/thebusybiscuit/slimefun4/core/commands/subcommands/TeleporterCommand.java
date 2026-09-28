package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ThreadUtils;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class TeleporterCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    TeleporterCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "teleporter", false);
    }

    @Override
    public void onExecute(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            if (sender.hasPermission("slimefun.command.teleporter")) {
                if (args.length == 1) {
                    Slimefun.getGPSNetwork()
                            .getTeleportationManager()
                            .openTeleporterGUI(
                                    player,
                                    player.getUniqueId(),
                                    player.getLocation().getBlock().getRelative(BlockFace.DOWN),
                                    999999999);
                } else if (args.length == 2) {
                    String targetName = args[1];
                    Slimefun.getDatabaseManager()
                            .getProfileDataController()
                            .getPlayerUuidAsync(targetName)
                            .thenAcceptAsync(
                                    targetUuid -> {
                                        if (!player.isOnline()) {
                                            return;
                                        }

                                        if (targetUuid == null) {
                                            Slimefun.getLocalization()
                                                    .sendMessage(
                                                            player,
                                                            "messages.unknown-player",
                                                            msg -> msg.replace("%player%", targetName));
                                            return;
                                        }

                                        Slimefun.getGPSNetwork()
                                                .getTeleportationManager()
                                                .openTeleporterGUI(
                                                        player,
                                                        targetUuid,
                                                        player.getLocation().getBlock().getRelative(BlockFace.DOWN),
                                                        999999999);
                                    },
                                    ThreadUtils.getEntityDelayedExecutor(player));
                } else {
                    Slimefun.getLocalization()
                            .sendMessage(
                                    sender, "messages.usage", msg -> msg.replace("%usage%", "/sf teleporter [Player]"));
                }
            } else {
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission");
            }
        } else {
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players");
        }
    }
}
