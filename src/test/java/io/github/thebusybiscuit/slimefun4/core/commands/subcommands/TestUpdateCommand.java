package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestUpdateCommand {

    @Test
    void comparesReleaseVersions() {
        assertTrue(UpdateCommand.compareVersions("4.1.48", "4.1.47") > 0);
        assertTrue(UpdateCommand.compareVersions("v4.1.48", "4.1.47") > 0);
        assertTrue(UpdateCommand.compareVersions("4.1.48.1", "4.1.48") > 0);
        assertTrue(UpdateCommand.compareVersions("4.1.47", "4.1.48") < 0);
        assertEquals(0, UpdateCommand.compareVersions("v4.1.48", "4.1.48"));
        assertEquals(0, UpdateCommand.compareVersions("4.1.48", "4.1.48.0"));
    }

    @Test
    void treatsUnparseableVersionsAsUnknownRatherThanNewer() {
        assertEquals(0, UpdateCommand.compareVersions("development", "4.1.48"));
        assertEquals(0, UpdateCommand.compareVersions("4.1.48", "development"));
        assertEquals(0, UpdateCommand.compareVersions("", "4.1.48"));
    }
}
