package io.github.wickidcow.validation;

import io.github.sefiraat.slimetinker.utils.Ids;
import io.github.sefiraat.slimetinker.items.tinkermaterials.TinkerMaterialManager;
import io.github.sefiraat.slimetinker.events.friend.TraitPartType;
import io.github.sefiraat.slimetinker.utils.ItemUtils;
import io.github.sefiraat.slimetinker.utils.Keys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable generated fixtures. No player, combat or historical-world claims. */
public final class TinkerProbe extends JavaPlugin {
    private int assertions;
    private final NamespacedKey owner = new NamespacedKey("fixture", "owner");
    private final NamespacedKey amount = new NamespacedKey("fixture", "large_count");
    private final NamespacedKey fraction = new NamespacedKey("fixture", "fraction");
    private final NamespacedKey nested = new NamespacedKey("fixture", "nested");
    private final NamespacedKey bytes = new NamespacedKey("fixture", "bytes");

    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try { exercise(); }
            catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "TINKER_NATIVE_FAIL", failure);
            }
        }, 20L);
    }

    private void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    private ItemMeta withoutPresentation(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(null);
        meta.lore(null);
        return meta;
    }

    private ItemStack fixture(boolean armour, String identity) {
        // Leather owns binder/gambeson traits; metal-only IRON is not a valid fixture for those roles.
        check(TinkerMaterialManager.getTraitName(Ids.IRON, armour ? TraitPartType.PLATES : TraitPartType.HEAD) != null, "fixture primary trait registered");
        check(TinkerMaterialManager.getTraitName(Ids.LEATHER, armour ? TraitPartType.GAMBESON : TraitPartType.BINDER) != null, "fixture soft-material trait registered");
        check(TinkerMaterialManager.getTraitName(armour ? Ids.IRON : Ids.COPPER, armour ? TraitPartType.LINKS : TraitPartType.ROD) != null, "fixture joining trait registered");
        ItemStack stack = new ItemStack(armour ? Material.DIAMOND_HELMET : Material.DIAMOND_PICKAXE);
        ItemMeta meta = stack.getItemMeta();
        ((Damageable) meta).setDamage(17);
        meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        meta.itemName(Component.text("Independent item-name component"));
        meta.displayName(Component.text("Prior generated name"));
        meta.lore(List.of(Component.text("Prior generated lore")));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(owner, PersistentDataType.STRING, identity);
        pdc.set(amount, PersistentDataType.LONG, 9007199254740993L);
        pdc.set(fraction, PersistentDataType.FLOAT, 1.25F);
        pdc.set(bytes, PersistentDataType.BYTE_ARRAY, new byte[]{0, 1, -1, 64});
        PersistentDataContainer child = pdc.getAdapterContext().newPersistentDataContainer();
        child.set(owner, PersistentDataType.STRING, "nested-owner");
        child.set(amount, PersistentDataType.LONG, Long.MAX_VALUE - 17);
        pdc.set(nested, PersistentDataType.TAG_CONTAINER, child);
        pdc.set(Keys.ST_EXP_CURRENT, PersistentDataType.INTEGER, 17);
        pdc.set(Keys.ST_EXP_REQUIRED, PersistentDataType.DOUBLE, 4096.25);
        pdc.set(Keys.ST_LEVEL, PersistentDataType.INTEGER, 12);
        pdc.set(Keys.ST_MOD_SLOTS, PersistentDataType.INTEGER, 3);
        pdc.set(Keys.ST_MODS, PersistentDataType.INTEGER_ARRAY, armour ? new int[]{1} : new int[]{0, 12, 0, 0, 0, 0});
        if (armour) {
            pdc.set(Keys.ARMOUR_INFO_ARMOUR_TYPE, PersistentDataType.STRING, "helmet");
            pdc.set(Keys.ARMOUR_INFO_PLATE_MATERIAL, PersistentDataType.STRING, Ids.IRON);
            pdc.set(Keys.ARMOUR_INFO_GAMBESON_MATERIAL, PersistentDataType.STRING, Ids.LEATHER);
            pdc.set(Keys.ARMOUR_INFO_LINKS_MATERIAL, PersistentDataType.STRING, Ids.IRON);
            pdc.set(Keys.ST_MOD_LEVEL_OBSIDIAN, PersistentDataType.INTEGER, 1);
        } else {
            pdc.set(Keys.TOOL_INFO_TOOL_TYPE, PersistentDataType.STRING, "pickaxe");
            pdc.set(Keys.TOOL_INFO_HEAD_MATERIAL, PersistentDataType.STRING, Ids.IRON);
            pdc.set(Keys.TOOL_INFO_BINDER_MATERIAL, PersistentDataType.STRING, Ids.LEATHER);
            pdc.set(Keys.TOOL_INFO_ROD_MATERIAL, PersistentDataType.STRING, Ids.COPPER);
            pdc.set(Keys.ST_MOD_LEVEL_LAPIS, PersistentDataType.INTEGER, 1);
        }
        check(stack.setItemMeta(meta), "fixture metadata accepted");
        return stack;
    }

    private void verifyRebuild(ItemStack stack, String label) {
        ItemMeta before = withoutPresentation(stack);
        int count = stack.getAmount();
        Material material = stack.getType();
        ItemUtils.rebuildTinkerName(stack);
        ItemUtils.rebuildTinkerLore(stack);
        check(before.equals(withoutPresentation(stack)), label + ": non-presentation metadata unchanged");
        check(count == stack.getAmount() && material == stack.getType(), label + ": stack identity/count unchanged");
        check(stack.getItemMeta().displayName() != null, label + ": generated name");
        check(stack.getItemMeta().lore() != null && stack.getItemMeta().lore().size() >= 12, label + ": generated lore");
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        check(Long.valueOf(9007199254740993L).equals(pdc.get(amount, PersistentDataType.LONG)), label + ": exact LONG");
        check(Float.valueOf(1.25F).equals(pdc.get(fraction, PersistentDataType.FLOAT)), label + ": exact FLOAT");
        check(Arrays.equals(new byte[]{0, 1, -1, 64}, pdc.get(bytes, PersistentDataType.BYTE_ARRAY)), label + ": byte array");
        check("nested-owner".equals(pdc.get(nested, PersistentDataType.TAG_CONTAINER).get(owner, PersistentDataType.STRING)), label + ": nested data");
        check(ItemUtils.getTinkerExp(stack) == 17 && ItemUtils.getTinkerLevel(stack) == 12 && ItemUtils.getTinkerModifierSlots(stack) == 3, label + ": tool progression unchanged");
        ItemStack once = stack.clone();
        ItemUtils.rebuildTinkerName(stack);
        ItemUtils.rebuildTinkerLore(stack);
        check(once.equals(stack), label + ": repeat rebuild is stable");
        ItemStack roundtrip = ItemStack.deserializeBytes(stack.serializeAsBytes());
        check(stack.equals(roundtrip), label + ": native item roundtrip");
    }

    private void exercise() throws Exception {
        Path root = getDataFolder().toPath();
        Files.createDirectories(root);
        boolean restart = Files.exists(root.resolve("first-pass.txt"));
        check(getServer().getPluginManager().getPlugin("SlimeTinker").isEnabled(), "actual addon enabled");
        for (boolean armour : new boolean[]{false, true}) {
            String name = armour ? "armour" : "tool";
            ItemStack stack = fixture(armour, "owner-A");
            verifyRebuild(stack, name);
            ItemStack otherOwner = fixture(armour, "owner-B");
            verifyRebuild(otherOwner, name + " second owner");
            check(!stack.isSimilar(otherOwner), name + ": owner separation retained");
            Path stored = root.resolve(name + ".dat");
            if (restart) {
                ItemStack reopened = ItemStack.deserializeBytes(Files.readAllBytes(stored));
                check(stack.equals(reopened), name + ": separate-process persisted item equality");
                verifyRebuild(reopened, name + " reopened");
            } else {
                Files.write(stored, stack.serializeAsBytes());
            }
        }
        ItemStack ordinary = new ItemStack(Material.STONE, 17);
        ItemStack ordinaryBefore = ordinary.clone();
        ItemUtils.rebuildTinkerName(ordinary);
        ItemUtils.rebuildTinkerLore(ordinary);
        check(ordinaryBefore.equals(ordinary), "ordinary stack untouched");
        ItemStack malformed = fixture(false, "owner-A");
        ItemMeta badMeta = malformed.getItemMeta();
        badMeta.getPersistentDataContainer().remove(Keys.ST_EXP_REQUIRED);
        malformed.setItemMeta(badMeta);
        ItemStack malformedBefore = malformed.clone();
        boolean rejected = false;
        try { ItemUtils.rebuildTinkerLore(malformed); }
        catch (NullPointerException | IllegalArgumentException expected) { rejected = true; }
        check(rejected, "historical incomplete-data behavior retained");
        check(malformedBefore.equals(malformed), "incomplete item not rewritten");
        String result = "TINKER_NATIVE_PASS phase=" + (restart ? "restart" : "initial") + " assertions=" + assertions;
        Files.writeString(root.resolve(restart ? "restart-pass.txt" : "first-pass.txt"), result + "\n");
        getLogger().info(result);
    }
}
