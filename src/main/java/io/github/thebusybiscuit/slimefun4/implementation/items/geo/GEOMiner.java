package io.github.thebusybiscuit.slimefun4.implementation.items.geo;

import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunChunkData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemState;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineProcessHolder;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.rotations.NotDiagonallyRotatable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.GEOMiningOperation;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link GEOMiner} is an electrical machine that allows you to obtain a {@link GEOResource}.
 *
 * @author TheBusyBiscuit
 *
 * @see GEOResource
 */
public class GEOMiner extends SlimefunItem
        implements RecipeDisplayItem,
                EnergyNetComponent,
                InventoryBlock,
                HologramOwner,
                MachineProcessHolder<GEOMiningOperation>,
                NotDiagonallyRotatable {

    private static final int[] BORDER = {
        0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 26, 27, 35, 36, 44, 45, 53
    };
    private static final int[] OUTPUT_BORDER = {19, 20, 21, 22, 23, 24, 25, 28, 34, 37, 43, 46, 47, 48, 49, 50, 51, 52};
    private static final int[] OUTPUT_SLOTS = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};

    private static final int PROCESSING_TIME = 14;

    private final MachineProcessor<GEOMiningOperation> processor = new MachineProcessor<>(this);

    private int energyConsumedPerTick = -1;
    private int energyCapacity = -1;
    private int processingSpeed = -1;

    @ParametersAreNonnullByDefault
    public GEOMiner(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        processor.setProgressBar(new ItemStack(Material.DIAMOND_PICKAXE));
        createPreset(this, getItemName(), this::constructMenu);
        addItemHandler(onBlockPlace(), onBlockBreak());
    }

    @Override
    public @Nonnull MachineProcessor<GEOMiningOperation> getMachineProcessor() {
        return processor;
    }

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

    public final GEOMiner setCapacity(int capacity) {
        Validate.isTrue(capacity > 0, "The capacity must be greater than zero!");

        if (getState() == ItemState.UNREGISTERED) {
            this.energyCapacity = capacity;
            return this;
        } else {
            throw new IllegalStateException("You cannot modify the capacity after the Item was registered.");
        }
    }

    public final GEOMiner setProcessingSpeed(int speed) {
        Validate.isTrue(speed > 0, "The speed must be greater than zero!");
        this.processingSpeed = speed;
        return this;
    }

    public final GEOMiner setEnergyConsumption(int energyConsumption) {
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
    private BlockPlaceHandler onBlockPlace() {
        return new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                updateHologram(e.getBlock(), "&7Idle...");
            }
        };
    }

    @Nonnull
    private BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(@Nonnull Block b) {
                removeHologram(b);
                BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());

                if (inv != null) {
                    inv.dropItems(b.getLocation(), OUTPUT_SLOTS);
                }

                processor.endOperation(b);
            }
        };
    }

    @Nonnull
    @Override
    public int[] getInputSlots() {
        return new int[0];
    }

    @Nonnull
    @Override
    public int[] getOutputSlots() {
        return OUTPUT_SLOTS;
    }

    @Nonnull
    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> displayRecipes = new LinkedList<>();

        for (GEOResource resource : Slimefun.getRegistry().getGEOResources().values()) {
            if (resource.isObtainableFromGEOMiner()) {
                displayRecipes.add(new CustomItemStack(resource.getItem(), ChatColor.RESET + resource.getName()));
            }
        }

        return displayRecipes;
    }

    @Override
    public @Nonnull String getLabelLocalPath() {
        return "guide.tooltips.recipes.miner";
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    protected void constructMenu(@Nonnull BlockMenuPreset preset) {
        for (int i : BORDER) {
            preset.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : OUTPUT_BORDER) {
            preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }

        preset.addItem(
                4, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "), ChestMenuUtils.getEmptyClickHandler());

        for (int i : OUTPUT_SLOTS) {
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

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem sf, SlimefunBlockData data) {
                GEOMiner.this.tick(b);
            }

            @Override
            public boolean isSynchronized() {
                return true;
            }
        });
    }

    protected void tick(@Nonnull Block b) {
        BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());
        if (inv == null) {
            return;
        }

        GEOMiningOperation operation = processor.getOperation(b);

        if (operation != null) {
            if (!operation.isFinished()) {
                processor.updateProgressBar(inv, 4, operation);

                if (getCharge(b.getLocation()) < getEnergyConsumption()) {
                    return;
                }

                removeCharge(b.getLocation(), getEnergyConsumption());
                operation.addProgress(getSpeed());
            } else {
                ItemStack result = operation.getResult();
                if (!inv.fits(result, OUTPUT_SLOTS)) {
                    return;
                }

                inv.replaceExistingItem(4, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "));
                inv.pushItem(result, OUTPUT_SLOTS);
                processor.endOperation(b);
            }
            return;
        }

        SlimefunChunkData chunkData =
                Slimefun.getDatabaseManager().getBlockDataController().getChunkDataFromCache(b.getLocation());
        if (chunkData != null && chunkData.isDataLoaded()) {
            if (chunkData.getAllData().isEmpty()) {
                updateHologram(b, "&4GEO-Scan required!");
            } else {
                start(b, inv);
            }
        } else {
            Slimefun.getDatabaseManager()
                    .getBlockDataController()
                    .getChunkDataAsync(b.getChunk(), new IAsyncReadCallback<>() {
                        @Override
                        public void onResult(SlimefunChunkData result) {
                            updateHologram(b, "&4Block data loading...");
                        }
                    });
        }
    }

    private void start(@Nonnull Block b, @Nonnull BlockMenu inv) {
        boolean success = Slimefun.getRegistry().getGEOResources().values().isEmpty();
        for (GEOResource resource : Slimefun.getRegistry().getGEOResources().values()) {
            if (resource.isObtainableFromGEOMiner()) {
                OptionalInt optional = Slimefun.getGPSNetwork()
                        .getResourceManager()
                        .getSupplies(resource, b.getWorld(), b.getX() >> 4, b.getZ() >> 4);

                if (optional.isEmpty()) {
                    continue;
                }

                success = true;

                int supplies = optional.getAsInt();
                if (supplies > 0) {
                    if (!inv.fits(resource.getItem(), OUTPUT_SLOTS)) {
                        return;
                    }

                    processor.startOperation(b, new GEOMiningOperation(resource, PROCESSING_TIME));
                    Slimefun.getGPSNetwork()
                            .getResourceManager()
                            .setSupplies(resource, b.getWorld(), b.getX() >> 4, b.getZ() >> 4, supplies - 1);
                    updateHologram(b, "&7Mining: &r" + resource.getName());
                    return;
                }
            }
        }

        if (!success) {
            updateHologram(b, "&4GEO-Scan required!");
            return;
        }

        updateHologram(b, "&7Mining complete");
    }
}
