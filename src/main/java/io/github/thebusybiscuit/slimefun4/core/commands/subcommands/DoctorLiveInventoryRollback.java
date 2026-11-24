package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Operator-only, explicit postrestart recovery of a previously updated
 * player-inventory storage slot. The owner-scoped old-item backup comes from
 * a verified Doctor live batch; a replacement is authorized only when it is
 * the exact strict presentation-only repair of that old item.
 *
 * <p>This NEVER touches a physical container, machine, QuickShop database,
 * another player's inventory or an unrelated slot. It is not automatic.
 */
final class DoctorLiveInventoryRollback {
    private static final int MAX_SLOT = 36;
    private static final int MAX_STACK_BYTES = 256 * 1024;
    private static final long PLAN_TTL_MILLIS = 60_000L;
    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");

    private DoctorLiveInventoryRollback() {}

    record Plan(
            UUID originalReceipt, int slot, String originalHash, String updatedHash,
            String templateHash, String inventorySha256, long expiresAtMillis) {}

    record Result(int slot, UUID originalReceipt, UUID updatedBackupReceipt) {}

    static Plan preview(
            Inventory inventory, Path pluginFolder, UUID operator, UUID receipt, int slot,
            Function<ItemStack, ItemStack> templates, long now) throws IOException {
        if (inventory == null || pluginFolder == null || operator == null || receipt == null
                || templates == null || slot < 0 || slot >= MAX_SLOT
                || slot >= inventory.getStorageContents().length) {
            throw new IOException("Invalid operator-owned player storage slot recovery");
        }

        var before = DoctorLiveItemUpdate.captureContainerPreimage(inventory);
        ItemStack original = DoctorHistoricalStackRepair.readVerifiedBackup(pluginFolder, operator, receipt);
        ItemStack updated = inventory.getItem(slot);
        ItemStack template;
        try {
            template = templates.apply(original);
        } catch (RuntimeException exception) {
            throw new IOException("Registered template lookup refused", exception);
        }
        if (!exactIdentity(original, updated, template)
                || !bounded(original) || !bounded(updated) || !bounded(template)
                || !DoctorHistoricalStackRepair.matchesSavedBackup(original, updated, template, template)) {
            throw new IOException("Saved original is not the strict style-only predecessor of this exact slot");
        }
        if (!DoctorLiveItemUpdate.containerUnchanged(inventory, before)) {
            throw new IOException("Player inventory changed during rollback preview");
        }
        return new Plan(
                receipt, slot, DoctorHistoricalStackRepair.fingerprint(original),
                DoctorHistoricalStackRepair.fingerprint(updated),
                DoctorHistoricalStackRepair.fingerprint(template),
                before.sha256(), now + PLAN_TTL_MILLIS);
    }

    /**
     * The only write path. Save the currently repaired stack as another
     * independently reopenable native receipt before replacing it. Re-open
     * the old receipt and revalidate the whole player inventory after I/O.
     * Refuse stale plans, changed counts, IDs, PDC, text or unrelated slots.
     */
    static Result confirm(
            Inventory inventory, Path pluginFolder, UUID operator, Plan plan,
            Function<ItemStack, ItemStack> templates, long now) throws IOException {
        if (plan == null || now > plan.expiresAtMillis() || plan.expiresAtMillis() - now > PLAN_TTL_MILLIS) {
            throw new IOException("Player-inventory rollback preview expired");
        }
        Plan checked = preview(
                inventory, pluginFolder, operator, plan.originalReceipt(), plan.slot(), templates, now);
        if (!Objects.equals(plan.originalHash(), checked.originalHash())
                || !Objects.equals(plan.updatedHash(), checked.updatedHash())
                || !Objects.equals(plan.templateHash(), checked.templateHash())
                || !Objects.equals(plan.inventorySha256(), checked.inventorySha256())) {
            throw new IOException("Backup, current item, registered template or another inventory slot changed");
        }

        byte[][] beforeSlots = DoctorLiveItemUpdate.exactContainerSlots(inventory);
        if (!plan.inventorySha256().equals(DoctorContainerMigrationPreflight.hashSlots(beforeSlots))) {
            throw new IOException("Full player storage changed before restore");
        }
        ItemStack original = DoctorHistoricalStackRepair.readVerifiedBackup(
                pluginFolder, operator, plan.originalReceipt());
        ItemStack updated = inventory.getItem(plan.slot());
        if (updated == null) {
            throw new IOException("Player item disappeared before restore");
        }
        byte[] originalBytes = original.serializeAsBytes();
        byte[] updatedBytes = updated.serializeAsBytes();
        Path savedUpdated = DoctorHistoricalStackRepair.backup(pluginFolder, operator, updatedBytes);
        UUID updatedReceipt = receiptFromPath(operator, savedUpdated);
        ItemStack verifiedUpdated = DoctorHistoricalStackRepair.readVerifiedBackup(
                pluginFolder, operator, updatedReceipt);
        if (!Arrays.equals(updatedBytes, verifiedUpdated.serializeAsBytes())
                || !plan.inventorySha256().equals(
                        DoctorLiveItemUpdate.captureContainerPreimage(inventory).sha256())) {
            throw new IOException("Player inventory changed while persisting rollback safety backup");
        }
        // No disk I/O or external plugin callback occurs between this final
        // full-inventory check and the region-owned synchronous slot update.
        boolean attempted = false;
        try {
            if (!Arrays.equals(beforeSlots[plan.slot()], inventory.getItem(plan.slot()).serializeAsBytes())) {
                throw new IOException("Target player slot changed immediately before restore");
            }
            attempted = true;
            inventory.setItem(plan.slot(), ItemStack.deserializeBytes(originalBytes));
            byte[][] observed = DoctorLiveItemUpdate.exactContainerSlots(inventory);
            if (!Arrays.equals(originalBytes, observed[plan.slot()])) {
                throw new IOException("Restored player slot failed native-byte readback");
            }
            for (int slot = 0; slot < beforeSlots.length; slot++) {
                if (slot != plan.slot() && !Arrays.equals(beforeSlots[slot], observed[slot])) {
                    throw new IOException("Another player slot changed while restoring saved item");
                }
            }
        } catch (RuntimeException | IOException exception) {
            if (attempted) {
                // Do not overwrite a foreign item another plugin may have put
                // in the slot. Restore the repaired bytes only if the target
                // is still *exactly* the original from our own attempted write.
                try {
                    ItemStack observed = inventory.getItem(plan.slot());
                    if (observed != null && Arrays.equals(originalBytes, observed.serializeAsBytes())) {
                        inventory.setItem(plan.slot(), ItemStack.deserializeBytes(updatedBytes));
                    }
                } catch (RuntimeException ignored) {
                    // Both owner-scoped immutable receipts survive for recovery.
                }
            }
            throw new IOException("Player item restore interrupted; both native receipts retained", exception);
        }
        return new Result(plan.slot(), plan.originalReceipt(), updatedReceipt);
    }

    private static boolean bounded(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        try {
            byte[] bytes = item.serializeAsBytes();
            return bytes.length > 0 && bytes.length <= MAX_STACK_BYTES;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean exactIdentity(ItemStack a, ItemStack b, ItemStack c) {
        String id = itemId(a);
        return id != null && b != null && c != null
                && a.getType() == b.getType() && b.getType() == c.getType()
                && id.equals(itemId(b)) && id.equals(itemId(c));
    }

    private static String itemId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer()
                .get(SLIMEFUN_ID, PersistentDataType.STRING);
    }

    private static UUID receiptFromPath(UUID owner, Path path) throws IOException {
        String filename = path.getFileName().toString();
        String prefix = owner + "-";
        if (!filename.startsWith(prefix) || !filename.endsWith(".bin")) {
            throw new IOException("Unexpected owner-scoped item receipt filename");
        }
        try {
            return UUID.fromString(filename.substring(prefix.length(), filename.length() - 4));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid item receipt UUID", exception);
        }
    }
}
