package com.xzavier0722.mc.plugin.slimefun4.storage.migrator;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.nio.file.Path;
import java.util.logging.Level;

class MigratorUtil {
    protected static boolean createDirBackup(File dir) {
        try {
            LegacyDataBackup.createOrVerify(dir.toPath(), backupPath(dir));
            return true;
        } catch (Exception failure) {
            Slimefun.logger().log(Level.WARNING,
                    "Legacy backup was not verified for " + dir.getName()
                            + "; original files were retained. Do not delete them or overwrite an existing backup.",
                    failure);
            return false;
        }
    }

    protected static void deleteOldFolder(File dir) {
        try {
            // A previous true return is not sufficient: source files or the archive
            // may have changed since backup. Recheck before any flat-file cleanup.
            LegacyDataBackup.deleteVerifiedFlatDirectory(dir.toPath(), backupPath(dir));
        } catch (Exception failure) {
            Slimefun.logger().log(Level.WARNING,
                    "Legacy source cleanup was refused for " + dir.getName()
                            + "; retain the original files and inspect the backup before retrying.",
                    failure);
        }
    }

    private static Path backupPath(File dir) {
        return Path.of("data-storage/Slimefun/old_data/", dir.getName() + ".zip");
    }

    protected static boolean checkMigrateMark() {
        var backupData = new File("data-storage/Slimefun/old_data/");
        return backupData.exists()
                && backupData.isDirectory()
                && backupData.listFiles() != null
                && backupData.listFiles().length > 0;
    }
}
