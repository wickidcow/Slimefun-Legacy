package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
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
    private static final Map<UUID, Plan> PLANS = new ConcurrentHashMap<>();
    private static final Map<UUID, Restoration> RESTORATIONS = new ConcurrentHashMap<>();

    private DoctorHistoricalStackRepair() {}

    static void run(Slimefun plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cHistorical item repair requires a player holding both item versions.");
            return;
        }

        long now = System.currentTimeMillis();
        PLANS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        RESTORATIONS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);

        String action = args.length > 3 ? args[3].toLowerCase(java.util.Locale.ROOT) : "help";
        switch (action) {
            case "scan" -> scan(player, now);
            case "confirm" -> confirm(plugin, player, now);
            case "rollback" -> {
                if (args.length < 5 || !args[4].equalsIgnoreCase("confirm")) {
                    send(sender, "&eUsage: /sf doctor identity repair rollback confirm");
                } else {
                    rollback(player, now);
                }
            }
            default -> {
                send(sender, "&eHistorical styling repair is opt-in, one held stack at a time.");
                send(sender, "&eHold the OLD item in your main hand, a matching NEW item in your offhand.");
                send(sender, "&eRun /sf doctor identity repair scan then /sf doctor identity repair confirm.");
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
        try {
            ItemStack candidate = prepare(pair.old(), pair.fresh(), pair.template());
            if (candidate == null || !matches(plan, pair, candidate)) {
                send(player, "&cHeld item, amount, template or metadata changed since preview. No repair.");
                return;
            }

            // Write and verify a full original native ItemStack snapshot BEFORE the first mutation.
            byte[] originalBytes = pair.old().serializeAsBytes();
            Path backup = backup(plugin.getDataFolder().toPath(), player.getUniqueId(), originalBytes);
            ItemStack original = ItemStack.deserializeBytes(Files.readAllBytes(backup));
            if (!Arrays.equals(originalBytes, original.serializeAsBytes())
                    || original.getAmount() != pair.old().getAmount()
                    || !fingerprint(original).equals(plan.oldHash())) {
                send(player, "&cBackup readback did not preserve item identity. No repair was made.");
                return;
            }

            originalForRollback = original;
            attemptedWrite = true;
            player.getInventory().setItemInMainHand(candidate);
            if (!fingerprint(player.getInventory().getItemInMainHand()).equals(plan.repairedHash())) {
                player.getInventory().setItemInMainHand(original);
                send(player, "&cResult verification failed; original restored to the main hand.");
                send(player, "&cThe untouched original backup remains in Slimefun/recovery/identity.");
                return;
            }

            RESTORATIONS.put(
                    player.getUniqueId(),
                    new Restoration(backup, plan.oldHash(), plan.repairedHash(), now + PLAN_TTL_MILLIS));
            send(player, "&aOnly the historical name/lore styling was updated; amount and functional data retained.");
            send(player, "&7Original serialized item saved under plugins/Slimefun/recovery/identity.");
            send(player, "&eUndo within five minutes while still holding this stack:");
            send(player, "&6/sf doctor identity repair rollback confirm");
        } catch (IOException | RuntimeException exception) {
            if (attemptedWrite && originalForRollback != null) {
                try {
                    player.getInventory().setItemInMainHand(originalForRollback);
                } catch (RuntimeException ignored) {
                    // The independently verified on-disk backup remains for operator recovery.
                }
            }
            send(player, "&cRepair aborted due to backup or item verification failure.");
            send(player, "&cOriginal saved bytes are retained. Check the main hand before retrying.");
        }
    }

    private static void rollback(Player player, long now) {
        Restoration restore = RESTORATIONS.remove(player.getUniqueId());
        if (restore == null || restore.expiresAt() < now) {
            send(player, "&cNo recent repair can be rolled back in this session.");
            return;
        }
        try {
            ItemStack current = player.getInventory().getItemInMainHand();
            if (!fingerprint(current).equals(restore.repairedHash())) {
                send(player, "&cThe repaired stack has changed or moved. Nothing was overwritten.");
                return;
            }
            ItemStack original = ItemStack.deserializeBytes(Files.readAllBytes(restore.backup()));
            if (!fingerprint(original).equals(restore.originalHash())) {
                send(player, "&cOriginal backup failed verification. No change made.");
                return;
            }
            player.getInventory().setItemInMainHand(original);
            if (!fingerprint(player.getInventory().getItemInMainHand()).equals(restore.originalHash())) {
                player.getInventory().setItemInMainHand(current);
                send(player, "&cRollback readback failed; repaired stack restored when possible.");
                send(player, "&cOriginal backup remains on disk.");
                return;
            }
            send(player, "&aOriginal historical item restored exactly from the saved backup.");
        } catch (IOException | RuntimeException exception) {
            send(player, "&cRollback failed. The original saved-item backup was not deleted.");
        }
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
            return template != null && freshNative != null && freshNative.isSimilar(template)
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
        Path directory = pluginFolder.resolve("recovery").resolve("identity");
        Files.createDirectories(directory);
        Path destination = directory.resolve(playerId + "-" + UUID.randomUUID() + ".bin");
        // CREATE_NEW refuses overwrites; force() makes the original item bytes
        // durable before the held inventory is even considered for mutation.
        try (FileChannel channel =
                FileChannel.open(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
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

    private record Restoration(Path backup, String originalHash, String repairedHash, long expiresAt) {}
}
