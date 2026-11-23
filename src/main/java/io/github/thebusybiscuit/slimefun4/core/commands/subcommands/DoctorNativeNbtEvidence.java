package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.bukkit.inventory.ItemStack;

/**
 * Read-only, bounded comparison of serialized native item metadata.
 *
 * <p>Only an explicit Doctor inspection uses this evidence. Sorting NBT compound
 * names does not remove or change tag types, values, or list ordering. This
 * does NOT determine Doctor repair eligibility or authorize modifying items.
 */
final class DoctorNativeNbtEvidence {
    private static final int MAX_COMPRESSED_BYTES = 256 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 1024 * 1024;
    private static final int MAX_DEPTH = 24;
    private static final int MAX_ENTRIES = 4096;

    private DoctorNativeNbtEvidence() {}

    enum Verdict {
        EXACT_NATIVE_BYTES,
        SAME_TYPED_NBT,
        DIFFERENT_TYPED_NBT,
        UNAVAILABLE
    }

    /**
     * Compare the actual serialized source bytes without deserializing and
     * reconstructing a potentially lossy detached copy. Only the root item
     * count is normalized by the bounded NBT reader for this diagnostic.
     *
     * <p>Do not infer that identical typed NBT allows replacing an item's
     * historical custom name, owner, soulbound state, charge or other data.
     */
    static Verdict compare(ItemStack historical, ItemStack current) {
        if (historical == null || current == null) {
            return Verdict.UNAVAILABLE;
        }
        if (historical.getType() != current.getType()) {
            return Verdict.DIFFERENT_TYPED_NBT;
        }
        try {
            return compareNativeBytes(historical.serializeAsBytes(), current.serializeAsBytes());
        } catch (RuntimeException exception) {
            return Verdict.UNAVAILABLE;
        }
    }

    /** Diagnostic-only result; never infer an authorized repair operation. */
    static Verdict compareNativeBytes(byte[] historical, byte[] current) {
        if (historical == null || current == null
                || historical.length == 0 || current.length == 0
                || historical.length > MAX_COMPRESSED_BYTES
                || current.length > MAX_COMPRESSED_BYTES) {
            return Verdict.UNAVAILABLE;
        }
        if (Arrays.equals(historical, current)) {
            return Verdict.EXACT_NATIVE_BYTES;
        }
        Boolean same = equivalent(historical, current);
        if (same == null) {
            return Verdict.UNAVAILABLE;
        }
        return same ? Verdict.SAME_TYPED_NBT : Verdict.DIFFERENT_TYPED_NBT;
    }

    /** Null means malformed, unsupported or over-budget native input. */
    static Boolean equivalent(byte[] left, byte[] right) {
        try {
            return Arrays.equals(canonical(left), canonical(right));
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static byte[] canonical(byte[] nativeBytes) throws IOException {
        if (nativeBytes == null || nativeBytes.length == 0
                || nativeBytes.length > MAX_COMPRESSED_BYTES) {
            throw new IOException("Untrusted or oversized native snapshot");
        }
        byte[] data;
        try (var compressed = new GZIPInputStream(new ByteArrayInputStream(nativeBytes))) {
            data = compressed.readNBytes(MAX_DECOMPRESSED_BYTES + 1);
        }
        if (data.length > MAX_DECOMPRESSED_BYTES) {
            throw new IOException("Inflated native payload exceeds read-only budget");
        }
        var input = new DataInputStream(new ByteArrayInputStream(data));
        var buffer = new ByteArrayOutputStream();
        var output = new DataOutputStream(buffer);
        int rootTag = input.readUnsignedByte();
        if (rootTag != 10) {
            throw new IOException("Expected root NBT compound tag");
        }
        output.writeByte(rootTag);
        output.writeUTF(input.readUTF());
        canonicalPayload(input, output, rootTag, 0);
        if (input.available() != 0 || buffer.size() > MAX_DECOMPRESSED_BYTES) {
            throw new IOException("Unexpected trailing native bytes");
        }
        return buffer.toByteArray();
    }

    private static int boundedLength(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IOException("NBT collection count exceeds limit");
        }
        return count;
    }

    private static void canonicalPayload(DataInputStream in, DataOutputStream out, int tag, int depth)
            throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting exceeds limit");
        }
        switch (tag) {
            case 1 -> out.writeByte(in.readByte());
            case 2 -> out.writeShort(in.readShort());
            case 3 -> out.writeInt(in.readInt());
            case 4 -> out.writeLong(in.readLong());
            // Copy raw IEEE float/double bits, including NaN payloads.
            case 5 -> out.writeInt(in.readInt());
            case 6 -> out.writeLong(in.readLong());
            case 7 -> {
                int length = in.readInt();
                if (length < 0 || length > MAX_DECOMPRESSED_BYTES) {
                    throw new IOException("Native byte array is oversized");
                }
                out.writeInt(length);
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                out.write(bytes);
            }
            case 8 -> out.writeUTF(in.readUTF());
            case 9 -> {
                int element = in.readUnsignedByte();
                int count = boundedLength(in);
                if (element < 1 || element > 12) {
                    if (element != 0 || count != 0) {
                        throw new IOException("Invalid typed NBT list");
                    }
                }
                out.writeByte(element);
                out.writeInt(count);
                for (int index = 0; index < count; index++) {
                    canonicalPayload(in, out, element, depth + 1);
                }
            }
            case 10 -> {
                Map<String, byte[]> entries = new TreeMap<>();
                int count = 0;
                while (true) {
                    int element = in.readUnsignedByte();
                    if (element == 0) {
                        break;
                    }
                    if (element < 1 || element > 12 || ++count > MAX_ENTRIES) {
                        throw new IOException("Invalid native compound tags");
                    }
                    String name = in.readUTF();
                    var entryBytes = new ByteArrayOutputStream();
                    var entry = new DataOutputStream(entryBytes);
                    entry.writeByte(element);
                    if (depth == 0 && (name.equals("count") || name.equals("Count"))
                            && (element == 1 || element == 3)) {
                        // Root ItemStack quantity is intentionally ignored.
                        // Preserve its original primitive tag type, but never
                        // ignore an owner-defined nested "count" key.
                        if (element == 1) {
                            in.readByte();
                            entry.writeByte(1);
                        } else {
                            in.readInt();
                            entry.writeInt(1);
                        }
                    } else {
                        canonicalPayload(in, entry, element, depth + 1);
                    }
                    if (entries.putIfAbsent(name, entryBytes.toByteArray()) != null) {
                        throw new IOException("Duplicate native compound tag name");
                    }
                }
                for (var entry : entries.entrySet()) {
                    byte[] payload = entry.getValue();
                    out.writeByte(payload[0]);
                    out.writeUTF(entry.getKey());
                    out.write(payload, 1, payload.length - 1);
                }
                out.writeByte(0);
            }
            case 11 -> {
                int count = boundedLength(in);
                out.writeInt(count);
                for (int index = 0; index < count; index++) {
                    out.writeInt(in.readInt());
                }
            }
            case 12 -> {
                int count = boundedLength(in);
                out.writeInt(count);
                for (int index = 0; index < count; index++) {
                    out.writeLong(in.readLong());
                }
            }
            default -> throw new IOException("Unsupported native tag type");
        }
    }
}
