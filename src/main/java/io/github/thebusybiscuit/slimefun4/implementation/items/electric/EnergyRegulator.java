package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.attributes.rotations.NotRotatable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@link EnergyRegulator} is a special type of {@link SlimefunItem} which serves as the heart of every
 * {@link EnergyNet}.
 *
 * @author TheBusyBiscuit
 *
 * @see EnergyNet
 * @see EnergyNetComponent
 *
 */
public class EnergyRegulator extends SlimefunItem implements HologramOwner, NotRotatable {

    private static final Color VISUALIZER_GOLD = Color.fromRGB(255, 191, 0);
    private static final int VISUALIZER_PARTICLE_BUDGET = 160;
    private static final long VISUALIZER_PULSE_INTERVAL = 10L;

    private static final Map<UUID, Location> ACTIVE_VISUALIZERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_VISUALIZER_PULSE = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public EnergyRegulator(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(onBreak(), onUse());
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(@Nonnull Block b) {
                removeHologram(b);
                disableVisualizersAt(b.getLocation());
            }
        };
    }

    @Nonnull
    private BlockUseHandler onUse() {
        return e -> {
            if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock().isEmpty()) {
                return;
            }

            e.cancel();
            openVisualizerMenu(e.getPlayer(), e.getClickedBlock().get());
        };
    }

    @Nonnull
    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                updateHologram(e.getBlock(), "&7Connecting...");
            }
        };
    }

    @Override
    public void preRegister() {
        addItemHandler(onPlace());

        addItemHandler(new BlockTicker() {

            @Override
            public boolean isSynchronized() {
                // Energy-network ticks update the regulator hologram and must run on the
                // owning Paper/Purpur thread (or the owning Folia region thread).
                return true;
            }

            @Override
            public void tick(Block b, SlimefunItem item, SlimefunBlockData data) {
                EnergyRegulator.this.tick(b, data);
            }
        });
    }

    private void tick(@Nonnull Block b, SlimefunBlockData blockData) {
        EnergyNet network = EnergyNet.getNetworkFromLocationOrCreate(b.getLocation());
        network.tick(b, blockData);
        displayActiveVisualizer(b, network);
    }

    private void openVisualizerMenu(@Nonnull Player player, @Nonnull Block regulator) {
        ChestMenu menu = new ChestMenu(ChatColor.DARK_GRAY + "Energy Regulator");
        menu.setEmptySlotsClickable(false);

        boolean globallyEnabled = Slimefun.getNetworkManager().isVisualizerEnabled();
        boolean active = isVisualizerActive(player.getUniqueId(), regulator.getLocation());
        ItemStack toggle = createVisualizerToggleItem(active, globallyEnabled);

        menu.addItem(4, toggle);
        menu.addMenuClickHandler(4, (clickedPlayer, slot, item, action) -> {
            if (!Slimefun.getNetworkManager().isVisualizerEnabled()) {
                clickedPlayer.sendMessage(ChatColor.RED + "Network visualizers are disabled in the server configuration.");
                return false;
            }

            boolean enabled = toggleVisualizer(clickedPlayer.getUniqueId(), regulator.getLocation());
            clickedPlayer.sendMessage(
                    ChatColor.GOLD + "Energy network visualizer " + (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") + ChatColor.GOLD + ".");
            openVisualizerMenu(clickedPlayer, regulator);
            return false;
        });

        menu.open(player);
    }

    @Nonnull
    private ItemStack createVisualizerToggleItem(boolean active, boolean globallyEnabled) {
        ItemStack item = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Energy Network Visualizer");
        if (!globallyEnabled) {
            meta.setLore(List.of(
                    "",
                    ChatColor.GRAY + "Shows this Energy Network with clean gold dust particles.",
                    ChatColor.GRAY + "Only you can see the visualizer.",
                    "",
                    ChatColor.RED + "Disabled by server configuration"));
        } else {
            meta.setLore(List.of(
                    "",
                    ChatColor.GRAY + "Shows this Energy Network with clean gold dust particles.",
                    ChatColor.GRAY + "Only you can see the visualizer.",
                    ChatColor.GRAY + "Rendering is range-limited and particle-capped.",
                    "",
                    ChatColor.GRAY + "Status: " + (active ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                    ChatColor.YELLOW + "Click to toggle"));
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean toggleVisualizer(@Nonnull UUID playerId, @Nonnull Location regulator) {
        Location current = ACTIVE_VISUALIZERS.get(playerId);
        if (current != null && current.equals(regulator)) {
            ACTIVE_VISUALIZERS.remove(playerId, current);
            LAST_VISUALIZER_PULSE.remove(playerId);
            return false;
        }

        ACTIVE_VISUALIZERS.put(playerId, regulator.clone());
        LAST_VISUALIZER_PULSE.remove(playerId);
        return true;
    }

    private boolean isVisualizerActive(@Nonnull UUID playerId, @Nonnull Location regulator) {
        Location current = ACTIVE_VISUALIZERS.get(playerId);
        return current != null && current.equals(regulator);
    }

    private void displayActiveVisualizer(@Nonnull Block regulatorBlock, @Nonnull EnergyNet network) {
        if (!Slimefun.getNetworkManager().isVisualizerEnabled() || ACTIVE_VISUALIZERS.isEmpty()) {
            return;
        }

        Location regulator = regulatorBlock.getLocation();
        long gameTime = regulatorBlock.getWorld().getGameTime();

        for (Map.Entry<UUID, Location> entry : ACTIVE_VISUALIZERS.entrySet()) {
            UUID playerId = entry.getKey();
            Location selectedRegulator = entry.getValue();
            if (!selectedRegulator.equals(regulator)) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                ACTIVE_VISUALIZERS.remove(playerId, selectedRegulator);
                LAST_VISUALIZER_PULSE.remove(playerId);
                continue;
            }

            long lastPulse = LAST_VISUALIZER_PULSE.getOrDefault(playerId, Long.MIN_VALUE);
            if (lastPulse != Long.MIN_VALUE && gameTime - lastPulse < VISUALIZER_PULSE_INTERVAL) {
                continue;
            }

            LAST_VISUALIZER_PULSE.put(playerId, gameTime);
            network.display(player, VISUALIZER_GOLD, VISUALIZER_PARTICLE_BUDGET);
        }
    }

    private void disableVisualizersAt(@Nonnull Location regulator) {
        for (Map.Entry<UUID, Location> entry : ACTIVE_VISUALIZERS.entrySet()) {
            if (entry.getValue().equals(regulator)) {
                UUID playerId = entry.getKey();
                ACTIVE_VISUALIZERS.remove(playerId, entry.getValue());
                LAST_VISUALIZER_PULSE.remove(playerId);
            }
        }
    }
}
