package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Opt-in single held-stack repair of presentation-only historical differences.
 * Does not iterate saved inventories, modify templates or loosen item matching.
 */
final class DoctorHistoricalStackRepair {

    private static final long PLAN_TTL_MILLIS = 5L * 60L * 1000L;
    private static final int MAX_PLANS = 128;
    private static final int MAX_RESTORATIONS_PER_PLAYER = 32;
    private static final long MAX_BACKUP_BYTES = 16L * 1024L * 1024L;
    private static final Map<UUID, Plan> PLANS = new ConcurrentHashMap<>();
    // Track independent recent repairs by the complete repaired item fingerprint.
    private static final Map<UUID, Map<String, Restoration>> RESTORATIONS = new ConcurrentHashMap<>();

    private DoctorHistoricalStackRepair() {}

    static void run(Slimefun plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cHistorical item repair requires a player holding both item versions.");
            return;
        }

        long now = System.currentTimeMillis();
        PLANS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        RESTORATIONS.forEach((playerId, records) -> {
            records.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
            if (records.isEmpty()) {
                RESTORATIONS.remove(playerId, records);
            }
        });

        String action = args.length > 3 ? args[3].toLowerCase(java.util.Locale.ROOT) : "help";
        switch (action) {
            case "scan" -> scan(player, now);
            case "confirm" -> confirm(plugin, player, now);
            case "verify" -> {
                if (args.length == 5) {
                    verifySavedBackup(plugin, player, args[4], false);
                } else {
                    send(sender, "&eUsage: /sf doctor identity repair verify <backup-uuid>");
                }
            }
            case "rollback" -> {
                if (args.length == 5 && args[4].equalsIgnoreCase("confirm")) {
                    rollback(plugin, player, now);
                } else if (args.length == 6 && args[5].equalsIgnoreCase("confirm")) {
                    verifySavedBackup(plugin, player, args[4], true);
                } else {
                    send(sender, "&eUsage: /sf doctor identity repair rollback confirm");
                    send(sender, "&eAfter restart: /sf doctor identity repair rollback <backup-uuid> confirm");
                }
            }
            default -> {
                send(sender, "&eHistorical styling repair is opt-in, one held stack at a time.");
                send(sender, "&eHold the OLD item in your main hand, a matching NEW item in your offhand.");
                send(sender, "&eRun /sf doctor identity repair scan then /sf doctor identity repair confirm.");
                send(sender, "&eAfter restart: /sf doctor identity repair verify <backup-uuid>.");
                send(sender, "&cNo barrel, backpack, machine or other inventory is rewritten by this command.");
            }
        }
    }

    private static void scan(Player player, long now) {
        PLANS.remove(player.getUniqueId());
        if (PLANS.size() >= MAX_PLANS) {
            send(player, "&cToo many pending item repair scans. Try again after they expire.");
            return;
        }

        var pair = eligiblePair(player);
        if (pair == null) {
            send(player, "&cRepair refused: both hands must have the same registered Slimefun ID,");
            send(player, "&cand the offhand must match the current registered item template exactly.");
            return;
        }

        try {
            ItemStack candidate = prepare(pair.old(), pair.fresh(), pair.template());
            if (candidate == null) {
                send(player, "&eNo safe presentation-only repair is proven for these items.");
                send(player, "&eUse /sf doctor identity compare for read-only mismatch details.");
                return;
            }

            PLANS.put(
                    player.getUniqueId(),
                    new Plan(
                            fingerprint(pair.old()),
                            fingerprint(pair.fresh()),
                            fingerprint(pair.template()),
                            fingerprint(candidate),
                            now + PLAN_TTL_MILLIS));
            send(player, "&aHistorical item preview passed: only Adventure name/lore presentation would change.");
            send(player, "&7Original amount: &e" + pair.old().getAmount() + " &7| New stack capacity unchanged.");
            send(player, "&7Item ID: &e" + pair.itemId() + " &7| PDC, model, skull and other metadata MATCH.");
            send(player, "&cMake a server backup first. This plan expires in five minutes.");
            send(player, "&eTo back up and convert only the main-hand stack:");
            send(player, "&6/sf doctor identity repair confirm");
        } catch (RuntimeException exception) {
            PLANS.remove(player.getUniqueId());
            send(player, "&cRepair preview cannot safely serialize these items. Nothing was changed.");
        }
    }

    private static void confirm(Slimefun plugin, Player player, long now) {
        Plan plan = PLANS.remove(player.getUniqueId());
        if (plan == null || plan.expiresAt() < now) {
            send(player, "&cNo current repair preview. Run /sf doctor identity repair scan again.");
            return;
        }
        var pair = eligiblePair(player);
        if (pair == null) {
            send(player, "&cItem registration or reference changed; no repair was made.");
            return;
        }

        ItemStack originalForRollback = null;
        boolean attemptedWrite = false;
        boolean backupVerified = false;
        try {
            ItemStack candidate = prepare(pair.old(), pair.fresh(), pair.template());
            if (candidate == null || !matches(plan, pair, candidate)) {
                send(player, "&cHeld item, amount, template or metadata changed since preview. No repair.");
                return;
            }
            // Identical repaired stacks cannot safely map to two different
            // historical originals. Refuse rather than pick an ambiguous backup.
            Map<String, Restoration> records =
                    RESTORATIONS.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
            if (records.containsKey(plan.repairedHash())) {
                send(player, "&cA rollback for an identical repaired stack is already pending. No repair made.");
                return;
            }
            if (records.size() >= MAX_RESTORATIONS_PER_PLAYER) {
                send(player, "&cToo many active rollback records. Let one expire or undo it first.");
                return;
            }

            // Write and verify a full original native ItemStack snapshot BEFORE the first mutation.
            byte[] originalBytes = pair.old().serializeAsBytes();
            Path backup = backup(plugin.getDataFolder().toPath(), player.getUniqueId(), originalBytes);
            String backupName = backup.getFileName().toString();
            String ownerPrefix = player.getUniqueId().toString() + "-";
            UUID backupId = UUID.fromString(backupName.substring(ownerPrefix.length(), backupName.length() - 4));
            ItemStack original = readVerifiedBackup(plugin.getDataFolder().toPath(), player.getUniqueId(), backupId);
            if (!Arrays.equals(originalBytes, original.serializeAsBytes())
                    || original.getAmount() != pair.old().getAmount()
                    || !fingerprint(original).equals(plan.oldHash())) {
                send(player, "&cBackup readback did not preserve item identity. No repair was made.");
                return;
            }

            backupVerified = true;
            originalForRollback = original;
            attemptedWrite = true;
            player.getInventory().setItemInMainHand(candidate);
            if (!fingerprint(player.getInventory().getItemInMainHand()).equals(plan.repairedHash())) {
                player.getInventory().setItemInMainHand(original);
                send(player, "&cResult verification failed; original restored to the main hand.");
                send(player, "&cThe untouched original backup remains in Slimefun/recovery/identity.");
                return;
            }

            Restoration saved = new Restoration(backupId, plan.oldHash(), plan.repairedHash(), now + PLAN_TTL_MILLIS);
            if (records.putIfAbsent(plan.repairedHash(), saved) != null) {
                player.getInventory().setItemInMainHand(original);
                send(player, "&cAn identical rollback was registered during confirmation; original restored.");
                return;
            }
            send(player, "&aOnly the historical name/lore styling was updated; amount and functional data retained.");
            send(player, "&7Original serialized item saved under plugins/Slimefun/recovery/identity.");
            send(player, "&7Private rollback receipt: &e" + backupId);
            send(player, "&eUndo within five minutes while still holding this stack:");
            send(player, "&6/sf doctor identity repair rollback confirm");
            send(player, "&eAfter restart keep a fresh reference in the offhand and use:");
            send(player, "&6/sf doctor identity repair verify " + backupId);
        } catch (IOException | RuntimeException exception) {
            if (attemptedWrite && originalForRollback != null) {
                try {
                    player.getInventory().setItemInMainHand(originalForRollback);
                } catch (RuntimeException ignored) {
                    // The independently verified on-disk backup remains for operator recovery.
                }
            }
            send(player, "&cRepair aborted due to backup or item verification failure.");
            if (backupVerified) {
                send(player, "&cVerified original backup is retained under Slimefun/recovery/identity.");
            } else {
                send(player, "&cNo verified disk backup was produced. Check your original stack before retrying.");
            }
        }
    }

    private static void rollback(Slimefun plugin, Player player, long now) {
        Map<String, Restoration> records = RESTORATIONS.get(player.getUniqueId());
        if (records == null || records.isEmpty()) {
            send(player, "&cNo recent repair can be rolled back in this session.");
            return;
        }
        try {
            ItemStack current = player.getInventory().getItemInMainHand();
            String currentHash = fingerprint(current);
            Restoration restore = records.get(currentHash);
            if (restore == null || restore.expiresAt() < now) {
                send(player, "&cNo rollback matches the item in your main hand. Other backups remain available.");
                return;
            }
            ItemStack original =
                    readVerifiedBackup(plugin.getDataFolder().toPath(), player.getUniqueId(), restore.receipt());
            if (!fingerprint(original).equals(restore.originalHash())) {
                send(player, "&cOriginal backup failed verification. No change made.");
                return;
            }
            if (!fingerprint(player.getInventory().getItemInMainHand()).equals(currentHash)) {
                send(player, "&cThe held item changed while checking the backup. No change made.");
                return;
            }
            player.getInventory().setItemInMainHand(original);
            if (!fingerprint(player.getInventory().getItemInMainHand()).equals(restore.originalHash())) {
                player.getInventory().setItemInMainHand(current);
                send(player, "&cRollback readback failed; repaired stack restored when possible.");
                send(player, "&cOriginal backup remains on disk.");
                return;
            }
            records.remove(currentHash, restore);
            if (records.isEmpty()) {
                RESTORATIONS.remove(player.getUniqueId(), records);
            }
            send(player, "&aOriginal historical item restored exactly from the saved backup.");
        } catch (IOException | RuntimeException exception) {
            send(player, "&cRollback failed. The original saved-item backup was not deleted.");
        }
    }

    /**
     * Deliberately explicit post-restart verification/rollback: the in-memory
     * five-minute plan is lost on reboot, but the original native item bytes
     * remain on disk. No automatic inventory search or restoration is allowed.
     */
    private static void verifySavedBackup(Slimefun plugin, Player player, String receiptText, boolean restore) {
        UUID receipt;
        try {
            receipt = UUID.fromString(receiptText);
        } catch (IllegalArgumentException exception) {
            send(player, "&cInvalid backup UUID. No item was changed.");
            return;
        }
        try {
            ItemStack original = readVerifiedBackup(plugin.getDataFolder().toPath(), player.getUniqueId(), receipt);
            Pair pair = eligiblePair(player);
            if (pair == null || !matchesSavedBackup(original, pair.old(), pair.fresh(), pair.template())) {
                send(player, "&cBackup and held item/reference do not match exactly; no change made.");
                send(player, "&eKeep the unchanged repaired stack in main hand and a fresh template in offhand.");
                return;
            }

            if (!restore) {
                send(
                        player,
                        "&aOriginal backup verified after reload; candidate bytes, typed metadata and amount match.");
                send(player, "&7Only the original held-stack presentation differs; no item was changed.");
                send(player, "&eRestore on an isolated server clone only:");
                send(player, "&6/sf doctor identity repair rollback " + receipt + " confirm");
                return;
            }

            String repairedHash = fingerprint(pair.old());
            String originalHash = fingerprint(original);
            // Fail closed if another inventory operation altered the main hand
            // during disk verification. Do not replace another item or amount.
            if (!repairedHash.equals(fingerprint(player.getInventory().getItemInMainHand()))) {
                send(player, "&cMain-hand item changed during rollback verification; no change made.");
                return;
            }
            ItemStack repaired = nativeSnapshot(pair.old());
            if (repaired == null) {
                send(player, "&cUnable to preserve the current repaired stack; no change made.");
                return;
            }
            try {
                player.getInventory().setItemInMainHand(original);
                if (!originalHash.equals(fingerprint(player.getInventory().getItemInMainHand()))) {
                    player.getInventory().setItemInMainHand(repaired);
                    send(player, "&cRollback readback failed; original backup retained on disk.");
                    return;
                }
            } catch (RuntimeException exception) {
                try {
                    player.getInventory().setItemInMainHand(repaired);
                } catch (RuntimeException ignored) {
                    // Native original bytes also remain on disk for operator recovery.
                }
                send(player, "&cRollback write failed; verify the held stack before attempting another repair.");
                return;
            }
            Map<String, Restoration> recent = RESTORATIONS.get(player.getUniqueId());
            if (recent != null) {
                Restoration saved = recent.get(repairedHash);
                if (saved != null && saved.receipt().equals(receipt)) {
                    recent.remove(repairedHash, saved);
                }
            }
            send(player, "&aOriginal item presentation and exact quantity restored from the verified backup.");
            send(player, "&7Backup remains on disk. No other inventory was modified.");
        } catch (IOException | RuntimeException exception) {
            send(player, "&cBackup missing, unsafe, or unreadable. No item was changed.");
        }
    }

    /**
     * Owner-scoped receipt lookup, with no path strings supplied by callers and
     * no symlinks or oversized/unreadable serialized stacks accepted.
     */
    static ItemStack readVerifiedBackup(Path pluginFolder, UUID playerId, UUID receipt) throws IOException {
        Path recovery = pluginFolder.resolve("recovery");
        Path directory = recovery.resolve("identity");
        if (!Files.isDirectory(pluginFolder, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Doctor identity backup folder is missing or redirected");
        }
        Path source = directory.resolve(playerId + "-" + receipt + ".bin");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Doctor identity backup is not a regular file");
        }
        // Open the backing file once, refusing symlinks at open time. Size and
        // data must come from the same descriptor; never trust a prior
        // path-based size check followed by a separate unbounded read.
        byte[] data;
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size <= 0 || size > MAX_BACKUP_BYTES) {
                throw new IOException("Doctor identity backup is empty or oversized");
            }
            ByteBuffer bytes = ByteBuffer.allocate((int) size);
            while (bytes.hasRemaining()) {
                if (channel.read(bytes) <= 0) {
                    throw new IOException("Doctor identity backup ended or stalled during verification");
                }
            }
            if (channel.read(ByteBuffer.allocate(1)) != -1) {
                throw new IOException("Doctor identity backup grew during verification");
            }
            data = bytes.array();
        }
        try {
            ItemStack original = ItemStack.deserializeBytes(data);
            if (original.getType().isAir()
                    || original.getAmount() <= 0
                    || !Arrays.equals(data, original.serializeAsBytes())) {
                throw new IOException("Doctor identity backup failed exact native serialization");
            }
            return original;
        } catch (RuntimeException exception) {
            throw new IOException("Doctor identity backup cannot be deserialized safely", exception);
        }
    }

    /** Only exact original styling may be restored, never a different typed item. */
    static boolean matchesSavedBackup(ItemStack original, ItemStack held, ItemStack fresh, ItemStack template) {
        ItemStack candidate = prepare(original, fresh, template);
        return candidate != null
                && fingerprint(candidate).equals(fingerprint(held))
                && original.getAmount() == held.getAmount()
                && preservesNonPresentationBytes(original, held);
    }

    /**
     * Produces a detached candidate, or refuses if any functional field would
     * be lost. The replacement must be strictly stack-compatible with the
     * current registered template reference, never just visually equivalent.
     */
    static ItemStack prepare(ItemStack old, ItemStack fresh, ItemStack template) {
        if (old == null
                || fresh == null
                || template == null
                || old.getType().isAir()
                || fresh.getType().isAir()) {
            return null;
        }
        // A registered SlimefunItemStack is not a CraftItemStack. Comparing it
        // directly with a Paper inventory stack can throw ClassCastException.
        // Round-trip all three to verified native bytes BEFORE strict matching.
        ItemStack oldNative = nativeSnapshot(old);
        ItemStack freshNative = nativeSnapshot(fresh);
        ItemStack templateNative = nativeSnapshot(template);
        if (oldNative == null
                || freshNative == null
                || templateNative == null
                || oldNative.isSimilar(freshNative)
                || !freshNative.isSimilar(templateNative)
                || !sameNativeBytesIgnoringAmount(freshNative, templateNative)
                || !DoctorIdentityCommand.inspectHistoricalPresentation(oldNative, freshNative)
                        .candidate()) {
            return null;
        }
        ItemMeta referenceMeta = freshNative.getItemMeta();
        if (oldNative.getItemMeta() == null || referenceMeta == null) {
            return null;
        }
        oldNative.editMeta(meta -> {
            meta.displayName(referenceMeta.displayName());
            meta.lore(referenceMeta.lore());
        });
        // ItemStack.isSimilar is necessary for vanilla stacking but can ignore
        // opaque data components. After removing ONLY display name and lore from
        // both copies, the complete serialized bytes must still be identical.
        return oldNative.getAmount() == old.getAmount()
                        && oldNative.isSimilar(freshNative)
                        && sameNativeBytesIgnoringAmount(oldNative, freshNative)
                        && preservesNonPresentationBytes(old, oldNative)
                ? oldNative
                : null;
    }

    /**
     * Fail closed if updating visible text would change any other stored bytes,
     * including opaque future Minecraft or addon item components.
     */
    static boolean preservesNonPresentationBytes(ItemStack original, ItemStack repaired) {
        ItemStack oldCopy = nativeSnapshot(original);
        ItemStack repairedCopy = nativeSnapshot(repaired);
        if (oldCopy == null || repairedCopy == null) {
            return false;
        }
        oldCopy.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        repairedCopy.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        return Arrays.equals(oldCopy.serializeAsBytes(), repairedCopy.serializeAsBytes());
    }

    private static Pair eligiblePair(Player player) {
        try {
            ItemStack old = player.getInventory().getItemInMainHand();
            ItemStack fresh = player.getInventory().getItemInOffHand();
            if (old.getType().isAir() || fresh.getType().isAir()) {
                return null;
            }
            SlimefunItem oldItem = SlimefunItem.getByItem(old);
            SlimefunItem newItem = SlimefunItem.getByItem(fresh);
            if (oldItem == null || oldItem != newItem || !Objects.equals(oldItem.getId(), newItem.getId())) {
                return null;
            }
            // The source may be an addon SlimefunItemStack subclass; never pass
            // it directly to CraftItemStack.isSimilar.
            ItemStack template = nativeSnapshot(oldItem.getItem());
            ItemStack freshNative = nativeSnapshot(fresh);
            return template != null
                            && freshNative != null
                            && freshNative.isSimilar(template)
                            && sameNativeBytesIgnoringAmount(freshNative, template)
                    ? new Pair(old, fresh, template, oldItem.getId())
                    : null;
        } catch (RuntimeException exception) {
            // Unknown addon/native serialization must not authorize any repair.
            return null;
        }
    }

    /**
     * Full native byte round trip, not ItemStack's polymorphic clone hook.
     * Reject snapshots which lose even a non-comparing serialized component.
     */
    private static ItemStack nativeSnapshot(ItemStack original) {
        try {
            byte[] bytes = original.serializeAsBytes();
            ItemStack copy = ItemStack.deserializeBytes(bytes);
            return original.getType() == copy.getType()
                            && original.getAmount() == copy.getAmount()
                            && Arrays.equals(bytes, copy.serializeAsBytes())
                    ? copy
                    : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Bukkit similarity can ignore opaque future item components and the
     * original stack quantity. A safe repair must have the exact registered
     * source bytes after normalizing only the count, with zero mutation of
     * either player hand or the registered template.
     */
    static boolean sameNativeBytesIgnoringAmount(ItemStack first, ItemStack second) {
        ItemStack left = nativeSnapshot(first);
        ItemStack right = nativeSnapshot(second);
        if (left == null || right == null || left.getAmount() <= 0 || right.getAmount() <= 0) {
            return false;
        }
        left.setAmount(1);
        right.setAmount(1);
        return Arrays.equals(left.serializeAsBytes(), right.serializeAsBytes());
    }

    static String fingerprint(ItemStack item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(
                    ByteBuffer.allocate(Integer.BYTES).putInt(item.getAmount()).array());
            digest.update(item.serializeAsBytes());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be present", exception);
        }
    }

    private static boolean matches(Plan plan, Pair pair, ItemStack candidate) {
        return plan.oldHash().equals(fingerprint(pair.old()))
                && plan.referenceHash().equals(fingerprint(pair.fresh()))
                && plan.templateHash().equals(fingerprint(pair.template()))
                && plan.repairedHash().equals(fingerprint(candidate));
    }

    static Path backup(Path pluginFolder, UUID playerId, byte[] originalBytes) throws IOException {
        if (originalBytes == null || originalBytes.length == 0 || originalBytes.length > MAX_BACKUP_BYTES) {
            throw new IOException("Doctor identity original bytes must be nonempty and bounded");
        }
        Path recovery = pluginFolder.resolve("recovery");
        Path directory = recovery.resolve("identity");
        // Backup paths are owner-controlled plugin storage. Refuse symlinked
        // components instead of silently writing original PDC outside this tree.
        if (!Files.isDirectory(pluginFolder, LinkOption.NOFOLLOW_LINKS)
                || (Files.exists(recovery, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS))
                || (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Doctor identity backup path is not a real local directory");
        }
        Files.createDirectories(directory);
        if (!Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Doctor identity backup directory must not be a symlink");
        }
        Path destination = directory.resolve(playerId + "-" + UUID.randomUUID() + ".bin");
        // CREATE_NEW refuses overwrites; force() makes the original item bytes
        // durable before the held inventory is even considered for mutation.
        try (FileChannel channel = FileChannel.open(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer bytes = ByteBuffer.wrap(originalBytes);
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
        return destination;
    }

    private static void send(CommandSender sender, String text) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }

    private record Pair(ItemStack old, ItemStack fresh, ItemStack template, String itemId) {}

    private record Plan(
            String oldHash, String referenceHash, String templateHash, String repairedHash, long expiresAt) {}

    private record Restoration(UUID receipt, String originalHash, String repairedHash, long expiresAt) {}
}
