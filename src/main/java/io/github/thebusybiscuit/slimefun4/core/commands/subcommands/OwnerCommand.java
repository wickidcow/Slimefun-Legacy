package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Records and reports the placer of Slimefun blocks.
 *
 * <p>This intentionally stores ownership as ordinary Slimefun block metadata. Addons do not need to opt in, and both
 * normal and universal block data use the same key.
 */
final class OwnerCommand extends SubCommand {

    private static final String OWNER_KEY = "owner";
    private static final int TARGET_DISTANCE = 8;

    OwnerCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "owner", false);
        plugin.getServer().getPluginManager().registerEvents(new OwnershipRecorder(), plugin);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.owner")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "&c/sf owner currently requires a player target. Look at the machine you want to inspect.");
            return;
        }

        Block target = player.getTargetBlockExact(TARGET_DISTANCE, FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            send(sender, "&eLook directly at a Slimefun block within " + TARGET_DISTANCE + " blocks.");
            return;
        }

        ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(target.getLocation());
        if (data == null) {
            send(sender, "&cThat block is not a registered Slimefun block.");
            return;
        }

        if (!data.isDataLoaded()) {
            send(sender, "&7Loading the machine's stored data...");
            StorageCacheUtils.executeAfterLoad(data, () -> executeLoaded(player, target, data, args), true);
            return;
        }

        executeLoaded(player, target, data, args);
    }

    private void executeLoaded(Player player, Block block, ASlimefunDataContainer data, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "query";

        switch (action) {
            case "query", "show", "status" -> showOwner(player, block, data);
            case "claim" -> claim(player, block, data);
            case "clear" -> clear(player, block, data);
            case "transfer" -> transfer(player, block, data, args);
            default -> sendUsage(player);
        }
    }

    private void showOwner(Player player, Block block, ASlimefunDataContainer data) {
        SlimefunItem item = SlimefunItem.getById(data.getSfId());
        String storedOwner = data.getData(OWNER_KEY);

        send(player, "&6&m----------------------------------------");
        send(player, "&6&lSlimefun Block Owner");
        send(player, "&7Item: &f" + (item == null ? data.getSfId() + " &c(unregistered)" : item.getId()));
        send(player, "&7Location: &f" + block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " "
                + block.getZ());

        if (storedOwner == null || storedOwner.isBlank()) {
            send(player, "&7Owner: &eUnowned / legacy placement");
            send(player, "&8Use /sf owner claim to assign this machine to yourself.");
        } else {
            UUID ownerId = parseUuid(storedOwner);
            if (ownerId == null) {
                send(player, "&7Owner: &cInvalid stored UUID: &f" + storedOwner);
                send(player, "&8Use /sf owner clear or /sf owner claim to repair this metadata.");
            } else {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
                String name = owner.getName();
                send(player, "&7Owner: &a" + (name == null ? ownerId : name));
                send(player, "&7UUID: &f" + ownerId);
            }
        }

        send(player, "&6&m----------------------------------------");
    }

    private void claim(Player player, Block block, ASlimefunDataContainer data) {
        String storedOwner = data.getData(OWNER_KEY);
        UUID existing = parseUuid(storedOwner);

        if (existing != null && !existing.equals(player.getUniqueId())) {
            send(player, "&cThis machine already belongs to " + displayOwner(existing)
                    + ". Use /sf owner transfer <player> to change it.");
            return;
        }

        data.setData(OWNER_KEY, player.getUniqueId().toString());
        send(player, "&aClaimed " + data.getSfId() + " at " + formatLocation(block) + " for " + player.getName() + ".");
    }

    private void clear(Player player, Block block, ASlimefunDataContainer data) {
        data.removeData(OWNER_KEY);
        send(player, "&eCleared the recorded owner for " + data.getSfId() + " at " + formatLocation(block) + ".");
    }

    private void transfer(Player player, Block block, ASlimefunDataContainer data, String[] args) {
        if (args.length < 3) {
            sendUsage(player);
            return;
        }

        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            send(player, "&cNo cached or online player matched '" + args[2] + "'. Use an online player name or UUID.");
            return;
        }

        data.setData(OWNER_KEY, target.getUniqueId().toString());
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        send(player, "&aTransferred " + data.getSfId() + " at " + formatLocation(block) + " to " + name + ".");
    }

    private OfflinePlayer resolvePlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }

        UUID uuid = parseUuid(input);
        if (uuid != null) {
            return Bukkit.getOfflinePlayer(uuid);
        }

        return Bukkit.getOfflinePlayerIfCached(input);
    }

    private String displayOwner(UUID uuid) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
        return owner.getName() == null ? uuid.toString() : owner.getName();
    }

    private UUID parseUuid(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String formatLocation(Block block) {
        return block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ();
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf owner [query|claim|clear|transfer <player>]");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColors.color(message));
    }

    /** Records ownership after core Slimefun placement handling has created the block data. */
    private static final class OwnershipRecorder implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockPlace(BlockPlaceEvent event) {
            ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(event.getBlock().getLocation());
            if (data == null) {
                return;
            }

            Runnable record = () -> {
                if (!data.isPendingRemove() && data.getData(OWNER_KEY) == null) {
                    data.setData(OWNER_KEY, event.getPlayer().getUniqueId().toString());
                }
            };

            if (data.isDataLoaded()) {
                record.run();
            } else {
                StorageCacheUtils.executeAfterLoad(data, record, true);
            }
        }
    }
}
