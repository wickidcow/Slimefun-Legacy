package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Runtime recognition data for Slimefun addon families known to Slimefun Legacy.
 *
 * <p>Entries may be release-blocking CI targets, advisory CI targets, or recognition-only aliases. This registry
 * deliberately does not declare an installed addon compatible. Exact runtime compatibility still comes from an addon
 * declaration and the normal compatibility checks.
 */
@SlimefunInternal
public final class KnownAddonCompatibilityRegistry {

    public static final String RESOURCE_PATH = "compatibility/addon-support-registry.txt";

    private final Map<String, KnownAddonSupport> aliases;
    private final List<KnownAddonSupport> entries;

    private KnownAddonCompatibilityRegistry(
            @Nonnull Map<String, KnownAddonSupport> aliases, @Nonnull List<KnownAddonSupport> entries) {
        this.aliases = Map.copyOf(aliases);
        this.entries = List.copyOf(entries);
    }

    public static @Nonnull KnownAddonCompatibilityRegistry load(@Nonnull ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        InputStream stream = classLoader.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            return empty();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return parse(reader);
        } catch (IOException | RuntimeException ignored) {
            return empty();
        }
    }

    static @Nonnull KnownAddonCompatibilityRegistry parse(@Nonnull BufferedReader reader) throws IOException {
        Map<String, KnownAddonSupport> aliases = new LinkedHashMap<>();
        List<KnownAddonSupport> entries = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            String[] columns = trimmed.split("\\|", -1);
            if (columns.length != 4) {
                continue;
            }

            String slug = columns[0].trim();
            String tier = columns[1].trim();
            String displayName = columns[2].trim();
            if (slug.isEmpty() || tier.isEmpty() || displayName.isEmpty()) {
                continue;
            }

            KnownAddonSupport support = new KnownAddonSupport(slug, tier, displayName);
            entries.add(support);
            registerAlias(aliases, displayName, support);
            for (String alias : columns[3].split(",")) {
                registerAlias(aliases, alias, support);
            }
        }
        return new KnownAddonCompatibilityRegistry(aliases, entries);
    }

    private static void registerAlias(
            Map<String, KnownAddonSupport> aliases, String alias, KnownAddonSupport support) {
        String normalized = normalize(alias);
        if (!normalized.isEmpty()) {
            // Stronger evidence wins when multiple families share a runtime alias: required CI, advisory CI, then
            // recognition-only. This keeps Legacy forks preferred over upstream aliases without promoting recognition.
            KnownAddonSupport existing = aliases.get(normalized);
            if (existing == null || support.getTierPriority() > existing.getTierPriority()) {
                aliases.put(normalized, support);
            }
        }
    }

    public @Nonnull Optional<KnownAddonSupport> find(@Nonnull String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        return Optional.ofNullable(aliases.get(normalize(pluginName)));
    }

    public @Nonnull List<KnownAddonSupport> getEntries() {
        return entries;
    }

    static @Nonnull String normalize(@Nonnull String value) {
        String normalized = Objects.requireNonNull(value, "value")
                .toLowerCase(Locale.ROOT)
                .replace("legacy", "")
                .replace("upstream", "")
                .replaceAll("[^a-z0-9]", "");
        return normalized;
    }

    private static @Nonnull KnownAddonCompatibilityRegistry empty() {
        return new KnownAddonCompatibilityRegistry(Map.of(), List.of());
    }

    /** Runtime recognition metadata for one known addon family. */
    public record KnownAddonSupport(@Nonnull String slug, @Nonnull String tier, @Nonnull String displayName) {
        public KnownAddonSupport {
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(displayName, "displayName");
        }

        public boolean isRequired() {
            return tier.equalsIgnoreCase("required");
        }

        public boolean isCiMonitored() {
            return isRequired() || tier.equalsIgnoreCase("advisory");
        }

        public boolean isRecognizedOnly() {
            return tier.equalsIgnoreCase("recognized");
        }

        int getTierPriority() {
            if (isRequired()) {
                return 3;
            }
            if (tier.equalsIgnoreCase("advisory")) {
                return 2;
            }
            return isRecognizedOnly() ? 1 : 0;
        }

        public @Nonnull String getTierDisplayName() {
            if (isRequired()) {
                return "required compatibility target";
            }
            if (tier.equalsIgnoreCase("advisory")) {
                return "advisory compatibility target";
            }
            return "recognized addon family (not CI monitored)";
        }
    }
}
