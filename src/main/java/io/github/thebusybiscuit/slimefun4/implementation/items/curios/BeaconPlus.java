package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Native Slimefun Legacy Beacon Plus.
 *
 * <p>The 29-effect menu and runtime are independent of any external beacon plugin. Periodic effects share one
 * Slimefun block ticker, event-driven effects share one listener, and Activator chunk loading remains bounded by the
 * Beacon Plus manager's server-wide safety caps.
 */
public final class BeaconPlus extends SlimefunItem {

    private static final int[] EFFECT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37
    };

    private static final int STATUS_SLOT = 4;
    private static final int DISABLE_ALL_SLOT = 47;
    private static final int ACTIVATOR_COVERAGE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final double EXTRA_RANGE_BLOCKS = 20.0D;
    private static final double PLAYER_STATE_RECONCILE_RANGE = 96.0D;

    @ParametersAreNonnullByDefault
    public BeaconPlus(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onPlace(), onUse(), onBreak(), createTicker());
    }

    @Override
    public void postRegister() {
        if (isDisabled()) {
            return;
        }

        BeaconPlusLifecycleListener.register(Slimefun.instance());
        BeaconPlusEffectListener.register(Slimefun.instance());
        Slimefun.getSchedulerService().runLater(() -> {
            if (BeaconPlusManager.getInstance() == null) {
                BeaconPlusManager.start(Slimefun.instance());
            }
        }, 1L);
    }

    private @Nonnull BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                Block block = event.getBlockPlaced();
                Location location = block.getLocation();
                UUID owner = event.getPlayer().getUniqueId();

                StorageCacheUtils.setData(location, BeaconPlusManager.OWNER_KEY, owner.toString());
                StorageCacheUtils.setData(location, BeaconPlusManager.CHUNK_MODE_KEY, BeaconPlusChunkMode.OFF.name());
                StorageCacheUtils.setData(location, BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());
                StorageCacheUtils.setData(location, BeaconPlusRuntime.EFFECTS_KEY, "");

                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.register(location, owner);
                }
                BeaconPlusRuntime.observe(block);

                event.getPlayer().sendMessage(ChatColor.GOLD + "Beacon Plus placed. " + ChatColor.GRAY
                        + "Build a beacon pyramid, then right click it to configure all 29 effects.");
            }
        };
    }

    private @Nonnull BlockUseHandler onUse() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            Block block = event.getClickedBlock().orElse(null);
            if (block == null) {
                return;
            }

            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager == null) {
                player.sendMessage(ChatColor.RED + "Beacon Plus is still initializing. Try again in a moment.");
                return;
            }

            UUID owner = manager.getOwner(block.getLocation());
            if (!canConfigure(player, owner)) {
                player.sendMessage(ChatColor.RED + "Only this Beacon Plus owner or a server operator can configure it.");
                return;
            }

            BeaconPlusRuntime.observe(block);
            openMenu(player, block, ownerOrPlayer(owner, player));
        };
    }

    private @Nonnull SimpleBlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block block) {
                BeaconPlusRuntime.forget(block.getLocation());
                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.unregister(block.getLocation());
                }
                BeaconPlusRuntime.refreshNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
            }
        };
    }

    private @Nonnull BlockTicker createTicker() {
        return new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block block, SlimefunItem item, ASlimefunDataContainer data) {
                BeaconPlusRuntime.tick(block, data);
            }
        };
    }

    private void openMenu(Player player, Block block, UUID owner) {
        if (!StorageCacheUtils.isBlock(block.getLocation(), getId())) {
            player.closeInventory();
            return;
        }

        ChestMenu menu = new ChestMenu("&6&lBeacon Plus", 54);
        menu.setPlayerInventoryClickable(false);
        menu.setEmptySlotsClickable(false);

        EnumSet<BeaconPlusEffect> enabled = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        BeaconPlusChunkMode chunkMode = manager == null
                ? BeaconPlusChunkMode.OFF
                : manager.getChunkMode(block.getLocation());

        menu.addItem(STATUS_SLOT, createStatusItem(block, enabled, chunkMode));

        BeaconPlusEffect[] effects = BeaconPlusEffect.values();
        for (int index = 0; index < effects.length; index++) {
            BeaconPlusEffect effect = effects[index];
            int slot = EFFECT_SLOTS[index];
            boolean active = enabled.contains(effect);
            menu.addItem(slot, createEffectItem(effect, active, chunkMode));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, item, action) -> {
                toggleEffect(pl, block, owner, effect);
                return false;
            });
        }

        menu.addItem(
                DISABLE_ALL_SLOT,
                createMenuItem(
                        Material.BARRIER,
                        ChatColor.RED + "Disable All Effects",
                        List.of(
                                ChatColor.GRAY + "Turns off every Beacon Plus effect",
                                ChatColor.GRAY + "including the Activator chunk loader.",
                                "",
                                ChatColor.YELLOW + "Click to disable everything")));
        menu.addMenuClickHandler(DISABLE_ALL_SLOT, (pl, slot, item, action) -> {
            disableAll(pl, block, owner);
            return false;
        });

        menu.addItem(ACTIVATOR_COVERAGE_SLOT, createActivatorCoverageItem(chunkMode));
        menu.addMenuClickHandler(ACTIVATOR_COVERAGE_SLOT, (pl, slot, item, action) -> {
            cycleActivatorCoverage(pl, block, owner);
            return false;
        });

        menu.addItem(
                CLOSE_SLOT,
                createMenuItem(
                        Material.RED_STAINED_GLASS_PANE,
                        ChatColor.RED + "Close",
                        List.of(ChatColor.GRAY + "Close Beacon Plus configuration.")));
        menu.addMenuClickHandler(CLOSE_SLOT, (pl, slot, item, action) -> {
            pl.closeInventory();
            return false;
        });

        menu.open(player);
    }

    private void toggleEffect(Player player, Block block, UUID owner, BeaconPlusEffect effect) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        if (effect == BeaconPlusEffect.ACTIVATOR) {
            toggleActivator(player, block, owner);
            return;
        }

        EnumSet<BeaconPlusEffect> enabled = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        if (!enabled.remove(effect)) {
            enabled.add(effect);
        }
        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
        BeaconPlusRuntime.observe(block);
        BeaconPlusRuntime.refreshNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);

        boolean active = enabled.contains(effect);
        player.playSound(
                block.getLocation(),
                Sound.BLOCK_BEACON_POWER_SELECT,
                0.65F,
                active ? 1.35F : 0.85F);
        player.sendMessage(ChatColor.GOLD + "Beacon Plus: " + ChatColor.WHITE + effect.getDisplayName() + ChatColor.GRAY
                + " is now " + (active ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED") + ChatColor.GRAY + ".");
        openMenu(player, block, owner);
    }

    private void toggleActivator(Player player, Block block, UUID owner) {
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            player.sendMessage(ChatColor.RED + "Beacon Plus chunk loading is not ready.");
            return;
        }

        BeaconPlusChunkMode current = manager.getChunkMode(block.getLocation());
        BeaconPlusChunkMode next = current == BeaconPlusChunkMode.OFF ? BeaconPlusChunkMode.SINGLE : BeaconPlusChunkMode.OFF;
        if (!setChunkMode(manager, block, owner, next)) {
            player.sendMessage(ChatColor.RED + "The Beacon Plus chunk-loader safety cap would be exceeded.");
            return;
        }

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.1F);
        player.sendMessage(ChatColor.AQUA + "Beacon Plus Activator: " + ChatColor.WHITE + next.getDisplayName());
        openMenu(player, block, owner);
    }

    private void cycleActivatorCoverage(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            player.sendMessage(ChatColor.RED + "Beacon Plus chunk loading is not ready.");
            return;
        }

        BeaconPlusChunkMode current = manager.getChunkMode(block.getLocation());
        BeaconPlusChunkMode next = switch (current) {
            case OFF -> BeaconPlusChunkMode.SINGLE;
            case SINGLE -> BeaconPlusChunkMode.AREA_3X3;
            case AREA_3X3 -> BeaconPlusChunkMode.SINGLE;
        };

        if (!setChunkMode(manager, block, owner, next)) {
            player.sendMessage(ChatColor.RED + "The requested coverage would exceed the Beacon Plus safety cap.");
            return;
        }

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.25F);
        player.sendMessage(ChatColor.AQUA + "Activator coverage: " + ChatColor.WHITE + next.getDisplayName());
        openMenu(player, block, owner);
    }

    private void disableAll(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), EnumSet.noneOf(BeaconPlusEffect.class));
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            setChunkMode(manager, block, owner, BeaconPlusChunkMode.OFF);
        }
        BeaconPlusRuntime.refreshNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.65F, 1.0F);
        player.sendMessage(ChatColor.RED + "All Beacon Plus effects have been disabled.");
        openMenu(player, block, owner);
    }

    private boolean setChunkMode(
            BeaconPlusManager manager, Block block, UUID owner, BeaconPlusChunkMode next) {
        return manager.updateModes(
                block.getLocation(),
                owner,
                next,
                manager.getSupportMode(block.getLocation()));
    }

    private boolean validateMenuAction(Player player, Block block, UUID expectedOwner) {
        if (!StorageCacheUtils.isBlock(block.getLocation(), getId())) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "That Beacon Plus no longer exists.");
            return false;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? expectedOwner : manager.getOwner(block.getLocation());
        if (!canConfigure(player, owner)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "You no longer have permission to configure this Beacon Plus.");
            return false;
        }
        return true;
    }

    private ItemStack createStatusItem(
            Block block, EnumSet<BeaconPlusEffect> enabled, BeaconPlusChunkMode chunkMode) {
        BlockState state = block.getState();
        int tier = state instanceof Beacon beacon ? beacon.getTier() : 0;
        double range = state instanceof Beacon beacon ? beacon.getEffectRange() : 0.0D;
        if (enabled.contains(BeaconPlusEffect.EXTRA_RANGE)) {
            range += EXTRA_RANGE_BLOCKS;
        }

        int effectCount = enabled.size();
        Material icon = tier > 0 ? Material.NETHER_STAR : Material.GRAY_DYE;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Beacon pyramid tier: " + (tier > 0 ? ChatColor.GREEN : ChatColor.RED) + tier);
        lore.add(ChatColor.GRAY + "Effective field range: " + ChatColor.AQUA + (int) Math.floor(range) + " blocks");
        lore.add(ChatColor.GRAY + "Enabled effects: " + ChatColor.GOLD + effectCount + "/29");
        lore.add(ChatColor.GRAY + "Activator: " + ChatColor.AQUA + chunkMode.getDisplayName());
        lore.add("");
        lore.add(tier > 0
                ? ChatColor.GREEN + "Field effects are powered."
                : ChatColor.RED + "Build a valid beacon pyramid to power field effects.");
        return createMenuItem(icon, ChatColor.GOLD + "Beacon Plus Status", lore);
    }

    private ItemStack createEffectItem(
            BeaconPlusEffect effect, boolean active, BeaconPlusChunkMode chunkMode) {
        boolean shownActive = effect == BeaconPlusEffect.ACTIVATOR
                ? chunkMode != BeaconPlusChunkMode.OFF
                : active;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + effect.getDescription());
        lore.add("");
        lore.add(ChatColor.GRAY + "Status: "
                + (shownActive ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        if (effect == BeaconPlusEffect.ACTIVATOR) {
            lore.add(ChatColor.GRAY + "Coverage: " + ChatColor.AQUA + chunkMode.getDisplayName());
            lore.add(ChatColor.DARK_GRAY + "Bounded by server-wide loader safety caps.");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle");

        String nameColor = shownActive ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
        return createMenuItem(effect.getIcon(), nameColor + effect.getDisplayName(), lore);
    }

    private ItemStack createActivatorCoverageItem(BeaconPlusChunkMode chunkMode) {
        return createMenuItem(
                Material.LODESTONE,
                ChatColor.AQUA + "Activator Coverage",
                List.of(
                        ChatColor.GRAY + "Current: " + ChatColor.WHITE + chunkMode.getDisplayName(),
                        "",
                        ChatColor.GRAY + "Off -> This Chunk",
                        ChatColor.GRAY + "This Chunk <-> 3x3 Area",
                        ChatColor.DARK_GRAY + "3x3 coverage uses 9 chunk tickets at most.",
                        "",
                        ChatColor.YELLOW + "Click to change coverage"));
    }

    private static ItemStack createMenuItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean canConfigure(Player player, UUID owner) {
        return owner == null || owner.equals(player.getUniqueId()) || player.isOp();
    }

    private static UUID ownerOrPlayer(UUID owner, Player player) {
        return owner == null ? player.getUniqueId() : owner;
    }
}
