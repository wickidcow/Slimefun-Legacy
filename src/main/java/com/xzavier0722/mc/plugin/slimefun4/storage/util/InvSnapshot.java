package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import io.github.bakedlibs.dough.collections.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** An owned inventory baseline; caller mutations must not acknowledge unsaved changes. */
public class InvSnapshot {
    @Nonnull
    private final List<Pair<ItemStack, Integer>> snapshot;

    public InvSnapshot(List<Pair<ItemStack, Integer>> snapshot) {
        this.snapshot = List.copyOf(copySnapshot(snapshot));
    }

    public InvSnapshot(Inventory inventory) {
        this.snapshot = InvStorageUtils.getInvSnapshot(inventory.getContents());
    }

    public InvSnapshot(ItemStack[] list) {
        this.snapshot = InvStorageUtils.getInvSnapshot(list);
    }

    /** Returns an editable detached list of pairs and items, never the saved comparison baseline. */
    @Nonnull
    public List<Pair<ItemStack, Integer>> getSnapshot() {
        return copySnapshot(snapshot);
    }

    public Set<Integer> getChangedSlots(ItemStack[] item) {
        return InvStorageUtils.getChangedSlots(snapshot, item);
    }

    public Set<Integer> getChangedSlots(Inventory inv) {
        return getChangedSlots(inv.getContents());
    }

    private static List<Pair<ItemStack, Integer>> copySnapshot(List<Pair<ItemStack, Integer>> source) {
        var copy = new ArrayList<Pair<ItemStack, Integer>>(source.size());
        for (var entry : source) {
            ItemStack item = entry.getFirstValue();
            // Preserve the independently recorded amount, including historical non-normalized pairs.
            copy.add(new Pair<>(item == null ? null : item.clone(), entry.getSecondValue()));
        }
        return copy;
    }
}
