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
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only full physical-container provenance. Creates an owner-scoped,
 * location-bound, byte-exact private evidence envelope; never applies or
 * restores items, changes a container, or accesses a QuickShop database.
 *
 * <p>Evidence is NOT a production container repair journal: it does not freeze
 * hoppers, authenticate historical origin, or prove transactions are safe.
 */
final class DoctorContainerEvidenceEnvelope {

    private static final int MAGIC = 0x53464C43; // SFLC
    private static final int VERSION = 1;
    private static final int MAX_SLOTS = 54;
    private static final int MAX_BLOCK_DATA = 512;
    private static final int MAX_ITEM_BYTES = 256 * 1024;
    private static final int MAX_NATIVE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = MAX_NATIVE_BYTES + 4096;
    private static final int SHA256_LENGTH = 32;

    private DoctorContainerEvidenceEnvelope() {}

    record Binding(UUID worldId, int x, int y, int z, String blockData, int slots) {
        Binding {
            if (worldId == null || blockData == null || blockData.isBlank()
                    || blockData.length() > MAX_BLOCK_DATA || slots < 1 || slots > MAX_SLOTS) {
                throw new IllegalArgumentException("Unsupported physical container binding");
            }
        }

        static Binding from(Location location, BlockState state, Inventory inventory) {
            if (location == null || location.getWorld() == null || state == null || inventory == null) {
                throw new IllegalArgumentException("Missing physical block location");
            }
            return new Binding(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    state.getBlockData().getAsString(),
                    inventory.getSize());
        }
    }

    record Receipt(UUID id, int slots) {}
    record Verification(int slots, boolean unchanged) {}
    record Assessment(
            int slots, int mismatched, int eligible, int boundedCandidates, int refused,
            int blankLoreOnly, int meaningfulText, int functionalMetadata, int unknownIds,
            List<Integer> candidateSlots) {}

    static Receipt capture(Path pluginFolder, UUID operator, Binding binding, Inventory inventory) throws IOException {
        validateArguments(operator, binding, inventory);
        DoctorLiveItemUpdate.ContainerPreimage before =
                DoctorLiveItemUpdate.captureContainerPreimage(inventory);
        byte[][] originalSlots = DoctorLiveItemUpdate.exactContainerSlots(inventory);
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Physical inventory changed before private container evidence backup");
        }
        byte[] payload = encode(operator, binding, originalSlots);
        byte[] digest = sha256(payload);
        Path directory = directory(pluginFolder);
        UUID id = UUID.randomUUID();
        Path target = directory.resolve(operator + "-" + id + ".bin");
        try (FileChannel channel = FileChannel.open(
                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeAll(channel, payload);
            writeAll(channel, digest);
            channel.force(true);
        }
        Saved saved = read(pluginFolder, operator, id);
        if (!saved.binding().equals(binding) || !sameSlots(originalSlots, saved.slots())) {
            throw new IOException("Private container evidence did not survive exact-byte readback");
        }
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Physical inventory changed while persisting private container evidence");
        }
        return new Receipt(id, binding.slots());
    }

    /**
     * Reopen the original evidence file, including after restart. A changed
     * live inventory is reported, never overwritten. The location, world,
     * block data and slot size must match the original source binding.
     */
    static Verification verify(
            Path pluginFolder, UUID operator, UUID receipt, Binding binding, Inventory inventory) throws IOException {
        validateArguments(operator, binding, inventory);
        Saved saved = read(pluginFolder, operator, receipt);
        if (!saved.binding().equals(binding)) {
            throw new IOException("Private container receipt belongs to another physical block");
        }
        var before = DoctorLiveItemUpdate.captureContainerPreimage(inventory);
        byte[][] observed = DoctorLiveItemUpdate.exactContainerSlots(inventory);
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Physical inventory changed while verifying original evidence");
        }
        return new Verification(binding.slots(), sameSlots(saved.slots(), observed));
    }

    /**
     * Read-only, receipt-bound assessment. A candidate is only a diagnostic
     * hint; the receipt is not a repair plan and no chest write method exists.
     *
     * <p>Reject changes anywhere in the saved container, including empty,
     * vanilla and functional-PDC stock. Retest the entire inventory after
     * resolving registered templates, so hoppers/Cargo cannot silently race
     * an otherwise matching historical item.
     */
    static Assessment assess(
            Path pluginFolder, UUID operator, UUID receipt, Binding binding,
            Inventory inventory, Function<ItemStack, ItemStack> templates) throws IOException {
        validateArguments(operator, binding, inventory);
        if (templates == null) {
            throw new IOException("Missing read-only current-template resolver");
        }
        Saved saved = read(pluginFolder, operator, receipt);
        if (!saved.binding().equals(binding)) {
            throw new IOException("Private original receipt does not belong to this physical container");
        }
        var before = DoctorLiveItemUpdate.captureContainerPreimage(inventory);
        byte[][] observed = DoctorLiveItemUpdate.exactContainerSlots(inventory);
        if (!sameSlots(saved.slots(), observed)
                || !DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Physical container no longer matches its private original receipt");
        }
        DoctorLiveItemUpdate.Preview preview =
                DoctorLiveItemUpdate.preview(inventory, templates, binding.slots());
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Physical container changed during read-only identity assessment");
        }
        return new Assessment(
                binding.slots(),
                preview.mismatched(),
                preview.eligible(),
                preview.entries().size(),
                preview.refused(),
                preview.blankLoreOnly(),
                preview.meaningfulText(),
                preview.functionalMetadata(),
                preview.unknownIds(),
                preview.entries().stream().map(DoctorLiveItemUpdate.Entry::slot).toList());
    }

    private static void validateArguments(UUID operator, Binding binding, Inventory inventory) throws IOException {
        if (operator == null || binding == null || inventory == null
                || inventory.getSize() != binding.slots()
                || inventory.getStorageContents().length != binding.slots()) {
            throw new IOException("Invalid private physical container evidence request");
        }
    }

    private static byte[] encode(UUID owner, Binding binding, byte[][] slots) throws IOException {
        if (slots.length != binding.slots()) {
            throw new IOException("Container slot size differs from location binding");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            writeUuid(out, owner);
            writeUuid(out, binding.worldId());
            out.writeInt(binding.x());
            out.writeInt(binding.y());
            out.writeInt(binding.z());
            out.writeUTF(binding.blockData());
            out.writeInt(slots.length);
            int total = 0;
            for (byte[] item : slots) {
                if (item == null) {
                    out.writeInt(-1);
                } else {
                    if (item.length == 0 || item.length > MAX_ITEM_BYTES
                            || total > MAX_NATIVE_BYTES - item.length) {
                        throw new IOException("Oversized private container original");
                    }
                    total += item.length;
                    out.writeInt(item.length);
                    out.write(item);
                }
            }
        }
        byte[] result = buffer.toByteArray();
        if (result.length > MAX_FILE_BYTES - SHA256_LENGTH) {
            throw new IOException("Private container envelope too large");
        }
        return result;
    }

    private static Saved read(Path pluginFolder, UUID owner, UUID receipt) throws IOException {
        if (owner == null || receipt == null) {
            throw new IOException("Invalid private container owner or receipt");
        }
        Path target = directory(pluginFolder).resolve(owner + "-" + receipt + ".bin");
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.size(target) <= SHA256_LENGTH
                || Files.size(target) > MAX_FILE_BYTES) {
            throw new IOException("Private container original evidence is missing or oversized");
        }
        byte[] bytes = Files.readAllBytes(target);
        int payloadLength = bytes.length - SHA256_LENGTH;
        if (payloadLength <= 0
                || !MessageDigest.isEqual(
                        Arrays.copyOfRange(bytes, payloadLength, bytes.length),
                        sha256(Arrays.copyOf(bytes, payloadLength)))) {
            throw new IOException("Private container evidence digest mismatch");
        }
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes, 0, payloadLength))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION
                    || !owner.equals(readUuid(in))) {
                throw new IOException("Private container envelope version or owner mismatch");
            }
            UUID world = readUuid(in);
            int x = in.readInt();
            int y = in.readInt();
            int z = in.readInt();
            String block = in.readUTF();
            int count = in.readInt();
            Binding binding = new Binding(world, x, y, z, block, count);
            byte[][] slots = new byte[count][];
            int total = 0;
            for (int slot = 0; slot < count; slot++) {
                int length = in.readInt();
                if (length == -1) {
                    continue;
                }
                if (length < 1 || length > MAX_ITEM_BYTES || total > MAX_NATIVE_BYTES - length) {
                    throw new IOException("Private container evidence has an invalid stack length");
                }
                total += length;
                byte[] original = new byte[length];
                in.readFully(original);
                ItemStack item;
                try {
                    item = ItemStack.deserializeBytes(original);
                    if (item == null || item.getType().isAir()
                            || !Arrays.equals(original, item.serializeAsBytes())) {
                        throw new IOException("Native private container stack changed on readback");
                    }
                } catch (RuntimeException exception) {
                    throw new IOException("Native private container stack cannot be recovered", exception);
                }
                slots[slot] = original;
            }
            if (in.available() != 0) {
                throw new IOException("Private container evidence has trailing fields");
            }
            return new Saved(binding, slots);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid private container provenance", exception);
        }
    }

    private static Path directory(Path pluginFolder) throws IOException {
        if (pluginFolder == null) {
            throw new IOException("Missing plugin recovery directory");
        }
        Path recovery = pluginFolder.resolve("recovery");
        Path containers = recovery.resolve("containers");
        if (!Files.isDirectory(pluginFolder, LinkOption.NOFOLLOW_LINKS)
                || (Files.exists(recovery, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS))
                || (Files.exists(containers, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(containers, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Container evidence recovery path must not be a symlink");
        }
        Files.createDirectories(containers);
        if (!Files.isDirectory(recovery, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(containers, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Container evidence recovery directory was replaced");
        }
        return containers;
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeAll(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer source = ByteBuffer.wrap(bytes);
        while (source.hasRemaining()) {
            channel.write(source);
        }
    }

    private static byte[] sha256(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable", exception);
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

    private record Saved(Binding binding, byte[][] slots) {}
}
