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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestDoctorHistoricalStackEvidenceAudit {

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

    private static ItemStack reference(String id) {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Carbonado").decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(Component.text("Black Diamond")));
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("addon", "storage_owner"), PersistentDataType.LONG, 787001L);
        });
        return stack;
    }

    private static ItemStack olderStyling(ItemStack current, int amount) {
        ItemStack old = ItemStack.deserializeBytes(current.serializeAsBytes());
        old.setAmount(amount);
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true)));
        return old;
    }

    @Test
    void replayOfPrivateHistoricalReceiptsIsReadOnlyAndRejectsTemplateDrift() throws Exception {
        ItemStack live = reference("CARBONADO");
        ItemStack old = olderStyling(live, 41);
        byte[] originalBytes = old.serializeAsBytes();
        byte[] templateBytes = live.serializeAsBytes();
        UUID owner = UUID.randomUUID();
        var receipts = DoctorIdentityEvidenceExport.capture(data, owner, old, live);
        Path directory = data.resolve("recovery/identity");
        byte[] receiptOld = Files.readAllBytes(directory.resolve(owner + "-" + receipts.mainReceipt() + ".bin"));
        byte[] receiptReference = Files.readAllBytes(directory.resolve(owner + "-" + receipts.offhandReceipt() + ".bin"));

        var first = DoctorHistoricalStackEvidenceAudit.inspect(
                data, owner, receipts.mainReceipt(), receipts.offhandReceipt(), ignored -> live);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.STYLE_ONLY_CANDIDATE, first.savedDifference());
        assertTrue(first.savedReferenceStillRegistered());
        assertFalse(first.historicalMatchesCurrent());
        assertTrue(first.strictStyleOnlyCandidate());

        ItemStack updatedRegistration = ItemStack.deserializeBytes(templateBytes);
        updatedRegistration.editMeta(meta -> meta.lore(List.of(Component.text("New registered lore"))));
        var afterChange = DoctorHistoricalStackEvidenceAudit.inspect(
                data, owner, receipts.mainReceipt(), receipts.offhandReceipt(), ignored -> updatedRegistration);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.STYLE_ONLY_CANDIDATE,
                afterChange.savedDifference());
        assertFalse(afterChange.savedReferenceStillRegistered());
        assertFalse(afterChange.strictStyleOnlyCandidate());
        assertFalse(afterChange.historicalMatchesCurrent());

        assertArrayEquals(originalBytes, old.serializeAsBytes());
        assertEquals(41, old.getAmount());
        assertArrayEquals(templateBytes, live.serializeAsBytes());
        assertArrayEquals(receiptOld,
                Files.readAllBytes(directory.resolve(owner + "-" + receipts.mainReceipt() + ".bin")));
        assertArrayEquals(receiptReference,
                Files.readAllBytes(directory.resolve(owner + "-" + receipts.offhandReceipt() + ".bin")));
        try (var files = Files.list(directory)) {
            assertEquals(2L, files.count());
        }
    }

    @Test
    void oldLoreAndTypedMetadataDifferencesNeverAuthorizeStyleRecovery() throws Exception {
        ItemStack live = reference("CARBONADO");
        ItemStack blankLore = ItemStack.deserializeBytes(live.serializeAsBytes());
        blankLore.editMeta(meta -> meta.lore(List.of(Component.empty(), Component.text("Black Diamond"))));
        ItemStack meaningfulLore = ItemStack.deserializeBytes(live.serializeAsBytes());
        meaningfulLore.editMeta(meta -> meta.lore(List.of(Component.text("Private shop owner text"))));
        ItemStack typedOwner = ItemStack.deserializeBytes(live.serializeAsBytes());
        typedOwner.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "storage_owner"), PersistentDataType.INTEGER, 787001));
        ItemStack[] oldItems = {blankLore, meaningfulLore, typedOwner, live};
        var classes = new DoctorHistoricalStackEvidenceAudit.Difference[] {
                DoctorHistoricalStackEvidenceAudit.Difference.BLANK_LORE_LAYOUT,
                DoctorHistoricalStackEvidenceAudit.Difference.MEANINGFUL_TEXT,
                DoctorHistoricalStackEvidenceAudit.Difference.FUNCTIONAL_METADATA,
                DoctorHistoricalStackEvidenceAudit.Difference.CURRENT
        };
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < oldItems.length; i++) {
            ItemStack old = oldItems[i];
            byte[] before = old.serializeAsBytes();
            var receipts = DoctorIdentityEvidenceExport.capture(data, owner, old, live);
            var audit = DoctorHistoricalStackEvidenceAudit.inspect(
                    data, owner, receipts.mainReceipt(), receipts.offhandReceipt(), ignored -> live);
            assertEquals(classes[i], audit.savedDifference());
            assertFalse(audit.strictStyleOnlyCandidate());
            assertTrue(audit.savedReferenceStillRegistered());
            assertArrayEquals(before, old.serializeAsBytes());
        }
    }

    @Test
    void malformedWrongOwnerAndCrossIdReceiptsFailClosedWithoutWrites() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ItemStack old = olderStyling(reference("CARBONADO"), 33);
        ItemStack current = reference("CARBONADO");
        var pair = DoctorIdentityEvidenceExport.capture(data, owner, old, current);
        assertThrows(IOException.class, () -> DoctorHistoricalStackEvidenceAudit.inspect(
                data, other, pair.mainReceipt(), pair.offhandReceipt(), ignored -> current));
        assertThrows(IOException.class, () -> DoctorHistoricalStackEvidenceAudit.inspect(
                data, owner, pair.mainReceipt(), pair.mainReceipt(), ignored -> current));
        var unavailable = DoctorHistoricalStackEvidenceAudit.inspect(
                data, owner, pair.mainReceipt(), pair.offhandReceipt(), ignored -> null);
        assertFalse(unavailable.savedReferenceStillRegistered());
        assertFalse(unavailable.strictStyleOnlyCandidate());

        var swapped = DoctorIdentityEvidenceExport.capture(data, owner, old, reference("CARBON_CHUNK"));
        assertThrows(IOException.class, () -> DoctorHistoricalStackEvidenceAudit.inspect(
                data, owner, swapped.mainReceipt(), swapped.offhandReceipt(), ignored -> current));

        Path source = data.resolve("recovery/identity/" + owner + "-" + pair.mainReceipt() + ".bin");
        Files.write(source, new byte[] {0x1, 0x2, 0x3});
        assertThrows(IOException.class, () -> DoctorHistoricalStackEvidenceAudit.inspect(
                data, owner, pair.mainReceipt(), pair.offhandReceipt(), ignored -> current));
        assertArrayEquals(new byte[] {0x1, 0x2, 0x3}, Files.readAllBytes(source));
    }
}
