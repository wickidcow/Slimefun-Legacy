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

/**
 * Unexposed, read-only crash-replay journal of a proposed chest style update.
 *
 * <p>A durable proposal is NOT a write-ahead commit protocol: no physical
 * inventory or QuickShop code uses this class to apply, repair or restore
 * anything. The journal permits postrestart ORIGINAL/PROPOSED/DIVERGED
 * diagnosis, including unexpected partial changes, without any writes.
 */
final class DoctorContainerTransactionJournal {

    private static final int MAGIC = 0x53464C4A; // SFLJ
    private static final int VERSION = 1;
    private static final int MAX_FILE_BYTES = 4096;
    private static final int MAX_BLOCK_DATA = 512;
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

    record Inspection(State state, int slots, int changes, long originalItems, long proposedItems) {}

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
        var draft = DoctorContainerMigrationPreflight.prepare(
                pluginFolder, operator, receipt, binding, inventory, templates);
        validateDraft(draft, operator);
        UUID id = UUID.randomUUID();
        Journal journal = new Journal(
                id, operator, binding, receipt,
                draft.beforeSha256(), draft.proposedSha256(),
                draft.originalItems(), draft.proposedItems(), List.copyOf(draft.changedSlots()));
        byte[] body = encode(journal);
        byte[] digest = sha256(body);
        Path dir = directory(pluginFolder);
        Path destination = dir.resolve(operator + "-" + id + ".bin");
        try (FileChannel channel = FileChannel.open(
                destination, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS)) {
            writeAll(channel, body);
            writeAll(channel, digest);
            channel.force(true);
        }
        Journal reread = read(pluginFolder, operator, id);
        if (!journal.equals(reread)
                || !DoctorContainerEvidenceEnvelope.verify(
                                pluginFolder, operator, receipt, binding, inventory)
                        .unchanged()
                || !draft.beforeSha256().equals(
                        DoctorLiveItemUpdate.captureContainerPreimage(inventory).sha256())) {
            throw new IOException("Physical container changed while persisting proposed transaction");
        }
        return reread;
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
        Journal journal = read(pluginFolder, operator, journalId);
        if (!journal.binding().equals(binding)) {
            throw new IOException("Journal is bound to a different world, block, state or slot count");
        }
        // This reopens and validates the exact original all-slot envelope;
        // it is allowed to return false after a hypothetical migration.
        boolean original = DoctorContainerEvidenceEnvelope.verify(
                pluginFolder, operator, journal.originalReceipt(), binding, inventory).unchanged();
        String observed = DoctorLiveItemUpdate.captureContainerPreimage(inventory).sha256();
        State state = original && journal.beforeSha256().equals(observed)
                ? State.ORIGINAL
                : journal.afterSha256().equals(observed) ? State.PROPOSED : State.DIVERGED;
        return new Inspection(
                state, binding.slots(), journal.changedSlots().size(),
                journal.beforeItems(), journal.afterItems());
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

    private static byte[] encode(Journal record) throws IOException {
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
        }
        byte[] body = bytes.toByteArray();
        if (body.length + SHA256_BYTES > MAX_FILE_BYTES) {
            throw new IOException("Physical container transaction journal is oversized");
        }
        return body;
    }

    private static Journal read(Path pluginFolder, UUID owner, UUID journalId) throws IOException {
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
            if (in.available() != 0 || !validHash(before) || !validHash(after)
                    || before.equals(after) || beforeItems < 1 || beforeItems != afterItems) {
                throw new IOException("Physical container journal has invalid or trailing metadata");
            }
            return new Journal(
                    journalId, owner, binding, originalReceipt,
                    before, after, beforeItems, afterItems, List.copyOf(changes));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed physical container transaction journal binding", exception);
        }
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
