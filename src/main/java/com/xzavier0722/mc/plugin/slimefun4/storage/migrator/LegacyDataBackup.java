package com.xzavier0722.mc.plugin.slimefun4.storage.migrator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Verified backups of legacy source files; never a certificate that database import completed. */
final class LegacyDataBackup {
    private LegacyDataBackup() {}

    /**
     * Reuses only a byte-verified complete backup. A new backup is finished and verified before
     * publication, and an existing archive is never overwritten. Source data must be quiescent.
     */
    static void createOrVerify(Path source, Path archive) throws IOException {
        source = source.toAbsolutePath().normalize();
        archive = archive.toAbsolutePath().normalize();
        requireSafeSource(source);
        requireSafePath(archive);
        if (archive.startsWith(source)) {
            throw new IOException("Backup destination must not be inside its source");
        }
        if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) {
            verify(source, archive);
            return;
        }
        Files.createDirectories(archive.getParent());
        requireSafePath(archive);
        // A partial file must not appear in old_data: older migration detection treats
        // the presence of any file there as a marker. Use the source's parent instead.
        Path temporary = Files.createTempFile(source.getParent(), ".slimefun-backup-", ".partial");
        try {
            Map<String, Path> files = inventory(source);
            try (var output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (var entry : files.entrySet()) {
                    BasicFileAttributes before = attributes(entry.getValue());
                    output.putNextEntry(new ZipEntry(entry.getKey()));
                    try (var input = Files.newInputStream(entry.getValue(), LinkOption.NOFOLLOW_LINKS)) {
                        input.transferTo(output);
                    }
                    output.closeEntry();
                    requireUnchanged(before, attributes(entry.getValue()));
                }
            }
            // Verify the actual completed archive, including member set and CRCs,
            // rather than assuming that closing ZipOutputStream establishes a backup.
            verify(source, temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                // Hard-link publication is atomic and refuses an existing destination.
                // ATOMIC_MOVE may replace a concurrently created destination on Unix.
                Files.createLink(archive, temporary);
            } catch (FileAlreadyExistsException collision) {
                verify(source, archive);
            } catch (UnsupportedOperationException unsupported) {
                throw new IOException("Filesystem cannot safely publish this backup without replacing an existing file", unsupported);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void verify(Path source, Path archive) throws IOException {
        source = source.toAbsolutePath().normalize();
        archive = archive.toAbsolutePath().normalize();
        requireSafeSource(source);
        requireSafePath(archive);
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Existing backup is not a regular file");
        }
        Map<String, Path> files = inventory(source);
        Set<String> found = new HashSet<>();
        BasicFileAttributes originalArchive = attributes(archive);
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!found.add(name)) {
                    throw new IOException("Duplicate backup member: " + name);
                }
                requireSafeMember(name);
                if (entry.isDirectory()) {
                    Path directory = source.resolve(name).normalize();
                    if (!directory.startsWith(source) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Backup contains an unexpected directory: " + name);
                    }
                    continue;
                }
                Path file = files.remove(name);
                if (file == null) {
                    throw new IOException("Backup contains an unexpected file: " + name);
                }
                BasicFileAttributes before = attributes(file);
                if (entry.getSize() != before.size()) {
                    throw new IOException("Backup file length differs: " + name);
                }
                try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
                        var archived = zip.getInputStream(entry)) {
                    compare(input, archived, entry);
                }
                requireUnchanged(before, attributes(file));
            }
        }
        if (!files.isEmpty()) {
            throw new IOException("Backup is missing " + files.size() + " source file(s)");
        }
        requireUnchanged(originalArchive, attributes(archive));
        // Detect added or removed paths while verifying. Individual files are checked
        // before and after reading; this is not a filesystem transaction with writers.
        Set<String> current = inventory(source).keySet();
        found.removeIf(name -> name.endsWith("/"));
        if (!found.equals(current)) {
            throw new IOException("Source directory changed while verifying its backup");
        }
    }

    /** Retains the old flat-directory cleanup contract; never recursively removes a world tree. */
    static void deleteVerifiedFlatDirectory(Path source, Path archive) throws IOException {
        requireSafeSource(source);
        List<Path> files;
        try (var children = Files.list(source)) {
            files = children.sorted().toList();
        }
        for (Path file : files) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refused legacy cleanup of a non-flat directory");
            }
        }
        verify(source, archive);
        for (Path file : files) {
            Files.delete(file);
        }
        Files.delete(source);
    }

    private static Map<String, Path> inventory(Path source) throws IOException {
        requireSafeSource(source);
        List<Path> paths;
        try (var stream = Files.walk(source)) {
            paths = stream.sorted().toList();
        }
        Map<String, Path> files = new LinkedHashMap<>();
        for (Path path : paths) {
            BasicFileAttributes attrs = attributes(path);
            if (attrs.isDirectory()) {
                continue;
            }
            if (!attrs.isRegularFile() || Files.isSymbolicLink(path)) {
                throw new IOException("Refused backup of a link or non-regular file: " + path.getFileName());
            }
            String name = source.relativize(path).toString().replace(java.io.File.separatorChar, '/');
            requireSafeMember(name);
            files.put(name, path);
        }
        return files;
    }

    private static void compare(InputStream original, InputStream archived, ZipEntry entry) throws IOException {
        var crc = new CRC32();
        byte[] expected = new byte[8192];
        byte[] actual = new byte[8192];
        int length;
        while ((length = original.readNBytes(expected, 0, expected.length)) > 0) {
            int copied = archived.readNBytes(actual, 0, length);
            if (copied != length || !java.util.Arrays.equals(expected, 0, length, actual, 0, copied)) {
                throw new IOException("Backup contents differ: " + entry.getName());
            }
            crc.update(actual, 0, copied);
        }
        if (archived.read() != -1 || crc.getValue() != entry.getCrc()) {
            throw new IOException("Backup CRC or trailing contents differ: " + entry.getName());
        }
    }

    private static void requireSafeMember(String name) throws IOException {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe backup member name");
        }
        for (String part : name.split("/", -1)) {
            if (part.equals(".") || part.equals("..") || part.contains(":")) {
                throw new IOException("Unsafe backup member path");
            }
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireUnchanged(BasicFileAttributes before, BasicFileAttributes after) throws IOException {
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Backup source or archive changed during verification");
        }
    }

    private static void requireSafeSource(Path source) throws IOException {
        requireSafePath(source);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Legacy source is not an existing directory");
        }
    }

    private static void requireSafePath(Path path) throws IOException {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) {
                throw new IOException("Refused backup path containing a symbolic link");
            }
        }
    }
}
