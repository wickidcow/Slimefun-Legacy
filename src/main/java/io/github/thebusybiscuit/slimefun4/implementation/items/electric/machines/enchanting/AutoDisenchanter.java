package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.enchanting;

import io.github.thebusybiscuit.slimefun4.api.events.AutoDisenchantEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.integrations.AdvancedEnchantmentsIntegration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

/**
 * The {@link AutoDisenchanter}, in contrast to the {@link AutoEnchanter}, removes
 * {@link Enchantment Enchantments} from a given {@link ItemStack} and transfers them
 * to a book.
 *
 * @author TheBusyBiscuit
 * @author Poslovitch
 * @author John000708
 * @author Walshy
 * @author poma123
 * @author mrcoffee1026
 * @author VoidAngel
 * @author StarWishSama
 *
 * @see AutoEnchanter
 */
public class AutoDisenchanter extends AbstractEnchantmentMachine {

    // Slimefun Legacy 4.1.18 machine runtime hardening.
    @ParametersAreNonnullByDefault
    public AutoDisenchanter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.DIAMOND_CHESTPLATE);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        try {
            return findNextRecipeSafely(menu);
        } catch (RuntimeException | LinkageError failure) {
            EnchantmentMachineRuntime.reportFailure(this, menu, "find recipe", failure);
            EnchantmentMachineRuntime.status(
                    menu,
                    Material.BARRIER,
                    "&cAuto Disenchanter paused",
                    "&7A compatibility error was blocked.",
                    "&7Your inputs were left untouched.");
            return null;
        }
    }

    private @Nullable MachineRecipe findNextRecipeSafely(BlockMenu menu) {
        int[] inputSlots = getInputSlots();
        for (int itemSlot : inputSlots) {
            ItemStack item = menu.getItemInSlot(itemSlot);
            if (!isDisenchantable(item)) {
                continue;
            }

            int bookSlot = itemSlot == inputSlots[0] ? inputSlots[1] : inputSlots[0];
            ItemStack book = menu.getItemInSlot(bookSlot);
            if (book == null || book.getType() != Material.BOOK) {
                continue;
            }

            AutoDisenchantEvent event = new AutoDisenchantEvent(item, menu.getBlock());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                EnchantmentMachineRuntime.status(
                        menu,
                        Material.BARRIER,
                        "&cDisenchanting blocked",
                        "&7Another plugin cancelled the operation.",
                        "&7Inputs were not moved or consumed.");
                return null;
            }

            return disenchant(menu, item, book);
        }

        EnchantmentMachineRuntime.status(
                menu,
                Material.BOOK,
                "&eWaiting for inputs",
                "&7Insert one enchanted item and",
                "&7one normal book in the input slots.");
        return null;
    }

    @ParametersAreNonnullByDefault
    protected @Nullable MachineRecipe disenchant(BlockMenu menu, ItemStack item, ItemStack book) {
        try {
            return disenchantSafely(menu, item, book);
        } catch (RuntimeException | LinkageError failure) {
            EnchantmentMachineRuntime.reportFailure(this, menu, "extract enchantments", failure);
            EnchantmentMachineRuntime.status(
                    menu,
                    Material.BARRIER,
                    "&cDisenchanting failed safely",
                    "&7The item and book were not consumed.",
                    "&7Check the console for the cause.");
            return null;
        }
    }

    @ParametersAreNonnullByDefault
    private @Nullable MachineRecipe disenchantSafely(BlockMenu menu, ItemStack item, ItemStack book) {
        AdvancedEnchantmentsIntegration advancedEnchantments =
                Slimefun.getIntegrations().getAdvancedEnchantments();
        Map<String, Integer> customEnchantments =
                advancedEnchantments == null ? Collections.emptyMap() : advancedEnchantments.getEnchantments(item);
        int totalEnchantments = item.getEnchantments().size() + customEnchantments.size();
        if (totalEnchantments == 0) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&eNothing to extract", "&7The item has no supported enchantments.");
            return null;
        }
        if (!isEnchantmentCountAllowed(totalEnchantments)) {
            showEnchantmentLimitWarning(menu);
            return null;
        }

        for (int level : customEnchantments.values()) {
            if (!isEnchantmentLevelAllowed(level)) {
                if (!menu.toInventory().getViewers().isEmpty()) {
                    showEnchantmentLevelWarning(menu);
                }
                return null;
            }
        }

        if (!customEnchantments.isEmpty()) {
            Map.Entry<String, Integer> enchantment =
                    customEnchantments.entrySet().iterator().next();
            Map<String, Integer> extracted = Collections.singletonMap(enchantment.getKey(), enchantment.getValue());
            ItemStack disenchantedItem = advancedEnchantments.removeEnchantments(item, extracted);
            ItemStack enchantedBook =
                    advancedEnchantments.createEnchantmentBook(enchantment.getKey(), enchantment.getValue());
            if (disenchantedItem == null || enchantedBook == null) {
                EnchantmentMachineRuntime.status(
                        menu,
                        Material.BARRIER,
                        "&cCustom extraction failed",
                        "&7AdvancedEnchantments rejected the operation.",
                        "&7Inputs were left untouched.");
                return null;
            }
            disenchantedItem.setAmount(1);
            return createRecipe(menu, item, book, disenchantedItem, enchantedBook, 1);
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (!isEnchantmentLevelAllowed(entry.getValue())) {
                if (!menu.toInventory().getViewers().isEmpty()) {
                    showEnchantmentLevelWarning(menu);
                }
                return null;
            }
            enchantments.put(entry.getKey(), entry.getValue());
        }
        if (enchantments.isEmpty()) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&eNothing to extract", "&7No compatible Bukkit enchantments were found.");
            return null;
        }

        ItemStack disenchantedItem = EnchantmentMachineRuntime.one(item);
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        transferEnchantments(disenchantedItem, enchantedBook, enchantments);
        if (!transferWasComplete(disenchantedItem, enchantedBook, enchantments)) {
            EnchantmentMachineRuntime.status(
                    menu,
                    Material.BARRIER,
                    "&cExtraction verification failed",
                    "&7No inputs were consumed to prevent duplication.");
            return null;
        }

        return createRecipe(menu, item, book, disenchantedItem, enchantedBook, enchantments.size());
    }

    @ParametersAreNonnullByDefault
    private @Nullable MachineRecipe createRecipe(
            BlockMenu menu,
            ItemStack item,
            ItemStack book,
            ItemStack disenchantedItem,
            ItemStack enchantedBook,
            int enchantmentCount) {
        MachineRecipe recipe = new MachineRecipe(
                EnchantmentMachineRuntime.processingTicks(90, enchantmentCount, getSpeed()),
                new ItemStack[] {EnchantmentMachineRuntime.one(book), EnchantmentMachineRuntime.one(item)},
                new ItemStack[] {disenchantedItem, enchantedBook});
        if (!Slimefun.getItemStackService()
                .fitAll(menu.toInventory(), recipe.getOutput(), InventoryContext.MACHINE_OUTPUT, getOutputSlots())) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&cOutput full", "&7Clear both output slots before disenchanting.");
            return null;
        }

        if (!EnchantmentMachineRuntime.consumeOneEach(menu, getInputSlots())) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&cInputs changed", "&7The operation was cancelled before consumption.");
            return null;
        }
        return recipe;
    }

    @ParametersAreNonnullByDefault
    protected void transferEnchantments(ItemStack item, ItemStack book, Map<Enchantment, Integer> enchantments) {
        ItemMeta itemMeta = item.getItemMeta();
        ItemMeta bookMeta = book.getItemMeta();
        if (itemMeta instanceof Repairable itemRepairable && bookMeta instanceof Repairable bookRepairable) {
            bookRepairable.setRepairCost(itemRepairable.getRepairCost());
            itemRepairable.setRepairCost(0);
            book.setItemMeta(bookMeta);
        }

        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantmentToTransfer = entry.getKey();
            boolean wasEnchantmentRemoved = itemMeta.removeEnchant(enchantmentToTransfer);
            boolean stillHasEnchantment = itemMeta.getEnchants().containsKey(enchantmentToTransfer);
            if (wasEnchantmentRemoved && !stillHasEnchantment) {
                meta.addStoredEnchant(enchantmentToTransfer, entry.getValue(), true);
            } else {
                Slimefun.logger()
                        .log(
                                Level.SEVERE,
                                "AutoDisenchanter has failed to remove enchantment \"{0}\"",
                                enchantmentToTransfer.getKey().getKey());
            }
        }
        item.setItemMeta(itemMeta);
        book.setItemMeta(meta);
    }

    @ParametersAreNonnullByDefault
    private boolean transferWasComplete(ItemStack item, ItemStack book, Map<Enchantment, Integer> enchantments) {
        if (!(book.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) {
            return false;
        }
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (item.containsEnchantment(entry.getKey())
                    || bookMeta.getStoredEnchantLevel(entry.getKey()) != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean isDisenchantable(@Nullable ItemStack item) {
        if (item != null && !item.getType().isAir() && item.getType() != Material.BOOK && !hasIgnoredLore(item)) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);
            return sfItem == null || sfItem.isDisenchantable();
        }
        return false;
    }

    @Override
    public String getMachineIdentifier() {
        return "AUTO_DISENCHANTER";
    }
}
