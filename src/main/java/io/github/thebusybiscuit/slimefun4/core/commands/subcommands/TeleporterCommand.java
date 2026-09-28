package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
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
                            .getPlayerUuidAsync(targetName, new IAsyncReadCallback<>() {
                                @Override
                                public void onResult(java.util.UUID targetUuid) {
                                    if (!player.isOnline()) {
                                        return;
                                    }

                                    Slimefun.getGPSNetwork()
                                            .getTeleportationManager()
                                            .openTeleporterGUI(
                                                    player,
                                                    targetUuid,
                                                    player.getLocation().getBlock().getRelative(BlockFace.DOWN),
                                                    999999999);
                                }

                                @Override
                                public void onResultNotFound() {
                                    if (player.isOnline()) {
                                        Slimefun.getLocalization()
                                                .sendMessage(
                                                        player,
                                                        "messages.unknown-player",
                                                        msg -> msg.replace("%player%", targetName));
                                    }
                                }
                            });
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
