package io.github.wickidcow.validation;

import static io.github.wickidcow.validation.ResourcePackDoctorProbe.require;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
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
    private boolean cancelOpen;
    private boolean cancelledOpenObserved;
    private boolean cancelPickup;
    private volatile boolean disconnectedWorkSettled;
    private String stage;
    private boolean craftProbeActive;
    private boolean cancelCraft;
    private int craftEvents;
    private ItemStack craftedForStorage;
    private ItemStack givenForStorage;

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
            if (!stage.equals("seed")) verifyStoredOutputs(player);
            if (stage.equals("join") || stage.equals("retire")) {
                verifySavedPlayer(player, false);
            } else if (stage.equals("restart")) {
                verifyPickedUp(player);
            }
            Files.writeString(
                    plugin.getDataFolder().toPath().resolve("player-connected.ready"),
                    player.getUniqueId().toString());
            if (stage.equals("retire")) {
                disconnectBeforeCleanup(player);
                return;
            }
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
                        verifyBackpackOutputs(player, id, "restart");
                        verifyLoadedBackpack(player, id);
                    }
                    verifyStoredOutputs(player);
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
            if (stage.equals("retire")) {
                verifySavedPlayer(event.getPlayer(), false);
                var uuid = event.getPlayer().getUniqueId();
                Bukkit.getGlobalRegionScheduler()
                        .runDelayed(
                                plugin,
                                ignored -> {
                                    try {
                                        require(Bukkit.getPlayer(uuid) == null, "Retirement player is still connected");
                                        require(
                                                disconnectedWorkSettled,
                                                "Delayed player work did not settle after disconnect");
                                        plugin.pass("player-retire");
                                    } catch (Throwable failure) {
                                        plugin.fail(failure);
                                    }
                                },
                                40L);
            }
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
                case "player-identity-give" -> verifyGiveOutput(player);
                case "player-identity-craft" -> verifyCraftOutput(player);
                case "player-identity-compressor" -> verifyCompressorOutput(player);
                case "player-seed" -> {
                    require(stage.equals("seed"), "Player seed requested after authorization");
                    require(craftedForStorage != null && givenForStorage != null,
                            "Missing actual producer outputs for ender storage");
                    player.getInventory().clear();
                    player.getEnderChest().clear();
                    player.getEnderChest().setItem(10, craftedForStorage);
                    player.getEnderChest().setItem(11, givenForStorage);
                    player.getInventory().setItem(0, plugin.expected("old-0"));
                    player.getInventory().setItem(1, plugin.expected("clean-1"));
                    player.getInventory().setItem(2, plugin.expected("clean-5"));
                    player.getEnderChest().setItem(0, plugin.expected("old-0"));
                    later(player, 40L, () -> {
                        verifySavedPlayer(player, false);
                        verifyStoredOutputs(player);
                        plugin.pass(action);
                    });
                }
                case "player-cancel-open" -> cancelledOpen(player);
                case "player-open" -> openBackpack(player);
                case "player-cancel-pickup" -> cancelledPickup(player);
                case "player-pickup" -> pickup(player);
                default -> throw new IllegalArgumentException("Unknown player probe phase: " + action);
            }
        });
        return true;
    }

    private void verifyGiveOutput(Player player) throws Exception {
        require(stage.equals("seed"), "Give probe requested outside disposable seed phase");
        var permission = player.addAttachment(plugin, "slimefun.cheat.items", true);
        try {
            for (String id : java.util.List.of("SMALL_URANIUM", "COPPER_DUST", "CARBONADO")) {
                owned(player);
                SlimefunItem registered = SlimefunItem.getById(id);
                require(registered != null, "Missing give fixture: " + id);
                ItemStack template = registered.getItem();
                byte[] before = template.serializeAsBytes();
                player.getInventory().clear();
                require(player.performCommand("sf give " + NAME + " " + id + " 17"), "Give command rejected");
                verifyGivenStack(player, template, 17);
                require(player.performCommand("sf give " + NAME + " " + id + " 31"), "Second give rejected");
                verifyGivenStack(player, template, 48);
                require(Arrays.equals(before, template.serializeAsBytes()), "Give mutated template: " + id);
                plugin.getLogger().info("IDENTITY_ACTUAL_GIVE_PASS " + id
                        + " amounts=17+31 merged=48 runtime=" + Bukkit.getVersion());
            }
        } finally {
            player.removeAttachment(permission);
            player.getInventory().clear();
        }
        // This proves only the give command for these three items on connected-player runtimes.
        // It does not promote cheat-menu, recipe, machine, cargo or restart coverage.
        plugin.pass("player-identity-give");
    }

    private void verifyCompressorOutput(Player player) throws Exception {
        require(stage.equals("seed"), "Compressor probe outside disposable seed phase");
        owned(player);
        var carbon = SlimefunItem.getById("CARBON");
        var machine = SlimefunItem.getById("COMPRESSOR");
        require(carbon != null && machine instanceof io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.Compressor,
                "Missing compressor fixture registrations");
        byte[] templateBefore = carbon.getItem().serializeAsBytes();
        var dispenser = player.getLocation().getBlock().getRelative(0, 3, 0);
        var fence = dispenser.getRelative(0, 1, 0);
        require(Bukkit.isOwnedByCurrentRegion(dispenser) && Bukkit.isOwnedByCurrentRegion(fence),
                "Compressor fixture outside player region");
        require(dispenser.getType().isAir() && fence.getType().isAir(), "Compressor fixture space occupied");
        dispenser.setType(org.bukkit.Material.DISPENSER);
        fence.setType(org.bukkit.Material.NETHER_BRICK_FENCE);
        var inventory = ((org.bukkit.block.Dispenser) dispenser.getState(false)).getInventory();
        try {
            inventory.setItem(0, new ItemStack(org.bukkit.Material.COAL_BLOCK, 8));
            player.getInventory().clear();
            // Real production handler and delayed output service; not multiblock discovery/network interaction.
            ((io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.Compressor) machine)
                    .onInteract(player, fence);
            require(inventory.isEmpty(), "Compressor did not consume exactly eight coal blocks");
            later(player, 100L, () -> {
                var permission = player.addAttachment(plugin, "slimefun.cheat.items", true);
                try {
                    require(Bukkit.isOwnedByCurrentRegion(dispenser), "Compressor completion outside player region");
                    var outputs = Arrays.stream(inventory.getContents())
                            .filter(item -> item != null && !item.getType().isAir()).toList();
                    require(outputs.size() == 1, "Compressor left unexpected outputs");
                    ItemStack actual = outputs.getFirst();
                    require(actual.getAmount() == 9 && actual.isSimilar(carbon.getItem())
                                    && carbon.getItem().isSimilar(actual), "Compressor output identity/quantity");
                    require(player.getInventory().isEmpty(), "Compressor leaked output to player");
                    require(player.performCommand("sf give " + NAME + " CARBON 9"), "Carbon reference give rejected");
                    verifyGivenStack(player, carbon.getItem(), 9);
                    var given = player.getInventory().getItem(0);
                    require(actual.isSimilar(given) && given.isSimilar(actual)
                                    && actual.getItemMeta().equals(given.getItemMeta()), "Compressor/give metadata mismatch");
                    inventory.clear();
                    require(player.getInventory().addItem(actual).isEmpty(), "Compressor/give merge overflow");
                    verifyGivenStack(player, carbon.getItem(), 18);
                    require(Arrays.equals(templateBefore, carbon.getItem().serializeAsBytes()),
                            "Compressor mutated registered Carbon template");
                    plugin.pass("player-identity-compressor");
                } finally {
                    player.removeAttachment(permission);
                    player.getInventory().clear();
                    dispenser.setType(org.bukkit.Material.AIR);
                    fence.setType(org.bukkit.Material.AIR);
                }
            });
        } catch (Throwable failure) {
            dispenser.setType(org.bukkit.Material.AIR);
            fence.setType(org.bukkit.Material.AIR);
            throw failure;
        }
    }

    private void verifyCraftOutput(Player player) throws Exception {
        require(stage.equals("seed"), "Craft probe outside disposable seed phase");
        owned(player);
        var wire = SlimefunItem.getById("COPPER_WIRE");
        var copper = SlimefunItem.getById("COPPER_INGOT");
        var machine = SlimefunItem.getById("ENHANCED_CRAFTING_TABLE");
        require(wire != null && copper != null
                        && machine instanceof io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.EnhancedCraftingTable,
                "Missing crafting fixture registrations");
        var profile = io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile.find(player).orElseThrow();
        var research = wire.getResearch();
        boolean researched = research == null || profile.hasUnlocked(research);
        byte[] wireBefore = wire.getItem().serializeAsBytes();
        byte[] copperBefore = copper.getItem().serializeAsBytes();
        var dispenserBlock = player.getLocation().getBlock().getRelative(0, 3, 0);
        var tableBlock = dispenserBlock.getRelative(0, 1, 0);
        require(Bukkit.isOwnedByCurrentRegion(dispenserBlock) && Bukkit.isOwnedByCurrentRegion(tableBlock),
                "Craft fixture outside player region");
        require(dispenserBlock.getType().isAir() && tableBlock.getType().isAir(), "Craft fixture space occupied");
        var permission = player.addAttachment(plugin, "slimefun.cheat.items", true);
        try {
            if (!researched) profile.setResearched(research, true);
            dispenserBlock.setType(org.bukkit.Material.DISPENSER);
            tableBlock.setType(org.bukkit.Material.CRAFTING_TABLE);
            var inventory = ((org.bukkit.block.Dispenser) dispenserBlock.getState(false)).getInventory();
            player.getInventory().clear();
            require(player.performCommand("sf give " + NAME + " COPPER_INGOT 3"), "Ingredient give rejected");
            verifyGivenStack(player, copper.getItem(), 3);
            ItemStack ingredient = player.getInventory().getItem(0);
            require(ingredient != null, "Missing actual given ingredients");
            for (int slot = 3; slot <= 5; slot++) {
                ItemStack one = ingredient.clone();
                one.setAmount(1);
                inventory.setItem(slot, one);
            }
            player.getInventory().clear();
            // Invoke the real production handler, including matching, event, consumption and output service.
            // This does not simulate or claim network right-click/multiblock discovery coverage.
            var table = (io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.EnhancedCraftingTable) machine;
            craftProbeActive = true;
            craftEvents = 0;
            ItemStack middle = inventory.getItem(4);
            inventory.setItem(4, null);
            byte[][] incomplete = inventoryBytes(inventory);
            table.onInteract(player, tableBlock);
            require(craftEvents == 0, "Incomplete recipe emitted a craft event");
            require(Arrays.deepEquals(incomplete, inventoryBytes(inventory)),
                    "Incomplete recipe consumed or produced items");
            inventory.setItem(4, middle);
            byte[][] complete = inventoryBytes(inventory);
            cancelCraft = true;
            table.onInteract(player, tableBlock);
            require(craftEvents == 1, "Cancelled craft did not reach the native event");
            require(Arrays.deepEquals(complete, inventoryBytes(inventory)),
                    "Cancelled craft consumed or produced items");
            require(player.getInventory().isEmpty(), "Rejected craft leaked output to player");
            cancelCraft = false;
            table.onInteract(player, tableBlock);
            require(craftEvents == 2, "Successful craft did not reach the native event");
            var outputs = Arrays.stream(inventory.getContents())
                    .filter(item -> item != null && !item.getType().isAir()).toList();
            require(outputs.size() == 1, "Craft left ingredients or unexpected outputs");
            ItemStack crafted = outputs.getFirst();
            require(crafted.getAmount() == 8 && wire.getItem().isSimilar(crafted)
                            && crafted.isSimilar(wire.getItem()), "Craft output identity/quantity mismatch");
            require(player.performCommand("sf give " + NAME + " COPPER_WIRE 8"), "Reference give rejected");
            verifyGivenStack(player, wire.getItem(), 8);
            var folder = plugin.getDataFolder().toPath();
            Files.write(folder.resolve("identity-crafted-wire.bin"), crafted.serializeAsBytes());
            Files.write(folder.resolve("identity-given-wire.bin"),
                    player.getInventory().getItem(0).serializeAsBytes());
            craftedForStorage = crafted.clone();
            givenForStorage = player.getInventory().getItem(0).clone();
            inventory.clear();
            require(player.getInventory().addItem(crafted).isEmpty(), "Craft/give merge overflow");
            verifyGivenStack(player, wire.getItem(), 16);
            require(Arrays.equals(wireBefore, wire.getItem().serializeAsBytes())
                            && Arrays.equals(copperBefore, copper.getItem().serializeAsBytes()),
                    "Craft mutated registered template");
        } finally {
            craftProbeActive = false;
            cancelCraft = false;
            dispenserBlock.setType(org.bukkit.Material.AIR);
            tableBlock.setType(org.bukkit.Material.AIR);
            player.getInventory().clear();
            player.removeAttachment(permission);
            if (!researched) profile.setResearched(research, false);
        }
        plugin.pass("player-identity-craft");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIdentityCraft(io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent event) {
        if (!craftProbeActive || !NAME.equals(event.getPlayer().getName())) return;
        try {
            owned(event.getPlayer());
            require("ENHANCED_CRAFTING_TABLE".equals(event.getMachine().getId()), "Unexpected craft machine");
            require(SlimefunItem.getById("COPPER_WIRE").getItem().isSimilar(event.getOutput())
                            && event.getOutput().getAmount() == 8, "Unexpected craft event output");
            require(!event.isCancelled(), "Craft was cancelled before probe control");
            craftEvents++;
            if (cancelCraft) event.setCancelled(true);
        } catch (Throwable failure) {
            event.setCancelled(true);
            plugin.fail(failure);
        }
    }

    private void verifyStoredOutputs(Player player) throws Exception {
        owned(player);
        var folder = plugin.getDataFolder().toPath();
        ItemStack crafted = player.getEnderChest().getItem(10);
        ItemStack given = player.getEnderChest().getItem(11);
        require(crafted != null && given != null, "Actual producer outputs missing from saved ender chest");
        require(Arrays.equals(crafted.serializeAsBytes(), Files.readAllBytes(folder.resolve("identity-crafted-wire.bin")))
                        && Arrays.equals(given.serializeAsBytes(), Files.readAllBytes(folder.resolve("identity-given-wire.bin"))),
                "Saved ender outputs changed identity or quantity");
        byte[][] before = inventoryBytes(player.getEnderChest());
        var merge = Bukkit.createInventory(null, 9);
        require(crafted.getAmount() == 8 && given.getAmount() == 8
                        && crafted.isSimilar(given) && given.isSimilar(crafted), "Stored output identity/quantity");
        require(merge.addItem(crafted.clone(), given.clone()).isEmpty(), "Stored output merge overflow");
        var stacks = Arrays.stream(merge.getContents()).filter(item -> item != null && !item.getType().isAir()).toList();
        require(stacks.size() == 1 && stacks.getFirst().getAmount() == 16, "Stored outputs lost or duplicated on merge");
        require(Arrays.deepEquals(before, inventoryBytes(player.getEnderChest())), "Storage inspection mutated ender chest");
        plugin.pass("player-identity-ender-" + stage);
    }

    private byte[][] inventoryBytes(org.bukkit.inventory.Inventory inventory) {
        return Arrays.stream(inventory.getContents())
                .map(item -> item == null ? null : item.serializeAsBytes())
                .toArray(byte[][]::new);
    }

    private void verifyGivenStack(Player player, ItemStack template, int amount) {
        var stacks = Arrays.stream(player.getInventory().getStorageContents())
                .filter(item -> item != null && !item.getType().isAir())
                .toList();
        require(stacks.size() == 1, "Give output did not form exactly one stack");
        ItemStack actual = stacks.getFirst();
        require(actual.getAmount() == amount, "Give output lost or duplicated items");
        require(template.isSimilar(actual) && actual.isSimilar(template), "Give output differs from template");
        require(template.getItemMeta().equals(actual.getItemMeta()), "Give metadata differs from template");
    }

    private void disconnectBeforeCleanup(Player player) {
        // Exercise the same delayed entity scheduling contract as the join listener.
        // Folia retires this task; ordinary Paper executes it with the player offline.
        var task = Slimefun.getSchedulerService()
                .runForLater(
                        player,
                        () -> {
                            try {
                                require(
                                        !Slimefun.getSchedulerService().isFolia(),
                                        "Retired Folia player task executed");
                                require(!player.isOnline(), "Disconnect did not beat the cleanup delay");
                                disconnectedWorkSettled = true;
                            } catch (Throwable failure) {
                                plugin.fail(failure);
                            }
                        },
                        () -> disconnectedWorkSettled = true,
                        20L);
        require(!task.isCancelled(), "Could not queue the retirement probe");
        later(player, 2L, () -> {
            verifySavedPlayer(player, false);
            player.kick(Component.text("Doctor retirement probe"));
        });
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

    private void prepareBackpack(Player player) throws Exception {
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        backpack = profiles.createBackpack(player, "Connected Doctor fixture", 77, 9);
        backpack.getInventory().setItem(0, plugin.expected("old-0"));
        verifyStoredOutputs(player);
        backpack.getInventory().setItem(1, player.getEnderChest().getItem(10).clone());
        backpack.getInventory().setItem(2, player.getEnderChest().getItem(11).clone());
        profiles.saveBackpackInventoryAsync(backpack).get(10, TimeUnit.SECONDS);
        verifyBackpackOutputs(player, backpack.getUniqueId().toString(), "saved");
        Files.writeString(
                plugin.getDataFolder().toPath().resolve("player-backpack.uuid"),
                backpack.getUniqueId().toString());
    }

    private void verifyBackpackOutputs(Player player, String id, String phase) throws Exception {
        owned(player);
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        var folder = plugin.getDataFolder().toPath();
        ItemStack[] actual = new ItemStack[2];
        String[] captures = {"identity-crafted-wire.bin", "identity-given-wire.bin"};
        byte[][] rowsBefore = new byte[2][];
        for (int index = 0; index < 2; index++) {
            var row = plugin.readSlot(profiles, DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, id, index + 1);
            Object raw = row.getValue(FieldKey.INVENTORY_ITEM);
            require(raw instanceof byte[], "Expected native backpack producer payload");
            rowsBefore[index] = ((byte[]) raw).clone();
            actual[index] = row.getItemStack(FieldKey.INVENTORY_ITEM);
            require(actual[index] != null && actual[index].getAmount() == 8
                            && Arrays.equals(actual[index].serializeAsBytes(), Files.readAllBytes(folder.resolve(captures[index]))),
                    "Backpack producer row identity/quantity changed");
        }
        require(actual[0].isSimilar(actual[1]) && actual[1].isSimilar(actual[0]), "Backpack cross-source identity");
        var merge = Bukkit.createInventory(null, 9);
        require(merge.addItem(actual).isEmpty(), "Backpack producer merge overflow");
        var stacks = Arrays.stream(merge.getContents()).filter(item -> item != null && !item.getType().isAir()).toList();
        require(stacks.size() == 1 && stacks.getFirst().getAmount() == 16, "Backpack producer merge lost or duplicated items");
        for (int index = 0; index < 2; index++) {
            Object after = plugin.readSlot(profiles, DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, id, index + 1)
                    .getValue(FieldKey.INVENTORY_ITEM);
            require(after instanceof byte[] && Arrays.equals(rowsBefore[index], (byte[]) after),
                    "Backpack verification rewrote producer row");
        }
        plugin.pass("player-identity-backpack-" + phase);
    }

    private void verifyLoadedBackpack(Player player, String id) throws Exception {
        owned(player);
        require(backpack == null, "Restart probe retained a backpack from an earlier process");
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        var loaded = profiles.getBackpack(id);
        require(loaded != null && loaded.getUniqueId().toString().equals(id), "Backpack UUID reload mismatch");
        require(loaded.getOwner().getUniqueId().equals(player.getUniqueId()) && loaded.getId() == 77,
                "Backpack owner/number reload mismatch");
        require(loaded.getSize() == 9 && loaded.getName().equals("Connected Doctor fixture"),
                "Backpack size/name reload mismatch");
        var inventory = loaded.getInventory();
        require(inventory.getSize() == 9 && inventory.getHolder() == loaded, "Loaded backpack inventory holder/size");
        require(plugin.expected("clean-0").equals(inventory.getItem(0)), "Loaded backpack control slot changed");
        for (int slot = 3; slot < inventory.getSize(); slot++) {
            require(inventory.getItem(slot) == null, "Unexpected item in loaded backpack");
        }
        byte[][] before = inventoryBytes(inventory);
        var folder = plugin.getDataFolder().toPath();
        var crafted = inventory.getItem(1);
        var given = inventory.getItem(2);
        require(crafted != null && given != null && crafted.getAmount() == 8 && given.getAmount() == 8,
                "Loaded backpack output quantities");
        require(Arrays.equals(crafted.serializeAsBytes(), Files.readAllBytes(folder.resolve("identity-crafted-wire.bin")))
                        && Arrays.equals(given.serializeAsBytes(), Files.readAllBytes(folder.resolve("identity-given-wire.bin"))),
                "Loaded backpack output identity changed");
        require(crafted.isSimilar(given) && given.isSimilar(crafted), "Loaded backpack cross-source similarity");
        var merge = Bukkit.createInventory(null, 9);
        require(merge.addItem(crafted.clone(), given.clone()).isEmpty(), "Loaded backpack merge overflow");
        var stacks = Arrays.stream(merge.getContents()).filter(item -> item != null && !item.getType().isAir()).toList();
        require(stacks.size() == 1 && stacks.getFirst().getAmount() == 16, "Loaded backpack merge conservation");
        require(Arrays.deepEquals(before, inventoryBytes(inventory)), "Inspection mutated loaded backpack");
        verifyBackpackOutputs(player, id, "loaded");
    }

    private byte[] backpackBytes() throws Exception {
        Object value = plugin.readSlot(
                        Slimefun.getDatabaseManager().getProfileDataController(),
                        DataScope.BACKPACK_INVENTORY,
                        FieldKey.BACKPACK_ID,
                        backpack.getUniqueId().toString(),
                        0)
                .getValue(FieldKey.INVENTORY_ITEM);
        require(value instanceof byte[], "Expected the native backpack payload");
        return ((byte[]) value).clone();
    }

    private void cancelledOpen(Player player) throws Exception {
        prepareBackpack(player);
        byte[] original = backpackBytes();
        cancelOpen = true;
        require(player.openInventory(backpack.getInventory()) == null, "Cancelled backpack unexpectedly opened");
        player.getInventory().setItem(0, plugin.expected("old-0"));
        player.setItemOnCursor(plugin.expected("old-0"));
        later(player, 40L, () -> {
            require(cancelledOpenObserved && !opened, "Backpack open did not remain cancelled");
            require(
                    !player.getOpenInventory().getTopInventory().equals(backpack.getInventory()),
                    "Cancelled menu is visible");
            ItemStack expected = plugin.expected("old-0");
            require(expected.equals(backpack.getInventory().getItem(0)), "Cancelled open changed the backpack");
            require(expected.equals(player.getInventory().getItem(0)), "Cancelled open changed the player inventory");
            require(expected.equals(player.getItemOnCursor()), "Cancelled open changed the cursor");
            require(Arrays.equals(original, backpackBytes()), "Cancelled open rewrote the backpack row");
            player.setItemOnCursor(null);
            cancelOpen = false;
            plugin.pass("player-cancel-open");
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void cancelOpen(InventoryOpenEvent event) {
        if (cancelOpen && backpack != null && event.getInventory().getHolder() == backpack) {
            event.setCancelled(true);
            cancelledOpenObserved = true;
        }
    }

    private void openBackpack(Player player) throws Exception {
        require(backpack != null && cancelledOpenObserved, "Missing cancelled backpack fixture");
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
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
            verifyBackpackOutputs(player, backpack.getUniqueId().toString(), "opened");
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

    private void cancelledPickup(Player player) throws Exception {
        player.getInventory().clear();
        pickup = player.getWorld().dropItem(player.getLocation(), plugin.expected("old-0"));
        pickup.setGravity(false);
        pickup.setVelocity(new org.bukkit.util.Vector());
        cancelPickup = true;
        pickup.setPickupDelay(0);
        later(player, 40L, () -> {
            require(pickupObserved, "Cancelled native pickup was not observed");
            require(pickup.isValid() && Bukkit.isOwnedByCurrentRegion(pickup), "Cancelled pickup lost its item entity");
            require(
                    plugin.expected("old-0").equals(pickup.getItemStack()),
                    "Cancelled pickup changed the dropped stack");
            require(
                    Arrays.stream(player.getInventory().getStorageContents())
                            .allMatch(item -> item == null || item.getType().isAir()),
                    "Cancelled pickup moved an item into the inventory");
            // Keep the same entity out of reach until the positive-control phase.
            pickup.setPickupDelay(32767);
            plugin.pass("player-cancel-pickup");
        });
    }

    private void pickup(Player player) throws Exception {
        require(pickup != null && pickup.isValid() && cancelPickup, "Missing cancelled pickup fixture");
        cancelPickup = false;
        pickupObserved = false;
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
            if (cancelPickup) event.setCancelled(true);
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
