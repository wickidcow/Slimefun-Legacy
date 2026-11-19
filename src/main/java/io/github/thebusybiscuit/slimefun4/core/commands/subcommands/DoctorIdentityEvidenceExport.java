package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Operator-only evidence capture for a genuine old/new held item pair.
 *
 * <p>Writes exact original native bytes to the existing symlink-safe private
 * Doctor backup directory. Does not update either hand, any block inventory,
 * a QuickShop template or any player/world record. Never uploads evidence.
 */
final class DoctorIdentityEvidenceExport {

    private DoctorIdentityEvidenceExport() {}

    static void run(Slimefun plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            send(sender, "&cHistorical item evidence export requires an operator in game.");
            return;
        }
        if (args.length != 3) {
            send(sender, "&eUsage: /sf doctor identity export");
            return;
        }

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        try {
            if (main == null || offhand == null || main.getType().isAir() || offhand.getType().isAir()) {
                send(sender, "&cTwo actual held items are required. Nothing was changed.");
                return;
            }
            SlimefunItem mainItem = SlimefunItem.getByItem(main);
            SlimefunItem offItem = SlimefunItem.getByItem(offhand);
            if (mainItem == null || offItem == null || !Objects.equals(mainItem.getId(), offItem.getId())) {
                send(sender, "&cBoth items must resolve to the same registered Slimefun ID. No evidence written.");
                return;
            }
            Evidence record = capture(plugin.getDataFolder().toPath(), player.getUniqueId(), main, offhand);
            send(sender, "&aExact source-item evidence saved locally without changing either item.");
            send(sender, "&7Main-hand receipt: &e" + record.mainReceipt());
            send(sender, "&7Offhand receipt: &e" + record.offhandReceipt());
            send(sender, "&7Folder: &eplugins/Slimefun/recovery/identity/");
            send(sender, "&cPRIVATE: these .bin files contain raw item metadata and possibly owner IDs.");
            send(sender, "&cDo not post them publicly or commit them to GitHub. Test on a full backup clone.");
        } catch (IOException | RuntimeException exception) {
            send(sender, "&cUnable to verify a private exact-byte evidence capture. Held items unchanged.");
        }
    }

    /**
     * Writes and independently rereads both source stacks before reporting
     * receipts. An interrupted second write can leave the verified first
     * record intact; no world, shop or held item is ever updated.
     */
    static Evidence capture(Path pluginFolder, UUID owner, ItemStack main, ItemStack offhand) throws IOException {
        if (main == null || offhand == null || main.getType().isAir() || offhand.getType().isAir()) {
            throw new IOException("Historical source pair must contain two actual items");
        }
        byte[] mainBytes;
        byte[] offBytes;
        try {
            mainBytes = main.serializeAsBytes();
            offBytes = offhand.serializeAsBytes();
        } catch (RuntimeException exception) {
            throw new IOException("Native item evidence serialization failed", exception);
        }

        Path mainPath = DoctorHistoricalStackRepair.backup(pluginFolder, owner, mainBytes);
        UUID mainReceipt = receipt(owner, mainPath);
        verify(pluginFolder, owner, mainReceipt, mainBytes);

        Path offPath = DoctorHistoricalStackRepair.backup(pluginFolder, owner, offBytes);
        UUID offReceipt = receipt(owner, offPath);
        verify(pluginFolder, owner, offReceipt, offBytes);

        if (mainBytes.length == 0 || offBytes.length == 0 || !Files.isRegularFile(mainPath)
                || !Files.isRegularFile(offPath)) {
            throw new IOException("Evidence file is missing or empty");
        }
        return new Evidence(mainReceipt, offReceipt);
    }

    private static UUID receipt(UUID owner, Path path) throws IOException {
        String filename = path.getFileName().toString();
        String prefix = owner + "-";
        if (!filename.startsWith(prefix) || !filename.endsWith(".bin")) {
            throw new IOException("Unexpected backup receipt format");
        }
        try {
            return UUID.fromString(filename.substring(prefix.length(), filename.length() - 4));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid evidence receipt", exception);
        }
    }

    private static void verify(Path root, UUID owner, UUID receipt, byte[] exact) throws IOException {
        try {
            ItemStack reloaded = DoctorHistoricalStackRepair.readVerifiedBackup(root, owner, receipt);
            if (!Arrays.equals(exact, reloaded.serializeAsBytes())) {
                throw new IOException("Evidence readback did not preserve exact bytes");
            }
        } catch (RuntimeException exception) {
            throw new IOException("Evidence readback failed", exception);
        }
    }

    private static void send(CommandSender sender, String text) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }

    record Evidence(UUID mainReceipt, UUID offhandReceipt) {}
}
