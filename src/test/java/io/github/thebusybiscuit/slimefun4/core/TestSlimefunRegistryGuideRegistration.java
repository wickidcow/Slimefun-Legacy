package io.github.thebusybiscuit.slimefun4.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class TestSlimefunRegistryGuideRegistration {

    @Test
    void registersAndReturnsPreviousGuide() {
        SlimefunRegistry registry = new SlimefunRegistry();
        SlimefunGuideImplementation first = guide(SlimefunGuideMode.SURVIVAL_MODE);
        SlimefunGuideImplementation second = guide(SlimefunGuideMode.SURVIVAL_MODE);

        assertSame(null, registry.registerSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE, first));
        assertSame(first, registry.registerSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE, second));
        assertSame(second, registry.getSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE));
    }

    @Test
    void compareAndSetOnlyRestoresExpectedInstance() {
        SlimefunRegistry registry = new SlimefunRegistry();
        SlimefunGuideImplementation original = guide(SlimefunGuideMode.SURVIVAL_MODE);
        SlimefunGuideImplementation installed = guide(SlimefunGuideMode.SURVIVAL_MODE);
        SlimefunGuideImplementation otherAddon = guide(SlimefunGuideMode.SURVIVAL_MODE);

        registry.registerSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE, original);
        registry.registerSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE, installed);

        assertTrue(registry.compareAndSetSlimefunGuide(
                SlimefunGuideMode.SURVIVAL_MODE, installed, original));
        assertSame(original, registry.getSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE));

        registry.registerSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE, otherAddon);
        assertFalse(registry.compareAndSetSlimefunGuide(
                SlimefunGuideMode.SURVIVAL_MODE, installed, original));
        assertSame(otherAddon, registry.getSlimefunGuide(SlimefunGuideMode.SURVIVAL_MODE));
    }

    @Test
    void rejectsGuideRegisteredUnderWrongMode() {
        SlimefunRegistry registry = new SlimefunRegistry();
        SlimefunGuideImplementation survival = guide(SlimefunGuideMode.SURVIVAL_MODE);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerSlimefunGuide(SlimefunGuideMode.CHEAT_MODE, survival));
    }

    private static SlimefunGuideImplementation guide(SlimefunGuideMode mode) {
        return new SlimefunGuideImplementation() {
            @Override
            public SlimefunGuideMode getMode() {
                return mode;
            }

            @Override
            public ItemStack getItem() {
                return new ItemStack(Material.BOOK);
            }

            @Override
            public void openMainMenu(PlayerProfile profile, int page) {
            }

            @Override
            public void openItemGroup(PlayerProfile profile, ItemGroup group, int page) {
            }

            @Override
            public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
            }

            @Override
            public void displayItem(PlayerProfile profile, ItemStack item, int index, boolean addToHistory) {
            }

            @Override
            public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory) {
            }
        };
    }
}
