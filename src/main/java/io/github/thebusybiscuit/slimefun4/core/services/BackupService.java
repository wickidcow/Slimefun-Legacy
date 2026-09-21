package io.github.thebusybiscuit.slimefun4.core.services;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.StorageType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;

/**
 * This Service creates a Backup of your Slimefun world data on every server shutdown.
 *
 * @author TheBusyBiscuit
 *
 */
public class BackupService implements Runnable {

    /**
     * The maximum amount of backups to maintain
     */
    private static final int MAX_BACKUPS = 20;

    /**
     * Our {@link DateTimeFormatter} for formatting file names.
     */
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm", Locale.ROOT);

    /**
     * The directory in which to create the backups
     */
    private final File directory = new File("data-storage/Slimefun/block-backups");

    @Override
    public void run() {
        var dbManager = Slimefun.getDatabaseManager();
        if (dbManager.getProfileStorageType() != StorageType.SQLITE
                && dbManager.getBlockDataStorageType() != StorageType.SQLITE) {
            return;
        }

        try {
            Files.createDirectories(directory.toPath());
        } catch (IOException exception) {
            Slimefun.logger().log(Level.SEVERE, "Unable to create the Slimefun backup directory", exception);
            return;
        }

        File file = new File(directory, format.format(LocalDateTime.now()) + ".zip");
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
                        createBackup(output);
                    }

                    Slimefun.logger().log(Level.INFO, "Backed up Slimefun data to: {0}", file.getName());
                } else {
                    Slimefun.logger().log(Level.WARNING, "Unable to create backup file: {0}", file.getName());
                }
            } catch (IOException exception) {
                Slimefun.logger()
                        .log(
                                Level.SEVERE,
                                exception,
                                () -> "An Exception occurred while creating a backup for Slimefun "
                                        + Slimefun.getVersion());
                return;
            }
        }

        File[] files = directory.listFiles(candidate -> candidate.isFile() && candidate.getName().endsWith(".zip"));
        if (files != null && files.length > MAX_BACKUPS) {
            try {
                purgeBackups(Arrays.asList(files));
            } catch (IOException exception) {
                Slimefun.logger().log(Level.WARNING, "Unable to delete old Slimefun backup file", exception);
            }
        }
    }

    /** Returns whether the current database configuration has SQLite data covered by this backup service. */
    public boolean isApplicable() {
        var dbManager = Slimefun.getDatabaseManager();
        return dbManager.getProfileStorageType() == StorageType.SQLITE
                || dbManager.getBlockDataStorageType() == StorageType.SQLITE;
    }

    /** Returns the number of existing Slimefun shutdown backup ZIPs. */
    public int getBackupCount() {
        File[] backups = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        return backups == null ? 0 : backups.length;
    }

    /** Returns the newest backup file modification time, or {@code 0} when no backup exists. */
    public long getLatestBackupModifiedMillis() {
        File[] backups = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        if (backups == null || backups.length == 0) {
            return 0L;
        }

        return Arrays.stream(backups).mapToLong(File::lastModified).max().orElse(0L);
    }

    /** Returns the retention ceiling used by the shutdown backup service. */
    public int getMaximumBackups() {
        return MAX_BACKUPS;
    }

    private void createBackup(@Nonnull ZipOutputStream output) throws IOException {
        Validate.notNull(output, "The Output Stream cannot be null!");

        if (Slimefun.getDatabaseManager().getProfileStorageType() == StorageType.SQLITE) {
            addFile(output, new File("data-storage/Slimefun", "profile.db"), "");
        }

        if (Slimefun.getDatabaseManager().getBlockDataStorageType() == StorageType.SQLITE) {
            addFile(output, new File("data-storage/Slimefun", "block-storage.db"), "");
        }
    }

    private void addFile(ZipOutputStream output, File file, String path) throws IOException {
        var entry = new ZipEntry(path + "/" + file.getName());
        output.putNextEntry(entry);

        byte[] buffer = new byte[4096];
        try (var input = new FileInputStream(file)) {
            int length;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
        output.closeEntry();
    }

    private void addDirectory(@Nonnull ZipOutputStream output, @Nonnull File directory, @Nonnull String zipPath)
            throws IOException {
        for (File file : directory.listFiles()) {
            addFile(output, file, zipPath);
        }
    }

    /**
     * This method will delete old backups.
     *
     * @param backups
     *            The {@link List} of all backups
     *
     * @throws IOException
     *             An {@link IOException} is thrown if a {@link File} could not be deleted
     */
    private void purgeBackups(@Nonnull List<File> backups) throws IOException {
        List<File> matchedBackups = backups.stream()
                .filter(file -> file.getName().matches("^\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.zip$"))
                .sorted((left, right) -> Long.compare(right.lastModified(), left.lastModified()))
                .toList();

        for (int i = MAX_BACKUPS; i < matchedBackups.size(); i++) {
            Files.deleteIfExists(matchedBackups.get(i).toPath());
        }
    }
}
