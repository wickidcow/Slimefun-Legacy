package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class TestDoctorContainerTransactionJournal {

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

    private static DoctorContainerEvidenceEnvelope.Binding binding() {
        return new DoctorContainerEvidenceEnvelope.Binding(
                UUID.randomUUID(), 10, 180, 3, "minecraft:chest[facing=north,type=single,waterlogged=false]", 27);
    }

    private static ItemStack current() {
        ItemStack template = new ItemStack(Material.PAPER, 1);
        template.editMeta(meta -> {
            meta.displayName(Component.text("Carbonado").decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(Component.text("Black Diamond")));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "CARBONADO");
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 787_001L);
        });
        return template;
    }

    private static ItemStack historical(ItemStack current, int amount) {
        ItemStack old = ItemStack.deserializeBytes(current.serializeAsBytes());
        old.setAmount(amount);
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true)));
        return old;
    }

    @Test
    void crashReplayJournalDistinguishesOriginalProposedAndUnexpectedStockWithoutWrites() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current();
        ItemStack old = historical(template, 63);
        ItemStack typed = historical(template, 9);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787001));
        chest.setItem(0, old);
        chest.setItem(1, template);
        chest.setItem(2, typed);
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        byte[] oldBytes = old.serializeAsBytes();
        byte[] typedBytes = typed.serializeAsBytes();
        UUID operator = UUID.randomUUID();
        var binding = binding();
        var origin = DoctorContainerEvidenceEnvelope.capture(data, operator, binding, chest);
        var journal = DoctorContainerTransactionJournal.begin(
                data, operator, origin.id(), binding, chest, ignored -> template);
        Path file = data.resolve("recovery/container-journals/" + operator + "-" + journal.id() + ".bin");

        assertEquals(DoctorContainerTransactionJournal.State.ORIGINAL,
                DoctorContainerTransactionJournal.inspect(
                        data, operator, journal.id(), binding, chest).state());
        assertEquals(90, journal.beforeItems());
        assertEquals(journal.beforeItems(), journal.afterItems());
        assertEquals(List.of(0), journal.changedSlots());
        assertArrayEquals(oldBytes, chest.getItem(0).serializeAsBytes());
        assertArrayEquals(typedBytes, chest.getItem(2).serializeAsBytes());
        assertEquals(true, Files.isRegularFile(file));

        // TEST ONLY: synthetic after image in separate disposable inventory.
        Inventory simulated = Bukkit.createInventory(null, 27);
        for (int i = 0; i < chest.getSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack != null) {
                simulated.setItem(i, ItemStack.deserializeBytes(stack.serializeAsBytes()));
            }
        }
        ItemStack updated = ItemStack.deserializeBytes(template.serializeAsBytes());
        updated.setAmount(63);
        simulated.setItem(0, updated);
        assertEquals(DoctorContainerTransactionJournal.State.PROPOSED,
                DoctorContainerTransactionJournal.inspect(
                        data, operator, journal.id(), binding, simulated).state());
        simulated.setItem(26, new ItemStack(Material.STONE, 16));
        assertEquals(DoctorContainerTransactionJournal.State.DIVERGED,
                DoctorContainerTransactionJournal.inspect(
                        data, operator, journal.id(), binding, simulated).state());
        assertArrayEquals(typedBytes, chest.getItem(2).serializeAsBytes());

        assertThrows(IOException.class, () -> DoctorContainerTransactionJournal.inspect(
                data, UUID.randomUUID(), journal.id(), binding, chest));
        var otherLocation = new DoctorContainerEvidenceEnvelope.Binding(
                binding.worldId(), 12, binding.y(), binding.z(), binding.blockData(), binding.slots());
        assertThrows(IOException.class, () -> DoctorContainerTransactionJournal.inspect(
                data, operator, journal.id(), otherLocation, chest));
        assertFalse(Files.exists(data.resolve("recovery/transaction-commits")));
    }

    @Test
    void tamperedJournalOrMissingOriginalEvidenceRefusesAndNeverChangesStock() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current();
        ItemStack old = historical(template, 31);
        byte[] before = old.serializeAsBytes();
        chest.setItem(0, old);
        UUID operator = UUID.randomUUID();
        var binding = binding();
        var origin = DoctorContainerEvidenceEnvelope.capture(data, operator, binding, chest);
        var journal = DoctorContainerTransactionJournal.begin(
                data, operator, origin.id(), binding, chest, ignored -> template);
        Path path = data.resolve("recovery/container-journals/" + operator + "-" + journal.id() + ".bin");
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 0x10;
        Files.write(path, bytes);
        assertThrows(IOException.class, () -> DoctorContainerTransactionJournal.inspect(
                data, operator, journal.id(), binding, chest));
        assertArrayEquals(before, chest.getItem(0).serializeAsBytes());
        Files.delete(path);
        assertThrows(IOException.class, () -> DoctorContainerTransactionJournal.inspect(
                data, operator, journal.id(), binding, chest));
        assertArrayEquals(before, chest.getItem(0).serializeAsBytes());
    }

    @Test
    void failedOrRacingOriginDoesNotProduceACommittedJournal() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current();
        ItemStack old = historical(template, 18);
        chest.setItem(0, old);
        chest.setItem(26, new ItemStack(Material.STONE, 7));
        UUID operator = UUID.randomUUID();
        var binding = binding();
        var origin = DoctorContainerEvidenceEnvelope.capture(data, operator, binding, chest);
        assertThrows(IOException.class, () -> DoctorContainerTransactionJournal.begin(
                data, operator, origin.id(), binding, chest, ignored -> {
                    chest.setItem(26, new ItemStack(Material.STONE, 6));
                    return template;
                }));
        assertEquals(18, chest.getItem(0).getAmount());
        assertEquals(6, chest.getItem(26).getAmount());
        assertFalse(Files.exists(data.resolve("recovery/container-journals")));
    }
}
