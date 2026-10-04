package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Detects and removes only Slimefun Legacy's bundled item-model value from items whose server mapping
 * has explicitly been returned to {@code 0}.
 *
 * <p>This is intentionally not part of automatic Item Doctor repair. Operators must run the dedicated
 * item-model Doctor command after reviewing a dry-run.</p>
 */
final class ItemModelRepairExecutor extends ItemDoctorTraversalExecutor {

    private static final int MAX_CONTAINER_DEPTH = 4;

    private final boolean repair;
    private final boolean previewMappingRemoval;
    private final boolean requireFreshTemplates;
    private final Map<String, Integer> bundledModels;

    ItemModelRepairExecutor(boolean repair) {
        this(repair, false, false);
    }

    ItemModelRepairExecutor(boolean repair, boolean previewMappingRemoval) {
        this(repair, previewMappingRemoval, false);
    }

    ItemModelRepairExecutor(boolean repair, boolean previewMappingRemoval, boolean requireFreshTemplates) {
        this.repair = repair;
        this.previewMappingRemoval = previewMappingRemoval;
        this.requireFreshTemplates = requireFreshTemplates;
        this.bundledModels = loadBundledModels();
    }

    @Override
    boolean inspectInventory(@Nonnull Inventory inventory, @Nonnull ItemDoctorReport report) {
        return inspectInventory(inventory, report, 0);
    }

    private boolean inspectInventory(@Nonnull Inventory inventory, @Nonnull ItemDoctorReport report, int depth) {
        report.inventoryScanned();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (inspectItem(item, report, depth)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    boolean inspectItem(@Nullable ItemStack item, @Nonnull ItemDoctorReport report) {
        return inspectItem(item, report, 0);
    }

    private boolean inspectItem(@Nullable ItemStack item, @Nonnull ItemDoctorReport report, int depth) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        report.stackScanned();
        boolean changed = false;
        try {
            Optional<String> storedId = Slimefun.getItemDataService().getItemData(item);
            if (storedId.isPresent()) {
                report.slimefunStackFound();
                changed = this.inspectCandidate(item, storedId.get(), report);
            } else if (this.guideMode(item) != null) {
                report.slimefunStackFound();
                changed = this.inspectCandidate(item, "SLIMEFUN_GUIDE", report);
            }
            if (depth < 4) {
                changed |= this.inspectNestedItems(item, report, depth + 1);
            }
            return changed;
        } catch (LinkageError | RuntimeException exception) {
            report.failure();
            Slimefun.logger().log(Level.WARNING, "Item-model Doctor skipped a failing ItemStack.", exception);
            return changed;
        }
    }

    boolean inspectCandidate(ItemStack item, String slimefunId, ItemDoctorReport report) {
        String guideMode;
        String string = guideMode = slimefunId.equals("SLIMEFUN_GUIDE") ? this.guideMode(item) : null;
        if (SlimefunItem.getById(slimefunId) == null && guideMode == null) {
            return false;
        }
        int bundledModel = this.bundledModels.getOrDefault(slimefunId, 0);
        if (bundledModel == 0) {
            return false;
        }
        if (Slimefun.getItemTextureService().getModelData(slimefunId) != 0
                && (!this.previewMappingRemoval
                        || this.repair
                        || Slimefun.getItemTextureService().getModelData(slimefunId) != bundledModel)) {
            return false;
        }
        if (this.repair && this.requireFreshTemplates && this.templateHasBundledModel(slimefunId, bundledModel)) {
            report.itemModelConflictFound(slimefunId);
            return false;
        }
        ItemMeta currentMeta = item.getItemMeta();
        if (!currentMeta.hasCustomModelDataComponent()) {
            return false;
        }
        CustomModelDataComponent component = currentMeta.getCustomModelDataComponent();
        List<Float> floats = new ArrayList<>(component.getFloats());
        if (floats.isEmpty() || Float.compare(floats.get(0), (float) bundledModel) != 0) {
            return false;
        }
        report.itemModelCandidateFound(slimefunId);
        if (!this.repair) {
            return false;
        }
        ItemMeta originalMeta = currentMeta.clone();
        try {
            floats.remove(0);
            if (floats.isEmpty()
                    && component.getFlags().isEmpty()
                    && component.getStrings().isEmpty()
                    && component.getColors().isEmpty()) {
                currentMeta.setCustomModelDataComponent(null);
            } else {
                component.setFloats(floats);
                currentMeta.setCustomModelDataComponent(component);
            }
            item.setItemMeta(currentMeta);
            Optional<String> resultingId = Slimefun.getItemDataService().getItemData(item);
            if (guideMode != null
                    ? !guideMode.equals(this.guideMode(item))
                    : resultingId.isEmpty() || !slimefunId.equals(resultingId.get())) {
                item.setItemMeta(originalMeta);
                report.failure();
                Slimefun.logger()
                        .warning(
                                "Item-model Doctor refused a repair because the Slimefun item ID changed unexpectedly.");
                return false;
            }
            report.itemModelRepaired();
            report.stackRepaired();
            return true;
        } catch (LinkageError | RuntimeException exception) {
            try {
                item.setItemMeta(originalMeta);
            } catch (LinkageError | RuntimeException rollbackError) {
                exception.addSuppressed(rollbackError);
            }
            report.failure();
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Item-model Doctor could not safely remove the bundled model value from " + slimefunId
                                    + ".",
                            exception);
            return false;
        }
    }

    private boolean inspectNestedItems(ItemStack item, ItemDoctorReport report, int depth) {
        boolean changed = false;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
            boolean nestedChanged = false;
            for (ItemStack nested : contents) {
                nestedChanged |= inspectItem(nested, report, depth);
            }
            if (nestedChanged) {
                bundleMeta.setItems(contents);
                item.setItemMeta(bundleMeta);
                changed = true;
            }
        }

        meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof Container container
                    && inspectInventory(container.getInventory(), report, depth)) {
                blockStateMeta.setBlockState(container);
                item.setItemMeta(blockStateMeta);
                changed = true;
            }
        }

        return changed;
    }

    private static Map<String, Integer> loadBundledModels() {
        Map<String, Integer> models = new HashMap<>();
        InputStream stream = Slimefun.class.getResourceAsStream("/item-models.yml");
        if (stream == null) {
            Slimefun.logger().warning("Item-model Doctor could not find the bundled item-models.yml resource.");
            return models;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(reader);
            for (String key : config.getKeys(false)) {
                int model = config.getInt(key);
                if (model != 0) {
                    models.put(key, model);
                }
            }
        } catch (RuntimeException | java.io.IOException exception) {
            Slimefun.logger()
                    .log(Level.SEVERE, "Item-model Doctor could not load bundled item-model mappings.", exception);
        }

        return Map.copyOf(models);
    }

    private String guideMode(ItemStack item) {
        NamespacedKey key = Slimefun.getRegistry().getGuideDataKey();
        if (key == null || !item.hasItemMeta()) {
            return null;
        }
        String mode = (String) item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return "SURVIVAL_MODE".equals(mode) || "CHEAT_MODE".equals(mode) ? mode : null;
    }

    private boolean templateHasBundledModel(String id, int bundledModel) {
        SlimefunItem definition = SlimefunItem.getById(id);
        if (definition != null && this.hasBundledModel(definition.getItem(), bundledModel)) {
            return true;
        }
        if (id.equals("SLIMEFUN_GUIDE")) {
            for (SlimefunGuideMode mode : SlimefunGuideMode.values()) {
                SlimefunGuideImplementation guide = Slimefun.getRegistry().getSlimefunGuide(mode);
                if (guide == null || !this.hasBundledModel(guide.getItem(), bundledModel)) continue;
                return true;
            }
        }
        return false;
    }

    private boolean hasBundledModel(ItemStack item, int bundledModel) {
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelDataComponent()) {
            return false;
        }
        List<Float> floats = meta.getCustomModelDataComponent().getFloats();
        return !floats.isEmpty() && Float.compare(floats.get(0), (float) bundledModel) == 0;
    }
}
