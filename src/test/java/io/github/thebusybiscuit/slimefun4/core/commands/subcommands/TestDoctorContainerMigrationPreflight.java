package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class TestDoctorContainerMigrationPreflight {

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

    private static ItemStack old(ItemStack template, int amount) {
        ItemStack result = ItemStack.deserializeBytes(template.serializeAsBytes());
        result.setAmount(amount);
        result.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true)));
        return result;
    }

    @Test
    void receiptPreflightBuildsOnlyStyleAfterImageAndPreservesAllCounts() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current();
        ItemStack historical = old(template, 63);
        ItemStack functional = old(template, 9);
        functional.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787001));
        byte[] historicalBytes = historical.serializeAsBytes();
        byte[] functionalBytes = functional.serializeAsBytes();
        chest.setItem(0, historical);
        chest.setItem(1, template);
        chest.setItem(2, functional);
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        UUID operator = UUID.randomUUID();
        var location = binding();
        var receipt = DoctorContainerEvidenceEnvelope.capture(data, operator, location, chest);

        var plan = DoctorContainerMigrationPreflight.prepare(
                data, operator, receipt.id(), location, chest, item -> template);
        assertEquals(1, plan.changedStacks());
        assertEquals(List.of(0), plan.changedSlots());
        assertEquals(90, plan.originalItems());
        assertEquals(plan.originalItems(), plan.proposedItems());
        assertFalse(plan.beforeSha256().equals(plan.proposedSha256()));
        assertArrayEquals(historicalBytes, chest.getItem(0).serializeAsBytes());
        assertArrayEquals(functionalBytes, chest.getItem(2).serializeAsBytes());
        assertFalse(DoctorContainerMigrationPreflight.matchesProposedAfter(plan, location, chest));

        // Separate disposable comparison inventory: production preflight NEVER
        // wrote a chest or its private original receipt.
        Inventory syntheticAfter = Bukkit.createInventory(null, 27);
        for (int slot = 0; slot < chest.getSize(); slot++) {
            ItemStack item = chest.getItem(slot);
            if (item != null) {
                syntheticAfter.setItem(slot, ItemStack.deserializeBytes(item.serializeAsBytes()));
            }
        }
        ItemStack replacement = ItemStack.deserializeBytes(template.serializeAsBytes());
        replacement.setAmount(63);
        syntheticAfter.setItem(0, replacement);
        assertTrue(DoctorContainerMigrationPreflight.matchesProposedAfter(plan, location, syntheticAfter));
        syntheticAfter.setItem(26, new ItemStack(Material.STONE, 16));
        assertFalse(DoctorContainerMigrationPreflight.matchesProposedAfter(plan, location, syntheticAfter));
        assertTrue(DoctorContainerEvidenceEnvelope.verify(
                data, operator, receipt.id(), location, chest).unchanged());
        assertFalse(Files.exists(data.resolve("recovery/container-plans")));
    }

    @Test
    void changedAnySlotOrTemplateNeverProducesAStaleAfterImage() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current();
        ItemStack historical = old(template, 7);
        byte[] oldBytes = historical.serializeAsBytes();
        chest.setItem(0, historical);
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        UUID operator = UUID.randomUUID();
        var location = binding();
        var receipt = DoctorContainerEvidenceEnvelope.capture(data, operator, location, chest);

        chest.setItem(26, new ItemStack(Material.STONE, 16));
        assertThrows(IOException.class, () -> DoctorContainerMigrationPreflight.prepare(
                data, operator, receipt.id(), location, chest, item -> template));
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        assertThrows(IOException.class, () -> DoctorContainerMigrationPreflight.prepare(
                data, UUID.randomUUID(), receipt.id(), location, chest, item -> template));
        assertThrows(IOException.class, () -> DoctorContainerMigrationPreflight.prepare(
                data, operator, receipt.id(), location, chest, item -> {
                    chest.setItem(26, new ItemStack(Material.STONE, 16));
                    return template;
                }));
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        ItemStack meaningful = ItemStack.deserializeBytes(template.serializeAsBytes());
        meaningful.editMeta(meta -> meta.lore(List.of(Component.text("changed meaning"))));
        assertThrows(IOException.class, () -> DoctorContainerMigrationPreflight.prepare(
                data, operator, receipt.id(), location, chest, item -> meaningful));
        assertArrayEquals(oldBytes, chest.getItem(0).serializeAsBytes());
        assertEquals(17, chest.getItem(26).getAmount());
    }

    @Test
    void moreThanEightEligibleStacksRequiresSeparateOperatorEvidenceInsteadOfPartialPlan() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack template = current();
        for (int i = 0; i < 9; i++) {
            chest.setItem(i, old(template, i + 1));
        }
        UUID operator = UUID.randomUUID();
        var location = binding();
        var receipt = DoctorContainerEvidenceEnvelope.capture(data, operator, location, chest);
        assertThrows(IOException.class, () -> DoctorContainerMigrationPreflight.prepare(
                data, operator, receipt.id(), location, chest, item -> template));
        for (int slot = 0; slot < 9; slot++) {
            assertEquals(slot + 1, chest.getItem(slot).getAmount());
        }
    }
}
