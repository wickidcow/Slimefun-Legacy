package io.github.wickidcow.validation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectOutputStream;

/** Disposable CI fixture: never install on a player's server. */
public final class PriorReleaseUpgradeProbe extends JavaPlugin {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String[] IDS = {"COMPRESSED_CARBON", "ELECTRIC_MOTOR", "DURALUMIN_MULTI_TOOL", "SMALL_BACKPACK"};
    private static final int[] AMOUNTS = {37, 29, 1, 1, 17};
    private static final String BLOCK_ID = "CARGO_INPUT_NODE";
    private Path root;
    private String phase;
    private SlimefunBlockData block;
    private PlayerBackpack backpack;
    private List<Integer> blockSlots;
    private Chest chest;

    @Override public void onEnable() {
        root = getDataFolder().toPath();
        phase = System.getProperty("legacy.fixture.phase", "");
        if (!List.of("write", "baseline", "upgrade", "restart").contains(phase)) {
            getLogger().severe("PRIOR_RELEASE_FIXTURE FAIL: no approved fixture phase");
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, this::run, 100L);
    }

    private void run() {
        try {
            Files.createDirectories(root);
            require(Slimefun.instance().isEnabled(), "Slimefun did not enable");
            String expectedVersion = System.getProperty("legacy.fixture.coreVersion");
            require(expectedVersion.equals(Slimefun.instance().getPluginMeta().getVersion()), "Unexpected core version");
            if (phase.equals("write")) write(); else verify();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private Location location(int x) { return new Location(Bukkit.getWorlds().getFirst(), x, 80, 0); }

    private void write() throws Exception {
        require(!Files.exists(root.resolve("expected.json")), "Refusing to overwrite an existing fixture");
        var owner = Bukkit.getOfflinePlayer(OWNER);
        require("LegacyFixture".equals(owner.getName()), "Fixture player identity was not loaded from usercache");
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        profiles.createProfile(owner);
        backpack = profiles.createBackpack(owner, "Old owner backpack", 42, 27);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            SlimefunItem definition = Objects.requireNonNull(SlimefunItem.getById(IDS[i]), IDS[i]);
            ItemStack item = definition.getItem().clone();
            item.setAmount(AMOUNTS[i]);
            decorate(item, i);
            items.add(item);
        }
        ItemStack unknown = new ItemStack(Material.IRON_BLOCK, AMOUNTS[4]);
        Slimefun.getItemDataService().setItemData(unknown, "MISSING_PREVIOUS_ADDON_ITEM");
        decorate(unknown, 4);
        items.add(unknown);
        PlayerBackpack.setItemPdc(items.get(3), backpack.getUniqueId().toString(), OWNER.toString());
        location(0).getBlock().setType(Material.CHEST);
        chest = (Chest) location(0).getBlock().getState();
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        controller.setDelayedSavingEnable(false);
        location(4).getBlock().setType(Objects.requireNonNull(SlimefunItem.getById(BLOCK_ID)).getItem().getType());
        block = controller.createBlock(location(4), BLOCK_ID);
        require(block.getBlockMenu() != null, "Cargo node preset not available");
        block.setData("upgrade_fixture_owner", OWNER.toString());
        block.setData("upgrade_fixture_opaque", "  old: {\"counter\":\"00042\"}\nretained  ");
        blockSlots = new ArrayList<>();
        for (int slot = 0; slot < block.getBlockMenu().toInventory().getSize() && blockSlots.size() < items.size(); slot++) {
            if (!block.getBlockMenu().getPreset().getPresetSlots().contains(slot)) blockSlots.add(slot);
        }
        require(blockSlots.size() == items.size(), "Not enough actual item slots in core cargo node");
        for (int i = 0; i < items.size(); i++) {
            chest.getBlockInventory().setItem(i, items.get(i).clone());
            backpack.getInventory().setItem(i, items.get(i).clone());
            block.getBlockMenu().replaceExistingItem(blockSlots.get(i), items.get(i).clone());
            Files.write(root.resolve("native-" + i + ".bin"), ItemStackDataCodec.serialize(items.get(i)));
            Files.write(root.resolve("bukkit-" + i + ".txt"), legacyBytes(items.get(i)));
        }
        JsonObject identity = new JsonObject();
        identity.addProperty("owner", OWNER.toString());
        identity.addProperty("backpack", backpack.getUniqueId().toString());
        identity.add("blockSlots", JSON.toJsonTree(blockSlots));
        identity.addProperty("producerCore", Slimefun.instance().getPluginMeta().getVersion());
        identity.addProperty("producerMinecraft", Bukkit.getMinecraftVersion());
        Files.writeString(root.resolve("identities.json"), JSON.toJson(identity));
        Files.writeString(root.resolve("expected.json"), JSON.toJson(capture()));
        persist().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) fail(error); else finish();
        }));
    }

    private void verify() throws Exception {
        JsonObject identity = JsonParser.parseString(Files.readString(root.resolve("identities.json"))).getAsJsonObject();
        require("4.1.61".equals(identity.get("producerCore").getAsString()), "Not the approved released producer");
        require("1.21.11".equals(identity.get("producerMinecraft").getAsString()), "Unexpected producer Minecraft");
        require(OWNER.toString().equals(identity.get("owner").getAsString()), "Owner identity changed");
        blockSlots = new ArrayList<>();
        identity.getAsJsonArray("blockSlots").forEach(value -> blockSlots.add(value.getAsInt()));
        require(blockSlots.size() == 5, "Incomplete slot manifest");
        require(location(0).getBlock().getState() instanceof Chest, "Original world chest is missing");
        chest = (Chest) location(0).getBlock().getState();
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        controller.setDelayedSavingEnable(false);
        block = Objects.requireNonNull(controller.getBlockData(location(4)), "Original block record missing");
        controller.loadBlockData(block);
        require(block.isDataLoaded() && block.getBlockMenu() != null, "Original block did not completely load");
        backpack = Objects.requireNonNull(Slimefun.getDatabaseManager().getProfileDataController()
                .getBackpack(identity.get("backpack").getAsString()), "Original backpack record missing");
        JsonObject expected = JsonParser.parseString(Files.readString(root.resolve("expected.json"))).getAsJsonObject();
        JsonObject actual = capture();
        Files.writeString(root.resolve(phase + "-observed.json"), JSON.toJson(actual));
        require(expected.equals(actual), "Persisted item/owner/slot/metadata mismatch; compare expected and observed JSON");
        // Resave loaded objects, not new templates; a separate process reads them again.
        persist().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) fail(error); else finish();
        }));
    }

    private CompletableFuture<Void> persist() {
        return CompletableFuture.allOf(
                Slimefun.getDatabaseManager().getBlockDataController().saveBlockInventoryAsync(block),
                Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInventoryAsync(backpack));
    }

    private JsonObject capture() throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("blockId", block.getSfId());
        result.addProperty("blockOwner", block.getData("upgrade_fixture_owner"));
        result.addProperty("blockOpaque", block.getData("upgrade_fixture_opaque"));
        result.addProperty("backpackUUID", backpack.getUniqueId().toString());
        result.addProperty("backpackOwner", backpack.getOwner().getUniqueId().toString());
        result.addProperty("backpackId", backpack.getId());
        result.addProperty("backpackSize", backpack.getSize());
        result.addProperty("backpackName", backpack.getName());
        for (int i = 0; i < 5; i++) {
            result.add("chest-" + i, item(chest.getBlockInventory().getItem(i)));
            result.add("block-" + blockSlots.get(i), item(block.getBlockMenu().getItemInSlot(blockSlots.get(i))));
            result.add("backpack-" + i, item(backpack.getInventory().getItem(i)));
            result.add("native-" + i, item(ItemStackDataCodec.deserialize(Files.readAllBytes(root.resolve("native-" + i + ".bin")))));
            result.add("bukkit-" + i, item(ItemStackDataCodec.deserialize(Files.readAllBytes(root.resolve("bukkit-" + i + ".txt")))));
        }
        return result;
    }

    private static void decorate(ItemStack item, int index) {
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing player item " + index, TextColor.color(0x123ABC)));
        meta.lore(List.of(Component.text("Original custom lore"), Component.text("Charge: 123.4567 J")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (meta instanceof Damageable damage) damage.setDamage(37);
        var models = meta.getCustomModelDataComponent();
        models.setFloats(List.of(12345.5F, 0.25F));
        models.setStrings(List.of("original-model"));
        models.setFlags(List.of(true, false));
        meta.setCustomModelDataComponent(models);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        pdc.set(key("slimefun:soulbound"), PersistentDataType.BYTE, (byte) 1);
        pdc.set(key("oldaddon:counter"), PersistentDataType.LONG, 9000000001L);
        pdc.set(key("oldaddon:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
        pdc.set(key("oldaddon:integers"), PersistentDataType.INTEGER_ARRAY, new int[] {0, -1, 42});
        pdc.set(key("oldaddon:longs"), PersistentDataType.LONG_ARRAY, new long[] {0, -1, 9000000001L});
        pdc.set(key("oldaddon:opaque"), PersistentDataType.STRING, "  {\"owner\":\"old\"}\nretained " + index);
        item.setItemMeta(meta);
    }

    private static JsonObject item(ItemStack item) {
        require(item != null && !item.isEmpty(), "A non-empty old item became empty");
        JsonObject result = new JsonObject();
        result.addProperty("material", item.getType().name());
        result.addProperty("amount", item.getAmount());
        result.addProperty("storedId", Slimefun.getItemDataService().getItemData(item).orElse(""));
        var definition = SlimefunItem.getByItem(item);
        result.addProperty("recognizedId", definition == null ? "" : definition.getId());
        var meta = item.getItemMeta();
        result.addProperty("name", meta.displayName() == null ? "" : GsonComponentSerializer.gson().serialize(meta.displayName()));
        JsonArray lore = new JsonArray();
        if (meta.lore() != null) meta.lore().forEach(line -> lore.add(GsonComponentSerializer.gson().serialize(line)));
        result.add("lore", lore);
        result.addProperty("unbreakable", meta.isUnbreakable());
        result.addProperty("damage", meta instanceof Damageable damage ? damage.getDamage() : -1);
        result.add("flags", JSON.toJsonTree(meta.getItemFlags().stream().map(Enum::name).sorted().toList()));
        JsonObject enchantments = new JsonObject();
        meta.getEnchants().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getKey().toString()))
                .forEach(e -> enchantments.addProperty(e.getKey().getKey().toString(), e.getValue()));
        result.add("enchantments", enchantments);
        result.add("pdc", pdc(meta.getPersistentDataContainer()));
        var models = meta.getCustomModelDataComponent();
        result.add("modelFloats", JSON.toJsonTree(models.getFloats()));
        result.add("modelStrings", JSON.toJsonTree(models.getStrings()));
        result.add("modelFlags", JSON.toJsonTree(models.getFlags()));
        return result;
    }

    private static JsonObject pdc(PersistentDataContainer data) {
        JsonObject result = new JsonObject();
        for (NamespacedKey key : data.getKeys().stream().sorted(Comparator.comparing(NamespacedKey::toString)).toList()) {
            Object value = null;
            String typeName = "";
            for (PersistentDataType<?, ?> type : List.of(PersistentDataType.BYTE, PersistentDataType.SHORT,
                    PersistentDataType.INTEGER, PersistentDataType.LONG, PersistentDataType.FLOAT,
                    PersistentDataType.DOUBLE, PersistentDataType.STRING, PersistentDataType.BYTE_ARRAY,
                    PersistentDataType.INTEGER_ARRAY, PersistentDataType.LONG_ARRAY)) {
                if (data.has(key, type)) { value = data.get(key, type); typeName = type.getPrimitiveType().getName(); break; }
            }
            require(value != null, "Fixture contains an unhandled typed PDC field: " + key);
            JsonObject entry = new JsonObject();
            entry.addProperty("type", typeName);
            entry.add("value", JSON.toJsonTree(value));
            result.add(key.toString(), entry);
        }
        return result;
    }

    @SuppressWarnings("deprecation") // Intentional historical encoding fixture, not a production writer.
    private static byte[] legacyBytes(ItemStack item) throws Exception {
        try (var bytes = new ByteArrayOutputStream(); var stream = new BukkitObjectOutputStream(bytes)) {
            stream.writeObject(item); stream.flush();
            return Base64.getEncoder().encode(bytes.toByteArray());
        }
    }

    private void finish() {
        try {
            location(0).getWorld().save();
            JsonObject outcome = new JsonObject();
            outcome.addProperty("phase", phase);
            outcome.addProperty("core", Slimefun.instance().getPluginMeta().getVersion());
            outcome.addProperty("minecraft", Bukkit.getMinecraftVersion());
            outcome.addProperty("itemViews", 25);
            Files.writeString(root.resolve(phase + "-passed.json"), JSON.toJson(outcome));
            getLogger().info("PRIOR_RELEASE_FIXTURE PASS phase=" + phase + " views=25");
        } catch (Throwable failure) { fail(failure); }
    }
    private void fail(Throwable failure) { getLogger().log(Level.SEVERE, "PRIOR_RELEASE_FIXTURE FAIL phase=" + phase, failure); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static NamespacedKey key(String value) { return Objects.requireNonNull(NamespacedKey.fromString(value)); }
}
