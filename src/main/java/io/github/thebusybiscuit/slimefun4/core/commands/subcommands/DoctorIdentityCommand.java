package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Read-only item identity triage. This intentionally does not claim to exercise every
 * producer: machine, crafting, cheat, and persisted items must be supplied as actual
 * stacks to verify their real output metadata.
 */
final class DoctorIdentityCommand {

    private DoctorIdentityCommand() {}

    static void run(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cIdentity comparison requires a player with two held items.");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("compare")) {
            send(sender, "&eHold a reference item in your main hand and an actual output in your offhand.");
            send(sender, "&eUsage: /sf doctor identity compare");
            return;
        }

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (main.getType().isAir() || off.getType().isAir()) {
            send(sender, "&cBoth hands must contain an item. Nothing was changed.");
            return;
        }

        ItemStack mainCopy = main.clone();
        ItemStack offCopy = off.clone();
        mainCopy.setAmount(1);
        offCopy.setAmount(1);
        SlimefunItem mainItem = SlimefunItem.getByItem(main);
        SlimefunItem offItem = SlimefunItem.getByItem(off);
        String mainId = mainItem == null ? "(unregistered)" : mainItem.getId();
        String offId = offItem == null ? "(unregistered)" : offItem.getId();

        ItemMeta mainMeta = mainCopy.getItemMeta();
        ItemMeta offMeta = offCopy.getItemMeta();
        send(sender, "&6Slimefun Item Identity Doctor &8(read-only)");
        send(sender, "&7Main ID: &e" + mainId + " &8| &7Offhand ID: &e" + offId);
        send(sender, "&7Material: " + flag(mainCopy.getType() == offCopy.getType()));
        send(sender, "&7Actual stack compatibility: " + flag(mainCopy.isSimilar(offCopy)));
        send(sender, "&7Display name: " + flag(
                mainMeta == null ? offMeta == null
                        : offMeta != null && java.util.Objects.equals(mainMeta.displayName(), offMeta.displayName())));
        send(sender, "&7Adventure lore: " + flag(
                mainMeta == null ? offMeta == null
                        : offMeta != null && java.util.Objects.equals(mainMeta.lore(), offMeta.lore())));
        send(sender, "&7Full Bukkit item metadata: " + flag(java.util.Objects.equals(mainMeta, offMeta)));
        send(sender, "&7Effective max stack size: &e" + main.getMaxStackSize()
                + " &8/ &e" + off.getMaxStackSize());
        if (!mainCopy.isSimilar(offCopy)) {
            send(sender, "&eMismatch confirmed. No item was modified; inspect producer metadata before repair.");
        }
        send(sender, "&8This compares actual held items, not all possible crafting/machine sources.");
    }

    private static String flag(boolean equal) {
        return equal ? "&aMATCH" : "&cDIFFERENT";
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
