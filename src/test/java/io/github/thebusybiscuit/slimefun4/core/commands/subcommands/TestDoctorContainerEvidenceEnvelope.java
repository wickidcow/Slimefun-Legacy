package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestDoctorContainerEvidenceEnvelope {

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

    private static DoctorContainerEvidenceEnvelope.Binding binding(UUID world, int slots) {
        return new DoctorContainerEvidenceEnvelope.Binding(
                world, 12, 70, -8, "minecraft:chest[facing=north,type=single,waterlogged=false]", slots);
    }

    @Test
    void privateFullContainerReceiptPreservesEverySlotAndSurvivesReopenWithoutWrites() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack original = new ItemStack(Material.PAPER, 63);
        original.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 57L));
        chest.setItem(0, original);
        chest.setItem(20, new ItemStack(Material.STONE, 19));
        byte[] originalBytes = original.serializeAsBytes();
        byte[] ordinaryBytes = chest.getItem(20).serializeAsBytes();
        UUID owner = UUID.randomUUID();
        var place = binding(UUID.randomUUID(), 27);

        var receipt = DoctorContainerEvidenceEnvelope.capture(data, owner, place, chest);
        assertEquals(27, receipt.slots());
        var result = DoctorContainerEvidenceEnvelope.verify(data, owner, receipt.id(), place, chest);
        assertEquals(27, result.slots());
        assertTrue(result.unchanged());
        assertArrayEquals(originalBytes, chest.getItem(0).serializeAsBytes());
        assertArrayEquals(ordinaryBytes, chest.getItem(20).serializeAsBytes());
        assertTrue(Files.isRegularFile(data.resolve(
                "recovery/containers/" + owner + "-" + receipt.id() + ".bin")));

        chest.setItem(26, new ItemStack(Material.DIRT, 1));
        assertFalse(DoctorContainerEvidenceEnvelope.verify(data, owner, receipt.id(), place, chest).unchanged());
        chest.setItem(26, null);
        chest.setItem(20, new ItemStack(Material.STONE, 18));
        assertFalse(DoctorContainerEvidenceEnvelope.verify(data, owner, receipt.id(), place, chest).unchanged());
        chest.setItem(20, new ItemStack(Material.STONE, 19));
        assertTrue(DoctorContainerEvidenceEnvelope.verify(data, owner, receipt.id(), place, chest).unchanged());
        assertArrayEquals(originalBytes, chest.getItem(0).serializeAsBytes());
    }


    @Test
    void receiptBoundReadOnlyAssessmentRequiresAllSlotsAndRejectsFunctionalPdc() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack current = new ItemStack(Material.PAPER, 1);
        current.editMeta(meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("Carbonado")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
            meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Black Diamond")));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "CARBONADO");
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 787_001L);
        });
        ItemStack old = ItemStack.deserializeBytes(current.serializeAsBytes());
        old.setAmount(63);
        old.editMeta(meta -> meta.displayName(
                meta.displayName().decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)));
        ItemStack typedOwner = ItemStack.deserializeBytes(old.serializeAsBytes());
        typedOwner.setAmount(9);
        typedOwner.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 787_001));
        chest.setItem(0, old);
        chest.setItem(1, current);
        chest.setItem(2, typedOwner);
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        byte[] historicalBytes = old.serializeAsBytes();
        byte[] typedBytes = typedOwner.serializeAsBytes();
        UUID operator = UUID.randomUUID();
        var source = binding(UUID.randomUUID(), 27);
        var receipt = DoctorContainerEvidenceEnvelope.capture(data, operator, source, chest);

        var assessment = DoctorContainerEvidenceEnvelope.assess(
                data, operator, receipt.id(), source, chest, ignored -> current);
        assertEquals(27, assessment.slots());
        assertEquals(1, assessment.eligible());
        assertEquals(1, assessment.boundedCandidates());
        assertEquals(java.util.List.of(0), assessment.candidateSlots());
        assertTrue(assessment.refused() >= 1);
        assertTrue(assessment.functionalMetadata() >= 1);
        assertArrayEquals(historicalBytes, chest.getItem(0).serializeAsBytes());
        assertArrayEquals(typedBytes, chest.getItem(2).serializeAsBytes());

        chest.setItem(26, new ItemStack(Material.STONE, 16));
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.assess(
                data, operator, receipt.id(), source, chest, ignored -> current));
        chest.setItem(26, new ItemStack(Material.STONE, 17));
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.assess(
                data, UUID.randomUUID(), receipt.id(), source, chest, ignored -> current));
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.assess(
                data, operator, receipt.id(), binding(UUID.randomUUID(), 27), chest, ignored -> current));
        assertArrayEquals(historicalBytes, chest.getItem(0).serializeAsBytes());
        assertArrayEquals(typedBytes, chest.getItem(2).serializeAsBytes());
    }

    @Test
    void receiptBoundPreviewRefusesConcurrentUnrelatedChangesDuringTemplateResolution() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack current = new ItemStack(Material.PAPER, 1);
        current.editMeta(meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("Carbonado")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "CARBONADO");
        });
        ItemStack old = ItemStack.deserializeBytes(current.serializeAsBytes());
        old.editMeta(meta -> meta.displayName(
                meta.displayName().decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)));
        chest.setItem(0, old);
        chest.setItem(12, new ItemStack(Material.STONE, 5));
        byte[] unchangedOriginal = old.serializeAsBytes();
        UUID operator = UUID.randomUUID();
        var source = binding(UUID.randomUUID(), 27);
        var receipt = DoctorContainerEvidenceEnvelope.capture(data, operator, source, chest);

        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.assess(
                data, operator, receipt.id(), source, chest, observed -> {
                    chest.setItem(12, new ItemStack(Material.STONE, 4));
                    return current;
                }));
        assertArrayEquals(unchangedOriginal, chest.getItem(0).serializeAsBytes());
        assertEquals(4, chest.getItem(12).getAmount());
    }

    @Test
    void ownerWorldBlockAndOpaqueDataAreNotInterchangeable() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        chest.setItem(0, new ItemStack(Material.PAPER, 11));
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var source = binding(world, 27);
        var receipt = DoctorContainerEvidenceEnvelope.capture(data, owner, source, chest);

        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.verify(
                data, UUID.randomUUID(), receipt.id(), source, chest));
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.verify(
                data, owner, receipt.id(), binding(UUID.randomUUID(), 27), chest));
        var alternate = new DoctorContainerEvidenceEnvelope.Binding(
                world, 13, 70, -8, source.blockData(), 27);
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.verify(
                data, owner, receipt.id(), alternate, chest));
        var oldBlockData = new DoctorContainerEvidenceEnvelope.Binding(
                world, 12, 70, -8, "minecraft:barrel[facing=up,open=false]", 27);
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.verify(
                data, owner, receipt.id(), oldBlockData, chest));

        Path file = data.resolve("recovery/containers/" + owner + "-" + receipt.id() + ".bin");
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 0x1;
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.verify(
                data, owner, receipt.id(), source, chest));
        assertEquals(11, chest.getItem(0).getAmount());
    }

    @Test
    void corruptedRecoveryPathFailsBeforeAnyInventoryOrItemMutation() throws Exception {
        Inventory chest = Bukkit.createInventory(null, 27);
        chest.setItem(2, new ItemStack(Material.STONE, 7));
        byte[] untouched = chest.getItem(2).serializeAsBytes();
        UUID owner = UUID.randomUUID();
        Files.write(data.resolve("recovery"), new byte[] {1, 2});
        assertThrows(IOException.class, () -> DoctorContainerEvidenceEnvelope.capture(
                data, owner, binding(UUID.randomUUID(), 27), chest));
        assertArrayEquals(untouched, chest.getItem(2).serializeAsBytes());
    }
}
