package io.github.wickidcow.validation;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.ncbpfluffybear.fluffymachines.machines.AutoEnhancedCraftingTable;
import io.ncbpfluffybear.fluffymachines.objects.AutoCrafter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real native items, menu and database. Ticker invocation is controlled; no client simulation. */
public final class CrafterProbe extends JavaPlugin {
    private AutoCrafter crafter;
    private SlimefunBlockData data;
    private BlockMenu menu;
    private Location location;
    private Method tick;
    private boolean updated;
    private int assertions;
    private final NamespacedKey owner = new NamespacedKey("fixture", "owner");
    private final NamespacedKey count = new NamespacedKey("fixture", "exact_long");

    @Override public void onEnable() {
        crafter = Slimefun.getRegistry().getAllSlimefunItems().stream()
            .filter(AutoEnhancedCraftingTable.class::isInstance)
            .map(AutoCrafter.class::cast).findFirst().orElseThrow();
        // Pause only this fixture's machine type. All operations below explicitly
        // call the actual production ticker, avoiding uncontrolled duplicate ticks.
        Slimefun.getTickerTask().pauseItemTicker(crafter.getId());
        getServer().getScheduler().runTaskLater(this, () -> {
            try { exercise(); }
            catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "CRAFTER_NATIVE_FAIL", failure);
            }
        }, 60L);
    }

    private void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private void runTick() throws Exception { tick.invoke(crafter, location.getBlock()); }
    private int amount(int slot) {
        ItemStack item = menu.getItemInSlot(slot);
        return item == null || item.getType().isAir() ? 0 : item.getAmount();
    }
    private void reset() {
        for (int slot : crafter.getInputSlots()) menu.replaceExistingItem(slot, null);
        for (int slot : crafter.getOutputSlots()) menu.replaceExistingItem(slot, null);
        data.setData("enabled", "true");
        data.setData("single-craft-ready", "true");
        crafter.setCharge(location, 384);
    }
    private ItemStack output() {
        ItemStack result = new ItemStack(Material.EMERALD);
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(owner, PersistentDataType.STRING, "retained-owner");
        meta.getPersistentDataContainer().set(count, PersistentDataType.LONG, 9007199254740993L);
        result.setItemMeta(meta);
        return result;
    }
    private ItemStack[] input(boolean pair) {
        ItemStack[] grid = new ItemStack[9];
        grid[0] = new ItemStack(Material.DIAMOND);
        if (pair) grid[1] = new ItemStack(Material.IRON_INGOT);
        return grid;
    }
    private Map<?, ?> cached() throws Exception {
        Field f = AutoCrafter.class.getDeclaredField("recipeCache"); f.setAccessible(true);
        Object cache = f.get(crafter);
        if (cache instanceof Map<?, ?> old) return old;
        Field g = cache.getClass().getDeclaredField("generation"); g.setAccessible(true);
        Object generation = g.get(cache);
        if (generation == null) return Map.of();
        Field r = generation.getClass().getDeclaredField("results"); r.setAccessible(true);
        return (Map<?, ?>) r.get(generation);
    }

    private void exercise() throws Exception {
        String version = getServer().getPluginManager().getPlugin("FluffyMachines").getPluginMeta().getVersion();
        updated = version.equals("26.2.14");
        check(updated || version.equals("26.2.13"), "known original or candidate addon");
        tick = AutoCrafter.class.getDeclaredMethod("tick", Block.class); tick.setAccessible(true);
        Path root = getDataFolder().toPath(); Files.createDirectories(root);
        boolean restart = Files.exists(root.resolve("first-pass.txt"));
        location = new Location(getServer().getWorlds().getFirst(), 8, 100, 8);
        location.getChunk().load(true);
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        controller.loadChunk(location.getChunk(), true);
        data = controller.getBlockData(location);
        if (restart) {
            check(data != null, "block record survives restart");
            if (!data.isDataLoaded()) controller.loadBlockData(data);
            menu = data.getBlockMenu();
            check(menu != null, "native menu survives restart");
            check("false".equals(data.getData("enabled")), "disabled state retained");
            check("false".equals(data.getData("single-craft-ready")), "template arming state retained");
            check("machine-owner".equals(data.getData("fixture-owner")), "unrelated owner key retained");
            check(crafter.getCharge(location) == 256, "charge retained across restart");
            check(ItemStack.deserializeBytes(Files.readAllBytes(root.resolve("input.dat"))).equals(menu.getItemInSlot(19)), "exact input survives restart");
            check(ItemStack.deserializeBytes(Files.readAllBytes(root.resolve("output.dat"))).equals(menu.getItemInSlot(33)), "exact typed output survives restart");
        } else {
            check(data == null, "fresh disposable location");
            location.getBlock().setType(Material.CRAFTING_TABLE);
            data = controller.createBlock(location, crafter.getId());
            menu = data.getBlockMenu();
        }
        check(menu != null, "actual registered menu");
        BlockMenuPreset.getPreset(crafter.getId()).newInstance(menu, location.getBlock());
        var machine = (MultiBlockMachine) RecipeType.ENHANCED_CRAFTING_TABLE.getMachine();
        var recipes = machine.getRecipes();
        var original = new ArrayList<>(recipes);
        try {
            recipes.clear();
            reset();
            menu.replaceExistingItem(19, new ItemStack(Material.DIAMOND, 3));
            runTick();
            check(amount(19) == 3 && amount(33) == 0, "unregistered recipe consumes nothing");
            check(crafter.getCharge(location) == 384, "unregistered recipe spends no power");
            machine.addRecipe(input(false), output());
            runTick();
            check(amount(33) == (updated ? 1 : 0), "late-registration old-code control");
            check(amount(19) == (updated ? 2 : 3), "late-registration ingredient accounting");
            check(crafter.getCharge(location) == (updated ? 256 : 384), "late-registration energy accounting");
            getLogger().info("CRAFTER_OBSERVATION late-added-output=" + amount(33));

            // Force both original and corrected implementations to resolve a
            // positive cache using a changed grid, before removing its recipe.
            menu.replaceExistingItem(19, new ItemStack(Material.STONE, 3));
            runTick();
            reset();
            menu.replaceExistingItem(19, new ItemStack(Material.DIAMOND, 3));
            runTick();
            check(amount(33) == 1 && amount(19) == 2, "normal buffered crafting still works");
            check(output().isSimilar(menu.getItemInSlot(33)), "native output metadata retained");
            recipes.clear();
            runTick();
            check(amount(33) == (updated ? 1 : 2), "removed-recipe old-code control");
            check(amount(19) == (updated ? 2 : 1), "removed-recipe input accounting");
            check(crafter.getCharge(location) == (updated ? 256 : 128), "removed-recipe energy accounting");
            getLogger().info("CRAFTER_OBSERVATION after-removal-output=" + amount(33));

            reset();
            // Make the changed provider size observable to both versions.
            menu.replaceExistingItem(19, new ItemStack(Material.STONE, 3));
            runTick();
            machine.addRecipe(input(true), output());
            reset();
            menu.replaceExistingItem(19, new ItemStack(Material.DIAMOND, 2));
            menu.replaceExistingItem(20, new ItemStack(Material.IRON_INGOT, 2));
            runTick();
            check(amount(33) == 1 && amount(19) == 1 && amount(20) == 1, "buffered craft retains one template per input");
            check(crafter.getCharge(location) == 256, "buffered craft costs exactly 128");
            runTick();
            check(amount(33) == 1 && amount(19) == 1 && amount(20) == 1, "unarmed template is not consumed");
            check(crafter.getCharge(location) == 256, "waiting template spends no energy");
            menu.replaceExistingItem(19, new ItemStack(Material.DIAMOND, 2));
            runTick();
            check(amount(33) == 1 && amount(19) == 2 && amount(20) == 1, "partial refill waits");
            menu.replaceExistingItem(20, new ItemStack(Material.IRON_INGOT, 2));
            runTick();
            check(amount(33) == 2 && amount(19) == 1 && amount(20) == 1, "complete refill resumes immediately");
            check(crafter.getCharge(location) == 128, "refill craft costs exactly 128");
            data.setData("single-craft-ready", "true");
            runTick();
            check(amount(19) == 0 && amount(20) == 0 && amount(33) == 3, "explicitly rearmed one-shot craft retained");
            check(crafter.getCharge(location) == 0, "one-shot craft costs exactly 128");
            runTick();
            check("true".equals(data.getData("single-craft-ready")), "empty unpowered grid rearms");
            check(cached().containsKey(data.getKey()) != updated, "empty-grid cache old-code control");

            reset();
            menu.replaceExistingItem(19, new ItemStack(Material.DIAMOND, 3));
            menu.replaceExistingItem(20, new ItemStack(Material.IRON_INGOT, 3));
            menu.replaceExistingItem(33, new ItemStack(Material.STONE, 64));
            menu.replaceExistingItem(34, new ItemStack(Material.STONE, 64));
            runTick();
            check(amount(19) == 3 && amount(20) == 3 && crafter.getCharge(location) == 384, "blocked output consumes no inputs or energy");
            reset();
            menu.replaceExistingItem(19, new ItemStack(Material.DIAMOND, 3));
            menu.replaceExistingItem(20, new ItemStack(Material.IRON_INGOT, 3));
            data.setData("enabled", "false");
            runTick();
            check(amount(33) == 0 && amount(19) == 3 && crafter.getCharge(location) == 384, "disabled crafter unchanged");
            data.setData("enabled", "true");
            runTick();
            check(amount(33) == 1 && amount(19) == 2 && amount(20) == 2, "craft after reenable");
            check(crafter.getCharge(location) == 256, "final exact charge");
            check(Long.valueOf(9007199254740993L).equals(menu.getItemInSlot(33).getItemMeta().getPersistentDataContainer().get(count, PersistentDataType.LONG)), "exact LONG on crafted item");
            data.setData("enabled", "false");
            data.setData("fixture-owner", "machine-owner");
            if (!restart) {
                Files.write(root.resolve("input.dat"), menu.getItemInSlot(19).serializeAsBytes());
                Files.write(root.resolve("output.dat"), menu.getItemInSlot(33).serializeAsBytes());
            }
        } finally {
            recipes.clear(); recipes.addAll(original);
            data.setData("enabled", "false");
        }
        String pass = "CRAFTER_NATIVE_PASS mode=" + (updated ? "updated" : "baseline") + " phase=" + (restart ? "restart" : "initial") + " assertions=" + assertions;
        Files.writeString(root.resolve(restart ? "restart-pass.txt" : "first-pass.txt"), pass + "\n");
        getLogger().info(pass);
    }
}
