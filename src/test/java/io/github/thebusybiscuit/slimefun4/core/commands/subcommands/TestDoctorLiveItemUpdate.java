package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestDoctorLiveItemUpdate {

    @TempDir
    Path data;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private static ItemStack current(String id) {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Carbonado").decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(Component.text("Black Diamond")));
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 787_001L);
        });
        return item;
    }

    private static ItemStack olderStyling(ItemStack template, int amount) {
        ItemStack old = ItemStack.deserializeBytes(template.serializeAsBytes());
        old.setAmount(amount);
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true)));
        return old;
    }

    @Test
    void previewAndConfirmOnlySafeLivePlayerSlotsWithExactBackupsAndNoLoss() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 63);
        byte[] untouched = old.serializeAsBytes();
        byte[] registeredBytes = template.serializeAsBytes();
        inventory.setItem(0, old);
        inventory.setItem(1, template);

        var plan = DoctorLiveItemUpdate.preview(inventory, item -> template);
        assertEquals(36, plan.scannedSlots());
        assertEquals(1, plan.mismatched());
        assertEquals(1, plan.eligible());
        assertEquals(0, plan.refused());
        assertEquals(1, plan.entries().size());
        assertArrayEquals(untouched, inventory.getItem(0).serializeAsBytes());

        UUID operator = UUID.randomUUID();
        var receipts = DoctorLiveItemUpdate.apply(inventory, plan, item -> template, data, operator);
        assertEquals(1, receipts.size());
        assertEquals(0, receipts.getFirst().slot());
        ItemStack restored = DoctorHistoricalStackRepair.readVerifiedBackup(data, operator, receipts.getFirst().id());
        assertArrayEquals(untouched, restored.serializeAsBytes());
        ItemStack updated = inventory.getItem(0);
        assertTrue(updated.isSimilar(template));
        assertEquals(63, updated.getAmount());
        assertEquals(787_001L,
                updated.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG));
        assertArrayEquals(registeredBytes, template.serializeAsBytes());
        assertArrayEquals(registeredBytes, inventory.getItem(1).serializeAsBytes());

        Inventory merged = Bukkit.createInventory(null, 9);
        assertTrue(merged.addItem(updated, template).isEmpty());
        assertEquals(64, merged.getItem(0).getAmount());
        assertNull(merged.getItem(1));
    }

    @Test
    void meaningfulLoreAndTypedOwnerChangesNeverBecomeLiveCandidates() {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack customLore = olderStyling(template, 17);
        customLore.editMeta(meta -> meta.lore(List.of(Component.text("Stored owner note"))));
        ItemStack differentTypedOwner = olderStyling(template, 3);
        differentTypedOwner.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787001));
        ItemStack otherId = olderStyling(current("CARBON_CHUNK"), 2);
        inventory.setItem(0, customLore);
        inventory.setItem(1, differentTypedOwner);
        inventory.setItem(2, otherId);
        var before = new byte[][] {
                customLore.serializeAsBytes(),
                differentTypedOwner.serializeAsBytes(),
                otherId.serializeAsBytes()
        };

        var plan = DoctorLiveItemUpdate.preview(inventory, item -> template);
        assertEquals(0, plan.eligible());
        assertTrue(plan.refused() >= 3);
        assertTrue(plan.entries().isEmpty());
        for (int slot = 0; slot < 3; slot++) {
            assertArrayEquals(before[slot], inventory.getItem(slot).serializeAsBytes());
        }
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void oldInventoryOrCurrentTemplateChangeAfterPreviewRefusesEntireBatch() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 17);
        inventory.setItem(0, old);
        var plan = DoctorLiveItemUpdate.preview(inventory, item -> template);

        ItemStack changed = inventory.getItem(0);
        changed.setAmount(16);
        inventory.setItem(0, changed);
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                inventory, plan, item -> template, data, UUID.randomUUID()));
        assertEquals(16, inventory.getItem(0).getAmount());
        assertFalse(Files.exists(data.resolve("recovery")));

        changed.setAmount(17);
        inventory.setItem(0, changed);
        template.editMeta(meta -> meta.lore(List.of(Component.text("new registered text"))));
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                inventory, plan, item -> template, data, UUID.randomUUID()));
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void allEightBatchReceiptsAreWrittenAndNinthSlotLeftUnchanged() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        byte[] ninth = null;
        for (int i = 0; i < 9; i++) {
            ItemStack old = olderStyling(template, i + 1);
            inventory.setItem(i, old);
            if (i == 8) {
                ninth = old.serializeAsBytes();
            }
        }
        var plan = DoctorLiveItemUpdate.preview(inventory, item -> template);
        assertEquals(9, plan.eligible());
        assertEquals(8, plan.entries().size());
        UUID owner = UUID.randomUUID();
        var receipts = DoctorLiveItemUpdate.apply(inventory, plan, item -> template, data, owner);
        assertEquals(8, receipts.size());
        for (int i = 0; i < 8; i++) {
            assertTrue(inventory.getItem(i).isSimilar(template));
            var receipt = receipts.get(i);
            assertEquals(i, receipt.slot());
            assertEquals(i + 1, DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, receipt.id())
                    .getAmount());
        }
        assertArrayEquals(ninth, inventory.getItem(8).serializeAsBytes());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            assertEquals(8L, files.count());
        }
    }

    @Test
    void smallBackupBudgetRefusesEligibleBatchBeforeWritingAnySlot() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        byte[][] originalSlots = new byte[8][];
        for (int slot = 0; slot < 8; slot++) {
            ItemStack old = olderStyling(template, slot + 1);
            originalSlots[slot] = old.serializeAsBytes();
            inventory.setItem(slot, old);
        }

        var plan = DoctorLiveItemUpdate.preview(inventory, ignored -> template);
        assertEquals(8, plan.eligible());
        assertEquals(8, plan.entries().size());
        // Invoke the exact same production budget guard with a tiny test-only cap:
        // a bulk rejection is deterministic and cannot be masked by MockBukkit's
        // native round-trip refusal for large synthetic addon PDC payloads.
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.applyWithBudget(
                inventory, plan, ignored -> template, data, UUID.randomUUID(), 1));
        for (int slot = 0; slot < 8; slot++) {
            assertArrayEquals(originalSlots[slot], inventory.getItem(slot).serializeAsBytes());
        }
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void brokenAddonTemplateRefusesOnlyItsSlotAndNeverBlocksOtherSafeStacks() {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack safeFirst = olderStyling(template, 3);
        ItemStack broken = olderStyling(template, 4);
        ItemStack safeLast = olderStyling(template, 5);
        inventory.setItem(0, safeFirst);
        inventory.setItem(1, broken);
        inventory.setItem(2, safeLast);
        byte[][] before = {
                safeFirst.serializeAsBytes(),
                broken.serializeAsBytes(),
                safeLast.serializeAsBytes()
        };

        var preview = DoctorLiveItemUpdate.preview(inventory, item -> {
            if (item.getAmount() == 4) {
                throw new IllegalArgumentException("Malformed addon metadata");
            }
            return template;
        });
        assertEquals(36, preview.scannedSlots());
        assertEquals(2, preview.mismatched());
        assertEquals(2, preview.eligible());
        assertEquals(1, preview.refused());
        assertEquals(2, preview.entries().size());
        assertEquals(0, preview.entries().get(0).slot());
        assertEquals(2, preview.entries().get(1).slot());
        for (int slot = 0; slot < 3; slot++) {
            assertArrayEquals(before[slot], inventory.getItem(slot).serializeAsBytes());
        }
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void fullDoubleChestScanCountsSafeAndRefusedHistoricalItemsWithoutSavingOrChangingAnySlot() {
        Inventory doubleChest = Bukkit.createInventory(null, 54);
        ItemStack template = current("CARBONADO");
        ItemStack styleOnly = olderStyling(template, 63);
        ItemStack changedLore = olderStyling(template, 17);
        changedLore.editMeta(meta -> meta.lore(List.of(Component.text("Private shop notes"))));
        byte[] safeBytes = styleOnly.serializeAsBytes();
        byte[] refusedBytes = changedLore.serializeAsBytes();
        doubleChest.setItem(53, styleOnly);
        doubleChest.setItem(52, changedLore);

        // Player-inventory previews stop at slot 35 even when handed a chest.
        var playerLimit = DoctorLiveItemUpdate.preview(doubleChest, ignored -> template);
        assertEquals(36, playerLimit.scannedSlots());
        assertEquals(0, playerLimit.eligible());

        var inspection = DoctorLiveItemUpdate.preview(doubleChest, ignored -> template, 54);
        assertEquals(54, inspection.scannedSlots());
        assertEquals(2, inspection.mismatched());
        assertEquals(1, inspection.eligible());
        assertEquals(1, inspection.refused());
        assertEquals(1, inspection.entries().size());
        assertEquals(53, inspection.entries().getFirst().slot());
        assertArrayEquals(safeBytes, doubleChest.getItem(53).serializeAsBytes());
        assertArrayEquals(refusedBytes, doubleChest.getItem(52).serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void missingRecoveryPathRefusesBeforeWritingAnyPlayerStack() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 9);
        inventory.setItem(0, old);
        byte[] original = old.serializeAsBytes();
        var plan = DoctorLiveItemUpdate.preview(inventory, item -> template);
        Files.write(data.resolve("recovery"), new byte[] {0x1});
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                inventory, plan, item -> template, data, UUID.randomUUID()));
        assertArrayEquals(original, inventory.getItem(0).serializeAsBytes());
        assertArrayEquals(new byte[] {0x1}, Files.readAllBytes(data.resolve("recovery")));
    }
}
