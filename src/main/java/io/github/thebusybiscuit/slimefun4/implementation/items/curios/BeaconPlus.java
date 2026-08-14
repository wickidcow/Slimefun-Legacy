package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * A Slimefun-side commissioning item for the standalone BeaconPlus3 plugin.
 *
 * <p>Slimefun owns only the recipe and guide entry. On deliberate use this item asks the installed BeaconPlus3
 * plugin to create its own authentic empty beacon item through {@code BeaconAPI#createBeaconEmptyItem(Player)}.
 * BeaconPlus3 remains the sole owner of beacon placement, effects, upgrades, storage, access lists and runtime
 * behavior.
 */
public final class BeaconPlus extends SimpleSlimefunItem<ItemUseHandler> implements NotPlaceable {

    private static final String PLUGIN_NAME = "BeaconPlus3";
    private static final String API_CLASS = "thito.beaconplus.BeaconAPI";
    private static final String API_GETTER = "getAPI";
    private static final String CREATE_EMPTY_ITEM = "createBeaconEmptyItem";

    @ParametersAreNonnullByDefault
    public BeaconPlus(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();

            Player player = event.getPlayer();
            Plugin beaconPlusPlugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (beaconPlusPlugin == null || !beaconPlusPlugin.isEnabled()) {
                player.sendMessage(ChatColor.RED + "BeaconPlus3 is not installed or enabled. The Curio was not consumed.");
                return;
            }

            ItemStack genuineBeacon = createBeaconPlusItem(beaconPlusPlugin, player);
            if (genuineBeacon == null || genuineBeacon.getType().isAir()) {
                player.sendMessage(ChatColor.RED
                        + "BeaconPlus3 could not create a beacon item. The Curio was not consumed.");
                return;
            }

            ItemStack token = event.getItem();
            if (token.getAmount() <= 1) {
                replaceUsedHand(player, event.getHand(), genuineBeacon);
            } else {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(genuineBeacon);
                if (!leftovers.isEmpty()) {
                    player.sendMessage(ChatColor.RED
                            + "Make one free inventory slot before commissioning Beacon Plus. The Curio was not consumed.");
                    return;
                }
                token.setAmount(token.getAmount() - 1);
            }

            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
            player.sendMessage(ChatColor.GOLD + "Beacon Plus commissioned. " + ChatColor.GRAY
                    + "BeaconPlus3 now owns this beacon and all of its configured behavior.");
        };
    }

    private static ItemStack createBeaconPlusItem(Plugin plugin, Player player) {
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, true, classLoader);
            Method getApi = apiClass.getMethod(API_GETTER);
            Object api = getApi.invoke(null);
            Method createBeacon = apiClass.getMethod(CREATE_EMPTY_ITEM, Player.class);
            Object result = createBeacon.invoke(api, player);
            return result instanceof ItemStack item ? item.clone() : null;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            Slimefun.logger().log(Level.WARNING, "BeaconPlus3 API bridge is unavailable; no Curio was consumed.", e);
            return null;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Slimefun.logger().log(Level.WARNING, "BeaconPlus3 failed to create its beacon item; no Curio was consumed.", cause);
            return null;
        } catch (LinkageError e) {
            Slimefun.logger().log(Level.WARNING, "BeaconPlus3 API linkage failed; no Curio was consumed.", e);
            return null;
        }
    }

    private static void replaceUsedHand(Player player, EquipmentSlot hand, ItemStack replacement) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(replacement);
        } else {
            player.getInventory().setItemInMainHand(replacement);
        }
    }
}
