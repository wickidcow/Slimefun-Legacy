package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Adds transactional recipe filling to the native enhanced guide.
 *
 * <p>This first implementation deliberately supports only the three shaped, dispenser-backed core crafting
 * multiblocks. It transfers items but never crafts, fires no synthetic events and never reads nearby storage.
 */
public final class LegacyRecipeFillManager implements Listener {

    private static final int BUTTON_SLOT = 25;
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static LegacyRecipeFillManager instance;

    private final Slimefun plugin;
    private final NamespacedKey buttonKey;
    private final Map<UUID, RecipeFillContext> contexts = new ConcurrentHashMap<>();

    private LegacyRecipeFillManager(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        buttonKey = new NamespacedKey(plugin, "enhanced_guide_recipe_fill");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
        instance = new LegacyRecipeFillManager(plugin);
    }

    public static @Nonnull LegacyRecipeFillManager get() {
        if (instance == null) {
            throw new IllegalStateException("Enhanced guide recipe filling was accessed before initialization");
        }
        return instance;
    }

    public void decorateRecipePage(@Nonnull Player player, @Nonnull SlimefunItem item) {
        LegacyGuideSettings settings = LegacyGuideSettings.get();
        RecipeType recipeType = item.getRecipeType();
        ItemStack[] recipe = normalizeRecipe(item.getRecipe());

        if (!settings.hasRecipeFill() || recipe == null || !isSupported(recipeType)) {
            contexts.remove(player.getUniqueId());
            return;
        }

        Inventory guideInventory = player.getOpenInventory().getTopInventory();
        if (guideInventory.getSize() <= BUTTON_SLOT || !isEmpty(guideInventory.getItem(BUTTON_SLOT))) {
            contexts.remove(player.getUniqueId());
            return;
        }

        long expiresAt = System.currentTimeMillis() + settings.getRecipeFillSessionSeconds() * 1000L;
        RecipeFillContext context =
                new RecipeFillContext(guideInventory, recipeType, recipe, machineName(recipeType), expiresAt);
        contexts.put(player.getUniqueId(), context);
        guideInventory.setItem(BUTTON_SLOT, createButton(player, context));
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(@Nonnull InventoryClickEvent event) {
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player player)) {
            return;
        }

        RecipeFillContext context = contexts.get(player.getUniqueId());
        Inventory topInventory = event.getView().getTopInventory();
        if (context == null || context.guideInventory() != topInventory) {
            return;
        }

        boolean buttonClick = event.getRawSlot() == BUTTON_SLOT
                && event.getClickedInventory() == topInventory
                && isRecipeFillButton(event.getCurrentItem());

        // Prevent collection or unrelated shift-transfers from moving the protected guide button.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && !buttonClick)) {
            event.setCancelled(true);
            return;
        }

        if (!buttonClick) {
            return;
        }

        event.setCancelled(true);
        if (!event.isLeftClick()) {
            return;
        }
        if (context.expiresAt() < System.currentTimeMillis()) {
            contexts.remove(player.getUniqueId());
            send(player, ChatColor.RED + "This recipe-fill session expired. Reopen the recipe and try again.");
            return;
        }

        fillRecipe(player, context, event.isShiftClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(@Nonnull InventoryDragEvent event) {
        RecipeFillContext context = contexts.get(event.getWhoClicked().getUniqueId());
        if (context != null
                && context.guideInventory() == event.getView().getTopInventory()
                && event.getRawSlots().contains(BUTTON_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(@Nonnull InventoryCloseEvent event) {
        RecipeFillContext context = contexts.get(event.getPlayer().getUniqueId());
        if (context != null && context.guideInventory() == event.getInventory()) {
            contexts.remove(event.getPlayer().getUniqueId(), context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@Nonnull PlayerQuitEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    private void fillRecipe(@Nonnull Player player, @Nonnull RecipeFillContext context, boolean maximum) {
        LegacyGuideSettings settings = LegacyGuideSettings.get();
        Block lookedAt = player.getTargetBlockExact(settings.getRecipeFillTargetRange());
        Block dispenserBlock = resolveDispenser(context.recipeType(), lookedAt);

        if (dispenserBlock == null) {
            send(
                    player,
                    ChatColor.RED + "Aim at a valid " + context.machineName()
                            + " crafting block or its dispenser, then click again.");
            return;
        }

        if (!Slimefun.getSchedulerService().isOwnedByCurrentRegion(dispenserBlock.getLocation())) {
            send(player, ChatColor.RED + "Move closer to the machine and try again.");
            return;
        }

        if (!Slimefun.getProtectionManager()
                .hasPermission(player, dispenserBlock, Interaction.INTERACT_BLOCK)) {
            send(player, ChatColor.RED + "You do not have permission to access this machine's dispenser.");
            return;
        }

        BlockState state = dispenserBlock.getState();
        if (!(state instanceof Dispenser dispenser)) {
            send(player, ChatColor.RED + "The target dispenser is no longer available.");
            return;
        }

        PlayerInventory playerInventory = player.getInventory();
        Inventory targetInventory = dispenser.getInventory();
        ItemStack[] playerContents = cloneContents(playerInventory.getStorageContents());
        ItemStack[] targetContents = cloneContents(targetInventory.getContents());

        TransferPlan plan = maximum
                ? planMaximum(
                        playerContents,
                        targetContents,
                        context.recipeType(),
                        context.recipe(),
                        settings.getRecipeFillMaximumSets())
                : plan(playerContents, targetContents, context.recipeType(), context.recipe(), 1);

        if (!plan.success()) {
            send(player, ChatColor.RED + plan.message());
            return;
        }

        try {
            playerInventory.setStorageContents(plan.playerContents());
            targetInventory.setContents(plan.targetContents());
        } catch (RuntimeException exception) {
            restoreInventories(playerInventory, playerContents, targetInventory, targetContents);
            plugin.getLogger().log(Level.SEVERE, "Could not commit an enhanced-guide recipe fill", exception);
            send(player, ChatColor.RED + "The transfer was cancelled and both inventories were restored.");
            return;
        }
        player.updateInventory();
        SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(player);

        if (plan.movedItems() == 0) {
            send(
                    player,
                    ChatColor.YELLOW + "The " + context.machineName()
                            + " already contains a complete recipe.");
        } else {
            send(
                    player,
                    ChatColor.GREEN + "Filled the " + context.machineName() + " for " + plan.sets()
                            + (plan.sets() == 1 ? " recipe set." : " recipe sets."));
        }

        if (settings.shouldCloseGuideAfterRecipeFill()) {
            player.closeInventory();
        }
    }

    private @Nonnull ItemStack createButton(@Nonnull Player player, @Nonnull RecipeFillContext context) {
        TransferPlan preview = plan(
                cloneContents(player.getInventory().getStorageContents()),
                new ItemStack[9],
                context.recipeType(),
                context.recipe(),
                1);
        String status = preview.success()
                ? ChatColor.GREEN + "Inventory check: Ready"
                : ChatColor.RED + preview.message();
        ItemStack button = new CustomItemStack(
                Material.HOPPER,
                "&a&lFill Crafting Interface",
                "",
                "&7Aim at the nearby " + context.machineName(),
                "&7or its recipe dispenser.",
                "",
                status,
                "",
                "&fLeft Click: &7Fill one recipe",
                "&fShift + Left Click: &7Fill as many sets as fit",
                "",
                "&8Items are moved, never crafted.");
        ItemMeta meta = button.getItemMeta();
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.BYTE, (byte) 1);
        button.setItemMeta(meta);
        return button;
    }

    private boolean isRecipeFillButton(@Nullable ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(buttonKey, PersistentDataType.BYTE);
    }

    private static @Nonnull TransferPlan planMaximum(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalTarget,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            int maximumSets) {
        TransferPlan best = null;
        for (int sets = 1; sets <= maximumSets; sets++) {
            TransferPlan candidate = plan(originalPlayer, originalTarget, recipeType, recipe, sets);
            if (!candidate.success()) {
                return best == null ? candidate : best;
            }
            best = candidate;
        }
        return best == null
                ? TransferPlan.failure("This recipe has no transferable ingredients.")
                : best;
    }

    private static @Nonnull TransferPlan plan(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalTarget,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            int sets) {
        ItemStack[] player = cloneContents(originalPlayer);
        ItemStack[] target = cloneContents(originalTarget);
        int moved = 0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack expected = recipe[slot];
            ItemStack current = target[slot];

            if (isEmpty(expected)) {
                if (!isEmpty(current)) {
                    return TransferPlan.failure("Clear the dispenser's unused recipe slots first.");
                }
                continue;
            }

            if (!isEmpty(current) && !matchesRecipeIngredient(recipeType, current, expected)) {
                return TransferPlan.failure("The dispenser contains an incompatible item in recipe slot "
                        + (slot + 1) + '.');
            }

            int required;
            try {
                required = Math.multiplyExact(Math.max(1, expected.getAmount()), sets);
            } catch (ArithmeticException exception) {
                return TransferPlan.failure("The requested recipe amount is too large.");
            }

            int currentAmount = isEmpty(current) ? 0 : current.getAmount();
            if (currentAmount >= required) {
                continue;
            }

            int maximumStack = isEmpty(current) ? expected.getMaxStackSize() : current.getMaxStackSize();
            if (required > maximumStack) {
                return TransferPlan.failure("The crafting grid cannot hold " + sets + " full recipe sets.");
            }

            int deficit = required - currentAmount;
            ItemStack movedStack = removeMatching(player, recipeType, expected, current, deficit);
            if (movedStack == null) {
                return TransferPlan.failure("Missing ingredient: " + ItemUtils.getItemName(expected));
            }

            if (isEmpty(current)) {
                target[slot] = movedStack;
            } else {
                current.setAmount(currentAmount + deficit);
            }
            moved += deficit;
        }

        return TransferPlan.success(player, target, sets, moved);
    }

    private static @Nullable ItemStack removeMatching(
            @Nonnull ItemStack[] contents,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack expected,
            @Nullable ItemStack currentTarget,
            int amount) {
        ItemStack template = isEmpty(currentTarget)
                ? findStackableTemplate(contents, recipeType, expected, amount)
                : currentTarget.clone();
        if (template == null) {
            return null;
        }

        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack source = contents[slot];
            if (isEmpty(source)
                    || !matchesRecipeIngredient(recipeType, source, expected)
                    || !canStackTogether(source, template)) {
                continue;
            }

            int taken = Math.min(remaining, source.getAmount());
            remaining -= taken;
            if (taken == source.getAmount()) {
                contents[slot] = null;
            } else {
                source.setAmount(source.getAmount() - taken);
            }
        }

        if (remaining > 0) {
            return null;
        }

        template.setAmount(amount);
        return template;
    }

    private static @Nullable ItemStack findStackableTemplate(
            @Nonnull ItemStack[] contents,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack expected,
            int amount) {
        for (ItemStack candidate : contents) {
            if (isEmpty(candidate) || !matchesRecipeIngredient(recipeType, candidate, expected)) {
                continue;
            }

            int available = 0;
            for (ItemStack source : contents) {
                if (!isEmpty(source)
                        && matchesRecipeIngredient(recipeType, source, expected)
                        && canStackTogether(source, candidate)) {
                    available += source.getAmount();
                    if (available >= amount) {
                        return candidate.clone();
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesRecipeIngredient(
            @Nonnull RecipeType recipeType, @Nullable ItemStack actual, @Nullable ItemStack expected) {
        if (RecipeType.ENHANCED_CRAFTING_TABLE.equals(recipeType)) {
            if (SlimefunUtils.isItemSimilar(actual, expected, true, false, false, false)) {
                return true;
            }
            return isBackpack(expected)
                    && SlimefunUtils.isItemSimilar(actual, expected, false, false, false, false);
        }

        if (RecipeType.MAGIC_WORKBENCH.equals(recipeType)) {
            if (SlimefunUtils.isItemSimilar(actual, expected, true, false, false, true)) {
                return true;
            }
            return isBackpack(expected)
                    && SlimefunUtils.isItemSimilar(actual, expected, false, false, false, false);
        }

        return SlimefunUtils.isItemSimilar(actual, expected, true, false, true, true);
    }

    private static boolean canStackTogether(@Nullable ItemStack first, @Nullable ItemStack second) {
        return SlimefunUtils.isItemSimilar(first, second, true, false, true, true);
    }

    private static boolean isBackpack(@Nullable ItemStack item) {
        return SlimefunItem.getByItem(item) instanceof SlimefunBackpack;
    }

    private static boolean isSupported(@Nonnull RecipeType recipeType) {
        return RecipeType.ENHANCED_CRAFTING_TABLE.equals(recipeType)
                || RecipeType.MAGIC_WORKBENCH.equals(recipeType)
                || RecipeType.ARMOR_FORGE.equals(recipeType);
    }

    private static @Nonnull String machineName(@Nonnull RecipeType recipeType) {
        if (RecipeType.MAGIC_WORKBENCH.equals(recipeType)) {
            return "Magic Workbench";
        }
        if (RecipeType.ARMOR_FORGE.equals(recipeType)) {
            return "Armor Forge";
        }
        return "Enhanced Crafting Table";
    }

    private static @Nullable Block resolveDispenser(@Nonnull RecipeType recipeType, @Nullable Block target) {
        if (target == null) {
            return null;
        }
        if (RecipeType.ENHANCED_CRAFTING_TABLE.equals(recipeType)) {
            return findVerticalDispenser(target, Material.CRAFTING_TABLE);
        }
        if (RecipeType.ARMOR_FORGE.equals(recipeType)) {
            Block dispenser = findVerticalDispenser(target, Material.ANVIL);
            return isUpwardFacingDispenser(dispenser) ? dispenser : null;
        }
        if (RecipeType.MAGIC_WORKBENCH.equals(recipeType)) {
            return findMagicWorkbenchDispenser(target);
        }
        return null;
    }

    private static @Nullable Block findVerticalDispenser(@Nonnull Block target, @Nonnull Material topMaterial) {
        if (target.getType() == topMaterial && target.getRelative(BlockFace.DOWN).getType() == Material.DISPENSER) {
            return target.getRelative(BlockFace.DOWN);
        }
        if (target.getType() == Material.DISPENSER && target.getRelative(BlockFace.UP).getType() == topMaterial) {
            return target;
        }
        return null;
    }

    private static boolean isUpwardFacingDispenser(@Nullable Block block) {
        return block != null
                && block.getBlockData() instanceof Directional directional
                && directional.getFacing() == BlockFace.UP;
    }

    private static @Nullable Block findMagicWorkbenchDispenser(@Nonnull Block target) {
        if (target.getType() == Material.CRAFTING_TABLE) {
            return findMagicWorkbenchFromCenter(target);
        }

        if (target.getType() == Material.DISPENSER) {
            for (BlockFace face : HORIZONTAL_FACES) {
                Block center = target.getRelative(face);
                Block dispenser = findMagicWorkbenchFromCenter(center);
                if (target.equals(dispenser)) {
                    return target;
                }
            }
        }

        if (target.getType() == Material.BOOKSHELF) {
            for (BlockFace face : HORIZONTAL_FACES) {
                Block center = target.getRelative(face);
                Block dispenser = center.getRelative(face);
                if (center.getType() == Material.CRAFTING_TABLE
                        && dispenser.getType() == Material.DISPENSER) {
                    return dispenser;
                }
            }
        }
        return null;
    }

    private static @Nullable Block findMagicWorkbenchFromCenter(@Nonnull Block center) {
        if (center.getType() != Material.CRAFTING_TABLE) {
            return null;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            Block dispenser = center.getRelative(face);
            Block bookshelf = center.getRelative(face.getOppositeFace());
            if (dispenser.getType() == Material.DISPENSER && bookshelf.getType() == Material.BOOKSHELF) {
                return dispenser;
            }
        }
        return null;
    }

    private static @Nullable ItemStack[] normalizeRecipe(@Nullable ItemStack[] source) {
        if (source == null || source.length == 0 || source.length > 9) {
            return null;
        }
        ItemStack[] normalized = new ItemStack[9];
        for (int slot = 0; slot < source.length; slot++) {
            normalized[slot] = cloneItem(source[slot]);
        }
        return normalized;
    }

    private static void restoreInventories(
            @Nonnull PlayerInventory playerInventory,
            @Nonnull ItemStack[] playerContents,
            @Nonnull Inventory targetInventory,
            @Nonnull ItemStack[] targetContents) {
        try {
            playerInventory.setStorageContents(cloneContents(playerContents));
        } catch (RuntimeException ignored) {
            // The original exception is logged by the caller.
        }
        try {
            targetInventory.setContents(cloneContents(targetContents));
        } catch (RuntimeException ignored) {
            // The original exception is logged by the caller.
        }
    }

    private static @Nonnull ItemStack[] cloneContents(@Nonnull ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            clone[slot] = cloneItem(source[slot]);
        }
        return clone;
    }

    private static @Nullable ItemStack cloneItem(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static void send(@Nonnull Player player, @Nonnull String message) {
        player.sendMessage(ChatColor.DARK_GREEN + "[Slimefun Legacy] " + message);
    }

    private record RecipeFillContext(
            Inventory guideInventory,
            RecipeType recipeType,
            ItemStack[] recipe,
            String machineName,
            long expiresAt) {}

    private record TransferPlan(
            boolean success,
            ItemStack[] playerContents,
            ItemStack[] targetContents,
            int sets,
            int movedItems,
            String message) {

        private static @Nonnull TransferPlan success(
                @Nonnull ItemStack[] playerContents,
                @Nonnull ItemStack[] targetContents,
                int sets,
                int movedItems) {
            return new TransferPlan(true, playerContents, targetContents, sets, movedItems, "");
        }

        private static @Nonnull TransferPlan failure(@Nonnull String message) {
            return new TransferPlan(false, new ItemStack[0], new ItemStack[0], 0, 0, message);
        }
    }
}
