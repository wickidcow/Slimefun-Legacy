package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Creates per-run snapshots of addon-provided read-only legacy item schema probes. */
public final class LegacyItemSchemaProbeService {

    private final JavaPlugin plugin;

    public LegacyItemSchemaProbeService(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Snapshots enabled probe registrations once for a migration-aware Doctor scan.
     *
     * <p>Provider discovery and supported-ID publication are isolated here so Doctor does not query Bukkit's
     * service registry or addon metadata for every ItemStack it scans.</p>
     */
    @Nonnull
    Session createSession(@Nonnull ItemDoctorReport report) {
        Map<String, List<ProbeRegistration>> byItemId = new HashMap<>();
        int providers = 0;

        for (RegisteredServiceProvider<LegacyItemSchemaProbe> registration :
                Bukkit.getServicesManager().getRegistrations(LegacyItemSchemaProbe.class)) {
            if (registration.getPlugin() == null || !registration.getPlugin().isEnabled()) {
                continue;
            }

            String providerId = registration.getPlugin().getName();
            LegacyItemSchemaProbe provider = registration.getProvider();
            try {
                String migrationName = provider.getMigrationName();
                if (migrationName == null || migrationName.isBlank()) {
                    migrationName = providerId;
                } else {
                    migrationName = migrationName.trim();
                }

                Set<String> supportedIds = provider.getSupportedItemIds();
                if (supportedIds == null) {
                    throw new IllegalStateException("Schema probe returned null supported item IDs");
                }

                ProbeRegistration probe = new ProbeRegistration(providerId, migrationName, provider);
                boolean registeredAny = false;
                for (String supportedId : supportedIds) {
                    if (supportedId == null || supportedId.isBlank()) {
                        continue;
                    }
                    byItemId.computeIfAbsent(supportedId.trim(), ignored -> new ArrayList<>()).add(probe);
                    registeredAny = true;
                }
                if (registeredAny) {
                    providers++;
                }
            } catch (Throwable throwable) {
                report.failure();
                plugin.getLogger().log(
                        Level.WARNING,
                        "Could not initialize legacy item schema probe from " + providerId + '.',
                        throwable);
            }
        }

        Map<String, List<ProbeRegistration>> frozen = new HashMap<>();
        for (Map.Entry<String, List<ProbeRegistration>> entry : byItemId.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new Session(plugin, Collections.unmodifiableMap(frozen), providers);
    }

    static final class Session {
        private final JavaPlugin plugin;
        private final Map<String, List<ProbeRegistration>> byItemId;
        private final int providerCount;

        private Session(JavaPlugin plugin, Map<String, List<ProbeRegistration>> byItemId, int providerCount) {
            this.plugin = plugin;
            this.byItemId = byItemId;
            this.providerCount = providerCount;
        }

        int getProviderCount() {
            return providerCount;
        }

        void inspect(@Nonnull ItemStack item, @Nonnull String slimefunId, @Nonnull ItemDoctorReport report) {
            List<ProbeRegistration> probes = byItemId.get(slimefunId);
            if (probes == null || probes.isEmpty()) {
                return;
            }

            for (ProbeRegistration registration : probes) {
                try {
                    LegacyItemSchemaCandidate candidate =
                            registration.provider().probeItem(item.clone(), slimefunId);
                    if (candidate != null) {
                        report.schemaMigrationCandidateFound(
                                registration.providerId(), registration.migrationName(), candidate);
                    }
                } catch (Throwable throwable) {
                    report.failure();
                    plugin.getLogger().log(
                            Level.WARNING,
                            "Legacy item schema probe failed for " + registration.providerId()
                                    + " on Slimefun item " + slimefunId + '.',
                            throwable);
                }
            }
        }
    }

    private record ProbeRegistration(String providerId, String migrationName, LegacyItemSchemaProbe provider) {}
}
