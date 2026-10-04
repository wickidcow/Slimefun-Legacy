package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/**
 * This Service is responsible for applying custom model data to any {@link SlimefunItemStack}
 * if a Server Owner configured Slimefun to use those.
 * Modern Minecraft clients expose custom model data as a component. Slimefun Legacy keeps the
 * historical numeric item-model mapping format, but stores that numeric value as the first float
 * in {@link CustomModelDataComponent}. This is the modern equivalent of the old integer API.
 *
 * @author TheBusyBiscuit
 *
 */
public class CustomTextureService {

    private static final int SHARED_PAXEL_MODEL_DATA = 2201302;
    private static final String DEEPCORE_PAXEL_VISUAL_MIGRATION =
            "_SLIMEFUN_LEGACY_MIGRATIONS.DEEPCORE_PAXEL_VISUAL_SEPARATION";
    private static final String HOSTED_PACK_MODEL_MIGRATION =
            "_SLIMEFUN_LEGACY_MIGRATIONS.HOSTED_PACK_MODEL_MAP_2026_09";
    private static final String[] DEEPCORE_PAXEL_IDS = {
        "ADVENTURERS_DEEPCORE_PAXEL_3X3", "ADVENTURERS_DEEPCORE_PAXEL_5X5", "ADVENTURERS_DEEPCORE_PAXEL_9X9"
    };

    /**
     * The {@link Config} object in which the Server Owner can configure the item models.
     */
    private final Config config;

    /**
     * This nullable {@link StringBuffer} represents the "version" of the used item-models file.
     * This version is served with our resource pack.
     */
    private String version = null;

    /**
     * This boolean represents whether the file was modified anyway.
     * This is equivalent to at least one value being set to a number which
     * is not zero!
     */
    private boolean modified = false;

    private boolean defaultsLoaded = false;

    /**
     * This creates a new {@link CustomTextureService} for the provided {@link Config}
     *
     * @param config
     *            The {@link Config} to read custom model data from
     */
    public CustomTextureService(@Nonnull Config config) {
        this.config = config;
        config.getConfiguration()
                .options()
                .setHeader(List.of(
                        "This file is used to assign items from Slimefun or any of its addons",
                        "the 'CustomModelData' NBT tag. This can be used in conjunction with a custom resource pack",
                        "to give items custom textures.",
                        "0 means there is no data assigned to that item.",
                        "",
                        "New mappings default to 0. Existing mappings are preserved.",
                        "Pack delivery is configured separately in configSFLAddons.yml."))
                .parseComments(true);

        // SlimefunItemStack reads mappings while constructing templates. Missing mappings must remain
        // disabled: adding a bundled model here would make new stacks differ from existing untextured items,
        // even on servers that never enabled Legacy's resource-pack sender.
        loadDefaultValues();
        migrateAccidentalDeepcorePaxelMappings();
    }

    /**
     * This method registers the given {@link SlimefunItem SlimefunItems} to this {@link CustomTextureService}.
     * If saving is enabled, it will save them to the {@link Config} file.
     *
     * @param items
     *            The {@link SlimefunItem SlimefunItems} to register
     * @param save
     *            Whether to save this file
     */
    public void register(@Nonnull Collection<SlimefunItem> items, boolean save) {
        Validate.notEmpty(items, "items must neither be null or empty.");

        loadDefaultValues();

        for (SlimefunItem item : items) {
            if (item != null) {
                config.setDefaultValue(item.getId(), 0);

                if (config.getInt(item.getId()) != 0) {
                    modified = true;
                }
            }
        }

        version = config.getString("version");

        if (save) {
            config.save();
        }
    }

    private void loadDefaultValues() {
        if (defaultsLoaded) {
            return;
        }

        InputStream inputStream = Slimefun.class.getResourceAsStream("/item-models.yml");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(reader);

            for (String key : cfg.getKeys(false)) {
                if (!config.contains(key)) {
                    config.setValue(key, 0);
                }
            }

            // Slimefun Legacy 4.1.52 temporarily upgraded existing zero placeholders to the bundled
            // hosted-pack model map. That changed ItemStack equality for pre-existing servers and could
            // make otherwise identical Slimefun items stop matching storage/machine templates.
            //
            // Never rewrite an existing zero mapping here again. Servers affected by that historical
            // migration can use /sf doctor item-models rollback-v52, which is explicit and reversible
            // through the server owner's normal config backup instead of silently changing item identity.

            defaultsLoaded = true;
        } catch (Exception e) {
            Logger logger = Slimefun.instance() == null
                    ? Logger.getLogger(CustomTextureService.class.getName())
                    : Slimefun.logger();
            logger.log(Level.SEVERE, "Failed to load default item-models.yml file", e);
        }
    }

    private void migrateAccidentalDeepcorePaxelMappings() {
        FileConfiguration configuration = config.getConfiguration();
        if (configuration.getBoolean(DEEPCORE_PAXEL_VISUAL_MIGRATION, false)) {
            return;
        }

        int migrated = 0;
        for (String id : DEEPCORE_PAXEL_IDS) {
            if (configuration.isSet(id) && configuration.getInt(id) == SHARED_PAXEL_MODEL_DATA) {
                configuration.set(id, 0);
                migrated++;
            }
        }

        // This marker makes the repair one-time. Server owners remain free to deliberately assign
        // any custom model data they want after the migration has completed.
        configuration.set(DEEPCORE_PAXEL_VISUAL_MIGRATION, true);

        if (migrated > 0) {
            Logger logger = Slimefun.instance() == null
                    ? Logger.getLogger(CustomTextureService.class.getName())
                    : Slimefun.logger();
            logger.info("Cleared accidental shared Paxel visual mapping from " + migrated + " Deepcore Paxel item(s).");
        }
    }

    /**
     * Returns whether this item-models.yml was touched by the historical v4.1.52 hosted-pack migration.
     *
     * <p>The marker is diagnostic evidence only. It does not mean every bundled-looking mapping was
     * necessarily unwanted, so rollback remains an explicit operator action.</p>
     */
    public boolean wasHostedPackModelMigrationApplied() {
        return config.getConfiguration().getBoolean(HOSTED_PACK_MODEL_MIGRATION, false);
    }

    /**
     * Counts mappings that still exactly match Slimefun Legacy's bundled hosted-pack values.
     *
     * <p>This count is only considered a v4.1.52 rollback candidate when the historical migration
     * marker is present. Custom values that do not exactly match the bundled map are never included.</p>
     */
    public int getHostedPackRollbackCandidateCount() {
        if (!wasHostedPackModelMigrationApplied()) {
            return 0;
        }

        FileConfiguration bundled = loadBundledModelConfiguration();
        int candidates = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            if (bundledModel != 0 && config.contains(key) && config.getInt(key) == bundledModel) {
                candidates++;
            }
        }
        return candidates;
    }

    /**
     * Counts exact bundled resource-pack mappings that can be safely removed.
     *
     * <p>This is intentionally provenance-independent: it covers both the historical v4.1.52 migration and
     * mappings explicitly enabled later through Doctor. Custom/non-matching non-zero values are never included.</p>
     */
    public int getHostedPackRemovalCandidateCount() {
        FileConfiguration bundled = loadBundledModelConfiguration();
        int candidates = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            if (bundledModel != 0
                    && SlimefunItem.getById(key) != null
                    && config.contains(key)
                    && config.getInt(key) == bundledModel) {
                candidates++;
            }
        }
        return candidates;
    }

    /**
     * Counts bundled hosted-pack mappings that are currently disabled with an explicit zero.
     */
    public int getHostedPackEnableCandidateCount() {
        FileConfiguration bundled = loadBundledModelConfiguration();
        int candidates = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            if (bundledModel != 0
                    && SlimefunItem.getById(key) != null
                    && config.contains(key)
                    && config.getInt(key) == 0) {
                candidates++;
            }
        }
        return candidates;
    }

    /** Returns how many bundled hosted-pack mappings are already active exactly as shipped. */
    public int getHostedPackEnabledMappingCount() {
        FileConfiguration bundled = loadBundledModelConfiguration();
        int enabled = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            if (bundledModel != 0
                    && SlimefunItem.getById(key) != null
                    && config.contains(key)
                    && config.getInt(key) == bundledModel) {
                enabled++;
            }
        }
        return enabled;
    }

    /** Returns how many bundled IDs currently use a non-zero server-customized value. */
    public int getHostedPackCustomMappingCount() {
        FileConfiguration bundled = loadBundledModelConfiguration();
        int custom = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            int configured = config.contains(key) ? config.getInt(key) : bundledModel;
            if (bundledModel != 0
                    && SlimefunItem.getById(key) != null
                    && configured != 0
                    && configured != bundledModel) {
                custom++;
            }
        }
        return custom;
    }

    /**
     * Explicitly opts an existing server into Slimefun Legacy's bundled hosted-pack mappings.
     *
     * <p>Only mappings that are currently exactly zero are changed. Existing non-zero custom values
     * are preserved. Registered Slimefun templates are not rebuilt in-place, so operators must restart
     * after the corresponding Doctor traversal finishes.</p>
     *
     * @return number of zero mappings changed to bundled values
     */
    public int enableHostedPackMappings() {
        FileConfiguration bundled = loadBundledModelConfiguration();
        int enabled = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            if (bundledModel != 0
                    && SlimefunItem.getById(key) != null
                    && config.contains(key)
                    && config.getInt(key) == 0) {
                config.setValue(key, bundledModel);
                enabled++;
            }
        }

        if (enabled > 0) {
            modified = true;
            config.save();
        }
        return enabled;
    }

    /**
     * Resets exact bundled mappings back to zero for a server explicitly rolling back the v4.1.52
     * hosted-pack migration.
     *
     * <p>Only exact bundled values are changed. Unrelated/custom model values are preserved. A clean
     * restart is required afterwards because registered Slimefun item templates were created earlier
     * in this runtime using the old mappings.</p>
     *
     * @return number of item-model entries reset to zero
     */
    public int rollbackHostedPackMigrationMappings() {
        if (!wasHostedPackModelMigrationApplied()) {
            return 0;
        }

        return removeHostedPackMappings();
    }

    /**
     * Removes every currently configured mapping that still exactly matches Slimefun Legacy's bundled
     * resource-pack model value.
     *
     * <p>This is safe for both historical and newly adopted Legacy mappings. Server-customized values that differ
     * from the bundled map are preserved. A clean restart is required afterwards because registered item templates
     * were built earlier in this runtime.</p>
     *
     * @return number of exact bundled mappings reset to zero
     */
    public int removeHostedPackMappings() {
        FileConfiguration bundled = loadBundledModelConfiguration();
        int reset = 0;
        for (String key : bundled.getKeys(false)) {
            int bundledModel = bundled.getInt(key);
            if (bundledModel != 0
                    && SlimefunItem.getById(key) != null
                    && config.contains(key)
                    && config.getInt(key) == bundledModel) {
                config.setValue(key, 0);
                reset++;
            }
        }

        if (reset > 0) {
            config.save();
        }
        return reset;
    }

    private FileConfiguration loadBundledModelConfiguration() {
        InputStream stream = Slimefun.class.getResourceAsStream("/item-models.yml");
        if (stream == null) {
            Logger logger = Slimefun.instance() == null
                    ? Logger.getLogger(CustomTextureService.class.getName())
                    : Slimefun.logger();
            logger.warning("Could not load bundled item-models.yml while checking v4.1.52 recovery.");
            return new YamlConfiguration();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException exception) {
            Logger logger = Slimefun.instance() == null
                    ? Logger.getLogger(CustomTextureService.class.getName())
                    : Slimefun.logger();
            logger.log(Level.SEVERE, "Could not read bundled item-models.yml for v4.1.52 recovery.", exception);
            return new YamlConfiguration();
        }
    }

    @Nullable public String getVersion() {
        return version;
    }

    /**
     * This returns true if any custom model data was configured.
     * If every item id has no configured custom model data, it will return false.
     *
     * @return Whether any custom model data was configured
     */
    public boolean isActive() {
        return modified;
    }

    /**
     * This returns the configured custom model data for a given id.
     *
     * @param id
     *            The id to get the data for
     *
     * @return The configured custom model data
     */
    public int getModelData(@Nonnull String id) {
        Validate.notNull(id, "Cannot get the ModelData for 'null'");

        return config.getInt(id);
    }

    /**
     * This method sets the custom model data for this {@link ItemStack}
     * to the value configured for the provided item id.
     *
     * @param item
     *            The {@link ItemStack} to set the custom model data for
     * @param id
     *            The id for which to get the configured model data
     */
    public void setTexture(@Nonnull ItemStack item, @Nonnull String id) {
        Validate.notNull(item, "The Item cannot be null!");
        Validate.notNull(id, "Cannot store null on an Item!");

        ItemMeta im = item.getItemMeta();
        setTexture(im, id);
        item.setItemMeta(im);
    }

    /**
     * This method applies the custom model data configured for the provided item id.
     * A configured value of {@code 0} means Slimefun has no model override and therefore
     * leaves any model data supplied by another plugin, such as ItemsAdder, untouched.
     *
     * @param im
     *            The {@link ItemMeta} to set custom model data on
     * @param id
     *            The id for which to get the configured model data
     */
    public void setTexture(@Nonnull ItemMeta im, @Nonnull String id) {
        Validate.notNull(im, "The ItemMeta cannot be null!");
        Validate.notNull(id, "Cannot store null on an ItemMeta!");

        int data = getModelData(id);
        if (data != 0) {
            CustomModelDataComponent component = im.getCustomModelDataComponent();
            List<Float> floats = new ArrayList<>(component.getFloats());

            // Paper defines a legacy integer CustomModelData value as the first float in the
            // modern component. Preserve any extra values supplied by another plugin/resource
            // pack integration while replacing only Slimefun's legacy-compatible model slot.
            if (floats.isEmpty()) {
                floats.add((float) data);
            } else {
                floats.set(0, (float) data);
            }

            component.setFloats(floats);
            im.setCustomModelDataComponent(component);
        }
    }

    public int removeHostedPackMappingsChecked(@Nonnull Path backupDirectory) throws IOException {
        YamlConfiguration staged = YamlConfiguration.loadConfiguration(
                (Reader) new StringReader(this.config.getConfiguration().saveToString()));
        this.writeAtomically(backupDirectory.resolve("item-models-current.yml"), staged.saveToString());
        FileConfiguration bundled = this.loadBundledModelConfiguration();
        ArrayList<String> reset = new ArrayList<String>();
        for (String id2 : bundled.getKeys(false)) {
            int model = bundled.getInt(id2);
            if (model == 0 || staged.getInt(id2) != model) continue;
            staged.set(id2, (Object) 0);
            reset.add(id2);
        }
        if (!reset.isEmpty()) {
            this.writeAtomically(this.config.getFile().toPath(), staged.saveToString());
            reset.forEach(id -> this.config.setValue((String) id, 0));
        }
        return reset.size();
    }

    public int getHostedPackTemplateMismatchCount() {
        FileConfiguration bundled = this.loadBundledModelConfiguration();
        int mismatches = 0;
        for (String id : bundled.getKeys(false)) {
            SlimefunItem definition = SlimefunItem.getById(id);
            if (definition == null
                    || this.config.getInt(id) != 0
                    || !this.hasBundledFloat(definition.getItem(), bundled.getInt(id))) continue;
            ++mismatches;
        }
        for (SlimefunGuideMode mode : SlimefunGuideMode.values()) {
            SlimefunGuideImplementation guide = Slimefun.getRegistry().getSlimefunGuide(mode);
            if (guide == null
                    || this.config.getInt("SLIMEFUN_GUIDE") != 0
                    || !this.hasBundledFloat(guide.getItem(), bundled.getInt("SLIMEFUN_GUIDE"))) continue;
            ++mismatches;
        }
        return mismatches;
    }

    private boolean hasBundledFloat(ItemStack item, int bundledModel) {
        if (bundledModel == 0 || item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        List floats = meta.getCustomModelDataComponent().getFloats();
        return meta.hasCustomModelDataComponent()
                && !floats.isEmpty()
                && Float.compare(((Float) floats.get(0)).floatValue(), bundledModel) == 0;
    }

    private void writeAtomically(Path file, String text) throws IOException {
        Path temporary =
                Files.createTempFile(file.toAbsolutePath().getParent(), "item-models-", ".tmp", new FileAttribute[0]);
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE); ) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
