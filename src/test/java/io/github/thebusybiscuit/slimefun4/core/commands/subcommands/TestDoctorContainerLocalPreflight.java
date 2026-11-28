package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class TestDoctorContainerLocalPreflight {

    @Test
    void ordinaryBarrelIsOnlyALocalCheckNotAuthorization() {
        assertEquals(
                DoctorContainerLocalPreflight.Result.LOCAL_CHECKS_ONLY,
                inspect(Material.BARREL, Chest.Type.SINGLE, 27, false, false, false, false, false, false));
    }

    @Test
    void refusesUnsupportedLayoutBeforeOtherInventoryAccess() {
        assertEquals(
                DoctorContainerLocalPreflight.Result.UNSUPPORTED_LAYOUT,
                inspect(Material.CHEST, Chest.Type.LEFT, 27, false, false, false, false, false, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.UNSUPPORTED_LAYOUT,
                inspect(Material.CHEST, Chest.Type.SINGLE, 54, false, false, false, false, false, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.UNSUPPORTED_LAYOUT,
                inspect(Material.SHULKER_BOX, Chest.Type.SINGLE, 27, false, false, false, false, false, false));
    }

    @Test
    void reportsKnownLocalBlockersWithoutAnyInventoryWrites() {
        assertEquals(
                DoctorContainerLocalPreflight.Result.ACTIVE_VIEWERS,
                inspect(Material.BARREL, Chest.Type.SINGLE, 27, true, false, false, false, false, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.LOCKED,
                inspect(Material.BARREL, Chest.Type.SINGLE, 27, false, true, false, false, false, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.TILE_METADATA,
                inspect(Material.BARREL, Chest.Type.SINGLE, 27, false, false, true, false, false, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.LOOT_TABLE_OR_UNKNOWN,
                inspect(Material.BARREL, Chest.Type.SINGLE, 27, false, false, false, true, false, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.SLIMEFUN_BLOCK,
                inspect(Material.BARREL, Chest.Type.SINGLE, 27, false, false, false, false, true, false));
        assertEquals(
                DoctorContainerLocalPreflight.Result.SHOP_INTEGRATION_UNVERIFIED,
                inspect(Material.CHEST, Chest.Type.SINGLE, 27, false, false, false, false, false, true));
    }

    @Test
    void adjacentHoppersAreRefusedInEveryDirection() {
        for (BlockFace face : List.of(
                BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            assertEquals(
                    DoctorContainerLocalPreflight.Result.ADJACENT_HOPPER,
                    DoctorContainerLocalPreflight.inspectAdjacentHoppers(
                            direction -> direction == face ? Material.HOPPER : Material.AIR));
        }
    }

    @Test
    void missingNeighborCannotProveIsolation() {
        assertEquals(
                DoctorContainerLocalPreflight.Result.NEIGHBOR_UNAVAILABLE,
                DoctorContainerLocalPreflight.inspectAdjacentHoppers(
                        direction -> direction == BlockFace.WEST ? null : Material.STONE));
        assertEquals(
                DoctorContainerLocalPreflight.Result.ADJACENT_HOPPER,
                DoctorContainerLocalPreflight.inspectAdjacentHoppers(
                        direction -> direction == BlockFace.UP ? null : Material.HOPPER));
        assertEquals(
                DoctorContainerLocalPreflight.Result.LOCAL_CHECKS_ONLY,
                DoctorContainerLocalPreflight.inspectAdjacentHoppers(direction -> Material.AIR));
    }

    private static DoctorContainerLocalPreflight.Result inspect(
            Material material,
            Chest.Type chestType,
            int size,
            boolean viewers,
            boolean locked,
            boolean tagged,
            boolean loot,
            boolean slimefun,
            boolean shop) {
        Chest blockData = (Chest) Proxy.newProxyInstance(
                Chest.class.getClassLoader(), new Class<?>[] {Chest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getType")) {
                        return chestType;
                    }
                    throw new AssertionError("Unexpected block data access: " + method.getName());
                });
        PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getKeys")) {
                        return tagged ? Set.of(new NamespacedKey("fixture", "tag")) : Set.of();
                    }
                    throw new AssertionError("Unexpected tile metadata access: " + method.getName());
                });
        LootTable lootTable = (LootTable) Proxy.newProxyInstance(
                LootTable.class.getClassLoader(), new Class<?>[] {LootTable.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected loot access: " + method.getName());
                });
        Container state = (Container) Proxy.newProxyInstance(
                Container.class.getClassLoader(), new Class<?>[] {Container.class, Lootable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> material;
                    case "getBlockData" -> blockData;
                    case "isLocked" -> locked;
                    case "getPersistentDataContainer" -> pdc;
                    case "getLootTable" -> loot ? lootTable : null;
                    default -> throw new AssertionError("Unexpected container access: " + method.getName());
                });
        // The early guard uses only block-state metadata. The proxy rejects
        // getInventory and all other unexpected block access.
        assertEquals(loot, DoctorContainerLocalPreflight.hasAttachedLootTable(state));
        Inventory inventory = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSize" -> size;
                    case "getViewers" -> viewers ? Collections.singletonList(null) : List.of();
                    default -> throw new AssertionError("Unexpected inventory access: " + method.getName());
                });
        return DoctorContainerLocalPreflight.inspect(state, inventory, slimefun, shop);
    }
}
