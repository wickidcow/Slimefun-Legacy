package com.xzavier0722.mc.plugin.slimefun4.storage.migrator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual Java filesystem and ZIP tests; not a claim that an old database import was completed. */
class LegacyDataBackupTest {
    @TempDir
    Path root;

    @Test
    void backsUpExactNestedLegacyFilesWithoutTouchingTheirBytes() throws Exception {
        Path source = source();
        byte[] binary = new byte[33_001];
        new java.util.Random(123).nextBytes(binary);
        Files.createDirectories(source.resolve("old_world"));
        Files.write(source.resolve("old_world/UNKNOWN_ADDON.sfb"), binary);
        Files.writeString(source.resolve("owner.yml"), "backpacks:\n  42:\n    item: 'old Ω'\n");
        LegacyDataBackup.createOrVerify(source, archive());
        LegacyDataBackup.verify(source, archive());
        try (var zip = new ZipFile(archive().toFile())) {
            assertEquals(2, zip.size());
            assertArrayEquals(binary, zip.getInputStream(zip.getEntry("old_world/UNKNOWN_ADDON.sfb")).readAllBytes());
        }
        assertArrayEquals(binary, Files.readAllBytes(source.resolve("old_world/UNKNOWN_ADDON.sfb")));
        noPartialFiles();
    }

    @Test
    void validExistingArchiveIsReusedWithoutRewriting() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("old.sfi"), "saved");
        LegacyDataBackup.createOrVerify(source, archive());
        byte[] original = Files.readAllBytes(archive());
        var modified = Files.getLastModifiedTime(archive());
        LegacyDataBackup.createOrVerify(source, archive());
        assertArrayEquals(original, Files.readAllBytes(archive()));
        assertEquals(modified, Files.getLastModifiedTime(archive()));
    }

    @Test
    void corruptExistingArchiveNeverAuthorizesSourceDeletion() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("old.sfi"), "saved");
        Files.createDirectories(archive().getParent());
        byte[] corrupt = {0, 1, 2, 3};
        Files.write(archive(), corrupt);
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, archive()));
        assertThrows(IOException.class, () -> LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive()));
        assertEquals("saved", Files.readString(source.resolve("old.sfi")));
        assertArrayEquals(corrupt, Files.readAllBytes(archive()));
    }

    @Test
    void missingBackupCannotAuthorizeDeletion() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("old.sfi"), "saved");
        assertThrows(IOException.class, () -> LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive()));
        assertTrue(Files.exists(source.resolve("old.sfi")));
    }

    @Test
    void emptyArchiveCannotStandInForNonemptySource() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("old.sfi"), "saved");
        zip(Map.of());
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, archive()));
        assertTrue(Files.exists(source.resolve("old.sfi")));
    }

    @Test
    void archiveMissingOneSourceFileIsRejected() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("one"), "one");
        Files.writeString(source.resolve("two"), "two");
        zip(Map.of("one", "one"));
        assertThrows(IOException.class, () -> LegacyDataBackup.verify(source, archive()));
    }

    @Test
    void staleSameLengthContentsAreRejectedWithoutOverwritingArchive() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("old.sfi"), "before");
        LegacyDataBackup.createOrVerify(source, archive());
        byte[] original = Files.readAllBytes(archive());
        Files.writeString(source.resolve("old.sfi"), "AFTER!");
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, archive()));
        assertThrows(IOException.class, () -> LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive()));
        assertArrayEquals(original, Files.readAllBytes(archive()));
        assertEquals("AFTER!", Files.readString(source.resolve("old.sfi")));
    }

    @Test
    void addedFileAfterBackupPreventsAnyCleanup() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("one"), "one");
        LegacyDataBackup.createOrVerify(source, archive());
        Files.writeString(source.resolve("two"), "two");
        assertThrows(IOException.class, () -> LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive()));
        assertTrue(Files.exists(source.resolve("one")));
        assertTrue(Files.exists(source.resolve("two")));
    }

    @Test
    void removedFileAfterBackupIsNotSilentlyAccepted() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("one"), "one");
        LegacyDataBackup.createOrVerify(source, archive());
        Files.delete(source.resolve("one"));
        assertThrows(IOException.class, () -> LegacyDataBackup.verify(source, archive()));
    }

    @Test
    void validFlatCleanupLeavesTheCompleteVerifiedArchive() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("old.sfi"), "saved");
        LegacyDataBackup.createOrVerify(source, archive());
        byte[] backup = Files.readAllBytes(archive());
        LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive());
        assertFalse(Files.exists(source));
        assertArrayEquals(backup, Files.readAllBytes(archive()));
    }

    @Test
    void nestedDirectoryIsNotRecursivelyDeletedAndNoSiblingIsRemoved() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("one"), "one");
        Files.createDirectories(source.resolve("world"));
        Files.writeString(source.resolve("world/old.sfb"), "saved");
        LegacyDataBackup.createOrVerify(source, archive());
        assertThrows(IOException.class, () -> LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive()));
        assertTrue(Files.exists(source.resolve("one")));
        assertEquals("saved", Files.readString(source.resolve("world/old.sfb")));
    }

    @Test
    void emptyDirectoryHasAValidEmptyBackup() throws Exception {
        Path source = source();
        LegacyDataBackup.createOrVerify(source, archive());
        LegacyDataBackup.verify(source, archive());
        LegacyDataBackup.deleteVerifiedFlatDirectory(source, archive());
        assertFalse(Files.exists(source));
        try (var zip = new ZipFile(archive().toFile())) { assertEquals(0, zip.size()); }
    }

    @Test
    void historicalExplicitDirectoryEntriesAreAcceptedOnlyForRealDirectories() throws Exception {
        Path source = source();
        Files.createDirectories(source.resolve("world"));
        Files.writeString(source.resolve("world/item"), "saved");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("world/", "");
        entries.put("world/item", "saved");
        zip(entries);
        LegacyDataBackup.verify(source, archive());
    }

    @Test
    void unexpectedFileOrDirectoryInArchiveIsRejected() throws Exception {
        Path source = source();
        zip(Map.of("extra", "unexpected"));
        assertThrows(IOException.class, () -> LegacyDataBackup.verify(source, archive()));
        Files.delete(archive());
        zip(Map.of("absent/", ""));
        assertThrows(IOException.class, () -> LegacyDataBackup.verify(source, archive()));
    }

    @Test
    void malformedMemberNamesAreRefused() throws Exception {
        Path source = source();
        for (String name : new String[] {"../escape", "/absolute", "world/../escape", "world\\escape", "C:/escape"}) {
            Files.deleteIfExists(archive());
            zip(Map.of(name, "untouched"));
            assertThrows(IOException.class, () -> LegacyDataBackup.verify(source, archive()), name);
        }
        assertFalse(Files.exists(root.resolve("escape")));
    }

    @Test
    void duplicateArchiveMemberIsRefusedEvenWhenContentsMatch() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("aa"), "same");
        zip(Map.of("aa", "same", "bb", "same"));
        byte[] bytes = Files.readAllBytes(archive());
        int namesChanged = 0;
        for (int i = 0; i + 1 < bytes.length; i++) {
            if (bytes[i] == 'b' && bytes[i + 1] == 'b') {
                bytes[i] = 'a'; bytes[i + 1] = 'a'; namesChanged++; i++;
            }
        }
        assertEquals(2, namesChanged);
        Files.write(archive(), bytes);
        assertThrows(IOException.class, () -> LegacyDataBackup.verify(source, archive()));
    }

    @Test
    void truncatedArchiveIsNotReused() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("one"), "one");
        LegacyDataBackup.createOrVerify(source, archive());
        byte[] complete = Files.readAllBytes(archive());
        Files.write(archive(), Arrays.copyOf(complete, complete.length - 22));
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, archive()));
        assertEquals("one", Files.readString(source.resolve("one")));
    }

    @Test
    void linkedSourceEntryIsNotFollowedOrPublished() throws Exception {
        Path source = source();
        Path outside = Files.writeString(root.resolve("outside"), "private");
        Files.createSymbolicLink(source.resolve("linked"), outside);
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, archive()));
        assertFalse(Files.exists(archive()));
        assertEquals("private", Files.readString(outside));
        noPartialFiles();
    }

    @Test
    void linkedArchiveOrAncestorIsRefusedWithoutTouchingItsTarget() throws Exception {
        Path source = source();
        Files.writeString(source.resolve("one"), "one");
        Path outside = Files.writeString(root.resolve("outside"), "private");
        Files.createDirectories(archive().getParent());
        Files.createSymbolicLink(archive(), outside);
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, archive()));
        assertEquals("private", Files.readString(outside));
        Path linked = root.resolve("linked-root");
        Files.createSymbolicLink(linked, source);
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(linked, root.resolve("other.zip")));
    }

    @Test
    void missingSourceAndDestinationInsideSourceAreRefused() throws Exception {
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(root.resolve("missing"), archive()));
        Path source = source();
        assertThrows(IOException.class, () -> LegacyDataBackup.createOrVerify(source, source.resolve("backup.zip")));
        assertFalse(Files.exists(source.resolve("backup.zip")));
    }

    private Path source() throws IOException { return Files.createDirectory(root.resolve("stored-inventories")); }
    private Path archive() { return root.resolve("old_data/stored-inventories.zip"); }
    private void noPartialFiles() throws IOException {
        try (var files = Files.walk(root)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".partial")));
        }
    }
    private void zip(Map<String, String> entries) throws IOException {
        Files.createDirectories(archive().getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive()))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
