package io.github.thebusybiscuit.slimefun4.implementation.guide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestGuideItemGroupVisibility {

    private ServerMock server;
    private Player player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void visibleCategoryWithDefaultHidingHookAppears() {
        assertTrue(SurvivalSlimefunGuide.isVisibleInMainMenu(new VisibleGroup(), player));
    }

    @Test
    void legacyHiddenChildStaysAccessibleButLeavesTheMainMenu() {
        // Networks DummyItemGroup and Gastronomicon's InfinityLib SubGroup use this override.
        ItemGroup child = new VisibleGroup() {
            @Override
            public boolean isHidden(Player viewer) {
                return true;
            }
        };

        assertTrue(child.isVisible(player));
        assertTrue(child.isAccessible(player));
        assertFalse(SurvivalSlimefunGuide.isVisibleInMainMenu(child, player));
        assertTrue(child.isAccessible(player));
    }

    @Test
    void legacyVisibleOverrideCannotExposeAnInvisibleCategory() {
        ItemGroup group = new VisibleGroup() {
            @Override
            public boolean isVisible(Player viewer) {
                return false;
            }

            @Override
            public boolean isHidden(Player viewer) {
                return false;
            }
        };

        assertFalse(SurvivalSlimefunGuide.isVisibleInMainMenu(group, player));
    }

    @Test
    void emptyCategoryRemainsHidden() {
        ItemGroup empty = new ItemGroup(new NamespacedKey("guide_test", "empty"), new ItemStack(Material.CHEST));

        assertFalse(SurvivalSlimefunGuide.isVisibleInMainMenu(empty, player));
    }

    @Test
    void addonHidingIsEvaluatedForTheCurrentPlayer() {
        Player otherPlayer = server.addPlayer();
        ItemGroup group = new VisibleGroup() {
            @Override
            public boolean isHidden(Player viewer) {
                return viewer == player;
            }
        };

        assertFalse(SurvivalSlimefunGuide.isVisibleInMainMenu(group, player));
        assertTrue(SurvivalSlimefunGuide.isVisibleInMainMenu(group, otherPlayer));
    }

    private static class VisibleGroup extends ItemGroup {

        VisibleGroup() {
            super(new NamespacedKey("guide_test", "visible"), new ItemStack(Material.CHEST));
        }

        @Override
        public boolean isVisible(Player viewer) {
            return true;
        }
    }
}
