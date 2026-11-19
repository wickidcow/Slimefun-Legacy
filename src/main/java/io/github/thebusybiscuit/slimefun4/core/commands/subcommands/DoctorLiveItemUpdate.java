package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manual, operator-only live inventory compatibility batch. Uses the same
 * strict presentation-only candidate classifier as the single-stack Doctor.
 * Historical items with changed meaningful text or typed state are refused.
 *
 * <p>Does not run on inventory-open, touch QuickShop/shop snapshots, scan
 * unloaded chunks, or automatically mutate a chest or machine inventory.
 */
final class DoctorLiveItemUpdate {

    private static final int MAX_SLOTS = 36;
    private static final int MAX_CONTAINER_SCAN_SLOTS = 54;
    private static final int MAX_UPDATES = 8;
    // Refuse oversized addon/storage payloads on a live owner thread.
    private static final int MAX_STACK_BYTES = 256 * 1024;
    private static final int MAX_BATCH_BYTES = 512 * 1024;
    private static final int MAX_PLANS = 64;
    private static final long PLAN_TTL_MILLIS = 60_000L;
    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");
    private static final Map<UUID, SavedPlan> PLANS = new ConcurrentHashMap<>();

    private DoctorLiveItemUpdate() {}

    static void run(Slimefun plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            send(sender, "&cLive item updates require a server operator in game.");
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("export-container")) {
            PLANS.remove(player.getUniqueId());
            exportContainer(plugin, player, args[4]);
            return;
        }
        if (args.length != 4) {
            usage(sender);
            return;
        }
        String action = args[3].toLowerCase(java.util.Locale.ROOT);
        UUID owner = player.getUniqueId();
        long now = System.currentTimeMillis();
        PLANS.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        if (action.equals("cancel")) {
            PLANS.remove(owner);
            send(player, "&aPending live item update preview canceled; no items changed.");
            return;
        }
        if (action.equals("scan-container")) {
            PLANS.remove(owner);
            inspectContainer(player);
            return;
        }
        if (action.equals("scan")) {
            PLANS.remove(owner);
            if (PLANS.size() >= MAX_PLANS) {
                send(player, "&cToo many pending update previews. Retry after they expire.");
                return;
            }
            try {
                Preview preview = preview(player.getInventory(), DoctorLiveItemUpdate::registeredTemplate);
                send(player, "&6Live Slimefun Item Compatibility &8(OP-only, player inventory)");
                send(player, "&7Inspected slots: &e" + preview.scannedSlots()
                        + " &8| &7Mismatched registered stacks: &e" + preview.mismatched());
                send(player, "&7Safe style-only candidates: &a" + preview.eligible()
                        + " &8| &7Refused/unknown: &e" + preview.refused());
                send(player, "&7Batch size: &e" + preview.entries().size()
                        + " &8| &7Additional safe candidates: &e"
                        + Math.max(0, preview.eligible() - preview.entries().size()));
                if (preview.entries().isEmpty()) {
                    send(player, "&eNo proven style-only updates available; nothing changed.");
                    send(player, "&8Old meaningful names/lore and unverified typed data are not rewritten.");
                    return;
                }
                PLANS.put(owner, new SavedPlan(preview, now + PLAN_TTL_MILLIS));
                send(player, "&cBack up the whole server first. Plan expires in 60 seconds.");
                send(player, "&eOnly your loaded storage slots can change, not chests or QuickShop.");
                send(player, "&6/sf doctor identity live confirm &7to write verified original backups then update.");
            } catch (RuntimeException exception) {
                send(player, "&cUnable to verify source items. No live update preview was saved.");
            }
            return;
        }
        if (action.equals("confirm")) {
            SavedPlan saved = PLANS.remove(owner);
            if (saved == null || saved.expiresAtMillis() < now) {
                send(player, "&cNo current matching live preview. Run /sf doctor identity live scan.");
                return;
            }
            try {
                List<Receipt> receipts = apply(
                        player.getInventory(), saved.preview(), DoctorLiveItemUpdate::registeredTemplate,
                        plugin.getDataFolder().toPath(), owner);
                send(player, "&aUpdated &e" + receipts.size()
                        + " &aeligible live inventory stack(s). Original bytes verified on disk.");
                for (Receipt receipt : receipts) {
                    send(player, "&7Slot &e" + receipt.slot() + " &8| &7Private backup receipt: &e" + receipt.id());
                }
                send(player, "&cKeep these backups. Chests, machines and QuickShop templates were not changed.");
            } catch (IOException | RuntimeException exception) {
                send(player, "&cLive item update was refused or interrupted. Check inventory and private backups.");
                plugin.getLogger().warning("Operator live-item batch refused; verify original item receipts: "
                        + exception.getClass().getSimpleName());
            }
            return;
        }
        usage(player);
    }

    private static void usage(CommandSender sender) {
        send(sender, "&e/sf doctor identity live scan");
        send(sender, "&e/sf doctor identity live scan-container &8(read-only, look at chest/barrel)");
        send(sender, "&e/sf doctor identity live export-container <slot> &8(private evidence, 0-based slot)");
        send(sender, "&e/sf doctor identity live confirm");
        send(sender, "&e/sf doctor identity live cancel");
        send(sender, "&cManual and opt-in. No automatic chest, shop, player-join or machine rewrites.");
    }

    /**
     * Read-only physical container inspection dispatched to its owning location.
     * Never stores a repair plan or accesses a container from a player's
     * different Folia region. Report results from the player region.
     */
    private static void inspectContainer(Player player) {
        Block aimed = player.getTargetBlockExact(6);
        if (aimed == null || aimed.getType().isAir()) {
            send(player, "&eLook directly at a loaded chest/barrel within six blocks.");
            return;
        }
        Location location = aimed.getLocation();
        Slimefun.getSchedulerService().runAt(location, () -> {
            String message;
            try {
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    message = "&cTarget chunk unloaded. No items were inspected or changed.";
                } else {
                    BlockState state = location.getBlock().getState();
                    if (!(state instanceof Container container)) {
                        message = "&eTarget is not a physical chest/barrel/container.";
                    } else {
                        Preview inspection = preview(
                                container.getInventory(),
                                DoctorLiveItemUpdate::registeredTemplate,
                                MAX_CONTAINER_SCAN_SLOTS);
                        message = "&6Container item compatibility &8(read-only) &7Slots: &e"
                                + inspection.scannedSlots() + " &8| &7Mismatches: &e"
                                + inspection.mismatched() + " &8| &7Style-only: &a"
                                + inspection.eligible() + " &8| &7Refused/unknown: &e"
                                + inspection.refused()
                                + " &8| &7NO chest/shop data changed.";
                    }
                }
            } catch (RuntimeException exception) {
                message = "&cContainer metadata could not be inspected safely. Nothing changed.";
            }
            final String report = message;
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (player.isOnline()) {
                    send(player, report);
                }
            });
        });
    }

    /**
     * Read-only owner-operator evidence export for one physical container slot.
     * A separate region-owned callback avoids touching containers from the
     * player's region in Folia. Only bounded private original/template bytes
     * are written; inventories and shop snapshots are never modified.
     */
    private static void exportContainer(Slimefun plugin, Player player, String slotArgument) {
        int slot;
        try {
            slot = Integer.parseInt(slotArgument);
            if (slot < 0 || slot >= MAX_CONTAINER_SCAN_SLOTS) {
                throw new NumberFormatException("out of bounds");
            }
        } catch (NumberFormatException exception) {
            send(player, "&cSpecify a 0-based container slot from 0 through 53.");
            return;
        }
        Block aimed = player.getTargetBlockExact(6);
        if (aimed == null || aimed.getType().isAir()) {
            send(player, "&eLook directly at a loaded physical chest/barrel within six blocks.");
            return;
        }
        Location location = aimed.getLocation();
        Slimefun.getSchedulerService().runAt(location, () -> {
            String message;
            try {
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    message = "&cTarget chunk unloaded; no evidence was written.";
                } else {
                    BlockState state = location.getBlock().getState();
                    if (!(state instanceof Container container)) {
                        message = "&cTarget is not a physical inventory container.";
                    } else {
                        DoctorIdentityEvidenceExport.Evidence evidence = captureContainer(
                                container.getInventory(),
                                slot,
                                DoctorLiveItemUpdate::registeredTemplate,
                                plugin.getDataFolder().toPath(),
                                player.getUniqueId());
                        message = "&aPrivate chest evidence saved: &7slot &e" + slot
                                + " &8| &7original receipt &e" + evidence.mainReceipt()
                                + " &8| &7current template &e" + evidence.offhandReceipt()
                                + " &8| &cKeep files PRIVATE; container and shop unchanged.";
                    }
                }
            } catch (IOException | RuntimeException exception) {
                message = "&cChest item evidence refused. Nothing in the chest/shop was changed.";
            }
            final String result = message;
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (player.isOnline()) {
                    send(player, result);
                }
            });
        });
    }

    /**
     * Exports exactly one *different* saved item and its current registered
     * template using the established symlink-safe Doctor evidence store.
     * No caller may promote this evidence operation into a repair preview.
     */
    static DoctorIdentityEvidenceExport.Evidence captureContainer(
            Inventory inventory, int slot, Function<ItemStack, ItemStack> templates,
            Path pluginFolder, UUID owner) throws IOException {
        if (slot < 0 || slot >= Math.min(inventory.getSize(), MAX_CONTAINER_SCAN_SLOTS)) {
            throw new IOException("Out-of-range physical container evidence slot");
        }
        try {
            ItemStack original = inventory.getItem(slot);
            if (original == null || original.getType().isAir() || itemId(original) == null) {
                throw new IOException("Container slot does not contain a registered Slimefun item");
            }
            ItemStack template = templates.apply(original);
            if (template == null || !sameRegisteredId(original, template)
                    || DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(original, template)) {
                throw new IOException("Unknown item or no historical mismatch to capture");
            }
            byte[] originalBytes = original.serializeAsBytes();
            byte[] currentBytes = template.serializeAsBytes();
            if (originalBytes.length > MAX_STACK_BYTES || currentBytes.length > MAX_STACK_BYTES) {
                throw new IOException("Chest item evidence exceeds the native-byte safety limit");
            }
            DoctorIdentityEvidenceExport.Evidence receipts =
                    DoctorIdentityEvidenceExport.capture(pluginFolder, owner, original, template);
            if (!Arrays.equals(originalBytes, inventory.getItem(slot).serializeAsBytes())
                    || !Arrays.equals(currentBytes, template.serializeAsBytes())) {
                throw new IOException("Container item/template changed while saving private evidence");
            }
            return receipts;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot safely capture chest item evidence", exception);
        }
    }

    /**
     * Bounded exact-source preview. Only presentation-style-only candidates
     * returned by the existing fail-closed Doctor repair algorithm qualify.
     */
    static Preview preview(Inventory inventory, Function<ItemStack, ItemStack> templates) {
        return preview(inventory, templates, MAX_SLOTS);
    }

    static Preview preview(Inventory inventory, Function<ItemStack, ItemStack> templates, int maxSlots) {
        int scanned = Math.min(inventory.getStorageContents().length, Math.min(maxSlots, MAX_CONTAINER_SCAN_SLOTS));
        int mismatched = 0;
        int eligible = 0;
        int refused = 0;
        List<Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < scanned; slot++) {
            try {
                ItemStack old = inventory.getItem(slot);
                if (old == null || old.getType().isAir() || itemId(old) == null) {
                    continue;
                }
                ItemStack template = templates.apply(old);
                if (template == null || !sameRegisteredId(old, template)) {
                    refused++;
                    continue;
                }
                if (DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(old, template)) {
                    continue;
                }
                mismatched++;
                ItemStack replacement = DoctorHistoricalStackRepair.prepare(old, template, template);
                if (replacement == null || old.serializeAsBytes().length > MAX_STACK_BYTES
                        || replacement.serializeAsBytes().length > MAX_STACK_BYTES) {
                    refused++;
                    continue;
                }
                eligible++;
                if (entries.size() < MAX_UPDATES) {
                    entries.add(new Entry(
                            slot,
                            DoctorHistoricalStackRepair.fingerprint(old),
                            DoctorHistoricalStackRepair.fingerprint(template),
                            DoctorHistoricalStackRepair.fingerprint(replacement)));
                }
            } catch (RuntimeException exception) {
                // A malformed or addon-owned item must not suppress inspection of
                // unrelated slots. Refuse the single slot, never mutate it.
                refused++;
            }
        }
        return new Preview(scanned, mismatched, eligible, refused, List.copyOf(entries));
    }

    /**
     * Only an unchanged exact-slot preview may be committed. Write and verify
     * EVERY original backup before touching any inventory slot. Recheck every
     * fingerprint after I/O, then verify each write; roll back unchanged
     * candidates when a synchronous inventory write fails.
     */
    static List<Receipt> apply(
            Inventory inventory, Preview plan, Function<ItemStack, ItemStack> templates,
            Path pluginFolder, UUID owner) throws IOException {
        return applyWithBudget(inventory, plan, templates, pluginFolder, owner, MAX_BATCH_BYTES);
    }

    /** A testable, bounded budget; production always uses MAX_BATCH_BYTES. */
    static List<Receipt> applyWithBudget(
            Inventory inventory, Preview plan, Function<ItemStack, ItemStack> templates,
            Path pluginFolder, UUID owner, int maxBatchBytes) throws IOException {
        if (maxBatchBytes <= 0 || maxBatchBytes > MAX_BATCH_BYTES) {
            throw new IOException("Invalid live item backup byte budget");
        }
        if (plan.entries().isEmpty() || plan.entries().size() > MAX_UPDATES) {
            throw new IOException("Live item update plan is empty or oversized");
        }
        List<Prepared> prepared = new ArrayList<>();
        int totalOriginalBytes = 0;
        for (Entry entry : plan.entries()) {
            if (entry.slot() < 0 || entry.slot() >= Math.min(inventory.getStorageContents().length, MAX_SLOTS)) {
                throw new IOException("Live item update slot is out of bounds");
            }
            ItemStack old = inventory.getItem(entry.slot());
            ItemStack template = old == null ? null : templates.apply(old);
            if (template == null || !sameRegisteredId(old, template)
                    || !entry.oldHash().equals(DoctorHistoricalStackRepair.fingerprint(old))
                    || !entry.templateHash().equals(DoctorHistoricalStackRepair.fingerprint(template))) {
                throw new IOException("Live item update plan has stale or unknown source data");
            }
            ItemStack replacement = DoctorHistoricalStackRepair.prepare(old, template, template);
            if (replacement == null || !entry.replacementHash().equals(DoctorHistoricalStackRepair.fingerprint(replacement))
                    || !DoctorHistoricalStackRepair.matchesSavedBackup(old, replacement, template, template)) {
                throw new IOException("Live item update candidate no longer passes strict Doctor verification");
            }
            byte[] oldBytes = old.serializeAsBytes();
            if (oldBytes.length > MAX_STACK_BYTES || replacement.serializeAsBytes().length > MAX_STACK_BYTES
                    || totalOriginalBytes > maxBatchBytes - oldBytes.length) {
                throw new IOException("Live item update exceeds the bounded original-byte backup budget");
            }
            totalOriginalBytes += oldBytes.length;
            prepared.add(new Prepared(entry, oldBytes, replacement));
        }

        List<Receipt> receipts = new ArrayList<>();
        for (Prepared item : prepared) {
            Path path = DoctorHistoricalStackRepair.backup(pluginFolder, owner, item.oldBytes());
            UUID receipt = receiptId(owner, path);
            ItemStack readback = DoctorHistoricalStackRepair.readVerifiedBackup(pluginFolder, owner, receipt);
            if (!Arrays.equals(item.oldBytes(), readback.serializeAsBytes())) {
                throw new IOException("Live item backup does not match the exact original source");
            }
            receipts.add(new Receipt(item.entry().slot(), receipt));
        }
        // Disk operations can take time; no inventory mutation if anything has moved.
        for (Prepared item : prepared) {
            ItemStack live = inventory.getItem(item.entry().slot());
            if (live == null || !item.entry().oldHash().equals(DoctorHistoricalStackRepair.fingerprint(live))) {
                throw new IOException("Live inventory changed while saving backups");
            }
        }

        List<Prepared> attempted = new ArrayList<>();
        try {
            for (Prepared item : prepared) {
                attempted.add(item);
                inventory.setItem(item.entry().slot(), item.replacement());
                ItemStack written = inventory.getItem(item.entry().slot());
                if (written == null
                        || !item.entry().replacementHash().equals(DoctorHistoricalStackRepair.fingerprint(written))) {
                    throw new IOException("Live inventory write readback disagrees with verified replacement");
                }
            }
        } catch (RuntimeException | IOException failure) {
            // Don't overwrite an unrelated stack inserted by another plugin.
            for (int i = attempted.size() - 1; i >= 0; i--) {
                Prepared item = attempted.get(i);
                try {
                    ItemStack observed = inventory.getItem(item.entry().slot());
                    if (observed != null
                            && item.entry().replacementHash().equals(DoctorHistoricalStackRepair.fingerprint(observed))) {
                        inventory.setItem(item.entry().slot(), ItemStack.deserializeBytes(item.oldBytes()));
                    }
                } catch (RuntimeException ignored) {
                    // Independent exact-original backups remain on disk for recovery.
                }
            }
            throw new IOException("Live item update interrupted; original receipts retained", failure);
        }
        return List.copyOf(receipts);
    }

    private static UUID receiptId(UUID owner, Path path) throws IOException {
        String name = path.getFileName().toString();
        String prefix = owner + "-";
        if (!name.startsWith(prefix) || !name.endsWith(".bin")) {
            throw new IOException("Unexpected private receipt path");
        }
        try {
            return UUID.fromString(name.substring(prefix.length(), name.length() - 4));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid private receipt ID", exception);
        }
    }

    private static ItemStack registeredTemplate(ItemStack old) {
        SlimefunItem registered = SlimefunItem.getByItem(old);
        return registered != null && Objects.equals(registered.getId(), itemId(old))
                ? registered.getItem()
                : null;
    }

    private static boolean sameRegisteredId(ItemStack old, ItemStack template) {
        String id = itemId(old);
        return id != null && Objects.equals(id, itemId(template)) && old.getType() == template.getType();
    }

    private static String itemId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(SLIMEFUN_ID, PersistentDataType.STRING);
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    record Entry(int slot, String oldHash, String templateHash, String replacementHash) {}
    record Preview(int scannedSlots, int mismatched, int eligible, int refused, List<Entry> entries) {}
    record Receipt(int slot, UUID id) {}
    private record SavedPlan(Preview preview, long expiresAtMillis) {}
    private record Prepared(Entry entry, byte[] oldBytes, ItemStack replacement) {}
}
