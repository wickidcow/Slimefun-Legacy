package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link OreWasher} is a special {@link MultiBlockMachine} which allows you to
 * turn Sifted Ore into ore dusts.
 *
 * @author TheBusyBiscuit
 * @author Sfiguz7
 *
 */
public class OreWasher extends MultiBlockMachine {

    // @formatter:off
    private final ItemStack[] dusts = new ItemStack[] {
        SlimefunItems.IRON_DUST,
        SlimefunItems.GOLD_DUST,
        SlimefunItems.COPPER_DUST,
        SlimefunItems.TIN_DUST,
        SlimefunItems.ZINC_DUST,
        SlimefunItems.ALUMINUM_DUST,
        SlimefunItems.MAGNESIUM_DUST,
        SlimefunItems.LEAD_DUST,
        SlimefunItems.SILVER_DUST
    };
    // @formatter:on

    private final boolean legacyMode;

    @ParametersAreNonnullByDefault
    public OreWasher(ItemGroup itemGroup, SlimefunItemStack item) {
        // @formatter:off
        super(
                itemGroup,
                item,
                new ItemStack[] {
                    null, new ItemStack(Material.DISPENSER), null,
                    null, new ItemStack(Material.OAK_FENCE), null,
                    null, new ItemStack(Material.CAULDRON), null
                },
                BlockFace.SELF);
        // @formatter:on

        legacyMode = Slimefun.getCfg().getBoolean("options.legacy-ore-washer");
    }

    @Override
    protected void registerDefaultRecipes(List<ItemStack> recipes) {
        /*
         * Iron and Gold are displayed as Ore Crusher recipes, as that is their primary
         * way of obtaining them. But we also wanna display them here, so we just
         * add these two recipes manually
         */
        recipes.add(SlimefunItems.SIFTED_ORE);
        recipes.add(SlimefunItems.IRON_DUST);

        recipes.add(SlimefunItems.SIFTED_ORE);
        recipes.add(SlimefunItems.GOLD_DUST);

        recipes.add(new ItemStack(Material.SAND));
        recipes.add(SlimefunItems.SALT);
    }

    @Override
    public @Nonnull List<ItemStack> getDisplayRecipes() {
        return recipes.stream().map(items -> items[0]).toList();
    }

    @Override
    public void onInteract(Player p, Block b) {
        Block dispBlock = b.getRelative(BlockFace.UP);
        BlockState state = dispBlock.getState(false);

        if (state instanceof Dispenser disp) {
            Inventory inv = disp.getInventory();

            for (ItemStack input : inv.getContents()) {
                if (input == null) {
                    continue;
                }

                if (SlimefunUtils.isItemSimilar(input, SlimefunItems.SIFTED_ORE, true)) {
                    ItemStack defaultOutput = getRandomDust();
                    MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, defaultOutput);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }

                    ItemStack output = event.getOutput();
                    ItemStack selector = legacyMode ? output : SlimefunItems.DEBUG_FISH;
                    Inventory outputInv = findSafeOutputInventory(
                            selector,
                            dispBlock,
                            inv,
                            input,
                            1,
                            output,
                            SlimefunItems.STONE_CHUNK);

                    completeCraft(
                            p,
                            b,
                            inv,
                            outputInv,
                            input,
                            1,
                            output,
                            SlimefunItems.STONE_CHUNK);
                    return;
                } else if (SlimefunUtils.isItemSimilar(input, new ItemStack(Material.SAND, 2), false)) {
                    ItemStack defaultOutput = SlimefunItems.SALT;
                    MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, defaultOutput);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }

                    ItemStack output = event.getOutput();
                    Inventory outputInv =
                            findSafeOutputInventory(output, dispBlock, inv, input, 2, output);
                    completeCraft(p, b, inv, outputInv, input, 2, output);
                    return;
                } else if (SlimefunUtils.isItemSimilar(input, SlimefunItems.PULVERIZED_ORE, true)) {
                    ItemStack defaultOutput = SlimefunItems.PURE_ORE_CLUSTER;
                    MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, defaultOutput);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }

                    ItemStack output = event.getOutput();
                    Inventory outputInv =
                            findSafeOutputInventory(output, dispBlock, inv, input, 1, output);
                    completeCraft(p, b, inv, outputInv, input, 1, output);
                    return;
                }
            }

            Slimefun.getLocalization().sendMessage(p, "machines.unknown-material", true);
        }
    }

    @ParametersAreNonnullByDefault
    private @Nullable Inventory findSafeOutputInventory(
            ItemStack selector,
            Block dispBlock,
            Inventory inputInv,
            ItemStack input,
            int amount,
            ItemStack... outputs) {
        Inventory preferred = findOutputInventory(selector, dispBlock, inputInv);

        if (preferred != null && canFitAll(preferred, inputInv, input, amount, outputs)) {
            return preferred;
        }

        if (preferred != inputInv && canFitAll(inputInv, inputInv, input, amount, outputs)) {
            return inputInv;
        }

        return null;
    }

    @ParametersAreNonnullByDefault
    private boolean canFitAll(
            Inventory target,
            Inventory inputInv,
            ItemStack input,
            int amount,
            ItemStack... outputs) {
        Inventory simulation = Bukkit.createInventory(null, target.getSize());
        ItemStack[] contents = target.getContents();
        ItemStack[] cloned = new ItemStack[contents.length];

        for (int i = 0; i < contents.length; i++) {
            cloned[i] = contents[i] == null ? null : contents[i].clone();
        }

        simulation.setContents(cloned);

        if (target == inputInv) {
            ItemStack removing = input.clone();
            removing.setAmount(amount);
            simulation.removeItem(removing);
        }

        InventoryContext context = target == inputInv ? InventoryContext.MACHINE_OUTPUT : InventoryContext.OUTPUT_CHEST;
        for (ItemStack output : outputs) {
            ItemStack remainder = Slimefun.getItemStackService().addItem(simulation, output.clone(), context);
            if (remainder != null && remainder.getAmount() > 0) {
                return false;
            }
        }

        return true;
    }

    @ParametersAreNonnullByDefault
    private void completeCraft(
            Player p,
            Block b,
            Inventory inputInv,
            @Nullable Inventory outputInv,
            ItemStack input,
            int amount,
            ItemStack... outputs) {
        if (outputInv == null) {
            Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
            return;
        }

        ItemStack removing = input.clone();
        removing.setAmount(amount);
        inputInv.removeItem(removing);

        InventoryContext context = outputInv == inputInv ? InventoryContext.MACHINE_OUTPUT : InventoryContext.OUTPUT_CHEST;
        for (ItemStack output : outputs) {
            ItemStack remainder = Slimefun.getItemStackService().addItem(outputInv, output.clone(), context);
            if (remainder != null && remainder.getAmount() > 0) {
                b.getWorld().dropItemNaturally(b.getLocation(), remainder);
            }
        }

        VisualEffectUtils.playBlockBreakEffect(b.getLocation(), Material.WATER);
        SoundEffect.ORE_WASHER_WASH_SOUND.playAt(b);
    }

    /**
     * This returns a random dust item from Slimefun.
     *
     * @return A randomly picked dust item
     */
    public @Nonnull ItemStack getRandomDust() {
        int index = ThreadLocalRandom.current().nextInt(dusts.length);
        return dusts[index].clone();
    }
}
