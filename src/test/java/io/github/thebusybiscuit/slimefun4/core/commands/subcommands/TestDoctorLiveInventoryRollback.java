package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class TestDoctorLiveInventoryRollback {
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

    private static ItemStack template() {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Carbonado").decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(Component.text("Black Diamond")));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "CARBONADO");
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 77713L);
        });
        return item;
    }

    private static ItemStack old(ItemStack template, int amount) {
        ItemStack old = ItemStack.deserializeBytes(template.serializeAsBytes());
        old.setAmount(amount);
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true)));
        return old;
    }

    @Test
    void restoresVerifiedHistoricalPlayerSlotAndPreservesPreviouslyUpdatedBackup() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack reference = template();
        ItemStack old = old(reference, 63);
        byte[] originalBytes = old.serializeAsBytes();
        inventory.setItem(0, old);
        inventory.setItem(22, new ItemStack(Material.STONE, 19));
        byte[] unrelated = inventory.getItem(22).serializeAsBytes();
        UUID owner = UUID.randomUUID();

        var preview = DoctorLiveItemUpdate.preview(inventory, item -> reference);
        var committed = DoctorLiveItemUpdate.apply(inventory, preview, item -> reference, data, owner);
        assertEquals(1, committed.size());
        byte[] updatedBytes = inventory.getItem(0).serializeAsBytes();

        var restorePlan = DoctorLiveInventoryRollback.preview(
                inventory, data, owner, committed.getFirst().id(), 0, item -> reference, 10_000L);
        assertArrayEquals(updatedBytes, inventory.getItem(0).serializeAsBytes());
        assertArrayEquals(unrelated, inventory.getItem(22).serializeAsBytes());

        var restored = DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, restorePlan, item -> reference, 10_050L);
        assertEquals(0, restored.slot());
        assertEquals(committed.getFirst().id(), restored.originalReceipt());
        assertArrayEquals(originalBytes, inventory.getItem(0).serializeAsBytes());
        assertEquals(63, inventory.getItem(0).getAmount());
        assertArrayEquals(unrelated, inventory.getItem(22).serializeAsBytes());
        assertEquals(77713L, inventory.getItem(0).getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG));
        assertArrayEquals(updatedBytes,
                DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, restored.updatedBackupReceipt())
                        .serializeAsBytes());
        assertArrayEquals(originalBytes,
                DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, restored.originalReceipt())
                        .serializeAsBytes());
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, restorePlan, item -> reference, 10_055L));
    }

    @Test
    void staleOrChangedPlayerStorageAndWrongOwnerAreRefusedWithoutWrites() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack reference = template();
        ItemStack old = old(reference, 26);
        inventory.setItem(0, old);
        UUID owner = UUID.randomUUID();
        var committed = DoctorLiveItemUpdate.apply(
                inventory, DoctorLiveItemUpdate.preview(inventory, item -> reference),
                item -> reference, data, owner);
        UUID oldReceipt = committed.getFirst().id();
        byte[] updated = inventory.getItem(0).serializeAsBytes();

        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.preview(
                inventory, data, UUID.randomUUID(), oldReceipt, 0, item -> reference, 200L));
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.preview(
                inventory, data, owner, oldReceipt, 1, item -> reference, 200L));
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.preview(
                inventory, data, owner, oldReceipt, 36, item -> reference, 200L));
        assertFalse(Files.exists(data.resolve("recovery/container-journals")));

        var plan = DoctorLiveInventoryRollback.preview(
                inventory, data, owner, oldReceipt, 0, item -> reference, 200L);
        inventory.setItem(12, new ItemStack(Material.EMERALD, 3));
        byte[] unrelated = inventory.getItem(12).serializeAsBytes();
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, plan, item -> reference, 250L));
        assertArrayEquals(updated, inventory.getItem(0).serializeAsBytes());
        assertArrayEquals(unrelated, inventory.getItem(12).serializeAsBytes());

        inventory.setItem(12, null);
        ItemStack wrongCount = ItemStack.deserializeBytes(updated);
        wrongCount.setAmount(25);
        inventory.setItem(0, wrongCount);
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.preview(
                inventory, data, owner, oldReceipt, 0, item -> reference, 300L));
        inventory.setItem(0, ItemStack.deserializeBytes(updated));
        ItemStack typed = ItemStack.deserializeBytes(updated);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 77713));
        inventory.setItem(0, typed);
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.preview(
                inventory, data, owner, oldReceipt, 0, item -> reference, 300L));
        assertArrayEquals(typed.serializeAsBytes(), inventory.getItem(0).serializeAsBytes());
    }

    @Test
    void synchronousThirdPartySlotMutationAbortsRestoreAndPreservesForeignChange() throws Exception {
        Inventory backing = Bukkit.createInventory(null, 36);
        ItemStack reference = template();
        backing.setItem(0, old(reference, 24));
        UUID owner = UUID.randomUUID();
        UUID receipt = DoctorLiveItemUpdate.apply(
                backing, DoctorLiveItemUpdate.preview(backing, item -> reference),
                item -> reference, data, owner).getFirst().id();
        byte[] updated = backing.getItem(0).serializeAsBytes();
        var writes = new AtomicInteger();
        Inventory withHook = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(backing, args);
                        if (method.getName().equals("setItem")
                                && writes.incrementAndGet() == 1) {
                            backing.setItem(17, new ItemStack(Material.EMERALD, 11));
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        var plan = DoctorLiveInventoryRollback.preview(
                withHook, data, owner, receipt, 0, item -> reference, 1_000L);
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                withHook, data, owner, plan, item -> reference, 1_100L));
        assertEquals(2, writes.get(), "The targeted source write must be rolled back");
        assertArrayEquals(updated, backing.getItem(0).serializeAsBytes());
        assertEquals(11, backing.getItem(17).getAmount());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            assertEquals(2L, files.filter(Files::isRegularFile).count(),
                    "Both old and repaired receipts survive the interrupted restore");
        }
    }

    @Test
    void foreignTargetInsertedDuringRestoreIsNeverOverwrittenByRollback() throws Exception {
        Inventory backing = Bukkit.createInventory(null, 36);
        ItemStack reference = template();
        backing.setItem(0, old(reference, 12));
        UUID owner = UUID.randomUUID();
        UUID receipt = DoctorLiveItemUpdate.apply(
                backing, DoctorLiveItemUpdate.preview(backing, item -> reference),
                item -> reference, data, owner).getFirst().id();
        ItemStack foreign = new ItemStack(Material.DIAMOND, 8);
        var writes = new AtomicInteger();
        Inventory withHook = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class}, (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(backing, args);
                        if (method.getName().equals("setItem")
                                && writes.incrementAndGet() == 1) {
                            backing.setItem(0, foreign);
                        }
                        return result;
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        var plan = DoctorLiveInventoryRollback.preview(
                withHook, data, owner, receipt, 0, item -> reference, 1_000L);
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                withHook, data, owner, plan, item -> reference, 1_100L));
        assertEquals(1, writes.get(), "Never overwrite the foreign replacement in the target slot");
        assertArrayEquals(foreign.serializeAsBytes(), backing.getItem(0).serializeAsBytes());
        try (var files = Files.list(data.resolve("recovery/identity"))) {
            assertEquals(2L, files.filter(Files::isRegularFile).count(),
                    "Both private receipts remain available for manual recovery");
        }
    }

    @Test
    void lookupCallbackCannotMutateOriginalBackupIntoAnAcceptedRestore() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack reference = template();
        inventory.setItem(0, old(reference, 17));
        UUID owner = UUID.randomUUID();
        UUID receipt = DoctorLiveItemUpdate.apply(
                inventory, DoctorLiveItemUpdate.preview(inventory, item -> reference),
                item -> reference, data, owner).getFirst().id();
        byte[] updated = inventory.getItem(0).serializeAsBytes();
        byte[] original = DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, receipt)
                .serializeAsBytes();
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.preview(
                inventory, data, owner, receipt, 0, item -> {
                    item.setAmount(item.getAmount() - 1);
                    return reference;
                }, 1_000L));
        assertArrayEquals(updated, inventory.getItem(0).serializeAsBytes());
        assertArrayEquals(original,
                DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, receipt).serializeAsBytes());
    }

    @Test
    void armorAndOffhandChangesInvalidateFullPlayerInventoryRollback() throws Exception {
        var player = MockBukkit.getMock().addPlayer();
        var inventory = player.getInventory();
        ItemStack reference = template();
        inventory.setItem(0, old(reference, 9));
        UUID owner = player.getUniqueId();
        UUID receipt = DoctorLiveItemUpdate.apply(
                inventory, DoctorLiveItemUpdate.preview(inventory, item -> reference),
                item -> reference, data, owner).getFirst().id();
        byte[] repaired = inventory.getItem(0).serializeAsBytes();

        var armorPlan = DoctorLiveInventoryRollback.preview(
                inventory, data, owner, receipt, 0, item -> reference, 1_000L);
        inventory.setHelmet(new ItemStack(Material.IRON_HELMET, 1));
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, armorPlan, item -> reference, 1_100L));
        assertArrayEquals(repaired, inventory.getItem(0).serializeAsBytes());
        assertEquals(Material.IRON_HELMET, inventory.getHelmet().getType());

        inventory.setHelmet(null);
        var offhandPlan = DoctorLiveInventoryRollback.preview(
                inventory, data, owner, receipt, 0, item -> reference, 2_000L);
        inventory.setItemInOffHand(new ItemStack(Material.DIAMOND, 5));
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, offhandPlan, item -> reference, 2_100L));
        assertArrayEquals(repaired, inventory.getItem(0).serializeAsBytes());
        assertEquals(5, inventory.getItemInOffHand().getAmount());
    }

    @Test
    void expiryAndMissingOriginalDoNotDestroyUpdatedPlayerItems() throws Exception {
        Inventory inventory = Bukkit.createInventory(null, 36);
        ItemStack reference = template();
        inventory.setItem(5, old(reference, 5));
        UUID owner = UUID.randomUUID();
        var committed = DoctorLiveItemUpdate.apply(
                inventory, DoctorLiveItemUpdate.preview(inventory, item -> reference),
                item -> reference, data, owner);
        UUID receipt = committed.getFirst().id();
        byte[] updated = inventory.getItem(5).serializeAsBytes();
        var plan = DoctorLiveInventoryRollback.preview(
                inventory, data, owner, receipt, 5, item -> reference, 100L);
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, plan, item -> reference, 60_102L));
        assertArrayEquals(updated, inventory.getItem(5).serializeAsBytes());
        Path original = data.resolve("recovery/identity/" + owner + "-" + receipt + ".bin");
        Files.delete(original);
        assertThrows(IOException.class, () -> DoctorLiveInventoryRollback.confirm(
                inventory, data, owner, plan, item -> reference, 105L));
        assertArrayEquals(updated, inventory.getItem(5).serializeAsBytes());
    }
}
