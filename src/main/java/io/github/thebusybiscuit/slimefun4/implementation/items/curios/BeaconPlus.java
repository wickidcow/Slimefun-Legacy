package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Native Slimefun Legacy Beacon Plus.
 *
 * <p>The 30-effect menu and runtime are independent of any external beacon plugin. Periodic effects share one
 * Slimefun block ticker, event-driven effects share one listener, and Activator chunk loading remains bounded by the
 * Beacon Plus manager's server-wide safety caps.
 */
@SuppressWarnings("deprecation")
public final class BeaconPlus extends SlimefunItem implements EnergyNetComponent {

    private static final int[] EFFECT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38
    };

    private static final int STATUS_SLOT = 4;
    private static final int POWER_SOURCE_SLOT = 45;
    private static final int DISABLE_ALL_SLOT = 47;
    private static final int FIELD_AREA_SLOT = 49;
    private static final int CLOSE_SLOT = 53;

    private static final int ENERGY_CAPACITY = 8_192;
    private static final int BASE_ENERGY_PER_EFFECT_PER_PULSE = 16;
    private static final int EXTRA_POWER_PERCENT = 50;
    private static final int EXTRA_POWER_XP_LEVEL_COST = 30;
    private static final String EXTRA_POWER_UNLOCKED_KEY = "beacon_plus_extra_power_unlocked";

    private static final int POWER_PULSE_INTERVAL_TICKS = 20;
    private static final Map<PulseKey, Long> LAST_FIELD_PULSE_TICKS = new ConcurrentHashMap<>();
    private static final double PLAYER_STATE_RECONCILE_RANGE = 96.0D;

    @ParametersAreNonnullByDefault
    public BeaconPlus(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onPlace(), onUse(), onBreak(), createTicker());
    }

    @Override
    public int getCapacity() {
        return ENERGY_CAPACITY;
    }

    @Override
    public @Nonnull EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public void postRegister() {
        if (isDisabled()) {
            return;
        }

        BeaconPlusLifecycleListener.register(Slimefun.instance());
        BeaconPlusEffectListener.register(Slimefun.instance());
        Slimefun.getSchedulerService()
                .runLater(
                        () -> {
                            if (BeaconPlusManager.getInstance() == null) {
                                BeaconPlusManager.start(Slimefun.instance());
                            }
                        },
                        1L);
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
                StorageCacheUtils.setData(
                        location, BeaconPlusManager.FIELD_AREA_KEY, BeaconPlusFieldArea.DEFAULT.name());
                StorageCacheUtils.setData(
                        location, BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());
                StorageCacheUtils.setData(location, BeaconPlusRuntime.EFFECTS_KEY, "");
                StorageCacheUtils.setData(location, EXTRA_POWER_UNLOCKED_KEY, "false");
                BeaconPlusPowerSource.setMode(location, BeaconPlusPowerMode.SLIMEFUN_ENERGY);

                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.register(location, owner);
                }
                BeaconPlusPowerState.markUnpowered(location);
                BeaconPlusRuntime.observe(block);

                event.getPlayer()
                        .sendMessage(
                                ChatColor.GOLD + "Beacon Plus placed. " + ChatColor.GRAY
                                        + "Right click it to configure all 30 effects. Effect coverage defaults to a 3x3 chunk area.");
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
                player.sendMessage(
                        ChatColor.RED + "Only this Beacon Plus owner or a server operator can configure it.");
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
                BeaconPlusPowerState.markUnpowered(block.getLocation());
                LAST_FIELD_PULSE_TICKS.remove(PulseKey.from(block.getLocation()));
                BeaconPlusRuntime.forget(block.getLocation());
                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.unregister(block.getLocation());
                }
                BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
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
                BeaconPlusRuntime.observe(block);

                if (!isFieldPulse(block)) {
                    return;
                }

                EnumSet<BeaconPlusEffect> configured =
                        BeaconPlusEffect.parse(data.getData(BeaconPlusRuntime.EFFECTS_KEY));
                int fieldCost = calculateFieldEnergyCost(configured);
                BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());

                if (fieldCost <= 0 || !BeaconPlusPowerSource.isSourceReady(block, powerMode)) {
                    BeaconPlusPowerState.markUnpowered(block.getLocation());
                    BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
                    return;
                }

                if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
                    long stored = getChargeLong(block.getLocation(), data);
                    if (stored < fieldCost) {
                        BeaconPlusPowerState.markUnpowered(block.getLocation());
                        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
                        return;
                    }
                    removeCharge(block.getLocation(), fieldCost, data);
                }

                BeaconPlusPowerState.markPowered(block, data);
                BeaconPlusRuntime.tick(block, data);
                BeaconPlusPowerState.applyInvisibility(block);
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
        BeaconPlusChunkMode chunkMode =
                manager == null ? BeaconPlusChunkMode.OFF : manager.getChunkMode(block.getLocation());
        BeaconPlusFieldArea selectedArea = manager == null
                ? BeaconPlusFieldArea.fromStored(
                        StorageCacheUtils.getData(block.getLocation(), BeaconPlusManager.FIELD_AREA_KEY))
                : manager.getFieldArea(block.getLocation());
        BeaconPlusFieldArea effectiveArea = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), enabled);

        menu.addItem(STATUS_SLOT, createStatusItem(block, enabled, chunkMode, selectedArea, effectiveArea));
        menu.addMenuClickHandler(STATUS_SLOT, (pl, slot, item, action) -> false);

        BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
        menu.addItem(POWER_SOURCE_SLOT, createPowerSourceItem(powerMode));
        menu.addMenuClickHandler(POWER_SOURCE_SLOT, (pl, slot, item, action) -> {
            togglePowerSource(pl, block, owner);
            return false;
        });

        BeaconPlusEffect[] effects = BeaconPlusEffect.values();
        for (int index = 0; index < effects.length; index++) {
            BeaconPlusEffect effect = effects[index];
            int slot = EFFECT_SLOTS[index];
            boolean active = enabled.contains(effect);
            menu.addItem(slot, createEffectItem(effect, active, chunkMode, powerMode, effectiveArea));
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

        menu.addItem(FIELD_AREA_SLOT, createFieldAreaItem(selectedArea, effectiveArea, chunkMode));
        menu.addMenuClickHandler(FIELD_AREA_SLOT, (pl, slot, item, action) -> {
            cycleFieldArea(pl, block, owner);
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
        boolean activating = !enabled.contains(effect);
        if (activating && effect == BeaconPlusEffect.EXTRA_POWER && !isExtraPowerUnlocked(block.getLocation())) {
            if (!player.isOp()) {
                if (player.getLevel() < EXTRA_POWER_XP_LEVEL_COST) {
                    player.sendMessage(ChatColor.RED + "Extra Power requires " + EXTRA_POWER_XP_LEVEL_COST
                            + " experience levels to unlock on this Beacon Plus.");
                    return;
                }
                player.giveExpLevels(-EXTRA_POWER_XP_LEVEL_COST);
            }

            StorageCacheUtils.setData(block.getLocation(), EXTRA_POWER_UNLOCKED_KEY, "true");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Extra Power unlocked. " + ChatColor.GRAY
                    + "Its field-energy draw is " + EXTRA_POWER_PERCENT + "% higher while enabled.");
        }

        if (!enabled.remove(effect)) {
            enabled.add(effect);
        }

        if (effect == BeaconPlusEffect.EXTRA_RANGE) {
            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager != null && manager.getChunkMode(block.getLocation()) != BeaconPlusChunkMode.OFF) {
                BeaconPlusFieldArea effectiveArea =
                        BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), enabled);
                if (!setChunkMode(manager, block, owner, BeaconPlusChunkMode.forFieldArea(effectiveArea))) {
                    player.sendMessage(ChatColor.RED
                            + "Extra Range would make the active chunk coverage exceed the Beacon Plus safety cap.");
                    return;
                }
            }
        }

        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
        BeaconPlusRuntime.observe(block);
        BeaconPlusPowerState.markUnpowered(block.getLocation());
        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);

        boolean active = enabled.contains(effect);
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, active ? 1.35F : 0.85F);
        player.sendMessage(ChatColor.GOLD + "Beacon Plus: " + ChatColor.WHITE + effect.getDisplayName() + ChatColor.GRAY
                + " is now " + (active ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED") + ChatColor.GRAY
                + ".");
        if (active && effect != BeaconPlusEffect.ACTIVATOR) {
            int requiredEnergy = calculateFieldEnergyCost(enabled);
            BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
            if (!BeaconPlusPowerSource.isSourceReady(block, powerMode)) {
                player.sendMessage(
                        ChatColor.RED
                                + "Configured, but not active: Beacon Blocks mode needs a valid vanilla beacon pyramid and sky activation.");
            } else if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY
                    && requiredEnergy > 0
                    && getChargeLong(block.getLocation()) < requiredEnergy) {
                player.sendMessage(ChatColor.RED + "Configured, but not active: Beacon Plus needs " + requiredEnergy
                        + " J for its next one-second field pulse.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Field is powered by " + ChatColor.WHITE
                        + powerMode.getDisplayName() + ChatColor.GREEN + ".");
            }
        }
        openMenu(player, block, owner);
    }

    private void togglePowerSource(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusPowerMode current = BeaconPlusPowerSource.getMode(block.getLocation());
        BeaconPlusPowerMode next = current.next();
        BeaconPlusPowerSource.setMode(block.getLocation(), next);
        BeaconPlusPowerState.markUnpowered(block.getLocation());
        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
        BeaconPlusRuntime.observe(block);

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7F, 1.2F);
        player.sendMessage(ChatColor.GOLD + "Beacon Plus power source: " + ChatColor.WHITE + next.getDisplayName());
        openMenu(player, block, owner);
    }

    private void toggleActivator(Player player, Block block, UUID owner) {
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            player.sendMessage(ChatColor.RED + "Beacon Plus chunk loading is not ready.");
            return;
        }

        BeaconPlusChunkMode current = manager.getChunkMode(block.getLocation());
        EnumSet<BeaconPlusEffect> effects = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        BeaconPlusFieldArea effectiveArea = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), effects);
        BeaconPlusChunkMode next = current == BeaconPlusChunkMode.OFF
                ? BeaconPlusChunkMode.forFieldArea(effectiveArea)
                : BeaconPlusChunkMode.OFF;
        if (!setChunkMode(manager, block, owner, next)) {
            player.sendMessage(ChatColor.RED + "The Beacon Plus chunk-loader safety cap would be exceeded.");
            return;
        }

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.1F);
        player.sendMessage(ChatColor.AQUA + "Beacon Plus Activator: " + ChatColor.WHITE + next.getDisplayName());
        openMenu(player, block, owner);
    }

    private void cycleFieldArea(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            player.sendMessage(ChatColor.RED + "Beacon Plus area controls are not ready.");
            return;
        }

        BeaconPlusFieldArea current = manager.getFieldArea(block.getLocation());
        BeaconPlusFieldArea next = current.next();
        EnumSet<BeaconPlusEffect> effects = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        BeaconPlusFieldArea effectiveNext = effects.contains(BeaconPlusEffect.EXTRA_RANGE) ? next.expand() : next;

        if (manager.getChunkMode(block.getLocation()) != BeaconPlusChunkMode.OFF
                && !setChunkMode(manager, block, owner, BeaconPlusChunkMode.forFieldArea(effectiveNext))) {
            player.sendMessage(
                    ChatColor.RED + "The requested effect area would exceed the Beacon Plus chunk safety cap.");
            return;
        }

        StorageCacheUtils.setData(block.getLocation(), BeaconPlusManager.FIELD_AREA_KEY, next.name());
        BeaconPlusPowerState.markUnpowered(block.getLocation());
        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
        BeaconPlusRuntime.observe(block);

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.25F);
        player.sendMessage(ChatColor.AQUA + "Beacon Plus effect area: " + ChatColor.WHITE + next.getDisplayName());
        if (effectiveNext != next) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Extra Range expands the active field to " + ChatColor.WHITE
                    + effectiveNext.getDisplayName() + ChatColor.LIGHT_PURPLE + ".");
        }
        openMenu(player, block, owner);
    }

    private void disableAll(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), EnumSet.noneOf(BeaconPlusEffect.class));
        BeaconPlusPowerState.markUnpowered(block.getLocation());
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            setChunkMode(manager, block, owner, BeaconPlusChunkMode.OFF);
        }
        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.65F, 1.0F);
        player.sendMessage(ChatColor.RED + "All Beacon Plus effects have been disabled.");
        openMenu(player, block, owner);
    }

    private boolean setChunkMode(BeaconPlusManager manager, Block block, UUID owner, BeaconPlusChunkMode next) {
        return manager.updateModes(block.getLocation(), owner, next, manager.getSupportMode(block.getLocation()));
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
            Block block,
            EnumSet<BeaconPlusEffect> enabled,
            BeaconPlusChunkMode chunkMode,
            BeaconPlusFieldArea selectedArea,
            BeaconPlusFieldArea effectiveArea) {
        BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
        int tier = BeaconPlusPowerSource.getPyramidTier(block);
        int effectCount = enabled.size();
        int energyCost = calculateFieldEnergyCost(enabled);
        long storedEnergy = getChargeLong(block.getLocation());
        boolean sourceReady = BeaconPlusPowerSource.isSourceReady(block, powerMode);
        boolean hasFieldWork = energyCost > 0;
        boolean enoughEnergy = powerMode != BeaconPlusPowerMode.SLIMEFUN_ENERGY || storedEnergy >= energyCost;
        boolean fieldReady = hasFieldWork && sourceReady && enoughEnergy;
        Material icon = fieldReady ? Material.NETHER_STAR : powerMode.getIcon();

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Power source: " + ChatColor.AQUA + powerMode.getDisplayName());
        if (powerMode == BeaconPlusPowerMode.BEACON_BLOCKS) {
            lore.add(ChatColor.GRAY + "Beacon pyramid tier: " + (tier > 0 ? ChatColor.GREEN : ChatColor.RED) + tier);
            lore.add(ChatColor.DARK_GRAY + "Uses normal vanilla beacon pyramid/sky rules for power.");
        } else {
            lore.add(
                    ChatColor.GRAY + "Field energy: " + ChatColor.YELLOW + storedEnergy + "/" + ENERGY_CAPACITY + " J");
            lore.add(ChatColor.GRAY + "Current field draw: " + ChatColor.YELLOW + energyCost + " J/s");
        }
        lore.add(ChatColor.GRAY + "Selected effect area: " + ChatColor.AQUA + selectedArea.getDisplayName());
        lore.add(ChatColor.GRAY + "Effective effect area: " + ChatColor.GREEN + effectiveArea.getDisplayName());
        lore.add(ChatColor.GRAY + "Enabled effects: " + ChatColor.GOLD + effectCount + "/30");
        lore.add(ChatColor.GRAY + "Activator chunk loading: "
                + (chunkMode == BeaconPlusChunkMode.OFF
                        ? ChatColor.RED + "OFF"
                        : ChatColor.GREEN + chunkMode.getDisplayName()));

        if (!hasFieldWork) {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.YELLOW + "IDLE");
            lore.add(ChatColor.YELLOW + "Reason: no powered field effect currently needs power.");
        } else if (fieldReady) {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.GREEN + "ACTIVE");
        } else {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.RED + "NOT POWERED");
            if (!sourceReady) {
                lore.add(ChatColor.RED + "Reason: Beacon Blocks mode needs a valid pyramid and sky activation.");
            } else if (!enoughEnergy) {
                lore.add(ChatColor.RED + "Reason: needs at least " + energyCost + " J for the next field pulse.");
            }
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "Use Effect Area to choose 1x1, 3x3 or 5x5 chunks.");
        return createMenuItem(icon, ChatColor.GOLD + "Beacon Plus Status", lore);
    }

    private ItemStack createEffectItem(
            BeaconPlusEffect effect,
            boolean active,
            BeaconPlusChunkMode chunkMode,
            BeaconPlusPowerMode powerMode,
            BeaconPlusFieldArea effectiveArea) {
        boolean shownActive = effect == BeaconPlusEffect.ACTIVATOR ? chunkMode != BeaconPlusChunkMode.OFF : active;
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + effect.getDescription());
        lore.add("");
        lore.add(
                ChatColor.GRAY + "Status: " + (shownActive ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        lore.add(ChatColor.GRAY + "Affected area: " + ChatColor.AQUA + effectiveArea.getDisplayName());
        if (effect == BeaconPlusEffect.ACTIVATOR) {
            lore.add(ChatColor.GRAY + "Chunk loading: " + ChatColor.AQUA + chunkMode.getDisplayName());
            lore.add(ChatColor.DARK_GRAY + "When enabled, keeps the effective effect area loaded.");
        } else if (effect == BeaconPlusEffect.EXTRA_POWER) {
            lore.add(ChatColor.LIGHT_PURPLE + "One-time unlock: " + ChatColor.WHITE + EXTRA_POWER_XP_LEVEL_COST
                    + " XP levels");
            lore.add(ChatColor.YELLOW + "Supported effect boost: +" + EXTRA_POWER_PERCENT + "%");
            if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
                lore.add(ChatColor.DARK_GRAY + "Electricity draw: +" + EXTRA_POWER_PERCENT + "% while enabled.");
            } else {
                lore.add(ChatColor.DARK_GRAY + "Beacon Blocks mode consumes no electricity.");
            }
            lore.add(ChatColor.DARK_GRAY + "Operators bypass the XP unlock cost.");
        } else if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
            lore.add(ChatColor.DARK_GRAY + "Base field cost: " + BASE_ENERGY_PER_EFFECT_PER_PULSE + " J/s");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Powered by beacon blocks; no electricity consumed.");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to toggle");

        String nameColor = shownActive ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
        return createMenuItem(effect.getIcon(), nameColor + effect.getDisplayName(), lore);
    }

    private ItemStack createPowerSourceItem(BeaconPlusPowerMode powerMode) {
        BeaconPlusPowerMode other = powerMode.next();
        return createMenuItem(
                powerMode.getIcon(),
                ChatColor.GOLD + "Power Source: " + ChatColor.WHITE + powerMode.getDisplayName(),
                List.of(
                        ChatColor.GRAY + "Choose how this Beacon Plus powers all field effects.",
                        "",
                        powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY
                                ? ChatColor.GRAY + "Consumes Slimefun electricity once per second."
                                : ChatColor.GRAY + "Uses a normal vanilla beacon pyramid; no J is consumed.",
                        ChatColor.GRAY + "Switch to: " + ChatColor.AQUA + other.getDisplayName(),
                        "",
                        ChatColor.YELLOW + "Click to switch power source"));
    }

    private ItemStack createFieldAreaItem(
            BeaconPlusFieldArea selectedArea, BeaconPlusFieldArea effectiveArea, BeaconPlusChunkMode chunkMode) {
        return createMenuItem(
                Material.LODESTONE,
                ChatColor.AQUA + "Effect Area: " + ChatColor.WHITE + effectiveArea.getDisplayName(),
                List.of(
                        ChatColor.GRAY + "All enabled Beacon Plus effects use this chunk area.",
                        ChatColor.GRAY + "Selected: " + ChatColor.WHITE + selectedArea.getDisplayName(),
                        ChatColor.GRAY + "Effective: " + ChatColor.GREEN + effectiveArea.getDisplayName(),
                        ChatColor.GRAY + "Activator: "
                                + (chunkMode == BeaconPlusChunkMode.OFF
                                        ? ChatColor.RED + "OFF"
                                        : ChatColor.GREEN + "ON"),
                        "",
                        ChatColor.GRAY + "1x1 -> 3x3 -> 5x5 -> 1x1",
                        ChatColor.GRAY + "Default: " + ChatColor.AQUA + "3x3 Chunks",
                        ChatColor.DARK_GRAY + "Extra Range expands one tier, capped at 5x5.",
                        "",
                        ChatColor.YELLOW + "Click to change affected area"));
    }

    static int calculateFieldEnergyCost(Set<BeaconPlusEffect> configured) {
        EnumSet<BeaconPlusEffect> effects =
                configured.isEmpty() ? EnumSet.noneOf(BeaconPlusEffect.class) : EnumSet.copyOf(configured);
        effects.remove(BeaconPlusEffect.ACTIVATOR);
        boolean extraPower = effects.remove(BeaconPlusEffect.EXTRA_POWER);

        int baseCost = effects.size() * BASE_ENERGY_PER_EFFECT_PER_PULSE;
        if (baseCost <= 0 || !extraPower) {
            return baseCost;
        }
        return (int) Math.ceil(baseCost * (100.0D + EXTRA_POWER_PERCENT) / 100.0D);
    }

    private static boolean isFieldPulse(Block block) {
        PulseKey key = PulseKey.from(block.getLocation());
        long gameTime = block.getWorld().getGameTime();
        Long previous = LAST_FIELD_PULSE_TICKS.get(key);

        if (previous == null) {
            long stagger = Math.floorMod(block.getX() * 31L + block.getZ() * 17L, POWER_PULSE_INTERVAL_TICKS);
            LAST_FIELD_PULSE_TICKS.put(key, gameTime - stagger);
            return stagger == 0L;
        }

        if (gameTime - previous < POWER_PULSE_INTERVAL_TICKS) {
            return false;
        }

        LAST_FIELD_PULSE_TICKS.put(key, gameTime);
        return true;
    }

    static void clearPulseState() {
        LAST_FIELD_PULSE_TICKS.clear();
    }

    private static boolean isExtraPowerUnlocked(Location location) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(location, EXTRA_POWER_UNLOCKED_KEY));
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

    private record PulseKey(UUID worldId, int x, int y, int z) {
        private static PulseKey from(Location location) {
            return new PulseKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
