package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
    void templateChangeAfterBackupsRefusesEverySlotAndKeepsExactReceipts() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        byte[] templateBytes = template.serializeAsBytes();
        ItemStack changedTemplate = ItemStack.deserializeBytes(templateBytes);
        changedTemplate.editMeta(meta -> meta.lore(List.of(Component.text("New registered text"))));
        byte[] changedTemplateBytes = changedTemplate.serializeAsBytes();
        byte[][] originals = new byte[2][];
        for (int slot = 0; slot < originals.length; slot++) {
            ItemStack old = olderStyling(template, 21 * (slot + 1));
            originals[slot] = old.serializeAsBytes();
            inventory.setItem(slot, old);
        }
        var plan = DoctorLiveItemUpdate.preview(inventory, ignored -> template);
        assertEquals(2, plan.entries().size());

        // Both preparation reads and the first post-backup read succeed. The
        // second post-backup read observes a newly registered template, so even
        // the already-verified first slot must remain untouched.
        AtomicInteger templateReads = new AtomicInteger();
        UUID owner = UUID.randomUUID();
        IOException failure = assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                inventory, plan, ignored -> templateReads.incrementAndGet() < 4 ? template : changedTemplate,
                data, owner));
        assertEquals("Registered item template changed while saving backups", failure.getMessage());
        assertEquals(4, templateReads.get());
        for (int slot = 0; slot < originals.length; slot++) {
            assertArrayEquals(originals[slot], inventory.getItem(slot).serializeAsBytes());
        }
        assertNull(inventory.getItem(2));
        assertArrayEquals(templateBytes, template.serializeAsBytes());
        assertArrayEquals(changedTemplateBytes, changedTemplate.serializeAsBytes());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            var receipts = files.filter(Files::isRegularFile).toList();
            assertEquals(2, receipts.size());
            byte[] first = Files.readAllBytes(receipts.get(0));
            byte[] second = Files.readAllBytes(receipts.get(1));
            assertTrue(java.util.Arrays.equals(originals[0], first) || java.util.Arrays.equals(originals[0], second));
            assertTrue(java.util.Arrays.equals(originals[1], first) || java.util.Arrays.equals(originals[1], second));
        }
    }

    @Test
    void finalTemplateLookupReplacingSelectedSlotNeverOverwritesForeignStack() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack historical = olderStyling(template, 23);
        ItemStack foreign = new ItemStack(Material.DIAMOND, 7);
        inventory.setItem(0, historical);
        byte[] originalBytes = inventory.getItem(0).serializeAsBytes();
        byte[] foreignBytes = foreign.serializeAsBytes();
        var plan = DoctorLiveItemUpdate.preview(inventory, ignored -> template);
        assertEquals(1, plan.entries().size());

        AtomicInteger reads = new AtomicInteger();
        UUID owner = UUID.randomUUID();
        IOException failure = assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                inventory, plan, ignored -> {
                    // Preparation and post-backup reads succeed. The final
                    // pre-write lookup synchronously replaces the selected slot.
                    if (reads.incrementAndGet() == 3) {
                        inventory.setItem(0, foreign);
                    }
                    return template;
                }, data, owner));

        assertEquals(3, reads.get());
        assertEquals("Live inventory slot changed during final template lookup", failure.getCause().getMessage());
        assertArrayEquals(foreignBytes, inventory.getItem(0).serializeAsBytes());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            var receipts = files.filter(Files::isRegularFile).toList();
            assertEquals(1, receipts.size());
            assertArrayEquals(originalBytes, Files.readAllBytes(receipts.getFirst()));
        }
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
    void fullContainerPreimageDetectsUnrelatedSlotAndTypedPdcChangesWithoutWrites() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 63);
        chest.setItem(53, old);
        chest.setItem(12, new ItemStack(Material.STONE, 13));
        var preimage = DoctorLiveItemUpdate.captureContainerPreimage(chest);
        assertEquals(54, preimage.slots());
        assertTrue(DoctorLiveItemUpdate.containerUnchanged(chest, preimage));
        byte[] oldBytes = chest.getItem(53).serializeAsBytes();

        chest.setItem(12, new ItemStack(Material.STONE, 14));
        assertFalse(DoctorLiveItemUpdate.containerUnchanged(chest, preimage));
        chest.setItem(12, new ItemStack(Material.STONE, 13));
        assertTrue(DoctorLiveItemUpdate.containerUnchanged(chest, preimage));
        chest.setItem(2, new ItemStack(Material.DIRT, 1));
        assertFalse(DoctorLiveItemUpdate.containerUnchanged(chest, preimage));
        chest.setItem(2, null);
        assertTrue(DoctorLiveItemUpdate.containerUnchanged(chest, preimage));
        chest.getItem(53).editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787_001));
        assertFalse(DoctorLiveItemUpdate.containerUnchanged(chest, preimage));
        assertArrayEquals(oldBytes, old.serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void containerExportRejectsUnrelatedRacingSlotWhileKeepingTargetUnchanged() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 19);
        byte[] oldBytes = old.serializeAsBytes();
        chest.setItem(0, old);
        chest.setItem(6, new ItemStack(Material.STONE, 3));
        UUID owner = UUID.randomUUID();
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.captureContainer(
                chest, 0, observed -> {
                    // A hopper or addon changes an unrelated slot after the
                    // complete snapshot but before the evidence has been saved.
                    chest.setItem(6, new ItemStack(Material.STONE, 2));
                    return template;
                }, data, owner));
        assertArrayEquals(oldBytes, chest.getItem(0).serializeAsBytes());
        assertEquals(2, chest.getItem(6).getAmount());
        assertFalse(chest.getItem(0).isSimilar(template));
    }

    @Test
    void oldChestItemAndCurrentTemplateExportAsExactPrivateReceiptsWithoutChangingStock() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack current = current("CARBONADO");
        ItemStack historical = olderStyling(current, 63);
        chest.setItem(53, historical);
        byte[] originalBytes = historical.serializeAsBytes();
        byte[] templateBytes = current.serializeAsBytes();

        UUID owner = UUID.randomUUID();
        var exported = DoctorLiveItemUpdate.captureContainer(chest, 53, ignored -> current, data, owner);
        assertArrayEquals(
                originalBytes,
                DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, exported.mainReceipt()).serializeAsBytes());
        assertArrayEquals(
                templateBytes,
                DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, exported.offhandReceipt()).serializeAsBytes());
        assertArrayEquals(originalBytes, chest.getItem(53).serializeAsBytes());
        assertArrayEquals(templateBytes, current.serializeAsBytes());
        assertEquals(63, chest.getItem(53).getAmount());
        assertFalse(chest.getItem(53).isSimilar(current));
        assertNull(chest.getItem(52));
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            assertEquals(2L, files.count());
        }
    }

    @Test
    void chestEvidenceRefusesMissingItemsOrDifferentIdsBeforeWritingAnyBackup() {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack current = current("CARBONADO");
        ItemStack wrongId = olderStyling(current("CARBON_CHUNK"), 11);
        chest.setItem(0, wrongId);
        byte[] wrongBytes = wrongId.serializeAsBytes();

        UUID owner = UUID.randomUUID();
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.captureContainer(
                chest, -1, ignored -> current, data, owner));
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.captureContainer(
                chest, 54, ignored -> current, data, owner));
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.captureContainer(
                chest, 1, ignored -> current, data, owner));
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.captureContainer(
                chest, 0, ignored -> current, data, owner));
        assertArrayEquals(wrongBytes, chest.getItem(0).serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));

        chest.setItem(0, current);
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.captureContainer(
                chest, 0, ignored -> current, data, owner));
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void failedSecondWriteRollsBackAllAttemptedLiveStacksAndRetainsOriginalReceipts() throws Exception {
        Inventory backing = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack oldFirst = olderStyling(template, 21);
        ItemStack oldSecond = olderStyling(template, 42);
        byte[] firstBytes = oldFirst.serializeAsBytes();
        byte[] secondBytes = oldSecond.serializeAsBytes();
        byte[] templateBytes = template.serializeAsBytes();
        backing.setItem(0, oldFirst);
        backing.setItem(1, oldSecond);

        // Fail AFTER the second slot write, just as a third-party inventory
        // adapter might throw after modifying one of its own slot records.
        AtomicInteger writes = new AtomicInteger();
        Inventory failing = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(backing, args);
                        if (method.getName().equals("setItem") && writes.incrementAndGet() == 2) {
                            throw new IllegalStateException("Synthetic post-write inventory failure");
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });

        var preview = DoctorLiveItemUpdate.preview(failing, ignored -> template);
        assertEquals(2, preview.entries().size());
        UUID owner = UUID.randomUUID();
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                failing, preview, ignored -> template, data, owner));

        assertTrue(writes.get() >= 4, "Both attempted slot changes must be rolled back");
        assertArrayEquals(firstBytes, backing.getItem(0).serializeAsBytes());
        assertArrayEquals(secondBytes, backing.getItem(1).serializeAsBytes());
        assertArrayEquals(templateBytes, template.serializeAsBytes());
        assertNull(backing.getItem(2));
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            var receipts = files.filter(Files::isRegularFile).toList();
            assertEquals(2, receipts.size());
            var originals = receipts.stream()
                    .map(path -> {
                        try {
                            return Files.readAllBytes(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertTrue(originals.stream().anyMatch(bytes -> java.util.Arrays.equals(firstBytes, bytes)));
            assertTrue(originals.stream().anyMatch(bytes -> java.util.Arrays.equals(secondBytes, bytes)));
        }
    }



    @Test
    void nativePhysicalChestFixtureKeepsOtherSlotsExactDuringManualEngineExercise() throws Exception {
        // This is only the disposable native saved-world fixture API: the
        // operator live command still passes PlayerInventory exclusively.
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 63);
        ItemStack typed = current("CARBONADO");
        typed.setAmount(9);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 75123L));
        chest.setItem(0, old);
        chest.setItem(1, template);
        chest.setItem(2, typed);
        byte[] oldBytes = old.serializeAsBytes();
        byte[] freshBytes = template.serializeAsBytes();
        byte[] typedBytes = typed.serializeAsBytes();

        var preview = DoctorLiveItemUpdate.preview(chest, ignored -> template);
        assertEquals(1, preview.eligible());
        UUID owner = UUID.randomUUID();
        var receipts = DoctorLiveItemUpdate.apply(chest, preview, ignored -> template, data, owner);

        assertEquals(1, receipts.size());
        assertEquals(63, chest.getItem(0).getAmount());
        assertTrue(chest.getItem(0).isSimilar(template));
        assertArrayEquals(freshBytes, chest.getItem(1).serializeAsBytes());
        assertArrayEquals(typedBytes, chest.getItem(2).serializeAsBytes());
        assertNull(chest.getItem(3));
        assertArrayEquals(oldBytes, DoctorHistoricalStackRepair.readVerifiedBackup(
                data, owner, receipts.getFirst().id()).serializeAsBytes());
    }

    @Test
    void unrelatedSlotMutationDuringUpdateIsDetectedWithoutOverwritingForeignStack() throws Exception {
        Inventory backing = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack historical = olderStyling(template, 23);
        ItemStack originalUnselected = new ItemStack(Material.STONE, 11);
        ItemStack foreign = new ItemStack(Material.DIAMOND, 2);
        byte[] historicalBytes = historical.serializeAsBytes();
        byte[] foreignBytes = foreign.serializeAsBytes();
        backing.setItem(0, historical);
        backing.setItem(5, originalUnselected);

        AtomicInteger writes = new AtomicInteger();
        Inventory hooked = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(backing, args);
                        if (method.getName().equals("setItem") && Integer.valueOf(0).equals(args[0])
                                && writes.incrementAndGet() == 1) {
                            // Another plugin synchronously modifies an unrelated slot.
                            backing.setItem(5, foreign);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });

        var plan = DoctorLiveItemUpdate.preview(hooked, item -> template);
        assertEquals(1, plan.entries().size());
        UUID owner = UUID.randomUUID();
        IOException failure = assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                hooked, plan, item -> template, data, owner));

        assertEquals("Unselected player inventory slot changed during live update",
                failure.getCause().getMessage());
        assertEquals(2, writes.get(), "Doctor must roll back only its own selected slot");
        assertArrayEquals(historicalBytes, backing.getItem(0).serializeAsBytes());
        assertArrayEquals(foreignBytes, backing.getItem(5).serializeAsBytes(),
                "Doctor must never overwrite the foreign unrelated-slot change");
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            assertEquals(1, files.filter(Files::isRegularFile).count(),
                    "The verified historical receipt must be retained for recovery");
        }
    }

    @Test
    void thirdPartyMutationOfLaterSlotDuringFirstWriteDoesNotOverwriteIt() throws Exception {
        Inventory backing = Bukkit.createInventory(null, 36);
        ItemStack reference = current("CARBONADO");
        ItemStack oldFirst = olderStyling(reference, 19);
        ItemStack oldSecond = olderStyling(reference, 27);
        ItemStack external = current("CARBON_CHUNK");
        external.setAmount(17);
        byte[] originalFirst = oldFirst.serializeAsBytes();
        byte[] replacedLater = external.serializeAsBytes();
        byte[] referenceBytes = reference.serializeAsBytes();
        backing.setItem(0, oldFirst);
        backing.setItem(1, oldSecond);

        AtomicInteger writes = new AtomicInteger();
        Inventory changedDuringWrite = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(backing, args);
                        if (method.getName().equals("setItem")
                                && writes.incrementAndGet() == 1
                                && Integer.valueOf(0).equals(args[0])) {
                            // Reproduce a synchronous addon hook changing a
                            // later planned slot after the first write.
                            backing.setItem(1, external);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });

        var preview = DoctorLiveItemUpdate.preview(changedDuringWrite, ignored -> reference);
        assertEquals(2, preview.entries().size());
        UUID operator = UUID.randomUUID();
        assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                changedDuringWrite, preview, ignored -> reference, data, operator));

        assertEquals(2, writes.get(), "Only first write and conditional rollback may occur");
        assertArrayEquals(originalFirst, backing.getItem(0).serializeAsBytes());
        assertArrayEquals(replacedLater, backing.getItem(1).serializeAsBytes(),
                "External replacement must never be overwritten by stale batch plan");
        assertArrayEquals(referenceBytes, reference.serializeAsBytes());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            assertEquals(2, files.filter(Files::isRegularFile).count(),
                    "Both verified original receipts must be preserved for recovery");
        }
    }

    @Test
    void laterWriteChangingEarlierSlotRefusesSuccessAndPreservesForeignStack() throws Exception {
        Inventory backing = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack oldFirst = olderStyling(template, 19);
        ItemStack oldSecond = olderStyling(template, 27);
        ItemStack external = current("CARBON_CHUNK");
        external.setAmount(17);
        byte[] firstBytes = oldFirst.serializeAsBytes();
        byte[] secondBytes = oldSecond.serializeAsBytes();
        byte[] externalBytes = external.serializeAsBytes();
        byte[] templateBytes = template.serializeAsBytes();
        backing.setItem(0, oldFirst);
        backing.setItem(1, oldSecond);

        AtomicInteger writes = new AtomicInteger();
        Inventory changedDuringWrite = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(backing, args);
                        if (method.getName().equals("setItem")
                                && writes.incrementAndGet() == 2
                                && Integer.valueOf(1).equals(args[0])) {
                            backing.setItem(0, external);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });

        var plan = DoctorLiveItemUpdate.preview(changedDuringWrite, ignored -> template);
        assertEquals(2, plan.entries().size());
        UUID owner = UUID.randomUUID();
        IOException failure = assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                changedDuringWrite, plan, ignored -> template, data, owner));
        assertEquals("Live item update interrupted; original receipts retained", failure.getMessage());
        assertEquals("Live inventory final batch readback disagrees with verified replacement",
                failure.getCause().getMessage());
        assertEquals(3, writes.get());
        assertArrayEquals(externalBytes, backing.getItem(0).serializeAsBytes());
        assertArrayEquals(secondBytes, backing.getItem(1).serializeAsBytes());
        assertArrayEquals(templateBytes, template.serializeAsBytes());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            var receipts = files.filter(Files::isRegularFile).toList();
            assertEquals(2, receipts.size());
            byte[] first = Files.readAllBytes(receipts.get(0));
            byte[] second = Files.readAllBytes(receipts.get(1));
            assertTrue(java.util.Arrays.equals(firstBytes, first) || java.util.Arrays.equals(firstBytes, second));
            assertTrue(java.util.Arrays.equals(secondBytes, first) || java.util.Arrays.equals(secondBytes, second));
        }
    }

    @Test
    void duplicatePlannedSlotRefusesBeforeWritingBackups() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack template = current("CARBONADO");
        ItemStack old = olderStyling(template, 19);
        byte[] original = old.serializeAsBytes();
        inventory.setItem(0, old);
        var plan = DoctorLiveItemUpdate.preview(inventory, ignored -> template);
        var entry = plan.entries().getFirst();
        var duplicate = new DoctorLiveItemUpdate.Preview(
                plan.scannedSlots(), plan.mismatched(), plan.eligible(), plan.refused(),
                List.of(entry, entry), plan.blankLoreOnly(), plan.meaningfulText(),
                plan.functionalMetadata(), plan.unknownIds(), plan.differingSlots());
        IOException failure = assertThrows(IOException.class, () -> DoctorLiveItemUpdate.apply(
                inventory, duplicate, ignored -> template, data, UUID.randomUUID()));
        assertEquals("Live item update plan repeats a slot", failure.getMessage());
        assertArrayEquals(original, inventory.getItem(0).serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void oldSteelModelComponentIsDetectedBeforeMockBukkitLosesItDuringStackCopies() {
        // MockBukkit 4.110.0 does not faithfully preserve modern model
        // components through item copies / ItemMetaMock equality. Assert
        // the model difference at the API boundary; native Paper's two-boot
        // Steel regression proves the real inventory refusal.
        ItemStack current = current("STEEL_INGOT");
        var historicMeta = current.getItemMeta();
        var model = historicMeta.getCustomModelDataComponent();
        model.setFloats(List.of(2200080.0F));
        historicMeta.setCustomModelDataComponent(model);

        assertEquals(List.of(2200080.0F), historicMeta.getCustomModelDataComponent().getFloats());
        assertNotEquals(
                DoctorIdentityCommand.modelData(historicMeta),
                DoctorIdentityCommand.modelData(current.getItemMeta()));
    }

    @Test
    void functionalMetadataChangeInHistoricalSteelChestIsNeverPresentationOnly() {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack current = current("STEEL_INGOT");
        ItemStack old = olderStyling(current, 31);
        // Typed PDC survives MockBukkit's native byte round-trip. Model
        // components are separately covered by real Paper native tests.
        old.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "steel_model_source"), PersistentDataType.INTEGER, 2200080));
        byte[] original = old.serializeAsBytes();
        byte[] template = current.serializeAsBytes();
        chest.setItem(53, old);

        var plan = DoctorLiveItemUpdate.preview(chest, ignored -> current, 54);
        assertEquals(1, plan.mismatched());
        assertEquals(0, plan.eligible());
        assertEquals(1, plan.refused());
        assertEquals(1, plan.functionalMetadata());
        assertTrue(plan.entries().isEmpty());
        assertArrayEquals(original, chest.getItem(53).serializeAsBytes());
        assertArrayEquals(template, current.serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void chestScanSeparatesBlankLoreFromMeaningfulTextTypedDataAndWrongIds() {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack template = current("CARBONADO");

        ItemStack blankLore = ItemStack.deserializeBytes(template.serializeAsBytes());
        blankLore.editMeta(meta -> meta.lore(List.of(Component.empty(), Component.text("Black Diamond"))));
        ItemStack changedText = ItemStack.deserializeBytes(template.serializeAsBytes());
        changedText.editMeta(meta -> meta.lore(List.of(Component.text("Private owner message"))));
        ItemStack changedTypedData = ItemStack.deserializeBytes(template.serializeAsBytes());
        changedTypedData.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787001));
        ItemStack otherId = olderStyling(current("CARBON_CHUNK"), 5);
        ItemStack[] original = {blankLore, changedText, changedTypedData, otherId};
        byte[][] originalBytes = new byte[4][];
        for (int i = 0; i < original.length; i++) {
            originalBytes[i] = original[i].serializeAsBytes();
            chest.setItem(50 + i, original[i]);
        }

        var preview = DoctorLiveItemUpdate.preview(chest, ignored -> template, 54);
        assertEquals(54, preview.scannedSlots());
        assertEquals(3, preview.mismatched());
        assertEquals(List.of(50, 51, 52), preview.differingSlots());
        assertEquals(0, preview.eligible());
        assertEquals(4, preview.refused());
        assertEquals(1, preview.blankLoreOnly());
        assertEquals(1, preview.meaningfulText());
        assertEquals(1, preview.functionalMetadata());
        assertEquals(1, preview.unknownIds());
        assertTrue(preview.entries().isEmpty());
        for (int i = 0; i < original.length; i++) {
            assertArrayEquals(originalBytes[i], chest.getItem(50 + i).serializeAsBytes());
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
        assertEquals(List.of(52, 53), inspection.differingSlots());
        assertEquals(1, inspection.eligible());
        assertEquals(1, inspection.refused());
        assertEquals(1, inspection.entries().size());
        assertEquals(53, inspection.entries().getFirst().slot());
        assertArrayEquals(safeBytes, doubleChest.getItem(53).serializeAsBytes());
        assertArrayEquals(refusedBytes, doubleChest.getItem(52).serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void crossChunkDoubleChestIsRefusedBeforeAccessingItsCombinedInventory() {
        var single = chestBlockData(org.bukkit.block.data.type.Chest.Type.SINGLE);
        var doubleLeft = chestBlockData(org.bukkit.block.data.type.Chest.Type.LEFT);
        var doubleRight = chestBlockData(org.bukkit.block.data.type.Chest.Type.RIGHT);
        assertFalse(DoctorLiveItemUpdate.maySpanAnotherChunk(single, 0, 15));
        assertFalse(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleLeft, 1, 14));
        assertFalse(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleRight, -14, -2));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleLeft, 15, 4));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleRight, 0, 4));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleLeft, -1, 4));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleRight, -16, 4));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleLeft, 4, 0));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleRight, 4, 15));
        assertTrue(DoctorLiveItemUpdate.maySpanAnotherChunk(doubleLeft, 4, -1));
        assertFalse(DoctorLiveItemUpdate.maySpanAnotherChunk(Bukkit.createBlockData(Material.STONE), 0, 15));
    }

    private static org.bukkit.block.data.type.Chest chestBlockData(org.bukkit.block.data.type.Chest.Type type) {
        Class<org.bukkit.block.data.type.Chest> chestClass = org.bukkit.block.data.type.Chest.class;
        return (org.bukkit.block.data.type.Chest) Proxy.newProxyInstance(
                chestClass.getClassLoader(), new Class<?>[] {chestClass}, (ignored, method, args) -> {
                    if (method.getName().equals("getType")) {
                        return type;
                    }
                    throw new UnsupportedOperationException("Unexpected chest block-data read");
                });
    }

    @Test
    void inspectOneStoredChestSlotReportsRedactedMismatchClassWithoutAnyMutation() {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack template = current("CARBONADO");
        ItemStack styled = olderStyling(template, 63);
        ItemStack blankLore = ItemStack.deserializeBytes(template.serializeAsBytes());
        blankLore.editMeta(meta -> meta.lore(List.of(Component.empty(), Component.text("Black Diamond"))));
        ItemStack meaningfulText = ItemStack.deserializeBytes(template.serializeAsBytes());
        meaningfulText.editMeta(meta -> meta.lore(List.of(Component.text("Private historical message"))));
        ItemStack typedOwner = olderStyling(template, 9);
        typedOwner.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787001));
        ItemStack unknown = olderStyling(current("CARBON_CHUNK"), 4);
        ItemStack vanilla = new ItemStack(Material.STONE, 5);
        ItemStack[] before = {styled, blankLore, meaningfulText, typedOwner, unknown, template, vanilla};
        byte[][] bytes = new byte[before.length][];
        for (int i = 0; i < before.length; i++) {
            bytes[i] = before[i].serializeAsBytes();
            chest.setItem(i, before[i]);
        }
        assertEquals(DoctorLiveItemUpdate.SlotStatus.STYLE_ONLY_CANDIDATE,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 0, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.BLANK_LORE_LAYOUT,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 1, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.MEANINGFUL_TEXT,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 2, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.FUNCTIONAL_METADATA,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 3, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.UNKNOWN_ID,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 4, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.CURRENT,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 5, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.NOT_SLIMEFUN,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 6, ignored -> template));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.EMPTY,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 53, ignored -> template));
        assertThrows(IllegalArgumentException.class,
                () -> DoctorLiveItemUpdate.classifyStoredSlot(chest, -1, ignored -> template));
        assertThrows(IllegalArgumentException.class,
                () -> DoctorLiveItemUpdate.classifyStoredSlot(chest, 54, ignored -> template));
        for (int i = 0; i < before.length; i++) {
            assertArrayEquals(bytes[i], chest.getItem(i).serializeAsBytes());
        }
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void oneSlotInspectionFailsClosedWhenAddonResolutionThrowsOrStoredSlotChanges() {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack template = current("CARBONADO");
        ItemStack historical = olderStyling(template, 31);
        byte[] original = historical.serializeAsBytes();
        byte[] registeredBytes = template.serializeAsBytes();
        chest.setItem(0, historical);
        assertEquals(DoctorLiveItemUpdate.SlotStatus.UNSAFE,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 0, ignored -> {
                    throw new IllegalArgumentException("Malformed addon resolver");
                }));
        assertEquals(DoctorLiveItemUpdate.SlotStatus.UNSAFE,
                DoctorLiveItemUpdate.classifyStoredSlot(chest, 0, ignored -> {
                    chest.setItem(0, olderStyling(template, 30));
                    return template;
                }));
        assertEquals(30, chest.getItem(0).getAmount());
        assertFalse(java.util.Arrays.equals(original, chest.getItem(0).serializeAsBytes()));
        assertArrayEquals(registeredBytes, template.serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void chestScanBoundsSlotHintsWithoutChangingStoredItems() {
        Inventory chest = Bukkit.createInventory(null, 54);
        ItemStack template = current("CARBONADO");
        byte[][] originals = new byte[13][];
        for (int slot = 0; slot < originals.length; slot++) {
            ItemStack old = olderStyling(template, slot + 1);
            originals[slot] = old.serializeAsBytes();
            chest.setItem(slot, old);
        }

        var inspection = DoctorLiveItemUpdate.preview(chest, ignored -> template, 54);
        assertEquals(54, inspection.scannedSlots());
        assertEquals(13, inspection.mismatched());
        assertEquals(13, inspection.eligible());
        assertEquals(8, inspection.entries().size());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), inspection.differingSlots());
        for (int slot = 0; slot < originals.length; slot++) {
            assertArrayEquals(originals[slot], chest.getItem(slot).serializeAsBytes());
        }
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
