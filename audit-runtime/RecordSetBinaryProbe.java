package audit;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native-item fixture. It does not require or impersonate a connected player. */
public final class RecordSetBinaryProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                boolean original = Boolean.getBoolean("recordset.audit.original");
                boolean restart = Boolean.getBoolean("recordset.audit.restart");
                Path root = Path.of("recordset-fixtures");
                Files.createDirectories(root);
                for (int path = 0; path < 3; path++) {
                    ItemStack expected = item();
                    var record = new RecordSet();
                    if (restart) {
                        record.put(FieldKey.INVENTORY_ITEM, Files.readAllBytes(root.resolve(path + ".bin")));
                    } else {
                        record.put(FieldKey.INVENTORY_ITEM, expected);
                    }
                    record.readonly();
                    byte[] before = ((byte[]) record.getValue(FieldKey.INVENTORY_ITEM)).clone();
                    byte[] exported = switch (path) {
                        case 0 -> (byte[]) record.getValue(FieldKey.INVENTORY_ITEM);
                        case 1 -> (byte[]) record.getAllValues().get(FieldKey.INVENTORY_ITEM);
                        default -> (byte[]) record.getAllValues().entrySet().iterator().next().getValue();
                    };
                    Arrays.fill(exported, (byte) 0);
                    byte[] after = (byte[]) record.getValue(FieldKey.INVENTORY_ITEM);
                    if (original) {
                        require(!Arrays.equals(before, after), "Original buffer escape did not reproduce for path " + path);
                    } else {
                        require(Arrays.equals(before, after), "Owned item buffer changed for path " + path);
                        ItemStack actual = record.getItemStack(FieldKey.INVENTORY_ITEM);
                        require(actual != null && actual.getAmount() == 37 && expected.isSimilar(actual),
                                "Exact native item identity/metadata did not survive path " + path);
                        var data = actual.getItemMeta().getPersistentDataContainer();
                        require(Float.floatToRawIntBits(data.get(key("slimefun:item_charge"), PersistentDataType.FLOAT))
                                == Float.floatToRawIntBits(123.4567F), "Charge bits changed");
                        require(data.get(key("other:large_count"), PersistentDataType.LONG) == 9_007_199_254_740_993L,
                                "Large count changed");
                        require("same-owner".equals(data.get(key("other:nested"), PersistentDataType.TAG_CONTAINER)
                                .get(key("other:owner"), PersistentDataType.STRING)), "Nested owner changed");
                        Files.write(root.resolve(path + ".bin"), after);
                    }
                }
                String marker = original ? "RECORDSET_ORIGINAL_ESCAPE_REPRODUCED"
                        : restart ? "RECORDSET_NATIVE_RESTART_PASS" : "RECORDSET_NATIVE_WRITE_PASS";
                Files.writeString(Path.of("recordset-result.txt"), marker + " paths=3\n");
                getLogger().info(marker + " paths=3");
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "RECORDSET_NATIVE_FAIL", failure);
            } finally {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private static ItemStack item() {
        ItemStack item = new ItemStack(Material.DIAMOND, 37);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Owner's existing item"));
        meta.lore(List.of(Component.text("Original lore; keep unchanged")));
        var data = meta.getPersistentDataContainer();
        data.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "UNREGISTERED_OLD_ID");
        data.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        data.set(key("other:large_count"), PersistentDataType.LONG, 9_007_199_254_740_993L);
        data.set(key("other:opaque"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 4});
        var nested = data.getAdapterContext().newPersistentDataContainer();
        nested.set(key("other:owner"), PersistentDataType.STRING, "same-owner");
        data.set(key("other:nested"), PersistentDataType.TAG_CONTAINER, nested);
        item.setItemMeta(meta);
        return item;
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString(value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
