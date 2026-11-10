package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

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
        produced.editMeta(meta -> meta.displayName(
                Component.text("Small Uranium", NamedTextColor.RED)
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
    }
}
