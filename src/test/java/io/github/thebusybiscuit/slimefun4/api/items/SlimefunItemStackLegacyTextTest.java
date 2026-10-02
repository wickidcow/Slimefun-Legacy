package io.github.thebusybiscuit.slimefun4.api.items;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class SlimefunItemStackLegacyTextTest {

    @Test
    void preservesSectionSignRgbFormatting() {
        Component component = SlimefunItemStack.legacyText("§x§6§B§E§E§D§1D");

        assertEquals("D", ((TextComponent) component).content());
        assertEquals(TextColor.color(0x6BEED1), component.style().color());
    }

    @Test
    void stillTranslatesAmpersandFormatting() {
        Component component = SlimefunItemStack.legacyText("&aGreen");

        assertEquals("Green", ((TextComponent) component).content());
        assertEquals(NamedTextColor.GREEN, component.style().color());
    }
}
