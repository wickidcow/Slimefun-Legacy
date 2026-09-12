package io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.EntityInteractHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.papermc.paper.entity.Leashable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A reusable lead that can leash any Paper {@link Leashable} and chain leashable entities together.
 *
 * <p>Fence attachment is intentionally left to Minecraft's normal lead-on-fence interaction. The item is backed by
 * a vanilla lead, so a chain whose final entity is held by the player can be transferred to a fence knot normally.
 */
public final class UniversalLeash extends SlimefunItem {

    private static final int MAX_CHAIN_LENGTH = 32;

    private final Map<UUID, UUID> chainEndpoints = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> chainLengths = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public UniversalLeash(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler((EntityInteractHandler) this::onEntityInteract);
    }

    private void onEntityInteract(PlayerInteractEntityEvent event, ItemStack item, boolean offHand) {
        if (offHand) {
            return;
        }

        Player player = event.getPlayer();
        Entity target = event.getRightClicked();

        if (!(target instanceof Leashable leashable)) {
            return;
        }

        event.setCancelled(true);

        if (!Slimefun.getIntegrations().canInteractEntity(player, target)) {
            player.sendMessage(ChatColor.RED + "You cannot leash that entity here.");
            return;
        }

        if (player.isSneaking()) {
            chainTo(player, target, leashable);
        } else {
            leashToPlayer(player, target, leashable);
        }
    }

    private void leashToPlayer(Player player, Entity target, Leashable leashable) {
        if (!leashable.setLeashHolder(player)) {
            player.sendMessage(ChatColor.RED + "That entity cannot be leashed right now.");
            return;
        }

        chainEndpoints.put(player.getUniqueId(), target.getUniqueId());
        chainLengths.put(player.getUniqueId(), 1);
        player.sendMessage(ChatColor.GREEN + "Leashed " + displayName(target) + '.');
    }

    private void chainTo(Player player, Entity target, Leashable targetLeashable) {
        UUID playerId = player.getUniqueId();
        Entity endpoint = resolveEndpoint(player);

        if (!(endpoint instanceof Leashable endpointLeashable) || endpoint.equals(target)) {
            leashToPlayer(player, target, targetLeashable);
            return;
        }

        if (!isHeldBy(endpointLeashable, player)) {
            leashToPlayer(player, target, targetLeashable);
            return;
        }

        int currentLength = chainLengths.getOrDefault(playerId, 1);
        if (currentLength >= MAX_CHAIN_LENGTH) {
            player.sendMessage(ChatColor.RED + "This leash chain has reached " + MAX_CHAIN_LENGTH + " mobs.");
            return;
        }

        if (!targetLeashable.setLeashHolder(player)) {
            player.sendMessage(ChatColor.RED + "That entity cannot be added to the leash chain.");
            return;
        }

        if (!endpointLeashable.setLeashHolder(target)) {
            targetLeashable.setLeashHolder(null);
            player.sendMessage(ChatColor.RED + "The previous leash could not be linked to that mob.");
            return;
        }

        chainEndpoints.put(playerId, target.getUniqueId());
        chainLengths.put(playerId, currentLength + 1);
        player.sendMessage(ChatColor.GREEN + "Linked " + displayName(endpoint) + ChatColor.GRAY + " → "
                + ChatColor.GREEN + displayName(target) + '.');
    }

    private Entity resolveEndpoint(Player player) {
        UUID endpointId = chainEndpoints.get(player.getUniqueId());
        if (endpointId == null) {
            return null;
        }

        Entity endpoint = Bukkit.getEntity(endpointId);
        if (endpoint == null
                || !endpoint.isValid()
                || endpoint.getWorld() != player.getWorld()
                || endpoint.getLocation().distanceSquared(player.getLocation()) > 1024.0D) {
            clearEndpoint(player);
            return null;
        }

        return endpoint;
    }

    private static boolean isHeldBy(Leashable leashable, Entity holder) {
        if (!leashable.isLeashed()) {
            return false;
        }

        try {
            return leashable.getLeashHolder().equals(holder);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private void clearEndpoint(Player player) {
        UUID playerId = player.getUniqueId();
        chainEndpoints.remove(playerId);
        chainLengths.remove(playerId);
    }

    private static String displayName(Entity entity) {
        if (entity.getCustomName() != null && !entity.getCustomName().isBlank()) {
            return entity.getCustomName();
        }

        return entity.getType().getKey().getKey().replace('_', ' ');
    }
}
