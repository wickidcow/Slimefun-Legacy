package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationCapability;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationProvider;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationService;
import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalIntegrationStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/** Default guarded registry for external machine/storage/cargo integration providers. */
public final class DefaultExternalIntegrationService implements ExternalIntegrationService {

    private static final KnownSystem REBAR = new KnownSystem(
            "rebar",
            "Rebar",
            "Rebar",
            "Detected only. Rebar is experimental, so active bridges must explicitly register supported capabilities.");
    private static final KnownSystem PYLON = new KnownSystem(
            "pylon",
            "Pylon",
            "Pylon",
            "Detected only. Pylon is built on Rebar; Slimefun does not assume cargo, energy, or storage semantics are compatible.");

    private final Plugin owner;
    private final Map<String, ExternalIntegrationProvider> providers = new ConcurrentHashMap<>();
    private volatile List<ExternalIntegrationStatus> statuses = List.of();

    public DefaultExternalIntegrationService(@Nonnull Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    @Override
    public void register(@Nonnull ExternalIntegrationProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String id = normalize(provider.getIntegrationId());
        if (id.isEmpty()) {
            throw new IllegalArgumentException("External integration id cannot be blank");
        }
        if (!provider.getPlugin().isEnabled()) {
            throw new IllegalStateException("External integration provider plugin must be enabled");
        }
        providers.put(id, provider);
        refresh();
    }

    @Override
    public void unregister(@Nonnull Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        providers.entrySet().removeIf(entry -> entry.getValue().getPlugin().equals(plugin));
        refresh();
    }

    @Override
    public void refresh() {
        Map<String, ExternalIntegrationStatus> result = new LinkedHashMap<>();
        addKnown(result, REBAR);
        addKnown(result, PYLON);

        for (Map.Entry<String, ExternalIntegrationProvider> entry : providers.entrySet()) {
            ExternalIntegrationProvider provider = entry.getValue();
            Plugin plugin = provider.getPlugin();
            Set<ExternalIntegrationCapability> capabilities;
            String displayName;
            String detail;
            try {
                capabilities = Set.copyOf(provider.getCapabilities());
                displayName = provider.getDisplayName();
                detail = provider.getStatusDescription();
            } catch (RuntimeException | LinkageError failure) {
                capabilities = Set.of();
                displayName = plugin.getName();
                detail = "Bridge provider failed its status probe: " + failure.getClass().getSimpleName();
            }
            result.put(entry.getKey(), new ExternalIntegrationStatus(
                    entry.getKey(),
                    displayName,
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    true,
                    plugin.isEnabled(),
                    true,
                    capabilities,
                    detail));
        }

        List<ExternalIntegrationStatus> ordered = new ArrayList<>(result.values());
        ordered.sort(Comparator.comparing(ExternalIntegrationStatus::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        statuses = List.copyOf(ordered);
    }

    @Override
    public @Nonnull List<ExternalIntegrationStatus> getStatuses() {
        return statuses;
    }

    private void addKnown(Map<String, ExternalIntegrationStatus> result, KnownSystem known) {
        Plugin plugin = findPlugin(known.pluginName);
        boolean detected = plugin != null;
        boolean enabled = detected && plugin.isEnabled();
        result.put(known.id, new ExternalIntegrationStatus(
                known.id,
                known.displayName,
                known.pluginName,
                detected ? plugin.getDescription().getVersion() : null,
                detected,
                enabled,
                false,
                Set.of(),
                known.detail));
    }

    private Plugin findPlugin(String name) {
        for (Plugin plugin : owner.getServer().getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private record KnownSystem(String id, String displayName, String pluginName, String detail) {}
}
