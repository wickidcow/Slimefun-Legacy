package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.KnownAddonCompatibilityRegistry;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** Checks and stages Slimefun Legacy release updates. */
class UpdateCommand extends SubCommand {

    private static final String PERMISSION = "slimefun.command.update";
    private static final String RELEASE_API =
            "https://api.github.com/repos/wickidcow/Slimefun-Legacy/releases/latest";
    private static final String BUNDLE_ASSET = "SFL_AddonsNDependencies.zip";
    private static final String USER_AGENT = "Slimefun-Legacy-Updater";
    private static final int MAX_DOWNLOAD_BYTES = 256 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final KnownAddonCompatibilityRegistry knownAddons;

    @ParametersAreNonnullByDefault
    UpdateCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "update", false);
        knownAddons = KnownAddonCompatibilityRegistry.load(UpdateCommand.class.getClassLoader());
    }

    @Override
    protected @Nonnull String getDescription() {
        return "Checks or stages Slimefun Legacy and maintained addon updates";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission(PERMISSION)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "check";
        if (!action.equals("check") && !action.equals("install")) {
            sender.sendMessage(ChatColors.color("&eUsage: /sf update [check|install]"));
            return;
        }

        sender.sendMessage(ChatColors.color(action.equals("install")
                ? "&6[Slimefun Legacy] &eChecking and preparing updates..."
                : "&6[Slimefun Legacy] &eChecking for updates..."));
        boolean install = action.equals("install");
        Slimefun.getSchedulerService().runAsync(() -> runUpdate(sender, install));
    }

    private void runUpdate(@Nonnull CommandSender sender, boolean install) {
        try {
            Release release = fetchRelease();
            byte[] bundleBytes = download(release.bundleUrl());
            Map<String, BundlePlugin> bundledPlugins = readBundle(bundleBytes);
            List<UpdateCandidate> updates = findUpdates(release, bundledPlugins);

            if (updates.isEmpty()) {
                send(sender, "&aSlimefun Legacy and installed maintained addons are up to date. &7Latest release: &f"
                        + displayVersion(release.tag()));
                return;
            }

            send(sender, "&6Available Slimefun Legacy updates &7(" + updates.size() + "):");
            for (UpdateCandidate update : updates) {
                send(sender, "&e- &f" + update.displayName() + " &7" + displayVersion(update.currentVersion()) + " &8→ &a"
                        + displayVersion(update.latestVersion()));
            }

            if (!install) {
                send(sender, "&7Run &e/sf update install &7to stage these updates for the next server restart.");
                return;
            }

            File updateFolder = plugin.getServer().getUpdateFolderFile();
            Files.createDirectories(updateFolder.toPath());
            int staged = 0;
            for (UpdateCandidate update : updates) {
                byte[] jarBytes = update.core() ? download(release.coreJarUrl()) : update.jarBytes();
                PluginDescriptor descriptor = readPluginDescriptor(jarBytes);
                if (!normalize(descriptor.name()).equals(normalize(update.expectedPluginName()))) {
                    throw new IOException("Refusing " + update.displayName() + ": downloaded JAR declares plugin name '"
                            + descriptor.name() + "'");
                }
                stage(updateFolder.toPath(), update.fileName(), jarBytes);
                staged++;
            }

            send(sender, "&aStaged &f" + staged + " &aupdate(s) in &f" + updateFolder.getPath() + "&a.");
            send(sender, "&eRestart the server normally to apply them. &cDo not /reload or hot-load the JARs.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(sender, "&cUpdate check was interrupted.");
        } catch (Exception e) {
            plugin.getLogger().warning("Slimefun Legacy update check failed: " + e.getClass().getSimpleName() + ": "
                    + e.getMessage());
            send(sender, "&cUpdate check failed: &f" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " &8- &7" + e.getMessage()));
        }
    }

    private @Nonnull Release fetchRelease() throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(RELEASE_API))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String tag = string(root, "tag_name");
        boolean draft = root.has("draft") && root.get("draft").getAsBoolean();
        boolean prerelease = root.has("prerelease") && root.get("prerelease").getAsBoolean();
        if (tag.isBlank() || draft || prerelease) {
            throw new IOException("Latest GitHub release is not a stable published release");
        }

        String coreUrl = null;
        String coreName = null;
        String bundleUrl = null;
        JsonArray assets = root.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement element : assets) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject asset = element.getAsJsonObject();
                String name = string(asset, "name");
                String url = string(asset, "browser_download_url");
                if (name.equals(BUNDLE_ASSET)) {
                    bundleUrl = url;
                } else if (name.startsWith("Slimefun-Legacy") && name.endsWith(".jar")) {
                    coreName = name;
                    coreUrl = url;
                }
            }
        }
        if (coreUrl == null || coreName == null || bundleUrl == null) {
            throw new IOException("Latest release is missing the Slimefun Legacy JAR or addon bundle");
        }
        return new Release(tag, coreName, checkedReleaseUrl(coreUrl), checkedReleaseUrl(bundleUrl));
    }

    private @Nonnull List<UpdateCandidate> findUpdates(
            @Nonnull Release release, @Nonnull Map<String, BundlePlugin> bundle) {
        List<UpdateCandidate> updates = new ArrayList<>();
        if (compareVersions(release.tag(), Slimefun.getVersion()) > 0) {
            updates.add(new UpdateCandidate(
                    "Slimefun Legacy", "Slimefun", Slimefun.getVersion(), release.tag(), release.coreFileName(), null, true));
        }

        for (Plugin addon : Slimefun.getInstalledAddons()) {
            Optional<KnownAddonCompatibilityRegistry.KnownAddonSupport> support = knownAddons.find(addon.getName());
            if (support.isEmpty() || !support.orElseThrow().isLegacyMaintained()) {
                continue;
            }
            BundlePlugin bundled = bundle.get(normalize(addon.getName()));
            if (bundled == null || compareVersions(bundled.version(), addon.getDescription().getVersion()) <= 0) {
                continue;
            }
            updates.add(new UpdateCandidate(
                    support.orElseThrow().displayName(),
                    addon.getName(),
                    addon.getDescription().getVersion(),
                    bundled.version(),
                    bundled.fileName(),
                    bundled.jarBytes(),
                    false));
        }
        return updates;
    }

    private @Nonnull Map<String, BundlePlugin> readBundle(byte[] bundleBytes) throws IOException {
        Map<String, BundlePlugin> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundleBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                byte[] jar = readEntry(zip);
                PluginDescriptor descriptor;
                try {
                    descriptor = readPluginDescriptor(jar);
                } catch (IOException ignored) {
                    continue;
                }
                result.put(normalize(descriptor.name()), new BundlePlugin(
                        Path.of(entry.getName()).getFileName().toString(), descriptor.name(), descriptor.version(), jar));
            }
        }
        return result;
    }

    private @Nonnull PluginDescriptor readPluginDescriptor(byte[] jarBytes) throws IOException {
        try (ZipInputStream jar = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = jar.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && (entry.getName().equals("plugin.yml") || entry.getName().equals("paper-plugin.yml"))) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(new ByteArrayInputStream(readEntry(jar)), StandardCharsets.UTF_8));
                    String name = yaml.getString("name", "").trim();
                    String version = yaml.getString("version", "").trim();
                    if (name.isEmpty() || version.isEmpty()) {
                        throw new IOException("Plugin descriptor is missing name/version");
                    }
                    return new PluginDescriptor(name, version);
                }
            }
        }
        throw new IOException("Downloaded JAR has no plugin descriptor");
    }

    private byte[] download(@Nonnull String url) throws IOException, InterruptedException {
        URI uri = URI.create(checkedReleaseUrl(url));
        HttpResponse<byte[]> response = HTTP.send(
                HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download returned HTTP " + response.statusCode());
        }
        if (response.body().length == 0 || response.body().length > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Download size is outside the allowed range");
        }
        return response.body();
    }

    private static @Nonnull String checkedReleaseUrl(@Nonnull String url) throws IOException {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !(host.equalsIgnoreCase("github.com") || host.endsWith(".githubusercontent.com"))) {
            throw new IOException("Refusing non-GitHub release URL");
        }
        return url;
    }

    private static byte[] readEntry(ZipInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            if (output.size() > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Archive entry exceeds the allowed size");
            }
        }
        return output.toByteArray();
    }

    private static void stage(Path updateFolder, String fileName, byte[] jarBytes) throws IOException {
        String safeName = Path.of(fileName).getFileName().toString();
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Refusing non-JAR update asset");
        }
        Path target = updateFolder.resolve(safeName);
        Path temp = Files.createTempFile(updateFolder, ".slimefun-update-", ".part");
        try {
            Files.write(temp, jarBytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static int compareVersions(String left, String right) {
        int[] l = parseVersion(left);
        int[] r = parseVersion(right);
        if (l.length == 0 || r.length == 0) {
            return 0;
        }
        int length = Math.max(l.length, r.length);
        for (int i = 0; i < length; i++) {
            int lv = i < l.length ? l[i] : 0;
            int rv = i < r.length ? r[i] : 0;
            if (lv != rv) {
                return Integer.compare(lv, rv);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        if (value == null) {
            return new int[0];
        }
        String normalized = value.trim();
        int start = 0;
        while (start < normalized.length() && !Character.isDigit(normalized.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < normalized.length()) {
            char c = normalized.charAt(end);
            if (!Character.isDigit(c) && c != '.') {
                break;
            }
            end++;
        }
        if (start >= end) {
            return new int[0];
        }
        String[] pieces = normalized.substring(start, end).split("\\.");
        int[] result = new int[pieces.length];
        try {
            for (int i = 0; i < pieces.length; i++) {
                result[i] = Integer.parseInt(pieces[i]);
            }
        } catch (NumberFormatException ignored) {
            return new int[0];
        }
        return result;
    }

    private static String displayVersion(String version) {
        int[] parsed = parseVersion(version);
        if (parsed.length == 0) {
            return version;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parsed.length; i++) {
            if (i > 0) {
                result.append('.');
            }
            result.append(parsed[i]);
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static void send(CommandSender sender, String message) {
        Slimefun.runSync(() -> sender.sendMessage(ChatColors.color(message)));
    }

    private record Release(String tag, String coreFileName, String coreJarUrl, String bundleUrl) {}

    private record PluginDescriptor(String name, String version) {}

    private record BundlePlugin(String fileName, String pluginName, String version, byte[] jarBytes) {}

    private record UpdateCandidate(
            String displayName,
            String expectedPluginName,
            String currentVersion,
            String latestVersion,
            String fileName,
            byte[] jarBytes,
            boolean core) {}
}
