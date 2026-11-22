package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only transaction preflight for a single physically bound container.
 *
 * <p>It proves only whether a private original receipt permits a bounded,
 * presentation-only proposed after-image. No public command uses the result
 * to change a chest, and this class deliberately does not write, rollback,
 * mutate, or expose item bytes. It is not a durable transaction journal.
 */
final class DoctorContainerMigrationPreflight {

    private static final int MAX_CANDIDATES = 8;
    private static final int MAX_NATIVE_BYTES = 2 * 1024 * 1024;

    private DoctorContainerMigrationPreflight() {}

    record Draft(
            UUID originalReceipt,
            DoctorContainerEvidenceEnvelope.Binding binding,
            String beforeSha256,
            String proposedSha256,
            int changedStacks,
            long originalItems,
            long proposedItems,
            List<Integer> changedSlots) {}

    static Draft prepare(
            Path pluginFolder,
            UUID operator,
            UUID receipt,
            DoctorContainerEvidenceEnvelope.Binding binding,
            Inventory inventory,
            Function<ItemStack, ItemStack> templates) throws IOException {
        var assessment = DoctorContainerEvidenceEnvelope.assess(
                pluginFolder, operator, receipt, binding, inventory, templates);
        if (assessment.eligible() < 1
                || assessment.eligible() > MAX_CANDIDATES
                || assessment.eligible() != assessment.boundedCandidates()) {
            throw new IOException("No complete bounded physical-container migration candidate set");
        }
        var before = DoctorLiveItemUpdate.captureContainerPreimage(inventory);
        byte[][] original = DoctorLiveItemUpdate.exactContainerSlots(inventory);
        if (!before.sha256().equals(hashSlots(original))) {
            throw new IOException("Original physical-container hash changed before preflight");
        }
        var preview = DoctorLiveItemUpdate.preview(inventory, templates, binding.slots());
        if (preview.eligible() != assessment.eligible()
                || preview.entries().size() != assessment.eligible()
                || !preview.entries().stream().map(DoctorLiveItemUpdate.Entry::slot)
                        .toList().equals(assessment.candidateSlots())) {
            throw new IOException("Physical-container candidate selection changed during preflight");
        }

        byte[][] proposed = original.clone();
        List<Integer> changed = new ArrayList<>();
        long beforeTotal = countItems(original);
        for (var entry : preview.entries()) {
            int slot = entry.slot();
            if (slot < 0 || slot >= proposed.length || original[slot] == null) {
                throw new IOException("Invalid physical-container candidate slot");
            }
            try {
                ItemStack item = ItemStack.deserializeBytes(original[slot]);
                ItemStack reference = templates.apply(item);
                if (reference == null
                        || !entry.oldHash().equals(DoctorHistoricalStackRepair.fingerprint(item))
                        || !entry.templateHash().equals(DoctorHistoricalStackRepair.fingerprint(reference))) {
                    throw new IOException("Physical-container source identity or template changed");
                }
                ItemStack replacement = DoctorHistoricalStackRepair.prepare(item, reference, reference);
                if (replacement == null || replacement.getAmount() != item.getAmount()
                        || !entry.replacementHash()
                                .equals(DoctorHistoricalStackRepair.fingerprint(replacement))
                        || !DoctorHistoricalStackRepair.matchesSavedBackup(
                                item, replacement, reference, reference)) {
                    throw new IOException("Physical-container candidate failed strict historical item proof");
                }
                byte[] afterBytes = replacement.serializeAsBytes();
                ItemStack roundTrip = ItemStack.deserializeBytes(afterBytes);
                if (afterBytes.length > 256 * 1024
                        || !Arrays.equals(afterBytes, roundTrip.serializeAsBytes())
                        || Arrays.equals(original[slot], afterBytes)) {
                    throw new IOException("Physical-container proposed after-image is unsafe");
                }
                proposed[slot] = afterBytes;
                changed.add(slot);
            } catch (RuntimeException exception) {
                throw new IOException("Physical-container template or native item cannot be prepared", exception);
            }
        }
        long afterTotal = countItems(proposed);
        if (beforeTotal != afterTotal || changed.size() != assessment.eligible()) {
            throw new IOException("Physical-container proposed item quantity would change");
        }
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)
                || !DoctorContainerEvidenceEnvelope.verify(
                                pluginFolder, operator, receipt, binding, inventory)
                        .unchanged()) {
            throw new IOException("Physical-container original changed while building draft");
        }
        return new Draft(receipt, binding, before.sha256(), hashSlots(proposed),
                changed.size(), beforeTotal, afterTotal, List.copyOf(changed));
    }

    /**
     * Recheck only the expected after-image at the same exact block location.
     * A stale or partially written after-state is false; no rollback occurs.
     */
    static boolean matchesProposedAfter(
            Draft draft, DoctorContainerEvidenceEnvelope.Binding binding, Inventory inventory) throws IOException {
        if (draft == null || !draft.binding().equals(binding)) {
            throw new IOException("Physical-container draft is not bound to this block");
        }
        return DoctorLiveItemUpdate.captureContainerPreimage(inventory).sha256().equals(draft.proposedSha256());
    }

    private static long countItems(byte[][] slots) throws IOException {
        long count = 0;
        int bytes = 0;
        try {
            for (byte[] slot : slots) {
                if (slot == null) {
                    continue;
                }
                if (slot.length == 0 || slot.length > 256 * 1024
                        || bytes > MAX_NATIVE_BYTES - slot.length) {
                    throw new IOException("Physical-container stock exceeded native item safety budget");
                }
                bytes += slot.length;
                ItemStack item = ItemStack.deserializeBytes(slot);
                if (item == null || item.getType().isAir() || item.getAmount() < 1
                        || !Arrays.equals(slot, item.serializeAsBytes())) {
                    throw new IOException("Physical-container native item count or round-trip is unsafe");
                }
                count += item.getAmount();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Physical-container stock could not be counted safely", exception);
        }
        return count;
    }

    private static String hashSlots(byte[][] slots) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(slots.length).array());
            for (int slot = 0; slot < slots.length; slot++) {
                byte[] bytes = slots[slot];
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(slot).array());
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes == null ? -1 : bytes.length).array());
                if (bytes != null) {
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable for physical-container after-image", exception);
        }
    }
}
