package io.github.thebusybiscuit.slimefun4.api.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Protect the actual Bukkit stack identity of representative already-released
 * core constructor formatting, including the old/current native item path.
 *
 * <p>These are independent v4.1.81 component baselines, not historical addon
 * world captures or claimed proof of every crafting/machine/shop producer.
 * A future migration away from deprecated text APIs must NOT make a freshly
 * produced matching Slimefun ID unable to stack with the released baseline.
 */
class SlimefunItemStackNativeStackingParityTest {
    private static final NamespacedKey ITEM_ID = new NamespacedKey("slimefun", "slimefun_item");

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private static ItemStack make(
            Material material, String id, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, id);
        });
        return item;
    }

    private static ItemStack freshlyConstructed(
            Material material, String id, String name, String... lore) {
        return make(material, id, SlimefunItemStack.legacyText(name),
                java.util.Arrays.stream(lore).map(SlimefunItemStack::legacyText).toList());
    }

    private static void checkStacking(
            Material material, String id, String name, List<Component> releasedNameAndLore,
            String... legacyLore) {
        Component releasedName = releasedNameAndLore.getFirst();
        List<Component> releasedLore = releasedNameAndLore.subList(1, releasedNameAndLore.size());
        ItemStack released = make(material, id, releasedName, releasedLore);
        ItemStack freshlyMade = freshlyConstructed(material, id, name, legacyLore);
        byte[] releasedBytes = released.serializeAsBytes();
        byte[] newBytes = freshlyMade.serializeAsBytes();
        assertArrayEquals(releasedBytes, newBytes,
                "A deprecated-text API refactor changed the released native metadata for " + id);
        assertTrue(released.isSimilar(freshlyMade), "Released and current " + id + " must stack");
        assertEquals(released.getItemMeta(), freshlyMade.getItemMeta());
        ItemStack fromSavedWorld = ItemStack.deserializeBytes(releasedBytes);
        ItemStack currentFromCommand = ItemStack.deserializeBytes(newBytes);
        fromSavedWorld.setAmount(63);
        currentFromCommand.setAmount(1);
        assertTrue(fromSavedWorld.isSimilar(currentFromCommand));

        Inventory merged = Bukkit.createInventory(null, 9);
        assertTrue(merged.addItem(fromSavedWorld, currentFromCommand).isEmpty(),
                "63 old plus one fresh " + id + " must fit without leftovers");
        assertEquals(64, merged.getItem(0).getAmount());
        assertNull(merged.getItem(1));
        assertArrayEquals(releasedBytes, released.serializeAsBytes());
        assertArrayEquals(newBytes, freshlyMade.serializeAsBytes());
    }

    @Test
    void releasedCarbonadoPresentationRemainsNativeEqualAndStacksSixtyThreePlusOne() {
        Component name = Component.text("Carbonado", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
        List<Component> released = List.of(
                name,
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Black Diamond", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        checkStacking(Material.PAPER, "CARBONADO", "&b&lCarbonado", released, "", "&7Black Diamond");
    }

    @Test
    void releasedMultiColorGoldNameRemainsNativeEqualAndStacks() {
        Component name = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Gold Ingot ", NamedTextColor.WHITE))
                .append(Component.text("(18-Carat)", NamedTextColor.GRAY));
        checkStacking(Material.GOLD_INGOT, "GOLD_18K", "&fGold Ingot &7(18-Carat)",
                List.of(name, Component.text("Refined gold", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)), "&7Refined gold");
    }

    @Test
    void sameMaterialWithDifferentSlimefunIdOrTypedPdcNeverStacks() {
        ItemStack reference = freshlyConstructed(Material.PAPER, "CARBONADO", "&b&lCarbonado",
                "", "&7Black Diamond");
        ItemStack differentId = ItemStack.deserializeBytes(reference.serializeAsBytes());
        differentId.editMeta(meta -> meta.getPersistentDataContainer()
                .set(ITEM_ID, PersistentDataType.STRING, "CARBON_CHUNK"));
        assertFalse(reference.isSimilar(differentId));
        ItemStack differentTypedMetadata = ItemStack.deserializeBytes(reference.serializeAsBytes());
        NamespacedKey owner = new NamespacedKey("addon", "stored_owner");
        differentTypedMetadata.editMeta(meta -> meta.getPersistentDataContainer()
                .set(owner, PersistentDataType.INTEGER, 737));
        assertFalse(reference.isSimilar(differentTypedMetadata));
        byte[] exactReference = reference.serializeAsBytes();
        assertArrayEquals(exactReference, reference.serializeAsBytes());
    }
}
