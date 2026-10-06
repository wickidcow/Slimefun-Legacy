package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import net.kyori.adventure.text.Component;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Guarded one-time SmartSpawner to InfinityExpansion2 Mob Simulation Chamber migration.
 *
 * <p>The integration deliberately uses reflection against SmartSpawner's public API so Slimefun
 * Legacy keeps its Java 21 / Paper 1.21.11 compatibility floor. No SmartSpawner implementation
 * classes or database files are accessed directly.</p>
 */
final class DoctorSmartSpawnerCommand {

    private static final String SMART_SPAWNER_PLUGIN = "SmartSpawner";
    private static final String CHAMBER_ID = "IE_MOB_SIMULATION_CHAMBER";
    private static final String CARD_PREFIX = "IE_MOB_DATA_CARD_";
    private static final int CARD_INPUT_SLOT = 1;
    private static final long PLAN_TTL_MILLIS = 10L * 60L * 1000L;
    private static final int MAX_DETAIL_LINES = 20;

    private final Slimefun plugin;
    private final NamespacedKey migrationSignKey;
    private final AtomicBoolean migrationActive = new AtomicBoolean();
    private volatile @Nullable MigrationPlan preparedPlan;

    DoctorSmartSpawnerCommand(@Nonnull Slimefun plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        migrationSignKey = new NamespacedKey(plugin, "smartspawner_migration_sign");
    }

    void execute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status" -> sendStatus(sender);
            case "scan", "plan", "dryrun", "dry-run" -> scan(sender);
            case "replace", "execute", "convert", "migrate" -> replace(sender, args);
            default -> sendUsage(sender);
        }
    }

    private void sendStatus(@Nonnull CommandSender sender) {
        send(sender, "&6SmartSpawner -> Mob Simulation Doctor");
        if (migrationActive.get()) {
            send(sender, "&eA SmartSpawner replacement run is currently active.");
        }

        SmartSpawnerBridge bridge = openBridge(sender);
        if (bridge == null) {
            return;
        }

        SlimefunItem chamber = SlimefunItem.getById(CHAMBER_ID);
        if (chamber == null || chamber.isDisabled()) {
            send(sender, "&cInfinityExpansion2 Mob Simulation Chamber is not registered/enabled.");
            send(sender, "&7Expected Slimefun ID: &f" + CHAMBER_ID);
            return;
        }

        try {
            List<SpawnerSnapshot> spawners = bridge.getAll();
            long itemSpawners = spawners.stream().filter(SpawnerSnapshot::itemSpawner).count();
            long stacked = spawners.stream().filter(s -> s.stackSize() > 1).count();
            long compatible = spawners.stream().filter(s -> resolveCardId(s) != null).count();
            send(sender, "&7SmartSpawner version: &e" + bridge.pluginVersion());
            send(sender, "&7Registered SmartSpawners: &e" + spawners.size());
            send(sender, "&7Card-compatible: &a" + compatible
                    + " &8| &7empty-chamber fallback: &e" + (spawners.size() - compatible));
            send(sender, "&7Item spawners: &e" + itemSpawners + " &8| &7stacked blocks: &e" + stacked);

            MigrationPlan plan = currentPlan();
            if (plan == null) {
                send(sender, "&7Prepared plan: &fnone");
            } else {
                long seconds = Math.max(0L, (plan.expiresAt() - System.currentTimeMillis()) / 1000L);
                send(sender, "&7Prepared plan: &b" + plan.fingerprint() + " &8| &7expires in &e" + seconds + "s");
            }
            send(sender, "&7Scan: &e/sf doctor smartspawners scan");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            bridgeFailure(sender, "read SmartSpawner records", ex);
        }
    }

    private void scan(@Nonnull CommandSender sender) {
        if (migrationActive.get()) {
            send(sender, "&eA SmartSpawner replacement run is active; a new plan was not created.");
            return;
        }

        preparedPlan = null;
        SmartSpawnerBridge bridge = openBridge(sender);
        if (bridge == null) {
            return;
        }

        SlimefunItem chamber = SlimefunItem.getById(CHAMBER_ID);
        if (chamber == null || chamber.isDisabled()) {
            send(sender, "&cCannot plan replacement: " + CHAMBER_ID + " is not registered/enabled.");
            return;
        }

        final List<SpawnerSnapshot> snapshots;
        try {
            snapshots = bridge.getAll();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            bridgeFailure(sender, "scan SmartSpawner records", ex);
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        int malformed = 0;
        for (SpawnerSnapshot snapshot : snapshots) {
            if (!snapshot.isUsable()) {
                malformed++;
                continue;
            }
            candidates.add(new Candidate(snapshot, resolveCardId(snapshot)));
        }
        candidates.sort(Comparator.comparing(c -> c.source().id(), String.CASE_INSENSITIVE_ORDER));

        if (candidates.isEmpty()) {
            send(sender, "&6SmartSpawner Migration Scan");
            send(sender, malformed == 0
                    ? "&aNo SmartSpawner records were found."
                    : "&eNo executable records were found; malformed/stale records: &c" + malformed);
            send(sender, "&8No chunks were force-loaded and no data was changed.");
            return;
        }

        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(bridge.pluginVersion(), candidates);
        MigrationPlan plan = new MigrationPlan(
                fingerprint,
                bridge.pluginVersion(),
                now,
                now + PLAN_TTL_MILLIS,
                List.copyOf(candidates));
        preparedPlan = plan;

        long compatible = candidates.stream().filter(c -> c.cardId() != null).count();
        long empty = candidates.size() - compatible;
        long itemSpawners = candidates.stream().filter(c -> c.source().itemSpawner()).count();
        long stacked = candidates.stream().filter(c -> c.source().stackSize() > 1).count();
        long stackedUnits = candidates.stream()
                .filter(c -> c.source().stackSize() > 1)
                .mapToLong(c -> c.source().stackSize())
                .sum();

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String type = candidate.source().displayType();
            byType.merge(type, 1L, Long::sum);
        }

        send(sender, "&6SmartSpawner -> Mob Simulation Plan");
        send(sender, "&7Exact SmartSpawner blocks: &e" + candidates.size());
        send(sender, "&7With matching Mob Data Card: &a" + compatible
                + " &8| &7empty chambers: &e" + empty);
        send(sender, "&7Item spawners -> empty chambers: &e" + itemSpawners);
        if (malformed > 0) {
            send(sender, "&7Malformed/stale records excluded: &c" + malformed);
        }
        if (stacked > 0) {
            send(sender, "&eStacked SmartSpawner blocks: " + stacked + " (" + stackedUnits + " source units).");
            send(sender, "&eEach SmartSpawner BLOCK becomes one chamber. The original stack size is recorded in the migration manifest.");
        }

        int shown = 0;
        for (Map.Entry<String, Long> entry : byType.entrySet()) {
            if (shown++ >= MAX_DETAIL_LINES) {
                send(sender, "&8... " + (byType.size() - MAX_DETAIL_LINES) + " more mob/item type(s)");
                break;
            }
            String cardId = resolveCardIdForEntity(entry.getKey());
            boolean hasCard = cardId != null && SlimefunItem.getById(cardId) != null;
            send(sender, "&8- &f" + entry.getKey() + " &8x&e" + entry.getValue()
                    + (hasCard ? " &8-> &aMob Data Card" : " &8-> &eempty chamber"));
        }

        long ttlMinutes = Math.max(1L, PLAN_TTL_MILLIS / 60_000L);
        send(sender, "&7Fingerprint: &b" + fingerprint);
        send(sender, "&7Plan expires after &e" + ttlMinutes + " minute(s)&7 and is single-use.");
        send(sender, "&eBefore replacement, collect any loot and XP currently stored inside SmartSpawner.");
        send(sender, "&8SmartSpawner's public API does not expose its current stored-loot/XP balances for migration.");
        send(sender, "&7Every replacement gets a migration sign above it when that block is air:");
        send(sender, "&fSpawner Migrated &8/ &fReplaced with a &8/ &fMob Simulation &8/ &fChamber");
        send(sender, "&7After an offline backup, execute:");
        send(sender, "&6/sf doctor smartspawners replace " + fingerprint);
        send(sender, "&8Scan only: SmartSpawner records were read through its public API; no chunks were loaded and no blocks changed.");
    }

    private void replace(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 4 || args[3].isBlank()) {
            send(sender, "&eUsage: /sf doctor smartspawners replace <fingerprint>");
            return;
        }
        if (!migrationActive.compareAndSet(false, true)) {
            send(sender, "&eA SmartSpawner replacement run is already active.");
            return;
        }

        MigrationPlan plan = currentPlan();
        if (plan == null) {
            migrationActive.set(false);
            send(sender, "&cNo active SmartSpawner plan exists, or it expired.");
            send(sender, "&7Run &e/sf doctor smartspawners scan &7again.");
            return;
        }
        if (!plan.matches(args[3])) {
            migrationActive.set(false);
            send(sender, "&cSmartSpawner migration fingerprint is missing or incorrect.");
            send(sender, "&7No plan was consumed and no blocks were changed.");
            return;
        }

        SmartSpawnerBridge bridge = openBridge(sender);
        if (bridge == null) {
            migrationActive.set(false);
            return;
        }
        if (!bridge.pluginVersion().equals(plan.smartSpawnerVersion())) {
            preparedPlan = null;
            migrationActive.set(false);
            send(sender, "&cSmartSpawner changed version after planning; replacement was blocked.");
            send(sender, "&7Create a fresh scan.");
            return;
        }

        if (SlimefunItem.getById(CHAMBER_ID) == null) {
            preparedPlan = null;
            migrationActive.set(false);
            send(sender, "&cMob Simulation Chamber disappeared after planning; replacement was blocked.");
            return;
        }

        final File manifest;
        try {
            manifest = writeManifest(plan);
        } catch (IOException ex) {
            migrationActive.set(false);
            plugin.getLogger().warning("Unable to write SmartSpawner migration manifest: " + ex.getMessage());
            send(sender, "&cReplacement blocked because the pre-migration manifest could not be written.");
            return;
        }

        // Single-use once mutation is authorized.
        preparedPlan = null;
        send(sender, "&6SmartSpawner Replacement");
        send(sender, "&ePlan consumed. Every exact record will be revalidated before SmartSpawner is asked to remove it.");
        send(sender, "&7Migration manifest: &f" + relativeToDataFolder(manifest));
        send(sender, "&7Candidates: &e" + plan.candidates().size());
        new MigrationRun(sender, bridge, plan).start();
    }

    private @Nullable MigrationPlan currentPlan() {
        MigrationPlan plan = preparedPlan;
        if (plan != null && plan.expiresAt() <= System.currentTimeMillis()) {
            preparedPlan = null;
            return null;
        }
        return plan;
    }

    private @Nullable SmartSpawnerBridge openBridge(@Nonnull CommandSender sender) {
        Plugin smartSpawner = Bukkit.getPluginManager().getPlugin(SMART_SPAWNER_PLUGIN);
        if (smartSpawner == null || !smartSpawner.isEnabled()) {
            send(sender, "&cSmartSpawner is not installed/enabled.");
            send(sender, "&7Keep SmartSpawner installed until the replacement pass has completed.");
            return null;
        }

        try {
            return SmartSpawnerBridge.open(smartSpawner);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            bridgeFailure(sender, "connect to the SmartSpawner public API", ex);
            return null;
        }
    }

    private void bridgeFailure(CommandSender sender, String action, Throwable throwable) {
        plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to " + action, throwable);
        send(sender, "&cUnable to " + action + ". No SmartSpawner data was changed.");
        send(sender, "&7Check the console and verify the installed SmartSpawner build exposes its current public API.");
    }

    private @Nullable String resolveCardId(SpawnerSnapshot snapshot) {
        if (snapshot.itemSpawner() || snapshot.entityType().isBlank()) {
            return null;
        }
        String id = resolveCardIdForEntity(snapshot.entityType());
        if (id == null) {
            return null;
        }
        SlimefunItem card = SlimefunItem.getById(id);
        return card == null || card.isDisabled() ? null : id;
    }

    private @Nullable String resolveCardIdForEntity(String entityName) {
        if (entityName == null || entityName.isBlank() || entityName.equalsIgnoreCase("ITEM")) {
            return null;
        }
        String suffix = entityName.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (suffix.isBlank()) {
            return null;
        }
        String id = CARD_PREFIX + suffix;
        SlimefunItem item = SlimefunItem.getById(id);
        return item == null || item.isDisabled() ? null : id;
    }

    private String fingerprint(String smartSpawnerVersion, List<Candidate> candidates) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "smartspawner-doctor-v1");
            updateDigest(digest, smartSpawnerVersion);
            updateDigest(digest, CHAMBER_ID);
            for (Candidate candidate : candidates) {
                SpawnerSnapshot source = candidate.source();
                updateDigest(digest, source.id());
                updateDigest(digest, String.valueOf(source.worldId()));
                updateDigest(digest, source.worldName());
                updateDigest(digest, source.x() + "," + source.y() + "," + source.z());
                updateDigest(digest, source.entityType());
                updateDigest(digest, source.spawnedItem());
                updateDigest(digest, String.valueOf(source.stackSize()));
                updateDigest(digest, String.valueOf(source.itemSpawner()));
                updateDigest(digest, candidate.cardId() == null ? "<empty>" : candidate.cardId());
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                out.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private File writeManifest(MigrationPlan plan) throws IOException {
        File folder = new File(plugin.getDataFolder(), "migrations/smartspawners");
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("Could not create " + folder);
        }

        File file = new File(folder, Instant.ofEpochMilli(plan.createdAt()).toString().replace(':', '-') + "-"
                + plan.fingerprint() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format", 1);
        yaml.set("created-at", Instant.ofEpochMilli(plan.createdAt()).toString());
        yaml.set("fingerprint", plan.fingerprint());
        yaml.set("smartspawner-version", plan.smartSpawnerVersion());
        yaml.set("target-chamber", CHAMBER_ID);
        yaml.set("candidate-count", plan.candidates().size());
        yaml.set("notes", List.of(
                "This is a migration manifest, not a SmartSpawner database backup.",
                "Collect stored SmartSpawner loot/XP before executing; the public API does not expose those balances.",
                "Unsupported mob/item spawners are replaced with empty Mob Simulation Chambers."));

        int index = 0;
        for (Candidate candidate : plan.candidates()) {
            SpawnerSnapshot source = candidate.source();
            String base = "spawners." + index++;
            yaml.set(base + ".id", source.id());
            yaml.set(base + ".world-uuid", source.worldId() == null ? null : source.worldId().toString());
            yaml.set(base + ".world", source.worldName());
            yaml.set(base + ".x", source.x());
            yaml.set(base + ".y", source.y());
            yaml.set(base + ".z", source.z());
            yaml.set(base + ".entity-type", source.entityType());
            yaml.set(base + ".spawned-item", source.spawnedItem());
            yaml.set(base + ".item-spawner", source.itemSpawner());
            yaml.set(base + ".stack-size", source.stackSize());
            yaml.set(base + ".target-card", candidate.cardId());
        }
        yaml.save(file);
        return file;
    }

    private String relativeToDataFolder(File file) {
        try {
            return plugin.getDataFolder().toPath().relativize(file.toPath()).toString();
        } catch (IllegalArgumentException ex) {
            return file.getPath();
        }
    }

    private void sendUsage(CommandSender sender) {
        send(sender, "&eUsage: /sf doctor smartspawners <status|scan|replace> [fingerprint]");
        send(sender, "&7Aliases for replace: execute, convert, migrate. Scan is read-only.");
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(message.replace('&', '\u00A7'));
    }

    private void sendAsync(CommandSender sender, String message) {
        Runnable task = () -> send(sender, message);
        if (sender instanceof Player player) {
            Slimefun.runSyncFor(player, task, 0L);
        } else {
            Slimefun.runSync(task);
        }
    }

    private final class MigrationRun {
        private final CommandSender sender;
        private final SmartSpawnerBridge bridge;
        private final MigrationPlan plan;
        private long migratedWithCard;
        private long migratedEmpty;
        private long skippedChanged;
        private long failures;
        private long signsPlaced;
        private long signsSkipped;
        private final List<String> details = new ArrayList<>();

        private MigrationRun(CommandSender sender, SmartSpawnerBridge bridge, MigrationPlan plan) {
            this.sender = sender;
            this.bridge = bridge;
            this.plan = plan;
        }

        private void start() {
            process(0);
        }

        private void process(int index) {
            if (index >= plan.candidates().size()) {
                finish();
                return;
            }

            Candidate candidate = plan.candidates().get(index);
            Slimefun.runSync(() -> prepareCandidate(index, candidate));
        }

        private void prepareCandidate(int index, Candidate candidate) {
            final SpawnerSnapshot current;
            try {
                current = bridge.getById(candidate.source().id());
            } catch (ReflectiveOperationException | RuntimeException ex) {
                failure(candidate, "SmartSpawner revalidation threw " + ex.getClass().getSimpleName());
                process(index + 1);
                return;
            }

            if (!candidate.source().sameIdentity(current)) {
                skipped(candidate, "SmartSpawner record changed after planning");
                process(index + 1);
                return;
            }

            Location location = candidate.source().toLocation();
            if (location == null) {
                skipped(candidate, "world is no longer available");
                process(index + 1);
                return;
            }

            World world = location.getWorld();
            if (world == null) {
                skipped(candidate, "world is no longer available");
                process(index + 1);
                return;
            }

            world.getChunkAtAsync(location.getBlockX() >> 4, location.getBlockZ() >> 4, true)
                    .whenComplete((chunk, error) -> {
                        if (error != null) {
                            failure(candidate, "chunk load failed: " + error.getClass().getSimpleName());
                            process(index + 1);
                            return;
                        }
                        Slimefun.runSyncAt(location, () -> preflightAndRemove(index, candidate, location));
                    });
        }

        private void preflightAndRemove(int index, Candidate candidate, Location location) {
            try {
                SpawnerSnapshot current = bridge.getById(candidate.source().id());
                if (!candidate.source().sameIdentity(current)) {
                    skipped(candidate, "SmartSpawner changed after chunk load");
                    process(index + 1);
                    return;
                }

                Block block = location.getBlock();
                if (block.getType() != Material.SPAWNER) {
                    skipped(candidate, "physical block is no longer a spawner (" + block.getType() + ")");
                    process(index + 1);
                    return;
                }
                if (StorageCacheUtils.hasSlimefunBlock(location)) {
                    skipped(candidate, "location already has Slimefun block data");
                    process(index + 1);
                    return;
                }

                SlimefunItem chamber = SlimefunItem.getById(CHAMBER_ID);
                if (chamber == null || chamber.isDisabled()) {
                    failure(candidate, "Mob Simulation Chamber target is unavailable");
                    process(index + 1);
                    return;
                }

                ItemStack card = null;
                if (candidate.cardId() != null) {
                    SlimefunItem cardItem = SlimefunItem.getById(candidate.cardId());
                    if (cardItem == null || cardItem.isDisabled()) {
                        failure(candidate, "planned Mob Data Card is unavailable: " + candidate.cardId());
                        process(index + 1);
                        return;
                    }
                    card = cardItem.getRecipeOutput();
                    card.setAmount(1);
                }

                Material chamberMaterial = chamber.getRecipeOutput().getType();
                ItemStack plannedCard = card == null ? null : card.clone();
                CompletableFuture<Boolean> removal = bridge.remove(candidate.source().id());
                removal.whenComplete((removed, error) -> {
                    if (error != null || !Boolean.TRUE.equals(removed)) {
                        String detail = error == null
                                ? "SmartSpawner refused removal"
                                : "SmartSpawner removal failed: " + error.getClass().getSimpleName();
                        failure(candidate, detail);
                        process(index + 1);
                        return;
                    }
                    Slimefun.runSyncAt(location, () ->
                            placeReplacement(index, candidate, location, chamberMaterial, plannedCard));
                });
            } catch (ReflectiveOperationException | RuntimeException ex) {
                failure(candidate, "preflight failed: " + ex.getClass().getSimpleName());
                process(index + 1);
            }
        }

        private void placeReplacement(
                int index,
                Candidate candidate,
                Location location,
                Material chamberMaterial,
                @Nullable ItemStack card) {
            try {
                Block block = location.getBlock();
                if (!block.getType().isAir()) {
                    failure(candidate, "SmartSpawner was removed but replacement location is occupied by " + block.getType());
                    process(index + 1);
                    return;
                }
                if (StorageCacheUtils.hasSlimefunBlock(location)) {
                    failure(candidate, "SmartSpawner was removed but Slimefun data appeared at the location");
                    process(index + 1);
                    return;
                }

                block.setType(chamberMaterial, false);
                try {
                    var blockData = Slimefun.getDatabaseManager()
                            .getBlockDataController()
                            .createBlock(location, CHAMBER_ID);
                    blockData.setData("smartspawner-migration-id", candidate.source().id());
                    blockData.setData("smartspawner-migration-type", candidate.source().displayType());
                    blockData.setData(
                            "smartspawner-migration-stack-size", String.valueOf(candidate.source().stackSize()));

                    BlockMenu menu = blockData.getBlockMenu();
                    if (menu == null) {
                        throw new IllegalStateException("Mob Simulation Chamber BlockMenu was not created");
                    }
                    if (card != null) {
                        menu.replaceExistingItem(CARD_INPUT_SLOT, card);
                        migratedWithCard++;
                    } else {
                        migratedEmpty++;
                    }
                } catch (RuntimeException placementFailure) {
                    // Do not leave a vanilla block masquerading as a Slimefun machine after a failed registration.
                    if (StorageCacheUtils.hasSlimefunBlock(location)) {
                        Slimefun.getDatabaseManager().getBlockDataController().removeBlock(location);
                    }
                    block.setType(Material.AIR, false);
                    throw placementFailure;
                }

                if (placeMigrationSign(block)) {
                    signsPlaced++;
                } else {
                    signsSkipped++;
                }

                addDetail(candidate.source().locationKey() + ": "
                        + candidate.source().displayType()
                        + (candidate.cardId() == null ? " -> empty chamber" : " -> " + candidate.cardId())
                        + (candidate.source().stackSize() > 1
                                ? " (source stack " + candidate.source().stackSize() + " recorded)"
                                : ""));
            } catch (RuntimeException ex) {
                failure(candidate, "replacement placement failed after SmartSpawner removal: "
                        + ex.getClass().getSimpleName() + " - " + safeMessage(ex));
                plugin.getLogger().log(
                        java.util.logging.Level.SEVERE,
                        "SmartSpawner was removed but Mob Simulation replacement failed at "
                                + candidate.source().locationKey(),
                        ex);
            }
            process(index + 1);
        }

        private boolean placeMigrationSign(Block chamber) {
            Block signBlock = chamber.getRelative(BlockFace.UP);
            if (!signBlock.getType().isAir()) {
                return false;
            }

            signBlock.setType(Material.OAK_SIGN, false);
            if (!(signBlock.getState() instanceof Sign sign)) {
                signBlock.setType(Material.AIR, false);
                return false;
            }

            SignSide front = sign.getSide(Side.FRONT);
            front.line(0, Component.text("Spawner Migrated"));
            front.line(1, Component.text("Replaced with a"));
            front.line(2, Component.text("Mob Simulation"));
            front.line(3, Component.text("Chamber"));
            sign.getPersistentDataContainer().set(migrationSignKey, PersistentDataType.BYTE, (byte) 1);
            return sign.update(true, false);
        }

        private void skipped(Candidate candidate, String detail) {
            skippedChanged++;
            addDetail(candidate.source().locationKey() + ": skipped - " + detail);
        }

        private void failure(Candidate candidate, String detail) {
            failures++;
            addDetail(candidate.source().locationKey() + ": FAILED - " + detail);
        }

        private void addDetail(String detail) {
            if (details.size() < MAX_DETAIL_LINES) {
                details.add(detail);
            }
        }

        private void finish() {
            migrationActive.set(false);
            long migrated = migratedWithCard + migratedEmpty;
            sendAsync(sender, "&6SmartSpawner Replacement Report");
            sendAsync(sender, "&7Migrated: &a" + migrated
                    + " &8| &7with card: &a" + migratedWithCard
                    + " &8| &7empty: &e" + migratedEmpty);
            sendAsync(sender, "&7Changed/skipped: &e" + skippedChanged
                    + " &8| &7failures: &c" + failures);
            sendAsync(sender, "&7Migration signs: &a" + signsPlaced
                    + " &8| &7skipped because above block was occupied/unavailable: &e" + signsSkipped);
            for (String detail : details) {
                sendAsync(sender, "&8- &7" + detail);
            }
            if (plan.candidates().size() > details.size()) {
                sendAsync(sender, "&8Only the first " + MAX_DETAIL_LINES + " per-location details are shown.");
            }
            if (failures > 0L) {
                sendAsync(sender, "&cReview the console and migration manifest before removing SmartSpawner.");
            } else {
                sendAsync(sender, "&aReplacement pass finished. Run /sf doctor smartspawners scan again.");
                sendAsync(sender, "&7Only remove SmartSpawner after the follow-up scan reports no remaining records.");
            }
        }

        private String safeMessage(Throwable throwable) {
            String message = throwable.getMessage();
            return message == null || message.isBlank() ? "<no message>" : message;
        }
    }

    private record Candidate(@Nonnull SpawnerSnapshot source, @Nullable String cardId) {}

    private record MigrationPlan(
            @Nonnull String fingerprint,
            @Nonnull String smartSpawnerVersion,
            long createdAt,
            long expiresAt,
            @Nonnull List<Candidate> candidates) {

        private boolean matches(String supplied) {
            return fingerprint.equalsIgnoreCase(supplied);
        }
    }

    private record SpawnerSnapshot(
            @Nonnull String id,
            @Nullable UUID worldId,
            @Nonnull String worldName,
            int x,
            int y,
            int z,
            @Nonnull String entityType,
            @Nonnull String spawnedItem,
            int stackSize,
            boolean itemSpawner) {

        private boolean isUsable() {
            return !id.isBlank() && worldId != null && !worldName.isBlank() && stackSize > 0;
        }

        private @Nullable Location toLocation() {
            World world = worldId == null ? null : Bukkit.getWorld(worldId);
            if (world == null && !worldName.isBlank()) {
                world = Bukkit.getWorld(worldName);
            }
            return world == null ? null : new Location(world, x, y, z);
        }

        private String locationKey() {
            return worldName + ":" + x + "," + y + "," + z;
        }

        private String displayType() {
            if (itemSpawner) {
                return spawnedItem.isBlank() ? "ITEM" : "ITEM:" + spawnedItem;
            }
            return entityType.isBlank() ? "<unknown>" : entityType;
        }

        private boolean sameIdentity(@Nullable SpawnerSnapshot other) {
            return other != null
                    && id.equals(other.id)
                    && Objects.equals(worldId, other.worldId)
                    && worldName.equals(other.worldName)
                    && x == other.x
                    && y == other.y
                    && z == other.z
                    && entityType.equals(other.entityType)
                    && spawnedItem.equals(other.spawnedItem)
                    && stackSize == other.stackSize
                    && itemSpawner == other.itemSpawner;
        }
    }

    /**
     * Reflection-only adapter over the documented SmartSpawner public API.
     */
    private static final class SmartSpawnerBridge {
        private final Plugin smartSpawner;
        private final Object api;
        private final Method getAllSpawners;
        private final Method getSpawnerById;
        private final Method removeSpawner;

        private SmartSpawnerBridge(
                Plugin smartSpawner,
                Object api,
                Method getAllSpawners,
                Method getSpawnerById,
                Method removeSpawner) {
            this.smartSpawner = smartSpawner;
            this.api = api;
            this.getAllSpawners = getAllSpawners;
            this.getSpawnerById = getSpawnerById;
            this.removeSpawner = removeSpawner;
        }

        private static SmartSpawnerBridge open(Plugin plugin) throws ReflectiveOperationException {
            Method getApi = plugin.getClass().getMethod("getAPI");
            Object api = getApi.invoke(plugin);
            if (api == null) {
                throw new IllegalStateException("SmartSpawner getAPI() returned null");
            }
            Class<?> apiType = api.getClass();
            return new SmartSpawnerBridge(
                    plugin,
                    api,
                    apiType.getMethod("getAllSpawners"),
                    apiType.getMethod("getSpawnerById", String.class),
                    apiType.getMethod("removeSpawner", String.class));
        }

        private String pluginVersion() {
            return smartSpawner.getPluginMeta().getVersion();
        }

        private List<SpawnerSnapshot> getAll() throws ReflectiveOperationException {
            Object raw = getAllSpawners.invoke(api);
            if (!(raw instanceof Collection<?> collection)) {
                throw new IllegalStateException("SmartSpawner getAllSpawners() did not return a Collection");
            }
            List<SpawnerSnapshot> result = new ArrayList<>(collection.size());
            for (Object dto : collection) {
                SpawnerSnapshot snapshot = snapshot(dto);
                if (snapshot != null) {
                    result.add(snapshot);
                }
            }
            return result;
        }

        private @Nullable SpawnerSnapshot getById(String id) throws ReflectiveOperationException {
            return snapshot(getSpawnerById.invoke(api, id));
        }

        @SuppressWarnings("unchecked")
        private CompletableFuture<Boolean> remove(String id) throws ReflectiveOperationException {
            Object raw = removeSpawner.invoke(api, id);
            if (!(raw instanceof CompletableFuture<?> future)) {
                throw new IllegalStateException("SmartSpawner removeSpawner(String) did not return CompletableFuture");
            }
            return ((CompletableFuture<Object>) future).thenApply(Boolean.TRUE::equals);
        }

        private @Nullable SpawnerSnapshot snapshot(@Nullable Object dto) throws ReflectiveOperationException {
            if (dto == null) {
                return null;
            }
            Class<?> type = dto.getClass();
            String id = String.valueOf(type.getMethod("getSpawnerId").invoke(dto));
            Object rawLocation = type.getMethod("getLocation").invoke(dto);
            if (!(rawLocation instanceof Location location)) {
                return new SpawnerSnapshot(id, null, "", 0, 0, 0, "", "", 0, false);
            }

            Object rawEntity = type.getMethod("getEntityType").invoke(dto);
            String entity = enumName(rawEntity);
            Object rawItem = type.getMethod("getSpawnedItemMaterial").invoke(dto);
            String item = enumName(rawItem);
            Object rawStack = type.getMethod("getStackSize").invoke(dto);
            int stackSize = rawStack instanceof Number number ? number.intValue() : 0;
            Object rawItemSpawner = type.getMethod("isItemSpawner").invoke(dto);
            boolean itemSpawner = Boolean.TRUE.equals(rawItemSpawner);
            World world = location.getWorld();

            return new SpawnerSnapshot(
                    id,
                    world == null ? null : world.getUID(),
                    world == null ? "" : world.getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    entity,
                    item,
                    stackSize,
                    itemSpawner);
        }

        private String enumName(@Nullable Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof Enum<?> enumValue) {
                return enumValue.name();
            }
            return String.valueOf(value);
        }
    }
}
