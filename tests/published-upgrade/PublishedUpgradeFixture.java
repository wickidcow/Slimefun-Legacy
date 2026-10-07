package io.github.wickidcow.sfltest;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ProfileDataController;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only plugin. Never ship this JAR in a server addon bundle. */
public final class PublishedUpgradeFixture extends JavaPlugin {
    private static final UUID OWNER = UUID.fromString("58817a00-e7e5-4ca2-bb8f-cb0e674bb230");
    private static final String WORLD = "sfl-upgrade-fixture";
    private static final NamespacedKey ROUNDTRIP = new NamespacedKey("sflupgradefixture", "roundtrip");
    private final Path expectedFile = Path.of("fixture-expected.properties");
    private final List<PlayerBackpack> heldBackpacks = new ArrayList<>();
    private boolean started;
    private String phase;
    private int checkedSlots;
    private int checkedItems;
    private int expectedPluginCount;

    @Override
    public void onEnable() {
        if (!"disposable-only".equals(System.getProperty("sfl.upgrade.fixture"))) {
            getLogger().severe("Test plugin refused: disposable fixture JVM authorization is absent.");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void guard() throws Exception {
        require(Bukkit.isPrimaryThread(), "Inventory operations must run on the primary thread");
        require("disposable-only".equals(System.getProperty("sfl.upgrade.fixture")), "Missing JVM authorization");
        require("127.0.0.1".equals(Bukkit.getIp()), "Fixture must bind only to loopback");
        require(Bukkit.getOnlinePlayers().isEmpty(), "Fixture refuses real online players");
        require(Files.readString(Path.of("fixture-authorization.txt")).trim().equals(OWNER.toString()),
                "Missing synthetic fixture marker");
        require(Bukkit.getWorld(WORLD) != null, "Dedicated generated fixture world is absent");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || started || args.length != 1) return false;
        phase = args[0];
        try {
            guard();
            require(List.of("seed", "baseline", "upgrade", "restart").contains(phase), "Unknown phase");
            require(Files.readString(Path.of("fixture-phase.txt")).trim().equals(phase), "Phase authorization mismatch");
            require(Slimefun.getVersion().equals((phase.equals("seed") || phase.equals("baseline"))
                    ? "4.1.69" : "4.1.70"), "Wrong running core version: " + Slimefun.getVersion());
            started = true;
            verifyPlugins();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(OWNER);
            require("SFLTestFixture".equals(owner.getName()), "Synthetic usercache owner was not resolved");
            ProfileDataController controller = controller();
            CompletableFuture<PlayerProfile> future = phase.equals("seed")
                    ? controller.getOrCreateProfileAsync(owner) : controller.getProfileAsync(owner);
            future.orTimeout(60, TimeUnit.SECONDS).whenComplete((profile, failure) -> onMain(() -> {
                if (failure != null) throw new IllegalStateException("Profile load failed", failure);
                require(profile != null && OWNER.equals(profile.getUUID()), "Stored owner profile missing or replaced");
                if (phase.equals("seed")) seed(profile, owner);
                else loadAndVerify(profile);
            }));
        } catch (Throwable failure) {
            fail(failure);
        }
        return true;
    }

    private void verifyPlugins() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("fixture-plugins.tsv"));
        require(lines.size() == 45, "Expected precisely 45 published addon records");
        for (String line : lines) {
            String[] fields = line.split("\t", -1);
            require(fields.length == 2, "Malformed expected plugin record");
            var plugin = Bukkit.getPluginManager().getPlugin(fields[0]);
            require(plugin != null && plugin.isEnabled(), "Addon not enabled: " + fields[0]);
            require(plugin.getPluginMeta().getVersion().equals(fields[1]), "Wrong addon version: " + fields[0]);
        }
        expectedPluginCount = lines.size();
    }

    private void seed(PlayerProfile profile, OfflinePlayer owner) throws Exception {
        require(!Files.exists(expectedFile), "Refusing to overwrite an existing fixture");
        require(profile.getBackpackCount() == 0, "Seed owner is not new");
        Map<String, List<SlimefunItem>> groups = new TreeMap<>();
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item.getAddon() == null || item.getAddon().getJavaPlugin() == null) continue;
            ItemStack template = item.getItem();
            if (template == null || template.isEmpty()) continue;
            groups.computeIfAbsent(item.getAddon().getJavaPlugin().getName(), ignored -> new ArrayList<>()).add(item);
        }
        require(groups.containsKey("Slimefun"), "No registered core items");
        Properties expected = new Properties();
        List<ItemStack> samples = new ArrayList<>();
        StringBuilder coverage = new StringBuilder("plugin\tregistered_items\tsampled_ids\n");
        for (Map.Entry<String, List<SlimefunItem>> entry : groups.entrySet()) {
            List<SlimefunItem> items = entry.getValue();
            items.sort(Comparator.comparing(SlimefunItem::getId));
            List<SlimefunItem> selected = new ArrayList<>(items.subList(0, Math.min(3, items.size())));
            if (entry.getKey().equals("Slimefun")) {
                for (String id : List.of("IRON_DUST", "SMALL_BACKPACK", "BATTERY", "ELECTRIC_MOTOR")) {
                    SlimefunItem extra = SlimefunItem.getById(id);
                    if (extra != null && !extra.isDisabled() && !selected.contains(extra)) selected.add(extra);
                }
            }
            List<String> ids = new ArrayList<>();
            for (SlimefunItem item : selected) {
                // Normalize a copied template through the real server codec, never alter its registry template.
                ItemStack sample = ItemStack.deserializeBytes(new ItemStack(item.getItem()).serializeAsBytes());
                sample.setAmount(Math.min(sample.getMaxStackSize(), samples.size() % 7 + 1));
                var meta = sample.getItemMeta();
                meta.getPersistentDataContainer().set(new NamespacedKey("sflupgradefixture", "opaque"),
                        PersistentDataType.STRING, "preserve:" + item.getId() + ":" + samples.size());
                meta.getPersistentDataContainer().set(new NamespacedKey("sflupgradefixture", "counter"),
                        PersistentDataType.INTEGER, 7919 + samples.size());
                sample.setItemMeta(meta);
                SlimefunItem identified = SlimefunItem.getByItem(sample);
                require(identified != null && item.getId().equals(identified.getId()),
                        "Sample does not resolve to its registered ID: " + item.getId());
                samples.add(sample);
                ids.add(item.getId());
            }
            coverage.append(entry.getKey()).append('\t').append(items.size()).append('\t')
                    .append(String.join(",", ids)).append('\n');
        }
        require(samples.size() >= 30 && samples.size() <= 512, "Unexpected fixture sample count");
        expected.setProperty("sampled.groups", Integer.toString(groups.size()));
        expected.setProperty("sampled.registered", Integer.toString(samples.size()));

        // A real nested backpack identity, not a mock inventory or a replacement guide template.
        PlayerBackpack child = controller().createBackpack(owner, "Fixture child", profile.nextBackpackNum(), 27);
        child.getInventory().setItem(0, samples.get(0).clone());
        child.getInventory().setItem(26, samples.get(samples.size() - 1).clone());
        heldBackpacks.add(child);
        SlimefunItem backpackItem = SlimefunItem.getById("SMALL_BACKPACK");
        require(backpackItem != null, "SMALL_BACKPACK fixture carrier is missing");
        ItemStack childCarrier = new ItemStack(backpackItem.getItem());
        PlayerBackpack.bindItem(childCarrier, child);
        require(PlayerBackpack.getBackpackUUID(childCarrier.getItemMeta()).orElse("")
                .equals(child.getUniqueId().toString()), "Child carrier is not bound to stored backpack UUID");
        samples.add(childCarrier);

        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta boxMeta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) boxMeta.getBlockState();
        state.getInventory().setItem(0, samples.get(0).clone());
        state.getInventory().setItem(13, samples.get(1).clone());
        state.getInventory().setItem(26, childCarrier.clone());
        boxMeta.setBlockState(state);
        box.setItemMeta(boxMeta);
        samples.add(box);

        int offset = 0;
        while (offset < samples.size()) {
            PlayerBackpack backpack = controller().createBackpack(owner, "Fixture page " + heldBackpacks.size(),
                    profile.nextBackpackNum(), 54);
            for (int slot = 0; slot < 50 && offset < samples.size(); slot++, offset++) {
                backpack.getInventory().setItem(slot, samples.get(offset).clone());
            }
            heldBackpacks.add(backpack);
        }
        controller().saveProfileBackpackCount(profile);
        expected.setProperty("profile.backpacks", Integer.toString(profile.getBackpackCount()));
        expected.setProperty("packs.count", Integer.toString(heldBackpacks.size()));
        for (int index = 0; index < heldBackpacks.size(); index++) {
            PlayerBackpack pack = heldBackpacks.get(index);
            String prefix = "pack." + index + ".";
            expected.setProperty(prefix + "uuid", pack.getUniqueId().toString());
            expected.setProperty(prefix + "id", Integer.toString(pack.getId()));
            expected.setProperty(prefix + "name", pack.getName());
            expected.setProperty(prefix + "size", Integer.toString(pack.getSize()));
            snapshot(expected, prefix, pack.getInventory());
        }
        int barrels = (samples.size() + 26) / 27;
        expected.setProperty("barrels.count", Integer.toString(barrels));
        for (int index = 0; index < barrels; index++) {
            Barrel barrel = barrel(index, true);
            for (int slot = 0; slot < 27 && index * 27 + slot < samples.size(); slot++) {
                barrel.getInventory().setItem(slot, samples.get(index * 27 + slot).clone());
            }
            snapshot(expected, "barrel." + index + ".", barrel.getInventory());
        }
        Files.writeString(Path.of("fixture-coverage.tsv"), coverage, StandardCharsets.UTF_8);
        try (OutputStream out = Files.newOutputStream(expectedFile)) { expected.store(out, "Synthetic 4.1.69 fixture; never owner data"); }
        persistAndFinish(expected);
    }

    private void loadAndVerify(PlayerProfile profile) throws Exception {
        Properties expected = new Properties();
        try (InputStream in = Files.newInputStream(expectedFile)) { expected.load(in); }
        require(profile.getBackpackCount() == number(expected, "profile.backpacks"), "Profile backpack count changed");
        List<CompletableFuture<PlayerBackpack>> loads = new ArrayList<>();
        for (int i = 0; i < number(expected, "packs.count"); i++) {
            loads.add(controller().getBackpackAsync(expected.getProperty("pack." + i + ".uuid")));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).orTimeout(60, TimeUnit.SECONDS)
                .whenComplete((ignored, failure) -> onMain(() -> {
                    if (failure != null) throw new IllegalStateException("Backpack load failed", failure);
                    for (int i = 0; i < loads.size(); i++) {
                        PlayerBackpack pack = loads.get(i).join();
                        String prefix = "pack." + i + ".";
                        require(pack != null, "Backpack vanished: " + prefix);
                        require(pack.getUniqueId().toString().equals(expected.getProperty(prefix + "uuid")), "Backpack UUID changed");
                        require(pack.getOwner().getUniqueId().equals(OWNER), "Backpack owner changed");
                        require(pack.getId() == number(expected, prefix + "id"), "Backpack numeric identity changed");
                        require(pack.getSize() == number(expected, prefix + "size"), "Backpack capacity changed");
                        require(pack.getName().equals(expected.getProperty(prefix + "name")), "Backpack name changed");
                        heldBackpacks.add(pack);
                        verifyInventory(expected, prefix, pack.getInventory());
                    }
                    for (int i = 0; i < number(expected, "barrels.count"); i++) {
                        verifyInventory(expected, "barrel." + i + ".", barrel(i, false).getInventory());
                    }
                    persistAndFinish(expected);
                }));
    }

    private void snapshot(Properties props, String prefix, Inventory inventory) {
        props.setProperty(prefix + "slots", Integer.toString(inventory.getSize()));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.isEmpty()) {
                props.setProperty(prefix + "slot." + slot, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                SlimefunItem identity = SlimefunItem.getByItem(item);
                if (identity != null) props.setProperty(prefix + "identity." + slot, identity.getId());
            }
        }
    }

    private void verifyInventory(Properties expected, String prefix, Inventory inventory) {
        require(inventory.getSize() == number(expected, prefix + "slots"), "Inventory size changed: " + prefix);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            checkedSlots++;
            String encoded = expected.getProperty(prefix + "slot." + slot);
            ItemStack actual = inventory.getItem(slot);
            if (encoded == null) {
                require(actual == null || actual.isEmpty(), "Unexpected item in originally empty slot: " + prefix + slot);
                continue;
            }
            require(actual != null && !actual.isEmpty(), "Lost stored item: " + prefix + slot);
            ItemStack old = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
            ItemStack comparable = actual.clone();
            if (phase.equals("restart")) {
                var meta = comparable.getItemMeta();
                require(Integer.valueOf(1).equals(meta.getPersistentDataContainer().get(ROUNDTRIP, PersistentDataType.INTEGER)),
                        "Candidate re-save did not survive restart: " + prefix + slot);
                meta.getPersistentDataContainer().remove(ROUNDTRIP);
                comparable.setItemMeta(meta);
            }
            require(old.equals(comparable), "Item amount/components changed: " + prefix + slot + " type=" + old.getType());
            String originalId = expected.getProperty(prefix + "identity." + slot);
            SlimefunItem actualIdentity = SlimefunItem.getByItem(comparable);
            if (originalId != null) {
                require(SlimefunItem.getById(originalId) != null && actualIdentity != null
                                && originalId.equals(actualIdentity.getId()),
                        "Stored Slimefun identity disappeared or changed: " + prefix + slot);
            }
            // Preserve the baseline snapshot; the candidate must really write and reload a marked state.
            if (phase.equals("upgrade")) {
                ItemStack changed = actual.clone();
                var meta = changed.getItemMeta();
                meta.getPersistentDataContainer().set(ROUNDTRIP, PersistentDataType.INTEGER, 1);
                changed.setItemMeta(meta);
                inventory.setItem(slot, changed);
            }
            checkedItems++;
        }
    }

    private void persistAndFinish(Properties expected) {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (PlayerBackpack pack : heldBackpacks) saves.add(controller().saveBackpackInventoryAsync(pack));
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).orTimeout(60, TimeUnit.SECONDS)
                .whenComplete((ignored, failure) -> onMain(() -> {
                    if (failure != null) throw new IllegalStateException("Acknowledged backpack persistence failed", failure);
                    guard();
                    Bukkit.getWorld(WORLD).save();
                    String result = "status=PASS\nphase=" + phase + "\ncore=" + Slimefun.getVersion()
                            + "\naddons_enabled=" + expectedPluginCount
                            + "\nsampled_groups=" + expected.getProperty("sampled.groups")
                            + "\nsampled_registered_items=" + expected.getProperty("sampled.registered")
                            + "\nbackpacks=" + heldBackpacks.size() + "\nbarrels=" + expected.getProperty("barrels.count")
                            + "\nchecked_slots=" + checkedSlots + "\nchecked_items=" + checkedItems + "\n";
                    Files.writeString(Path.of("fixture-" + phase + "-result.txt"), result, StandardCharsets.UTF_8);
                    getLogger().info("SFL_UPGRADE_FIXTURE_PASS phase=" + phase);
                }));
    }

    private Barrel barrel(int index, boolean create) {
        World world = Bukkit.getWorld(WORLD);
        require(world != null, "Fixture world missing");
        var block = world.getBlockAt(index * 3, 80, 0);
        block.getChunk().load();
        if (create) {
            require(block.getType().isAir(), "Refusing to replace an existing block");
            block.setType(Material.BARREL, false);
        }
        require(block.getState() instanceof Barrel, "Stored fixture barrel missing: " + index);
        return (Barrel) block.getState();
    }

    private ProfileDataController controller() { return Slimefun.getDatabaseManager().getProfileDataController(); }
    private static int number(Properties props, String key) { return Integer.parseInt(props.getProperty(key)); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private void onMain(CheckedRunnable action) {
        Bukkit.getScheduler().runTask(this, () -> { try { action.run(); } catch (Throwable e) { fail(e); } });
    }
    private void fail(Throwable failure) {
        getLogger().log(Level.SEVERE, "SFL_UPGRADE_FIXTURE_FAIL phase=" + phase, failure);
        try {
            Files.writeString(Path.of("fixture-" + phase + "-result.txt"),
                    "status=FAIL\nphase=" + phase + "\nerror=" + failure.getClass().getName() + "\n");
        } catch (Exception writeFailure) { getLogger().log(Level.SEVERE, "Could not persist failure report", writeFailure); }
    }
    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
}
