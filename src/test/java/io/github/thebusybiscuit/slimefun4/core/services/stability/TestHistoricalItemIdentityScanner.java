package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestHistoricalItemIdentityScanner {

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private static ItemStack registered() {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(Component.text("Radioactive", NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "SMALL_URANIUM");
        });
        return item;
    }

    @Test
    void registeredMatchIgnoresStackAmountAndDoesNotChangeInputs() {
        ItemStack template = registered();
        ItemStack actual = new ItemStack(template);
        actual.setAmount(63);
        ItemStack before = new ItemStack(actual);
        assertEquals(HistoricalItemIdentityScanner.Result.MATCH, HistoricalItemIdentityScanner.inspect(actual, template));
        assertEquals(before, actual);
        assertEquals(63, actual.getAmount());
        assertEquals(1, template.getAmount());
    }

    @Test
    void nestedHistoricalBoldFlagIsOnlyAReadOnlyCandidate() {
        ItemStack template = registered();
        ItemStack old = new ItemStack(template);
        old.editMeta(meta -> meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)));
        ItemStack before = new ItemStack(old);
        assertFalse(old.isSimilar(template));
        assertEquals(
                HistoricalItemIdentityScanner.Result.PRESENTATION_ONLY_CANDIDATE,
                HistoricalItemIdentityScanner.inspect(old, template));
        assertEquals(before, old);
        assertFalse(old.isSimilar(template));
    }

    @Test
    void typedPersistentDataDifferenceCannotBeClassifiedAsPresentationOnly() {
        ItemStack template = registered();
        ItemStack old = new ItemStack(template);
        old.editMeta(meta -> {
            meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, false));
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("addon", "owner"), PersistentDataType.INTEGER, 77);
        });
        template.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.LONG, 77L));
        assertEquals(
                HistoricalItemIdentityScanner.Result.OTHER_DIFFERENCE,
                HistoricalItemIdentityScanner.inspect(old, template));
    }

    @Test
    void differentVisibleLoreIsNotAFormattingOnlyCandidate() {
        ItemStack template = registered();
        ItemStack old = new ItemStack(template);
        old.editMeta(meta -> meta.lore(java.util.List.of(Component.text("Different functional lore"))));
        assertEquals(
                HistoricalItemIdentityScanner.Result.OTHER_DIFFERENCE,
                HistoricalItemIdentityScanner.inspect(old, template));
    }

    @Test
    void absentTemplateIsExplicitlyUnavailable() {
        assertEquals(
                HistoricalItemIdentityScanner.Result.TEMPLATE_UNAVAILABLE,
                HistoricalItemIdentityScanner.inspect(registered(), null));
    }

    @Test
    void inspectionDoesNotInvokeCustomCloneHooks() {
        ItemStack template = registered();
        ItemStack old = new ItemStack(template) {
            @Override
            public ItemStack clone() {
                throw new AssertionError("Scan must not invoke producer clone hooks");
            }
        };
        old.editMeta(meta -> meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)));
        assertEquals(
                HistoricalItemIdentityScanner.Result.PRESENTATION_ONLY_CANDIDATE,
                HistoricalItemIdentityScanner.inspect(old, template));
        assertTrue(old.getAmount() > 0);
    }
}
