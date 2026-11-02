package io.github.thebusybiscuit.slimefun4.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** Preserve legacy addon color linkage alongside the modern string overload. */
class ChatColorCompatibilityTest {

    @Test
    void stripsBothAmpersandAndSectionColors() {
        assertEquals("Green Red", ChatUtils.removeColorCodes("&aGreen §cRed"));
    }

    @Test
    void stripsExpandedRgbHexWithoutDroppingVisibleText() {
        assertEquals("Blue", ChatUtils.removeColorCodes("§x§6§B§E§E§D§1Blue"));
        assertEquals("Color", ChatUtils.removeColorCodes("§l§nColor§r"));
    }

    @Test
    void modernStringOverloadKeepsTextAndNewColor() {
        assertEquals("§aPig", ChatUtils.crop("&a", "&cPig"));
    }

    @SuppressWarnings("deprecation") // ABI bridge required by existing addons.
    @Test
    void legacyBukkitColorOverloadRemainsCallable() {
        assertEquals("§9Pig", ChatUtils.crop(ChatColor.BLUE, "§cPig"));
    }
}
