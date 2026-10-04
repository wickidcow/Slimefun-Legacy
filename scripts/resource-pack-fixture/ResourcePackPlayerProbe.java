package io.github.wickidcow.validation;

import static io.github.wickidcow.validation.ResourcePackDoctorProbe.require;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** Real socket-connected player checks; does not synthesize Bukkit events. */
final class ResourcePackPlayerProbe implements Listener {
    private static final String NAME = "SFLDoctorBot";
    private final ResourcePackDoctorProbe plugin;
    private PlayerBackpack backpack;
    private Item pickup;
    private boolean opened;
    private boolean pickupObserved;
    private String stage;

    ResourcePackPlayerProbe(ResourcePackDoctorProbe plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!NAME.equals(player.getName())) return;
        try {
            owned(player);
            stage = Files.readString(plugin.getDataFolder().toPath().resolve("player-stage.txt"))
                    .trim();
            player.setInvulnerable(true);
            player.setAllowFlight(true);
            player.setFlying(true);
            if (stage.equals("join")) {
                verifySavedPlayer(player, false);
            } else if (stage.equals("restart")) {
                verifyPickedUp(player);
            }
            Files.writeString(
                    plugin.getDataFolder().toPath().resolve("player-connected.ready"),
                    player.getUniqueId().toString());
            if (!stage.equals("seed")) {
                later(player, 60L, () -> {
                    if (stage.equals("join")) verifySavedPlayer(player, true);
                    else {
                        verifyPickedUp(player);
                        String id = Files.readString(
                                        plugin.getDataFolder().toPath().resolve("player-backpack.uuid"))
                                .trim();
                        require(
                                plugin.expected("clean-0")
                                        .equals(plugin.read(
                                                Slimefun.getDatabaseManager().getProfileDataController(),
                                                DataScope.BACKPACK_INVENTORY,
                                                FieldKey.BACKPACK_ID,
                                                id)),
                                "Opened backpack cleanup did not survive restart");
                    }
                    plugin.pass("player-" + stage);
                });
            }
        } catch (Throwable failure) {
            plugin.fail(failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!NAME.equals(event.getPlayer().getName())) return;
        try {
            owned(event.getPlayer());
            Files.writeString(plugin.getDataFolder().toPath().resolve("player-disconnected.ready"), stage);
        } catch (Throwable failure) {
            plugin.fail(failure);
        }
    }

    boolean command(String action) {
        Player player = Bukkit.getPlayerExact(NAME);
        if (player == null) {
            plugin.fail(new IllegalStateException("Missing connected Doctor player"));
            return true;
        }
        later(player, 1L, () -> {
            switch (action) {
                case "player-seed" -> {
                    require(stage.equals("seed"), "Player seed requested after authorization");
                    player.getInventory().clear();
                    player.getEnderChest().clear();
                    player.getInventory().setItem(0, plugin.expected("old-0"));
                    player.getInventory().setItem(1, plugin.expected("clean-1"));
                    player.getInventory().setItem(2, plugin.expected("clean-5"));
                    player.getEnderChest().setItem(0, plugin.expected("old-0"));
                    later(player, 40L, () -> {
                        verifySavedPlayer(player, false);
                        plugin.pass(action);
                    });
                }
                case "player-open" -> openBackpack(player);
                case "player-pickup" -> pickup(player);
                default -> throw new IllegalArgumentException("Unknown player probe phase: " + action);
            }
        });
        return true;
    }

    private void verifySavedPlayer(Player player, boolean cleaned) throws Exception {
        ItemStack expected = plugin.expected(cleaned ? "clean-0" : "old-0");
        require(expected.equals(player.getInventory().getItem(0)), "Saved player inventory mismatch");
        require(expected.equals(player.getEnderChest().getItem(0)), "Saved ender chest mismatch");
        require(plugin.expected("clean-1").equals(player.getInventory().getItem(1)), "External model changed on join");
        require(
                plugin.expected("clean-5").equals(player.getInventory().getItem(2)),
                "Unknown addon item changed on join");
    }

    private void openBackpack(Player player) throws Exception {
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        backpack = profiles.createBackpack(player, "Connected Doctor fixture", 77, 9);
        backpack.getInventory().setItem(0, plugin.expected("old-0"));
        profiles.saveBackpackInventoryAsync(backpack).get(10, TimeUnit.SECONDS);
        Files.writeString(
                plugin.getDataFolder().toPath().resolve("player-backpack.uuid"),
                backpack.getUniqueId().toString());
        require(player.openInventory(backpack.getInventory()) != null, "Backpack open was cancelled");
        player.getInventory().setItem(0, plugin.expected("old-0"));
        player.setItemOnCursor(plugin.expected("old-0"));
        later(player, 40L, () -> {
            require(opened, "Native inventory-open event was not observed");
            require(
                    player.getOpenInventory().getTopInventory().equals(backpack.getInventory()),
                    "Backpack is not open");
            ItemStack expected = plugin.expected("clean-0");
            require(expected.equals(backpack.getInventory().getItem(0)), "Open backpack was not cleaned");
            require(expected.equals(player.getInventory().getItem(0)), "Inventory-open player stack was not cleaned");
            require(expected.equals(player.getItemOnCursor()), "Inventory-open cursor was not cleaned");
            require(
                    expected.equals(plugin.read(
                            profiles,
                            DataScope.BACKPACK_INVENTORY,
                            FieldKey.BACKPACK_ID,
                            backpack.getUniqueId().toString())),
                    "Open backpack cleanup was not saved");
            player.setItemOnCursor(null);
            player.closeInventory();
            plugin.pass("player-open");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (backpack == null || event.getInventory().getHolder() != backpack) return;
        try {
            owned((Player) event.getPlayer());
            opened = true;
        } catch (Throwable failure) {
            plugin.fail(failure);
        }
    }

    private void pickup(Player player) throws Exception {
        player.getInventory().clear();
        pickup = player.getWorld().dropItem(player.getLocation(), plugin.expected("old-0"));
        pickup.setGravity(false);
        pickup.setVelocity(new org.bukkit.util.Vector());
        pickup.setPickupDelay(0);
        later(player, 60L, () -> {
            require(pickupObserved, "Native player pickup event was not observed");
            require(!pickup.isValid(), "Picked-up entity is still alive");
            verifyPickedUp(player);
            plugin.pass("player-pickup");
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (pickup == null || !event.getItem().getUniqueId().equals(pickup.getUniqueId())) return;
        try {
            require(
                    event.getEntity() instanceof Player player && NAME.equals(player.getName()),
                    "Unexpected pickup owner");
            owned((Player) event.getEntity());
            require(Bukkit.isOwnedByCurrentRegion(pickup), "Pickup event ran outside item owner");
            require(
                    plugin.expected("old-0").equals(pickup.getItemStack()),
                    "Pickup fixture was cleaned before its event");
            pickupObserved = true;
        } catch (Throwable failure) {
            plugin.fail(failure);
        }
    }

    private void verifyPickedUp(Player player) throws Exception {
        var stacks = Arrays.stream(player.getInventory().getStorageContents())
                .filter(item -> item != null && !item.getType().isAir())
                .toList();
        require(
                stacks.size() == 1 && plugin.expected("clean-0").equals(stacks.getFirst()),
                "Pickup lost, duplicated, or changed the stack");
        require(
                plugin.expected("clean-0").equals(player.getEnderChest().getItem(0)),
                "Ender chest cleanup did not persist");
    }

    private void owned(Player player) {
        require(
                player.isOnline() && Bukkit.isOwnedByCurrentRegion(player),
                "Player assertion ran outside its entity owner");
        if (Slimefun.getSchedulerService().isFolia())
            require(!Bukkit.isGlobalTickThread(), "Player probe ran globally on Folia");
    }

    private void later(Player player, long ticks, ProbeAction action) {
        var task = player.getScheduler()
                .runDelayed(
                        plugin,
                        ignored -> {
                            try {
                                owned(player);
                                action.run();
                            } catch (Throwable failure) {
                                plugin.fail(failure);
                            }
                        },
                        () -> plugin.fail(new IllegalStateException("Player retired before its probe completed")),
                        ticks);
        if (task == null) plugin.fail(new IllegalStateException("Could not schedule the connected-player probe"));
    }

    @FunctionalInterface
    private interface ProbeAction {
        void run() throws Exception;
    }
}
