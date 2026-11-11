package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

class TestDoctorIdentityCommand {

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private ItemStack uranium() {
        ItemStack item = new ItemStack(Material.PAPER, 1);
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
    void diagnosticDoesNotInvokeSubclassCloneHooks() {
        ItemStack reference = uranium();
        ItemStack registered = new ItemStack(reference) {
            @Override
            public ItemStack clone() {
                throw new AssertionError("Diagnostic must not invoke producer clone hooks");
            }
        };
        registered.setAmount(19);

        assertTrue(DoctorIdentityCommand.inspect(reference, registered).isSimilar());
        registered.editMeta(meta -> meta.displayName(Component.text("Different registered name")));
        var mismatch = DoctorIdentityCommand.inspect(reference, registered);
        assertFalse(mismatch.isSimilar());
        assertFalse(mismatch.displayNameSame());
        assertEquals(19, registered.getAmount());
    }

    @Test
    void identicalItemIdentityIgnoresQuantityAndNeverMutatesInputs() {
        ItemStack cheat = uranium();
        ItemStack produced = cheat.clone();
        produced.setAmount(25);
        ItemStack originalCheat = cheat.clone();
        ItemStack originalProduced = produced.clone();

        var result = DoctorIdentityCommand.inspect(cheat, produced);

        assertTrue(result.materialSame());
        assertTrue(result.isSimilar());
        assertTrue(result.displayNameSame());
        assertTrue(result.loreSame());
        assertTrue(result.itemMetaSame());
        assertEquals(originalCheat, cheat);
        assertEquals(originalProduced, produced);
    }

    @Test
    void sameVisibleNameWithDifferentBoldDecorationDoesNotStack() {
        ItemStack cheat = uranium();
        ItemStack produced = uranium();
        produced.editMeta(meta -> meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)));
        ItemStack before = produced.clone();

        var result = DoctorIdentityCommand.inspect(cheat, produced);

        assertTrue(result.materialSame());
        assertFalse(result.isSimilar());
        assertFalse(result.displayNameSame());
        assertTrue(result.loreSame());
        assertFalse(result.itemMetaSame());
        assertEquals(before, produced);
    }

    @Test
    void typedPdcDifferenceIsNotHiddenByMatchingNameAndLore() {
        ItemStack cheat = uranium();
        ItemStack produced = uranium();
        produced.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "generation"), PersistentDataType.LONG, 77L));

        var result = DoctorIdentityCommand.inspect(cheat, produced);

        assertFalse(result.isSimilar());
        assertTrue(result.displayNameSame());
        assertTrue(result.loreSame());
        assertFalse(result.itemMetaSame());
        assertFalse(result.pdcKeysSame());
        assertTrue(result.customModelDataSame());
    }

    @Test
    void differentPdcValueWithSameKeysIsNotMistakenForKeySetDifference() {
        ItemStack cheat = uranium();
        ItemStack produced = uranium();
        produced.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "OTHER_ID"));

        var result = DoctorIdentityCommand.inspect(cheat, produced);

        assertFalse(result.isSimilar());
        assertTrue(result.pdcKeysSame());
        assertFalse(result.itemMetaSame());
    }

    @Test
    void matchingHeldItemsCanBothDifferFromCurrentTemplateWithoutMutatingIt() {
        ItemStack template = uranium();
        ItemStack main = template.clone();
        main.editMeta(meta -> meta.displayName(Component.text("Historical uranium")));
        ItemStack off = main.clone();
        off.setAmount(32);
        ItemStack beforeTemplate = template.clone();
        ItemStack beforeMain = main.clone();
        ItemStack beforeOff = off.clone();

        assertTrue(DoctorIdentityCommand.inspect(main, off).isSimilar());
        assertFalse(DoctorIdentityCommand.inspect(main, template).isSimilar());
        assertFalse(DoctorIdentityCommand.inspect(off, template).displayNameSame());
        assertEquals(beforeTemplate, template);
        assertEquals(beforeMain, main);
        assertEquals(beforeOff, off);
    }

    @Test
    void samePdcKeyAndNumericValueWithDifferentPrimitiveTypesRemainDifferent() {
        ItemStack reference = uranium();
        ItemStack produced = uranium();
        NamespacedKey key = new NamespacedKey("addon", "generation");
        reference.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 77));
        produced.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, 77L));

        var result = DoctorIdentityCommand.inspect(reference, produced);

        assertTrue(result.pdcKeysSame());
        assertFalse(result.itemMetaSame());
        assertFalse(result.isSimilar());
    }

    @Test
    void customModelDataDifferenceIsReportedWithoutChangingInputs() {
        // MockBukkit 4.110.0 does not preserve modern model components through all
        // ItemStack copies or include them in ItemMetaMock.equals. Test extraction
        // directly; native Paper must verify clone preservation and stacking.
        var reference = uranium().getItemMeta();
        var produced = uranium().getItemMeta();
        var referenceModel = reference.getCustomModelDataComponent();
        referenceModel.setFloats(java.util.List.of(2200094.0F));
        reference.setCustomModelDataComponent(referenceModel);
        var producedModel = produced.getCustomModelDataComponent();
        producedModel.setFloats(java.util.List.of(2200095.0F));
        produced.setCustomModelDataComponent(producedModel);

        assertNotEquals(DoctorIdentityCommand.modelData(reference), DoctorIdentityCommand.modelData(produced));
        assertEquals(
                java.util.List.of(2200094.0F),
                reference.getCustomModelDataComponent().getFloats());
        assertEquals(
                java.util.List.of(2200095.0F),
                produced.getCustomModelDataComponent().getFloats());
    }
}
