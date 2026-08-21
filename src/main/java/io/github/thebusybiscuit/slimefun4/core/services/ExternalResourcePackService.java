package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.entity.Player;

/**
 * Sends an externally hosted resource pack to players when a server owner explicitly enables it.
 *
 * <p>Slimefun Legacy does not host, upload or download a resource pack itself. The service is disabled by default and
 * only adds the configured external pack to the player's existing resource-pack stack. This allows servers using
 * ItemsAdder or another pack manager to remain fully in control unless they opt in.
 */
public final class ExternalResourcePackService {

    private static final String CONFIG_ROOT = "resource-pack.";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes(
            "slimefun-legacy:external-resource-pack".getBytes(StandardCharsets.UTF_8));

    private final Slimefun plugin;
    private String lastWarning;

    public ExternalResourcePackService(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    /**
     * Adds the configured resource pack to this player when external pack delivery is enabled.
     *
     * @param player The player that just joined
     */
    public void sendIfEnabled(@Nonnull Player player) {
        Config config = Slimefun.getCfg();
        if (!config.getBoolean(CONFIG_ROOT + "enabled")) {
            return;
        }

        String url = trim(config.getString(CONFIG_ROOT + "url"));
        if (!isValidResourcePackUrl(url)) {
            warnOnce("External resource-pack delivery is enabled, but resource-pack.url is not a valid HTTP(S) URL.");
            return;
        }

        String configuredHash = trim(config.getString(CONFIG_ROOT + "sha1"));
        byte[] hash = parseSha1(configuredHash);
        if (!configuredHash.isEmpty() && hash == null) {
            warnOnce("External resource-pack delivery is enabled, but resource-pack.sha1 is not a 40-character SHA-1 hash.");
            return;
        }

        String prompt = trim(config.getString(CONFIG_ROOT + "prompt"));
        if (prompt.isEmpty()) {
            prompt = null;
        }

        boolean required = config.getBoolean(CONFIG_ROOT + "required");

        try {
            // addResourcePack stacks this pack with an existing server/ItemsAdder pack instead of replacing it.
            // This API is available on the supported 1.21.11+ server line and current Paper releases.
            player.addResourcePack(PACK_ID, url, hash, prompt, required);
        } catch (IllegalArgumentException ex) {
            warnOnce("Could not send the configured external resource pack: " + ex.getMessage());
        } catch (LinkageError ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "External resource-pack delivery is unavailable on this server implementation. Slimefun will continue without sending a pack.",
                    ex);
        }
    }

    static byte[] parseSha1(@Nonnull String value) {
        if (value.isEmpty()) {
            return null;
        }

        if (!value.matches("(?i)[0-9a-f]{40}")) {
            return null;
        }

        return HexFormat.of().parseHex(value);
    }

    static boolean isValidResourcePackUrl(@Nonnull String value) {
        if (value.isEmpty() || !StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
            return false;
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private void warnOnce(@Nonnull String warning) {
        if (!warning.equals(lastWarning)) {
            lastWarning = warning;
            plugin.getLogger().warning(warning);
        }
    }

    @Nonnull
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
