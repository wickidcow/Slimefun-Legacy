package io.github.wickidcow.validation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Disposable native-item validation only; never shipped in the maintained addon bundle. */
public final class FNStateProbe extends JavaPlugin {
    private static final String GEM_ID = "RETALIATE_GEM";
    private static final String GEM_NAME = "Retaliate";
    private static final NamespacedKey SOCKET = key("fnamplifications:diamond_sword_socket_amount");
    private static final NamespacedKey GEM = key("fnamplifications:retaliate_gem");
    private static final NamespacedKey TIER = key("fnamplifications:retaliate_gem_gem_tier");
    private static final NamespacedKey OWNER = key("legacyaddon:owner_uuid");
    private static final NamespacedKey CHARGE = key("slimefun:item_charge");
    private static final NamespacedKey ITEM_ID = key("slimefun:slimefun_item");
    private static final NamespacedKey BYTES = key("legacyaddon:opaque_bytes");
    private static final NamespacedKey COUNT = key("legacyaddon:long_count");
    private final List<String> passed = new ArrayList<>();
    private Method prepare;
    private Method attempt;
    private String header;
    private String footer;

    @Override
    public void onEnable() {
        try {
            Plugin addon = Objects.requireNonNull(getServer().getPluginManager().getPlugin("FNAmplifications"));
            require(addon.isEnabled(), "FNAmplifications is not enabled");
            ClassLoader loader = addon.getClass().getClassLoader();
            Class<?> operation = Class.forName("ne.fnfal113.fnamplifications.gems.implementation.GemUnbindOperation", true, loader);
            prepare = operation.getDeclaredMethod("prepare", ItemMeta.class, NamespacedKey.class,
                    NamespacedKey.class, String.class, String.class, Consumer.class);
            attempt = operation.getDeclaredMethod("attempt", ItemMeta.class, int.class, IntSupplier.class,
                    BooleanSupplier.class, Predicate.class, Runnable.class);
            prepare.setAccessible(true);
            attempt.setAccessible(true);
            Class<?> lore = Class.forName("ne.fnfal113.fnamplifications.gems.implementation.GemLore", true, loader);
            var first = lore.getDeclaredField("HEADER"); first.setAccessible(true); header = (String) first.get(null);
            var last = lore.getDeclaredField("FOOTER"); last.setAccessible(true); footer = (String) last.get(null);
            String nativeMeta = original(1).getItemMeta().getClass().getName();
            require(nativeMeta.contains("CraftMeta"), "Expected native CraftItemMeta, got " + nativeMeta);
            getLogger().info("FN_NATIVE_ITEM_PROBE implementation=" + nativeMeta);
            runChecks();
            require(passed.size() == 16, "Unexpected native case count: " + passed.size());
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("native-item-results.txt"),
                    "Native gem operation tests: PASS\nImplementation: " + nativeMeta + "\n"
                    + String.join("\n", passed) + "\n"
                    + "Scope: real server item/PDC/component/codec and operation methods; no real player GUI or Folia concurrency.\n");
            getLogger().info("FN_NATIVE_ITEM_PROBE_PASS cases=16");
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "FN_NATIVE_ITEM_PROBE_FAIL", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void runChecks() throws Exception {
        check("historical object-stream fixture preserves typed native state", () -> {
            ItemStack item = original(1);
            ItemStack decoded = legacyRoundTrip(item);
            complete(item, decoded);
            require(decoded.getItemMeta().getClass().getName().contains("CraftMeta"), "Decoder returned a non-native fixture");
        });
        check("preparation leaves the native original untouched", () -> {
            ItemStack item = legacyRoundTrip(original(1)); ItemStack before = item.clone();
            ItemMeta staged = prepared(item);
            complete(before, item);
            require(!staged.getPersistentDataContainer().has(GEM), "Selected gem was not removed");
            require(!staged.getPersistentDataContainer().has(SOCKET), "Last socket was not removed");
            ItemStack result = item.clone(); require(result.setItemMeta(staged), "Native metadata rejected");
            protectedState(before, result);
            require(result.getItemMeta().lore().equals(List.of(before.getItemMeta().lore().getFirst())), "Wrong final native lore");
        });
        check("native success commits before spending the original whole tool stack", () -> {
            ItemStack item = original(1); ItemStack before = item.clone(); ItemStack tool = new ItemStack(Material.STICK, 32);
            ItemMeta staged = prepared(item); List<String> sequence = new ArrayList<>();
            String result = tried(staged, 50, () -> 50, () -> true,
                    metadata -> {sequence.add("commit"); return item.setItemMeta(metadata);},
                    () -> {sequence.add("consume"); tool.setAmount(0);});
            require(result.equals("SUCCESS"), result);
            require(sequence.equals(List.of("commit", "consume")), sequence.toString());
            require(tool.isEmpty(), "The historical whole-stack cost changed");
            protectedState(before, item);
        });
        check("valid failed roll spends its tool but retains native item data", () -> {
            ItemStack item = original(2); ItemStack before = item.clone(); ItemStack tool = new ItemStack(Material.STICK, 32);
            String result = tried(prepared(item), 49, () -> 50, () -> true,
                    metadata -> {throw new AssertionError("Failed roll committed metadata");}, () -> tool.setAmount(0));
            require(result.equals("FAILED_ROLL"), result); complete(before, item); require(tool.isEmpty(), "Failed valid attempt cost changed");
        });
        check("missing INTEGER socket state refuses native item without consumption", () -> {
            ItemStack item = original(1); change(item, data -> data.remove(SOCKET)); expectInvalid(item);
        });
        check("wrong LONG socket type refuses without coercing saved data", () -> {
            ItemStack item = original(1); change(item, data -> data.set(SOCKET, PersistentDataType.LONG, 1L)); expectInvalid(item);
        });
        check("invalid native socket counts are not guessed", () -> {
            for (int count : new int[] {-1, 0, 6, Integer.MAX_VALUE}) expectInvalid(original(count));
        });
        check("unknown selected gem STRING is preserved for recovery", () -> {
            ItemStack item = original(1); change(item, data -> data.set(GEM, PersistentDataType.STRING, "UNAVAILABLE_OLD_GEM")); expectInvalid(item);
        });
        check("wrong selected-gem PDC type is not overwritten", () -> {
            ItemStack item = original(1); change(item, data -> data.set(GEM, PersistentDataType.INTEGER, 7)); expectInvalid(item);
        });
        check("missing selected-gem identity cannot spend a tool", () -> {
            ItemStack item = original(1); change(item, data -> data.remove(GEM)); expectInvalid(item);
        });
        check("native null lore remains supported when stored gem data is valid", () -> {
            ItemStack item = original(1); ItemMeta meta = item.getItemMeta(); meta.lore(null); item.setItemMeta(meta);
            ItemStack before = item.clone(); ItemMeta staged = prepared(item);
            complete(before, item); require(item.setItemMeta(staged), "Native null-lore result rejected"); protectedState(before, item);
            require(!item.getItemMeta().hasLore(), "Null lore became unrelated text");
        });
        check("five-socket items retain unrelated gems and exact typed state", () -> {
            ItemStack item = original(5); change(item, data -> data.set(key("fnamplifications:other_gem"), PersistentDataType.STRING, "OTHER_GEM"));
            ItemStack before = item.clone(); require(item.setItemMeta(prepared(item)), "Native metadata rejected");
            require(item.getItemMeta().getPersistentDataContainer().get(SOCKET, PersistentDataType.INTEGER) == 4, "Wrong remaining count");
            protectedState(before, item);
            require(item.getItemMeta().lore().size() == 4, "Multi-gem frame changed");
        });
        check("cleanup failure affects only detached native metadata", () -> {
            ItemStack item = original(1); ItemStack before = item.clone();
            try {
                invoke(prepare, item.getItemMeta(), SOCKET, GEM, GEM_ID, GEM_NAME,
                        (Consumer<PersistentDataContainer>) data -> {data.remove(OWNER); throw new IllegalStateException("injected cleanup failure");});
                throw new AssertionError("Cleanup failure accepted");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().equals("injected cleanup failure"), "Unexpected failure");
            }
            complete(before, item);
        });
        check("stale check prevents randomness metadata update and tool cost", () -> {
            ItemStack item = original(1); ItemStack before = item.clone(); ItemStack tool = new ItemStack(Material.STICK, 32);
            String result = tried(prepared(item), 100, () -> {throw new AssertionError("Stale operation used RNG");}, () -> false,
                    metadata -> {throw new AssertionError("Stale operation committed");}, () -> tool.setAmount(0));
            require(result.equals("STALE_ITEMS"), result); complete(before, item); require(tool.getAmount() == 32, "Stale tool was consumed");
        });
        check("rejected commit never consumes the native tool", () -> {
            ItemStack item = original(1); ItemStack before = item.clone(); ItemStack tool = new ItemStack(Material.STICK, 32);
            String result = tried(prepared(item), 100, () -> 0, () -> true, metadata -> false, () -> tool.setAmount(0));
            require(result.equals("REJECTED_METADATA"), result); complete(before, item); require(tool.getAmount() == 32, "Rejected tool was consumed");
        });
        check("native byte round trips preserve unbound item metadata exactly", () -> {
            ItemStack item = original(2); ItemStack before = item.clone(); require(item.setItemMeta(prepared(item)), "Native update rejected");
            ItemStack first = item.clone();
            for (int i = 0; i < 5; i++) {item = ItemStack.deserializeBytes(item.serializeAsBytes()); complete(first, item); protectedState(before, item);}
            PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
            require(Float.floatToIntBits(data.get(CHARGE, PersistentDataType.FLOAT)) == Float.floatToIntBits(123.4567F), "Charge precision changed");
            require(Arrays.equals(data.get(BYTES, PersistentDataType.BYTE_ARRAY), new byte[] {0, -1, 42, 127}), "Opaque bytes changed");
            require(data.get(COUNT, PersistentDataType.LONG) == 9_000_000_001L, "Long count changed");
        });
    }

    private ItemStack original(int sockets) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Player's original sword", NamedTextColor.AQUA));
        Component rich = Component.text("Original description", NamedTextColor.BLUE)
                .hoverEvent(HoverEvent.showText(Component.text("Retained hover data")));
        var serializer = LegacyComponentSerializer.legacySection();
        meta.lore(List.of(rich, Component.empty(), serializer.deserialize(header),
                serializer.deserialize("§c◬ " + GEM_NAME), serializer.deserialize(footer)));
        var data = meta.getPersistentDataContainer();
        data.set(SOCKET, PersistentDataType.INTEGER, sockets); data.set(GEM, PersistentDataType.STRING, GEM_ID);
        data.set(TIER, PersistentDataType.INTEGER, 2); data.set(ITEM_ID, PersistentDataType.STRING, "OLD_PLAYER_TOOL_ID");
        data.set(OWNER, PersistentDataType.STRING, "11111111-2222-3333-4444-555555555555");
        data.set(CHARGE, PersistentDataType.FLOAT, 123.4567F); data.set(COUNT, PersistentDataType.LONG, 9_000_000_001L);
        data.set(BYTES, PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
        data.set(key("legacyaddon:indices"), PersistentDataType.INTEGER_ARRAY, new int[] {0, -1, 42});
        meta.addEnchant(Enchantment.SHARPNESS, 7, true); meta.setUnbreakable(true); ((Damageable) meta).setDamage(127);
        var model = meta.getCustomModelDataComponent(); model.setFloats(List.of(12345F, 0.5F));
        model.setStrings(List.of("old-addon-model")); model.setFlags(List.of(true, false)); meta.setCustomModelDataComponent(model);
        require(item.setItemMeta(meta), "Native fixture rejected"); return item;
    }

    private ItemMeta prepared(ItemStack item) throws Exception {
        return (ItemMeta) invoke(prepare, item.getItemMeta(), SOCKET, GEM, GEM_ID, GEM_NAME,
                (Consumer<PersistentDataContainer>) data -> data.remove(TIER));
    }
    private String tried(ItemMeta item, int chance, IntSupplier roll, BooleanSupplier unchanged,
            Predicate<ItemMeta> commit, Runnable consume) throws Exception {
        return invoke(attempt, item, chance, roll, unchanged, commit, consume).toString();
    }
    private static Object invoke(Method method, Object... args) throws Exception {
        try {return method.invoke(null, args);} catch (InvocationTargetException error) {
            Throwable cause = error.getCause(); if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error fatal) throw fatal; throw error;
        }
    }
    private void expectInvalid(ItemStack item) throws Exception {
        ItemStack before = item.clone(); ItemStack tool = new ItemStack(Material.STICK, 32);
        try {prepared(item); throw new AssertionError("Invalid stored state accepted");}
        catch (IllegalArgumentException expected) { /* Required validation failure, not a setup error. */ }
        complete(before, item); require(tool.getAmount() == 32, "Invalid state consumed tool");
    }
    private static void change(ItemStack item, Consumer<PersistentDataContainer> edit) {
        ItemMeta meta = item.getItemMeta(); edit.accept(meta.getPersistentDataContainer()); require(item.setItemMeta(meta), "Fixture update rejected");
    }
    private static void protectedState(ItemStack expected, ItemStack actual) {
        require(expected.getType() == actual.getType() && expected.getAmount() == actual.getAmount(), "Material/amount changed");
        ItemMeta before = expected.getItemMeta(); ItemMeta after = actual.getItemMeta();
        for (ItemMeta meta : List.of(before, after)) {
            meta.displayName(null); meta.lore(null);
            meta.getPersistentDataContainer().remove(GEM); meta.getPersistentDataContainer().remove(SOCKET); meta.getPersistentDataContainer().remove(TIER);
        }
        require(before.equals(after), "Protected native metadata changed: expected=" + before + " actual=" + after);
        require(Objects.equals(expected.getItemMeta().displayName(), actual.getItemMeta().displayName()), "Item name changed");
    }
    private static void complete(ItemStack expected, ItemStack actual) {require(expected.equals(actual), "Complete native item differs");}
    @SuppressWarnings("deprecation") // Historical test fixture encoding only; not a production writer.
    private static ItemStack legacyRoundTrip(ItemStack item) throws Exception {
        byte[] stored;
        try (var bytes = new ByteArrayOutputStream(); var stream = new BukkitObjectOutputStream(bytes)) {
            stream.writeObject(item); stream.flush(); stored = Base64.getEncoder().encode(bytes.toByteArray());
        }
        try (var input = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(stored)))) {
            return (ItemStack) input.readObject();
        }
    }
    private void check(String name, Checked operation) throws Exception {operation.run(); passed.add(name); getLogger().info("FN_NATIVE_CASE_PASS " + name);}
    private static void require(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
    private static NamespacedKey key(String value) {return Objects.requireNonNull(NamespacedKey.fromString(value));}
    @FunctionalInterface private interface Checked {void run() throws Exception;}
}
