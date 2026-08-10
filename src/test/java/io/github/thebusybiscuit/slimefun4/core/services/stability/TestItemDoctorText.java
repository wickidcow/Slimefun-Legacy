package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestItemDoctorText {

    @Test
    void detectsCommonAndSupplementaryCjkCharacters() {
        Assertions.assertTrue(ItemDoctorText.containsCjk("Machine \u4E2D\u6587"));
        Assertions.assertTrue(ItemDoctorText.containsCjk("Item \uD840\uDC00"));
        Assertions.assertFalse(ItemDoctorText.containsCjk("English item name"));
        Assertions.assertFalse(ItemDoctorText.containsCjk((String) null));
    }

    @Test
    void rebuildsBaseLoreAndPreservesEnglishStateLines() {
        List<String> current =
                List.of("\u00A77\u4E2D\u6587\u63CF\u8FF0", "\u00A77Charge: 64 J", "\u00A7bCustom marker");
        List<String> canonical = List.of("\u00A77English description", "\u00A77Charge: 0 J");

        List<String> repaired = ItemDoctorText.mergeEnglishLore(current, canonical);

        Assertions.assertEquals(
                List.of("\u00A77English description", "\u00A77Charge: 64 J", "\u00A7bCustom marker"), repaired);
        Assertions.assertFalse(ItemDoctorText.containsCjk(repaired));
    }

    @Test
    void preservesHiddenStateAtItsOriginalIndex() {
        String hiddenUuid = "\u00A70cc5e8e27-7e4e-45cd-9396-62b41ecfd717";
        List<String> repaired = ItemDoctorText.mergeEnglishLore(
                List.of("\u00A77\u6240\u6709\u8005", hiddenUuid, "\u00A77\u4f7f\u7528\u8bf4\u660e"),
                List.of("\u00A77Owner: None", "", "\u00A77Usage instructions"));

        Assertions.assertEquals(List.of("\u00A77Owner: None", hiddenUuid, "\u00A77Usage instructions"), repaired);
    }

    @Test
    void carriesDynamicNumbersFromTranslatedLines() {
        List<String> repaired = ItemDoctorText.mergeEnglishLore(
                List.of("§7\u5269\u4F59\u4F7F\u7528\u6B21\u6570: 7", "§7\u5F53\u524D\u7535\u91CF: 64 / 128 J"),
                List.of("§7Uses left: 20", "§7Charge: 0 / 128 J"));

        Assertions.assertEquals(List.of("§7Uses left: 7", "§7Charge: 64 / 128 J"), repaired);
    }

    @Test
    void doesNotTreatLegacyColorCodesAsDynamicNumbers() {
        Assertions.assertEquals(
                "§7Uses left: 4",
                ItemDoctorText.carryDynamicTokens("§e\u5269\u4F59\u4F7F\u7528\u6B21\u6570: 4", "§7Uses left: 20"));
    }

    @Test
    void doesNotDuplicateEquivalentCanonicalLines() {
        List<String> repaired = ItemDoctorText.mergeEnglishLore(
                List.of("\u00A7aEnglish description", "\u00A77Extra state"), List.of("\u00A77English description"));

        Assertions.assertEquals(List.of("\u00A7aEnglish description", "\u00A77Extra state"), repaired);
    }

    @Test
    void removesCjkLoreWithoutMovingHiddenState() {
        List<String> repaired = ItemDoctorText.mergeEnglishLore(
                List.of("§7\u4E2D\u6587\u63CF\u8FF0", "§7Charge: 19 J", "§0hidden-state"), null);

        Assertions.assertEquals(List.of("", "§7Charge: 19 J", "§0hidden-state"), repaired);
        Assertions.assertEquals(List.of(), ItemDoctorText.mergeEnglishLore(List.of("§7\u4E2D\u6587"), null));
    }

    @Test
    void doesNotAppendDuplicateCanonicalUuidLines() {
        String owner = "cc5e8e27-7e4e-45cd-9396-62b41ecfd717";
        List<String> repaired = ItemDoctorText.mergeEnglishLore(
                List.of("§7\u6240\u6709\u8005 UUID: " + owner),
                List.of("§7Owner UUID: 00000000-0000-0000-0000-000000000000"));

        Assertions.assertEquals(List.of("§7Owner UUID: " + owner), repaired);
    }

    @Test
    void carriesUuidAndSignedDynamicValues() {
        String owner = "cc5e8e27-7e4e-45cd-9396-62b41ecfd717";
        Assertions.assertEquals(
                "§7Owner UUID: " + owner,
                ItemDoctorText.carryDynamicTokens(
                        "§7\u6240\u6709\u8005: " + owner, "§7Owner UUID: 00000000-0000-0000-0000-000000000000"));
        Assertions.assertEquals(
                "§7Temperature: -12.5 C",
                ItemDoctorText.carryDynamicTokens("§7\u6E29\u5EA6: -12.5 C", "§7Temperature: 0.0 C"));
    }

    @Test
    void recoversLegacyChargeAndSingleUseValues() {
        Assertions.assertEquals(
                64.5F, ItemDoctorText.findLegacyCharge(List.of("§7\u5F53\u524D\u7535\u91CF: 64.5 / 128 J")));
        Assertions.assertEquals(
                7, ItemDoctorText.findLegacyUsesLeft(List.of("§7\u5269\u4F59\u4F7F\u7528\u6B21\u6570: 7")));
        Assertions.assertEquals(7, ItemDoctorText.findLegacyUsesLeft(List.of("§7Uses left: §e7")));
        Assertions.assertEquals(64.5F, ItemDoctorText.findLegacyCharge(List.of("§8⇨ §e⚡ §764.5 / 128 J")));
        Assertions.assertEquals(
                7,
                ItemDoctorText.findLegacyUsesLeft(
                        List.of("§7\u7B49\u7EA7: 2", "§7\u5269\u4F59\u4F7F\u7528\u6B21\u6570: 7")));
        Assertions.assertNull(ItemDoctorText.findLegacyUsesLeft(List.of("§7\u7B49\u7EA7: 2")));
        Assertions.assertNull(ItemDoctorText.findLegacyUsesLeft(List.of("§7Uses left: 7", "§7Remaining uses: 6")));
    }

    @Test
    void canonicalizesStaticNumericLoreInsteadOfTreatingNumbersAsSavedState() {
        List<String> repaired = ItemDoctorText.mergeStaticEnglishLore(
                List.of(
                        "§7\u653B\u51FB\u65F6\u6709 45% \u7684\u51E0\u7387",
                        "§7\u6062\u590D 2 \u70B9\u751F\u547D\u503C", "§0hidden-state"),
                List.of("§7Has a 45% chance when attacking", "§7to restore 2 Hearts"));

        Assertions.assertEquals(
                List.of("§7Has a 45% chance when attacking", "§7to restore 2 Hearts", "§0hidden-state"), repaired);
        Assertions.assertFalse(ItemDoctorText.containsCjk(repaired));
    }

    @Test
    void conservativelyRepairsAddonLoreButLeavesAmbiguousStateLinesUntouched() {
        List<String> repaired = ItemDoctorText.mergeConservativeEnglishLore(
                List.of(
                        "§7\u8FD9\u662F\u673A\u5668\u8BF4\u660E",
                        "§7\u7B49\u7EA7: 2 / 4",
                        "§7\u5F53\u524D\u7535\u91CF: 64 / 128 J",
                        "§0addon-state"),
                List.of("§7Machine description", "§7Level: 0", "§7Charge: 0 / 128 J"),
                ignored -> false);

        Assertions.assertEquals("§7Machine description", repaired.get(0));
        Assertions.assertEquals("§7\u7B49\u7EA7: 2 / 4", repaired.get(1));
        Assertions.assertEquals("§7Charge: 64 / 128 J", repaired.get(2));
        Assertions.assertEquals("§0addon-state", repaired.get(3));
        Assertions.assertTrue(ItemDoctorText.containsCjk(repaired));
    }

    @Test
    void authoritativeStateLineMayBeReplacedEvenWhenItsTokenShapeDiffers() {
        List<String> repaired = ItemDoctorText.mergeConservativeEnglishLore(
                List.of("§7\u80CC\u5305\u7F16\u53F7: cc5e8e27-7e4e-45cd-9396-62b41ecfd717#4"),
                List.of("§7Backpack storage"),
                line -> line.contains("cc5e8e27-7e4e-45cd-9396-62b41ecfd717#4"));

        Assertions.assertEquals(List.of("§7Backpack storage"), repaired);
    }

    @Test
    void rejectsAmbiguousDynamicStateMappings() {
        Assertions.assertFalse(
                ItemDoctorText.canSafelyMergeDynamicTokens(List.of("§7\u7B49\u7EA7: 2 / 4"), List.of("§7Level: 0")));
        Assertions.assertFalse(ItemDoctorText.canSafelyMergeDynamicTokens(List.of("§7剩余次数: 7"), null));
        Assertions.assertTrue(
                ItemDoctorText.canSafelyMergeDynamicTokens(List.of("§7剩余次数: 7"), List.of("§7Uses left: 20")));
        Assertions.assertTrue(
                ItemDoctorText.canSafelyMergeDynamicTokens(List.of("§7纯文本说明"), List.of("§7Plain description")));
    }

    @Test
    void permitsExplicitlyRestoredLegacyStateLines() {
        String identity = "cc5e8e27-7e4e-45cd-9396-62b41ecfd717#4";
        Assertions.assertTrue(ItemDoctorText.canSafelyMergeDynamicTokens(
                List.of("§7背包编号: " + identity), List.of("§7Owner: None"), line -> line.contains(identity)));
        Assertions.assertFalse(ItemDoctorText.canSafelyMergeDynamicTokens(
                List.of("§7背包编号: " + identity), List.of("§7Owner: None"), line -> false));
    }

    @Test
    void testHumanizeOrphanedItemIds() {
        Assertions.assertEquals("Ender Talisman", ItemDoctorText.humanizeItemId("ENDER_TALISMAN"));
        Assertions.assertEquals("Potion of Healing 2", ItemDoctorText.humanizeItemId("legacy:POTION_OF_HEALING_2"));
        Assertions.assertEquals("DNA Extractor", ItemDoctorText.humanizeItemId("DNA_EXTRACTOR"));
    }

    @Test
    void testPreserveLeadingFormattingForOrphanedName() {
        Assertions.assertEquals(
                "§b§lEnder Talisman",
                ItemDoctorText.preserveLeadingFormatting("§b§l\u672B\u5F71\u62A4\u7B26", "Ender Talisman"));
        Assertions.assertEquals(
                "Potion of Healing",
                ItemDoctorText.preserveLeadingFormatting("\u6CBB\u7597\u836F\u6C34", "Potion of Healing"));
    }
}
