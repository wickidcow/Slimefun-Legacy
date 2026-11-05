package me.mrCookieSlime.Slimefun.api.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * An interactive machine's per-block click callback must not be replaced by
 * the decorative preset handler when its menu is cloned or rehydrated.
 */
class TestBlockMenuPresetHandlerOrder {
    private ServerMock server;
    private InventoryReadTestPlugin fixture;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        fixture = new InventoryReadTestPlugin(server);
    }

    @AfterEach
    void stopServer() {
        try {
            if (fixture != null) {
                fixture.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void liveMenuRetainsItsPerBlockCraftHandlerAcrossPresetReload() {
        var world = server.addSimpleWorld("handler-order-test");
        var preset = new HandlerPreset();
        var menu = new BlockMenu(preset, new Location(world, 10, 64, 10));

        assertSame(HandlerPreset.INSTANCE_HANDLER, menu.getMenuClickHandler(23));
        assertTrue(menu.isPlayerInventoryClickable(), "Fresh menus allow player-inventory interaction");
        menu.reload();
        assertSame(HandlerPreset.INSTANCE_HANDLER, menu.getMenuClickHandler(23),
                "A decorative preset button must not override its machine's real click action");
    }

    @Test
    void externallyHydratedMenuRetainsItemsAndHandlerAfterReload() {
        var world = server.addSimpleWorld("loaded-handler-order-test");
        var preset = new HandlerPreset();
        Inventory physical = Bukkit.createInventory(null, 54);
        var diamonds = new ItemStack(Material.DIAMOND, 37);
        physical.setItem(10, diamonds.clone());
        // A saved physical slot may contain historical data even where the
        // preset normally draws a decorative button. Reattaching callbacks
        // must not replace the saved item during hydration.
        var savedButtonSlot = new ItemStack(Material.EMERALD, 3);
        physical.setItem(23, savedButtonSlot.clone());
        var menu = new BlockMenu(preset, new Location(world, 20, 64, 10), physical);

        assertSame(physical, menu.toInventory(), "Hydrated menus own their original physical inventory");
        assertTrue(menu.isPlayerInventoryClickable(),
                "Rehydration must restore player-inventory access without replacing saved items");
        assertSame(HandlerPreset.INSTANCE_HANDLER, menu.getMenuClickHandler(23),
                "Restored menus must recover the real handler before any player opens the GUI");
        assertEquals(diamonds, physical.getItem(10),
                "Restoring actions must not overwrite a persisted ingredient");
        assertEquals(savedButtonSlot, physical.getItem(23),
                "Hydrating click handlers must not redraw a preset button over historical inventory data");
        menu.reload();
        assertSame(physical, menu.toInventory(), "Click handler restoration must not replace inventory backing");
        assertTrue(menu.isPlayerInventoryClickable(), "Preset reload must preserve player-inventory access");
        assertEquals(diamonds, physical.getItem(10),
                "Click handler restoration must not consume or recreate any saved inventory item");
        assertSame(HandlerPreset.INSTANCE_HANDLER, menu.getMenuClickHandler(23));
    }

    private static final class HandlerPreset extends BlockMenuPreset {
        // Preset.init runs during super() construction; these must be static.
        private static final MenuClickHandler PRESET_HANDLER = (player, slot, item, action) -> false;
        private static final MenuClickHandler INSTANCE_HANDLER = (player, slot, item, action) -> true;

        private HandlerPreset() {
            super("TEST_BLOCK_MENU_PRESET_HANDLER_ORDER", "Handler Order");
        }

        @Override
        public void init() {
            setSize(54);
            addItem(23, new ItemStack(Material.CRAFTING_TABLE));
            addMenuClickHandler(23, PRESET_HANDLER);
        }

        @Override
        public void newInstance(BlockMenu menu, Location location) {
            menu.addMenuClickHandler(23, INSTANCE_HANDLER);
        }

        @Override
        public boolean canOpen(Block block, Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
            return new int[] {10};
        }
    }
}
