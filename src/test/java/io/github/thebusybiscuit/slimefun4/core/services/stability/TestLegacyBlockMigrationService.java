package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationProvider;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyBlockMigrationResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class TestLegacyBlockMigrationService {

    private ServerMock server;
    private PluginMock plugin;
    private World world;
    private LegacyBlockMigrationService service;
    private TestProvider provider;
    private RegisteredServiceProvider<LegacyBlockMigrationProvider> registration;
    private LegacyBlockMigrationCandidate candidate;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        world.getChunkAt(0, 0).load();

        service = new LegacyBlockMigrationService(plugin);
        provider = new TestProvider();
        registration = new RegisteredServiceProvider<>(
                LegacyBlockMigrationProvider.class, provider, ServicePriority.Normal, plugin);
        candidate = new LegacyBlockMigrationCandidate(world.getUID(), 1, 64, 1, "OLD_MACHINE", "NEW_MACHINE", "claim");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void executesOnlyAfterSuccessfulRevalidation() {
        LegacyBlockMigrationPlan plan = plan(candidate);

        LegacyBlockMigrationExecutionReport report = service.execute(registration, plan);

        assertEquals(1L, report.authorized());
        assertEquals(1L, report.revalidated());
        assertEquals(1L, report.migrated());
        assertEquals(0L, report.skippedChanged());
        assertEquals(0L, report.blocked());
        assertEquals(0L, report.failures());
        assertEquals(1, provider.revalidationCalls);
        assertEquals(1, provider.migrationCalls);
    }

    @Test
    void skipsChangedCandidateWithoutCallingMigrator() {
        provider.valid = false;
        LegacyBlockMigrationExecutionReport report = service.execute(registration, plan(candidate));

        assertEquals(1L, report.authorized());
        assertEquals(0L, report.revalidated());
        assertEquals(0L, report.migrated());
        assertEquals(1L, report.skippedChanged());
        assertEquals(0L, report.failures());
        assertEquals(1, provider.revalidationCalls);
        assertEquals(0, provider.migrationCalls);
        assertTrue(report.details().stream().anyMatch(line -> line.contains("state claim no longer matches")));
    }

    @Test
    void refusesExecutionWhenProviderMappingsChangeAfterPlanning() {
        LegacyBlockMigrationPlan plan = plan(candidate);
        provider.mappings = Map.of("OLD_MACHINE", "OTHER_MACHINE");

        LegacyBlockMigrationExecutionReport report = service.execute(registration, plan);

        assertEquals(1L, report.authorized());
        assertEquals(0L, report.revalidated());
        assertEquals(0L, report.migrated());
        assertEquals(1L, report.blocked());
        assertEquals(0L, report.failures());
        assertEquals(0, provider.revalidationCalls);
        assertEquals(0, provider.migrationCalls);
        assertTrue(report.details().stream().anyMatch(line -> line.contains("mapping snapshot changed")));
    }

    @Test
    void accountsForBlockedAndFailedProviderResults() {
        provider.result = LegacyBlockMigrationResult.blocked("manual intervention required");
        LegacyBlockMigrationExecutionReport blocked = service.execute(registration, plan(candidate));

        assertEquals(1L, blocked.revalidated());
        assertEquals(1L, blocked.blocked());
        assertEquals(0L, blocked.failures());

        provider.result = LegacyBlockMigrationResult.failed("rollback completed");
        LegacyBlockMigrationExecutionReport failed = service.execute(registration, plan(candidate));

        assertEquals(1L, failed.revalidated());
        assertEquals(0L, failed.blocked());
        assertEquals(1L, failed.failures());
        assertTrue(failed.details().stream().anyMatch(line -> line.contains("rollback completed")));
    }

    @Test
    void catchesProviderExceptionsAndContinuesFailClosed() {
        provider.throwOnRevalidate = true;
        LegacyBlockMigrationExecutionReport revalidationFailure = service.execute(registration, plan(candidate));

        assertEquals(0L, revalidationFailure.revalidated());
        assertEquals(1L, revalidationFailure.failures());
        assertEquals(0, provider.migrationCalls);

        provider.throwOnRevalidate = false;
        provider.throwOnMigrate = true;
        LegacyBlockMigrationExecutionReport migrationFailure = service.execute(registration, plan(candidate));

        assertEquals(1L, migrationFailure.revalidated());
        assertEquals(1L, migrationFailure.failures());
        assertEquals(1, provider.migrationCalls);
    }

    @Test
    void skipsCandidateWhenWorldIsNoLongerLoaded() {
        LegacyBlockMigrationCandidate missingWorld = new LegacyBlockMigrationCandidate(
                java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                1,
                64,
                1,
                "OLD_MACHINE",
                "NEW_MACHINE",
                "claim");

        LegacyBlockMigrationExecutionReport report = service.execute(registration, plan(missingWorld));

        assertEquals(0L, report.revalidated());
        assertEquals(1L, report.skippedChanged());
        assertEquals(0, provider.revalidationCalls);
        assertEquals(0, provider.migrationCalls);
        assertTrue(report.details().stream().anyMatch(line -> line.contains("no longer loaded")));
    }

    @Test
    void executeConsumesAnyPreparedPlanForProvider() {
        LegacyBlockMigrationPlan prepared = plan(candidate);
        service.invalidatePreparedPlan(plugin.getName());
        assertFalse(service.getPreparedPlan(plugin.getName()).isPresent());

        LegacyBlockMigrationExecutionReport report = service.execute(registration, prepared);

        assertEquals(1L, report.migrated());
        assertFalse(service.getPreparedPlan(plugin.getName()).isPresent());
    }

    private LegacyBlockMigrationPlan plan(LegacyBlockMigrationCandidate migrationCandidate) {
        long now = System.currentTimeMillis();
        return new LegacyBlockMigrationPlan(
                service.getProviderId(registration),
                service.getProviderVersion(registration),
                now,
                now + service.getPlanTtlMillis(),
                provider.mappings,
                List.of(migrationCandidate));
    }

    private static final class TestProvider implements LegacyBlockMigrationProvider {

        private Map<String, String> mappings = Map.of("OLD_MACHINE", "NEW_MACHINE");
        private boolean valid = true;
        private boolean throwOnRevalidate;
        private boolean throwOnMigrate;
        private LegacyBlockMigrationResult result = LegacyBlockMigrationResult.migrated("done");
        private int revalidationCalls;
        private int migrationCalls;

        @Override
        public String getMigrationName() {
            return "Test machine migration";
        }

        @Override
        public Map<String, String> getLegacyBlockMappings() {
            return mappings;
        }

        @Override
        public Collection<LegacyBlockMigrationCandidate> scanLoadedCandidates() {
            return List.of();
        }

        @Override
        public boolean isCandidateStillValid(LegacyBlockMigrationCandidate candidate) {
            revalidationCalls++;
            if (throwOnRevalidate) {
                throw new IllegalStateException("revalidation failed");
            }
            return valid;
        }

        @Override
        public LegacyBlockMigrationResult migrate(LegacyBlockMigrationCandidate candidate) {
            migrationCalls++;
            if (throwOnMigrate) {
                throw new IllegalStateException("migration failed");
            }
            return result;
        }
    }
}
