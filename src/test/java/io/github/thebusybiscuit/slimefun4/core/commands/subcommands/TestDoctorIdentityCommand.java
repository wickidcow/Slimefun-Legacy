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

    @Test
    void historicalBoldMismatchIsReadOnlyPresentationCandidate() {
        ItemStack historical = uranium();
        ItemStack fresh = uranium();
        historical.editMeta(meta -> meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)));
        historical.setAmount(37);
        ItemStack beforeOld = new ItemStack(historical);
        ItemStack beforeFresh = new ItemStack(fresh);

        var result = DoctorIdentityCommand.inspectHistoricalPresentation(historical, fresh);

        assertTrue(result.plainTextSame());
        assertTrue(result.nonPresentationSame());
        assertTrue(result.candidate());
        assertFalse(DoctorIdentityCommand.inspect(historical, fresh).isSimilar());
        assertEquals(beforeOld, historical);
        assertEquals(beforeFresh, fresh);
        assertEquals(37, historical.getAmount());
    }

    @Test
    void typedPersistentDataDifferenceCannotBeClassifiedAsCosmetic() {
        ItemStack historical = uranium();
        ItemStack fresh = uranium();
        historical.editMeta(meta -> meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)));
        historical.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.INTEGER, 4));
        fresh.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.LONG, 4L));
        ItemStack beforeOld = new ItemStack(historical);
        ItemStack beforeFresh = new ItemStack(fresh);

        var result = DoctorIdentityCommand.inspectHistoricalPresentation(historical, fresh);

        assertTrue(result.plainTextSame());
        assertFalse(result.nonPresentationSame());
        assertFalse(result.candidate());
        assertEquals(beforeOld, historical);
        assertEquals(beforeFresh, fresh);
    }

    @Test
    void genuinelyDifferentTextIsNotPresentationOnlyCandidate() {
        ItemStack historical = uranium();
        ItemStack fresh = uranium();
        historical.editMeta(meta -> meta.displayName(Component.text("Unknown historical resource")));
        var result = DoctorIdentityCommand.inspectHistoricalPresentation(historical, fresh);
        assertFalse(result.plainTextSame());
        assertTrue(result.nonPresentationSame());
        assertFalse(result.candidate());
    }

    @Test
    void fullLegacyStackIsCapacityBlockNotIdentityFailure() {
        ItemStack historical = uranium();
        ItemStack fresh = uranium();
        historical.setAmount(64);
        assertTrue(DoctorIdentityCommand.inspect(historical, fresh).isSimilar());
        assertEquals(0, DoctorIdentityCommand.availableCapacity(historical));
        historical.setAmount(63);
        assertEquals(1, DoctorIdentityCommand.availableCapacity(historical));
        assertTrue(DoctorIdentityCommand.inspect(historical, fresh).isSimilar());
    }

    @Test
    void historicalPreviewNeverCallsProducerSpecificCloneOverride() {
        ItemStack original = uranium();
        ItemStack old = new ItemStack(original) {
            @Override
            public ItemStack clone() {
                throw new AssertionError("Read-only historical triage must avoid producer clone hooks");
            }
        };
        old.editMeta(meta -> meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)));
        var result = DoctorIdentityCommand.inspectHistoricalPresentation(old, original);
        assertTrue(result.candidate());
        assertFalse(DoctorIdentityCommand.inspect(old, original).isSimilar());
    }
}
