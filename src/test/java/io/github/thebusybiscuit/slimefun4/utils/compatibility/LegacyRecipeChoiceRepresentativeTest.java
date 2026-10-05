package io.github.thebusybiscuit.slimefun4.utils.compatibility;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class LegacyRecipeChoiceRepresentativeTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("deprecation") // Compare against the historical API on the 1.21.11 test floor.
    void exactChoiceRetainsFirstStackAmountMetadataAndCloneIsolation() {
        var first = new ItemStack(Material.DIAMOND, 7);
        var meta = first.getItemMeta();
        meta.displayName(Component.text("Old addon recipe"));
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:identity"),
                PersistentDataType.STRING, "keep-me");
        first.setItemMeta(meta);
        var choice = new RecipeChoice.ExactChoice(List.of(first, new ItemStack(Material.EMERALD)));
        var expected = choice.getItemStack();

        var actual = LegacyBukkitCompatibility.getRecipeChoiceRepresentative(choice);

        assertEquals(expected, actual);
        assertNotSame(first, actual);
        actual.setAmount(1);
        actual.editMeta(changed -> changed.displayName(Component.text("Changed preview")));
        assertEquals(7, first.getAmount());
        assertEquals(meta, first.getItemMeta());
        assertEquals(expected, LegacyBukkitCompatibility.getRecipeChoiceRepresentative(choice));
    }

    @Test
    @SuppressWarnings("deprecation") // MaterialChoice's multi-choice wildcard remains compatibility behavior.
    void materialChoicesRetainSingleAndWildcardRepresentatives() {
        for (var choice : List.of(new RecipeChoice.MaterialChoice(Material.STONE),
                new RecipeChoice.MaterialChoice(Material.STONE, Material.COAL))) {
            assertEquals(choice.getItemStack(), LegacyBukkitCompatibility.getRecipeChoiceRepresentative(choice));
        }
    }

    @Test
    void unknownChoiceRetainsItsOwnRepresentativeWithoutEvaluatingItsPredicate() {
        var expected = new ItemStack(Material.COAL, 3);
        RecipeChoice choice = new RecipeChoice() {
            @Override
            public ItemStack getItemStack() {
                return expected.clone();
            }

            @Override
            public RecipeChoice clone() {
                return this;
            }

            @Override
            public boolean test(ItemStack item) {
                fail("Reading a preview must not run its ingredient predicate");
                return false;
            }
        };

        assertEquals(expected, LegacyBukkitCompatibility.getRecipeChoiceRepresentative(choice));
    }
}
