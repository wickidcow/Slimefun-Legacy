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
import java.util.concurrent.atomic.AtomicInteger;
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

class TestDoctorQuickShopReadOnly {
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
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG, 75123L);
        });
        return item;
    }

    private static ItemStack historical(ItemStack template, int amount) {
        ItemStack old = ItemStack.deserializeBytes(template.serializeAsBytes());
        old.setAmount(amount);
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, true)));
        return old;
    }

    @Test
    void recognizesOldShopReferenceAndStockAgainstUnchangedNewSlimefunItem() throws Exception {
        ItemStack current = current("CARBONADO");
        ItemStack oldShop = historical(current, 1);
        ItemStack oldStock = historical(current, 63);
        byte[] oldShopBytes = oldShop.serializeAsBytes();
        byte[] oldStockBytes = oldStock.serializeAsBytes();
        byte[] currentBytes = current.serializeAsBytes();

        var report = DoctorQuickShopReadOnly.compare(oldShop, oldStock, current, oldShop::isSimilar);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.STYLE_ONLY_CANDIDATE, report.saleDifference());
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.STYLE_ONLY_CANDIDATE, report.stockDifference());
        assertTrue(report.stockPresent());
        assertTrue(report.saleMatchesStock());
        assertFalse(report.matcherAcceptsFresh());
        assertTrue(report.matcherAcceptsStock());
        assertArrayEquals(oldShopBytes, oldShop.serializeAsBytes());
        assertArrayEquals(oldStockBytes, oldStock.serializeAsBytes());
        assertArrayEquals(currentBytes, current.serializeAsBytes());
        assertEquals(63, oldStock.getAmount());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void distinguishesStaleShopReferenceFromUpdatedChestStock() throws Exception {
        ItemStack current = current("CARBONADO");
        ItemStack oldShop = historical(current, 1);
        ItemStack stock = ItemStack.deserializeBytes(current.serializeAsBytes());
        stock.setAmount(42);

        var report = DoctorQuickShopReadOnly.compare(oldShop, stock, current, oldShop::isSimilar);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.STYLE_ONLY_CANDIDATE, report.saleDifference());
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.CURRENT, report.stockDifference());
        assertFalse(report.saleMatchesStock());
        assertFalse(report.matcherAcceptsFresh());
        assertFalse(report.matcherAcceptsStock());
        assertEquals(42, stock.getAmount());
    }

    @Test
    void reportsEmptyStockWithoutDiscardingSavedSaleReference() throws Exception {
        ItemStack current = current("CARBONADO");
        var report = DoctorQuickShopReadOnly.compare(current, null, current, current::isSimilar);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.CURRENT, report.saleDifference());
        assertNull(report.stockDifference());
        assertFalse(report.stockPresent());
        assertTrue(report.matcherAcceptsFresh());
        assertFalse(report.matcherAcceptsStock());
    }

    @Test
    void neverCallsPermissiveMatcherWhenSlimefunIdIsDifferent() {
        ItemStack current = current("CARBONADO");
        ItemStack other = historical(current("CARBON_CHUNK"), 13);
        AtomicInteger checked = new AtomicInteger();
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                current, other, current, ignored -> {
                    checked.incrementAndGet();
                    return true;
                }));
        assertEquals(0, checked.get());
    }

    @Test
    void typedOwnerAndMeaningfulLoreRemainDifferentDespiteMatcherConfiguration() throws Exception {
        ItemStack current = current("CARBONADO");
        ItemStack typed = historical(current, 9);
        typed.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 75123));
        ItemStack named = ItemStack.deserializeBytes(current.serializeAsBytes());
        named.editMeta(meta -> meta.lore(List.of(Component.text("Private historical shop lore"))));
        byte[] ownerBytes = typed.serializeAsBytes();
        byte[] nameBytes = named.serializeAsBytes();
        var result = DoctorQuickShopReadOnly.compare(typed, named, current, ignored -> true);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.FUNCTIONAL_METADATA, result.saleDifference());
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.MEANINGFUL_TEXT, result.stockDifference());
        assertTrue(result.matcherAcceptsFresh());
        assertTrue(result.matcherAcceptsStock());
        assertFalse(result.saleMatchesStock());
        assertArrayEquals(ownerBytes, typed.serializeAsBytes());
        assertArrayEquals(nameBytes, named.serializeAsBytes());
    }

    @Test
    void refusesMaliciousMatcherThatMutatesInputAndThrows() {
        ItemStack template = current("CARBONADO");
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                template, template, template, ignored -> {
                    throw new IllegalArgumentException("Matcher unavailable");
                }));
        ItemStack original = historical(template, 24);
        byte[] before = original.serializeAsBytes();
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                original, original, template, ignored -> {
                    original.setAmount(25);
                    return true;
                }));
        assertFalse(java.util.Arrays.equals(before, original.serializeAsBytes()));
        assertEquals(25, original.getAmount());
    }

    @Test
    void emptyOrUnregisteredSaleSourceIsNeverTreatedAsCurrent() {
        ItemStack current = current("CARBONADO");
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                null, current, current, ignored -> true));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                new ItemStack(Material.STONE), current, current, ignored -> true));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                current("UNKNOWN_ADDON"), current, current, ignored -> true));
    }
}
