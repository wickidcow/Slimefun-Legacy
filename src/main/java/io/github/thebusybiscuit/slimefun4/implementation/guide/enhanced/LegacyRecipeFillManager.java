package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSpawnReason;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientAltar;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientPedestal;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
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
import org.bukkit.util.Vector;

/**
 * Adds transactional recipe preparation to the native enhanced guide.
 *
 * <p>Phase 2 introduced shaped dispenser filling for the Enhanced Crafting Table, Magic Workbench and Armor Forge.
 * Phase 3 extends that implementation with unordered core multiblock recipes, Ancient Altar pedestal preparation,
 * complete missing-ingredient reports and sub-recipe hints. This class only moves or displays ingredients; it never
 * performs the final craft and never reads nearby storage.
 */
public final class LegacyRecipeFillManager implements Listener {

    private static final int BUTTON_SLOT = 25;
    private static final BlockFace[] HORIZONTAL_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private static final int[] ALTAR_RECIPE_SLOTS = {0, 1, 2, 5, 8, 7, 6, 3};
    private static final int[][] ALTAR_PEDESTAL_OFFSETS = {
        {2, 0, -2},
        {3, 0, 0},
        {2, 0, 2},
        {0, 0, 3},
        {-2, 0, 2},
        {-3, 0, 0},
        {-2, 0, -2},
        {0, 0, -3}
    };

    private static LegacyRecipeFillManager instance;

    private final Slimefun plugin;
    private final NamespacedKey buttonKey;
    private final Map<UUID, RecipeFillContext> contexts = new ConcurrentHashMap<>();
    private final Map<Location, Long> observedAltarLocks = new ConcurrentHashMap<>();

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
        RecipeKind kind = classify(recipeType, recipe, settings);

        if (!settings.hasRecipeFill() || recipe == null || kind == RecipeKind.UNSUPPORTED) {
            contexts.remove(player.getUniqueId());
            return;
        }

        Inventory guideInventory = player.getOpenInventory().getTopInventory();
        if (guideInventory.getSize() <= BUTTON_SLOT || !isEmpty(guideInventory.getItem(BUTTON_SLOT))) {
            contexts.remove(player.getUniqueId());
            return;
        }

        long expiresAt = System.currentTimeMillis() + settings.getRecipeFillSessionSeconds() * 1000L;
        RecipeFillContext context = new RecipeFillContext(
                guideInventory, item, recipeType, recipe, kind, machineName(recipeType), expiresAt);
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
        if (context.expiresAt() < System.currentTimeMillis()) {
            contexts.remove(player.getUniqueId());
            send(player, ChatColor.RED + "This recipe-fill session expired. Reopen the recipe and try again.");
            return;
        }

        if (event.isRightClick()) {
            sendIngredientReport(player, context);
            return;
        }

        if (!event.isLeftClick()) {
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

    /**
     * Conservatively records altar activation attempts. The core altar consumes pedestal entities over time, so the
     * guide must not prepare the same structure during the final animation steps after some pedestals have become empty.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSlimefunRightClick(@Nonnull PlayerRightClickEvent event) {
        Optional<SlimefunItem> slimefunBlock = event.getSlimefunBlock();
        Optional<Block> clickedBlock = event.getClickedBlock();
        if (slimefunBlock.isEmpty()
                || clickedBlock.isEmpty()
                || !SlimefunItems.ANCIENT_ALTAR.getItemId().equals(slimefunBlock.get().getId())) {
            return;
        }

        int lockSeconds = LegacyGuideSettings.get().getRecipeFillAltarLockSeconds();
        SlimefunItem altar = SlimefunItems.ANCIENT_ALTAR.getItem();
        if (altar instanceof AncientAltar ancientAltar) {
            long ritualTicks = 10L + 36L * ancientAltar.getStepDelay();
            long ritualSeconds = Math.floorDiv(ritualTicks + 19L, 20L) + 2L;
            lockSeconds = (int) Math.min(Integer.MAX_VALUE, Math.max(lockSeconds, ritualSeconds));
        }

        long expiresAt = System.currentTimeMillis() + lockSeconds * 1000L;
        observedAltarLocks.put(clickedBlock.get().getLocation(), expiresAt);
    }

    private void fillRecipe(@Nonnull Player player, @Nonnull RecipeFillContext context, boolean maximum) {
        if (context.kind() == RecipeKind.ANCIENT_ALTAR) {
            fillAncientAltar(player, context);
        } else {
            fillDispenserRecipe(player, context, maximum);
        }
    }

    private void fillDispenserRecipe(
            @Nonnull Player player, @Nonnull RecipeFillContext context, boolean maximum) {
        LegacyGuideSettings settings = LegacyGuideSettings.get();
        Block lookedAt = player.getTargetBlockExact(settings.getRecipeFillTargetRange());
        ResolvedDispenser resolved = resolveDispenser(context, lookedAt);

        if (resolved == null) {
            send(
                    player,
                    ChatColor.RED + "Aim at a valid " + context.machineName()
                            + " crafting block or its dispenser, then click again.");
            return;
        }

        if (!isRegionOwned(resolved.dispenser()) || !isRegionOwned(resolved.interactionBlock())) {
            send(player, ChatColor.RED + "Move closer to the machine and try again.");
            return;
        }

        if (!hasAccess(player, resolved.dispenser()) || !hasAccess(player, resolved.interactionBlock())) {
            send(player, ChatColor.RED + "You do not have permission to access this machine.");
            return;
        }

        BlockState state = resolved.dispenser().getState();
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
                        context.kind(),
                        settings.getRecipeFillMaximumSets())
                : plan(
                        playerContents,
                        targetContents,
                        context.recipeType(),
                        context.recipe(),
                        context.kind(),
                        1);

        if (!plan.success()) {
            send(player, ChatColor.RED + plan.message());
            sendMissingSummary(player, context, plan.missing());
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

        closeAfterSuccess(player);
    }

    private void fillAncientAltar(@Nonnull Player player, @Nonnull RecipeFillContext context) {
        LegacyGuideSettings settings = LegacyGuideSettings.get();
        Block lookedAt = player.getTargetBlockExact(settings.getRecipeFillTargetRange());
        Block altar = resolveAncientAltar(lookedAt);
        if (altar == null) {
            send(player, ChatColor.RED + "Aim at the Ancient Altar or one of its eight pedestals, then click again.");
            return;
        }

        pruneAltarLocks();
        Long lockExpires = observedAltarLocks.get(altar.getLocation());
        if (lockExpires != null && lockExpires >= System.currentTimeMillis()) {
            send(player, ChatColor.RED + "That Ancient Altar is currently active. Wait for the ritual to finish.");
            return;
        }

        List<Block> pedestals = getAncientAltarPedestals(altar);
        if (pedestals.size() != ALTAR_PEDESTAL_OFFSETS.length) {
            send(player, ChatColor.RED + "This Ancient Altar does not have all eight valid Ancient Pedestals.");
            return;
        }

        if (!isRegionOwned(altar)) {
            send(player, ChatColor.RED + "Move closer to the Ancient Altar and try again.");
            return;
        }
        if (!hasAccess(player, altar)) {
            send(player, ChatColor.RED + "You do not have permission to use this Ancient Altar.");
            return;
        }

        AncientPedestal pedestalItem = getAncientPedestalItem();
        if (pedestalItem == null) {
            send(player, ChatColor.RED + "The Ancient Pedestal item is unavailable.");
            return;
        }

        for (Block pedestal : pedestals) {
            if (!isRegionOwned(pedestal)) {
                send(player, ChatColor.RED + "Move closer so every pedestal is owned by the current region.");
                return;
            }
            if (!hasAccess(player, pedestal)) {
                send(player, ChatColor.RED + "You do not have permission to access every Ancient Pedestal.");
                return;
            }
            if (!pedestal.getRelative(BlockFace.UP).getType().isAir()) {
                send(player, ChatColor.RED + "Clear the block above every Ancient Pedestal first.");
                return;
            }
            if (pedestalItem.getPlacedItem(pedestal).isPresent()) {
                send(player, ChatColor.RED + "Remove the existing items from all Ancient Pedestals first.");
                return;
            }
        }

        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalPlayer = cloneContents(playerInventory.getStorageContents());
        AltarTransferPlan plan = planAncientAltar(
                originalPlayer,
                playerInventory.getHeldItemSlot(),
                context.recipe(),
                settings.shouldPrepareAltarCatalystInHand());
        if (!plan.success()) {
            send(player, ChatColor.RED + plan.message());
            sendMissingSummary(player, context, plan.missing());
            return;
        }

        List<PlacedPedestalItem> placed = new ArrayList<>();
        try {
            for (int index = 0; index < pedestals.size(); index++) {
                Block pedestal = pedestals.get(index);
                Item entity = placePedestalItem(player, pedestalItem, pedestal, plan.pedestalItems().get(index));
                if (entity == null) {
                    throw new IllegalStateException("Could not spawn an Ancient Altar pedestal item");
                }
                placed.add(new PlacedPedestalItem(pedestal, entity));
            }

            playerInventory.setStorageContents(plan.playerContents());
        } catch (RuntimeException exception) {
            rollbackAltarPlacement(pedestalItem, placed);
            try {
                playerInventory.setStorageContents(cloneContents(originalPlayer));
            } catch (RuntimeException ignored) {
                // The original exception is logged below.
            }
            plugin.getLogger().log(Level.SEVERE, "Could not commit an enhanced-guide Ancient Altar fill", exception);
            send(player, ChatColor.RED + "The altar preparation was cancelled and your inventory was restored.");
            return;
        }

        player.updateInventory();
        SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(player);
        String catalystName = ItemUtils.getItemName(context.recipe()[4]);
        if (settings.shouldPrepareAltarCatalystInHand()) {
            send(
                    player,
                    ChatColor.GREEN + "Prepared all eight Ancient Pedestals and selected " + ChatColor.WHITE
                            + catalystName + ChatColor.GREEN + ". Right-click the altar to begin.");
        } else {
            send(
                    player,
                    ChatColor.GREEN + "Prepared all eight Ancient Pedestals. Hold " + ChatColor.WHITE
                            + catalystName + ChatColor.GREEN + " and right-click the altar.");
        }
        closeAfterSuccess(player);
    }

    private void closeAfterSuccess(@Nonnull Player player) {
        if (LegacyGuideSettings.get().shouldCloseGuideAfterRecipeFill()) {
            player.closeInventory();
        }
    }

    private @Nonnull ItemStack createButton(@Nonnull Player player, @Nonnull RecipeFillContext context) {
        IngredientReport report = analyzeIngredients(
                cloneContents(player.getInventory().getStorageContents()),
                player.getInventory().getHeldItemSlot(),
                context.recipeType(),
                context.recipe(),
                context.kind(),
                1,
                LegacyGuideSettings.get().shouldPrepareAltarCatalystInHand());

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (context.kind() == RecipeKind.ANCIENT_ALTAR) {
            lore.add("&7Aim at the Ancient Altar");
            lore.add("&7or one of its pedestals.");
        } else {
            lore.add("&7Aim at the nearby " + context.machineName());
            lore.add("&7or its recipe dispenser.");
        }
        lore.add("");
        if (report.ready()) {
            lore.add("&aInventory check: Ready");
        } else {
            lore.add("&cMissing ingredients:");
            int maximumLines = LegacyGuideSettings.get().getRecipeFillMaximumMissingLines();
            int shown = 0;
            for (IngredientStatus status : report.statuses()) {
                if (status.missing() <= 0 || shown >= maximumLines) {
                    continue;
                }
                lore.add("&8- &f" + status.missing() + "x &7" + ItemUtils.getItemName(status.expected()));
                if (status.craftable() && LegacyGuideSettings.get().shouldShowSubRecipeHints()) {
                    lore.add("&8  ↳ &7Has a Slimefun recipe");
                }
                shown++;
            }
            long remaining = report.statuses().stream().filter(status -> status.missing() > 0).count() - shown;
            if (remaining > 0) {
                lore.add("&8... and " + remaining + " more");
            }
        }
        lore.add("");
        lore.add("&fLeft Click: &7Prepare one recipe");
        if (context.kind() != RecipeKind.ANCIENT_ALTAR) {
            lore.add("&fShift + Left Click: &7Prepare as many sets as fit");
        }
        lore.add("&fRight Click: &7Show the full ingredient report");
        lore.add("");
        lore.add("&8Items are moved, never crafted.");

        ItemStack button = new CustomItemStack(
                context.kind() == RecipeKind.ANCIENT_ALTAR ? Material.END_CRYSTAL : Material.HOPPER,
                context.kind() == RecipeKind.ANCIENT_ALTAR
                        ? "&d&lPrepare Ancient Altar"
                        : "&a&lFill Crafting Interface",
                lore.toArray(new String[0]));
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

    private void sendIngredientReport(@Nonnull Player player, @Nonnull RecipeFillContext context) {
        IngredientReport report = analyzeIngredients(
                cloneContents(player.getInventory().getStorageContents()),
                player.getInventory().getHeldItemSlot(),
                context.recipeType(),
                context.recipe(),
                context.kind(),
                1,
                LegacyGuideSettings.get().shouldPrepareAltarCatalystInHand());

        send(player, ChatColor.GOLD + "Ingredient report for " + ChatColor.WHITE + context.item().getItemName());
        for (IngredientStatus status : report.statuses()) {
            ChatColor color = status.missing() == 0 ? ChatColor.GREEN : ChatColor.RED;
            player.sendMessage(ChatColor.DARK_GRAY + " • " + color + status.available() + "/" + status.required()
                    + ChatColor.GRAY + " " + ItemUtils.getItemName(status.expected()));
            if (status.missing() > 0
                    && status.craftable()
                    && LegacyGuideSettings.get().shouldShowSubRecipeHints()) {
                SlimefunItem ingredient = SlimefunItem.getByItem(status.expected());
                if (ingredient != null) {
                    player.sendMessage(ChatColor.DARK_GRAY + "   ↳ " + ChatColor.GRAY + "Sub-recipe available: "
                            + ChatColor.WHITE + ingredient.getId());
                }
            }
        }
        if (report.ready()) {
            player.sendMessage(ChatColor.DARK_GREEN + "All required ingredients are available.");
        } else {
            player.sendMessage(ChatColor.DARK_RED + "Missing " + report.totalMissing() + " ingredient item(s).");
        }
    }

    private void sendMissingSummary(
            @Nonnull Player player,
            @Nonnull RecipeFillContext context,
            @Nonnull List<IngredientStatus> missingFromPlan) {
        if (missingFromPlan.isEmpty()) {
            return;
        }
        int limit = Math.min(3, missingFromPlan.size());
        for (int index = 0; index < limit; index++) {
            IngredientStatus status = missingFromPlan.get(index);
            player.sendMessage(ChatColor.DARK_GRAY + " • " + ChatColor.RED + status.missing() + "x "
                    + ChatColor.GRAY + ItemUtils.getItemName(status.expected()));
        }
        if (missingFromPlan.size() > limit) {
            player.sendMessage(ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + "Right-click the guide button for the full report.");
        } else if (LegacyGuideSettings.get().shouldShowSubRecipeHints()) {
            boolean hasSubRecipe = missingFromPlan.stream().anyMatch(IngredientStatus::craftable);
            if (hasSubRecipe) {
                player.sendMessage(ChatColor.DARK_GRAY + " • " + ChatColor.GRAY
                        + "Some missing ingredients have their own Slimefun recipes.");
            }
        }
    }

    private static @Nonnull TransferPlan planMaximum(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalTarget,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            @Nonnull RecipeKind kind,
            int maximumSets) {
        TransferPlan best = null;
        for (int sets = 1; sets <= maximumSets; sets++) {
            TransferPlan candidate = plan(originalPlayer, originalTarget, recipeType, recipe, kind, sets);
            if (!candidate.success()) {
                return best == null ? candidate : best;
            }
            best = candidate;
        }
        return best == null
                ? TransferPlan.failure("This recipe has no transferable ingredients.", List.of())
                : best;
    }

    private static @Nonnull TransferPlan plan(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalTarget,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            @Nonnull RecipeKind kind,
            int sets) {
        return kind == RecipeKind.UNORDERED_DISPENSER
                ? planUnordered(originalPlayer, originalTarget, recipeType, recipe, sets)
                : planShaped(originalPlayer, originalTarget, recipeType, recipe, sets);
    }

    private static @Nonnull TransferPlan planShaped(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalTarget,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            int sets) {
        ItemStack[] player = cloneContents(originalPlayer);
        ItemStack[] target = cloneContents(originalTarget);
        int moved = 0;
        List<IngredientStatus> missing = new ArrayList<>();

        for (int slot = 0; slot < 9; slot++) {
            ItemStack expected = recipe[slot];
            ItemStack current = target[slot];

            if (isEmpty(expected)) {
                if (!isEmpty(current)) {
                    return TransferPlan.failure("Clear the dispenser's unused recipe slots first.", List.of());
                }
                continue;
            }

            if (!isEmpty(current) && !matchesRecipeIngredient(recipeType, current, expected)) {
                return TransferPlan.failure(
                        "The dispenser contains an incompatible item in recipe slot " + (slot + 1) + '.',
                        List.of());
            }

            int required;
            try {
                required = Math.multiplyExact(Math.max(1, expected.getAmount()), sets);
            } catch (ArithmeticException exception) {
                return TransferPlan.failure("The requested recipe amount is too large.", List.of());
            }

            int currentAmount = isEmpty(current) ? 0 : current.getAmount();
            if (currentAmount >= required) {
                continue;
            }

            int maximumStack = isEmpty(current) ? expected.getMaxStackSize() : current.getMaxStackSize();
            if (required > maximumStack) {
                return TransferPlan.failure("The crafting grid cannot hold " + sets + " full recipe sets.", List.of());
            }

            int deficit = required - currentAmount;
            ItemStack movedStack = removeMatchingStackable(player, recipeType, expected, current, deficit);
            if (movedStack == null) {
                int available = countBestStackableMatch(player, recipeType, expected);
                missing.add(status(expected, deficit, available));
                continue;
            }

            if (isEmpty(current)) {
                target[slot] = movedStack;
            } else {
                current.setAmount(currentAmount + deficit);
            }
            moved += deficit;
        }

        if (!missing.isEmpty()) {
            return TransferPlan.failure("Not all recipe ingredients are available.", mergeStatuses(missing));
        }
        return TransferPlan.success(player, target, sets, moved);
    }

    private static @Nonnull TransferPlan planUnordered(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalTarget,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            int sets) {
        ItemStack[] player = cloneContents(originalPlayer);
        ItemStack[] target = cloneContents(originalTarget);
        ItemStack[] targetAllocation = cloneContents(originalTarget);
        List<Requirement> requirements = aggregateRequirements(recipe, sets);
        List<IngredientStatus> missing = new ArrayList<>();
        int moved = 0;

        if (requirements.isEmpty()) {
            return TransferPlan.failure("This recipe has no transferable ingredients.", List.of());
        }

        for (ItemStack current : target) {
            if (isEmpty(current)) {
                continue;
            }
            boolean valid = requirements.stream()
                    .anyMatch(requirement -> matchesUnorderedIngredient(current, requirement.expected()));
            if (!valid) {
                return TransferPlan.failure("Clear unrelated items from the machine dispenser first.", List.of());
            }
        }

        for (Requirement requirement : requirements) {
            int alreadyPresent = removeUpToAny(
                    targetAllocation,
                    requirement.expected(),
                    requirement.amount(),
                    LegacyRecipeFillManager::matchesUnorderedIngredient);
            int deficit = requirement.amount() - alreadyPresent;
            if (deficit <= 0) {
                continue;
            }

            Extraction extraction = extractAnyMatching(
                    player,
                    requirement.expected(),
                    deficit,
                    LegacyRecipeFillManager::matchesUnorderedIngredient);
            if (extraction.amount() < deficit) {
                missing.add(status(requirement.expected(), deficit, extraction.amount()));
                continue;
            }

            for (ItemStack stack : extraction.stacks()) {
                if (!addStack(target, stack, -1)) {
                    return TransferPlan.failure(
                            "The machine dispenser does not have enough room for " + sets + " recipe sets.",
                            List.of());
                }
                moved += stack.getAmount();
            }
        }

        if (!missing.isEmpty()) {
            return TransferPlan.failure("Not all recipe ingredients are available.", mergeStatuses(missing));
        }
        return TransferPlan.success(player, target, sets, moved);
    }

    private static @Nonnull AltarTransferPlan planAncientAltar(
            @Nonnull ItemStack[] originalPlayer,
            int heldSlot,
            @Nonnull ItemStack[] recipe,
            boolean prepareCatalystInHand) {
        ItemStack[] player = cloneContents(originalPlayer);
        List<ItemStack> pedestalItems = new ArrayList<>(ALTAR_RECIPE_SLOTS.length);
        List<IngredientStatus> missing = new ArrayList<>();

        for (int recipeSlot : ALTAR_RECIPE_SLOTS) {
            ItemStack expected = recipe[recipeSlot];
            if (isEmpty(expected)) {
                return AltarTransferPlan.failure(
                        "This Ancient Altar recipe does not define all eight pedestal ingredients.", List.of());
            }
            Extraction extraction = extractAnyMatching(
                    player, expected, 1, LegacyRecipeFillManager::matchesAltarIngredient);
            if (extraction.amount() < 1) {
                missing.add(status(expected, 1, extraction.amount()));
            } else {
                ItemStack placed = extraction.stacks().get(0).clone();
                placed.setAmount(1);
                pedestalItems.add(placed);
            }
        }

        ItemStack catalyst = recipe[4];
        if (isEmpty(catalyst)) {
            return AltarTransferPlan.failure("This Ancient Altar recipe has no catalyst.", List.of());
        }

        if (prepareCatalystInHand) {
            ItemStack currentHand = heldSlot >= 0 && heldSlot < player.length ? player[heldSlot] : null;
            if (!matchesAltarIngredient(currentHand, catalyst)) {
                Extraction extraction = extractAnyMatching(
                        player, catalyst, 1, LegacyRecipeFillManager::matchesAltarIngredient);
                if (extraction.amount() < 1) {
                    missing.add(status(catalyst, 1, extraction.amount()));
                } else if (heldSlot < 0 || heldSlot >= player.length) {
                    return AltarTransferPlan.failure("The selected hotbar slot is unavailable.", List.of());
                } else {
                    ItemStack catalystStack = extraction.stacks().get(0).clone();
                    catalystStack.setAmount(1);
                    ItemStack displaced = cloneItem(player[heldSlot]);
                    player[heldSlot] = null;
                    if (!isEmpty(displaced) && !addStack(player, displaced, heldSlot)) {
                        return AltarTransferPlan.failure(
                                "Clear one inventory slot so the altar catalyst can be selected.", List.of());
                    }
                    player[heldSlot] = catalystStack;
                }
            }
        } else {
            int available = countAnyMatching(player, catalyst, LegacyRecipeFillManager::matchesAltarIngredient);
            if (available < 1) {
                missing.add(status(catalyst, 1, available));
            }
        }

        if (!missing.isEmpty()) {
            return AltarTransferPlan.failure("Not all Ancient Altar ingredients are available.", mergeStatuses(missing));
        }
        if (pedestalItems.size() != ALTAR_RECIPE_SLOTS.length) {
            return AltarTransferPlan.failure("Could not allocate all Ancient Altar ingredients.", List.of());
        }
        return AltarTransferPlan.success(player, pedestalItems);
    }

    private static @Nonnull IngredientReport analyzeIngredients(
            @Nonnull ItemStack[] originalPlayer,
            int heldSlot,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack[] recipe,
            @Nonnull RecipeKind kind,
            int sets,
            boolean prepareCatalystInHand) {
        ItemStack[] player = cloneContents(originalPlayer);
        List<IngredientStatus> statuses = new ArrayList<>();

        if (kind == RecipeKind.ANCIENT_ALTAR) {
            for (int recipeSlot : ALTAR_RECIPE_SLOTS) {
                ItemStack expected = recipe[recipeSlot];
                if (isEmpty(expected)) {
                    continue;
                }
                int available = removeUpToAny(
                        player, expected, 1, LegacyRecipeFillManager::matchesAltarIngredient);
                statuses.add(status(expected, 1, available));
            }
            ItemStack catalyst = recipe[4];
            if (!isEmpty(catalyst)) {
                ItemStack currentHand = heldSlot >= 0 && heldSlot < player.length ? player[heldSlot] : null;
                int available;
                if (prepareCatalystInHand && matchesAltarIngredient(currentHand, catalyst)) {
                    available = 1;
                } else {
                    available = removeUpToAny(
                            player, catalyst, 1, LegacyRecipeFillManager::matchesAltarIngredient);
                }
                statuses.add(status(catalyst, 1, available));
            }
        } else if (kind == RecipeKind.UNORDERED_DISPENSER) {
            for (Requirement requirement : aggregateRequirements(recipe, sets)) {
                int available = removeUpToAny(
                        player,
                        requirement.expected(),
                        requirement.amount(),
                        LegacyRecipeFillManager::matchesUnorderedIngredient);
                statuses.add(status(requirement.expected(), requirement.amount(), available));
            }
        } else {
            for (ItemStack expected : recipe) {
                if (isEmpty(expected)) {
                    continue;
                }
                int required;
                try {
                    required = Math.multiplyExact(Math.max(1, expected.getAmount()), sets);
                } catch (ArithmeticException exception) {
                    required = Integer.MAX_VALUE;
                }
                int available = removeUpToBestStackable(player, recipeType, expected, required);
                statuses.add(status(expected, required, available));
            }
        }

        List<IngredientStatus> merged = mergeStatuses(statuses);
        int totalMissing = merged.stream().mapToInt(IngredientStatus::missing).sum();
        return new IngredientReport(totalMissing == 0, merged, totalMissing);
    }

    private static @Nonnull List<Requirement> aggregateRequirements(@Nonnull ItemStack[] recipe, int sets) {
        List<Requirement> requirements = new ArrayList<>();
        for (ItemStack expected : recipe) {
            if (isEmpty(expected)) {
                continue;
            }
            int amount;
            try {
                amount = Math.multiplyExact(Math.max(1, expected.getAmount()), sets);
            } catch (ArithmeticException exception) {
                amount = Integer.MAX_VALUE;
            }

            Requirement existing = null;
            for (Requirement requirement : requirements) {
                if (canStackTogether(requirement.expected(), expected)) {
                    existing = requirement;
                    break;
                }
            }
            if (existing == null) {
                requirements.add(new Requirement(expected.clone(), amount));
            } else {
                existing.add(amount);
            }
        }
        return requirements;
    }

    private static @Nonnull List<IngredientStatus> mergeStatuses(@Nonnull List<IngredientStatus> statuses) {
        Map<String, MutableIngredientStatus> merged = new LinkedHashMap<>();
        for (IngredientStatus status : statuses) {
            String id = Optional.ofNullable(SlimefunItem.getByItem(status.expected()))
                    .map(SlimefunItem::getId)
                    .orElse(status.expected().getType().getKey().toString());
            String key = id + '|' + ItemUtils.getItemName(status.expected());
            MutableIngredientStatus existing = merged.get(key);
            if (existing == null) {
                merged.put(
                        key,
                        new MutableIngredientStatus(
                                status.expected().clone(),
                                status.required(),
                                status.available(),
                                status.craftable()));
            } else {
                existing.required += status.required();
                existing.available += status.available();
                existing.craftable |= status.craftable();
            }
        }

        return merged.values().stream()
                .map(value -> new IngredientStatus(
                        value.expected,
                        value.required,
                        Math.min(value.required, value.available),
                        Math.max(0, value.required - value.available),
                        value.craftable))
                .toList();
    }

    private static @Nonnull IngredientStatus status(
            @Nonnull ItemStack expected, int required, int available) {
        int safeAvailable = Math.max(0, Math.min(required, available));
        SlimefunItem ingredient = SlimefunItem.getByItem(expected);
        boolean craftable = ingredient != null
                && ingredient.getRecipeType() != null
                && !RecipeType.NULL.equals(ingredient.getRecipeType());
        return new IngredientStatus(
                expected.clone(), required, safeAvailable, Math.max(0, required - safeAvailable), craftable);
    }

    private static @Nullable ItemStack removeMatchingStackable(
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

        ItemStack[] trial = cloneContents(contents);
        int remaining = amount;
        for (int slot = 0; slot < trial.length && remaining > 0; slot++) {
            ItemStack source = trial[slot];
            if (isEmpty(source)
                    || !matchesRecipeIngredient(recipeType, source, expected)
                    || !canStackTogether(source, template)) {
                continue;
            }

            int taken = Math.min(remaining, source.getAmount());
            remaining -= taken;
            decrement(trial, slot, taken);
        }

        if (remaining > 0) {
            return null;
        }

        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = cloneItem(trial[slot]);
        }
        template.setAmount(amount);
        return template;
    }

    private static int removeUpToBestStackable(
            @Nonnull ItemStack[] contents,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack expected,
            int amount) {
        ItemStack template = findBestStackableTemplate(contents, recipeType, expected);
        if (template == null) {
            return 0;
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
            decrement(contents, slot, taken);
        }
        return amount - remaining;
    }

    private static int countBestStackableMatch(
            @Nonnull ItemStack[] contents,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack expected) {
        ItemStack template = findBestStackableTemplate(contents, recipeType, expected);
        if (template == null) {
            return 0;
        }
        int available = 0;
        for (ItemStack source : contents) {
            if (!isEmpty(source)
                    && matchesRecipeIngredient(recipeType, source, expected)
                    && canStackTogether(source, template)) {
                available += source.getAmount();
            }
        }
        return available;
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

    private static @Nullable ItemStack findBestStackableTemplate(
            @Nonnull ItemStack[] contents,
            @Nonnull RecipeType recipeType,
            @Nonnull ItemStack expected) {
        ItemStack best = null;
        int bestAmount = 0;
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
                }
            }
            if (available > bestAmount) {
                bestAmount = available;
                best = candidate.clone();
            }
        }
        return best;
    }

    private static @Nonnull Extraction extractAnyMatching(
            @Nonnull ItemStack[] contents,
            @Nonnull ItemStack expected,
            int amount,
            @Nonnull IngredientMatcher matcher) {
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack source = contents[slot];
            if (isEmpty(source) || !matcher.matches(source, expected)) {
                continue;
            }
            int taken = Math.min(remaining, source.getAmount());
            ItemStack moved = source.clone();
            moved.setAmount(taken);
            extracted.add(moved);
            decrement(contents, slot, taken);
            remaining -= taken;
        }
        return new Extraction(extracted, amount - remaining);
    }

    private static int removeUpToAny(
            @Nonnull ItemStack[] contents,
            @Nonnull ItemStack expected,
            int amount,
            @Nonnull IngredientMatcher matcher) {
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack source = contents[slot];
            if (isEmpty(source) || !matcher.matches(source, expected)) {
                continue;
            }
            int taken = Math.min(remaining, source.getAmount());
            decrement(contents, slot, taken);
            remaining -= taken;
        }
        return amount - remaining;
    }

    private static int countAnyMatching(
            @Nonnull ItemStack[] contents,
            @Nonnull ItemStack expected,
            @Nonnull IngredientMatcher matcher) {
        int available = 0;
        for (ItemStack source : contents) {
            if (!isEmpty(source) && matcher.matches(source, expected)) {
                available += source.getAmount();
            }
        }
        return available;
    }

    private static boolean addStack(@Nonnull ItemStack[] contents, @Nonnull ItemStack stack, int excludedSlot) {
        ItemStack remaining = stack.clone();
        for (int slot = 0; slot < contents.length && remaining.getAmount() > 0; slot++) {
            if (slot == excludedSlot) {
                continue;
            }
            ItemStack current = contents[slot];
            if (isEmpty(current) || !canStackTogether(current, remaining)) {
                continue;
            }
            int space = current.getMaxStackSize() - current.getAmount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.getAmount());
            current.setAmount(current.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        for (int slot = 0; slot < contents.length && remaining.getAmount() > 0; slot++) {
            if (slot == excludedSlot || !isEmpty(contents[slot])) {
                continue;
            }
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            contents[slot] = placed;
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount() == 0;
    }

    private static void decrement(@Nonnull ItemStack[] contents, int slot, int amount) {
        ItemStack source = contents[slot];
        if (source == null || amount >= source.getAmount()) {
            contents[slot] = null;
        } else {
            source.setAmount(source.getAmount() - amount);
        }
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

    private static boolean matchesUnorderedIngredient(@Nullable ItemStack actual, @Nullable ItemStack expected) {
        return SlimefunUtils.isItemSimilar(actual, expected, true);
    }

    private static boolean matchesAltarIngredient(@Nullable ItemStack actual, @Nullable ItemStack expected) {
        if (isEmpty(actual) || isEmpty(expected)) {
            return false;
        }
        if (SlimefunUtils.isItemSimilar(
                expected, SlimefunItems.BROKEN_SPAWNER, false, false, false, false)) {
            return SlimefunUtils.isItemSimilar(actual, expected, false, false, false, false);
        }
        return SlimefunUtils.isItemSimilar(actual, expected, true);
    }

    private static boolean canStackTogether(@Nullable ItemStack first, @Nullable ItemStack second) {
        return SlimefunUtils.isItemSimilar(first, second, true, false, true, true);
    }

    private static boolean isBackpack(@Nullable ItemStack item) {
        return SlimefunItem.getByItem(item) instanceof SlimefunBackpack;
    }

    private static @Nonnull RecipeKind classify(
            @Nonnull RecipeType recipeType,
            @Nullable ItemStack[] recipe,
            @Nonnull LegacyGuideSettings settings) {
        if (recipe == null) {
            return RecipeKind.UNSUPPORTED;
        }
        if (RecipeType.ENHANCED_CRAFTING_TABLE.equals(recipeType)
                || RecipeType.MAGIC_WORKBENCH.equals(recipeType)
                || RecipeType.ARMOR_FORGE.equals(recipeType)) {
            return RecipeKind.SHAPED_DISPENSER;
        }
        if (RecipeType.ANCIENT_ALTAR.equals(recipeType)
                && settings.hasRecipeFillAncientAltar()
                && isValidAncientAltarRecipe(recipe)) {
            return RecipeKind.ANCIENT_ALTAR;
        }
        if (settings.hasRecipeFillUnorderedMachines() && isSupportedUnorderedType(recipeType)) {
            return RecipeKind.UNORDERED_DISPENSER;
        }
        return RecipeKind.UNSUPPORTED;
    }

    private static boolean isSupportedUnorderedType(@Nonnull RecipeType recipeType) {
        return RecipeType.GRIND_STONE.equals(recipeType)
                || RecipeType.SMELTERY.equals(recipeType)
                || RecipeType.ORE_CRUSHER.equals(recipeType)
                || RecipeType.COMPRESSOR.equals(recipeType)
                || RecipeType.PRESSURE_CHAMBER.equals(recipeType);
    }

    private static boolean isValidAncientAltarRecipe(@Nonnull ItemStack[] recipe) {
        if (isEmpty(recipe[4])) {
            return false;
        }
        for (int slot : ALTAR_RECIPE_SLOTS) {
            if (isEmpty(recipe[slot])) {
                return false;
            }
        }
        return true;
    }

    private static @Nonnull String machineName(@Nonnull RecipeType recipeType) {
        if (RecipeType.MAGIC_WORKBENCH.equals(recipeType)) {
            return "Magic Workbench";
        }
        if (RecipeType.ARMOR_FORGE.equals(recipeType)) {
            return "Armor Forge";
        }
        if (RecipeType.ANCIENT_ALTAR.equals(recipeType)) {
            return "Ancient Altar";
        }
        if (RecipeType.GRIND_STONE.equals(recipeType)) {
            return "Grind Stone";
        }
        if (RecipeType.SMELTERY.equals(recipeType)) {
            return "Smeltery";
        }
        if (RecipeType.ORE_CRUSHER.equals(recipeType)) {
            return "Ore Crusher";
        }
        if (RecipeType.COMPRESSOR.equals(recipeType)) {
            return "Compressor";
        }
        if (RecipeType.PRESSURE_CHAMBER.equals(recipeType)) {
            return "Pressure Chamber";
        }
        return "Enhanced Crafting Table";
    }

    private static @Nullable ResolvedDispenser resolveDispenser(
            @Nonnull RecipeFillContext context, @Nullable Block target) {
        if (target == null) {
            return null;
        }
        RecipeType recipeType = context.recipeType();
        if (RecipeType.ENHANCED_CRAFTING_TABLE.equals(recipeType)) {
            Block dispenser = findVerticalDispenser(target, Material.CRAFTING_TABLE);
            return dispenser == null ? null : new ResolvedDispenser(dispenser, target);
        }
        if (RecipeType.ARMOR_FORGE.equals(recipeType)) {
            Block dispenser = findVerticalDispenser(target, Material.ANVIL);
            return isFacing(dispenser, BlockFace.UP) ? new ResolvedDispenser(dispenser, target) : null;
        }
        if (RecipeType.MAGIC_WORKBENCH.equals(recipeType)) {
            Block dispenser = findMagicWorkbenchDispenser(target);
            return dispenser == null ? null : new ResolvedDispenser(dispenser, target);
        }
        if (context.kind() == RecipeKind.UNORDERED_DISPENSER) {
            return findUnorderedMachineDispenser(recipeType, target);
        }
        return null;
    }

    private static @Nullable ResolvedDispenser findUnorderedMachineDispenser(
            @Nonnull RecipeType recipeType, @Nonnull Block target) {
        SlimefunItem machineItem = recipeType.getMachine();
        if (!(machineItem instanceof MultiBlockMachine machine)) {
            return null;
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block trigger = target.getRelative(x, y, z);
                    MachineMatch match = matchMachine(machine, trigger);
                    if (match == null || !match.structureBlocks().contains(target)) {
                        continue;
                    }
                    BlockFace requiredFacing = RecipeType.PRESSURE_CHAMBER.equals(recipeType)
                            ? BlockFace.DOWN
                            : BlockFace.UP;
                    if (!isFacing(match.dispenser(), requiredFacing)) {
                        continue;
                    }
                    return new ResolvedDispenser(match.dispenser(), trigger);
                }
            }
        }
        return null;
    }

    private static @Nullable MachineMatch matchMachine(
            @Nonnull MultiBlockMachine machine, @Nonnull Block trigger) {
        MultiBlock multiBlock = machine.getMultiBlock();
        Block center = trigger.getRelative(multiBlock.getTriggerBlock());
        BlockFace[] directions = multiBlock.isSymmetric()
                ? new BlockFace[] {BlockFace.NORTH, BlockFace.EAST}
                : HORIZONTAL_FACES;

        for (BlockFace direction : directions) {
            Block[] blocks = mapStructure(center, direction);
            Material[] expected = multiBlock.getStructure();
            boolean matches = true;
            Block dispenser = null;
            for (int slot = 0; slot < expected.length; slot++) {
                Material expectedMaterial = expected[slot];
                if (expectedMaterial != null && !materialsEqual(blocks[slot].getType(), expectedMaterial)) {
                    matches = false;
                    break;
                }
                if (expectedMaterial == Material.DISPENSER) {
                    dispenser = blocks[slot];
                }
            }
            if (matches && dispenser != null) {
                return new MachineMatch(dispenser, Arrays.asList(blocks));
            }
        }
        return null;
    }

    private static @Nonnull Block[] mapStructure(@Nonnull Block center, @Nonnull BlockFace direction) {
        Block side = center.getRelative(direction);
        Block opposite = center.getRelative(direction.getOppositeFace());
        return new Block[] {
            side.getRelative(BlockFace.UP),
            center.getRelative(BlockFace.UP),
            opposite.getRelative(BlockFace.UP),
            side,
            center,
            opposite,
            side.getRelative(BlockFace.DOWN),
            center.getRelative(BlockFace.DOWN),
            opposite.getRelative(BlockFace.DOWN)
        };
    }

    private static boolean materialsEqual(@Nonnull Material actual, @Nonnull Material expected) {
        if (actual == expected) {
            return true;
        }
        for (Tag<Material> tag : MultiBlock.getSupportedTags()) {
            if (tag.isTagged(actual) && tag.isTagged(expected)) {
                return true;
            }
        }
        if (expected == Material.PISTON) {
            return actual == Material.PISTON || actual == Material.MOVING_PISTON;
        }
        return actual == Material.PISTON && expected == Material.MOVING_PISTON;
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

    private static boolean isFacing(@Nullable Block block, @Nonnull BlockFace face) {
        return block != null
                && block.getBlockData() instanceof Directional directional
                && directional.getFacing() == face;
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

    private static @Nullable Block resolveAncientAltar(@Nullable Block target) {
        if (target == null) {
            return null;
        }
        String altarId = SlimefunItems.ANCIENT_ALTAR.getItemId();
        String pedestalId = SlimefunItems.ANCIENT_PEDESTAL.getItemId();
        if (StorageCacheUtils.isBlock(target.getLocation(), altarId)) {
            return target;
        }
        if (!StorageCacheUtils.isBlock(target.getLocation(), pedestalId)) {
            return null;
        }
        for (int[] offset : ALTAR_PEDESTAL_OFFSETS) {
            Block candidate = target.getRelative(-offset[0], -offset[1], -offset[2]);
            if (StorageCacheUtils.isBlock(candidate.getLocation(), altarId)) {
                return candidate;
            }
        }
        return null;
    }

    private static @Nonnull List<Block> getAncientAltarPedestals(@Nonnull Block altar) {
        List<Block> pedestals = new ArrayList<>(ALTAR_PEDESTAL_OFFSETS.length);
        String pedestalId = SlimefunItems.ANCIENT_PEDESTAL.getItemId();
        for (int[] offset : ALTAR_PEDESTAL_OFFSETS) {
            Block pedestal = altar.getRelative(offset[0], offset[1], offset[2]);
            if (!StorageCacheUtils.isBlock(pedestal.getLocation(), pedestalId)) {
                return List.of();
            }
            pedestals.add(pedestal);
        }
        return pedestals;
    }

    private @Nullable Item placePedestalItem(
            @Nonnull Player player,
            @Nonnull AncientPedestal pedestalItem,
            @Nonnull Block pedestal,
            @Nonnull ItemStack original) {
        String displayName = AncientPedestal.ITEM_PREFIX + System.nanoTime();
        ItemStack displayItem = new CustomItemStack(original, displayName);
        displayItem.setAmount(1);
        String nametag = ItemUtils.getItemName(original);

        Item entity = SlimefunUtils.spawnItem(
                pedestal.getLocation().add(0.5, 1.2, 0.5),
                displayItem,
                ItemSpawnReason.ANCIENT_PEDESTAL_PLACE_ITEM,
                false,
                player);
        if (entity == null) {
            return null;
        }

        ArmorStand armorStand = pedestalItem.getArmorStand(pedestal, true);
        if (armorStand == null) {
            entity.remove();
            return null;
        }

        try {
            entity.setInvulnerable(true);
            entity.setVelocity(new Vector(0, 0.1, 0));
            entity.setCustomNameVisible(true);
            entity.setCustomName(nametag);
            armorStand.setCustomName(displayName);
            armorStand.addPassenger(entity);
            SlimefunUtils.markAsNoPickup(entity, "altar_item");
            SoundEffect.ANCIENT_PEDESTAL_ITEM_PLACE_SOUND.playAt(pedestal);
            return entity;
        } catch (RuntimeException exception) {
            entity.removeMetadata("no_pickup", plugin);
            entity.remove();
            if (armorStand.isValid()) {
                armorStand.remove();
            }
            throw exception;
        }
    }

    private void rollbackAltarPlacement(
            @Nonnull AncientPedestal pedestalItem, @Nonnull List<PlacedPedestalItem> placed) {
        for (PlacedPedestalItem placement : placed) {
            Item entity = placement.entity();
            if (entity.isValid()) {
                entity.removeMetadata("no_pickup", plugin);
                entity.remove();
            }
            ArmorStand armorStand = pedestalItem.getArmorStand(placement.pedestal(), false);
            if (armorStand != null && armorStand.isValid()) {
                armorStand.remove();
            }
        }
    }

    private static @Nullable AncientPedestal getAncientPedestalItem() {
        SlimefunItem item = SlimefunItems.ANCIENT_PEDESTAL.getItem();
        return item instanceof AncientPedestal pedestal ? pedestal : null;
    }

    private void pruneAltarLocks() {
        long now = System.currentTimeMillis();
        observedAltarLocks.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static boolean isRegionOwned(@Nonnull Block block) {
        return Slimefun.getSchedulerService().isOwnedByCurrentRegion(block.getLocation());
    }

    private static boolean hasAccess(@Nonnull Player player, @Nonnull Block block) {
        return Slimefun.getProtectionManager().hasPermission(player, block, Interaction.INTERACT_BLOCK);
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

    private enum RecipeKind {
        SHAPED_DISPENSER,
        UNORDERED_DISPENSER,
        ANCIENT_ALTAR,
        UNSUPPORTED
    }

    @FunctionalInterface
    private interface IngredientMatcher {
        boolean matches(@Nullable ItemStack actual, @Nullable ItemStack expected);
    }

    private record RecipeFillContext(
            Inventory guideInventory,
            SlimefunItem item,
            RecipeType recipeType,
            ItemStack[] recipe,
            RecipeKind kind,
            String machineName,
            long expiresAt) {}

    private record ResolvedDispenser(Block dispenser, Block interactionBlock) {}

    private record MachineMatch(Block dispenser, List<Block> structureBlocks) {}

    private record PlacedPedestalItem(Block pedestal, Item entity) {}

    private record Extraction(List<ItemStack> stacks, int amount) {}

    private static final class Requirement {
        private final ItemStack expected;
        private int amount;

        private Requirement(@Nonnull ItemStack expected, int amount) {
            this.expected = expected;
            this.amount = amount;
        }

        private @Nonnull ItemStack expected() {
            return expected;
        }

        private int amount() {
            return amount;
        }

        private void add(int additional) {
            try {
                amount = Math.addExact(amount, additional);
            } catch (ArithmeticException exception) {
                amount = Integer.MAX_VALUE;
            }
        }
    }

    private static final class MutableIngredientStatus {
        private final ItemStack expected;
        private int required;
        private int available;
        private boolean craftable;

        private MutableIngredientStatus(
                @Nonnull ItemStack expected, int required, int available, boolean craftable) {
            this.expected = expected;
            this.required = required;
            this.available = available;
            this.craftable = craftable;
        }
    }

    private record IngredientStatus(
            ItemStack expected, int required, int available, int missing, boolean craftable) {}

    private record IngredientReport(boolean ready, List<IngredientStatus> statuses, int totalMissing) {}

    private record TransferPlan(
            boolean success,
            ItemStack[] playerContents,
            ItemStack[] targetContents,
            int sets,
            int movedItems,
            String message,
            List<IngredientStatus> missing) {

        private static @Nonnull TransferPlan success(
                @Nonnull ItemStack[] playerContents,
                @Nonnull ItemStack[] targetContents,
                int sets,
                int movedItems) {
            return new TransferPlan(true, playerContents, targetContents, sets, movedItems, "", List.of());
        }

        private static @Nonnull TransferPlan failure(
                @Nonnull String message, @Nonnull List<IngredientStatus> missing) {
            return new TransferPlan(false, new ItemStack[0], new ItemStack[0], 0, 0, message, missing);
        }
    }

    private record AltarTransferPlan(
            boolean success,
            ItemStack[] playerContents,
            List<ItemStack> pedestalItems,
            String message,
            List<IngredientStatus> missing) {

        private static @Nonnull AltarTransferPlan success(
                @Nonnull ItemStack[] playerContents, @Nonnull List<ItemStack> pedestalItems) {
            return new AltarTransferPlan(true, playerContents, pedestalItems, "", List.of());
        }

        private static @Nonnull AltarTransferPlan failure(
                @Nonnull String message, @Nonnull List<IngredientStatus> missing) {
            return new AltarTransferPlan(false, new ItemStack[0], List.of(), message, missing);
        }
    }
}
