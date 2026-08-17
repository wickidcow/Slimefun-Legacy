package me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemState;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.MatchContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineProcessHolder;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

// TODO: Replace this with "AbstractContainer" and "AbstractElectricalMachine" classes.
public abstract class AContainer extends SlimefunItem
        implements InventoryBlock, EnergyNetComponent, MachineProcessHolder<CraftingOperation> {

    private static final int[] BORDER = {0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 31, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int[] BORDER_IN = {9, 10, 11, 12, 18, 21, 27, 28, 29, 30};
    private static final int[] BORDER_OUT = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

    protected final List<MachineRecipe> recipes = new ArrayList<>();

    private final MachineProcessor<CraftingOperation> processor = new MachineProcessor<>(this);
    private final ThreadLocal<TickContext> tickContext = new ThreadLocal<>();

    private int energyConsumedPerTick = -1;
    private int energyCapacity = -1;
    private int processingSpeed = -1;

    private record TickContext(Location location, SlimefunBlockData data) {}

    @ParametersAreNonnullByDefault
    protected AContainer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        processor.setProgressBar(getProgressBar());
        createPreset(this, getInventoryTitle(), this::constructMenu);
        addItemHandler(onBlockBreak());
    }

    @Nonnull
    protected BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());
                if (inv != null) {
                    inv.dropItems(b.getLocation(), getInputSlots());
                    inv.dropItems(b.getLocation(), getOutputSlots());
                }
                processor.endOperation(b);
            }
        };
    }

    @ParametersAreNonnullByDefault
    protected AContainer(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            ItemStack recipeOutput) {
        this(itemGroup, item, recipeType, recipe);
        this.recipeOutput = recipeOutput;
    }

    @Override
    public MachineProcessor<CraftingOperation> getMachineProcessor() {
        return processor;
    }

    protected void constructMenu(BlockMenuPreset preset) {
        for (int i : BORDER) {
            preset.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
        for (int i : BORDER_IN) {
            preset.addItem(i, ChestMenuUtils.getInputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }
        for (int i : BORDER_OUT) {
            preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }

        preset.addItem(
                22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "), ChestMenuUtils.getEmptyClickHandler());

        for (int i : getOutputSlots()) {
            preset.addMenuClickHandler(i, new AdvancedMenuClickHandler() {
                @Override
                public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
                    return false;
                }

                @Override
                public boolean onClick(
                        InventoryClickEvent e, Player p, int slot, ItemStack cursor, ClickAction action) {
                    return cursor == null || cursor.getType() == null || cursor.getType() == Material.AIR;
                }
            });
        }
    }

    @Nonnull
    public String getInventoryTitle() {
        return getItemName();
    }

    public abstract ItemStack getProgressBar();

    @Override
    public int getCapacity() {
        return energyCapacity;
    }

    public int getEnergyConsumption() {
        return energyConsumedPerTick;
    }

    public int getSpeed() {
        return processingSpeed;
    }

    public final AContainer setCapacity(int capacity) {
        Validate.isTrue(capacity > 0, "The capacity must be greater than zero!");
        if (getState() == ItemState.UNREGISTERED) {
            this.energyCapacity = capacity;
            return this;
        }
        throw new IllegalStateException("You cannot modify the capacity after the Item was registered.");
    }

    public final AContainer setProcessingSpeed(int speed) {
        Validate.isTrue(speed > 0, "The speed must be greater than zero!");
        this.processingSpeed = speed;
        return this;
    }

    public final AContainer setEnergyConsumption(int energyConsumption) {
        Validate.isTrue(energyConsumption > 0, "The energy consumption must be greater than zero!");
        Validate.isTrue(energyCapacity > 0, "You must specify the capacity before you can set the consumption amount.");
        Validate.isTrue(
                energyConsumption <= energyCapacity,
                "The energy consumption cannot be higher than the capacity (" + energyCapacity + ')');
        this.energyConsumedPerTick = energyConsumption;
        return this;
    }

    @Override
    public void register(@Nonnull SlimefunAddon addon) {
        this.addon = addon;
        if (getCapacity() <= 0) {
            warn("The capacity has not been configured correctly. The Item was disabled.");
            warn("Make sure to call '" + getClass().getSimpleName() + "#setEnergyCapacity(...)' before registering!");
        }
        if (getEnergyConsumption() <= 0) {
            warn("The energy consumption has not been configured correctly. The Item was disabled.");
            warn("Make sure to call '"
                    + getClass().getSimpleName()
                    + "#setEnergyConsumption(...)' before registering!");
        }
        if (getSpeed() <= 0) {
            warn("The processing speed has not been configured correctly. The Item was disabled.");
            warn("Make sure to call '" + getClass().getSimpleName() + "#setProcessingSpeed(...)' before registering!");
        }
        if (getCapacity() > 0 && getEnergyConsumption() > 0 && getSpeed() > 0) {
            super.register(addon);
        }
    }

    @Nonnull
    public abstract String getMachineIdentifier();

    protected void registerDefaultRecipes() {}

    public List<MachineRecipe> getMachineRecipes() {
        return recipes;
    }

    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> displayRecipes = new ArrayList<>(recipes.size() * 2);
        for (MachineRecipe recipe : recipes) {
            if (recipe.getInput().length != 1) {
                continue;
            }
            displayRecipes.add(recipe.getInput()[0]);
            displayRecipes.add(recipe.getOutput()[0]);
        }
        return displayRecipes;
    }

    @Override
    public int[] getInputSlots() {
        return new int[] {19, 20};
    }

    @Override
    public int[] getOutputSlots() {
        return new int[] {24, 25};
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    public void registerRecipe(MachineRecipe recipe) {
        recipe.setTicks(Math.max(1, recipe.getTicks() / getSpeed()));
        recipes.add(recipe);
    }

    public void registerRecipe(int seconds, ItemStack[] input, ItemStack[] output) {
        registerRecipe(new MachineRecipe(seconds, input, output));
    }

    public void registerRecipe(int seconds, ItemStack input, ItemStack output) {
        registerRecipe(new MachineRecipe(seconds, new ItemStack[] {input}, new ItemStack[] {output}));
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {
            @Override
            public void tick(Block b, SlimefunItem sf, SlimefunBlockData data) {
                TickContext previous = tickContext.get();
                tickContext.set(new TickContext(b.getLocation(), data));
                try {
                    AContainer.this.tick(b);
                } finally {
                    if (previous == null) {
                        tickContext.remove();
                    } else {
                        tickContext.set(previous);
                    }
                }
            }

            @Override
            public boolean isSynchronized() {
                return false;
            }
        });
    }

    protected void tick(Block b) {
        BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());
        if (inv == null) {
            return;
        }

        CraftingOperation currentOperation = processor.getOperation(b);
        if (currentOperation != null) {
            if (!currentOperation.isFinished()) {
                if (!canProgressOperation(inv, currentOperation)) {
                    return;
                }
                if (takeCharge(b.getLocation())) {
                    processor.updateProgressBar(inv, 22, currentOperation);
                    currentOperation.addProgress(1);
                }
                return;
            }

            ItemStack[] results = currentOperation.getResults();
            if (!Slimefun.getItemStackService()
                    .fitAll(inv.toInventory(), results, InventoryContext.MACHINE_OUTPUT, getOutputSlots())) {
                return;
            }
            if (!commitOperationInputs(inv, currentOperation)) {
                return;
            }

            for (ItemStack output : results) {
                ItemStack remainder = inv.pushItem(output.clone(), getOutputSlots());
                if (remainder != null) {
                    ItemStack overflow = remainder.clone();
                    Location overflowLocation = b.getLocation();
                    Slimefun.runSyncAt(
                            overflowLocation,
                            () -> overflowLocation.getWorld().dropItemNaturally(overflowLocation, overflow));
                }
            }

            inv.replaceExistingItem(22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "));
            processor.endOperation(b);
            return;
        }

        MachineRecipe next = findNextRecipe(inv);
        if (next != null) {
            currentOperation = new CraftingOperation(next);
            processor.startOperation(b, currentOperation);
            processor.updateProgressBar(inv, 22, currentOperation);
        }
    }

    /**
     * Hook for machines that keep their operation inputs in inventory while processing.
     * Returning false pauses progress without consuming energy.
     */
    @ParametersAreNonnullByDefault
    protected boolean canProgressOperation(BlockMenu menu, CraftingOperation operation) {
        return true;
    }

    /**
     * Hook for machines that defer input consumption until their finished outputs are ready to commit.
     * Returning false keeps the completed operation pending without producing outputs.
     */
    @ParametersAreNonnullByDefault
    protected boolean commitOperationInputs(BlockMenu menu, CraftingOperation operation) {
        return true;
    }

    protected boolean takeCharge(@Nonnull Location l) {
        Validate.notNull(l, "Can't attempt to take charge from a null location!");
        if (!isChargeable()) {
            return true;
        }

        TickContext context = tickContext.get();
        if (context != null && context.location().equals(l)) {
            return takeCharge(l, context.data());
        }

        ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(l);
        if (data == null || data.isPendingRemove()) {
            return false;
        }
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return false;
        }
        return takeCharge(l, data);
    }

    private boolean takeCharge(@Nonnull Location l, @Nonnull ASlimefunDataContainer data) {
        if (data.isPendingRemove()) {
            return false;
        }
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return false;
        }

        long charge = getChargeLong(l, data);
        if (charge < getEnergyConsumption()) {
            return false;
        }
        setCharge(l, charge - getEnergyConsumption(), data);
        return true;
    }

    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        Map<Integer, ItemStack> inventory = new HashMap<>();
        for (int slot : getInputSlots()) {
            ItemStack item = inv.getItemInSlot(slot);
            if (item != null) {
                inventory.put(slot, ItemStackWrapper.wrap(item));
            }
        }

        Map<Integer, Integer> found = new HashMap<>();
        for (MachineRecipe recipe : recipes) {
            for (ItemStack input : recipe.getInput()) {
                for (int slot : getInputSlots()) {
                    if (found.containsKey(slot)) {
                        continue;
                    }
                    if (Slimefun.getItemStackService()
                            .isSimilar(inventory.get(slot), input, MatchContext.RECIPE_INPUT, true, true)) {
                        found.put(slot, input.getAmount());
                        break;
                    }
                }
            }

            if (found.size() == recipe.getInput().length) {
                if (!Slimefun.getItemStackService()
                        .fitAll(
                                inv.toInventory(),
                                recipe.getOutput(),
                                InventoryContext.MACHINE_OUTPUT,
                                getOutputSlots())) {
                    return null;
                }
                for (Map.Entry<Integer, Integer> entry : found.entrySet()) {
                    inv.consumeItem(entry.getKey(), entry.getValue());
                }
                return recipe;
            }
            found.clear();
        }
        return null;
    }

    @Override
    public void enable() {
        super.enable();
        registerDefaultRecipes();
    }

    @Override
    public void disable() {
        super.disable();
        recipes.clear();
    }

    @Override
    public void postRegister() {
        if (getState() == ItemState.ENABLED) {
            registerDefaultRecipes();
        }
    }
}
