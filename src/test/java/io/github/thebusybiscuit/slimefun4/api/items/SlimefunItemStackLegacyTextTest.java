package io.github.thebusybiscuit.slimefun4.api.items;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class SlimefunItemStackLegacyTextTest {

    // Fixed component trees from the v4.1.81 constructor at d728bf04. These
    // protect newly constructed item identity during later API/serializer
    // changes; they do not certify old-world items. A deliberate change to
    // this baseline needs the separate historical/native compatibility gates.
    @Test
    void preservesReleasedRootDecorationsAndLiteralBlankLore() {
        assertEquals(
                Component.text("Carbonado", NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false),
                SlimefunItemStack.legacyText("&b&lCarbonado"));
        assertEquals(
                Component.text("\"Black Diamond\"", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                SlimefunItemStack.legacyText("&7&o\"Black Diamond\""));
        assertEquals(
                Component.empty().decoration(TextDecoration.ITALIC, false), SlimefunItemStack.legacyText(""));
        assertEquals(
                Component.text(" ").decoration(TextDecoration.ITALIC, false), SlimefunItemStack.legacyText(" "));
        assertEquals(
                Component.text("Literal &z code &").decoration(TextDecoration.ITALIC, false),
                SlimefunItemStack.legacyText("Literal &z code &"));
    }

    @Test
    void preservesReleasedMultiColorNameTree() {
        Component expected = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Gold Ingot ", NamedTextColor.WHITE))
                .append(Component.text("(18-Carat)", NamedTextColor.GRAY));
        assertEquals(expected, SlimefunItemStack.legacyText("&fGold Ingot &7(18-Carat)"));
        assertEquals(expected, SlimefunItemStack.legacyText("§fGold Ingot &7(18-Carat)"));
    }

    @Test
    void preservesReleasedNestedDecorationAndResetTree() {
        Component expected = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Green ", NamedTextColor.GREEN)
                        .append(Component.text("italic").decoration(TextDecoration.ITALIC, true)))
                .append(Component.text(" plain"));
        assertEquals(expected, SlimefunItemStack.legacyText("&aGreen &oitalic&r plain"));
    }

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
