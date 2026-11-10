package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

        SlimefunItem mainItem = SlimefunItem.getByItem(main);
        SlimefunItem offItem = SlimefunItem.getByItem(off);
        String mainId = mainItem == null ? "(unregistered)" : mainItem.getId();
        String offId = offItem == null ? "(unregistered)" : offItem.getId();
        Comparison comparison = inspect(main, off);

        send(sender, "&6Slimefun Item Identity Doctor &8(read-only)");
        send(sender, "&7Main ID: &e" + mainId + " &8| &7Offhand ID: &e" + offId);
        send(sender, "&7Material: " + flag(comparison.materialSame()));
        send(sender, "&7Actual stack compatibility: " + flag(comparison.isSimilar()));
        send(sender, "&7Display name: " + flag(comparison.displayNameSame()));
        send(sender, "&7Adventure lore: " + flag(comparison.loreSame()));
        send(sender, "&7Full Bukkit item metadata: " + flag(comparison.itemMetaSame()));
        send(sender, "&7PDC key set: " + flag(comparison.pdcKeysSame()));
        send(sender, "&7Custom model data: " + flag(comparison.customModelDataSame()));
        send(sender, "&7Effective max stack size: &e" + main.getMaxStackSize()
                + " &8/ &e" + off.getMaxStackSize());
        if (!comparison.isSimilar()) {
            send(sender, "&eMismatch confirmed. No item was modified; inspect producer metadata before repair.");
        }
        send(sender, "&8This compares actual held items, not all possible crafting/machine sources.");
    }

    /**
     * Compare one-item copies without normalizing Adventure components or changing inputs.
     * Quantity differences alone must never produce a false identity mismatch.
     */
    static Comparison inspect(@Nonnull ItemStack main, @Nonnull ItemStack off) {
        ItemStack mainCopy = main.clone();
        ItemStack offCopy = off.clone();
        mainCopy.setAmount(1);
        offCopy.setAmount(1);
        ItemMeta mainMeta = mainCopy.getItemMeta();
        ItemMeta offMeta = offCopy.getItemMeta();
        return new Comparison(
                mainCopy.getType() == offCopy.getType(),
                mainCopy.isSimilar(offCopy),
                mainMeta == null ? offMeta == null
                        : offMeta != null && Objects.equals(mainMeta.displayName(), offMeta.displayName()),
                mainMeta == null ? offMeta == null
                        : offMeta != null && Objects.equals(mainMeta.lore(), offMeta.lore()),
                Objects.equals(mainMeta, offMeta),
                pdcKeys(mainMeta).equals(pdcKeys(offMeta)),
                Objects.equals(modelData(mainMeta), modelData(offMeta)));
    }

    record Comparison(
            boolean materialSame,
            boolean isSimilar,
            boolean displayNameSame,
            boolean loreSame,
            boolean itemMetaSame,
            boolean pdcKeysSame,
            boolean customModelDataSame) {}

    private static Set<NamespacedKey> pdcKeys(ItemMeta meta) {
        return meta == null ? Set.of() : Set.copyOf(meta.getPersistentDataContainer().getKeys());
    }

    @SuppressWarnings("deprecation")
    private static Object modelData(ItemMeta meta) {
        return meta == null || !meta.hasCustomModelData() ? null : meta.getCustomModelData();
    }

    private static String flag(boolean equal) {
        return equal ? "&aMATCH" : "&cDIFFERENT";
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
