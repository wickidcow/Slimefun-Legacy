package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestDoctorRouterCommand {

    @Test
    @DisplayName("Doctor migration route should accept migrate, migration and migrations")
    void testMigrationAliases() {
        Assertions.assertTrue(DoctorRouterCommand.isMigrationRoute("migrate"));
        Assertions.assertTrue(DoctorRouterCommand.isMigrationRoute("migration"));
        Assertions.assertTrue(DoctorRouterCommand.isMigrationRoute("migrations"));
        Assertions.assertTrue(DoctorRouterCommand.isMigrationRoute("MiGrAtE"));

        Assertions.assertFalse(DoctorRouterCommand.isMigrationRoute("migrated"));
        Assertions.assertFalse(DoctorRouterCommand.isMigrationRoute("upgrade"));
    }
}
