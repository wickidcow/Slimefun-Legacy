package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/** Append-only before-images; a failed backup prevents the corresponding mutation. */
final class ResourcePackDoctorBackup {
    private final Path directory;

    ResourcePackDoctorBackup(Path directory) throws IOException {
        this.directory = directory;
        if (!Files.isDirectory(directory, new LinkOption[0])) {
            throw new IOException("Resource-pack Doctor backup directory is missing: " + String.valueOf(directory));
        }
    }

    static ResourcePackDoctorBackup create(Path pluginDirectory, String operation) throws IOException {
        Path directory = pluginDirectory.resolve("backups/resource-pack-doctor/" + String.valueOf(UUID.randomUUID()));
        Files.createDirectories(directory, new FileAttribute[0]);
        for (String name : new String[] {"item-models.yml", "configSFLAddons.yml", "resource-pack-doctor.yml"}) {
            Path source = pluginDirectory.resolve(name);
            if (!Files.isRegularFile(source, new LinkOption[0])) continue;
            Files.copy(source, directory.resolve(name), new CopyOption[0]);
        }
        ResourcePackDoctorBackup.atomicWrite(
                directory.resolve("README.txt"),
                "Slimefun resource-pack Doctor " + operation
                        + "\nitems.tsv contains original item payloads before live inventory changes.\nrows.tsv contains exact original database values before guarded rewrites.\nFields are tab-separated; identities and payloads use Base64. B = binary, T = UTF-8 text.\nRestore individual records only while the server is stopped, with a full server backup.\nThis journal is not an automatic rollback command or a complete world backup.\n");
        return new ResourcePackDoctorBackup(directory);
    }

    Path directory() {
        return this.directory;
    }

    void item(String identity, ItemStack original) throws IOException {
        byte[] bytes = DataUtils.serializeItemStackBytesForStorage(original);
        ItemStack roundTrip = DataUtils.deserializeItemStack(bytes);
        if (bytes.length == 0 || roundTrip == null || !original.equals((Object) roundTrip)) {
            throw new IOException("Item backup did not preserve the original stack: " + identity);
        }
        this.append(
                "items.tsv",
                ResourcePackDoctorBackup.encode(identity.getBytes(StandardCharsets.UTF_8)) + "\tB\t"
                        + ResourcePackDoctorBackup.encode(bytes) + "\n");
    }

    void row(String identity, PersistedItemStorageMaintenance.StoredItemValue original) throws IOException {
        byte[] payload = original.isBinary()
                ? original.binaryCopy()
                : original.textValue().getBytes(StandardCharsets.UTF_8);
        this.append(
                "rows.tsv",
                ResourcePackDoctorBackup.encode(identity.getBytes(StandardCharsets.UTF_8)) + "\t"
                        + (original.isBinary() ? "B" : "T") + "\t" + ResourcePackDoctorBackup.encode(payload) + "\n");
    }

    private synchronized void append(String name, String record) throws IOException {
        try (FileChannel channel = FileChannel.open(
                this.directory.resolve(name),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND); ) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(record);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    static void atomicWrite(Path file, String text) throws IOException {
        Path temporary =
                Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp", new FileAttribute[0]);
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE); ) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(text);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
