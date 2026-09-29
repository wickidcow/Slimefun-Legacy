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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Native Slimefun Legacy Resonance Beacon.
 *
 * <p>The historic {@code BEACON_PLUS} item id and storage keys are deliberately retained so development builds and
 * imported BeaconPlus data migrate without losing their locations. Player-facing behavior is the Resonance Beacon:
 * 29 administrator-controlled powers, permanent owner unlocks up to Tier III, and a physical pyramid/material
 * resonance ceiling.
 */
public final class BeaconPlus extends SlimefunItem implements EnergyNetComponent {

    private static final int[] EFFECT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37
    };

    private static final int STATUS_SLOT = 4;
    private static final int ELECTRIC_OPERATION_SLOT = 46;
    private static final int DISABLE_ALL_SLOT = 47;
    private static final int PYRAMID_INFO_SLOT = 49;
    private static final int CONTROLS_SLOT = 51;
    private static final int CLOSE_SLOT = 53;

    @ParametersAreNonnullByDefault
    public BeaconPlus(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onPlace(), onUse(), onBreak(), createTicker());
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public int getCapacity() {
        return BeaconPlusConfig.getEnergyCapacity();
    }

    @Override
    public long getCapacityLong() {
        return BeaconPlusConfig.getEnergyCapacity();
    }

    @Override
    public boolean isEnergyNetActive(@Nonnull Location location, @Nonnull ASlimefunDataContainer data) {
        return BeaconPlusConfig.isElectricOperationEnabled() && BeaconPlusEnergy.isElectricModeSelected(location);
    }

    @Override
    public void postRegister() {
        if (isDisabled()) {
            return;
        }

        BeaconPlusConfig.installDefaults();
        BeaconPlusLifecycleListener.register(Slimefun.instance());
        BeaconPlusEffectListener.register(Slimefun.instance());
        Slimefun.getSchedulerService()
                .runLater(
                        () -> {
                            if (BeaconPlusManager.getInstance() == null) {
                                BeaconPlusManager.start(Slimefun.instance());
                            }
                            BeaconPlusLegacyDataStore.start(Slimefun.instance());
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
                        location, BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());
                StorageCacheUtils.setData(location, BeaconPlusRuntime.EFFECTS_KEY, "");
                StorageCacheUtils.setData(location, BeaconPlusEnergy.ELECTRIC_MODE_KEY, Boolean.FALSE.toString());
                StorageCacheUtils.removeData(location, BeaconPlusLegacyDataStore.IMPORTED_KEY);

                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.register(location, owner);
                }
                BeaconPlusRuntime.observe(block);
                BeaconPlusLegacyDataStore.sync(block);

                event.getPlayer()
                        .sendMessage("&6" + "Resonance Beacon placed. " + "&7"
                                + "Build its mineral pyramid, then right click it to unlock and configure powers.");
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
            if (!BeaconPlusConfig.isEnabled()) {
                message(player, "&c" + "Resonance Beacons are disabled by the server administrator.");
                return;
            }

            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager == null) {
                message(player, "&c" + "Resonance Beacon is still initializing. Try again in a moment.");
                return;
            }

            UUID owner = manager.getOwner(block.getLocation());
            if (!canConfigure(player, owner)) {
                message(player, 
                        "&c" + "Only this Resonance Beacon owner or a server operator can configure it.");
                return;
            }

            BeaconPlusRuntime.observe(block);
            openMenu(player, block, owner);
        };
    }

    private @Nonnull SimpleBlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block block) {
                BeaconPlusRuntime.forget(block.getLocation());
                BeaconPlusEnergy.forget(block.getLocation());
                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.unregister(block.getLocation());
                }
                BeaconPlusLegacyDataStore.remove(block.getLocation());
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

        ChestMenu menu = new ChestMenu("&6&lResonance Beacon", 54);
        menu.setPlayerInventoryClickable(false);
        menu.setEmptySlotsClickable(false);

        EnumSet<BeaconPlusEffect> enabled = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        BeaconPlusPyramid.Profile profile = BeaconPlusPyramid.inspect(block);
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        BeaconPlusChunkMode chunkMode =
                manager == null ? BeaconPlusChunkMode.OFF : manager.getChunkMode(block.getLocation());
        EnumMap<BeaconPlusEffect, Integer> potentialTiers = BeaconPlusRuntime.getPotentialActiveTiers(block);
        boolean operational = BeaconPlusRuntime.isOperational(block, potentialTiers);

        menu.addItem(STATUS_SLOT, createStatusItem(block, owner, enabled, profile, chunkMode));
        menu.addMenuClickHandler(STATUS_SLOT, (pl, slot, item, action) -> false);

        BeaconPlusEffect[] effects = BeaconPlusEffect.configurableValues();
        for (int index = 0; index < effects.length; index++) {
            BeaconPlusEffect effect = effects[index];
            int slot = EFFECT_SLOTS[index];
            menu.addItem(
                    slot,
                    createEffectItem(
                            block, effect, enabled.contains(effect), profile, potentialTiers, operational));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, item, action) -> {
                handleEffectClick(pl, block, owner, effect, action.isRightClicked(), action.isShiftClicked());
                return false;
            });
        }

        menu.addItem(ELECTRIC_OPERATION_SLOT, createElectricOperationItem(block, potentialTiers, operational));
        menu.addMenuClickHandler(ELECTRIC_OPERATION_SLOT, (pl, slot, item, action) -> {
            if (action.isRightClicked()) {
                toggleElectricOperation(pl, block, owner);
            }
            return false;
        });

        menu.addItem(
                DISABLE_ALL_SLOT,
                createMenuItem(
                        Material.BARRIER,
                        "&c" + "Disable All Powers",
                        List.of(
                                "&7" + "Turns off every Resonance Beacon power",
                                "&7" + "including the Activator chunk loader.",
                                "",
                                "&e" + "Right click to disable everything")));
        menu.addMenuClickHandler(DISABLE_ALL_SLOT, (pl, slot, item, action) -> {
            if (action.isRightClicked()) {
                disableAll(pl, block, owner);
            }
            return false;
        });

        menu.addItem(PYRAMID_INFO_SLOT, createPyramidItem(profile));
        menu.addMenuClickHandler(PYRAMID_INFO_SLOT, (pl, slot, item, action) -> false);
        menu.addItem(CONTROLS_SLOT, createControlsItem());
        menu.addMenuClickHandler(CONTROLS_SLOT, (pl, slot, item, action) -> false);
        menu.addItem(
                CLOSE_SLOT,
                createMenuItem(
                        Material.RED_STAINED_GLASS_PANE,
                        "&c" + "Close",
                        List.of("&7" + "Close Resonance Beacon configuration.")));
        menu.addMenuClickHandler(CLOSE_SLOT, (pl, slot, item, action) -> {
            pl.closeInventory();
            return false;
        });

        menu.open(player);
    }

    private void handleEffectClick(
            Player player, Block block, UUID owner, BeaconPlusEffect effect, boolean rightClick, boolean shiftClick) {
        if (!rightClick) {
            message(player, "&7" + "Use right click to buy, enable, disable, or upgrade this power.");
            return;
        }
        if (!validateMenuAction(player, block, owner)) {
            return;
        }
        if (!BeaconPlusConfig.isPowerEnabled(effect)) {
            message(player, "&c" + effect.getDisplayName() + " is disabled by the server administrator.");
            openMenu(player, block, owner);
            return;
        }

        int unlocked = BeaconPlusRuntime.getUnlockedTierAtBeacon(block, effect);
        int maximum = BeaconPlusConfig.getMaxTier();
        boolean legacyImported = BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation());

        if (shiftClick) {
            if (legacyImported) {
                message(player, "&e" + "This is a legacy-imported beacon. " + "&7"
                        + "Its old BeaconData unlock levels are grandfathered and cannot be purchased again.");
                openMenu(player, block, owner);
                return;
            }
            if (unlocked >= maximum) {
                message(player, "&7" + effect.getDisplayName() + " is already at Tier " + maximum + ".");
                openMenu(player, block, owner);
                return;
            }
            purchaseAndEnable(player, block, owner, effect);
            return;
        }

        EnumSet<BeaconPlusEffect> enabled = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        if (enabled.contains(effect)) {
            enabled.remove(effect);
            if (effect == BeaconPlusEffect.ACTIVATOR && !BeaconPlusRuntime.reconcileActivator(block, 0)) {
                message(player, "&c" + "Could not release this Resonance Beacon's Activator coverage.");
                openMenu(player, block, owner);
                return;
            }
            BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
            BeaconPlusRuntime.refreshPlayerState(player);
            player.playSound(block.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.65F, 1.0F);
            message(player, "&6" + "Resonance Beacon: " + "&f" + effect.getDisplayName()
                    + "&7" + " is now " + "&c" + "DISABLED" + "&7" + ".");
            openMenu(player, block, owner);
            return;
        }

        if (unlocked <= 0) {
            if (legacyImported) {
                message(player, "&c" + "That power was not unlocked in this imported BeaconData record.");
                openMenu(player, block, owner);
                return;
            }
            purchaseAndEnable(player, block, owner, effect);
            return;
        }

        enabled.add(effect);
        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
        boolean activatorAccepted = effect != BeaconPlusEffect.ACTIVATOR || BeaconPlusRuntime.reconcileActivator(block);
        if (!activatorAccepted) {
            enabled.remove(effect);
            BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
            message(player, "&c" + "The Resonance Beacon chunk-loader safety cap would be exceeded.");
        } else {
            player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.35F);
            message(player, "&6" + "Resonance Beacon: " + "&f" + effect.getDisplayName()
                    + "&7" + " is now " + "&a" + "ENABLED" + "&7" + ".");
        }
        openMenu(player, block, owner);
    }

    private void purchaseAndEnable(Player player, Block block, UUID owner, BeaconPlusEffect effect) {
        BeaconPlusProgression.PurchaseResult result = BeaconPlusProgression.purchaseNextTier(player, owner, effect);
        if (!result.success()) {
            message(player, "&c" + result.error());
            openMenu(player, block, owner);
            return;
        }

        EnumSet<BeaconPlusEffect> enabled = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        enabled.add(effect);
        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
        boolean activatorAccepted = effect != BeaconPlusEffect.ACTIVATOR || BeaconPlusRuntime.reconcileActivator(block);
        if (!activatorAccepted) {
            enabled.remove(effect);
            BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
        }

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.45F);
        message(player, "&a" + "Unlocked " + "&f" + effect.getDisplayName() + "&a"
                + " Tier " + result.newTier() + "&7" + "."
                + (activatorAccepted
                        ? " It is enabled."
                        : " Unlock kept; Activator stayed disabled because of the loader cap."));
        openMenu(player, block, owner);
    }

    private void disableAll(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        // Release Activator first so native storage and the optional legacy BeaconData mirror see the same OFF state.
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            manager.updateModes(
                    block.getLocation(), owner, BeaconPlusChunkMode.OFF, manager.getSupportMode(block.getLocation()));
        }
        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), EnumSet.noneOf(BeaconPlusEffect.class));
        BeaconPlusRuntime.refreshPlayerState(player);
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.65F, 1.0F);
        message(player, "&c" + "All Resonance Beacon powers have been disabled.");
        openMenu(player, block, owner);
    }

    private void toggleElectricOperation(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }
        if (!BeaconPlusConfig.isElectricOperationEnabled()) {
            message(player, 
                    "&c" + "Electric Resonance Beacon operation is disabled by the server administrator.");
            openMenu(player, block, owner);
            return;
        }

        boolean enabled = !BeaconPlusEnergy.isElectricModeSelected(block.getLocation());
        BeaconPlusEnergy.setElectricMode(block.getLocation(), enabled);
        BeaconPlusRuntime.reconcileActivator(block);
        BeaconPlusRuntime.refreshPlayerState(player);
        player.playSound(
                block.getLocation(),
                enabled ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE,
                0.65F,
                enabled ? 1.55F : 1.0F);
        message(player, "&6" + "Resonance Beacon electric operation: "
                + (enabled ? "&a" + "ON" : "&c" + "OFF")
                + "&7"
                + (enabled
                        ? ". Powers now require Slimefun energy."
                        : ". Powers now use normal pyramid-only operation."));
        openMenu(player, block, owner);
    }

    private boolean validateMenuAction(Player player, Block block, UUID expectedOwner) {
        if (!StorageCacheUtils.isBlock(block.getLocation(), getId())) {
            player.closeInventory();
            message(player, "&c" + "That Resonance Beacon no longer exists.");
            return false;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID currentOwner = manager == null ? expectedOwner : manager.getOwner(block.getLocation());
        if (!canConfigure(player, currentOwner)) {
            player.closeInventory();
            message(player, "&c" + "You no longer have permission to configure this Resonance Beacon.");
            return false;
        }
        return true;
    }

    private ItemStack createStatusItem(
            Block block,
            UUID owner,
            EnumSet<BeaconPlusEffect> enabled,
            BeaconPlusPyramid.Profile profile,
            BeaconPlusChunkMode chunkMode) {
        List<String> lore = new ArrayList<>();
        int baseSize = profile.completedLayers() <= 0 ? 0 : profile.completedLayers() * 2 + 1;
        lore.add("&7" + "Physical pyramid: "
                + (baseSize > 0
                        ? "&a" + baseSize + "x" + baseSize
                        : "&c" + "Incomplete"));
        lore.add("&7" + "Natural power tier: " + tierColor(profile.naturalPowerTier())
                + roman(profile.naturalPowerTier()));
        lore.add("&7" + "Dominant mineral: " + "&b" + profile.dominantMaterialName());
        lore.add("&7" + "Average mineral power: " + "&b"
                + String.format(java.util.Locale.ROOT, "%.2f", profile.averageMaterialPower()));
        lore.add("&7" + "Enabled powers: " + "&6" + enabled.size() + "/29");
        lore.add("&7" + "Activator coverage: " + "&b" + chunkMode.getDisplayName());
        lore.add("");
        if (BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
            lore.add("&e" + "Legacy BeaconData import");
            lore.add("&7" + "No owner existed in the old format; operator-managed.");
        } else if (owner != null) {
            lore.add("&8" + "Unlocks are permanently owned by the placing player.");
        }
        lore.add(
                profile.naturalPowerTier() > 0
                        ? "&a" + "Pyramid resonance is active."
                        : "&c" + "Build a valid powered mineral pyramid.");
        return createMenuItem(
                profile.naturalPowerTier() > 0 ? Material.NETHER_STAR : Material.GRAY_DYE,
                "&6" + "Resonance Beacon Status",
                lore);
    }

    private ItemStack createEffectItem(
            Block block,
            BeaconPlusEffect effect,
            boolean active,
            BeaconPlusPyramid.Profile profile,
            EnumMap<BeaconPlusEffect, Integer> potentialTiers,
            boolean operational) {
        boolean serverEnabled = BeaconPlusConfig.isPowerEnabled(effect);
        int unlocked = BeaconPlusRuntime.getUnlockedTierAtBeacon(block, effect);
        int selected = BeaconPlusRuntime.getSelectedTierAtBeacon(block, effect);
        int effective = active ? potentialTiers.getOrDefault(effect, 0) : 0;
        int maximum = BeaconPlusConfig.getMaxTier();

        List<String> lore = new ArrayList<>();
        lore.add("&7" + effect.getDescription());
        lore.add("");
        lore.add("&7" + "Server: "
                + (serverEnabled ? "&a" + "AVAILABLE" : "&c" + "DISABLED"));
        lore.add("&7" + "Unlocked: " + tierColor(unlocked) + roman(unlocked) + "&8" + "/III");
        if (BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation()) && selected > 0) {
            lore.add("&7" + "Legacy selected tier: " + tierColor(selected) + roman(selected));
        }
        lore.add("&7" + "Pyramid ceiling: " + tierColor(profile.naturalPowerTier())
                + roman(profile.naturalPowerTier()));
        lore.add("&7" + "Status: " + (active ? "&a" + "ENABLED" : "&c" + "DISABLED"));
        if (active) {
            lore.add("&7" + "Effective tier: "
                    + (effective > 0 ? tierColor(effective) + roman(effective) : "&c" + "DORMANT"));
            if (effective > 0) {
                lore.add("&7" + "Runtime: "
                        + (operational ? "&a" + "ACTIVE" : "&c" + "DORMANT (ENERGY)"));
            }
        }
        if (effect == BeaconPlusEffect.ACTIVATOR) {
            lore.add("&8" + "Tier I = this chunk; II = 3x3; III = 5x5.");
        } else if (effect == BeaconPlusEffect.RADIATION_ABSORBER) {
            lore.add("&8" + "Tier I absorbs 25 exposure; II absorbs 50; III clears all.");
        }

        if (serverEnabled && unlocked < maximum && !BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
            lore.add("");
            lore.add("&6" + "Next Tier: " + roman(unlocked + 1));
            lore.add("&7" + "Cost: " + "&e"
                    + BeaconPlusProgression.describeCost(effect, unlocked + 1));
        }

        lore.add("");
        if (!serverEnabled) {
            lore.add("&c" + "Disabled in configSFLAddons.yml");
        } else if (unlocked <= 0 && BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
            lore.add("&8" + "Not unlocked in imported BeaconData.");
        } else if (unlocked <= 0) {
            lore.add("&e" + "Right click to buy Tier I + enable");
        } else {
            lore.add("&e" + "Right click to " + (active ? "disable" : "enable"));
            if (unlocked < maximum && !BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
                lore.add("&e" + "Shift + Right Click to buy Tier " + roman(unlocked + 1));
            }
        }

        Material icon = serverEnabled ? effect.getIcon() : Material.BARRIER;
        String nameColor = !serverEnabled
                ? "&8"
                : active
                        ? "&a"
                        : unlocked > 0 ? "&6" : "&c";
        return createMenuItem(icon, nameColor + effect.getDisplayName(), lore);
    }

    private ItemStack createElectricOperationItem(
            Block block, EnumMap<BeaconPlusEffect, Integer> potentialTiers, boolean operational) {
        boolean available = BeaconPlusConfig.isElectricOperationEnabled();
        boolean selected = BeaconPlusEnergy.isElectricModeSelected(block.getLocation());
        long charge = BeaconPlusEnergy.getStoredCharge(block.getLocation());
        long capacity = BeaconPlusConfig.getEnergyCapacity();
        long demand = BeaconPlusEnergy.getDemand(potentialTiers);
        boolean powered = !selected || demand <= 0L || operational;

        List<String> lore = new ArrayList<>();
        lore.add("&7" + "Optional native Slimefun Energy Network operation.");
        lore.add("&7" + "Mode: " + (selected ? "&a" + "ON" : "&c" + "OFF"));
        lore.add("&7" + "Charge: " + "&b" + charge + "&7" + "/" + capacity + " J");
        lore.add("&7" + "Current draw: " + "&e" + demand + " J/second");
        lore.add("&7" + "Power state: "
                + (powered ? "&a" + "READY" : "&c" + "INSUFFICIENT ENERGY"));
        lore.add("");
        if (!available) {
            lore.add("&c" + "Disabled by server configuration.");
        } else {
            lore.add("&e" + "Right click to turn electric operation " + (selected ? "OFF" : "ON"));
            lore.add("&8" + "When ON, all powers pause if charge is too low.");
            lore.add("&8" + "Activator chunk tickets release until energy returns.");
        }

        return createMenuItem(
                available ? (selected ? Material.REDSTONE_BLOCK : Material.REDSTONE_TORCH) : Material.BARRIER,
                "&e" + "Electric Operation",
                lore);
    }

    private ItemStack createPyramidItem(BeaconPlusPyramid.Profile profile) {
        return createMenuItem(
                profile.naturalPowerTier() > 0 ? profile.dominantMaterial() : Material.IRON_BLOCK,
                "&b" + "Pyramid Resonance",
                List.of(
                        "&7" + "Tier I: 3x3+ base / material power 1.0",
                        "&7" + "Tier II: 5x5+ base / material power 3.0",
                        "&7" + "Tier III: 7x7+ base / material power 4.0",
                        "",
                        "&8" + "Default mineral power:",
                        "&7" + "Iron 1 • Gold 2 • Emerald 3",
                        "&7" + "Diamond 4 • Netherite 5",
                        "",
                        "&8" + "All thresholds are server-configurable."));
    }

    private ItemStack createControlsItem() {
        return createMenuItem(
                Material.BOOK,
                "&e" + "Power Controls",
                List.of(
                        "&7" + "Right click a locked power to buy Tier I",
                        "&7" + "and immediately enable it.",
                        "&7" + "Right click an unlocked power to toggle it.",
                        "&7" + "Shift + Right Click buys the next tier.",
                        "",
                        "&7" + "Purchased tiers stay with the beacon owner.",
                        "&7" + "The physical pyramid caps the tier that can run."));
    }

    private static ItemStack createMenuItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacyText(displayName));
        meta.lore(lore.stream().map(BeaconPlus::legacyText).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static void message(Player player, String value) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }

    private static Component legacyText(String value) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(value)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static boolean canConfigure(Player player, UUID owner) {
        if (owner == null || BeaconPlusLegacyDataStore.LEGACY_IMPORTED_OWNER.equals(owner)) {
            return player.isOp();
        }
        return owner.equals(player.getUniqueId()) || player.isOp();
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "0";
        };
    }

    private static String tierColor(int tier) {
        return switch (tier) {
            case 1 -> "&e";
            case 2 -> "&b";
            case 3 -> "&d";
            default -> "&c";
        };
    }
}
