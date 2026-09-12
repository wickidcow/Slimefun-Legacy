package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

/** Item-stack-only legacy ID and translated presentation upgrade inspector. */
final class ItemUpgradeInspector {

    private static final int MAX_CONTAINER_DEPTH = 4;

    private final ItemPresentationDoctor presentationDoctor = new ItemPresentationDoctor();

    boolean inspectInventory(@Nonnull Inventory inventory, boolean repair, @Nonnull ItemUpgradeReport report) {
        return inspectInventory(inventory, repair, report, 0);
    }

    private boolean inspectInventory(
            @Nonnull Inventory inventory, boolean repair, @Nonnull ItemUpgradeReport report, int depth) {
        boolean changed = false;
        report.inventoryScanned();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (inspectItem(item, repair, report, depth)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    boolean inspectItem(@Nullable ItemStack item, boolean repair, @Nonnull ItemUpgradeReport report) {
        return inspectItem(item, repair, report, 0);
    }

    private boolean inspectItem(
            @Nullable ItemStack item, boolean repair, @Nonnull ItemUpgradeReport report, int depth) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        report.stackScanned();
        boolean changed = false;
        try {
            changed = inspectSlimefunItem(item, repair, report);
        } catch (RuntimeException | LinkageError ex) {
            report.failure();
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Item Upgrade Doctor skipped a failing stack [" + describeStack(item) + "].",
                            ex);
        }

        if (depth < MAX_CONTAINER_DEPTH) {
            try {
                changed |= inspectNestedItems(item, repair, report, depth + 1);
            } catch (RuntimeException | LinkageError ex) {
                report.failure();
                Slimefun.logger()
                        .log(
                                Level.WARNING,
                                "Item Upgrade Doctor could not inspect a nested container [" + describeStack(item) + "].",
                                ex);
            }
        }
        return changed;
    }

    private boolean inspectSlimefunItem(ItemStack item, boolean repair, ItemUpgradeReport report) {
        Optional<String> storedId = Slimefun.getItemDataService().getItemData(item);
        if (storedId.isEmpty()) {
            return false;
        }

        report.slimefunStackFound();
        String itemId = storedId.get();
        Map<String, String> mappings = Slimefun.getRegistry().getLegacySlimefunItemIds();
        String targetId = mappings.get(itemId);

        if (targetId == null) {
            SlimefunItem current = SlimefunItem.getById(itemId);
            if (current == null) {
                report.unknownUnmapped(itemId);
                return false;
            }
            return presentationDoctor.inspectItem(item, repair, report.getPresentationReport());
        }

        SlimefunItem target = SlimefunItem.getById(targetId);
        if (target == null) {
            report.missingTarget(itemId, targetId);
            return false;
        }

        Material currentMaterial = item.getType();
        Material targetMaterial = target.getItem().getType();
        if (currentMaterial != targetMaterial) {
            report.materialMismatch(itemId, targetId, currentMaterial + " -> " + targetMaterial);
            return false;
        }

        report.readyIdUpgrade(itemId, targetId, target.getAddon().getName());

        if (!repair) {
            ItemStack preview = item.clone();
            Slimefun.getItemDataService().setItemData(preview, targetId);
            presentationDoctor.inspectItem(preview, false, report.getPresentationReport());
            return false;
        }

        Slimefun.getItemDataService().setItemData(item, targetId);
        report.idRewritten();
        presentationDoctor.inspectItem(item, true, report.getPresentationReport());
        return true;
    }

    private boolean inspectNestedItems(ItemStack item, boolean repair, ItemUpgradeReport report, int depth) {
        var meta = item.getItemMeta();
        boolean changed = false;
        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
            for (ItemStack nested : contents) {
                changed |= inspectItem(nested, repair, report, depth);
            }
            if (changed && repair) {
                bundleMeta.setItems(contents);
                item.setItemMeta(bundleMeta);
            }
        }

        meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof Container container) {
                changed |= inspectInventory(container.getInventory(), repair, report, depth);
                if (changed && repair) {
                    blockStateMeta.setBlockState(container);
                    item.setItemMeta(blockStateMeta);
                }
            }
        }
        return changed;
    }

    private static String describeStack(ItemStack item) {
        String itemId;
        try {
            itemId = Slimefun.getItemDataService().getItemData(item).orElse("<none>");
        } catch (RuntimeException | LinkageError ignored) {
            itemId = "<unreadable>";
        }
        return "type=" + item.getType() + ", slimefunId=" + itemId;
    }
}
