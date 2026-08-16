package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.enchanting;

import io.github.thebusybiscuit.slimefun4.api.events.AsyncAutoEnchanterProcessEvent;
import io.github.thebusybiscuit.slimefun4.api.events.AutoEnchantEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.integrations.AdvancedEnchantmentsIntegration;
import io.github.thebusybiscuit.slimefun4.integrations.AdvancedEnchantmentsIntegration.EnchantmentBook;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/**
 * The {@link AutoEnchanter}, in contrast to the {@link AutoDisenchanter}, adds
 * {@link Enchantment Enchantments} from a given enchanted book and transfers them onto
 * an {@link ItemStack}.
 *
 * @author TheBusyBiscuit
 * @author Poslovitch
 * @author Mooy1
 * @author StarWishSama
 * @author martinbrom
 *
 * @see AutoDisenchanter
 */
public class AutoEnchanter extends AbstractEnchantmentMachine {

    // Slimefun Legacy 4.1.18 machine runtime hardening.
    private final ItemSetting<Boolean> overrideExistingEnchantsLvl =
            new ItemSetting<>(this, "override-existing-enchants-lvl", false);

    @ParametersAreNonnullByDefault
    public AutoEnchanter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(overrideExistingEnchantsLvl);
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.GOLDEN_CHESTPLATE);
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
                    "&cAuto Enchanter paused",
                    "&7A compatibility error was blocked.",
                    "&7Your inputs were left untouched.");
            return null;
        }
    }

    private @Nullable MachineRecipe findNextRecipeSafely(BlockMenu menu) {
        int[] inputSlots = getInputSlots();
        for (int bookSlot : inputSlots) {
            ItemStack enchantedBook = menu.getItemInSlot(bookSlot);
            if (enchantedBook == null || enchantedBook.getType() != Material.ENCHANTED_BOOK) {
                continue;
            }

            int targetSlot = bookSlot == inputSlots[0] ? inputSlots[1] : inputSlots[0];
            ItemStack target = menu.getItemInSlot(targetSlot);
            if (!isEnchantable(target)) {
                continue;
            }

            AutoEnchantEvent event = new AutoEnchantEvent(target, menu.getBlock());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                EnchantmentMachineRuntime.status(
                        menu,
                        Material.BARRIER,
                        "&cEnchanting blocked",
                        "&7Another plugin cancelled the operation.",
                        "&7Inputs were not moved or consumed.");
                return null;
            }

            return enchant(menu, target, enchantedBook);
        }

        EnchantmentMachineRuntime.status(
                menu,
                Material.ENCHANTED_BOOK,
                "&eWaiting for inputs",
                "&7Insert one enchantable item and",
                "&7one enchanted book in the input slots.");
        return null;
    }

    @Nullable @ParametersAreNonnullByDefault
    protected MachineRecipe enchant(BlockMenu menu, ItemStack target, ItemStack enchantedBook) {
        try {
            return enchantSafely(menu, target, enchantedBook);
        } catch (RuntimeException | LinkageError failure) {
            EnchantmentMachineRuntime.reportFailure(this, menu, "apply enchantments", failure);
            EnchantmentMachineRuntime.status(
                    menu,
                    Material.BARRIER,
                    "&cEnchanting failed safely",
                    "&7The item and book were not consumed.",
                    "&7Check the console for the cause.");
            return null;
        }
    }

    @Nullable @ParametersAreNonnullByDefault
    private MachineRecipe enchantSafely(BlockMenu menu, ItemStack target, ItemStack enchantedBook) {
        AsyncAutoEnchanterProcessEvent event = new AsyncAutoEnchanterProcessEvent(target, enchantedBook, menu);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            EnchantmentMachineRuntime.status(
                    menu,
                    Material.BARRIER,
                    "&cEnchanting blocked",
                    "&7A process listener cancelled this operation.",
                    "&7Inputs were left untouched.");
            return null;
        }

        ItemStack targetSnapshot = EnchantmentMachineRuntime.one(target);
        ItemStack bookSnapshot = EnchantmentMachineRuntime.one(enchantedBook);
        if (!(bookSnapshot.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&cInvalid enchanted book", "&7The book metadata could not be read.");
            return null;
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>();
        Map<String, Integer> customEnchantments = new LinkedHashMap<>();
        AdvancedEnchantmentsIntegration advancedEnchantments =
                Slimefun.getIntegrations().getAdvancedEnchantments();
        EnchantmentBook customBook =
                advancedEnchantments == null ? null : advancedEnchantments.getEnchantmentBook(bookSnapshot);
        if (customBook != null) {
            customEnchantments.put(customBook.enchantment(), customBook.level());
        }

        for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
            if (!entry.getKey().canEnchantItem(targetSnapshot)) {
                continue;
            }
            if (!isEnchantmentLevelAllowed(entry.getValue())) {
                if (!menu.toInventory().getViewers().isEmpty()) {
                    showEnchantmentLevelWarning(menu);
                }
                return null;
            }
            enchantments.put(entry.getKey(), entry.getValue());
        }

        for (int level : customEnchantments.values()) {
            if (!isEnchantmentLevelAllowed(level)) {
                if (!menu.toInventory().getViewers().isEmpty()) {
                    showEnchantmentLevelWarning(menu);
                }
                return null;
            }
        }

        Map<String, Integer> existingCustomEnchantments =
                advancedEnchantments == null ? Map.of() : advancedEnchantments.getEnchantments(targetSnapshot);
        if (!overrideExistingEnchantsLvl.getValue()) {
            enchantments
                    .entrySet()
                    .removeIf(entry -> targetSnapshot.getEnchantmentLevel(entry.getKey()) >= entry.getValue());
            customEnchantments
                    .entrySet()
                    .removeIf(entry -> existingCustomEnchantments.getOrDefault(entry.getKey(), 0) >= entry.getValue());
        }

        int enchantmentCount = enchantments.size() + customEnchantments.size();
        if (enchantmentCount == 0) {
            EnchantmentMachineRuntime.status(
                    menu,
                    Material.BARRIER,
                    "&eNothing to apply",
                    "&7The book has no compatible upgrades",
                    "&7for this item, or equal levels already exist.");
            return null;
        }

        int resultingCount = targetSnapshot.getEnchantments().size() + existingCustomEnchantments.size();
        for (Enchantment enchantment : enchantments.keySet()) {
            if (!targetSnapshot.containsEnchantment(enchantment)) {
                resultingCount++;
            }
        }
        for (String enchantment : customEnchantments.keySet()) {
            if (!existingCustomEnchantments.containsKey(enchantment)) {
                resultingCount++;
            }
        }
        if (!isEnchantmentCountAllowed(resultingCount)) {
            showEnchantmentLimitWarning(menu);
            return null;
        }

        ItemStack enchantedItem = EnchantmentMachineRuntime.one(targetSnapshot);
        enchantedItem.addUnsafeEnchantments(enchantments);
        if (!customEnchantments.isEmpty()) {
            if (advancedEnchantments == null) {
                EnchantmentMachineRuntime.status(
                        menu,
                        Material.BARRIER,
                        "&cCustom enchant unavailable",
                        "&7AdvancedEnchantments is not ready.",
                        "&7Inputs were left untouched.");
                return null;
            }
            enchantedItem = advancedEnchantments.applyEnchantments(enchantedItem, customEnchantments);
            if (enchantedItem == null) {
                EnchantmentMachineRuntime.status(
                        menu,
                        Material.BARRIER,
                        "&cCustom enchant failed",
                        "&7AdvancedEnchantments rejected the operation.",
                        "&7Inputs were left untouched.");
                return null;
            }
        }

        MachineRecipe recipe = new MachineRecipe(
                EnchantmentMachineRuntime.processingTicks(75, enchantmentCount, getSpeed()),
                new ItemStack[] {
                    EnchantmentMachineRuntime.one(targetSnapshot), EnchantmentMachineRuntime.one(bookSnapshot)
                },
                new ItemStack[] {enchantedItem, new ItemStack(Material.BOOK)});
        if (!Slimefun.getItemStackService()
                .fitAll(menu.toInventory(), recipe.getOutput(), InventoryContext.MACHINE_OUTPUT, getOutputSlots())) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&cOutput full", "&7Clear both output slots before enchanting.");
            return null;
        }

        if (!EnchantmentMachineRuntime.consumeOneEachIfUnchanged(
                menu, getInputSlots(), new ItemStack[] {targetSnapshot, bookSnapshot})) {
            EnchantmentMachineRuntime.status(
                    menu, Material.BARRIER, "&cInputs changed", "&7The operation was cancelled before consumption.");
            return null;
        }
        return recipe;
    }

    private boolean isEnchantable(@Nullable ItemStack item) {
        if (item != null
                && item.getType() != Material.ENCHANTED_BOOK
                && !item.getType().isAir()
                && !hasIgnoredLore(item)) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);
            return sfItem == null || sfItem.isEnchantable();
        }
        return false;
    }

    @Override
    public String getMachineIdentifier() {
        return "AUTO_ENCHANTER";
    }
}
