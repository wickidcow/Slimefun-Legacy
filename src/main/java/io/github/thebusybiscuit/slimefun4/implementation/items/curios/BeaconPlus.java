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
 * 28 administrator-controlled powers, permanent owner unlocks up to Tier III, and a physical pyramid/material
 * resonance ceiling.
 */
public final class BeaconPlus extends SlimefunItem {

    private static final int[] EFFECT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36
    };

    private static final int STATUS_SLOT = 4;
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
                StorageCacheUtils.removeData(location, BeaconPlusLegacyDataStore.IMPORTED_KEY);

                BeaconPlusManager manager = BeaconPlusManager.getInstance();
                if (manager != null) {
                    manager.register(location, owner);
                }
                BeaconPlusRuntime.observe(block);
                BeaconPlusLegacyDataStore.sync(block);

                event.getPlayer()
                        .sendMessage(ChatColor.GOLD + "Resonance Beacon placed. " + ChatColor.GRAY
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
                player.sendMessage(ChatColor.RED + "Resonance Beacons are disabled by the server administrator.");
                return;
            }

            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager == null) {
                player.sendMessage(ChatColor.RED + "Resonance Beacon is still initializing. Try again in a moment.");
                return;
            }

            UUID owner = manager.getOwner(block.getLocation());
            if (!canConfigure(player, owner)) {
                player.sendMessage(
                        ChatColor.RED + "Only this Resonance Beacon owner or a server operator can configure it.");
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

        menu.addItem(STATUS_SLOT, createStatusItem(block, owner, enabled, profile, chunkMode));

        BeaconPlusEffect[] effects = BeaconPlusEffect.configurableValues();
        for (int index = 0; index < effects.length; index++) {
            BeaconPlusEffect effect = effects[index];
            int slot = EFFECT_SLOTS[index];
            menu.addItem(slot, createEffectItem(block, effect, enabled.contains(effect), profile));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, item, action) -> {
                handleEffectClick(pl, block, owner, effect, action.isRightClicked(), action.isShiftClicked());
                return false;
            });
        }

        menu.addItem(
                DISABLE_ALL_SLOT,
                createMenuItem(
                        Material.BARRIER,
                        ChatColor.RED + "Disable All Powers",
                        List.of(
                                ChatColor.GRAY + "Turns off every Resonance Beacon power",
                                ChatColor.GRAY + "including the Activator chunk loader.",
                                "",
                                ChatColor.YELLOW + "Right click to disable everything")));
        menu.addMenuClickHandler(DISABLE_ALL_SLOT, (pl, slot, item, action) -> {
            if (action.isRightClicked()) {
                disableAll(pl, block, owner);
            }
            return false;
        });

        menu.addItem(PYRAMID_INFO_SLOT, createPyramidItem(profile));
        menu.addItem(CONTROLS_SLOT, createControlsItem());
        menu.addItem(
                CLOSE_SLOT,
                createMenuItem(
                        Material.RED_STAINED_GLASS_PANE,
                        ChatColor.RED + "Close",
                        List.of(ChatColor.GRAY + "Close Resonance Beacon configuration.")));
        menu.addMenuClickHandler(CLOSE_SLOT, (pl, slot, item, action) -> {
            pl.closeInventory();
            return false;
        });

        menu.open(player);
    }

    private void handleEffectClick(
            Player player, Block block, UUID owner, BeaconPlusEffect effect, boolean rightClick, boolean shiftClick) {
        if (!rightClick) {
            player.sendMessage(ChatColor.GRAY + "Use right click to buy, enable, disable, or upgrade this power.");
            return;
        }
        if (!validateMenuAction(player, block, owner)) {
            return;
        }
        if (!BeaconPlusConfig.isPowerEnabled(effect)) {
            player.sendMessage(ChatColor.RED + effect.getDisplayName() + " is disabled by the server administrator.");
            openMenu(player, block, owner);
            return;
        }

        int unlocked = BeaconPlusRuntime.getUnlockedTierAtBeacon(block, effect);
        int maximum = BeaconPlusConfig.getMaxTier();
        boolean legacyImported = BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation());

        if (shiftClick) {
            if (legacyImported) {
                player.sendMessage(ChatColor.YELLOW + "This is a legacy-imported beacon. " + ChatColor.GRAY
                        + "Its old BeaconData unlock levels are grandfathered and cannot be purchased again.");
                openMenu(player, block, owner);
                return;
            }
            if (unlocked >= maximum) {
                player.sendMessage(ChatColor.GRAY + effect.getDisplayName() + " is already at Tier " + maximum + ".");
                openMenu(player, block, owner);
                return;
            }
            purchaseAndEnable(player, block, owner, effect);
            return;
        }

        EnumSet<BeaconPlusEffect> enabled = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        if (enabled.contains(effect)) {
            enabled.remove(effect);
            BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
            if (effect == BeaconPlusEffect.ACTIVATOR) {
                BeaconPlusRuntime.reconcileActivator(block);
            }
            BeaconPlusRuntime.refreshPlayerState(player);
            player.playSound(block.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.65F, 1.0F);
            player.sendMessage(ChatColor.GOLD + "Resonance Beacon: " + ChatColor.WHITE + effect.getDisplayName()
                    + ChatColor.GRAY + " is now " + ChatColor.RED + "DISABLED" + ChatColor.GRAY + ".");
            openMenu(player, block, owner);
            return;
        }

        if (unlocked <= 0) {
            if (legacyImported) {
                player.sendMessage(ChatColor.RED + "That power was not unlocked in this imported BeaconData record.");
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
            player.sendMessage(ChatColor.RED + "The Resonance Beacon chunk-loader safety cap would be exceeded.");
        } else {
            player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.35F);
            player.sendMessage(ChatColor.GOLD + "Resonance Beacon: " + ChatColor.WHITE + effect.getDisplayName()
                    + ChatColor.GRAY + " is now " + ChatColor.GREEN + "ENABLED" + ChatColor.GRAY + ".");
        }
        openMenu(player, block, owner);
    }

    private void purchaseAndEnable(Player player, Block block, UUID owner, BeaconPlusEffect effect) {
        BeaconPlusProgression.PurchaseResult result = BeaconPlusProgression.purchaseNextTier(player, owner, effect);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
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
        player.sendMessage(ChatColor.GREEN + "Unlocked " + ChatColor.WHITE + effect.getDisplayName() + ChatColor.GREEN
                + " Tier " + result.newTier() + ChatColor.GRAY + "."
                + (activatorAccepted
                        ? " It is enabled."
                        : " Unlock kept; Activator stayed disabled because of the loader cap."));
        openMenu(player, block, owner);
    }

    private void disableAll(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), EnumSet.noneOf(BeaconPlusEffect.class));
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            manager.updateModes(
                    block.getLocation(), owner, BeaconPlusChunkMode.OFF, manager.getSupportMode(block.getLocation()));
        }
        BeaconPlusRuntime.refreshPlayerState(player);
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.65F, 1.0F);
        player.sendMessage(ChatColor.RED + "All Resonance Beacon powers have been disabled.");
        openMenu(player, block, owner);
    }

    private boolean validateMenuAction(Player player, Block block, UUID expectedOwner) {
        if (!StorageCacheUtils.isBlock(block.getLocation(), getId())) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "That Resonance Beacon no longer exists.");
            return false;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID currentOwner = manager == null ? expectedOwner : manager.getOwner(block.getLocation());
        if (!canConfigure(player, currentOwner)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "You no longer have permission to configure this Resonance Beacon.");
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
        lore.add(ChatColor.GRAY + "Physical pyramid: "
                + (baseSize > 0
                        ? ChatColor.GREEN.toString() + baseSize + "x" + baseSize
                        : ChatColor.RED + "Incomplete"));
        lore.add(ChatColor.GRAY + "Natural power tier: " + tierColor(profile.naturalPowerTier())
                + roman(profile.naturalPowerTier()));
        lore.add(ChatColor.GRAY + "Dominant mineral: " + ChatColor.AQUA + profile.dominantMaterialName());
        lore.add(ChatColor.GRAY + "Average mineral power: " + ChatColor.AQUA
                + String.format(java.util.Locale.ROOT, "%.2f", profile.averageMaterialPower()));
        lore.add(ChatColor.GRAY + "Enabled powers: " + ChatColor.GOLD + enabled.size() + "/28");
        lore.add(ChatColor.GRAY + "Activator coverage: " + ChatColor.AQUA + chunkMode.getDisplayName());
        lore.add("");
        if (BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
            lore.add(ChatColor.YELLOW + "Legacy BeaconData import");
            lore.add(ChatColor.GRAY + "No owner existed in the old format; operator-managed.");
        } else if (owner != null) {
            lore.add(ChatColor.DARK_GRAY + "Unlocks are permanently owned by the placing player.");
        }
        lore.add(
                profile.naturalPowerTier() > 0
                        ? ChatColor.GREEN + "Pyramid resonance is active."
                        : ChatColor.RED + "Build a valid powered mineral pyramid.");
        return createMenuItem(
                profile.naturalPowerTier() > 0 ? Material.NETHER_STAR : Material.GRAY_DYE,
                ChatColor.GOLD + "Resonance Beacon Status",
                lore);
    }

    private ItemStack createEffectItem(
            Block block, BeaconPlusEffect effect, boolean active, BeaconPlusPyramid.Profile profile) {
        boolean serverEnabled = BeaconPlusConfig.isPowerEnabled(effect);
        int unlocked = BeaconPlusRuntime.getUnlockedTierAtBeacon(block, effect);
        int selected = BeaconPlusRuntime.getSelectedTierAtBeacon(block, effect);
        int effective = active ? BeaconPlusRuntime.getEffectiveTierAtBeacon(block, effect) : 0;
        int maximum = BeaconPlusConfig.getMaxTier();

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + effect.getDescription());
        lore.add("");
        lore.add(ChatColor.GRAY + "Server: "
                + (serverEnabled ? ChatColor.GREEN + "AVAILABLE" : ChatColor.RED + "DISABLED"));
        lore.add(ChatColor.GRAY + "Unlocked: " + tierColor(unlocked) + roman(unlocked) + ChatColor.DARK_GRAY + "/III");
        if (BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation()) && selected > 0) {
            lore.add(ChatColor.GRAY + "Legacy selected tier: " + tierColor(selected) + roman(selected));
        }
        lore.add(ChatColor.GRAY + "Pyramid ceiling: " + tierColor(profile.naturalPowerTier())
                + roman(profile.naturalPowerTier()));
        lore.add(ChatColor.GRAY + "Status: " + (active ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        if (active) {
            lore.add(ChatColor.GRAY + "Effective tier: "
                    + (effective > 0 ? tierColor(effective) + roman(effective) : ChatColor.RED + "DORMANT"));
        }
        if (effect == BeaconPlusEffect.ACTIVATOR) {
            lore.add(ChatColor.DARK_GRAY + "Tier I = this chunk; II = 3x3; III = 5x5.");
        }

        if (serverEnabled && unlocked < maximum && !BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
            lore.add("");
            lore.add(ChatColor.GOLD + "Next Tier: " + roman(unlocked + 1));
            lore.add(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW
                    + BeaconPlusProgression.describeCost(effect, unlocked + 1));
        }

        lore.add("");
        if (!serverEnabled) {
            lore.add(ChatColor.RED + "Disabled in config.yml");
        } else if (unlocked <= 0 && BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
            lore.add(ChatColor.DARK_GRAY + "Not unlocked in imported BeaconData.");
        } else if (unlocked <= 0) {
            lore.add(ChatColor.YELLOW + "Right click to buy Tier I + enable");
        } else {
            lore.add(ChatColor.YELLOW + "Right click to " + (active ? "disable" : "enable"));
            if (unlocked < maximum && !BeaconPlusLegacyDataStore.isLegacyImported(block.getLocation())) {
                lore.add(ChatColor.YELLOW + "Shift + Right Click to buy Tier " + roman(unlocked + 1));
            }
        }

        Material icon = serverEnabled ? effect.getIcon() : Material.BARRIER;
        String nameColor = !serverEnabled
                ? ChatColor.DARK_GRAY.toString()
                : active
                        ? ChatColor.GREEN.toString()
                        : unlocked > 0 ? ChatColor.GOLD.toString() : ChatColor.RED.toString();
        return createMenuItem(icon, nameColor + effect.getDisplayName(), lore);
    }

    private ItemStack createPyramidItem(BeaconPlusPyramid.Profile profile) {
        return createMenuItem(
                profile.naturalPowerTier() > 0 ? profile.dominantMaterial() : Material.IRON_BLOCK,
                ChatColor.AQUA + "Pyramid Resonance",
                List.of(
                        ChatColor.GRAY + "Tier I: 3x3+ base / material power 1.0",
                        ChatColor.GRAY + "Tier II: 5x5+ base / material power 3.0",
                        ChatColor.GRAY + "Tier III: 7x7+ base / material power 4.0",
                        "",
                        ChatColor.DARK_GRAY + "Default mineral power:",
                        ChatColor.GRAY + "Iron 1 • Gold 2 • Emerald 3",
                        ChatColor.GRAY + "Diamond 4 • Netherite 5",
                        "",
                        ChatColor.DARK_GRAY + "All thresholds are server-configurable."));
    }

    private ItemStack createControlsItem() {
        return createMenuItem(
                Material.BOOK,
                ChatColor.YELLOW + "Power Controls",
                List.of(
                        ChatColor.GRAY + "Right click a locked power to buy Tier I",
                        ChatColor.GRAY + "and immediately enable it.",
                        ChatColor.GRAY + "Right click an unlocked power to toggle it.",
                        ChatColor.GRAY + "Shift + Right Click buys the next tier.",
                        "",
                        ChatColor.GRAY + "Purchased tiers stay with the beacon owner.",
                        ChatColor.GRAY + "The physical pyramid caps the tier that can run."));
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

    private static ChatColor tierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.YELLOW;
            case 2 -> ChatColor.AQUA;
            case 3 -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.RED;
        };
    }
}
