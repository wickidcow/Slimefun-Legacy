package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.utils.ArmorStandUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Adventurer's Curios upgrade of the classic Hologram Projector.
 *
 * <p>The projector retains editable floating text and adds a real persisted block-menu slot whose
 * item is mirrored into an {@link ItemDisplay}. Text and item visibility are independent, the item
 * can be raised/lowered directly, and its position can be flipped above or below the text anchor.
 */
public final class HologramDisplayProjector extends SlimefunItem {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private static final int DISPLAY_ITEM_SLOT = 13;

    private static final String TEXT_KEY = "curios_hologram_text";
    private static final String OWNER_KEY = "owner";
    private static final String TEXT_OFFSET_KEY = "curios_hologram_text_offset";
    private static final String ITEM_OFFSET_KEY = "curios_hologram_item_offset";
    private static final String TEXT_VISIBLE_KEY = "curios_hologram_text_visible";
    private static final String ITEM_VISIBLE_KEY = "curios_hologram_item_visible";

    private static final double DEFAULT_TEXT_OFFSET = 0.5D;
    private static final double TEXT_RENDER_OFFSET = 1.0D;
    private static final double DEFAULT_ITEM_DISTANCE = 0.65D;
    private static final double DEFAULT_ITEM_OFFSET = DEFAULT_TEXT_OFFSET + TEXT_RENDER_OFFSET + DEFAULT_ITEM_DISTANCE;

    private final NamespacedKey textMarkerKey =
            new NamespacedKey(Objects.requireNonNull(Slimefun.instance()), "curios_hologram_projector_text");
    private final NamespacedKey itemMarkerKey =
            new NamespacedKey(Objects.requireNonNull(Slimefun.instance()), "curios_hologram_projector_item");

    @ParametersAreNonnullByDefault
    public HologramDisplayProjector(
            ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        new BlockMenuPreset(getId(), "&6Hologram Display Projector") {
            @Override
            public void init() {
                setSize(27);
                for (int slot = 0; slot < 27; slot++) {
                    if (slot != DISPLAY_ITEM_SLOT) {
                        addItem(
                                slot,
                                menuItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()),
                                ChestMenuUtils.getEmptyClickHandler());
                    }
                }
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block block) {
                updateMenu(menu, block);
                refreshVisuals(block);
            }

            @Override
            protected @Nullable ItemStack onItemStackChange(
                    @Nonnull DirtyChestMenu menu,
                    int slot,
                    @Nullable ItemStack previous,
                    @Nullable ItemStack next) {
                if (slot == DISPLAY_ITEM_SLOT && menu instanceof BlockMenu blockMenu) {
                    scheduleItemRefresh(blockMenu);
                }
                return next;
            }

            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                if (player.hasPermission("slimefun.inventory.bypass")) {
                    return true;
                }

                String owner = StorageCacheUtils.getData(block.getLocation(), OWNER_KEY);
                return player.getUniqueId().toString().equals(owner)
                        && Slimefun.getIntegrations().canInteractBlock(player, block)
                        && HologramDisplayProjector.this.canUse(player, false);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                // The display slot is deliberately player-configured. Cargo/hoppers do not rewrite it.
                return new int[0];
            }
        };

        addItemHandler(onPlace(), onBreak());
    }

    private @Nonnull BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(BlockPlaceEvent event) {
                Block block = event.getBlockPlaced();
                var data = StorageCacheUtils.getBlock(block.getLocation());
                if (data == null) {
                    return;
                }

                data.setData(TEXT_KEY, "Use the projector to edit this text");
                data.setData(TEXT_OFFSET_KEY, String.valueOf(DEFAULT_TEXT_OFFSET));
                data.setData(ITEM_OFFSET_KEY, String.valueOf(DEFAULT_ITEM_OFFSET));
                data.setData(TEXT_VISIBLE_KEY, Boolean.TRUE.toString());
                data.setData(ITEM_VISIBLE_KEY, Boolean.TRUE.toString());
                data.setData(OWNER_KEY, event.getPlayer().getUniqueId().toString());

                refreshVisuals(block);
            }
        };
    }

    private @Nonnull BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block block) {
                BlockMenu menu = StorageCacheUtils.getMenu(block.getLocation());
                if (menu != null) {
                    menu.dropItems(block.getLocation(), DISPLAY_ITEM_SLOT);
                }

                removeTextStand(block);
                removeItemDisplay(block);
            }
        };
    }

    private void updateMenu(@Nonnull BlockMenu menu, @Nonnull Block block) {
        menu.replaceExistingItem(
                3,
                menuItem(
                        Material.NAME_TAG,
                        "&eDisplayed Text",
                        List.of("", "&7" + storedText(block), "", "&eClick &7to edit")));
        menu.addMenuClickHandler(3, (player, slot, item, action) -> {
            player.closeInventory();
            player.sendMessage(LEGACY_SECTION.deserialize(
                    "§7Enter the hologram text in chat. §r(Color codes are supported.)"));

            ChatUtils.awaitInput(player, message -> {
                Location location = block.getLocation();
                if (!StorageCacheUtils.isBlock(location, getId())) {
                    return;
                }

                StorageCacheUtils.setData(location, TEXT_KEY, message.replace('&', '§'));
                refreshText(block);

                BlockMenu liveMenu = StorageCacheUtils.getMenu(location);
                if (liveMenu != null) {
                    updateMenu(liveMenu, block);
                    liveMenu.open(player);
                }
            });
            return false;
        });

        menu.replaceExistingItem(
                5,
                menuItem(
                        Material.CLOCK,
                        "&eText Height: &f"
                                + NumberUtils.reparseDouble(readTextOffset(block) + TEXT_RENDER_OFFSET),
                        List.of(
                                "",
                                "&fLeft Click: &7raise +0.1",
                                "&fRight Click: &7lower -0.1",
                                "&8The item moves with the text")));
        menu.addMenuClickHandler(5, (player, slot, item, action) -> {
            double delta = action.isRightClicked() ? -0.1D : 0.1D;
            double textOffset = NumberUtils.reparseDouble(readTextOffset(block) + delta);
            double itemOffset = NumberUtils.reparseDouble(readItemOffset(block) + delta);
            StorageCacheUtils.setData(block.getLocation(), TEXT_OFFSET_KEY, String.valueOf(textOffset));
            StorageCacheUtils.setData(block.getLocation(), ITEM_OFFSET_KEY, String.valueOf(itemOffset));
            refreshVisuals(block);
            updateMenu(menu, block);
            return false;
        });

        boolean textVisible = readBoolean(block, TEXT_VISIBLE_KEY, true);
        menu.replaceExistingItem(
                10,
                menuItem(
                        textVisible ? Material.LIME_DYE : Material.GRAY_DYE,
                        textVisible ? "&aText Visible" : "&7Text Hidden",
                        List.of("", "&eClick &7to " + (textVisible ? "hide" : "show") + " the text")));
        menu.addMenuClickHandler(10, (player, slot, item, action) -> {
            StorageCacheUtils.setData(
                    block.getLocation(), TEXT_VISIBLE_KEY, Boolean.toString(!readBoolean(block, TEXT_VISIBLE_KEY, true)));
            refreshText(block);
            updateMenu(menu, block);
            return false;
        });

        menu.replaceExistingItem(
                12,
                menuItem(
                        Material.ITEM_FRAME,
                        "&eDisplay Item",
                        List.of(
                                "",
                                "&7Drop any item into the empty",
                                "&7center slot to project a copy.",
                                "",
                                "&8The real item remains stored safely.")));

        boolean above = isItemAboveText(block);
        menu.replaceExistingItem(
                14,
                menuItem(
                        Material.ARROW,
                        "&eItem Position: &f" + (above ? "ABOVE" : "BELOW"),
                        List.of(
                                "",
                                "&eClick &7to move it " + (above ? "below" : "above") + " the text",
                                "&8Keeps the current distance from the text")));
        menu.addMenuClickHandler(14, (player, slot, item, action) -> {
            double textHeight = visibleTextHeight(block);
            double itemHeight = readItemOffset(block);
            double distance = Math.max(0.25D, Math.abs(itemHeight - textHeight));
            double flipped = itemHeight >= textHeight ? textHeight - distance : textHeight + distance;
            StorageCacheUtils.setData(
                    block.getLocation(), ITEM_OFFSET_KEY, String.valueOf(NumberUtils.reparseDouble(flipped)));
            refreshItemDisplay(block);
            updateMenu(menu, block);
            return false;
        });

        menu.replaceExistingItem(
                16,
                menuItem(
                        Material.CLOCK,
                        "&eItem Height: &f" + NumberUtils.reparseDouble(readItemOffset(block)),
                        List.of(
                                "",
                                "&fLeft Click: &7raise +0.1",
                                "&fRight Click: &7lower -0.1",
                                "&8Independent of text height")));
        menu.addMenuClickHandler(16, (player, slot, item, action) -> {
            double offset =
                    NumberUtils.reparseDouble(readItemOffset(block) + (action.isRightClicked() ? -0.1D : 0.1D));
            StorageCacheUtils.setData(block.getLocation(), ITEM_OFFSET_KEY, String.valueOf(offset));
            refreshItemDisplay(block);
            updateMenu(menu, block);
            return false;
        });

        boolean itemVisible = readBoolean(block, ITEM_VISIBLE_KEY, true);
        menu.replaceExistingItem(
                22,
                menuItem(
                        itemVisible ? Material.ENDER_EYE : Material.ENDER_PEARL,
                        itemVisible ? "&aItem Visible" : "&7Item Hidden",
                        List.of(
                                "",
                                "&eClick &7to " + (itemVisible ? "hide" : "show") + " the projected item")));
        menu.addMenuClickHandler(22, (player, slot, item, action) -> {
            StorageCacheUtils.setData(
                    block.getLocation(), ITEM_VISIBLE_KEY, Boolean.toString(!readBoolean(block, ITEM_VISIBLE_KEY, true)));
            refreshItemDisplay(block);
            updateMenu(menu, block);
            return false;
        });

        // Slot 13 remains a real inventory slot. Allow ordinary click behavior, then refresh next tick.
        menu.addMenuClickHandler(DISPLAY_ITEM_SLOT, (player, slot, item, action) -> {
            menu.markDirty();
            scheduleItemRefresh(menu);
            return true;
        });

        // Shift-clicks originate from the player's inventory, so schedule the same next-tick refresh there too.
        menu.addPlayerInventoryClickHandler((player, slot, item, action) -> {
            menu.markDirty();
            scheduleItemRefresh(menu);
            return true;
        });

        menu.addMenuCloseHandler(player -> {
            menu.markDirty();
            scheduleItemRefresh(menu);
        });
    }

    private void scheduleItemRefresh(@Nonnull BlockMenu menu) {
        Location location = menu.getLocation().clone();
        Slimefun.getSchedulerService().runAtLater(
                location,
                () -> {
                    if (StorageCacheUtils.isBlock(location, getId())) {
                        refreshItemDisplay(location.getBlock());
                    }
                },
                1L);
    }

    private void refreshVisuals(@Nonnull Block block) {
        refreshText(block);
        refreshItemDisplay(block);
    }

    private void refreshText(@Nonnull Block block) {
        ArmorStand stand = getTextStand(block, true);
        if (stand == null) {
            return;
        }

        Location target = new Location(
                block.getWorld(), block.getX() + 0.5D, block.getY() + readTextOffset(block), block.getZ() + 0.5D);
        if (!stand.getLocation().equals(target)) {
            stand.teleport(target);
        }

        stand.customName(LEGACY_SECTION.deserialize(storedText(block)));
        stand.setCustomNameVisible(readBoolean(block, TEXT_VISIBLE_KEY, true));
    }

    private void refreshItemDisplay(@Nonnull Block block) {
        if (!readBoolean(block, ITEM_VISIBLE_KEY, true)) {
            removeItemDisplay(block);
            return;
        }

        BlockMenu menu = StorageCacheUtils.getMenu(block.getLocation());
        ItemStack stored = menu == null ? null : menu.getItemInSlot(DISPLAY_ITEM_SLOT);
        if (stored == null || stored.isEmpty() || stored.getType().isAir() || stored.getAmount() <= 0) {
            removeItemDisplay(block);
            return;
        }

        ItemDisplay display = getItemDisplay(block, true);
        if (display == null) {
            return;
        }

        ItemStack shown = stored.clone();
        shown.setAmount(1);
        display.setItemStack(shown);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.CENTER);

        Location target = new Location(
                block.getWorld(), block.getX() + 0.5D, block.getY() + readItemOffset(block), block.getZ() + 0.5D);
        if (!display.getLocation().equals(target)) {
            display.teleport(target);
        }
    }

    private @Nullable ArmorStand getTextStand(@Nonnull Block block, boolean create) {
        String marker = marker(block);
        ArmorStand found = null;

        for (Entity entity : block.getChunk().getEntities()) {
            if (entity instanceof ArmorStand stand
                    && marker.equals(stand.getPersistentDataContainer().get(textMarkerKey, PersistentDataType.STRING))) {
                if (found == null) {
                    found = stand;
                } else {
                    stand.remove();
                }
            }
        }

        if (found != null || !create) {
            return found;
        }

        Location location = new Location(
                block.getWorld(), block.getX() + 0.5D, block.getY() + readTextOffset(block), block.getZ() + 0.5D);
        ArmorStand stand = ArmorStandUtils.spawnArmorStand(location, storedText(block));
        stand.getPersistentDataContainer().set(textMarkerKey, PersistentDataType.STRING, marker);
        stand.setPersistent(true);
        return stand;
    }

    private @Nullable ItemDisplay getItemDisplay(@Nonnull Block block, boolean create) {
        String marker = marker(block);
        ItemDisplay found = null;

        for (Entity entity : block.getChunk().getEntities()) {
            if (entity instanceof ItemDisplay display
                    && marker.equals(display.getPersistentDataContainer().get(itemMarkerKey, PersistentDataType.STRING))) {
                if (found == null) {
                    found = display;
                } else {
                    display.remove();
                }
            }
        }

        if (found != null || !create) {
            return found;
        }

        Location location = new Location(
                block.getWorld(), block.getX() + 0.5D, block.getY() + readItemOffset(block), block.getZ() + 0.5D);
        return block.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.getPersistentDataContainer().set(itemMarkerKey, PersistentDataType.STRING, marker);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setSilent(true);
            display.setGravity(false);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.CENTER);
        });
    }

    private void removeTextStand(@Nonnull Block block) {
        ArmorStand stand = getTextStand(block, false);
        if (stand != null) {
            stand.remove();
        }
    }

    private void removeItemDisplay(@Nonnull Block block) {
        ItemDisplay display = getItemDisplay(block, false);
        if (display != null) {
            display.remove();
        }
    }

    private String storedText(@Nonnull Block block) {
        String text = StorageCacheUtils.getData(block.getLocation(), TEXT_KEY);
        if (text == null) {
            text = "Use the projector to edit this text";
            StorageCacheUtils.setData(block.getLocation(), TEXT_KEY, text);
        }
        return text;
    }

    private double readTextOffset(@Nonnull Block block) {
        return readDouble(block, TEXT_OFFSET_KEY, DEFAULT_TEXT_OFFSET);
    }

    private double readItemOffset(@Nonnull Block block) {
        return readDouble(block, ITEM_OFFSET_KEY, DEFAULT_ITEM_OFFSET);
    }

    private double visibleTextHeight(@Nonnull Block block) {
        return readTextOffset(block) + TEXT_RENDER_OFFSET;
    }

    private boolean isItemAboveText(@Nonnull Block block) {
        return readItemOffset(block) >= visibleTextHeight(block);
    }

    private double readDouble(@Nonnull Block block, @Nonnull String key, double fallback) {
        String raw = StorageCacheUtils.getData(block.getLocation(), key);
        if (raw != null) {
            try {
                double value = Double.parseDouble(raw);
                if (Double.isFinite(value)) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Repair malformed data below.
            }
        }

        StorageCacheUtils.setData(block.getLocation(), key, String.valueOf(fallback));
        return fallback;
    }

    private boolean readBoolean(@Nonnull Block block, @Nonnull String key, boolean fallback) {
        String raw = StorageCacheUtils.getData(block.getLocation(), key);
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }

        StorageCacheUtils.setData(block.getLocation(), key, Boolean.toString(fallback));
        return fallback;
    }

    private static @Nonnull ItemStack menuItem(
            @Nonnull Material material, @Nonnull String displayName, @Nonnull List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(legacyText(displayName));
        meta.lore(lore.stream().map(HologramDisplayProjector::legacyText).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static @Nonnull Component legacyText(@Nonnull String value) {
        return LEGACY_AMPERSAND.deserialize(value).decoration(TextDecoration.ITALIC, false);
    }

    private static @Nonnull String marker(@Nonnull Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
