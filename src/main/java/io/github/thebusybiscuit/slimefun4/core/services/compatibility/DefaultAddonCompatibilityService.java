package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityDeclaration;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityProvider;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityResult;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityService;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilitySource;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityStatus;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonRuntimeHealthService;
import io.github.thebusybiscuit.slimefun4.api.addons.OptionalDependencyService;
import io.github.thebusybiscuit.slimefun4.api.addons.SlimefunCoreVariant;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCompatibilityReport;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCompatibilityService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

/** Internal runtime registry for addon compatibility declarations and diagnostics. */
@SlimefunInternal
public final class DefaultAddonCompatibilityService implements AddonCompatibilityService {

    private final Plugin owner;
    private final PlatformCompatibilityService platformCompatibilityService;
    private final OptionalDependencyService optionalDependencyService;
    private final AddonRuntimeHealthService runtimeHealthService;
    private final AddonCompatibilityManifestReader manifestReader = new AddonCompatibilityManifestReader();
    private final Map<String, AddonCompatibilityDeclaration> explicitDeclarations = new ConcurrentHashMap<>();
    private volatile List<AddonCompatibilityResult> results = List.of();

    public DefaultAddonCompatibilityService(
            @Nonnull Plugin owner,
            @Nonnull PlatformCompatibilityService platformCompatibilityService,
            @Nonnull OptionalDependencyService optionalDependencyService) {
        this(owner, platformCompatibilityService, optionalDependencyService, null);
    }

    public DefaultAddonCompatibilityService(
            @Nonnull Plugin owner,
            @Nonnull PlatformCompatibilityService platformCompatibilityService,
            @Nonnull OptionalDependencyService optionalDependencyService,
            AddonRuntimeHealthService runtimeHealthService) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.platformCompatibilityService =
                Objects.requireNonNull(platformCompatibilityService, "platformCompatibilityService");
        this.optionalDependencyService =
                Objects.requireNonNull(optionalDependencyService, "optionalDependencyService");
        this.runtimeHealthService = runtimeHealthService;
    }

    @Override
    public @Nonnull SlimefunCoreVariant getRunningCoreVariant() {
        return SlimefunCoreVariant.LEGACY;
    }

    @Override
    public void register(@Nonnull Plugin plugin, @Nonnull AddonCompatibilityDeclaration declaration) {
        Objects.requireNonNull(plugin, "plugin");
        explicitDeclarations.put(key(plugin.getName()), Objects.requireNonNull(declaration, "declaration"));
    }

    @Override
    public void unregister(@Nonnull Plugin plugin) {
        explicitDeclarations.remove(key(Objects.requireNonNull(plugin, "plugin").getName()));
    }

    @Override
    public void refresh() {
        List<AddonCompatibilityResult> refreshed = installedAddons().stream()
                .map(this::inspect)
                .sorted(Comparator.comparingInt(
                                (AddonCompatibilityResult result) -> result.getStatus().getSeverity())
                        .reversed()
                        .thenComparing(AddonCompatibilityResult::getPluginName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        results = List.copyOf(refreshed);
    }

    @Override
    public @Nonnull List<AddonCompatibilityResult> getResults() {
        return results;
    }

    @Override
    public @Nonnull AddonCompatibilityResult inspect(@Nonnull Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        ResolvedDeclaration resolved = resolveDeclaration(plugin);
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> details = new ArrayList<>();

        if (!owner.getServer().getPluginManager().isPluginEnabled(plugin)) {
            warnings.add("Plugin is disabled; check its startup error");
            resolved.errorMessage().ifPresent(warnings::add);
            return result(plugin, AddonCompatibilityStatus.DISABLED, resolved, warnings);
        }

        AddonCompatibilityDeclaration declaration = resolved.declaration();
        if (declaration == null) {
            resolved.errorMessage().ifPresent(warnings::add);
            warnings.add("No runtime declaration or embedded " + AddonCompatibilityManifestReader.MANIFEST_PATH);
            return result(plugin, AddonCompatibilityStatus.UNDECLARED, resolved, warnings);
        }

        Set<SlimefunCoreVariant> testedCores = declaration.getTestedCoreVariants();
        if (testedCores.isEmpty()) {
            warnings.add("No tested Slimefun core variants were declared");
        } else if (!testedCores.contains(getRunningCoreVariant())) {
            warnings.add("Not explicitly tested on " + getRunningCoreVariant().getDisplayName()
                    + " (declared: " + joinCoreNames(testedCores) + ")");
        }

        PlatformCompatibilityReport platformReport =
                platformCompatibilityService.check(declaration.getPlatformRequirements());
        failures.addAll(platformReport.getIncompatibilities());

        for (String dependency : declaration.getRequiredPlugins()) {
            if (!optionalDependencyService.isPluginAvailable(dependency)) {
                failures.add("Required plugin is missing or disabled: " + dependency);
            }
        }
        for (String dependency : declaration.getOptionalPlugins()) {
            if (!optionalDependencyService.isPluginAvailable(dependency)) {
                details.add("Optional integration is inactive: " + dependency);
            }
        }

        if (!declaration.getNotes().isEmpty()) {
            details.add("Declaration note: " + declaration.getNotes());
        }
        resolved.errorMessage().ifPresent(warnings::add);

        if (!failures.isEmpty()) {
            failures.addAll(warnings);
            failures.addAll(details);
            return result(plugin, AddonCompatibilityStatus.INCOMPATIBLE, resolved, failures);
        }
        if (!warnings.isEmpty()) {
            warnings.addAll(details);
            return result(plugin, AddonCompatibilityStatus.WARNING, resolved, warnings);
        }
        return result(plugin, AddonCompatibilityStatus.COMPATIBLE, resolved, details);
    }

    private Set<Plugin> installedAddons() {
        Set<Plugin> addons = new LinkedHashSet<>();
        String coreName = owner.getName();
        Arrays.stream(owner.getServer().getPluginManager().getPlugins())
                .filter(plugin -> plugin != owner)
                .filter(plugin -> dependsOn(plugin.getDescription(), coreName))
                .forEach(addons::add);

        for (String pluginName : explicitDeclarations.keySet()) {
            Arrays.stream(owner.getServer().getPluginManager().getPlugins())
                    .filter(plugin -> key(plugin.getName()).equals(pluginName))
                    .findFirst()
                    .ifPresent(addons::add);
        }
        return addons;
    }

    private ResolvedDeclaration resolveDeclaration(Plugin plugin) {
        AddonCompatibilityDeclaration explicit = explicitDeclarations.get(key(plugin.getName()));
        if (explicit != null) {
            return new ResolvedDeclaration(explicit, AddonCompatibilitySource.EXPLICIT_REGISTRATION, null);
        }

        if (plugin instanceof AddonCompatibilityProvider provider) {
            try {
                AddonCompatibilityDeclaration declaration = provider.getAddonCompatibilityDeclaration();
                if (declaration == null) {
                    return new ResolvedDeclaration(
                            null,
                            AddonCompatibilitySource.PROVIDER_INTERFACE,
                            "Compatibility provider returned null");
                }
                return new ResolvedDeclaration(
                        declaration, AddonCompatibilitySource.PROVIDER_INTERFACE, null);
            } catch (RuntimeException | LinkageError error) {
                if (runtimeHealthService != null) {
                    runtimeHealthService.recordFailure(plugin, "compatibility-provider", error);
                }
                return new ResolvedDeclaration(
                        null,
                        AddonCompatibilitySource.PROVIDER_INTERFACE,
                        "Compatibility provider failed: " + error.getClass().getSimpleName());
            }
        }

        AddonCompatibilityManifestReader.ManifestReadResult manifest = manifestReader.read(plugin);
        if (manifest.present()) {
            return new ResolvedDeclaration(
                    manifest.declaration(), AddonCompatibilitySource.EMBEDDED_MANIFEST, manifest.error());
        }
        return new ResolvedDeclaration(null, AddonCompatibilitySource.NONE, null);
    }

    private static AddonCompatibilityResult result(
            Plugin plugin,
            AddonCompatibilityStatus status,
            ResolvedDeclaration resolved,
            List<String> messages) {
        return new AddonCompatibilityResult(
                plugin.getName(),
                plugin.getDescription().getVersion(),
                status,
                resolved.source(),
                resolved.declaration(),
                messages);
    }

    private static boolean dependsOn(PluginDescriptionFile description, String coreName) {
        return containsIgnoreCase(description.getDepend(), coreName)
                || containsIgnoreCase(description.getSoftDepend(), coreName);
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private static String joinCoreNames(Set<SlimefunCoreVariant> variants) {
        return variants.stream()
                .map(SlimefunCoreVariant::getDisplayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private static String key(String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT);
    }

    private record ResolvedDeclaration(
            AddonCompatibilityDeclaration declaration, AddonCompatibilitySource source, String error) {
        java.util.Optional<String> errorMessage() {
            return java.util.Optional.ofNullable(error);
        }
    }
}
