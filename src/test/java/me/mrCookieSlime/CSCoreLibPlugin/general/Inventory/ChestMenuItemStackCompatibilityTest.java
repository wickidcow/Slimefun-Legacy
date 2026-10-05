package me.mrCookieSlime.CSCoreLibPlugin.general.Inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ChestMenuItemStackCompatibilityTest {

    private ServerMock server;
    private InventoryReadTestPlugin fixture;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        fixture = new InventoryReadTestPlugin(server);
    }

    @AfterEach
    void tearDown() {
        if (fixture != null) {
            fixture.close();
        }
        MockBukkit.unmock();
    }

    @Test
    void replaceExistingItemConvertsSlimefunItemStackBeforeInventoryWrite() {
        SlimefunItemStack source =
                new SlimefunItemStack("TEST_MENU_REPLACEMENT", Material.DIAMOND, "&aReplacement", "&7Compatibility");
        source.setAmount(7);

        ChestMenu menu = new ChestMenu("&8Compatibility Test", 9);
        menu.replaceExistingItem(0, source);

        ItemStack stored = menu.getItemInSlot(0);
        assertSafeCopy(source, stored);
    }

    @Test
    void addItemUsesTheSameSafeConversion() {
        SlimefunItemStack source =
                new SlimefunItemStack("TEST_MENU_ADD", Material.EMERALD, "&aAdd", "&7Compatibility");
        source.setAmount(3);

        ChestMenu menu = new ChestMenu("&8Compatibility Test", 9);
        menu.addItem(0, source);

        ItemStack stored = menu.getItemInSlot(0);
        assertSafeCopy(source, stored);
    }

    private static void assertSafeCopy(SlimefunItemStack source, ItemStack stored) {
        assertNotNull(stored);
        assertFalse(
                stored instanceof SlimefunItemStack,
                "Bukkit inventories must not receive SlimefunItemStack subclasses on Paper/Purpur 1.21+");
        assertEquals(source.getType(), stored.getType());
        assertEquals(source.getAmount(), stored.getAmount());
        assertEquals(source.getItemMeta(), stored.getItemMeta());
    }
}
