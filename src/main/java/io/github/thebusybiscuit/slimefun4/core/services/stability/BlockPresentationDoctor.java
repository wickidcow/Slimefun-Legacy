package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.Nameable;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.meta.ItemMeta;

/** Repairs stale translated names and diagnoses legacy identities on placed Slimefun block entities. */
final class BlockPresentationDoctor {

    /**
     * Inspects the block at a known Slimefun location and, when safe, replaces a stale CJK custom
     * name with the currently registered English Slimefun item name.
     *
     * <p>The stored Slimefun ID is also classified for migration diagnostics. Identity findings
     * are read-only here: actual addon block-ID/storage migrations remain owned by a registered
     * {@code LegacyItemMigrationProvider}.
     *
     * <p>This intentionally changes only the block entity's visible custom name. Inventory
     * contents, Slimefun block data, PDC, ownership, machine progress and other functional state
     * are left untouched.
     */
    boolean inspectBlock(@Nonnull Location location, boolean repair, @Nonnull ItemDoctorReport report) {
        report.blockScanned();
        inspectStoredIdentity(location, report);

        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(location);
        if (sfItem == null) {
            return false;
        }

        BlockState state = location.getBlock().getState(false);
        if (!(state instanceof Nameable nameable)) {
            return false;
        }

        String currentName = nameable.getCustomName();
        if (!ItemDoctorText.containsCjk(currentName)) {
            return false;
        }

        report.cjkBlockFound();

        ItemMeta canonicalMeta = sfItem.getItem().getItemMeta();
        if (!canonicalMeta.hasDisplayName()) {
            return false;
        }

        String canonicalName = canonicalMeta.getDisplayName();
        if (canonicalName == null
                || canonicalName.isBlank()
                || ItemDoctorText.containsCjk(canonicalName)
                || Objects.equals(currentName, canonicalName)) {
            return false;
        }

        if (!repair) {
            return false;
        }

        nameable.setCustomName(canonicalName);
        if (!state.update(false, false)) {
            report.failure();
            return false;
        }

        report.blockRepaired();
        return true;
    }

    private void inspectStoredIdentity(Location location, ItemDoctorReport report) {
        var data = StorageCacheUtils.getDataContainer(location);
        if (data == null) {
            return;
        }

        String storedId = data.getSfId();
        if (storedId == null || storedId.isBlank()) {
            return;
        }

        String declaredTarget = Slimefun.getRegistry()
                .getLegacySlimefunItemIdTarget(storedId)
                .orElse(null);
        SlimefunItem resolved = SlimefunItem.getById(storedId);

        if (declaredTarget != null || (resolved != null && !storedId.equals(resolved.getId()))) {
            report.legacyBlockIdFound(storedId);
        } else if (resolved == null) {
            report.unknownBlockIdFound(storedId);
        }
    }
}
