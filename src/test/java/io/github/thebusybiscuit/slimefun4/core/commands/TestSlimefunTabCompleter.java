package io.github.thebusybiscuit.slimefun4.core.commands;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestSlimefunTabCompleter {

    @Test
    @DisplayName("Doctor migration aliases should share tab-completion routing")
    void testDoctorMigrationAliases() {
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("migrate"));
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("migration"));
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("migrations"));
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("MIGRATE"));

        Assertions.assertFalse(SlimefunTabCompleter.isDoctorMigrationRoute("migrated"));
        Assertions.assertFalse(SlimefunTabCompleter.isDoctorMigrationRoute("upgrade"));
    }
}
