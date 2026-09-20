package io.github.thebusybiscuit.slimefun4.core.services.stability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Internal strategy used by server-wide Doctor traversals that need custom item inspection logic. */
abstract class ItemDoctorTraversalExecutor {

    abstract boolean inspectInventory(@Nonnull Inventory inventory, @Nonnull ItemDoctorReport report);

    abstract boolean inspectItem(@Nullable ItemStack item, @Nonnull ItemDoctorReport report);
}
