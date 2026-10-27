package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.core.limiting.ChunkMachineLimitPolicy;
import io.github.thebusybiscuit.slimefun4.core.ticker.FairTickerBatch;
import io.github.thebusybiscuit.slimefun4.core.ticker.TickLocation;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Integration smoke for PRs #476, #477 and #478 on one exact Java core.
 * No test method touches or pauses an actual machine inventory.
 */
class TestCombinedChunkProtection {

    private ServerMock server;
    private World world;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void packagedConfigurationKeepsBothSafeguardsOffUntilAdminOptIn() throws Exception {
        try (InputStream input = TestCombinedChunkProtection.class.getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertNotNull(input, "The packaged Slimefun config.yml must be readable");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));

            assertFalse(cfg.getBoolean("machine-limiter.enabled"));
            assertEquals(256, cfg.getInt("machine-limiter.max-ticking-machines-per-chunk"));
            assertFalse(cfg.getBoolean("machine-limiter.allow-bypass-permission"));
            assertFalse(cfg.getBoolean("stability.chunk-ticker-budget.enabled"));
            assertEquals(512, cfg.getInt("stability.chunk-ticker-budget.max-tickers-per-chunk-per-cycle"));
        }
    }

    @Test
    void duplicateRegistrationsCannotConsumeExtraPlacementOrRuntimeBudgetSlots() {
        TickerTask ticker = new TickerTask();
        Location first = new Location(world, -5314, -57, -5569);
        Location second = first.clone().add(1, 0, 0);

        // Simulate a base where one machine is repeatedly registered during
        // addon initialization and chunk-load restoration.
        for (int i = 0; i < 500; i++) {
            ticker.enableTicker(first.clone());
        }
        ticker.enableTicker(second);

        Set<TickLocation> registrations = ticker.getTickLocations(first.getChunk());
        assertEquals(2, registrations.size(), "A repeat callback is never a second machine");
        assertFalse(ChunkMachineLimitPolicy.rejects(true, true, false, 1, 2));
        assertTrue(ChunkMachineLimitPolicy.rejects(true, true, false, registrations.size(), 2));
        assertFalse(ChunkMachineLimitPolicy.rejects(false, true, false, registrations.size(), 1));

        FairTickerBatch<TickLocation> window = new FairTickerBatch<>(registrations);
        assertEquals(2, window.size());
        Set<TickLocation> served = new HashSet<>(window.next(1));
        served.addAll(window.next(1));
        assertEquals(registrations, served, "One full rotation visits every unique machine once");
    }

    @Test
    void universalMachineIdentityIsPreservedAcrossPlacementAndFairness() {
        TickerTask ticker = new TickerTask();
        Location at = new Location(world, 17, 64, 17);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            ticker.enableTicker(at, firstId);
        }
        ticker.enableTicker(at, secondId);

        Set<TickLocation> registered = ticker.getTickLocations(at.getChunk());
        assertEquals(2, registered.size(), "Distinct UniversalBlock UUIDs must not be collapsed");
        FairTickerBatch<TickLocation> window = new FairTickerBatch<>(registered);

        assertEquals(2, new HashSet<>(window.next(2)).size());
        assertTrue(ChunkMachineLimitPolicy.rejects(true, true, false, registered.size(), 2));
    }
}
