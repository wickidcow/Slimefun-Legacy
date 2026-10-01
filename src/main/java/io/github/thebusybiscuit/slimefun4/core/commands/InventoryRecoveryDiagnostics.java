package io.github.thebusybiscuit.slimefun4.core.commands;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryRecoverySnapshot;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryRecoverySnapshot.Kind;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/** Read-only operator access to the live recovery holds; does not fetch or repair stored items. */
public final class InventoryRecoveryDiagnostics {
    private static final String PERMISSION = "slimefun.command.doctor";

    private InventoryRecoveryDiagnostics() {}

    public static void sendReport(CommandSender sender, String[] args) {
        execute(sender, args, InventoryRecoveryDiagnostics::capture);
    }

    public static void sendSummary(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION)) {
            return;
        }
        InventoryRecoverySnapshot snapshot = capture();
        if (snapshot == null) {
            send(sender, "&eInventory recovery diagnostics are unavailable: storage controllers are not ready.");
        } else if (snapshot.hasEntries()) {
            send(
                    sender,
                    "&eInventory recovery holds: &f" + snapshot.totalEntries()
                            + " &eobserved entries; inspect &6/sf doctor storage recovery&e.");
            send(sender, "&8Queue readiness is not proof that guarded inventories have recovered.");
        }
    }

    // Validate permission and arguments before collecting even read-only identity information.
    static void execute(CommandSender sender, String[] args, Supplier<InventoryRecoverySnapshot> source) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "&cYou do not have permission to use Slimefun Doctor.");
            return;
        }
        int page = 1;
        try {
            if (args.length < 3 || args.length > 4) {
                throw new IllegalArgumentException();
            }
            if (args.length == 4) {
                if (!args[3].matches("[1-9][0-9]?")) {
                    throw new IllegalArgumentException();
                }
                page = Integer.parseInt(args[3]);
            }
            if (page > InventoryRecoverySnapshot.MAX_ENTRIES / InventoryRecoverySnapshot.PAGE_SIZE) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) {
            send(sender, "&eUsage: /sf doctor storage recovery [page 1-10]");
            return;
        }
        InventoryRecoverySnapshot snapshot = source.get();
        if (snapshot == null) {
            send(sender, "&cInventory recovery diagnostics are unavailable: storage controllers are not ready.");
            return;
        }
        if (page > snapshot.pageCount()) {
            send(sender, "&eThat page is outside the current sample. Available pages: 1-" + snapshot.pageCount());
            return;
        }
        render(snapshot, page).forEach(line -> send(sender, line));
    }

    private static InventoryRecoverySnapshot capture() {
        var database = Slimefun.getDatabaseManager();
        if (database == null
                || database.getBlockDataController() == null
                || database.getProfileDataController() == null) {
            return null;
        }
        return InventoryRecoverySnapshot.combine(
                database.getBlockDataController().getInventoryRecoverySnapshot(),
                database.getProfileDataController().getInventoryRecoverySnapshot());
    }

    static List<String> render(InventoryRecoverySnapshot snapshot, int page) {
        List<String> lines = new ArrayList<>();
        lines.add("&6Slimefun Inventory Recovery &8(read-only)");
        lines.add("&7Load holds: block &e" + snapshot.count(Kind.BLOCK_LOAD) + " &8| &7universal &e"
                + snapshot.count(Kind.UNIVERSAL_LOAD) + " &8| &7backpack &e" + snapshot.count(Kind.BACKPACK_LOAD));
        lines.add("&7Pending universal migrations: &e" + snapshot.count(Kind.UNIVERSAL_MIGRATION));
        if (!snapshot.hasEntries()) {
            lines.add("&7No incomplete loads or pending migrations were observed in memory.");
            lines.add("&8This is not a persisted-item scan or an old-world compatibility certification.");
            return List.copyOf(lines);
        }
        lines.add("&7Sample page &e" + page + "&7/&e" + snapshot.pageCount() + " &8| &7showing up to "
                + InventoryRecoverySnapshot.MAX_ENTRIES + " entries; counts are not unique inventory totals.");
        for (var entry : snapshot.page(page)) {
            String detail = "&8- &e" + entry.kind() + " &f" + display(entry.owner());
            if (entry.destination() != null) {
                detail += " &7-> &f" + display(entry.destination());
            }
            lines.add(detail);
        }
        lines.add("&8In-flight loads can appear here. Each call samples current state; pages can change.");
        lines.add("&7Keep an offline backup. Resolve the exact addon/preset or decoding error in the console first.");
        lines.add("&7Review supported repairs with &e/sf doctor upgrade plan &7and &e/sf doctor addons&7.");
        lines.add("&7After repair, normal complete loading must succeed before its hold is released.");
        lines.add(
                "&ePending migration IDs may already be committed. Do not delete either identity or create a replacement.");
        lines.add("&8This command never retries migration, clears holds, changes IDs or writes item data.");
        lines.add(
                "&8/sf doctor repair confirm repairs presentation; it does not clear these holds or rebuild corrupt items.");
        return List.copyOf(lines);
    }

    // World names are operator-provided data, not color markup. Only the displayed copy is sanitized.
    static String display(String value) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < value.length() && text.length() < 160; index++) {
            char character = value.charAt(index);
            text.append(
                    Character.isISOControl(character)
                                    || character == '&'
                                    || character == '\u00a7'
                                    || Character.getType(character) == Character.FORMAT
                                    || Character.getType(character) == Character.LINE_SEPARATOR
                                    || Character.getType(character) == Character.PARAGRAPH_SEPARATOR
                            ? '?'
                            : character);
        }
        if (value.length() > 160) {
            text.append("...");
        }
        return text.toString();
    }

    private static void send(CommandSender sender, String text) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }
}
