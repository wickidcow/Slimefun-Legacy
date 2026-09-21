package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sends an externally hosted resource pack to players when a server owner explicitly enables it.
 *
 * <p>Slimefun Legacy does not host, upload or download a resource pack itself. The service is disabled by default and
 * only adds the configured external pack to the player's existing resource-pack stack. This allows servers using
 * ItemsAdder or another pack manager to remain fully in control unless they opt in.
 */
public final class ExternalResourcePackService {

    private static final String CONFIG_ROOT = "resource-pack.";
    private static final String DEFAULT_PACK_URL =
            "https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip";
    private static final String PREVIOUS_HOSTED_PACK_URL =
            "http://overlord.kicks-ass.org:8163/SlimefunLegacyRP.zip";
    private static final String PREVIOUS_UNOFFICIAL_PACK_URL =
            "https://github.com/wickidcow/SFL_ResourePack_UnOfficial/releases/latest/download/SlimefunLegacyRP.zip";
    private static final String PREVIOUS_LEGACY_REPO_PACK_URL =
            "https://github.com/wickidcow/Slimefun-Legacy/releases/latest/download/SlimefunLegacy-ResourcePack-1.21.11-26.3.zip";
    private static final String RETIRED_DEFAULT_PACK_URL =
            "https://cdn.modrinth.com/data/TznkVJky/versions/nwij66MR/Slimefun-ResourcePack.zip";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes(
            "slimefun-legacy:external-resource-pack".getBytes(StandardCharsets.UTF_8));

    private final Slimefun plugin;
    private final NamespacedKey playerOptOutKey;
    private String lastWarning;

    public ExternalResourcePackService(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        playerOptOutKey = new NamespacedKey(plugin, "resource_pack_opt_out");
    }

    /**
     * Adds the configured resource pack to this player when external pack delivery is enabled.
     *
     * @param player The player that just joined
     */
    public void sendIfEnabled(@Nonnull Player player) {
        if (!isDeliveryEnabled() || (!isRequired() && !isPlayerEnabled(player))) {
            return;
        }

        sendConfiguredPack(player);
    }

    /**
     * Returns whether Slimefun Legacy's own external pack sender is enabled by the server owner.
     */
    public boolean isDeliveryEnabled() {
        return CuriositiesConfig.getConfig().getBoolean(CONFIG_ROOT + "enabled");
    }

    /**
     * Enables or disables Slimefun Legacy's own resource-pack sender immediately.
     *
     * <p>Turning this on re-sends the configured Legacy pack to eligible online players. Turning it off removes only
     * Slimefun Legacy's pack UUID from online players. This never changes item-models.yml or stored Slimefun items.</p>
     *
     * @return whether the setting was saved successfully
     */
    public boolean setDeliveryEnabled(boolean enabled) {
        if (!CuriositiesConfig.getConfig().setResourcePackEnabled(enabled)) {
            return false;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (enabled) {
                if (isRequired() || isPlayerEnabled(player)) {
                    sendConfiguredPack(player);
                }
            } else {
                removeConfiguredPack(player);
            }
        }

        return true;
    }

    /**
     * Returns whether the configured Slimefun Legacy pack is mandatory for clients.
     */
    public boolean isRequired() {
        return CuriositiesConfig.getConfig().getBoolean(CONFIG_ROOT + "required");
    }

    /** Returns the configured pack URL after normalizing retired Legacy defaults. */
    public @Nonnull String getEffectivePackUrl() {
        return normalizeLegacyResourcePackUrl(trim(CuriositiesConfig.getConfig().getString(CONFIG_ROOT + "url")));
    }

    /** Returns whether the configured effective pack URL is a valid absolute HTTP(S) URL. */
    public boolean isConfiguredUrlValid() {
        return isValidResourcePackUrl(getEffectivePackUrl());
    }

    /** Returns whether a SHA-1 was explicitly configured for the current pack. */
    public boolean hasConfiguredSha1() {
        return !trim(CuriositiesConfig.getConfig().getString(CONFIG_ROOT + "sha1")).isEmpty();
    }

    /**
     * Returns whether the configured SHA-1 is usable.
     *
     * <p>An empty SHA-1 is valid because Minecraft accepts pack requests without an explicit hash.</p>
     */
    public boolean isConfiguredSha1Valid() {
        String configured = trim(CuriositiesConfig.getConfig().getString(CONFIG_ROOT + "sha1"));
        return configured.isEmpty() || parseSha1(configured) != null;
    }

    /**
     * Sends the currently configured Legacy pack to one administrator without changing their saved opt-in preference.
     *
     * <p>This intentionally works even when server-wide Legacy delivery is disabled so an operator can test the URL
     * and client behavior before enabling it for everyone.</p>
     */
    public boolean testForPlayer(@Nonnull Player player) {
        return sendConfiguredPack(player);
    }

    /**
     * Returns whether this player has left automatic Slimefun Legacy pack delivery enabled.
     *
     * <p>This preference only controls Slimefun Legacy's own pack UUID. It never removes or disables
     * resource packs owned by ItemsAdder, Oraxen, a proxy, or another plugin.</p>
     */
    public boolean isPlayerEnabled(@Nonnull Player player) {
        Byte optedOut = player.getPersistentDataContainer().get(playerOptOutKey, PersistentDataType.BYTE);
        return optedOut == null || optedOut == 0;
    }

    /**
     * Changes the player's persistent automatic-delivery preference.
     *
     * @return {@code false} when the server marks the pack as required and an opt-out was requested
     */
    public boolean setPlayerEnabled(@Nonnull Player player, boolean enabled) {
        if (!enabled && isRequired()) {
            return false;
        }

        if (enabled) {
            player.getPersistentDataContainer().remove(playerOptOutKey);
            if (isDeliveryEnabled()) {
                sendConfiguredPack(player);
            }
        } else {
            player.getPersistentDataContainer().set(playerOptOutKey, PersistentDataType.BYTE, (byte) 1);
            removeFromPlayer(player);
        }

        return true;
    }

    /**
     * Re-sends the configured Slimefun Legacy pack to a player and clears their optional opt-out.
     *
     * @return whether a pack request was sent
     */
    public boolean reloadForPlayer(@Nonnull Player player) {
        if (!isDeliveryEnabled()) {
            return false;
        }

        player.getPersistentDataContainer().remove(playerOptOutKey);
        return sendConfiguredPack(player);
    }

    /**
     * Removes only Slimefun Legacy's own resource-pack UUID from this player's active pack stack.
     *
     * @return whether the removal request was allowed
     */
    public boolean removeFromPlayer(@Nonnull Player player) {
        if (isRequired()) {
            return false;
        }

        return removeConfiguredPack(player);
    }

    private boolean removeConfiguredPack(@Nonnull Player player) {
        try {
            player.removeResourcePack(PACK_ID);
            return true;
        } catch (LinkageError ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Resource-pack removal is unavailable on this server implementation. Slimefun will continue normally.",
                    ex);
            return false;
        }
    }

    private boolean sendConfiguredPack(@Nonnull Player player) {
        var config = CuriositiesConfig.getConfig();

        String configuredUrl = trim(config.getString(CONFIG_ROOT + "url"));
        String url = normalizeLegacyResourcePackUrl(configuredUrl);
        boolean normalizedLegacyUrl = !configuredUrl.equals(url);
        if (normalizedLegacyUrl) {
            warnOnce(
                    "An older Slimefun Legacy resource-pack URL was detected. Slimefun Legacy is using the current "
                            + "GitHub-hosted pack instead: " + DEFAULT_PACK_URL);
        }

        if (!isValidResourcePackUrl(url)) {
            warnOnce("External resource-pack delivery is enabled, but configSFLAddons.yml resource-pack.url is not a valid HTTP(S) URL.");
            return false;
        }

        // Never pair a replacement GitHub URL with a checksum that belonged to a retired pack.
        String configuredHash = normalizedLegacyUrl ? "" : trim(config.getString(CONFIG_ROOT + "sha1"));
        byte[] hash = parseSha1(configuredHash);
        if (!configuredHash.isEmpty() && hash == null) {
            warnOnce("External resource-pack delivery is enabled, but configSFLAddons.yml resource-pack.sha1 is not a 40-character SHA-1 hash.");
            return false;
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
            return true;
        } catch (IllegalArgumentException ex) {
            warnOnce("Could not send the configured external resource pack: " + ex.getMessage());
        } catch (LinkageError ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "External resource-pack delivery is unavailable on this server implementation. Slimefun will continue without sending a pack.",
                    ex);
        }

        return false;
    }

    @Nonnull
    static String normalizeLegacyResourcePackUrl(@Nonnull String value) {
        if (RETIRED_DEFAULT_PACK_URL.equals(value)
                || PREVIOUS_HOSTED_PACK_URL.equals(value)
                || PREVIOUS_UNOFFICIAL_PACK_URL.equals(value)
                || PREVIOUS_LEGACY_REPO_PACK_URL.equals(value)) {
            return DEFAULT_PACK_URL;
        }

        return value;
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
