package audit;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper item and snapshot APIs; no player simulation or database-controller certificate. */
public final class SnapshotNativeProbe extends JavaPlugin {
    private Class<?> snapshotType;
    private Class<?> utilsType;
    private boolean original;
    private final List<String> cases = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                original = Boolean.getBoolean("snapshot.original");
                var core = Bukkit.getPluginManager().getPlugin("Slimefun");
                require(core != null && core.isEnabled(), "Core must be active");
                var loader = core.getClass().getClassLoader();
                String prefix = "com.xzavier0722.mc.plugin.slimefun4.storage.util.";
                snapshotType = Class.forName(prefix + "InvSnapshot", true, loader);
                utilsType = Class.forName(prefix + "InvStorageUtils", true, loader);
                quantity();
                metadata();
                constructorInput();
                emptyEntries();
                nativeData();
                independentCurrentInventory();
                String marker = original ? "SNAPSHOT_ORIGINAL_ALIAS_REPRODUCED" : "SNAPSHOT_NATIVE_OWNERSHIP_PASS";
                Files.writeString(Path.of("snapshot-result.txt"), marker + "\n" + String.join("\n", cases) + "\n");
                getLogger().info(marker + " cases=" + cases.size());
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "SNAPSHOT_NATIVE_FAIL", failure);
            } finally {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private void quantity() throws Exception {
        var saved = snapshot(rich(37));
        var export = exported(saved).getFirst();
        first(export).setAmount(12);
        second(export, 12);
        var changed = rich(12);
        require(difference(saved, changed).isEmpty() == original, "Quantity alias result differs from expected old/fixed behavior");
        require(first(exported(saved).getFirst()).getAmount() == (original ? 12 : 37), "Baseline amount ownership mismatch");
        cases.add("quantity_alias=" + (original ? "hidden" : "detected"));
    }

    private void metadata() throws Exception {
        var saved = snapshot(rich(37));
        ItemStack changed = rich(37);
        var meta = changed.getItemMeta();
        meta.getPersistentDataContainer().set(key("old:owner"), PersistentDataType.STRING, "new-owner");
        meta.getPersistentDataContainer().set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 99.125F);
        changed.setItemMeta(meta);
        first(exported(saved).getFirst(), changed.clone());
        require(difference(saved, changed).isEmpty() == original, "Metadata alias result differs from expected behavior");
        require("UNREGISTERED_OLD_ID".equals(changed.getItemMeta().getPersistentDataContainer()
                .get(key("slimefun:slimefun_item"), PersistentDataType.STRING)), "Item identity changed in fixture");
        cases.add("metadata_alias=" + (original ? "hidden" : "detected"));
    }

    private void constructorInput() throws Exception {
        List<?> source = utilitySnapshot(new ItemStack[] {rich(37)});
        Object saved = snapshotType.getConstructor(List.class).newInstance(source);
        var changed = rich(12);
        first(source.getFirst(), changed.clone());
        second(source.getFirst(), 12);
        require(difference(saved, changed).isEmpty() == original, "Constructor retained caller-owned pair");
        cases.add("list_constructor=" + (original ? "aliased" : "owned"));
    }

    private void emptyEntries() throws Exception {
        List<?> a = utilitySnapshot(new ItemStack[2]);
        List<?> b = utilitySnapshot(new ItemStack[1]);
        Object saved = snapshotType.getConstructor(ItemStack[].class).newInstance((Object) new ItemStack[1]);
        Object pair = a.getFirst();
        try {
            first(pair, new ItemStack(Material.DIAMOND));
            second(pair, 1);
            require((first(a.get(1)) != null) == original, "Empty slots unexpectedly share a pair");
            require((first(b.getFirst()) != null) == original, "Separate helper results unexpectedly share a pair");
            require((!difference(saved, (ItemStack) null).isEmpty()) == original, "Empty baseline isolation mismatch");
        } finally {
            first(pair, null);
            second(pair, 0);
        }
        cases.add("empty_entries=" + (original ? "shared" : "isolated"));
    }

    private void nativeData() throws Exception {
        ItemStack item = rich(37);
        Object saved = snapshot(item);
        ItemStack export = first(exported(saved).getFirst());
        require(export != item && export.equals(item), "Native clone changed existing metadata");
        byte[] serialized = export.serializeAsBytes();
        ItemStack decoded = ItemStack.deserializeBytes(serialized);
        require(decoded.equals(item), "Native bytes changed snapshot item data");
        var data = decoded.getItemMeta().getPersistentDataContainer();
        require(data.has(key("slimefun:item_charge"), PersistentDataType.FLOAT), "Charge type changed");
        require(Float.floatToRawIntBits(data.get(key("slimefun:item_charge"), PersistentDataType.FLOAT))
                == Float.floatToRawIntBits(123.4567F), "Charge bits changed");
        require(data.get(key("old:count"), PersistentDataType.LONG) == 9_007_199_254_740_993L, "Large count changed");
        require(java.util.Arrays.equals(new byte[] {0, -1, 4}, data.get(key("old:bytes"), PersistentDataType.BYTE_ARRAY)), "Bytes changed");
        require("keep".equals(data.get(key("old:nested"), PersistentDataType.TAG_CONTAINER)
                .get(key("old:nested-owner"), PersistentDataType.STRING)), "Nested owner changed");
        Files.write(Path.of("native-old-item.bin"), serialized);
        require(ItemStack.deserializeBytes(Files.readAllBytes(Path.of("native-old-item.bin"))).equals(item), "Native file read changed item");
        cases.add("native_typed_data=exact");
    }

    private void independentCurrentInventory() throws Exception {
        var inventory = Bukkit.createInventory(null, 9);
        inventory.setItem(0, rich(37));
        Object saved = snapshotType.getConstructor(org.bukkit.inventory.Inventory.class).newInstance(inventory);
        inventory.setItem(0, rich(12));
        Set<?> changed = (Set<?>) snapshotType.getMethod("getChangedSlots", org.bukkit.inventory.Inventory.class)
                .invoke(saved, inventory);
        require(changed.equals(Set.of(0)), "Normal live inventory comparison changed");
        cases.add("normal_inventory_change=detected");
    }

    private Object snapshot(ItemStack item) throws Exception {
        return snapshotType.getConstructor(ItemStack[].class).newInstance((Object) new ItemStack[] {item});
    }

    private List<?> utilitySnapshot(ItemStack[] items) throws Exception {
        return (List<?>) utilsType.getMethod("getInvSnapshot", ItemStack[].class).invoke(null, (Object) items);
    }

    private List<?> exported(Object snapshot) throws Exception {
        return (List<?>) snapshotType.getMethod("getSnapshot").invoke(snapshot);
    }

    private Set<?> difference(Object snapshot, ItemStack item) throws Exception {
        return (Set<?>) snapshotType.getMethod("getChangedSlots", ItemStack[].class)
                .invoke(snapshot, (Object) new ItemStack[] {item});
    }

    private static ItemStack first(Object pair) throws Exception {
        return (ItemStack) pair.getClass().getMethod("getFirstValue").invoke(pair);
    }

    private static void first(Object pair, ItemStack item) throws Exception {
        pair.getClass().getMethod("setFirstValue", Object.class).invoke(pair, item);
    }

    private static void second(Object pair, int amount) throws Exception {
        pair.getClass().getMethod("setSecondValue", Object.class).invoke(pair, amount);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static NamespacedKey key(String value) {
        return java.util.Objects.requireNonNull(NamespacedKey.fromString(value));
    }

    private static ItemStack rich(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing named item"));
        meta.lore(List.of(Component.text("Owner-written lore")));
        var data = meta.getPersistentDataContainer();
        data.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "UNREGISTERED_OLD_ID");
        data.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        data.set(key("old:owner"), PersistentDataType.STRING, "original-owner");
        data.set(key("old:count"), PersistentDataType.LONG, 9_007_199_254_740_993L);
        data.set(key("old:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 4});
        var nested = data.getAdapterContext().newPersistentDataContainer();
        nested.set(key("old:nested-owner"), PersistentDataType.STRING, "keep");
        data.set(key("old:nested"), PersistentDataType.TAG_CONTAINER, nested);
        item.setItemMeta(meta);
        return item;
    }
}
