package com.wickidcow.doctorfixture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * TEST-ONLY structural evidence for native ItemStack snapshots. Canonicalizes
 * NBT compound key order while preserving every tag type, numeric bit pattern,
 * list order and value. This NEVER edits a Bukkit ItemStack or authorizes a
 * Doctor item migration; failures are represented as unavailable evidence.
 */
final class NativeNbtStructuralEvidence {
    private static final int MAX_COMPRESSED_BYTES = 512 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DEPTH = 48;
    private static final int MAX_ENTRIES = 8192;

    private NativeNbtStructuralEvidence() {}

    /**
     * Synthetic policy regression: compound field ordering is irrelevant,
     * but typed value changes, including int versus long, MUST remain distinct.
     */
    static void selfTest() {
        try {
            byte[] first = synthetic(false, false);
            byte[] reordered = synthetic(true, false);
            byte[] changedType = synthetic(true, true);
            if (!Boolean.TRUE.equals(equivalent(first, reordered))
                    || !Boolean.FALSE.equals(equivalent(first, changedType))
                    || equivalent(first, Arrays.copyOf(first, first.length - 2)) != null) {
                throw new IllegalStateException("Typed NBT policy self-test mismatch");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Synthetic typed NBT policy self-test failed", failure);
        }
    }

    private static byte[] synthetic(boolean reverse, boolean useLong) throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var zipped = new GZIPOutputStream(compressed);
                var out = new DataOutputStream(zipped)) {
            out.writeByte(10);
            out.writeUTF("");
            if (reverse) {
                out.writeByte(8);
                out.writeUTF("label");
                out.writeUTF("fixed-value");
            }
            out.writeByte(useLong ? 4 : 3);
            out.writeUTF("typed-value");
            if (useLong) out.writeLong(7L);
            else out.writeInt(7);
            if (!reverse) {
                out.writeByte(8);
                out.writeUTF("label");
                out.writeUTF("fixed-value");
            }
            out.writeByte(0);
        }
        return compressed.toByteArray();
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
                    canonicalPayload(in, entry, element, depth + 1);
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
                for (int index = 0; index < count; index++) out.writeInt(in.readInt());
            }
            case 12 -> {
                int count = boundedLength(in);
                out.writeInt(count);
                for (int index = 0; index < count; index++) out.writeLong(in.readLong());
            }
            default -> throw new IOException("Unsupported native tag type");
        }
    }
}
