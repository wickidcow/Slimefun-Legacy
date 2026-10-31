package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import city.norain.slimefun4.api.menu.UniversalMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Execute the non-Ageable listener-harvest delivery path without starting a
 * Slimefun server. The menu is mocked to exercise its documented remainder
 * contract; this does not prove ExoticGarden world-state or restart behavior.
 */
class FarmerAndroidListenerHarvestTest {

    @Test
    void completeListenerHarvestDeliveryDoesNotSpawnAnExtraDrop() {
        UniversalMenu menu = mock(UniversalMenu.class);
        Block block = mock(Block.class);
        int[] slots = {10, 11};
        ItemStack harvest = new ItemStack(Material.SWEET_BERRIES, 4);

        when(menu.pushItem(harvest, slots)).thenReturn(null);

        FarmerAndroid.deliverListenerHarvest(menu, block, harvest, slots);

        verify(menu).pushItem(harvest, slots);
        verify(block, never()).getWorld();
    }

    @Test
    void partialListenerHarvestOverflowIsDroppedAtOriginalBlock() {
        UniversalMenu menu = mock(UniversalMenu.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        Location location = new Location(world, 12, 70, -4);
        int[] slots = {10, 11};
        ItemStack harvest = new ItemStack(Material.SWEET_BERRIES, 5);
        ItemStack remainder = new ItemStack(Material.SWEET_BERRIES, 2);

        when(menu.pushItem(harvest, slots)).thenReturn(remainder);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(location);

        FarmerAndroid.deliverListenerHarvest(menu, block, harvest, slots);

        verify(menu).pushItem(harvest, slots);
        verify(world).dropItemNaturally(location, remainder);
    }

    @Test
    void fullListenerHarvestOverflowIsNotSilentlyDiscarded() {
        UniversalMenu menu = mock(UniversalMenu.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        Location location = new Location(world, 12, 70, -4);
        int[] slots = {10, 11};
        ItemStack harvest = new ItemStack(Material.APPLE, 3);

        when(menu.pushItem(harvest, slots)).thenReturn(harvest);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(location);

        FarmerAndroid.deliverListenerHarvest(menu, block, harvest, slots);

        verify(menu).pushItem(harvest, slots);
        verify(world).dropItemNaturally(location, harvest);
    }
}
