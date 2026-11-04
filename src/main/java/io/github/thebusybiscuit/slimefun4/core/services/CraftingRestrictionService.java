package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nullable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Server-controlled crafting restrictions. This service deliberately does not disable, unregister,
 * replace, or invalidate Slimefun items: it only answers whether a new recipe may be crafted.
 * Addons implementing their own production logic can consult this service before consuming inputs.
 */
public final class CraftingRestrictionService {

    private static final String FILE_NAME = "CraftingRestrictions.yml";
    private static final String DEFAULT_MESSAGE = "&cCrafting is disabled for %item%!";

    private final Slimefun plugin;
    private volatile Snapshot snapshot = new Snapshot(false, DEFAULT_MESSAGE, Map.of());

    public CraftingRestrictionService(Slimefun plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads the independent restrictions file. On a malformed file the last valid snapshot is
     * retained, so a typo cannot silently reset an existing server policy.
     *
     * @return true if the configuration was read successfully
     */
    public boolean reload() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);

        try {
            if (!file.isFile()) {
                plugin.saveResource(FILE_NAME, false);
            }

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            Snapshot loaded = parse(yaml);
            snapshot = loaded;
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to load " + FILE_NAME + "; retaining previous policy", exception);
            return false;
        }
    }

    /**
     * Whether crafting the registered item ID is restricted. IDs are global across Slimefun and
     * addons, and are compared case-insensitively for administrator convenience.
     */
    public boolean isBlocked(@Nullable String itemId) {
        return snapshot.isBlocked(itemId);
    }

    /** Checks the output's registered Slimefun identity, not its material or display name. */
    public boolean isBlocked(@Nullable ItemStack output) {
        SlimefunItem item = output == null ? null : SlimefunItem.getByItem(output);
        return item != null && isBlocked(item.getId());
    }

    /** Blocks multi-output machine recipes when any result has a restricted Slimefun ID. */
    public boolean isBlockedAny(@Nullable ItemStack[] outputs) {
        if (outputs != null) {
            for (ItemStack output : outputs) {
                if (isBlocked(output)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks and reports a crafting denial, without changing the output or consuming ingredients.
     * Safe to use from addon crafting handlers running in the player's owning tick/region.
     *
     * @return true if the requested craft must be cancelled
     */
    public boolean denyIfBlocked(@Nullable Player player, @Nullable ItemStack output) {
        SlimefunItem item = output == null ? null : SlimefunItem.getByItem(output);
        if (item == null) {
            return false;
        }

        Snapshot current = snapshot;
        if (!current.isBlocked(item.getId())) {
            return false;
        }

        if (player != null) {
            String message = current.denialMessage(item.getId(), item.getItemName());
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        }

        return true;
    }

    static Snapshot parse(ConfigurationSection yaml) {
        boolean enabled = yaml.getBoolean("enabled", true);
        String defaultMessage = yaml.getString("default-message", DEFAULT_MESSAGE);
        if (defaultMessage == null || defaultMessage.isBlank()) {
            defaultMessage = DEFAULT_MESSAGE;
        }

        if (yaml.contains("items") && !yaml.isConfigurationSection("items")) {
            throw new IllegalArgumentException("'items' must be a YAML configuration section");
        }

        Map<String, String> blocked = new HashMap<>();
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection entry = items.getConfigurationSection(key);
                if (entry == null) {
                    throw new IllegalArgumentException("Item '" + key + "' must contain crafting-enabled and message");
                }

                if (!entry.getBoolean("crafting-enabled", true)) {
                    String message = entry.getString("message", "");
                    blocked.put(normalizeId(key), message == null ? "" : message);
                }
            }
        }

        return new Snapshot(enabled, defaultMessage, Map.copyOf(blocked));
    }

    private static String normalizeId(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    static final class Snapshot {
        private final boolean enabled;
        private final String defaultMessage;
        private final Map<String, String> blockedMessages;

        private Snapshot(boolean enabled, String defaultMessage, Map<String, String> blockedMessages) {
            this.enabled = enabled;
            this.defaultMessage = defaultMessage;
            this.blockedMessages = blockedMessages;
        }

        boolean isBlocked(@Nullable String id) {
            return enabled && id != null && blockedMessages.containsKey(normalizeId(id));
        }

        String denialMessage(String id, String displayName) {
            String message = blockedMessages.getOrDefault(normalizeId(id), "");
            if (message.isBlank()) {
                message = defaultMessage;
            }
            return message.replace("%item%", displayName).replace("%id%", id);
        }
    }
}
