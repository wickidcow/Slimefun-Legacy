package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BiomeSearchResult;

/**
 * Opens a biome menu and performs a bounded Paper biome lookup from a randomized probe origin.
 */
@SuppressWarnings("deprecation")
public final class WayfarersLodestone extends SimpleSlimefunItem<ItemUseHandler> {

    static final int SEARCH_RADIUS_BLOCKS = 4_096;
    static final int RANDOM_PROBE_RADIUS_BLOCKS = 10_000;
    static final int MAX_SEARCH_PROBES = 3;
    static final long COOLDOWN_MILLIS = 180_000L;
    private static final int HORIZONTAL_SEARCH_INTERVAL = 64;
    private static final int VERTICAL_SEARCH_INTERVAL = 64;
    private static final int[] TARGET_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final Map<UUID, SearchRequest> ACTIVE_SEARCHES = new ConcurrentHashMap<>();

    private final NamespacedKey cooldownKey = new NamespacedKey(Slimefun.instance(), "wayfarers_lodestone_cooldown_until");

    @ParametersAreNonnullByDefault
    public WayfarersLodestone(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();

            if (player.isSneaking()) {
                SearchRequest removed = ACTIVE_SEARCHES.remove(playerId);
                if (removed != null) {
                    player.sendMessage(ChatColor.YELLOW + "Wayfarer's Lodestone search cancelled.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.8F);
                    return;
                }
            }

            if (ACTIVE_SEARCHES.containsKey(playerId)) {
                player.sendMessage(ChatColor.GRAY + "A biome search is already running. Sneak-right-click to cancel it.");
                return;
            }

            long remaining = getCooldownRemainingMillis(player);
            if (remaining > 0L) {
                player.sendMessage(ChatColor.RED + "Wayfarer's Lodestone is recovering for "
                        + formatSeconds(remaining) + " more seconds.");
                return;
            }

            openMenu(player);
        };
    }

    private void openMenu(Player player) {
        ChestMenu menu = new ChestMenu("&6&lWayfarer's Lodestone", 54);
        menu.setPlayerInventoryClickable(false);
        menu.setEmptySlotsClickable(false);

        World world = player.getWorld();
        List<BiomeTarget> targets = targetsFor(world.getEnvironment());
        menu.addItem(
                4,
                createMenuItem(
                        Material.LODESTONE,
                        ChatColor.GOLD + "Random Biome Travel",
                        List.of(
                                ChatColor.GRAY + "World: " + ChatColor.WHITE + world.getName(),
                                ChatColor.GRAY + "Dimension: " + ChatColor.AQUA + world.getEnvironment().name(),
                                "",
                                ChatColor.GRAY + "Choose a biome below.",
                                ChatColor.GRAY + "The search begins from a randomized probe",
                                ChatColor.GRAY + "and teleports only to verified safe ground.",
                                "",
                                ChatColor.DARK_GRAY + "Cooldown after success: 3 minutes")));
        menu.addMenuClickHandler(4, (pl, slot, item, action) -> false);

        int slotIndex = 0;
        for (BiomeTarget target : targets) {
            if (slotIndex >= TARGET_SLOTS.length) {
                break;
            }
            int slot = TARGET_SLOTS[slotIndex++];
            menu.addItem(
                    slot,
                    createMenuItem(
                            target.icon(),
                            ChatColor.AQUA + target.displayName(),
                            List.of(
                                    ChatColor.GRAY + "Searches for a random nearby instance",
                                    ChatColor.GRAY + "of this biome in the current dimension.",
                                    "",
                                    ChatColor.YELLOW + "Click to begin search")));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, item, action) -> {
                pl.closeInventory();
                startSearch(pl, target);
                return false;
            });
        }

        if (targets.isEmpty()) {
            menu.addItem(
                    22,
                    createMenuItem(
                            Material.BARRIER,
                            ChatColor.RED + "Unsupported Dimension",
                            List.of(ChatColor.GRAY + "No biome destinations are configured for this world environment.")));
            menu.addMenuClickHandler(22, (pl, slot, item, action) -> false);
        }

        menu.addItem(
                53,
                createMenuItem(
                        Material.RED_STAINED_GLASS_PANE,
                        ChatColor.RED + "Close",
                        List.of(ChatColor.GRAY + "Close biome selection.")));
        menu.addMenuClickHandler(53, (pl, slot, item, action) -> {
            pl.closeInventory();
            return false;
        });
        menu.open(player);
    }

    private void startSearch(Player player, BiomeTarget target) {
        UUID playerId = player.getUniqueId();
        if (ACTIVE_SEARCHES.containsKey(playerId)) {
            return;
        }

        SearchRequest request = new SearchRequest(
                player.getWorld().getUID(),
                player.getLocation().clone(),
                target,
                player.getLocation().getYaw(),
                player.getLocation().getPitch());
        ACTIVE_SEARCHES.put(playerId, request);

        player.playSound(player.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 0.8F, 1.2F);
        player.sendMessage(ChatColor.GOLD + "Wayfarer's Lodestone: " + ChatColor.GRAY + "searching for "
                + ChatColor.AQUA + target.displayName() + ChatColor.GRAY + "..."
                + ChatColor.DARK_GRAY + " Sneak-right-click to cancel.");
        scheduleProbe(player, request, 1L);
    }

    private void scheduleProbe(Player player, SearchRequest request, long delayTicks) {
        Slimefun.getSchedulerService()
                .runForLater(
                        player,
                        () -> runProbe(player, request),
                        () -> ACTIVE_SEARCHES.remove(player.getUniqueId(), request),
                        delayTicks);
    }

    private void runProbe(Player player, SearchRequest request) {
        UUID playerId = player.getUniqueId();
        if (ACTIVE_SEARCHES.get(playerId) != request) {
            return;
        }
        if (!player.getWorld().getUID().equals(request.worldId())) {
            ACTIVE_SEARCHES.remove(playerId, request);
            player.sendMessage(ChatColor.RED + "Biome search cancelled because you changed worlds.");
            return;
        }
        if (request.probes() >= MAX_SEARCH_PROBES) {
            failSearch(player, request, "No safe matching biome was found within the bounded search.");
            return;
        }

        request.incrementProbes();
        World world = player.getWorld();
        Location probe = randomProbe(world, request.origin());
        Slimefun.getSchedulerService().runAt(probe, () -> locateBiome(player, request, probe));
    }

    private void locateBiome(Player player, SearchRequest request, Location probe) {
        if (ACTIVE_SEARCHES.get(player.getUniqueId()) != request) {
            return;
        }

        World world = Bukkit.getWorld(request.worldId());
        if (world == null) {
            finishUnavailable(player, request, "The search world is no longer available.");
            return;
        }

        BiomeSearchResult result;
        try {
            result = world.locateNearestBiome(
                    probe,
                    SEARCH_RADIUS_BLOCKS,
                    HORIZONTAL_SEARCH_INTERVAL,
                    VERTICAL_SEARCH_INTERVAL,
                    request.target().biome());
        } catch (RuntimeException exception) {
            scheduleProbe(player, request, 10L);
            return;
        }

        if (result == null) {
            scheduleProbe(player, request, 10L);
            return;
        }

        Location located = result.getLocation();
        Slimefun.getSchedulerService().runAt(located, () -> prepareSafeDestination(player, request, located));
    }

    private void prepareSafeDestination(Player player, SearchRequest request, Location located) {
        if (ACTIVE_SEARCHES.get(player.getUniqueId()) != request) {
            return;
        }

        Location safe = findSafeDestination(located, request.target().biome());
        if (safe == null) {
            scheduleProbe(player, request, 10L);
            return;
        }

        safe.setYaw(request.yaw());
        safe.setPitch(request.pitch());
        Slimefun.getSchedulerService()
                .runFor(
                        player,
                        () -> completeTeleport(player, request, safe),
                        () -> ACTIVE_SEARCHES.remove(player.getUniqueId(), request));
    }

    private void completeTeleport(Player player, SearchRequest request, Location safe) {
        UUID playerId = player.getUniqueId();
        if (ACTIVE_SEARCHES.get(playerId) != request) {
            return;
        }
        if (!player.getWorld().getUID().equals(request.worldId())) {
            ACTIVE_SEARCHES.remove(playerId, request);
            player.sendMessage(ChatColor.RED + "Biome search cancelled because you changed worlds.");
            return;
        }

        player.teleportAsync(safe).whenComplete((success, error) -> Slimefun.getSchedulerService()
                .runFor(
                        player,
                        () -> {
                            ACTIVE_SEARCHES.remove(playerId, request);
                            if (error != null || !Boolean.TRUE.equals(success)) {
                                player.sendMessage(ChatColor.RED + "Wayfarer's Lodestone could not complete the teleport.");
                                return;
                            }

                            startCooldown(player);
                            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.5F, 1.25F);
                            player.sendMessage(ChatColor.GOLD + "Wayfarer's Lodestone: " + ChatColor.GRAY + "arrived in "
                                    + ChatColor.AQUA + request.target().displayName() + ChatColor.GRAY + " at "
                                    + ChatColor.YELLOW + safe.getBlockX() + ", " + safe.getBlockY() + ", "
                                    + safe.getBlockZ() + ChatColor.GRAY + ".");
                        },
                        () -> ACTIVE_SEARCHES.remove(playerId, request)));
    }

    private void failSearch(Player player, SearchRequest request, String message) {
        ACTIVE_SEARCHES.remove(player.getUniqueId(), request);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.7F);
        player.sendMessage(ChatColor.RED + message + ChatColor.GRAY + " Try again to use a new randomized probe.");
    }

    private void finishUnavailable(Player player, SearchRequest request, String message) {
        Slimefun.getSchedulerService()
                .runFor(
                        player,
                        () -> {
                            ACTIVE_SEARCHES.remove(player.getUniqueId(), request);
                            player.sendMessage(ChatColor.RED + message);
                        },
                        () -> ACTIVE_SEARCHES.remove(player.getUniqueId(), request));
    }

    private static Location randomProbe(World world, Location origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        WorldBorder border = world.getWorldBorder();
        int y = Math.max(world.getMinHeight() + 2, Math.min(world.getMaxHeight() - 3, origin.getBlockY()));

        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            int distance = random.nextInt(1_024, RANDOM_PROBE_RADIUS_BLOCKS + 1);
            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            Location candidate = new Location(world, x, y, z);
            if (border.isInside(candidate)) {
                return candidate;
            }
        }

        return origin.clone();
    }

    private static Location findSafeDestination(Location located, Biome targetBiome) {
        World world = located.getWorld();
        int x = located.getBlockX();
        int z = located.getBlockZ();
        if (world.getEnvironment() == World.Environment.NETHER) {
            return findNetherDestination(world, x, located.getBlockY(), z, targetBiome);
        }

        int highest = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int startFeetY = Math.min(world.getMaxHeight() - 2, highest + 1);
        int minimum = Math.max(world.getMinHeight() + 2, startFeetY - 24);
        for (int feetY = startFeetY; feetY >= minimum; feetY--) {
            if (isSafe(world, x, feetY, z, targetBiome)) {
                return centered(world, x, feetY, z);
            }
        }
        return null;
    }

    private static Location findNetherDestination(World world, int x, int preferredY, int z, Biome targetBiome) {
        int minimum = world.getMinHeight() + 2;
        int maximum = world.getMaxHeight() - 2;
        int center = Math.max(minimum, Math.min(maximum, preferredY));

        for (int offset = 0; offset <= 48; offset++) {
            int high = center + offset;
            if (high <= maximum && isSafe(world, x, high, z, targetBiome)) {
                return centered(world, x, high, z);
            }
            int low = center - offset;
            if (low >= minimum && isSafe(world, x, low, z, targetBiome)) {
                return centered(world, x, low, z);
            }
        }

        for (int feetY = maximum; feetY >= minimum; feetY--) {
            if (isSafe(world, x, feetY, z, targetBiome)) {
                return centered(world, x, feetY, z);
            }
        }
        return null;
    }

    private static boolean isSafe(World world, int x, int feetY, int z, Biome targetBiome) {
        if (feetY <= world.getMinHeight() + 1 || feetY >= world.getMaxHeight() - 1) {
            return false;
        }

        Block floor = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        if (!targetBiome.equals(feet.getBiome()) && !targetBiome.equals(floor.getBiome())) {
            return false;
        }
        if (!floor.getType().isSolid() || isDangerousFloor(floor.getType())) {
            return false;
        }
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return false;
        }

        Location destination = centered(world, x, feetY, z);
        return world.getWorldBorder().isInside(destination);
    }

    private static boolean isDangerousFloor(Material material) {
        return switch (material) {
            case LAVA, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private static Location centered(World world, int x, int y, int z) {
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private long getCooldownRemainingMillis(Player player) {
        Long until = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    private void startCooldown(Player player) {
        player.getPersistentDataContainer()
                .set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MILLIS);
    }

    private static long formatSeconds(long millis) {
        return Math.max(1L, (millis + 999L) / 1_000L);
    }

    private static List<BiomeTarget> targetsFor(World.Environment environment) {
        List<BiomeTarget> result = new ArrayList<>();
        for (BiomeTarget target : BiomeTarget.values()) {
            if (target.environment() == environment) {
                result.add(target);
            }
        }
        return result;
    }

    private static ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private enum BiomeTarget {
        PLAINS(World.Environment.NORMAL, Biome.PLAINS, Material.GRASS_BLOCK, "Plains"),
        FOREST(World.Environment.NORMAL, Biome.FOREST, Material.OAK_SAPLING, "Forest"),
        BIRCH_FOREST(World.Environment.NORMAL, Biome.BIRCH_FOREST, Material.BIRCH_SAPLING, "Birch Forest"),
        DARK_FOREST(World.Environment.NORMAL, Biome.DARK_FOREST, Material.DARK_OAK_SAPLING, "Dark Forest"),
        JUNGLE(World.Environment.NORMAL, Biome.JUNGLE, Material.JUNGLE_SAPLING, "Jungle"),
        BAMBOO_JUNGLE(World.Environment.NORMAL, Biome.BAMBOO_JUNGLE, Material.BAMBOO, "Bamboo Jungle"),
        DESERT(World.Environment.NORMAL, Biome.DESERT, Material.SAND, "Desert"),
        BADLANDS(World.Environment.NORMAL, Biome.BADLANDS, Material.RED_SAND, "Badlands"),
        SAVANNA(World.Environment.NORMAL, Biome.SAVANNA, Material.ACACIA_SAPLING, "Savanna"),
        TAIGA(World.Environment.NORMAL, Biome.TAIGA, Material.SPRUCE_SAPLING, "Taiga"),
        SNOWY_PLAINS(World.Environment.NORMAL, Biome.SNOWY_PLAINS, Material.SNOW_BLOCK, "Snowy Plains"),
        CHERRY_GROVE(World.Environment.NORMAL, Biome.CHERRY_GROVE, Material.CHERRY_SAPLING, "Cherry Grove"),
        MANGROVE_SWAMP(World.Environment.NORMAL, Biome.MANGROVE_SWAMP, Material.MANGROVE_PROPAGULE, "Mangrove Swamp"),
        SWAMP(World.Environment.NORMAL, Biome.SWAMP, Material.LILY_PAD, "Swamp"),
        MUSHROOM_FIELDS(World.Environment.NORMAL, Biome.MUSHROOM_FIELDS, Material.MYCELIUM, "Mushroom Fields"),
        MEADOW(World.Environment.NORMAL, Biome.MEADOW, Material.DANDELION, "Meadow"),
        ICE_SPIKES(World.Environment.NORMAL, Biome.ICE_SPIKES, Material.PACKED_ICE, "Ice Spikes"),
        OLD_GROWTH_PINE_TAIGA(
                World.Environment.NORMAL, Biome.OLD_GROWTH_PINE_TAIGA, Material.PODZOL, "Old Growth Pine Taiga"),
        NETHER_WASTES(World.Environment.NETHER, Biome.NETHER_WASTES, Material.NETHERRACK, "Nether Wastes"),
        CRIMSON_FOREST(World.Environment.NETHER, Biome.CRIMSON_FOREST, Material.CRIMSON_FUNGUS, "Crimson Forest"),
        WARPED_FOREST(World.Environment.NETHER, Biome.WARPED_FOREST, Material.WARPED_FUNGUS, "Warped Forest"),
        SOUL_SAND_VALLEY(
                World.Environment.NETHER, Biome.SOUL_SAND_VALLEY, Material.SOUL_SAND, "Soul Sand Valley"),
        BASALT_DELTAS(World.Environment.NETHER, Biome.BASALT_DELTAS, Material.BASALT, "Basalt Deltas"),
        THE_END(World.Environment.THE_END, Biome.THE_END, Material.END_STONE, "The End"),
        END_HIGHLANDS(World.Environment.THE_END, Biome.END_HIGHLANDS, Material.CHORUS_FLOWER, "End Highlands"),
        END_MIDLANDS(World.Environment.THE_END, Biome.END_MIDLANDS, Material.CHORUS_FRUIT, "End Midlands"),
        SMALL_END_ISLANDS(
                World.Environment.THE_END, Biome.SMALL_END_ISLANDS, Material.PURPUR_BLOCK, "Small End Islands"),
        END_BARRENS(World.Environment.THE_END, Biome.END_BARRENS, Material.END_STONE_BRICKS, "End Barrens");

        private final World.Environment environment;
        private final Biome biome;
        private final Material icon;
        private final String displayName;

        BiomeTarget(World.Environment environment, Biome biome, Material icon, String displayName) {
            this.environment = environment;
            this.biome = biome;
            this.icon = icon;
            this.displayName = displayName;
        }

        World.Environment environment() {
            return environment;
        }

        Biome biome() {
            return biome;
        }

        Material icon() {
            return icon;
        }

        String displayName() {
            return displayName;
        }
    }

    private static final class SearchRequest {
        private final UUID worldId;
        private final Location origin;
        private final BiomeTarget target;
        private final float yaw;
        private final float pitch;
        private int probes;

        private SearchRequest(UUID worldId, Location origin, BiomeTarget target, float yaw, float pitch) {
            this.worldId = worldId;
            this.origin = origin;
            this.target = target;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        UUID worldId() {
            return worldId;
        }

        Location origin() {
            return origin;
        }

        BiomeTarget target() {
            return target;
        }

        float yaw() {
            return yaw;
        }

        float pitch() {
            return pitch;
        }

        int probes() {
            return probes;
        }

        void incrementProbes() {
            probes++;
        }
    }
}