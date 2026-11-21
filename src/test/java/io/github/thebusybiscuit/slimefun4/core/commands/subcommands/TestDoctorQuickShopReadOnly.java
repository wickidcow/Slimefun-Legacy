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
import org.bukkit.configuration.file.YamlConfiguration;
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

    private static String cachedYaml(ItemStack stack) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("item", stack);
        return configuration.saveToString();
    }

    @Test
    void mockYamlCodecDriftIsNotMistakenForSafeHistoricalPresentation() throws Exception {
        ItemStack current = current("CARBONADO");
        ItemStack old = historical(current, 1);
        ItemStack stock = historical(current, 63);
        byte[] savedCurrent = current.serializeAsBytes();
        byte[] savedOld = old.serializeAsBytes();
        byte[] savedStock = stock.serializeAsBytes();
        var result = DoctorQuickShopReadOnly.compareCached(cachedYaml(old), old, stock, current);
        // MockBukkit's YAML codec can alter non-presentation metadata when
        // rebuilding a component-bearing item. That drift is UNSAFE to
        // normalize as just a styling change. The native Paper test checks
        // its own separate serialized compatibility.
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.FUNCTIONAL_METADATA,
                result.cachedDifference());
        assertEquals("CARBONADO", result.item().getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING));
        assertArrayEquals(savedOld, old.serializeAsBytes());
        assertArrayEquals(savedCurrent, current.serializeAsBytes());
        assertArrayEquals(savedStock, stock.serializeAsBytes());
    }

    @Test
    void detectsRetrieveListenerChangingViewWithoutRewritingQuickShopCache() throws Exception {
        ItemStack current = current("CARBONADO");
        ItemStack oldView = historical(current, 1);
        ItemStack stock = historical(current, 42);
        byte[] savedStock = stock.serializeAsBytes();
        var result = DoctorQuickShopReadOnly.compareCached(cachedYaml(current), oldView, stock, current);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.FUNCTIONAL_METADATA,
                result.cachedDifference());
        assertFalse(result.cachedMatchesView());
        assertFalse(result.cachedMatchesStock());
        assertArrayEquals(savedStock, stock.serializeAsBytes());
        assertEquals(42, stock.getAmount());
    }

    @Test
    void cachedSerializationPreservesTypedFunctionalOwnerDifference() throws Exception {
        ItemStack current = current("CARBONADO");
        ItemStack changed = historical(current, 9);
        changed.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "stored_owner"), PersistentDataType.INTEGER, 75123));
        byte[] saved = changed.serializeAsBytes();
        var result = DoctorQuickShopReadOnly.compareCached(cachedYaml(changed), changed, null, current);
        assertEquals(DoctorHistoricalStackEvidenceAudit.Difference.FUNCTIONAL_METADATA,
                result.cachedDifference());
        assertTrue(result.cachedMatchesView());
        assertFalse(result.cachedMatchesStock());
        assertArrayEquals(saved, changed.serializeAsBytes());
    }

    @Test
    void malformedOrWrongIdentityCacheCannotAuthorizePrivateShopEvidence() {
        ItemStack current = current("CARBONADO");
        ItemStack wrongId = current("CARBON_CHUNK");
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compareCached(
                cachedYaml(wrongId), current, current, current));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compareCached(
                "item: [this is not an ItemStack", current, current, current));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compareCached(
                "item: plain text", current, current, current));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compareCached(
                cachedYaml(current) + "another: private\\n", current, current, current));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compareCached(
                " ", current, current, current));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compareCached(
                "x".repeat(1024 * 1024 + 1), current, current, current));
    }

    @Test
    void prospectiveShopStorageIsOpaqueStableAndHasNoSideEffects() throws Exception {
        ItemStack actual = historical(current("CARBONADO"), 63);
        byte[] before = actual.serializeAsBytes();
        String encoded = "opaque-encrypted-or-encoded-item-with-private-pdc";
        var first = DoctorQuickShopReadOnly.checkedProspective(
                encoded, "private-owner", 27.25, "private-owner", 27.25);
        var second = DoctorQuickShopReadOnly.checkedProspective(
                encoded, "private-owner", 27.25, "private-owner", 27.25);
        assertEquals(first, second);
        assertEquals(encoded, first.encodedItem());
        assertEquals("private-owner", first.owner());
        assertFalse(first.equals(DoctorQuickShopReadOnly.checkedProspective(
                encoded + "-changed", "private-owner", 27.25, "private-owner", 27.25)));
        assertArrayEquals(before, actual.serializeAsBytes());
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    @Test
    void prospectiveShopStorageRefusesMissingChangedOrMispricedData() throws Exception {
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                null, "owner", 2, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "", "owner", 2, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "x".repeat(1024 * 1024 + 1), "owner", 2, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "secret", null, 2, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "secret", "other-owner", 2, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "secret", "owner", 2.01, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "secret", "owner", Double.NaN, "owner", 2));
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.checkedProspective(
                "secret", "owner", Double.POSITIVE_INFINITY, "owner", 2));
    }

    @Test
    void exportsRealSaleAndStockEvidenceWithoutChangingSourceOrTypedData() throws Exception {
        ItemStack fresh = current("CARBONADO");
        ItemStack sale = historical(fresh, 1);
        ItemStack stock = historical(fresh, 63);
        byte[] freshBytes = fresh.serializeAsBytes();
        byte[] saleBytes = sale.serializeAsBytes();
        byte[] stockBytes = stock.serializeAsBytes();
        UUID operator = UUID.randomUUID();
        var receipts = DoctorQuickShopReadOnly.captureEvidence(data, operator, sale, stock, fresh);
        assertEquals(4L, countPrivateReceipts());
        assertArrayEquals(saleBytes, saved(operator, receipts.sale()));
        assertArrayEquals(freshBytes, saved(operator, receipts.current()));
        assertArrayEquals(stockBytes, saved(operator, receipts.stock()));
        assertArrayEquals(freshBytes, saved(operator, receipts.stockReference()));
        assertArrayEquals(saleBytes, sale.serializeAsBytes());
        assertArrayEquals(stockBytes, stock.serializeAsBytes());
        assertArrayEquals(freshBytes, fresh.serializeAsBytes());
        assertEquals(63, stock.getAmount());
        assertEquals(75123L, stock.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey("addon", "stored_owner"), PersistentDataType.LONG));
    }

    @Test
    void unlimitedShopWithoutPhysicalStockExportsSaleAndCurrentOnly() throws Exception {
        ItemStack fresh = current("CARBONADO");
        ItemStack sale = historical(fresh, 1);
        byte[] saleBytes = sale.serializeAsBytes();
        UUID operator = UUID.randomUUID();
        var receipts = DoctorQuickShopReadOnly.captureEvidence(data, operator, sale, null, fresh);
        assertEquals(2L, countPrivateReceipts());
        assertArrayEquals(saleBytes, saved(operator, receipts.sale()));
        assertArrayEquals(fresh.serializeAsBytes(), saved(operator, receipts.current()));
        assertNull(receipts.stock());
        assertNull(receipts.stockReference());
        assertArrayEquals(saleBytes, sale.serializeAsBytes());
    }

    @Test
    void differentSlimefunIdCannotCreateShopEvidenceReceipts() throws Exception {
        ItemStack fresh = current("CARBONADO");
        ItemStack different = current("CARBON_CHUNK");
        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.captureEvidence(
                data, UUID.randomUUID(), fresh, different, fresh));
        assertFalse(Files.exists(data.resolve("recovery")));
    }

    private long countPrivateReceipts() throws IOException {
        try (var stream = Files.list(data.resolve("recovery/identity"))) {
            return stream.count();
        }
    }

    private byte[] saved(UUID owner, UUID receipt) throws IOException {
        return DoctorHistoricalStackRepair.readVerifiedBackup(data, owner, receipt).serializeAsBytes();
    }

    @Test
    void refusesMatcherThatChangesOnlyItsDetachedTestCandidate() {
        ItemStack template = current("CARBONADO");
        ItemStack old = historical(template, 34);
        byte[] beforeOld = old.serializeAsBytes();
        byte[] beforeTemplate = template.serializeAsBytes();
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IOException.class, () -> DoctorQuickShopReadOnly.compare(
                old, old, template, candidate -> {
                    attempts.incrementAndGet();
                    candidate.setAmount(candidate.getAmount() + 1);
                    return true;
                }));

        assertEquals(1, attempts.get());
        assertArrayEquals(beforeOld, old.serializeAsBytes());
        assertArrayEquals(beforeTemplate, template.serializeAsBytes());
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
