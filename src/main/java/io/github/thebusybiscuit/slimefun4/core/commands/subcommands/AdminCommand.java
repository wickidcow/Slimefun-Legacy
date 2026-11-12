package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.CraftingRestrictionService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/** Administrative Slimefun recipe policy without unregistering items or rewriting stored stacks. */
final class AdminCommand extends SubCommand {

    AdminCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "admin", false);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.admin") && !(sender instanceof ConsoleCommandSender)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        if (args.length != 4 || !args[1].equalsIgnoreCase("crafting")) {
            usage(sender);
            return;
        }

        String requestedId = args[2];
        SlimefunItem item = SlimefunItem.getById(requestedId);
        if (item == null) {
            item = Slimefun.getRegistry().getAllSlimefunItems().stream()
                    .filter(candidate -> candidate.getId().equalsIgnoreCase(requestedId))
                    .findFirst()
                    .orElse(null);
        }
        if (item == null) {
            send(sender, "&cUnknown Slimefun item ID: &f" + requestedId);
            send(sender, "&7Press Tab after /sf admin crafting to select a registered item.");
            return;
        }

        String id = item.getId();
        CraftingRestrictionService restrictions = Slimefun.getCraftingRestrictionService();
        String action = args[3].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            send(sender, "&e" + id + " crafting: "
                    + (restrictions.isBlocked(id) ? "&cDISABLED" : "&aENABLED"));
            if (!restrictions.isEnabled()) {
                send(sender, "&eThe global crafting restrictions switch is off in CraftingRestrictions.yml.");
            }
            return;
        }

        if (!action.equals("enable") && !action.equals("disable")) {
            usage(sender);
            return;
        }

        boolean enabled = action.equals("enable");
        if (!restrictions.setCraftingEnabled(item.getId(), enabled)) {
            send(sender, "&cUnable to save CraftingRestrictions.yml. Check the server console.");
            return;
        }

        send(sender, "&aSaved " + item.getId() + " crafting as " + (enabled ? "&aENABLED" : "&cDISABLED")
                + "&a. Existing items remain usable.");
        if (!restrictions.isEnabled()) {
            send(sender, "&eThe global switch is off; restrictions will not apply until it is enabled.");
        }
    }

    @Override
    public @Nonnull String getDescription(@Nonnull CommandSender sender) {
        return "Manages individual Slimefun crafting restrictions (admin only)";
    }

    private void usage(CommandSender sender) {
        send(sender, "&eUsage: /sf admin crafting <item-id> <enable|disable|status>");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
