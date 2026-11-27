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
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
    private static final int MAX_SLOT_HINTS = 12;
    // Refuse oversized addon/storage payloads on a live owner thread.
    private static final int MAX_STACK_BYTES = 256 * 1024;
    // Bounded full physical-inventory provenance for read-only evidence.
    // Even unrelated hopper/addon slot changes must invalidate a receipt export.
    private static final int MAX_CONTAINER_PREIMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 512 * 1024;
    private static final int MAX_PLANS = 64;
    private static final long PLAN_TTL_MILLIS = 60_000L;
    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");
    private static final Map<UUID, SavedPlan> PLANS = new ConcurrentHashMap<>();
    private static final Map<UUID, DoctorLiveInventoryRollback.Plan> ROLLBACK_PLANS =
            new ConcurrentHashMap<>();

    private DoctorLiveItemUpdate() {}

    static void run(Slimefun plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            send(sender, "&cLive item updates require a server operator in game.");
            return;
        }
        if (args.length == 6 && args[3].equalsIgnoreCase("rollback-preview")) {
            UUID operator = player.getUniqueId();
            PLANS.remove(operator);
            ROLLBACK_PLANS.remove(operator);
            try {
                UUID receipt = UUID.fromString(args[4]);
                int slot = Integer.parseInt(args[5]);
                var plan = DoctorLiveInventoryRollback.preview(
                        player.getInventory(), plugin.getDataFolder().toPath(), operator, receipt, slot,
                        DoctorLiveItemUpdate::registeredTemplate, System.currentTimeMillis());
                if (ROLLBACK_PLANS.size() >= MAX_PLANS) {
                    throw new IOException("Too many pending operator rollback previews");
                }
                ROLLBACK_PLANS.put(operator, plan);
                send(player, "&6Postrestart live-batch recovery preview &8| &7Slot: &e" + slot
                        + " &8| &7Original receipt: &e" + receipt);
                send(player, "&7Only the backed-up historical styling can be restored; "
                        + "the complete player storage, typed data, Slimefun ID and count are bound.");
                send(player, "&cPreview expires in 60 seconds. Back up the server first.");
                send(player, "&6/sf doctor identity live rollback-confirm");
            } catch (IOException | RuntimeException exception) {
                send(player, "&cOriginal receipt and current inventory slot do not match. Nothing changed.");
            }
            return;
        }
        if (args.length == 6 && args[3].equalsIgnoreCase("verify-evidence")) {
            PLANS.remove(player.getUniqueId());
            DoctorHistoricalStackEvidenceAudit.run(plugin, player, args[4], args[5]);
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("journal-container-full")) {
            PLANS.remove(player.getUniqueId());
            try {
                inspectContainerJournal(plugin, player, UUID.fromString(args[4]), true, false);
            } catch (IllegalArgumentException exception) {
                send(player, "&cProvide your own private full-container receipt UUID.");
            }
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("validate-container-journal")) {
            PLANS.remove(player.getUniqueId());
            try {
                inspectContainerJournal(plugin, player, UUID.fromString(args[4]), false, true);
            } catch (IllegalArgumentException exception) {
                send(player, "&cProvide your own private proposed-container journal UUID.");
            }
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("inspect-container-journal")) {
            PLANS.remove(player.getUniqueId());
            try {
                inspectContainerJournal(plugin, player, UUID.fromString(args[4]), false, false);
            } catch (IllegalArgumentException exception) {
                send(player, "&cProvide your own private proposed-container journal UUID.");
            }
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("preview-container-full")) {
            PLANS.remove(player.getUniqueId());
            try {
                inspectFullContainer(plugin, player, UUID.fromString(args[4]), true);
            } catch (IllegalArgumentException exception) {
                send(player, "&cProvide a valid UUID from your own full-container evidence receipt.");
            }
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("verify-container-full")) {
            PLANS.remove(player.getUniqueId());
            try {
                inspectFullContainer(plugin, player, UUID.fromString(args[4]), false);
            } catch (IllegalArgumentException exception) {
                send(player, "&cProvide the UUID of your own private container receipt.");
            }
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("export-shop")) {
            PLANS.remove(player.getUniqueId());
            DoctorQuickShopReadOnly.export(plugin, player, args[4]);
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("inspect-shop")) {
            PLANS.remove(player.getUniqueId());
            DoctorQuickShopReadOnly.run(player, args[4]);
            return;
        }
        if (args.length == 5 && args[3].equalsIgnoreCase("inspect-container")) {
            PLANS.remove(player.getUniqueId());
            inspectContainerSlot(player, args[4]);
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
        ROLLBACK_PLANS.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        if (action.equals("rollback-confirm")) {
            PLANS.remove(owner);
            var plan = ROLLBACK_PLANS.remove(owner);
            if (plan == null) {
                send(player, "&cNo pending matching inventory rollback. Run rollback-preview first.");
                return;
            }
            try {
                var restored = DoctorLiveInventoryRollback.confirm(
                        player.getInventory(), plugin.getDataFolder().toPath(), owner,
                        plan, DoctorLiveItemUpdate::registeredTemplate, now);
                send(player, "&aRestored old presentation in slot &e" + restored.slot()
                        + " &awith the exact original amount and typed item data.");
                send(player, "&7Historical original receipt: &e" + restored.originalReceipt());
                send(player, "&7New backup of the previously updated stack: &e"
                        + restored.updatedBackupReceipt());
                send(player, "&cBoth native receipts are retained; no chest, shop or machine was changed.");
            } catch (IOException | RuntimeException exception) {
                send(player, "&cRollback refused or interrupted. Confirm inventory and private backups; "
                        + "no unrelated slot is intentionally changed.");
                plugin.getLogger().warning("Operator inventory receipt rollback interrupted: "
                        + exception.getClass().getSimpleName());
            }
            return;
        }
        if (action.equals("cancel")) {
            PLANS.remove(owner);
            ROLLBACK_PLANS.remove(owner);
            send(player, "&aPending live item update preview canceled; no items changed.");
            return;
        }
        if (action.equals("export-container-full")) {
            PLANS.remove(owner);
            inspectFullContainer(plugin, player, null, false);
            return;
        }
        if (action.equals("scan-container")) {
            PLANS.remove(owner);
            inspectContainer(player);
            return;
        }
        if (action.equals("scan")) {
            ROLLBACK_PLANS.remove(owner);
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
                send(player, "&7Blank-lore layout: &e" + preview.blankLoreOnly()
                        + " &8| &7Meaningful text: &e" + preview.meaningfulText()
                        + " &8| &7Non-presentation data: &e" + preview.functionalMetadata()
                        + " &8| &7Unknown IDs: &e" + preview.unknownIds());
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
                send(player, "&ePostrestart player-slot recovery: "
                        + "/sf doctor identity live rollback-preview <receipt> <slot>");
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
        send(sender, "&e/sf doctor identity live inspect-container <slot> &8(read-only, 0-based)");
        send(sender, "&e/sf doctor identity live inspect-shop <slot> &8(read-only, QuickShop single chest)");
        send(sender, "&e/sf doctor identity live export-shop <slot> &8(private evidence, single chest)");
        send(sender, "&e/sf doctor identity live export-container <slot> &8(private evidence, 0-based slot)");
        send(sender, "&e/sf doctor identity live export-container-full &8(private all-slot receipt)");
        send(sender, "&e/sf doctor identity live verify-container-full <receipt> &8(read-only)");
        send(sender, "&e/sf doctor identity live preview-container-full <receipt> &8(read-only, NO confirm)");
        send(sender, "&e/sf doctor identity live journal-container-full <receipt> &8(private read-only proposal)");
        send(sender, "&e/sf doctor identity live inspect-container-journal <journal> &8(read-only state)");
        send(sender, "&e/sf doctor identity live validate-container-journal <journal> &8(read-only revalidation)");
        send(sender, "&e/sf doctor identity live verify-evidence <old-receipt> <template-receipt>");
        send(sender, "&e/sf doctor identity live confirm");
        send(sender, "&e/sf doctor identity live rollback-preview <original-receipt> <slot> &8(OP-only)");
        send(sender, "&e/sf doctor identity live rollback-confirm &8(60-second exact inventory plan)");
        send(sender, "&e/sf doctor identity live cancel");
        send(sender, "&cManual and opt-in. No automatic chest, shop, player-join or machine rewrites.");
    }

    /**
     * Explicit operator-only whole-container provenance. The region-owned
     * callback can create or check a private evidence file but never applies
     * or rolls back any chest, QuickShop shop or saved world record.
     */
    private static void inspectFullContainer(Slimefun plugin, Player player, UUID receiptId, boolean previewOnly) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            send(player, "&eLook directly at a loaded single chest or barrel within six blocks.");
            return;
        }
        Location location = target.getLocation();
        Slimefun.getSchedulerService().runAt(location, () -> {
            String message;
            try {
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    throw new IOException("Unloaded target chunk");
                }
                BlockState state = location.getBlock().getState();
                if (!(state instanceof Container container)
                        || (state.getBlockData() instanceof Chest chest
                                && chest.getType() != Chest.Type.SINGLE)
                        || maySpanAnotherChunk(state.getBlockData(), location.getBlockX(), location.getBlockZ())) {
                    throw new IOException("Target is not a supported single physical container");
                }
                Inventory inventory = container.getInventory();
                var binding = DoctorContainerEvidenceEnvelope.Binding.from(location, state, inventory);
                if (receiptId == null) {
                    var receipt = DoctorContainerEvidenceEnvelope.capture(
                            plugin.getDataFolder().toPath(), player.getUniqueId(), binding, inventory);
                    message = "&aPrivate all-slot container evidence written: &e" + receipt.id()
                            + " &8| &7Slots: &e" + receipt.slots()
                            + " &8| &cKeep recovery/containers PRIVATE. No chest/shop data changed.";
                } else if (previewOnly) {
                    var report = DoctorContainerEvidenceEnvelope.assess(
                            plugin.getDataFolder().toPath(), player.getUniqueId(), receiptId, binding,
                            inventory, DoctorLiveItemUpdate::registeredTemplate);
                    message = "&6Original container receipt matches ALL slots &8| &7Scanned: &e"
                            + report.slots() + " &8| &7Style-only candidates: &a" + report.eligible()
                            + " &8| &7Refused/unknown: &e" + report.refused()
                            + " &8| &7Blank lore: &e" + report.blankLoreOnly()
                            + " &8| &7Meaningful text: &e" + report.meaningfulText()
                            + " &8| &7Functional data: &e" + report.functionalMetadata()
                            + " &8| &7Unknown ID: &e" + report.unknownIds()
                            + " &8| &7Candidate slots (first 8): &e" + report.candidateSlots()
                            + " &8| &cREAD-ONLY; no chest update/confirm exists.";
                } else {
                    var verified = DoctorContainerEvidenceEnvelope.verify(
                            plugin.getDataFolder().toPath(), player.getUniqueId(), receiptId, binding, inventory);
                    message = "&6Private container receipt verified &8| &7Slots: &e"
                            + verified.slots() + " &8| &7Original inventory still identical: &e"
                            + (verified.unchanged() ? "YES" : "NO")
                            + " &8| &cNo restoration or mutation performed.";
                }
            } catch (IOException | RuntimeException exception) {
                message = "&cContainer evidence refused. No inventory, shop or SQL changed.";
            }
            String response = message;
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (player.isOnline()) {
                    send(player, response);
                }
            });
        });
    }

    /**
     * Explicit operator-only read-only journal management, dispatched on the
     * physical container owning region. A journal records an after-image
     * proposal, not a write authorization. There is NO confirmation, apply,
     * rollback, QuickShop mutation, or hidden automatic repair.
     */
    private static void inspectContainerJournal(
            Slimefun plugin, Player player, UUID reference, boolean create, boolean revalidate) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            send(player, "&eLook directly at a loaded single chest or barrel within six blocks.");
            return;
        }
        Location location = target.getLocation();
        Slimefun.getSchedulerService().runAt(location, () -> {
            String message;
            try {
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    throw new IOException("Unloaded physical container target");
                }
                BlockState state = location.getBlock().getState();
                if (!(state instanceof Container container)
                        || (state.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE)
                        || maySpanAnotherChunk(state.getBlockData(), location.getBlockX(), location.getBlockZ())) {
                    throw new IOException("Unsupported physical chest/barrel or cross-region container");
                }
                Inventory inventory = container.getInventory();
                var binding = DoctorContainerEvidenceEnvelope.Binding.from(location, state, inventory);
                Path pluginFolder = plugin.getDataFolder().toPath();
                UUID operator = player.getUniqueId();
                if (create) {
                    var proposal = DoctorContainerTransactionJournal.begin(
                            pluginFolder, operator, reference, binding, inventory,
                            DoctorLiveItemUpdate::registeredTemplate);
                    message = "&aPrivate proposed-container journal saved: &e" + proposal.id()
                            + " &8| &7Eligible style-only slots: &e" + proposal.changedSlots()
                            + " &8| &7Original items: &e" + proposal.beforeItems()
                            + " &8| &7Proposed items: &e" + proposal.afterItems()
                            + " &8| &cNO chest writes, apply or confirm are available.";
                } else if (revalidate) {
                    var proposal = DoctorContainerTransactionJournal.revalidate(
                            pluginFolder, operator, reference, binding, inventory,
                            DoctorLiveItemUpdate::registeredTemplate);
                    message = "&aSaved proposal still matches current templates and ALL original slots."
                            + " &8| &7Candidates: &e" + proposal.changedSlots()
                            + " &8| &cREAD-ONLY; no chest writes authorized.";
                } else {
                    var report = DoctorContainerTransactionJournal.inspect(
                            pluginFolder, operator, reference, binding, inventory);
                    message = "&6Private container journal inspection &8| &7State: &e" + report.state()
                            + " &8| &7Slots: &e" + report.slots()
                            + " &8| &7Changed candidates: &e" + report.changes()
                            + " &8| &7Original item count: &e" + report.originalItems()
                            + " &8| &7Proposed item count: &e" + report.proposedItems()
                            + " &8| &7Exact proposed candidate slots: &e" + report.appliedCandidates()
                            + " &8| &7Exact original candidate slots: &e" + report.originalCandidates()
                            + " &8| &7Unexpected changed slots: &e" + report.unexpectedSlots()
                            + (report.expectedOnlyPartial()
                                    ? " &8| &6EXPECTED-ONLY PARTIAL (still DIVERGED)"
                                    : "")
                            + " &8| &cDiagnostic ONLY; no resume, repair or rollback is authorized.";
                }
            } catch (IOException | RuntimeException exception) {
                message = "&cContainer proposal/inspection refused. Verify receipt, location, ownership, "
                        + "unchanged source and native item identity. No chest/shop/SQL writes occurred.";
            }
            String response = message;
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (player.isOnline()) {
                    send(player, response);
                }
            });
        });
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
                    } else if (maySpanAnotherChunk(state.getBlockData(), location.getBlockX(), location.getBlockZ())) {
                        message = "&cBoundary double chest may span Folia regions; inspection refused. No items changed.";
                    } else {
                        Inventory inventory = container.getInventory();
                        ContainerPreimage preimage = captureContainerPreimage(inventory);
                        Preview inspection = preview(
                                inventory,
                                DoctorLiveItemUpdate::registeredTemplate,
                                MAX_CONTAINER_SCAN_SLOTS);
                        if (!containerUnchanged(inventory, preimage)) {
                            throw new IOException("Container changed while producing a diagnostic preview");
                        }
                        message = "&6Container item compatibility &8(read-only) &7Slots: &e"
                                + inspection.scannedSlots() + " &8| &7Mismatches: &e"
                                + inspection.mismatched() + " &8| &7Style-only: &a"
                                + inspection.eligible() + " &8| &7Refused/unknown: &e"
                                + inspection.refused()
                                + " &8| &7Blank lore: &e" + inspection.blankLoreOnly()
                                + " &8| &7Text drift: &e" + inspection.meaningfulText()
                                + " &8| &7Other data: &e" + inspection.functionalMetadata()
                                + " &8| &7Unknown: &e" + inspection.unknownIds()
                                + " &8| &7NO chest/shop data changed.";
                        message += " &8| &7Different slots (0-based): &e"
                                + inspection.differingSlots()
                                + (inspection.mismatched() > inspection.differingSlots().size()
                                        ? " &8(first " + MAX_SLOT_HINTS + " shown)"
                                        : "");
                    }
                }
            } catch (IOException | RuntimeException exception) {
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
     * Inspect one explicitly selected physical container slot on its owning
     * region. No private name/lore/PDC values are printed and no repair plan
     * is created. The earlier scan is only a hint; the slot is re-read here.
     */
    private static void inspectContainerSlot(Player player, String slotArgument) {
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
            send(player, "&eLook at a loaded physical chest/barrel within six blocks.");
            return;
        }
        Location location = aimed.getLocation();
        Slimefun.getSchedulerService().runAt(location, () -> {
            String message;
            try {
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    message = "&cTarget chunk unloaded. No item was inspected or changed.";
                } else {
                    BlockState state = location.getBlock().getState();
                    if (!(state instanceof Container container)) {
                        message = "&cTarget is not a physical inventory container.";
                    } else if (maySpanAnotherChunk(state.getBlockData(), location.getBlockX(), location.getBlockZ())) {
                        message = "&cBoundary double chest may span Folia regions; inspection refused. No data changed.";
                    } else {
                        SlotStatus status = classifyStoredSlot(
                                container.getInventory(), slot, DoctorLiveItemUpdate::registeredTemplate);
                        message = "&6Historical chest slot &e" + slot + " &8| &7Read-only classification: &e"
                                + status + " &8| &7No data changed. No repair authorized.";
                    }
                }
            } catch (RuntimeException exception) {
                message = "&cUnable to classify that slot safely. No data changed.";
            }
            String report = message;
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (player.isOnline()) {
                    send(player, report);
                }
            });
        });
    }

    /**
     * Narrow diagnostic for an actual stored item rather than a reconstructed
     * Bukkit setter fixture. Never equate matching visible text with permission
     * to rewrite metadata; classification cannot create a repair plan.
     */
    static SlotStatus classifyStoredSlot(
            Inventory inventory, int slot, Function<ItemStack, ItemStack> templates) {
        if (slot < 0 || slot >= Math.min(inventory.getSize(), MAX_CONTAINER_SCAN_SLOTS)) {
            throw new IllegalArgumentException("Out-of-range physical container slot");
        }
        try {
            ItemStack stored = inventory.getItem(slot);
            if (stored == null || stored.getType().isAir()) {
                return SlotStatus.EMPTY;
            }
            if (itemId(stored) == null) {
                return SlotStatus.NOT_SLIMEFUN;
            }
            byte[] originalBytes = stored.serializeAsBytes();
            ItemStack template = templates.apply(stored);
            if (template == null || !sameRegisteredId(stored, template)) {
                return SlotStatus.UNKNOWN_ID;
            }
            byte[] templateBytes = template.serializeAsBytes();
            if (originalBytes.length > MAX_STACK_BYTES || templateBytes.length > MAX_STACK_BYTES) {
                return SlotStatus.UNSAFE;
            }
            // Unsupported native serialization must be UNKNOWN/UNSAFE rather than
            // falsely diagnosed as a real difference in functional item metadata.
            ItemStack storedNative = ItemStack.deserializeBytes(originalBytes);
            ItemStack templateNative = ItemStack.deserializeBytes(templateBytes);
            if (storedNative.getType() != stored.getType()
                    || templateNative.getType() != template.getType()
                    || storedNative.getAmount() != stored.getAmount()
                    || templateNative.getAmount() != template.getAmount()
                    || !Arrays.equals(originalBytes, storedNative.serializeAsBytes())
                    || !Arrays.equals(templateBytes, templateNative.serializeAsBytes())) {
                return SlotStatus.UNSAFE;
            }
            SlotStatus status;
            if (DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(stored, template)) {
                status = SlotStatus.CURRENT;
            } else {
                var presentation = DoctorIdentityCommand.inspectHistoricalPresentation(stored, template);
                if (!presentation.nonPresentationSame()) {
                    status = SlotStatus.FUNCTIONAL_METADATA;
                } else {
                    var plain = DoctorIdentityCommand.inspectPlainPresentation(stored, template);
                    if (plain.nameSame()
                            && !plain.loreSame()
                            && DoctorIdentityCommand.sameNonblankPlainLore(stored, template)) {
                        status = SlotStatus.BLANK_LORE_LAYOUT;
                    } else if (!presentation.plainTextSame()) {
                        status = SlotStatus.MEANINGFUL_TEXT;
                    } else if (DoctorHistoricalStackRepair.prepare(stored, template, template) != null) {
                        status = SlotStatus.STYLE_ONLY_CANDIDATE;
                    } else {
                        status = SlotStatus.UNSAFE;
                    }
                }
            }
            // Refuse even a read-only classification if an intervening hopper or
            // addon changed the physical slot or registered template mid-scan.
            ItemStack current = inventory.getItem(slot);
            if (current == null
                    || !Arrays.equals(originalBytes, current.serializeAsBytes())
                    || !Arrays.equals(templateBytes, template.serializeAsBytes())) {
                return SlotStatus.UNSAFE;
            }
            return status;
        } catch (RuntimeException exception) {
            return SlotStatus.UNSAFE;
        }
    }

    enum SlotStatus {
        EMPTY,
        NOT_SLIMEFUN,
        UNKNOWN_ID,
        CURRENT,
        STYLE_ONLY_CANDIDATE,
        BLANK_LORE_LAYOUT,
        MEANINGFUL_TEXT,
        FUNCTIONAL_METADATA,
        UNSAFE
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
                    } else if (maySpanAnotherChunk(state.getBlockData(), location.getBlockX(), location.getBlockZ())) {
                        message = "&cBoundary double chest may span Folia regions; evidence refused. No data changed.";
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
     * A double chest on a chunk edge may contain a neighboring chunk's inventory,
     * which the target block's Folia region scheduler does not independently
     * establish ownership over. Refuse this ambiguous layout BEFORE obtaining the
     * combined inventory. This deliberately also refuses some same-chunk pairs
     * lying on an edge; a false refusal is safer than cross-region access.
     */
    static boolean maySpanAnotherChunk(BlockData data, int blockX, int blockZ) {
        return data instanceof Chest chest
                && chest.getType() != Chest.Type.SINGLE
                && ((blockX & 15) == 0
                        || (blockX & 15) == 15
                        || (blockZ & 15) == 0
                        || (blockZ & 15) == 15);
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
            ContainerPreimage preimage = captureContainerPreimage(inventory);
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
            ItemStack live = inventory.getItem(slot);
            if (live == null
                    || !Arrays.equals(originalBytes, live.serializeAsBytes())
                    || !Arrays.equals(currentBytes, template.serializeAsBytes())
                    || !containerUnchanged(inventory, preimage)) {
                throw new IOException("Container or registered template changed while saving private evidence");
            }
            return receipts;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot safely capture chest item evidence", exception);
        }
    }

    /**
     * Read-only complete physical-inventory provenance for a future
     * container-scoped migration. Hashes all slot positions, including empty
     * slots, using exact native serialization; validates a second independent
     * snapshot to refuse racing hoppers/Cargo/other inventory adapters.
     *
     * <p>The hash is private and never displayed or treated as repair
     * permission. This method makes no inventory, shop, or database writes.
     */
    static ContainerPreimage captureContainerPreimage(Inventory inventory) throws IOException {
        if (inventory == null || inventory.getSize() < 1
                || inventory.getSize() > MAX_CONTAINER_SCAN_SLOTS
                || inventory.getStorageContents().length != inventory.getSize()) {
            throw new IOException("Unsupported or oversized physical inventory layout");
        }
        byte[][] first = exactContainerSlots(inventory);
        byte[][] second = exactContainerSlots(inventory);
        if (!sameSlotBytes(first, second)) {
            throw new IOException("Physical inventory changed during native snapshot");
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(first.length).array());
            for (int slot = 0; slot < first.length; slot++) {
                byte[] item = first[slot];
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(slot).array());
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES)
                        .putInt(item == null ? -1 : item.length).array());
                if (item != null) {
                    digest.update(item);
                }
            }
            return new ContainerPreimage(first.length, java.util.HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable for physical chest provenance", impossible);
        }
    }

    static boolean containerUnchanged(Inventory inventory, ContainerPreimage expected) throws IOException {
        return expected != null
                && expected.equals(captureContainerPreimage(inventory));
    }

    static byte[][] exactContainerSlots(Inventory inventory) throws IOException {
        int size = inventory.getSize();
        byte[][] values = new byte[size][];
        int totalBytes = 0;
        try {
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                byte[] nativeBytes = stack.serializeAsBytes();
                if (nativeBytes.length > MAX_STACK_BYTES
                        || totalBytes > MAX_CONTAINER_PREIMAGE_BYTES - nativeBytes.length) {
                    throw new IOException("Physical inventory exceeds bounded native-byte snapshot budget");
                }
                ItemStack decoded = ItemStack.deserializeBytes(nativeBytes);
                if (decoded.getType() != stack.getType()
                        || decoded.getAmount() != stack.getAmount()
                        || !Arrays.equals(nativeBytes, decoded.serializeAsBytes())) {
                    throw new IOException("Physical inventory native item is not an exact round-trip");
                }
                values[slot] = nativeBytes;
                totalBytes += nativeBytes.length;
            }
            return values;
        } catch (RuntimeException exception) {
            throw new IOException("Unsafe physical inventory serialization", exception);
        }
    }

    private static boolean sameSlotBytes(byte[][] left, byte[][] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int slot = 0; slot < left.length; slot++) {
            if (!Arrays.equals(left[slot], right[slot])) {
                return false;
            }
        }
        return true;
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
        int blankLoreOnly = 0;
        int meaningfulText = 0;
        int functionalMetadata = 0;
        int unknownIds = 0;
        List<Entry> entries = new ArrayList<>();
        List<Integer> differingSlots = new ArrayList<>();
        for (int slot = 0; slot < scanned; slot++) {
            try {
                ItemStack old = inventory.getItem(slot);
                if (old == null || old.getType().isAir() || itemId(old) == null) {
                    continue;
                }
                ItemStack template = templates.apply(old);
                if (template == null || !sameRegisteredId(old, template)) {
                    refused++;
                    unknownIds++;
                    continue;
                }
                if (DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(old, template)) {
                    continue;
                }
                mismatched++;
                if (differingSlots.size() < MAX_SLOT_HINTS) {
                    differingSlots.add(slot);
                }
                ItemStack replacement = DoctorHistoricalStackRepair.prepare(old, template, template);
                if (replacement == null || old.serializeAsBytes().length > MAX_STACK_BYTES
                        || replacement.serializeAsBytes().length > MAX_STACK_BYTES) {
                    refused++;
                    if (replacement == null) {
                        // These counts are hints only. They MUST NOT be treated
                        // as approval to normalize a historical stack.
                        try {
                            var presentation = DoctorIdentityCommand.inspectHistoricalPresentation(old, template);
                            if (!presentation.nonPresentationSame()) {
                                functionalMetadata++;
                            } else {
                                var plain = DoctorIdentityCommand.inspectPlainPresentation(old, template);
                                if (plain.nameSame() && !plain.loreSame()
                                        && DoctorIdentityCommand.sameNonblankPlainLore(old, template)) {
                                    blankLoreOnly++;
                                } else if (!presentation.plainTextSame()) {
                                    meaningfulText++;
                                } else {
                                    functionalMetadata++;
                                }
                            }
                        } catch (RuntimeException ignored) {
                            // Refused item remains refused; never expose metadata.
                        }
                    }
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
        return new Preview(scanned, mismatched, eligible, refused, List.copyOf(entries),
                blankLoreOnly, meaningfulText, functionalMetadata, unknownIds, List.copyOf(differingSlots));
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
        // Preserve every ordinary storage, armor and offhand slot, not just
        // the eight slots selected for the operator-approved update.
        byte[][] originalInventory = snapshotFullInventory(inventory);
        List<Prepared> prepared = new ArrayList<>();
        boolean[] selectedSlots = new boolean[MAX_SLOTS];
        int totalOriginalBytes = 0;
        for (Entry entry : plan.entries()) {
            if (entry.slot() < 0 || entry.slot() >= Math.min(inventory.getStorageContents().length, MAX_SLOTS)) {
                throw new IOException("Live item update slot is out of bounds");
            }
            if (selectedSlots[entry.slot()]) {
                throw new IOException("Live item update plan repeats a slot");
            }
            selectedSlots[entry.slot()] = true;
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
        // Disk operations can take time; refuse if a source or registered template changed.
        for (Prepared item : prepared) {
            ItemStack live = inventory.getItem(item.entry().slot());
            if (live == null || !item.entry().oldHash().equals(DoctorHistoricalStackRepair.fingerprint(live))) {
                throw new IOException("Live inventory changed while saving backups");
            }
            ItemStack template = templates.apply(live);
            if (template == null || !sameRegisteredId(live, template)
                    || !item.entry().templateHash().equals(DoctorHistoricalStackRepair.fingerprint(template))) {
                throw new IOException("Registered item template changed while saving backups");
            }
        }

        // Saving verified receipts involves disk I/O. If any unselected
        // player slot changed while saving, do not attempt even one write.
        if (!sameSlotBytes(originalInventory, snapshotFullInventory(inventory))) {
            throw new IOException("Player inventory changed while saving live item backups");
        }

        List<Prepared> attempted = new ArrayList<>();
        try {
            for (Prepared item : prepared) {
                // A previous setItem may synchronously trigger a third-party
                // adapter which changes another planned slot or registration.
                // Revalidate the exact preimage immediately before EACH write,
                // not only once after backup I/O.
                ItemStack live = inventory.getItem(item.entry().slot());
                if (live == null
                        || !item.entry().oldHash().equals(DoctorHistoricalStackRepair.fingerprint(live))) {
                    throw new IOException("Live inventory slot changed during an earlier batch write");
                }
                ItemStack template = templates.apply(live);
                if (template == null || !sameRegisteredId(live, template)
                        || !item.entry().templateHash().equals(DoctorHistoricalStackRepair.fingerprint(template))) {
                    throw new IOException("Registered template changed during an earlier batch write");
                }
                // Template resolution is another callback boundary. Do not
                // overwrite a foreign stack inserted during that lookup.
                ItemStack beforeWrite = inventory.getItem(item.entry().slot());
                if (beforeWrite == null || !Arrays.equals(item.oldBytes(), beforeWrite.serializeAsBytes())) {
                    throw new IOException("Live inventory slot changed during final template lookup");
                }
                attempted.add(item);
                inventory.setItem(item.entry().slot(), item.replacement());
                ItemStack written = inventory.getItem(item.entry().slot());
                if (written == null
                        || !item.entry().replacementHash().equals(DoctorHistoricalStackRepair.fingerprint(written))) {
                    throw new IOException("Live inventory write readback disagrees with verified replacement");
                }
                // A synchronous addon hook must not silently mutate an
                // unrelated stack while Doctor repairs a different slot.
                if (!unchangedOutsideSelected(originalInventory,
                        snapshotFullInventory(inventory), selectedSlots)) {
                    throw new IOException("Unselected player inventory slot changed during live update");
                }
            }
            // A later write can synchronously change an earlier slot. Never
            // report success based on a stale per-slot readback.
            for (Prepared item : prepared) {
                ItemStack written = inventory.getItem(item.entry().slot());
                if (written == null
                        || !item.entry().replacementHash().equals(DoctorHistoricalStackRepair.fingerprint(written))) {
                    throw new IOException("Live inventory final batch readback disagrees with verified replacement");
                }
            }
            if (!unchangedOutsideSelected(originalInventory,
                    snapshotFullInventory(inventory), selectedSlots)) {
                throw new IOException("Unselected player inventory slot changed during final live update validation");
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

    /**
     * The live command passes an actual PlayerInventory, so keep its full
     * storage, armor and offhand byte-level snapshot. The existing native
     * migration harness deliberately calls this engine by reflection with
     * a disposable 27-slot chest to verify historical saved-world identity.
     * Its inventory is fully captured as well, but this method does not
     * expose any new chest-write command or automatic migration.
     */
    private static byte[][] snapshotFullInventory(Inventory inventory) throws IOException {
        if (inventory instanceof PlayerInventory) {
            return DoctorLiveInventoryRollback.snapshotStorage(inventory);
        }
        if (inventory == null || inventory.getSize() < 9
                || inventory.getSize() > MAX_CONTAINER_SCAN_SLOTS
                || inventory.getStorageContents().length != inventory.getSize()) {
            throw new IOException("Unsupported bounded native inventory fixture layout");
        }
        byte[][] first = exactContainerSlots(inventory);
        byte[][] second = exactContainerSlots(inventory);
        if (!sameSlotBytes(first, second)) {
            throw new IOException("Native inventory fixture changed during snapshot");
        }
        return first;
    }

    private static boolean unchangedOutsideSelected(byte[][] original, byte[][] observed, boolean[] selected) {
        if (original.length != observed.length) {
            return false;
        }
        for (int slot = 0; slot < original.length; slot++) {
            if ((slot >= selected.length || !selected[slot])
                    && !Arrays.equals(original[slot], observed[slot])) {
                return false;
            }
        }
        return true;
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
    record Preview(
            int scannedSlots,
            int mismatched,
            int eligible,
            int refused,
            List<Entry> entries,
            int blankLoreOnly,
            int meaningfulText,
            int functionalMetadata,
            int unknownIds,
            List<Integer> differingSlots) {}
    record Receipt(int slot, UUID id) {}
    record ContainerPreimage(int slots, String sha256) {}
    private record SavedPlan(Preview preview, long expiresAtMillis) {}
    private record Prepared(Entry entry, byte[] oldBytes, ItemStack replacement) {}
}
