package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.thebusybiscuit.slimefun4.api.addons.AddonCompatibilityDeclaration;
import io.github.thebusybiscuit.slimefun4.api.addons.SlimefunCoreVariant;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.platform.MinecraftVersionNumber;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformFamily;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformRequirements;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/** Reads the optional addon compatibility manifest without loading addon implementation classes. */
@SlimefunInternal
final class AddonCompatibilityManifestReader {

    static final String MANIFEST_PATH = "slimefun-compatibility.json";
    private static final int SUPPORTED_SCHEMA = 1;

    ManifestReadResult read(@Nonnull Plugin plugin) {
        try {
            URI location = plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path source = Path.of(location);
            if (Files.isDirectory(source)) {
                Path manifest = source.resolve(MANIFEST_PATH);
                if (!Files.isRegularFile(manifest)) {
                    return ManifestReadResult.absent();
                }
                try (InputStream stream = Files.newInputStream(manifest)) {
                    return parse(stream);
                }
            }

            if (!Files.isRegularFile(source)) {
                return ManifestReadResult.absent();
            }
            try (ZipFile zip = new ZipFile(source.toFile())) {
                ZipEntry entry = zip.getEntry(MANIFEST_PATH);
                if (entry == null) {
                    return ManifestReadResult.absent();
                }
                try (InputStream stream = zip.getInputStream(entry)) {
                    return parse(stream);
                }
            }
        } catch (Exception | LinkageError error) {
            return ManifestReadResult.invalid("Unable to read " + MANIFEST_PATH + ": " + error.getMessage());
        }
    }

    ManifestReadResult parse(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return ManifestReadResult.invalid("Compatibility manifest root must be an object");
            }
            JsonObject object = root.getAsJsonObject();
            int schema = integer(object, "schema", -1);
            if (schema != SUPPORTED_SCHEMA) {
                return ManifestReadResult.invalid(
                        "Unsupported compatibility manifest schema " + schema + " (expected " + SUPPORTED_SCHEMA + ")");
            }

            AddonCompatibilityDeclaration.Builder declaration = AddonCompatibilityDeclaration.builder();
            PlatformRequirements.Builder platform = PlatformRequirements.builder();

            for (String value : strings(object, "tested_core_variants")) {
                SlimefunCoreVariant variant = SlimefunCoreVariant.fromId(value)
                        .filter(candidate -> candidate != SlimefunCoreVariant.UNKNOWN)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown core variant: " + value));
                declaration.testCore(variant);
            }

            String minimumMinecraft = string(object, "minimum_minecraft").orElse(null);
            if (minimumMinecraft != null) {
                MinecraftVersionNumber version = MinecraftVersionNumber.parse(minimumMinecraft)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Invalid minimum_minecraft version: " + minimumMinecraft));
                platform.minimumMinecraftVersion(version);
            }

            int minimumJava = integer(object, "minimum_java", 0);
            if (minimumJava > 0) {
                platform.minimumJavaVersion(minimumJava);
            }

            for (String value : strings(object, "required_capabilities")) {
                platform.requireCapability(enumValue(PlatformCapability.class, value, "platform capability"));
            }
            for (String value : strings(object, "accepted_platform_families")) {
                platform.acceptFamily(enumValue(PlatformFamily.class, value, "platform family"));
            }
            for (String value : strings(object, "required_plugins")) {
                declaration.requirePlugin(value);
            }
            for (String value : strings(object, "optional_plugins")) {
                declaration.optionalPlugin(value);
            }
            string(object, "notes").ifPresent(declaration::notes);

            declaration.platformRequirements(platform.build());
            return ManifestReadResult.present(declaration.build());
        } catch (JsonParseException | IllegalArgumentException error) {
            return ManifestReadResult.invalid("Invalid compatibility manifest: " + error.getMessage());
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return element.getAsInt();
    }

    private static Optional<String> string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static Iterable<String> strings(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return java.util.List.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        java.util.List<String> values = new java.util.ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " must contain only strings");
            }
            String value = entry.getAsString().trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(key + " cannot contain blank values");
            }
            values.add(value);
        }
        return values;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unknown " + label + ": " + value, error);
        }
    }

    record ManifestReadResult(AddonCompatibilityDeclaration declaration, String error, boolean present) {

        static ManifestReadResult absent() {
            return new ManifestReadResult(null, null, false);
        }

        static ManifestReadResult present(AddonCompatibilityDeclaration declaration) {
            return new ManifestReadResult(declaration, null, true);
        }

        static ManifestReadResult invalid(String error) {
            return new ManifestReadResult(null, error, true);
        }
    }
}
