package audit;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.barrel.FluffyBarrel;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.ncbpfluffybear.fluffymachines.items.Barrel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Disposable Paper-only native integration check. Runs the actual registered Fluffy
 * ticker and Networks FluffyBarrel storage adapter; no mock of deposit or withdrawal.
 * This is test-only and is never included in any released addon or core JAR.
 */
public final class R135FluffyNetworksProbe extends JavaPlugin {
    private static final int[] REAL_SLOTS = {19, 20, 24, 25};
    private Chunk heldChunk;

    @Override
    public void onEnable() {
        getLogger().info("R135 native integration probe ready");
    }

    @Override
    public void onDisable() {
        if (heldChunk != null) {
            heldChunk.removePluginChunkTicket(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !"run".equals(args[0])) {
            sender.sendMessage("Usage: /r135probe run");
            return true;
        }
        try {
            runNativeCase();
            publish("PASS", "12 withdrawn; 9 redeposited; 14 withdrawn; rich PDC and counts conserved");
            getLogger().info("R135_INTEGRATION_RESULT PASS");
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE, "R135 native integration failed", failure);
            publish("FAIL", failure.toString());
            getLogger().severe("R135_INTEGRATION_RESULT FAIL");
        }
        return true;
    }

    private void runNativeCase() throws Exception {
        requireVersion("Slimefun", System.getProperty("albion.probe.slimefun.version"));
        requireVersion("Networks", System.getProperty("albion.probe.networks.version"));
        requireVersion("FluffyMachines", System.getProperty("albion.probe.fluffy.version"));

        if (Boolean.getBoolean("albion.probe.paxel")) {
            verifyPaxelRecipeSelection();
        }

        World world = Bukkit.getWorlds().getFirst();
        Location location = new Location(world, 4096, 72, 4096);
        heldChunk = world.getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        heldChunk.addPluginChunkTicket(this);

        BlockDataController blocks = Slimefun.getDatabaseManager().getBlockDataController();
        blocks.getChunkData(heldChunk);
        SlimefunItem slimefunItem = SlimefunItem.getById("SMALL_FLUFFY_BARREL");
        check(slimefunItem instanceof Barrel, "The real SMALL_FLUFFY_BARREL must be registered");
        Barrel barrel = (Barrel) slimefunItem;
        check(slimefunItem.getBlockTicker() != null
                && slimefunItem.getBlockTicker().isSynchronized(), "Barrel ticker must be synchronous");
        Slimefun.getTickerTask().pauseItemTicker("SMALL_FLUFFY_BARREL");

        Block block = location.getBlock();
        block.setType(slimefunItem.getItem().getType(), false);
        SlimefunBlockData data = blocks.createBlock(location, slimefunItem.getId());
        BlockMenu menu = data.getBlockMenu();
        check(menu != null && data.isDataLoaded(), "Native Slimefun Barrel menu did not load");
        // createBlock() is a programmatic test fixture, not a player placement event.
        // Real empty barrels have this persisted key initialized by the menu build callback.
        barrel.setStored(block, 0);

        ItemStack identity = rich(Material.DIAMOND, 17, "r135-original");
        menu.replaceExistingItem(19, identity.clone());
        tick(barrel, slimefunItem, block, data);
        check(quantity(barrel, block, menu) == 17L, "First barrel ticker lost or created diamonds");
        FluffyBarrel adapter = NetworkRoot.getFluffyBarrel(menu, barrel, false);
        check(adapter != null, "Networks did not discover a filled Fluffy Barrel");
        check(adapter.getAmount() == 17L, "Networks reported an incorrect initial barrel count");
        check(adapter.getItemStack() != null && adapter.getItemStack().isSimilar(identity),
                "Network discovery changed custom item identity");

        ItemStack wrongTemplate = rich(Material.DIAMOND, 1, "other-owner");
        check(adapter.requestItem(new ItemRequest(wrongTemplate, 4)) == null,
                "A different PDC owner was permitted to withdraw the barrel's items");
        for (int invalid : new int[] {0, -1, Integer.MIN_VALUE}) {
            check(adapter.requestItem(new ItemRequest(identity, invalid)) == null,
                    "Invalid network withdrawal request changed real items: " + invalid);
        }
        check(quantity(barrel, block, menu) == 17L, "Rejected withdrawals changed source inventory");

        ItemStack withdrawn = adapter.requestItem(new ItemRequest(identity, 12));
        check(withdrawn != null && withdrawn.getAmount() == 12 && withdrawn.isSimilar(identity),
                "Networks withdrawal must return exactly 12 diamonds with original PDC");
        check(quantity(barrel, block, menu) == 5L, "First withdrawal must leave exactly five diamonds");

        ItemStack deposit = rich(Material.DIAMOND, 9, "r135-original");
        adapter.depositItemStack(new ItemStack[] {deposit});
        check(deposit.getAmount() == 0, "Network adapter did not consume exactly nine offered items");
        check(quantity(barrel, block, menu) == 14L,
                "After deposit, physical slots and internal reserve must total exactly 14");
        tick(barrel, slimefunItem, block, data);
        check(quantity(barrel, block, menu) == 14L, "Barrel output refill changed item quantity");

        FluffyBarrel refreshed = NetworkRoot.getFluffyBarrel(menu, barrel, false);
        check(refreshed != null && refreshed.getAmount() == 14L,
                "Networks discovery duplicated or hid Fluffy reserve plus output");
        ItemStack last = refreshed.requestItem(new ItemRequest(identity, 64));
        check(last != null && last.getAmount() == 14 && last.isSimilar(identity),
                "Networks must return all 14 physical diamonds with original metadata");
        check(quantity(barrel, block, menu) == 0L, "Final withdrawal left phantom inventory");
        check(withdrawn.getAmount() + last.getAmount() == 17 + 9,
                "The entire network/barrel flow did not conserve 26 physical diamonds");

        ItemStack emerald = rich(Material.EMERALD, 6, "r135-new-type");
        menu.replaceExistingItem(19, emerald.clone());
        tick(barrel, slimefunItem, block, data);
        FluffyBarrel reassigned = NetworkRoot.getFluffyBarrel(menu, barrel, false);
        check(reassigned != null && reassigned.getAmount() == 6L,
                "After draining, Networks must discover a new item type");
        check(reassigned.getItemStack() != null && reassigned.getItemStack().isSimilar(emerald),
                "New type assignment lost PDC or display identity");
        check(reassigned.requestItem(new ItemRequest(identity, 6)) == null,
                "Old diamond template must not retrieve the new emerald contents");
        check(quantity(barrel, block, menu) == 6L, "Old item request changed reassigned storage");
        ItemStack retrievedEmerald = reassigned.requestItem(new ItemRequest(emerald, 64));
        check(retrievedEmerald != null && retrievedEmerald.getAmount() == 6
                && retrievedEmerald.isSimilar(emerald), "Reassigned emerald retrieval changed count or PDC");
        check(quantity(barrel, block, menu) == 0L, "Final emerald quantity was not conserved");
    }

    /** Native selection and consumption stages; no player interaction or guide clicks. */
    private void verifyPaxelRecipeSelection() throws Exception {
        var registeredMachine = SlimefunItem.getById("ENHANCED_CRAFTING_TABLE");
        var type = io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.EnhancedCraftingTable.class;
        check(type.isInstance(registeredMachine), "Registered Enhanced Crafting Table missing");
        var machine = type.cast(registeredMachine);
        var matcher = type.getDeclaredMethod("isCraftable", org.bukkit.inventory.Inventory.class, ItemStack[].class);
        matcher.setAccessible(true);
        var inputs = io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.getRecipeInputList(machine);
        for (String id : List.of("ADVENTURERS_PAXEL", "PAXEL")) {
            var item = SlimefunItem.getById(id);
            check(item != null, "Registered Paxel missing: " + id);
            check(item.getRecipeType() == io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.ENHANCED_CRAFTING_TABLE,
                    "Unexpected Paxel recipe type: " + id);
            ItemStack[] recipe = item.getRecipe();
            check(recipe.length == 9, "Paxel recipe must have nine slots: " + id);
            Material top = id.equals("ADVENTURERS_PAXEL") ? Material.DIAMOND_SHOVEL : Material.DIAMOND_PICKAXE;
            Material bottom = id.equals("ADVENTURERS_PAXEL") ? Material.DIAMOND_PICKAXE : Material.DIAMOND_SHOVEL;
            check(recipe[1].getType() == top && recipe[7].getType() == bottom,
                    "Registered Paxel tool positions differ from reviewed recipe: " + id);
            byte[] templateBefore = item.getItem().serializeAsBytes();
            byte[][] recipeBefore = new byte[9][];
            var inventory = Bukkit.createInventory(null, 9);
            for (int slot = 0; slot < 9; slot++) {
                check(recipe[slot] != null, "Missing Paxel ingredient: " + id + "/" + slot);
                recipeBefore[slot] = recipe[slot].serializeAsBytes();
                inventory.setItem(slot, recipe[slot].clone());
            }
            int matches = 0;
            for (ItemStack[] input : inputs) {
                if ((boolean) matcher.invoke(machine, inventory, input)) {
                    matches++;
                    var output = io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.getRecipeOutputList(machine, input);
                    check(SlimefunItem.getByItem(output) == item,
                            "Paxel pattern selected another registered output: " + id);
                    check(output.getAmount() == 1 && output.isSimilar(item.getItem()) && item.getItem().isSimilar(output),
                            "Paxel selected output identity or amount changed: " + id);
                }
            }
            check(matches == 1, "Expected exactly one registered matching recipe for " + id + ", got " + matches);
            for (int slot = 0; slot < 9; slot++) {
                check(java.util.Arrays.equals(recipeBefore[slot], recipe[slot].serializeAsBytes()),
                        "Recipe inspection changed registered ingredient: " + id);
                check(java.util.Arrays.equals(recipeBefore[slot], inventory.getItem(slot).serializeAsBytes()),
                        "Recipe matcher changed input inventory: " + id);
            }
            verifyPaxelInputConsumption(machine, recipe, id);
            for (int slot = 0; slot < 9; slot++) {
                check(java.util.Arrays.equals(recipeBefore[slot], recipe[slot].serializeAsBytes()),
                        "Consumption changed registered ingredient: " + id + "/" + slot);
            }
            check(java.util.Arrays.equals(templateBefore, item.getItem().serializeAsBytes()),
                    "Recipe inspection or consumption changed output template: " + id);
        }
        getLogger().info("PAXEL_NATIVE_RECIPE_SELECTION PASS: both registered patterns select unique intended outputs; templates unchanged");
    }

    /** Exercise the same consumption method used by craft, without claiming onInteract acceptance. */
    private void verifyPaxelInputConsumption(Object machine, ItemStack[] recipe, String id) throws Exception {
        var type = io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.EnhancedCraftingTable.class
                .getSuperclass();
        check(type.getSimpleName().equals("AbstractCraftingTable"), "Unexpected crafting superclass");
        var consume = type.getDeclaredMethod("consumeInputs", org.bukkit.inventory.Inventory.class, ItemStack[].class);
        consume.setAccessible(true);
        for (boolean surplus : List.of(false, true)) {
            var inventory = Bukkit.createInventory(null, 9);
            byte[][] expectedResidual = new byte[9][];
            int surplusSlots = 0;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack input = recipe[slot].clone();
                if (surplus && input.getAmount() < input.getMaxStackSize()) {
                    input.setAmount(input.getAmount() + 1);
                    ItemStack residual = recipe[slot].clone();
                    residual.setAmount(1);
                    expectedResidual[slot] = residual.serializeAsBytes();
                    surplusSlots++;
                }
                inventory.setItem(slot, input);
            }
            check(!surplus || surplusSlots > 0, "No stackable Paxel ingredients tested: " + id);
            consume.invoke(machine, inventory, recipe);
            for (int slot = 0; slot < 9; slot++) {
                ItemStack actual = inventory.getItem(slot);
                if (expectedResidual[slot] == null) {
                    check(actual == null || actual.getType().isAir(),
                            "Paxel ingredient not fully consumed: " + id + "/" + slot);
                } else {
                    check(actual != null && java.util.Arrays.equals(expectedResidual[slot], actual.serializeAsBytes()),
                            "Paxel surplus amount or native metadata changed: " + id + "/" + slot);
                }
            }
        }
        getLogger().info("PAXEL_NATIVE_INPUT_CONSUMPTION PASS: " + id + "; exact inputs and surplus checked");
    }

    private void requireVersion(String name, String expected) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        check(plugin != null && plugin.isEnabled(), name + " must be enabled in the disposable server");
        check(expected != null && expected.equals(plugin.getPluginMeta().getVersion()),
                name + " version is not " + expected);
    }

    private void tick(Barrel barrel, SlimefunItem item, Block block, SlimefunBlockData data) {
        item.getBlockTicker().tick(block, item, data);
    }

    private long quantity(Barrel barrel, Block block, BlockMenu menu) {
        long amount = barrel.getStored(block);
        for (int slot : REAL_SLOTS) {
            ItemStack stack = menu.getItemInSlot(slot);
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    private ItemStack rich(Material material, int count, String owner) {
        ItemStack item = new ItemStack(material, count);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("R135 inventory identity"));
        meta.lore(List.of(Component.text("Do not convert PDC items")));
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "owner"),
                PersistentDataType.STRING, owner);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "wide"),
                PersistentDataType.LONG, 9_007_199_254_741_111L);
        item.setItemMeta(meta);
        return item;
    }

    private static void check(boolean condition, String explanation) {
        if (!condition) {
            throw new IllegalStateException(explanation);
        }
    }

    private void publish(String status, String explanation) {
        try {
            Path path = getDataFolder().toPath();
            Files.createDirectories(path);
            Files.writeString(path.resolve("result.txt"),
                    status + System.lineSeparator() + explanation + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Could not save R135 native integration evidence", failure);
        }
    }
}
