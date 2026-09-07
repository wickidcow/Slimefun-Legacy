package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemMigrationProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Discovers and safely invokes addon-owned legacy item migration providers. */
public final class LegacyItemMigrationService {

    private final JavaPlugin plugin;

    public LegacyItemMigrationService(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    public List<RegisteredServiceProvider<LegacyItemMigrationProvider>> getProviders() {
        List<RegisteredServiceProvider<LegacyItemMigrationProvider>> providers =
                new ArrayList<>(Bukkit.getServicesManager().getRegistrations(LegacyItemMigrationProvider.class));
        providers.removeIf(provider -> provider.getPlugin() == null || !provider.getPlugin().isEnabled());
        providers.sort(Comparator.comparing(this::getProviderId, String.CASE_INSENSITIVE_ORDER));
        return providers;
    }

    @Nonnull
    public Optional<RegisteredServiceProvider<LegacyItemMigrationProvider>> findProvider(@Nonnull String providerId) {
        return getProviders().stream()
                .filter(provider -> getProviderId(provider).equalsIgnoreCase(providerId))
                .findFirst();
    }

    /** Stable command identifier. This deliberately uses the owning Bukkit plugin name. */
    @Nonnull
    public String getProviderId(@Nonnull RegisteredServiceProvider<LegacyItemMigrationProvider> registration) {
        return registration.getPlugin() == null ? "unknown" : registration.getPlugin().getName();
    }

    /** Returns a display name without trusting third-party provider code during status output. */
    @Nonnull
    public String getProviderName(@Nonnull RegisteredServiceProvider<LegacyItemMigrationProvider> registration) {
        String fallback = getProviderId(registration);
        try {
            String name = registration.getProvider().getMigrationName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINE, "Migration provider name lookup failed for " + fallback, throwable);
        }
        return fallback;
    }

    /**
     * Returns a defensive, read-only snapshot of provider mappings.
     * Invalid provider output is treated as an empty mapping set and logged.
     */
    @Nonnull
    public Map<String, String> getMappings(
            @Nonnull RegisteredServiceProvider<LegacyItemMigrationProvider> registration) {
        String providerId = getProviderId(registration);
        try {
            Map<String, String> mappings = registration.getProvider().getLegacyItemMappings();
            if (mappings == null) {
                throw new IllegalStateException("Migration provider returned null mappings");
            }

            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String legacyId = entry.getKey();
                String currentId = entry.getValue();
                if (legacyId == null || currentId == null || legacyId.isBlank() || currentId.isBlank()) {
                    throw new IllegalStateException("Migration provider returned a blank/null mapping");
                }
                copy.put(legacyId, currentId);
            }
            return Collections.unmodifiableMap(copy);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Unable to read migration mappings from " + providerId, throwable);
            return Collections.emptyMap();
        }
    }

    /** Safely invokes one provider and converts provider failures into a report. */
    @Nonnull
    public AddonDoctorReport run(
            @Nonnull RegisteredServiceProvider<LegacyItemMigrationProvider> registration, boolean repair) {
        String providerId = getProviderId(registration);
        String providerName = getProviderName(registration);
        try {
            AddonDoctorReport report = registration.getProvider().runMigration(repair);
            if (report == null) {
                throw new IllegalStateException("Migration provider returned null report");
            }
            return report;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Legacy migration provider failed for " + providerId, throwable);
            return new AddonDoctorReport(
                    providerName,
                    repair,
                    0L,
                    1L,
                    0L,
                    1L,
                    List.of("Provider threw " + throwable.getClass().getSimpleName() + ": "
                            + String.valueOf(throwable.getMessage())));
        }
    }
}
