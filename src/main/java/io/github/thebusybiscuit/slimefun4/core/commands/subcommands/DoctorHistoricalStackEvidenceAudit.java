package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Read-only historical evidence replay: operator-owned exact-native receipts
 * are compared with one another and with the currently registered template.
 * No inventory, world, private receipt, shop record or migration plan is changed.
 */
final class DoctorHistoricalStackEvidenceAudit {

    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");

    private DoctorHistoricalStackEvidenceAudit() {}

    static void run(Slimefun plugin, CommandSender sender, String originalText, String templateText) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            send(sender, "&cEvidence replay requires a server operator in game.");
            return;
        }
        try {
            UUID originalReceipt = UUID.fromString(originalText);
            UUID templateReceipt = UUID.fromString(templateText);
            Assessment assessment = inspect(
                    plugin.getDataFolder().toPath(),
                    player.getUniqueId(),
                    originalReceipt,
                    templateReceipt,
                    DoctorHistoricalStackEvidenceAudit::registeredTemplate);
            send(player, "&6Historical Doctor Evidence Replay &8(read-only, private receipts)");
            send(player, "&7Saved historical item vs saved reference: &e" + assessment.savedDifference());
            send(player, "&7Saved reference still matches live registration: "
                    + flag(assessment.savedReferenceStillRegistered()));
            send(player, "&7Saved historical item already matches live registration: "
                    + flag(assessment.historicalMatchesCurrent()));
            send(player, "&7Strict style-only candidate against unchanged live registration: "
                    + flag(assessment.strictStyleOnlyCandidate()));
            send(player, "&cNo item or shop changed. This is NOT approval to repair a chest or QuickShop snapshot.");
        } catch (IOException | RuntimeException exception) {
            send(sender, "&cUnable to verify both private, operator-owned original receipts. No data changed.");
        }
    }

    /**
     * Safe to invoke again after a separate server boot, using the exact old and
     * reference receipt UUIDs printed by the earlier container evidence export.
     * Malformed/cross-owner receipts and different Slimefun IDs fail closed.
     *
     * <p>A stored source/reference style match is not automatically an approved
     * live repair when registration has changed since the receipt was written.
     */
    static Assessment inspect(
            Path pluginFolder, UUID owner, UUID originalReceipt, UUID templateReceipt,
            Function<ItemStack, ItemStack> currentTemplates) throws IOException {
        if (owner == null || originalReceipt == null || templateReceipt == null
                || originalReceipt.equals(templateReceipt) || currentTemplates == null) {
            throw new IOException("Evidence replay requires two distinct operator-owned receipts");
        }
        ItemStack original = DoctorHistoricalStackRepair.readVerifiedBackup(pluginFolder, owner, originalReceipt);
        ItemStack savedReference = DoctorHistoricalStackRepair.readVerifiedBackup(pluginFolder, owner, templateReceipt);
        if (!sameRegisteredIdentity(original, savedReference)) {
            throw new IOException("Evidence receipts do not reference the same registered item identity");
        }

        Difference savedDifference = classify(original, savedReference);
        if (savedDifference == Difference.UNSAFE) {
            throw new IOException("Saved historical source could not be classified safely");
        }

        ItemStack current;
        try {
            current = currentTemplates.apply(original);
        } catch (RuntimeException exception) {
            throw new IOException("Current registered template could not be inspected", exception);
        }
        // A missing addon/template must not be interpreted as an empty or newly
        // registered replacement item. Keep the original bytes in their receipts.
        if (!sameRegisteredIdentity(original, current)) {
            return new Assessment(savedDifference, false, false, false);
        }
        boolean savedReferenceStillRegistered =
                DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(savedReference, current);
        boolean historicalMatchesCurrent =
                DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(original, current);
        boolean strictStyleOnlyCandidate = savedReferenceStillRegistered
                && savedDifference == Difference.STYLE_ONLY_CANDIDATE
                && DoctorHistoricalStackRepair.prepare(original, current, current) != null;
        return new Assessment(
                savedDifference, savedReferenceStillRegistered, historicalMatchesCurrent, strictStyleOnlyCandidate);
    }

    private static Difference classify(ItemStack original, ItemStack reference) {
        if (DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(original, reference)) {
            return Difference.CURRENT;
        }
        var presentation = DoctorIdentityCommand.inspectHistoricalPresentation(original, reference);
        if (!presentation.nonPresentationSame()) {
            return Difference.FUNCTIONAL_METADATA;
        }
        var plain = DoctorIdentityCommand.inspectPlainPresentation(original, reference);
        if (plain.nameSame()
                && !plain.loreSame()
                && DoctorIdentityCommand.sameNonblankPlainLore(original, reference)) {
            return Difference.BLANK_LORE_LAYOUT;
        }
        if (!presentation.plainTextSame()) {
            return Difference.MEANINGFUL_TEXT;
        }
        return DoctorHistoricalStackRepair.prepare(original, reference, reference) != null
                ? Difference.STYLE_ONLY_CANDIDATE
                : Difference.UNSAFE;
    }

    private static ItemStack registeredTemplate(ItemStack old) {
        SlimefunItem registered = SlimefunItem.getByItem(old);
        return registered != null && Objects.equals(registered.getId(), itemId(old))
                ? registered.getItem()
                : null;
    }

    private static boolean sameRegisteredIdentity(ItemStack original, ItemStack reference) {
        return original != null && reference != null && !original.getType().isAir()
                && original.getType() == reference.getType()
                && itemId(original) != null
                && Objects.equals(itemId(original), itemId(reference));
    }

    private static String itemId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(SLIMEFUN_ID, PersistentDataType.STRING);
    }

    private static String flag(boolean value) {
        return value ? "&aYES" : "&cNO";
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    record Assessment(
            Difference savedDifference,
            boolean savedReferenceStillRegistered,
            boolean historicalMatchesCurrent,
            boolean strictStyleOnlyCandidate) {}

    enum Difference {
        CURRENT,
        STYLE_ONLY_CANDIDATE,
        BLANK_LORE_LAYOUT,
        MEANINGFUL_TEXT,
        FUNCTIONAL_METADATA,
        UNSAFE
    }
}
