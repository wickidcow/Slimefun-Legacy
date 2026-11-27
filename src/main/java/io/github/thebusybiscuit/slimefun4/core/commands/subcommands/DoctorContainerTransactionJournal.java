package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Crash-replay journal of a proposed chest style update, with an internal fixture apply engine.
 *
 * <p>A durable proposal is NOT a write authorization. No live command or
 * QuickShop code calls the internal apply engine. The journal permits
 * postrestart ORIGINAL/PROPOSED/DIVERGED diagnosis without writes.
 */
final class DoctorContainerTransactionJournal {

    private static final int MAGIC = 0x53464C4A; // SFLJ
    private static final int VERSION = 2;
    private static final int MAX_NATIVE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = MAX_NATIVE_BYTES + 4096;
    private static final int MAX_STACK_BYTES = 256 * 1024;
    private static final int MAX_CHANGES = 8;
    private static final int SHA256_BYTES = 32;

    private DoctorContainerTransactionJournal() {}

    enum State {
        ORIGINAL,
        PROPOSED,
        DIVERGED
    }

    record Journal(
            UUID id, UUID owner, DoctorContainerEvidenceEnvelope.Binding binding,
            UUID originalReceipt, String beforeSha256, String afterSha256,
            long beforeItems, long afterItems, List<Integer> changedSlots) {}

    /**
     * All counters describe exact native-byte comparisons. A candidate in its
     * original form is NOT authorization to resume a partial transaction.
     * Unexpected includes any change outside the planned candidate positions,
     * including vanilla, empty, owner-PDC and machine metadata changes.
     */
    record Inspection(
            State state, int slots, int changes, long originalItems, long proposedItems,
            int appliedCandidates, int originalCandidates, int unexpectedSlots,
            boolean expectedOnlyPartial, boolean attemptBlocked) {}

    private record Stored(Journal journal, byte[][] proposedSlots) {}

    /**
     * Creates a durable, immutable diagnostic proposal. Complete original
     * evidence and all-source stock must still match both before and AFTER
     * the journal has been independently reopened from disk.
     */
    static Journal begin(
            Path pluginFolder, UUID operator, UUID receipt,
            DoctorContainerEvidenceEnvelope.Binding binding, Inventory inventory,
            java.util.function.Function<org.bukkit.inventory.ItemStack, org.bukkit.inventory.ItemStack> templates)
            throws IOException {
        var prepared = DoctorContainerMigrationPreflight.prepareSnapshot(
                pluginFolder, operator, receipt, binding, inventory, templates);
        var draft = prepared.draft();
        validateDraft(draft, operator);
        UUID id = UUID.randomUUID();
        Journal journal = new Journal(
                id, operator, binding, receipt,
                draft.beforeSha256(), draft.proposedSha256(),
                draft.originalItems(), draft.proposedItems(), List.copyOf(draft.changedSlots()));
        byte[] body = encode(journal, prepared.proposedSlots());
        byte[] digest = sha256(body);
        Path dir = directory(pluginFolder);
        Path destination = dir.resolve(operator + "-" + id + ".bin");
        try (FileChannel channel = FileChannel.open(
                destination, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS)) {
            writeAll(channel, body);
            writeAll(channel, digest);
            channel.force(true);
        }
        Stored reopened = read(pluginFolder, operator, id);
        verifySourceAndProposed(pluginFolder, reopened);
        if (!journal.equals(reopened.journal())
                || !sameSlots(prepared.proposedSlots(), reopened.proposedSlots())
                || !DoctorContainerEvidenceEnvelope.verify(
                                pluginFolder, operator, receipt, binding, inventory)
                        .unchanged()
                || !draft.beforeSha256().equals(
                        DoctorLiveItemUpdate.captureContainerPreimage(inventory).sha256())) {
            throw new IOException("Physical container changed while persisting proposed transaction");
        }
        return reopened.journal();
    }

    /**
     * Validate disk evidence and compare exact, complete present inventory
     * identity. DIVERGED is a diagnosis, NEVER an instruction to restore.
     * The underlying private original receipt must also be valid and belong
     * to this same operator/world/block before any status is returned.
     */
    static Inspection inspect(
            Path pluginFolder, UUID operator, UUID journalId,
            DoctorContainerEvidenceEnvelope.Binding binding, Inventory inventory) throws IOException {
        if (binding == null || inventory == null || inventory.getSize() != binding.slots()) {
            throw new IOException("Invalid physical container replay binding");
        }
        Stored stored = read(pluginFolder, operator, journalId);
        Journal journal = stored.journal();
        if (!journal.binding().equals(binding)) {
            throw new IOException("Journal is bound to a different world, block, state or slot count");
        }
        byte[][] originalSlots = verifySourceAndProposed(pluginFolder, stored);
        // This reopens and validates the exact original all-slot envelope;
        // it is allowed to return false after a hypothetical migration.
        boolean original = DoctorContainerEvidenceEnvelope.verify(
                pluginFolder, operator, journal.originalReceipt(), binding, inventory).unchanged();
        var before = DoctorLiveItemUpdate.captureContainerPreimage(inventory);
        byte[][] observedSlots = DoctorLiveItemUpdate.exactContainerSlots(inventory);
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Physical container changed during journal read-only replay");
        }
        String observed = before.sha256();
        State state = original && journal.beforeSha256().equals(observed)
                ? State.ORIGINAL
                : journal.afterSha256().equals(observed)
                        && sameSlots(stored.proposedSlots(), observedSlots)
                        ? State.PROPOSED
                        : State.DIVERGED;
        int applied = 0;
        int pending = 0;
        int unexpected = 0;
        for (int slot = 0; slot < observedSlots.length; slot++) {
            byte[] observedSlot = observedSlots[slot];
            if (journal.changedSlots().contains(slot)) {
                if (Arrays.equals(observedSlot, originalSlots[slot])) {
                    pending++;
                } else if (Arrays.equals(observedSlot, stored.proposedSlots()[slot])) {
                    applied++;
                } else {
                    unexpected++;
                }
            } else if (!Arrays.equals(observedSlot, originalSlots[slot])) {
                unexpected++;
            }
        }
        boolean onlyExpectedPartial =
                state == State.DIVERGED && unexpected == 0 && applied > 0 && pending > 0;
        return new Inspection(
                state, binding.slots(), journal.changedSlots().size(),
                journal.beforeItems(), journal.afterItems(),
                applied, pending, unexpected, onlyExpectedPartial,
                !Files.notExists(attemptMarker(directory(pluginFolder), operator, journalId), LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Re-prove an immutable proposal against the current registry and exact
     * original stock. A successful check is not a write authorization.
     */
    static Journal revalidate(
            Path pluginFolder, UUID operator, UUID journalId,
            DoctorContainerEvidenceEnvelope.Binding binding, Inventory inventory,
            java.util.function.Function<ItemStack, ItemStack> templates) throws IOException {
        if (binding == null || inventory == null || inventory.getSize() != binding.slots()) {
            throw new IOException("Invalid physical container revalidation binding");
        }
        Stored stored = read(pluginFolder, operator, journalId);
        Journal journal = stored.journal();
        if (!journal.binding().equals(binding)) {
            throw new IOException("Journal is bound to a different physical container");
        }
        verifySourceAndProposed(pluginFolder, stored);
        var current = DoctorContainerMigrationPreflight.prepareSnapshot(
                pluginFolder, operator, journal.originalReceipt(), binding, inventory, templates);
        var draft = current.draft();
        if (!journal.beforeSha256().equals(draft.beforeSha256())
                || !journal.afterSha256().equals(draft.proposedSha256())
                || journal.beforeItems() != draft.originalItems()
                || journal.afterItems() != draft.proposedItems()
                || !journal.changedSlots().equals(draft.changedSlots())
                || !sameSlots(stored.proposedSlots(), current.proposedSlots())) {
            throw new IOException("Current registered templates no longer reproduce the saved proposal");
        }
        if (!DoctorContainerEvidenceEnvelope.verify(
                        pluginFolder, operator, journal.originalReceipt(), binding, inventory)
                .unchanged()) {
            throw new IOException("Physical container changed during proposal revalidation");
        }
        return journal;
    }

    /**
     * Internal single-use execution gate. Once an attempt marker exists,
     * even ORIGINAL stock is not permission to repeat the same proposal.
     * A failed or incomplete marker is deliberately never removed.
     *
     * <p>This persists intent, not a world-save or successful commit record.
     * No live command calls this method; platform-specific directory flush
     * and native interruption/restart behavior must be verified first.
     */
    static Journal applyOnce(
            Path pluginFolder, UUID operator, UUID journalId,
            DoctorContainerEvidenceEnvelope.Binding binding, Inventory inventory,
            java.util.function.Function<ItemStack, ItemStack> templates) throws IOException {
        Path dir = directory(pluginFolder);
        Path marker = attemptMarker(dir, operator, journalId);
        // Refuse prior or indeterminate attempts before inventory and template
        // work. Exclusive creation below remains the actual race-safe gate.
        if (!Files.notExists(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Private container execution intent already exists or cannot be checked");
        }
        Journal validated = revalidate(pluginFolder, operator, journalId, binding, inventory, templates);
        byte[] intent = ("SFL_CONTAINER_ATTEMPT_V1\n" + operator + "\n" + journalId + "\n"
                + validated.originalReceipt() + "\n" + validated.beforeSha256() + "\n"
                + validated.afterSha256() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Exclusive creation is the gate, not parsing a previous marker.
        // Any existing file, symlink or truncated intent refuses replay.
        try (FileChannel channel = FileChannel.open(
                marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeAll(channel, intent);
            channel.force(true);
        }
        // Fail closed on filesystems/providers that cannot flush a directory.
        // Intent must be persisted before any inventory setter is invoked.
        try (FileChannel directoryChannel = FileChannel.open(
                dir, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            directoryChannel.force(true);
        }
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) != intent.length
                || !Arrays.equals(intent, Files.readAllBytes(marker))) {
            throw new IOException("Private container execution intent did not survive exact readback");
        }
        // The underlying engine independently re-proves all source and
        // current-template identities after the intent has been persisted.
        return applyVerifiedProposal(pluginFolder, operator, journalId, binding, inventory, templates);
    }

    /**
     * Internal apply engine for controlled fixtures only until native region,
     * interruption and restart tests permit an explicit operator command.
     * A caller must already own the inventory's region and exclude shops,
     * machines and unsupported physical layouts. Never resumes partial stock.
     */
    static Journal applyVerifiedProposal(
            Path pluginFolder, UUID operator, UUID journalId,
            DoctorContainerEvidenceEnvelope.Binding binding, Inventory inventory,
            java.util.function.Function<ItemStack, ItemStack> templates) throws IOException {
        Journal validated = revalidate(pluginFolder, operator, journalId, binding, inventory, templates);
        Stored stored = read(pluginFolder, operator, journalId);
        if (!validated.equals(stored.journal())) {
            throw new IOException("Private proposal changed after current-template validation");
        }
        byte[][] original = verifySourceAndProposed(pluginFolder, stored);
        byte[][] proposed = stored.proposedSlots();
        byte[][] expected = original.clone();
        requireExactStock(inventory, expected);
        List<Integer> attempted = new java.util.ArrayList<>();
        try {
            for (int slot : validated.changedSlots()) {
                ItemStack replacement = verifyCurrentCandidate(original[slot], proposed[slot], templates);
                // All registry callbacks and deserialization have finished.
                // Refuse ANY changed slot before a single targeted write.
                requireExactStock(inventory, expected);
                attempted.add(slot);
                inventory.setItem(slot, replacement);
                expected[slot] = proposed[slot];
                requireExactStock(inventory, expected);
            }
            // A later callback may have changed the registered identity of
            // an earlier candidate. Do not report a stale proposal as applied.
            for (int slot : validated.changedSlots()) {
                verifyCurrentCandidate(original[slot], proposed[slot], templates);
            }
            requireExactStock(inventory, proposed);
        } catch (IOException | RuntimeException failure) {
            for (int i = attempted.size() - 1; i >= 0; i--) {
                int slot = attempted.get(i);
                try {
                    ItemStack restore = ItemStack.deserializeBytes(original[slot]);
                    ItemStack observed = inventory.getItem(slot);
                    // Never overwrite a foreign replacement or changed count.
                    if (observed != null && Arrays.equals(proposed[slot], observed.serializeAsBytes())) {
                        inventory.setItem(slot, restore);
                    }
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            // Neither the original receipt nor the proposal is deleted.
            // A failed rollback is not reported as a successful restoration.
            throw new IOException("Physical container apply interrupted; inspect retained journal and original", failure);
        }
        return validated;
    }

    private static ItemStack verifyCurrentCandidate(
            byte[] original, byte[] proposed, java.util.function.Function<ItemStack, ItemStack> templates)
            throws IOException {
        if (original == null || proposed == null) {
            throw new IOException("Container proposal cannot create or remove a stack");
        }
        ItemStack source = ItemStack.deserializeBytes(original);
        ItemStack reference = templates.apply(source);
        ItemStack replacement = reference == null
                ? null
                : DoctorHistoricalStackRepair.prepare(source, reference, reference);
        if (!Arrays.equals(original, source.serializeAsBytes())
                || replacement == null
                || !Arrays.equals(proposed, replacement.serializeAsBytes())
                || !DoctorHistoricalStackRepair.matchesSavedBackup(source, replacement, reference, reference)) {
            throw new IOException("Current registered template no longer proves the proposed item");
        }
        return replacement;
    }

    private static void requireExactStock(Inventory inventory, byte[][] expected) throws IOException {
        if (!sameSlots(expected, DoctorLiveItemUpdate.exactContainerSlots(inventory))
                || !sameSlots(expected, DoctorLiveItemUpdate.exactContainerSlots(inventory))) {
            throw new IOException("Physical container stock diverged from exact expected slots");
        }
    }

    /**
     * A valid checksum alone does not bind this transaction to its source.
     * Require the owner-scoped original receipt to still encode the exact
     * before-hash and item total saved when the proposal was created. Every
     * noncandidate byte must remain identical in the proposed after-image.
     *
     * <p>No inventory mutation occurs here. This explicitly refuses swapped
     * valid receipts, cross-location original files, altered unchanged slots,
     * and journal metadata inconsistent with its independently saved source.
     */
    private static byte[][] verifySourceAndProposed(Path pluginFolder, Stored stored) throws IOException {
        Journal journal = stored.journal();
        byte[][] original = DoctorContainerEvidenceEnvelope.openVerifiedOriginal(
                pluginFolder, journal.owner(), journal.originalReceipt(), journal.binding());
        byte[][] after = stored.proposedSlots();
        if (original.length != after.length
                || !DoctorContainerMigrationPreflight.hashSlots(original).equals(journal.beforeSha256())
                || DoctorContainerMigrationPreflight.countItems(original) != journal.beforeItems()) {
            throw new IOException("Private source receipt does not match this journal's exact original");
        }
        int changed = 0;
        for (int slot = 0; slot < original.length; slot++) {
            boolean differs = !Arrays.equals(original[slot], after[slot]);
            boolean listed = journal.changedSlots().contains(slot);
            if (differs != listed) {
                throw new IOException("Container journal changed an unexpected original slot");
            }
            if (differs) {
                changed++;
            }
        }
        if (changed != journal.changedSlots().size()) {
            throw new IOException("Physical container journal candidate-count mismatch");
        }
        return original;
    }

    private static void validateDraft(DoctorContainerMigrationPreflight.Draft draft, UUID owner)
            throws IOException {
        if (owner == null || draft == null || draft.binding() == null || draft.originalReceipt() == null
                || draft.changedSlots().isEmpty() || draft.changedSlots().size() > MAX_CHANGES
                || draft.changedStacks() != draft.changedSlots().size()
                || draft.originalItems() < 1 || draft.originalItems() != draft.proposedItems()
                || !validHash(draft.beforeSha256()) || !validHash(draft.proposedSha256())
                || draft.beforeSha256().equals(draft.proposedSha256())) {
            throw new IOException("Invalid or unconserved private container transaction draft");
        }
        int prior = -1;
        for (int index : draft.changedSlots()) {
            if (index <= prior || index < 0 || index >= draft.binding().slots()) {
                throw new IOException("Unsorted or invalid physical container candidate slot");
            }
            prior = index;
        }
    }

    private static byte[] encode(Journal record, byte[][] proposed) throws IOException {
        if (proposed == null || proposed.length != record.binding().slots()
                || !DoctorContainerMigrationPreflight.hashSlots(proposed).equals(record.afterSha256())
                || DoctorContainerMigrationPreflight.countItems(proposed) != record.afterItems()) {
            throw new IOException("Invalid or unconserved proposed container witness");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            writeUuid(out, record.id());
            writeUuid(out, record.owner());
            var b = record.binding();
            writeUuid(out, b.worldId());
            out.writeInt(b.x());
            out.writeInt(b.y());
            out.writeInt(b.z());
            out.writeUTF(b.blockData());
            out.writeInt(b.slots());
            writeUuid(out, record.originalReceipt());
            out.writeUTF(record.beforeSha256());
            out.writeUTF(record.afterSha256());
            out.writeLong(record.beforeItems());
            out.writeLong(record.afterItems());
            out.writeInt(record.changedSlots().size());
            for (int slot : record.changedSlots()) {
                out.writeInt(slot);
            }
            out.writeInt(proposed.length);
            int consumed = 0;
            for (byte[] bytesAtSlot : proposed) {
                if (bytesAtSlot == null) {
                    out.writeInt(-1);
                } else {
                    if (bytesAtSlot.length < 1 || bytesAtSlot.length > MAX_STACK_BYTES
                            || consumed > MAX_NATIVE_BYTES - bytesAtSlot.length) {
                        throw new IOException("Physical container witness exceeded native-byte budget");
                    }
                    consumed += bytesAtSlot.length;
                    out.writeInt(bytesAtSlot.length);
                    out.write(bytesAtSlot);
                }
            }
        }
        byte[] body = bytes.toByteArray();
        if (body.length + SHA256_BYTES > MAX_FILE_BYTES) {
            throw new IOException("Physical container transaction journal is oversized");
        }
        return body;
    }

    private static Stored read(Path pluginFolder, UUID owner, UUID journalId) throws IOException {
        if (owner == null || journalId == null) {
            throw new IOException("Missing private journal owner or ID");
        }
        Path file = directory(pluginFolder).resolve(owner + "-" + journalId + ".bin");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) <= SHA256_BYTES || Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("Missing or oversized physical container transaction journal");
        }
        byte[] all = Files.readAllBytes(file);
        byte[] body = Arrays.copyOf(all, all.length - SHA256_BYTES);
        if (!MessageDigest.isEqual(
                Arrays.copyOfRange(all, body.length, all.length), sha256(body))) {
            throw new IOException("Physical container transaction journal integrity mismatch");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION
                    || !journalId.equals(readUuid(in)) || !owner.equals(readUuid(in))) {
                throw new IOException("Physical container transaction journal version or owner mismatch");
            }
            UUID world = readUuid(in);
            int x = in.readInt();
            int y = in.readInt();
            int z = in.readInt();
            String block = in.readUTF();
            int slotCount = in.readInt();
            var binding = new DoctorContainerEvidenceEnvelope.Binding(world, x, y, z, block, slotCount);
            UUID originalReceipt = readUuid(in);
            String before = in.readUTF();
            String after = in.readUTF();
            long beforeItems = in.readLong();
            long afterItems = in.readLong();
            int n = in.readInt();
            if (n < 1 || n > MAX_CHANGES) {
                throw new IOException("Physical container journal has unsupported candidate count");
            }
            int prior = -1;
            java.util.ArrayList<Integer> changes = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                int slot = in.readInt();
                if (slot <= prior || slot < 0 || slot >= slotCount) {
                    throw new IOException("Physical container journal has invalid candidate slot");
                }
                changes.add(slot);
                prior = slot;
            }
            if (!validHash(before) || !validHash(after)
                    || before.equals(after) || beforeItems < 1 || beforeItems != afterItems) {
                throw new IOException("Physical container journal has invalid stock or hash metadata");
            }
            int witnessCount = in.readInt();
            if (witnessCount != slotCount) {
                throw new IOException("Physical container journal witness slot count mismatch");
            }
            byte[][] proposed = new byte[witnessCount][];
            int consumed = 0;
            for (int slot = 0; slot < witnessCount; slot++) {
                int length = in.readInt();
                if (length == -1) {
                    continue;
                }
                if (length < 1 || length > MAX_STACK_BYTES
                        || consumed > MAX_NATIVE_BYTES - length) {
                    throw new IOException("Physical container journal witness has invalid item length");
                }
                consumed += length;
                byte[] nativeBytes = new byte[length];
                in.readFully(nativeBytes);
                try {
                    ItemStack restored = ItemStack.deserializeBytes(nativeBytes);
                    if (restored == null || restored.getType().isAir()
                            || !Arrays.equals(nativeBytes, restored.serializeAsBytes())) {
                        throw new IOException("Physical container journal witness is not native-byte exact");
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("Physical container journal witness native metadata failed", exception);
                }
                proposed[slot] = nativeBytes;
            }
            if (in.available() != 0
                    || !DoctorContainerMigrationPreflight.hashSlots(proposed).equals(after)
                    || DoctorContainerMigrationPreflight.countItems(proposed) != afterItems) {
                throw new IOException("Physical container journal after-image is not byte-exact/conserved");
            }
            return new Stored(
                    new Journal(journalId, owner, binding, originalReceipt,
                            before, after, beforeItems, afterItems, List.copyOf(changes)),
                    proposed);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed physical container transaction journal binding", exception);
        }
    }

    private static boolean sameSlots(byte[][] left, byte[][] right) {
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

    private static boolean validHash(String value) {
        if (value == null || value.length() != SHA256_BYTES * 2) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0
                    || Character.isUpperCase(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Path attemptMarker(Path directory, UUID operator, UUID journalId) throws IOException {
        if (operator == null || journalId == null) {
            throw new IOException("Missing private container execution identity");
        }
        return directory.resolve(operator + "-" + journalId + ".attempt");
    }

    private static Path directory(Path pluginFolder) throws IOException {
        if (pluginFolder == null
                || !Files.isDirectory(pluginFolder, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Physical container journal root is not a real directory");
        }
        Path recovery = pluginFolder.resolve("recovery");
        Path journalDir = recovery.resolve("container-journals");
        if ((Files.exists(recovery, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS))
                || (Files.exists(journalDir, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(journalDir, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Physical container journal path is not an ordinary directory");
        }
        Files.createDirectories(journalDir);
        if (!Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(journalDir, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Physical container transaction journal directory was replaced");
        }
        return journalDir;
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeAll(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static byte[] sha256(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable for physical container journal", exception);
        }
    }
}
