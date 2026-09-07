package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestBackpackNameFormatting {

    @Test
    void preservesPlainBackpackNames() {
        assertEquals("Mining Backpack", BackpackListener.formatBackpackName("Mining Backpack"));
    }

    @Test
    void translatesLegacyColorCodes() {
        assertEquals("\u00A7aMining Backpack", BackpackListener.formatBackpackName("&aMining Backpack"));
    }

    @Test
    void translatesFormattingAndResetCodes() {
        assertEquals(
                "\u00A76\u00A7lLoot \u00A7rBackpack",
                BackpackListener.formatBackpackName("&6&lLoot &rBackpack"));
    }

    @Test
    void translatesHexColorShortcut() {
        assertEquals(
                "\u00A7x\u00A7f\u00A7f\u00A78\u00A78\u00A70\u00A70Orange Backpack",
                BackpackListener.formatBackpackName("&#FF8800Orange Backpack"));
    }

    @Test
    void leavesInvalidHexLiteral() {
        assertEquals("&#GG8800Backpack", BackpackListener.formatBackpackName("&#GG8800Backpack"));
    }
}
