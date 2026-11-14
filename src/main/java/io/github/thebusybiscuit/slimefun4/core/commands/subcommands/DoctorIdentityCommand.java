package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
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

    static void run(@Nonnull Slimefun plugin, @Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length > 2 && args[2].equalsIgnoreCase("repair")) {
            DoctorHistoricalStackRepair.run(plugin, sender, args);
            return;
        }
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
        send(sender, "&7Effective max stack size: &e" + main.getMaxStackSize() + " &8/ &e" + off.getMaxStackSize());
        int freeOffhand = availableCapacity(off);
        send(sender, "&7Offhand available capacity: &e" + freeOffhand);
        if (comparison.isSimilar() && freeOffhand == 0) {
            send(sender, "&eItems match, but offhand is full. A full stack cannot accept another item.");
        }
        reportTemplate(sender, "Main hand", main, mainItem);
        reportTemplate(sender, "Offhand", off, offItem);
        if (!comparison.isSimilar()) {
            HistoricalPresentation historical = inspectHistoricalPresentation(main, off);
            send(sender, "&7Same plain text (ignores styling): " + flag(historical.plainTextSame()));
            send(sender, "&7Non-presentation Bukkit metadata: " + flag(historical.nonPresentationSame()));
            send(sender, historical.candidate()
                    ? "&ePresentation-only candidate on detached copies; NOT an approved repair. No item changed."
                    : "&eNot a verified presentation-only candidate. No item changed.");
            send(sender, "&eMismatch confirmed. Inspect producer metadata before repair.");
        }
        send(sender, "&8This compares actual held items, not all possible crafting/machine sources.");
    }

    private static void reportTemplate(CommandSender sender, String label, ItemStack actual, SlimefunItem item) {
        if (item == null) {
            send(sender, "&7" + label + " vs registered template: &eUNAVAILABLE (unregistered item)");
            return;
        }
        Comparison comparison = inspect(actual, item.getItem());
        send(sender, "&6" + label + " vs registered template:");
        send(sender, "&7Stack compatibility: " + flag(comparison.isSimilar()));
        send(sender, "&7Display name: " + flag(comparison.displayNameSame()));
        send(sender, "&7Adventure lore: " + flag(comparison.loreSame()));
        send(sender, "&7Full Bukkit item metadata: " + flag(comparison.itemMetaSame()));
        send(sender, "&7PDC key set: " + flag(comparison.pdcKeysSame()));
        send(sender, "&7Custom model data: " + flag(comparison.customModelDataSame()));
    }

    /**
     * Read actual metadata without invoking producer-specific clone hooks or changing inputs.
     * Bukkit isSimilar already ignores quantity; cloning a registered SlimefunItemStack
     * would reapply item data and textures before the diagnostic could observe them.
     */
    static Comparison inspect(@Nonnull ItemStack main, @Nonnull ItemStack off) {
        ItemMeta mainMeta = main.getItemMeta();
        ItemMeta offMeta = off.getItemMeta();
        return new Comparison(
                main.getType() == off.getType(),
                main.isSimilar(off),
                mainMeta == null
                        ? offMeta == null
                        : offMeta != null && Objects.equals(mainMeta.displayName(), offMeta.displayName()),
                mainMeta == null ? offMeta == null : offMeta != null && Objects.equals(mainMeta.lore(), offMeta.lore()),
                Objects.equals(mainMeta, offMeta),
                pdcKeys(mainMeta).equals(pdcKeys(offMeta)),
                Objects.equals(modelData(mainMeta), modelData(offMeta)));
    }

    /**
     * Purely read-only historical triage. Removing text from ordinary detached ItemStack
     * copies tests whether the remaining Bukkit metadata differs, including typed PDC,
     * skull profile, model components and enchantments. It is NOT a repair eligibility
     * decision: plain text ignores styling, and native saved-world acceptance is still
     * required before any opt-in historical conversion can be permitted.
     */
    static HistoricalPresentation inspectHistoricalPresentation(ItemStack old, ItemStack fresh) {
        ItemMeta oldMeta = old.getItemMeta();
        ItemMeta freshMeta = fresh.getItemMeta();
        if (old.getType() != fresh.getType() || oldMeta == null || freshMeta == null) {
            return new HistoricalPresentation(false, false, false);
        }

        boolean plainTextSame = Objects.equals(plain(oldMeta.displayName()), plain(freshMeta.displayName()))
                && Objects.equals(plainLore(oldMeta.lore()), plainLore(freshMeta.lore()));
        // ItemStack(ItemStack) delegates to the source clone() on some Bukkit
        // implementations. Avoid producer-specific clone hooks and reject any
        // detached representation which cannot first round-trip strict identity.
        ItemStack oldCopy = detachedCopy(old);
        ItemStack freshCopy = detachedCopy(fresh);
        if (oldCopy == null || freshCopy == null) {
            return new HistoricalPresentation(plainTextSame, false, false);
        }
        oldCopy.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        freshCopy.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        boolean nonPresentationSame = oldCopy.isSimilar(freshCopy);
        return new HistoricalPresentation(
                plainTextSame, nonPresentationSame, !old.isSimilar(fresh) && plainTextSame && nonPresentationSame);
    }

    private static ItemStack detachedCopy(ItemStack source) {
        ItemStack detached = new ItemStack(source.getType(), source.getAmount());
        ItemMeta meta = source.getItemMeta();
        if (meta != null && !detached.setItemMeta(meta)) {
            return null;
        }
        // Never classify if Bukkit lost a typed PDC entry, profile, model
        // component, enchantment or other identity field during detachment.
        return source.isSimilar(detached) ? detached : null;
    }

    static int availableCapacity(ItemStack stack) {
        return Math.max(0, stack.getMaxStackSize() - stack.getAmount());
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static java.util.List<String> plainLore(java.util.List<Component> lore) {
        return lore == null ? null : lore.stream().map(DoctorIdentityCommand::plain).toList();
    }

    record HistoricalPresentation(boolean plainTextSame, boolean nonPresentationSame, boolean candidate) {}

    record Comparison(
            boolean materialSame,
            boolean isSimilar,
            boolean displayNameSame,
            boolean loreSame,
            boolean itemMetaSame,
            boolean pdcKeysSame,
            boolean customModelDataSame) {}

    private static Set<NamespacedKey> pdcKeys(ItemMeta meta) {
        return meta == null
                ? Set.of()
                : Set.copyOf(meta.getPersistentDataContainer().getKeys());
    }

    static Object modelData(ItemMeta meta) {
        if (meta == null || !meta.hasCustomModelDataComponent()) {
            return null;
        }
        var component = meta.getCustomModelDataComponent();
        return new ModelData(
                component.getFloats(), component.getFlags(), component.getStrings(), component.getColors());
    }

    private record ModelData(
            java.util.List<Float> floats,
            java.util.List<Boolean> flags,
            java.util.List<String> strings,
            java.util.List<org.bukkit.Color> colors) {}

    private static String flag(boolean equal) {
        return equal ? "&aMATCH" : "&cDIFFERENT";
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
