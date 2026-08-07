package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.integrations.ExternalBlockIntegration;
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
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** Default guarded registry for external machine/storage/cargo integration providers. */
public final class DefaultExternalIntegrationService implements ExternalIntegrationService {

    private static final KnownSystem REBAR = new KnownSystem(
            "rebar",
            "Rebar",
            "Rebar",
            "Optional Rebar integration. Phase 1E Part 2 loads its block capability adapter only when the runtime API can be probed safely.");
    private static final KnownSystem PYLON = new KnownSystem(
            "pylon",
            "Pylon",
            "Pylon",
            "Optional Pylon integration. Pylon blocks are inspected through Rebar without assuming Slimefun cargo or energy semantics.");

    private final Plugin owner;
    private final Map<String, ExternalIntegrationProvider> providers = new ConcurrentHashMap<>();
    private volatile List<ExternalIntegrationStatus> statuses = List.of();
    private volatile List<ExternalIntegrationProvider> activeProviders = List.of();

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

        Map<String, ExternalIntegrationProvider> effectiveProviders = new LinkedHashMap<>();
        Plugin rebarPlugin = findPlugin(REBAR.pluginName);
        Plugin pylonPlugin = findPlugin(PYLON.pluginName);
        if (rebarPlugin != null && rebarPlugin.isEnabled()) {
            try {
                ReflectiveRebarAccess access = ReflectiveRebarAccess.create(rebarPlugin);
                effectiveProviders.put(
                        REBAR.id,
                        new ReflectiveRebarIntegrationProvider(REBAR.id, REBAR.displayName, rebarPlugin, access, false));
                if (pylonPlugin != null && pylonPlugin.isEnabled()) {
                    effectiveProviders.put(
                            PYLON.id,
                            new ReflectiveRebarIntegrationProvider(PYLON.id, PYLON.displayName, pylonPlugin, access, true));
                }
            } catch (ClassNotFoundException | RuntimeException | LinkageError failure) {
                replaceKnownDetail(
                        result,
                        REBAR,
                        rebarPlugin,
                        "Rebar detected, but the reflection-only adapter could not load: "
                                + failure.getClass().getSimpleName());
                if (pylonPlugin != null && pylonPlugin.isEnabled()) {
                    replaceKnownDetail(
                            result,
                            PYLON,
                            pylonPlugin,
                            "Pylon detected, but its Rebar-backed adapter is unavailable because the Rebar API probe failed.");
                }
            }
        }

        // Explicit addon registrations take precedence over Legacy's conservative built-in reflective adapters.
        effectiveProviders.putAll(providers);

        for (Map.Entry<String, ExternalIntegrationProvider> entry : effectiveProviders.entrySet()) {
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
        activeProviders = List.copyOf(effectiveProviders.values());
    }

    @Override
    public @Nonnull List<ExternalIntegrationStatus> getStatuses() {
        return statuses;
    }

    @Override
    public @Nonnull List<ExternalBlockIntegration> inspectBlock(@Nonnull Block block) {
        Objects.requireNonNull(block, "block");
        List<ExternalBlockIntegration> result = new ArrayList<>();
        for (ExternalIntegrationProvider provider : activeProviders) {
            if (!provider.getPlugin().isEnabled()) {
                continue;
            }
            try {
                provider.inspectBlock(block).ifPresent(result::add);
            } catch (RuntimeException | LinkageError ignored) {
                // An experimental external API must never make a Slimefun diagnostic command fail.
            }
        }
        result.sort(Comparator.comparing(ExternalBlockIntegration::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
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

    private void replaceKnownDetail(
            Map<String, ExternalIntegrationStatus> result, KnownSystem known, Plugin plugin, String detail) {
        result.put(known.id, new ExternalIntegrationStatus(
                known.id,
                known.displayName,
                plugin.getName(),
                plugin.getDescription().getVersion(),
                true,
                plugin.isEnabled(),
                false,
                Set.of(),
                detail));
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
