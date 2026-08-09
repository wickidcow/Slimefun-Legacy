package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.papermc.paper.plugin.configuration.PluginMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Runtime diagnostics for Slimefun Legacy's GuizhanLib compatibility bridge.
 *
 * <p>The bridge intentionally exposes the GuizhanLib library API without pretending that the Slimefun main class is
 * the GuizhanLibPlugin main class. Paper's {@code provides} alias can satisfy plugin dependency resolution when the
 * external plugin is absent, while this service makes the exact runtime mode visible to operators.
 */
public final class GuizhanLibCompatibilityBridge {

    public static final String PROVIDED_PLUGIN = "GuizhanLibPlugin";
    public static final String COMPATIBILITY_VERSION = "2.5.0";

    private static final List<String> API_PROBES = List.of(
            "net.guizhanss.guizhanlib.common.Cooldown",
            "net.guizhanss.guizhanlib.minecraft.utils.ItemUtil",
            "net.guizhanss.guizhanlib.slimefun.addon.AbstractAddon",
            "net.guizhanss.guizhanlib.slimefun.machines.MenuBlock",
            "net.guizhanss.guizhanlib.updater.GuizhanBuildsUpdater");

    private static final List<String> LEGACY_COMPATIBILITY_PROBES = List.of(
            "net.guizhanss.minecraft.guizhanlib.gugu.minecraft.ChatColors",
            "net.guizhanss.minecraft.guizhanlib.utils.NamespacedKeyUtils",
            "net.guizhanss.minecraft.guizhanlib.updater.GuizhanUpdater",
            "net.guizhanss.guizhanlibplugin.updater.GuizhanUpdater");

    private GuizhanLibCompatibilityBridge() {}

    /**
     * Inspects the current GuizhanLib dependency/provider state.
     *
     * @param owner the Slimefun Legacy plugin instance
     * @return an immutable bridge snapshot
     */
    public static @Nonnull Snapshot inspect(@Nonnull Plugin owner) {
        Objects.requireNonNull(owner, "owner");

        PluginManager pluginManager = owner.getServer().getPluginManager();
        Plugin resolvedProvider = pluginManager.getPlugin(PROVIDED_PLUGIN);
        Plugin externalPlugin = findExternalPlugin(pluginManager, owner);

        ClassLoader classLoader = owner.getClass().getClassLoader();
        List<String> missingApiClasses = missingClasses(classLoader, API_PROBES);
        List<String> missingLegacyClasses = missingClasses(classLoader, LEGACY_COMPATIBILITY_PROBES);

        List<String> hardDependents = new ArrayList<>();
        List<String> softDependents = new ArrayList<>();
        for (Plugin candidate : pluginManager.getPlugins()) {
            if (candidate == owner) {
                continue;
            }

            PluginMeta meta = candidate.getPluginMeta();
            if (containsIgnoreCase(meta.getPluginDependencies(), PROVIDED_PLUGIN)) {
                hardDependents.add(meta.getName());
            }
            if (containsIgnoreCase(meta.getPluginSoftDependencies(), PROVIDED_PLUGIN)) {
                softDependents.add(meta.getName());
            }
        }
        hardDependents.sort(String.CASE_INSENSITIVE_ORDER);
        softDependents.sort(String.CASE_INSENSITIVE_ORDER);

        boolean aliasRoutesToLegacy = resolvedProvider == owner;
        boolean externalInstalled = externalPlugin != null;
        boolean publicApiReady = missingApiClasses.isEmpty();
        boolean legacyCompatibilityReady = missingLegacyClasses.isEmpty();
        boolean fallbackReady = !externalInstalled && aliasRoutesToLegacy && publicApiReady && legacyCompatibilityReady;

        return new Snapshot(
                externalInstalled,
                externalPlugin == null ? "" : externalPlugin.getPluginMeta().getVersion(),
                resolvedProvider == null ? "" : resolvedProvider.getPluginMeta().getName(),
                aliasRoutesToLegacy,
                publicApiReady,
                legacyCompatibilityReady,
                fallbackReady,
                List.copyOf(missingApiClasses),
                List.copyOf(missingLegacyClasses),
                List.copyOf(hardDependents),
                List.copyOf(softDependents));
    }

    private static Plugin findExternalPlugin(PluginManager pluginManager, Plugin owner) {
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (plugin != owner && plugin.getPluginMeta().getName().equalsIgnoreCase(PROVIDED_PLUGIN)) {
                return plugin;
            }
        }
        return null;
    }

    private static List<String> missingClasses(ClassLoader classLoader, List<String> probes) {
        List<String> missing = new ArrayList<>();
        for (String className : probes) {
            try {
                Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | LinkageError ex) {
                missing.add(className);
            }
        }
        return missing;
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        String normalized = expected.toLowerCase(Locale.ROOT);
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    /** Immutable operator-facing snapshot of the bridge state. */
    public record Snapshot(
            boolean externalPluginInstalled,
            @Nonnull String externalPluginVersion,
            @Nonnull String resolvedProviderName,
            boolean aliasRoutesToLegacy,
            boolean publicApiReady,
            boolean legacyCompatibilityReady,
            boolean fallbackReady,
            @Nonnull List<String> missingApiClasses,
            @Nonnull List<String> missingLegacyClasses,
            @Nonnull List<String> hardDependents,
            @Nonnull List<String> softDependents) {

        public Snapshot {
            Objects.requireNonNull(externalPluginVersion, "externalPluginVersion");
            Objects.requireNonNull(resolvedProviderName, "resolvedProviderName");
            missingApiClasses = List.copyOf(Objects.requireNonNull(missingApiClasses, "missingApiClasses"));
            missingLegacyClasses = List.copyOf(Objects.requireNonNull(missingLegacyClasses, "missingLegacyClasses"));
            hardDependents = List.copyOf(Objects.requireNonNull(hardDependents, "hardDependents"));
            softDependents = List.copyOf(Objects.requireNonNull(softDependents, "softDependents"));
        }
    }
}
