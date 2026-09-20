package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.core.services.stability.BackpackOpenRegistry;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.Cooler;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.utils.ThreadUtils;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link Listener} is responsible for all events centered around a {@link SlimefunBackpack}.
 * This also includes the {@link Cooler}
 *
 * @author TheBusyBiscuit
 * @author Walshy
 * @author NihilistBrew
 * @author AtomicScience
 * @author VoidAngel
 * @author John000708
 *
 * @see SlimefunBackpack
 * @see PlayerBackpack
 */
public class BackpackListener implements Listener {
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    // Stores the player uuid maps to the opening backpack uuid
    private final BackpackOpenRegistry openRegistry = new BackpackOpenRegistry();
    private final Map<UUID, UUID> backpacks = new ConcurrentHashMap<>();
    private final Map<UUID, SlimefunBackpack> backpackInstances = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();

    public void register(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Returns whether this player currently has a backpack request waiting for its
     * asynchronous profile/backpack lookup to finish.
     *
     * <p>This is intentionally different from an already-open backpack session. It
     * exists so the central interaction dispatcher can reject a second hand/item
     * interaction during the short pending window instead of allowing another GUI
     * to race the delayed backpack open callback.</p>
     */
    public boolean isOpening(@Nonnull UUID playerId) {
        return openRegistry.isOpening(playerId);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();

        if (e.getInventory().getHolder(false) instanceof PlayerBackpack backpack) {
            beginBackpackSave(p.getUniqueId(), backpack, "close");
            SoundEffect.BACKPACK_CLOSE_SOUND.playFor(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        UUID playerId = player.getUniqueId();

        // InventoryCloseEvent normally starts this save first, but explicitly
        // start it here as well so a disconnect cannot release the canonical
        // reservation before persistence when platform event ordering changes.
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof PlayerBackpack backpack) {
            beginBackpackSave(playerId, backpack, "quit");
        }

        if (!pendingSaves.containsKey(playerId)) {
            finishBackpackSession(playerId, null);
        }
    }

    private void beginBackpackSave(
            @Nonnull UUID playerId, @Nonnull PlayerBackpack backpack, @Nonnull String context) {
        /*
         * PlayerBackpack is public API, so addons can open one without going
         * through this listener's reservation path. Persist those inventories,
         * but never let their save lifecycle mutate a different tracked session
         * that the same player may start while the detached save is still running.
         */
        if (!backpack.getUniqueId().equals(backpacks.get(playerId))) {
            saveDetachedBackpack(backpack, context);
            return;
        }

        final CompletableFuture<Void> save;

        synchronized (pendingSaves) {
            if (pendingSaves.containsKey(playerId)) {
                return;
            }

            try {
                save = Slimefun.getDatabaseManager()
                        .getProfileDataController()
                        .saveBackpackInventoryAsync(backpack);
            } catch (RuntimeException | LinkageError failure) {
                Slimefun.logger()
                        .log(Level.SEVERE, "An Exception occurred while starting a backpack save on " + context, failure);
                finishBackpackSession(playerId, backpack.getUniqueId());
                return;
            }

            pendingSaves.put(playerId, save);

            // The GUI is no longer active, so stop applying the broad "open
            // backpack" interaction guards immediately. The canonical UUID
            // reservation intentionally remains held until the save future
            // completes, which is the protection needed against stale overlap.
            backpacks.remove(playerId, backpack.getUniqueId());
            backpackInstances.remove(playerId);
        }

        save.whenComplete((ignored, failure) -> {
            if (failure != null) {
                Slimefun.logger()
                        .log(Level.SEVERE, "An Exception occurred while saving a backpack on " + context, failure);
            }

            pendingSaves.remove(playerId, save);
            finishBackpackSession(playerId, backpack.getUniqueId());
        });
    }

    private void saveDetachedBackpack(@Nonnull PlayerBackpack backpack, @Nonnull String context) {
        final CompletableFuture<Void> save;

        try {
            save = Slimefun.getDatabaseManager()
                    .getProfileDataController()
                    .saveBackpackInventoryAsync(backpack);
        } catch (RuntimeException | LinkageError failure) {
            Slimefun.logger()
                    .log(
                            Level.SEVERE,
                            "An Exception occurred while starting a detached backpack save on " + context,
                            failure);
            return;
        }

        save.whenComplete((ignored, failure) -> {
            if (failure != null) {
                Slimefun.logger()
                        .log(Level.SEVERE, "An Exception occurred while saving a detached backpack on " + context, failure);
            }
        });
    }

    private void finishBackpackSession(@Nonnull UUID playerId, @Nullable UUID backpackId) {
        if (backpackId == null) {
            backpacks.remove(playerId);
        } else {
            backpacks.remove(playerId, backpackId);
        }
        backpackInstances.remove(playerId);
        openRegistry.release(playerId);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        if (openRegistry.isOpening(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (backpacks.containsKey(e.getPlayer().getUniqueId())) {
            ItemStack item = e.getItemDrop().getItemStack();
            SlimefunItem sfItem = SlimefunItem.getByItem(item);

            if (sfItem instanceof SlimefunBackpack) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerSwap(PlayerSwapHandItemsEvent e) {
        if (openRegistry.isOpening(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        var player = e.getPlayer();
        if (!backpacks.containsKey(player.getUniqueId())) {
            return;
        }

        // Both event items participate in the swap. Checking only the current
        // off-hand leaves a bypass when the physical backpack is in the main hand
        // and the off-hand is empty or contains a non-backpack item.
        if (isBackpackItem(e.getMainHandItem()) || isBackpackItem(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent atEntityEvent) {
        if (openRegistry.isOpening(atEntityEvent.getPlayer().getUniqueId())) {
            atEntityEvent.setCancelled(true);
            return;
        }
        var player = atEntityEvent.getPlayer();
        if (!backpacks.containsKey(player.getUniqueId())) {
            return;
        }
        atEntityEvent.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent atEntityEvent) {
        if (openRegistry.isOpening(atEntityEvent.getPlayer().getUniqueId())) {
            atEntityEvent.setCancelled(true);
            return;
        }
        var player = atEntityEvent.getPlayer();
        if (!backpacks.containsKey(player.getUniqueId())) {
            return;
        }
        atEntityEvent.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        UUID playerId = e.getWhoClicked().getUniqueId();
        if (openRegistry.isOpening(playerId)) {
            e.setCancelled(true);
            return;
        }

        if (!(e.getView().getTopInventory().getHolder(false) instanceof PlayerBackpack openedBackpack)) {
            return;
        }

        SlimefunBackpack slimefunBackpack = backpackInstances.get(playerId);
        UUID expectedBackpack = backpacks.get(playerId);
        if (slimefunBackpack == null || !openedBackpack.getUniqueId().equals(expectedBackpack)) {
            // A backpack inventory without matching listener/session state is not a
            // normal gameplay state. Fail closed instead of allowing a stale view
            // to mutate storage.
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() == null) {
            return;
        }

        boolean clickedBackpack = e.getClickedInventory().equals(e.getView().getTopInventory());
        if (clickedBackpack) {
            if (e.getClick() == ClickType.NUMBER_KEY) {
                // An item from the hotbar would enter the backpack.
                ItemStack hotbarItem = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
                if (!isAllowed(slimefunBackpack, hotbarItem)) {
                    e.setCancelled(true);
                }
            } else if (e.getClick() == ClickType.SWAP_OFFHAND) {
                // An item from the off hand would enter the backpack.
                ItemStack offHandItem = e.getWhoClicked().getInventory().getItemInOffHand();
                if (!isAllowed(slimefunBackpack, offHandItem)) {
                    e.setCancelled(true);
                }
            } else if (!isAllowed(slimefunBackpack, e.getCursor())) {
                // Validate the item entering the slot, not the item already in it.
                // The old current-item check allowed a forbidden backpack/shulker
                // to be placed onto an empty slot from the cursor.
                e.setCancelled(true);
            }
            return;
        }

        if (e.getClickedInventory().getType() == InventoryType.PLAYER) {
            // Preserve the historical protection that keeps physical backpack
            // items stationary while a backpack GUI is open. This prevents the
            // item representing an open container from being moved, swapped or
            // otherwise transformed mid-session.
            if (isBackpackItem(e.getCurrentItem()) || isBackpackItem(e.getCursor())) {
                e.setCancelled(true);
                return;
            }

            if (e.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
                if (isBackpackItem(hotbarItem)) {
                    e.setCancelled(true);
                    return;
                }
            } else if (e.getClick() == ClickType.SWAP_OFFHAND
                    && isBackpackItem(e.getWhoClicked().getInventory().getItemInOffHand())) {
                e.setCancelled(true);
                return;
            }

            // Shift-click is the only ordinary player-inventory click that sends
            // the current item directly into the top inventory.
            if (e.isShiftClick() && !isAllowed(slimefunBackpack, e.getCurrentItem())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        UUID playerId = e.getWhoClicked().getUniqueId();
        if (openRegistry.isOpening(playerId)) {
            e.setCancelled(true);
            return;
        }

        if (!(e.getView().getTopInventory().getHolder(false) instanceof PlayerBackpack openedBackpack)) {
            return;
        }

        SlimefunBackpack slimefunBackpack = backpackInstances.get(playerId);
        UUID expectedBackpack = backpacks.get(playerId);
        if (slimefunBackpack == null || !openedBackpack.getUniqueId().equals(expectedBackpack)) {
            e.setCancelled(true);
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        for (Map.Entry<Integer, ItemStack> entry : e.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            ItemStack newItem = entry.getValue();

            if (rawSlot < topSize) {
                if (!isAllowed(slimefunBackpack, newItem)) {
                    e.setCancelled(true);
                    return;
                }
            } else if (rawSlot < e.getView().countSlots()) {
                // Do not let drag mechanics alter a backpack item in the player's
                // inventory while another backpack is open. This mirrors the click
                // protection and closes the drag-event bypass.
                ItemStack currentItem = e.getView().getItem(rawSlot);
                if (isBackpackItem(currentItem) || isBackpackItem(newItem)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean isAllowed(@Nonnull SlimefunBackpack backpack, @Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }

        return backpack.isItemAllowed(item, SlimefunItem.getByItem(item));
    }

    private boolean isBackpackItem(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR && SlimefunItem.getByItem(item) instanceof SlimefunBackpack;
    }

    private boolean isStillHeld(@Nonnull Player player, @Nonnull ItemStack item) {
        return item.equals(player.getInventory().getItemInMainHand())
                || item.equals(player.getInventory().getItemInOffHand());
    }

    @ParametersAreNonnullByDefault
    public void openBackpack(Player p, ItemStack item, SlimefunBackpack backpack) {
        if (!backpack.canUse(p, true)) {
            return;
        }

        if (item.getAmount() != 1) {
            Slimefun.getLocalization().sendMessage(p, "backpack.no-stack", true);
            return;
        }

        var meta = item.getItemMeta();
        if (!PlayerBackpack.isOwnerOnline(meta)) {
            Slimefun.getLocalization().sendMessage(p, "backpack.not-backpack-owner");
            return;
        }

        UUID playerId = p.getUniqueId();
        boolean hasStoredIdentity = PlayerBackpack.getBackpackUUID(meta).isPresent()
                || PlayerBackpack.getBackpackID(meta).isPresent();
        String reservationKey = hasStoredIdentity ? getReservationKey(meta) : "new:" + playerId;

        /*
         * Reserve before PlayerProfile.get(...), not inside its callback. A slow
         * profile load creates a real interaction window; historically a player
         * could drop or transfer the backpack while the open request was still
         * waiting and then receive a stale/second view when loading completed.
         */
        if (backpacks.containsKey(playerId) || !openRegistry.reserve(playerId, reservationKey)) {
            Slimefun.getLocalization().sendMessage(p, "backpack.already-open", true);
            return;
        }

        try {
            boolean profileReady = PlayerProfile.get(
                    p, profile -> openBackpackInternal(p, item, backpack, reservationKey, hasStoredIdentity));
            if (!profileReady) {
                Slimefun.getLocalization().sendMessage(p, "messages.opening-backpack");
            }
        } catch (RuntimeException | Error ex) {
            openRegistry.release(playerId, reservationKey);
            throw ex;
        }
    }

    @ParametersAreNonnullByDefault
    private void openBackpackInternal(
            Player p,
            ItemStack item,
            SlimefunBackpack backpackItem,
            String reservationKey,
            boolean hadStoredIdentity) {
        UUID playerId = p.getUniqueId();
        boolean keepSession = false;

        try {
            if (!p.isOnline()) {
                return;
            }
            if (item.getAmount() != 1) {
                Slimefun.getLocalization().sendMessage(p, "backpack.no-stack", true);
                return;
            }
            if (!isStillHeld(p, item)) {
                Slimefun.getLocalization().sendMessage(p, "backpack.not-original-item", true);
                return;
            }

            var meta = item.getItemMeta();
            if (!PlayerBackpack.isOwnerOnline(meta)) {
                Slimefun.getLocalization().sendMessage(p, "backpack.not-backpack-owner");
                return;
            }

            boolean hasStoredIdentity = PlayerBackpack.getBackpackUUID(meta).isPresent()
                    || PlayerBackpack.getBackpackID(meta).isPresent();
            if (hasStoredIdentity != hadStoredIdentity
                    || (hasStoredIdentity && !reservationKey.equals(getReservationKey(meta)))) {
                Slimefun.getLocalization().sendMessage(p, "backpack.not-original-item", true);
                return;
            }

            if (!hasStoredIdentity) {
                // A fresh backpack only needed the pending reservation while its
                // profile was loading. Naming/binding already has its own exact-item
                // validation and does not open a storage inventory yet.
                openRegistry.release(playerId, reservationKey);
                Slimefun.getLocalization().sendMessage(p, "backpack.set-name", true);
                UUID puuid = p.getUniqueId();
                ItemStack itemCopy = item.clone();
                Slimefun.getChatCatcher().scheduleCatcher(puuid, name -> {
                    Player player = Bukkit.getPlayer(puuid);
                    // Don't let player quit server during the input
                    if (player == null) return;
                    var pInv = player.getInventory();
                    // Check if the player changed the amount of item
                    if (item.getAmount() != 1) {
                        Slimefun.getLocalization().sendMessage(player, "backpack.no-stack", true);
                        return;
                    }
                    // Check if the item is modified during the chat input
                    if (!Objects.equals(itemCopy, item)) {
                        Slimefun.getLocalization().sendMessage(player, "backpack.not-original-item", true);
                        return;
                    }
                    // Check if the player moves the item
                    if (!item.equals(pInv.getItemInMainHand()) && !item.equals(pInv.getItemInOffHand())) {
                        Slimefun.getLocalization().sendMessage(player, "backpack.not-original-item", true);
                        return;
                    }
                    // Create the backpack, and bind
                    PlayerProfile.get(player, profile -> {
                        PlayerBackpack.bindItem(
                                item,
                                Slimefun.getDatabaseManager()
                                        .getProfileDataController()
                                        .createBackpack(
                                                player,
                                                formatBackpackName(name),
                                                profile.nextBackpackNum(),
                                                backpackItem.getSize()));
                    });
                });
                return;
            }

            PlayerBackpack.getAsync(item)
                    .whenCompleteAsync(
                            (bp, ex) -> {
                                boolean keepResolvedSession = false;
                                try {
                                    if (!p.isOnline()) {
                                        return;
                                    }
                                    if (!isStillHeld(p, item)) {
                                        Slimefun.getLocalization().sendMessage(p, "backpack.not-original-item", true);
                                        return;
                                    }
                                    if (ex != null) {
                                        Slimefun.logger()
                                                .log(
                                                        Level.SEVERE,
                                                        "An Exception occurred while opening a backpack",
                                                        ex);
                                        return;
                                    }
                                    if (bp == null) {
                                        Slimefun.logger()
                                                .warning(() -> "Could not resolve backpack identity "
                                                        + reservationKey
                                                        + " for player "
                                                        + p.getUniqueId()
                                                        + ". The item may reference missing or stale storage data.");
                                        return;
                                    }
                                    if (bp.isInvalid()) {
                                        Slimefun.logger()
                                                .warning(() -> "Refused to open invalid backpack "
                                                        + reservationKey
                                                        + " for player "
                                                        + p.getUniqueId());
                                        return;
                                    }

                                    String canonicalKey = "uuid:" + bp.getUniqueId();
                                    if (!openRegistry.activate(playerId, reservationKey, canonicalKey)) {
                                        Slimefun.getLocalization().sendMessage(p, "backpack.already-open", true);
                                        return;
                                    }

                                    PlayerBackpack.migrateLegacyItem(item, bp);

                                    // Defense in depth for inventories opened outside the normal
                                    // reservation path or stale state left by another plugin.
                                    if (backpacks.containsValue(bp.getUniqueId())
                                            || !bp.getInventory().getViewers().isEmpty()) {
                                        Slimefun.getLocalization().sendMessage(p, "backpack.already-open", true);
                                        return;
                                    }

                                    SoundEffect.BACKPACK_OPEN_SOUND.playAt(p.getLocation(), SoundCategory.PLAYERS);
                                    backpacks.put(playerId, bp.getUniqueId());
                                    backpackInstances.put(playerId, backpackItem);
                                    bp.open(p);

                                    if (p.getOpenInventory().getTopInventory().getHolder(false) != bp) {
                                        Slimefun.logger()
                                                .warning(() -> "Backpack " + bp.getUniqueId()
                                                        + " did not become the active inventory for player " + playerId);
                                        return;
                                    }

                                    // The canonical reservation intentionally stays active until
                                    // InventoryCloseEvent or PlayerQuitEvent releases it.
                                    keepResolvedSession = true;
                                } finally {
                                    if (!keepResolvedSession) {
                                        backpacks.remove(playerId);
                                        backpackInstances.remove(playerId);
                                        openRegistry.release(playerId);
                                    }
                                }
                            },
                            ThreadUtils.getEntityThreadExecutor(p));

            // Ownership of the reservation has moved to the asynchronous backpack
            // resolution callback. Do not release it from this outer scope.
            keepSession = true;
        } finally {
            if (!keepSession) {
                openRegistry.release(playerId, reservationKey);
            }
        }
    }

    @Nonnull
    static String formatBackpackName(@Nonnull String name) {
        Matcher matcher = HEX_COLOR_PATTERN.matcher(name);
        StringBuilder formatted = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder expanded = new StringBuilder("&x");
            for (char digit : hex.toCharArray()) {
                expanded.append('&').append(digit);
            }
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(expanded.toString()));
        }

        matcher.appendTail(formatted);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', formatted.toString());
    }

    private String getReservationKey(@Nonnull org.bukkit.inventory.meta.ItemMeta meta) {
        return PlayerBackpack.getBackpackUUID(meta)
                .map(uuid -> "uuid:" + uuid)
                .orElseGet(() -> "legacy:" + PlayerBackpack.getLegacyBackpackIdentity(meta).orElse("unknown:-1"));
    }
}
