from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, new: str, label: str) -> str:
    a = text.find(start)
    if a < 0:
        raise SystemExit(f"{label}: start marker not found")
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:a] + new + text[b:]


root = Path('.')
base = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'
test_base = root / 'src/test/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'

# -----------------------------------------------------------------------------
# Shared field-area model. Every enabled Beacon Plus effect uses this selection.
# Existing/new beacons default to 3x3. Extra Range expands one tier, capped 5x5.
# -----------------------------------------------------------------------------
(base / 'BeaconPlusFieldArea.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/** Shared chunk-aligned coverage used by every Beacon Plus effect. */
enum BeaconPlusFieldArea {
    CHUNK_1X1("1x1 Chunks", 0),
    AREA_3X3("3x3 Chunks", 1),
    AREA_5X5("5x5 Chunks", 2);

    static final BeaconPlusFieldArea DEFAULT = AREA_3X3;

    private final String displayName;
    private final int radius;

    BeaconPlusFieldArea(String displayName, int radius) {
        this.displayName = displayName;
        this.radius = radius;
    }

    @Nonnull String getDisplayName() {
        return displayName;
    }

    int getRadius() {
        return radius;
    }

    int getChunkCount() {
        int width = radius * 2 + 1;
        return width * width;
    }

    @Nonnull BeaconPlusFieldArea next() {
        return switch (this) {
            case CHUNK_1X1 -> AREA_3X3;
            case AREA_3X3 -> AREA_5X5;
            case AREA_5X5 -> CHUNK_1X1;
        };
    }

    @Nonnull BeaconPlusFieldArea expand() {
        return switch (this) {
            case CHUNK_1X1 -> AREA_3X3;
            case AREA_3X3, AREA_5X5 -> AREA_5X5;
        };
    }

    boolean contains(@Nonnull Location beacon, @Nonnull Location target) {
        if (beacon.getWorld() == null || target.getWorld() == null || !beacon.getWorld().equals(target.getWorld())) {
            return false;
        }
        return containsChunk(beacon.getBlockX(), beacon.getBlockZ(), target.getBlockX(), target.getBlockZ());
    }

    boolean containsChunk(int beaconBlockX, int beaconBlockZ, int targetBlockX, int targetBlockZ) {
        int beaconChunkX = beaconBlockX >> 4;
        int beaconChunkZ = beaconBlockZ >> 4;
        int targetChunkX = targetBlockX >> 4;
        int targetChunkZ = targetBlockZ >> 4;
        return Math.abs(targetChunkX - beaconChunkX) <= radius && Math.abs(targetChunkZ - beaconChunkZ) <= radius;
    }

    static @Nonnull BeaconPlusFieldArea fromStored(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "CHUNK_1X1", "AREA_1X1", "1X1", "SINGLE", "THIS_CHUNK" -> CHUNK_1X1;
            case "AREA_3X3", "3X3", "AREA", "DEFAULT" -> AREA_3X3;
            case "AREA_5X5", "5X5", "LARGE" -> AREA_5X5;
            default -> DEFAULT;
        };
    }
}
''',
    encoding='utf-8',
)

# -----------------------------------------------------------------------------
# Activator chunk-loader modes gain 5x5 coverage. These are loader states only;
# the field-area selection remains independently persisted even when Activator is off.
# -----------------------------------------------------------------------------
chunk_mode_path = base / 'BeaconPlusChunkMode.java'
chunk_mode = chunk_mode_path.read_text(encoding='utf-8')
chunk_mode = replace_once(
    chunk_mode,
    '''    OFF("Off", 0, false),
    SINGLE("This Chunk", 0, true),
    AREA_3X3("3x3 Area", 1, true);
''',
    '''    OFF("Off", 0, false),
    SINGLE("1x1 Chunks", 0, true),
    AREA_3X3("3x3 Chunks", 1, true),
    AREA_5X5("5x5 Chunks", 2, true);
''',
    '5x5 activator mode',
)
chunk_mode = replace_once(
    chunk_mode,
    '''    public @Nonnull BeaconPlusChunkMode next() {
        BeaconPlusChunkMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
''',
    '''    public @Nonnull BeaconPlusChunkMode next() {
        return switch (this) {
            case OFF -> SINGLE;
            case SINGLE -> AREA_3X3;
            case AREA_3X3 -> AREA_5X5;
            case AREA_5X5 -> OFF;
        };
    }

    static @Nonnull BeaconPlusChunkMode forFieldArea(@Nonnull BeaconPlusFieldArea area) {
        return switch (area) {
            case CHUNK_1X1 -> SINGLE;
            case AREA_3X3 -> AREA_3X3;
            case AREA_5X5 -> AREA_5X5;
        };
    }
''',
    'field-area to loader mapping',
)
chunk_mode = replace_once(
    chunk_mode,
    '''            case "AREA", "AREA_3X3", "3X3" -> AREA_3X3;
            default -> OFF;
''',
    '''            case "AREA", "AREA_3X3", "3X3" -> AREA_3X3;
            case "AREA_5X5", "5X5", "LARGE" -> AREA_5X5;
            default -> OFF;
''',
    '5x5 loader migration aliases',
)
chunk_mode_path.write_text(chunk_mode, encoding='utf-8')

# -----------------------------------------------------------------------------
# Manager persists the selected effect area separately from Activator ON/OFF.
# -----------------------------------------------------------------------------
manager_path = base / 'BeaconPlusManager.java'
manager = manager_path.read_text(encoding='utf-8')
manager = replace_once(
    manager,
    '''    public static final String CHUNK_MODE_KEY = "beacon_plus_chunk_mode";
    public static final String SUPPORT_MODE_KEY = "beacon_plus_support_mode";
''',
    '''    public static final String CHUNK_MODE_KEY = "beacon_plus_chunk_mode";
    public static final String FIELD_AREA_KEY = "beacon_plus_field_area";
    public static final String SUPPORT_MODE_KEY = "beacon_plus_support_mode";
''',
    'field area storage key',
)
manager = replace_once(
    manager,
    '''    public synchronized @Nonnull BeaconPlusSupportMode getSupportMode(@Nonnull Location location) {
''',
    '''    public synchronized @Nonnull BeaconPlusFieldArea getFieldArea(@Nonnull Location location) {
        return BeaconPlusFieldArea.fromStored(StorageCacheUtils.getData(location, FIELD_AREA_KEY));
    }

    public synchronized @Nonnull BeaconPlusSupportMode getSupportMode(@Nonnull Location location) {
''',
    'field area getter',
)
manager = replace_once(
    manager,
    '''        String supportMode = data.getData(SUPPORT_MODE_KEY);
        if (supportMode == null || supportMode.isBlank()) {
            data.setData(SUPPORT_MODE_KEY, persisted.supportMode().name());
        }
''',
    '''        String fieldArea = data.getData(FIELD_AREA_KEY);
        if (fieldArea == null || fieldArea.isBlank()) {
            data.setData(FIELD_AREA_KEY, BeaconPlusFieldArea.DEFAULT.name());
        }
        String supportMode = data.getData(SUPPORT_MODE_KEY);
        if (supportMode == null || supportMode.isBlank()) {
            data.setData(SUPPORT_MODE_KEY, persisted.supportMode().name());
        }
''',
    'validate field area migration',
)
manager = replace_once(
    manager,
    '''        data.setData(OWNER_KEY, record.owner().toString());
        data.setData(CHUNK_MODE_KEY, record.chunkMode().name());
        data.setData(SUPPORT_MODE_KEY, record.supportMode().name());
''',
    '''        data.setData(OWNER_KEY, record.owner().toString());
        data.setData(CHUNK_MODE_KEY, record.chunkMode().name());
        String fieldArea = data.getData(FIELD_AREA_KEY);
        if (fieldArea == null || fieldArea.isBlank()) {
            data.setData(FIELD_AREA_KEY, BeaconPlusFieldArea.DEFAULT.name());
        }
        data.setData(SUPPORT_MODE_KEY, record.supportMode().name());
''',
    'persist default field area',
)
manager_path.write_text(manager, encoding='utf-8')

# -----------------------------------------------------------------------------
# Beacon GUI: every effect remains individually toggleable. Add a visible Effect
# Area control. 3x3 is default; 1x1/3x3/5x5 cycle; Extra Range expands one tier.
# Activator loads the current effective area when enabled.
# -----------------------------------------------------------------------------
beacon_path = base / 'BeaconPlus.java'
beacon = beacon_path.read_text(encoding='utf-8')
beacon = replace_once(
    beacon,
    '''    private static final int DISABLE_ALL_SLOT = 47;
    private static final int ACTIVATOR_COVERAGE_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
''',
    '''    private static final int DISABLE_ALL_SLOT = 47;
    private static final int FIELD_AREA_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
''',
    'field area GUI slot',
)
beacon = beacon.replace('    private static final double EXTRA_RANGE_BLOCKS = 20.0D;\n', '')
beacon = replace_once(
    beacon,
    '''                StorageCacheUtils.setData(location, BeaconPlusManager.CHUNK_MODE_KEY, BeaconPlusChunkMode.OFF.name());
                StorageCacheUtils.setData(
                        location, BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());
''',
    '''                StorageCacheUtils.setData(location, BeaconPlusManager.CHUNK_MODE_KEY, BeaconPlusChunkMode.OFF.name());
                StorageCacheUtils.setData(location, BeaconPlusManager.FIELD_AREA_KEY, BeaconPlusFieldArea.DEFAULT.name());
                StorageCacheUtils.setData(
                        location, BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());
''',
    'new beacon 3x3 default',
)
beacon = replace_once(
    beacon,
    '''                                        + "Right click it to choose Slimefun Electricity or Beacon Blocks and configure all 30 effects.");
''',
    '''                                        + "Right click it to configure all 30 effects. Effect coverage defaults to a 3x3 chunk area.");
''',
    'placement area message',
)
beacon = replace_once(
    beacon,
    '''        BeaconPlusChunkMode chunkMode =
                manager == null ? BeaconPlusChunkMode.OFF : manager.getChunkMode(block.getLocation());

        menu.addItem(STATUS_SLOT, createStatusItem(block, enabled, chunkMode));
''',
    '''        BeaconPlusChunkMode chunkMode =
                manager == null ? BeaconPlusChunkMode.OFF : manager.getChunkMode(block.getLocation());
        BeaconPlusFieldArea selectedArea = manager == null
                ? BeaconPlusFieldArea.fromStored(StorageCacheUtils.getData(block.getLocation(), BeaconPlusManager.FIELD_AREA_KEY))
                : manager.getFieldArea(block.getLocation());
        BeaconPlusFieldArea effectiveArea = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), enabled);

        menu.addItem(STATUS_SLOT, createStatusItem(block, enabled, chunkMode, selectedArea, effectiveArea));
''',
    'menu area state',
)
beacon = replace_once(
    beacon,
    '''            menu.addItem(slot, createEffectItem(effect, active, chunkMode, powerMode));
''',
    '''            menu.addItem(slot, createEffectItem(effect, active, chunkMode, powerMode, effectiveArea));
''',
    'effect item area lore',
)
beacon = replace_once(
    beacon,
    '''        menu.addItem(ACTIVATOR_COVERAGE_SLOT, createActivatorCoverageItem(chunkMode));
        menu.addMenuClickHandler(ACTIVATOR_COVERAGE_SLOT, (pl, slot, item, action) -> {
            cycleActivatorCoverage(pl, block, owner);
            return false;
        });
''',
    '''        menu.addItem(FIELD_AREA_SLOT, createFieldAreaItem(selectedArea, effectiveArea, chunkMode));
        menu.addMenuClickHandler(FIELD_AREA_SLOT, (pl, slot, item, action) -> {
            cycleFieldArea(pl, block, owner);
            return false;
        });
''',
    'effect area control',
)

# Extra Range must keep Activator loader coverage aligned with the effective area.
beacon = replace_once(
    beacon,
    '''        if (!enabled.remove(effect)) {
            enabled.add(effect);
        }
        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
''',
    '''        if (!enabled.remove(effect)) {
            enabled.add(effect);
        }

        if (effect == BeaconPlusEffect.EXTRA_RANGE) {
            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager != null && manager.getChunkMode(block.getLocation()) != BeaconPlusChunkMode.OFF) {
                BeaconPlusFieldArea effectiveArea = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), enabled);
                if (!setChunkMode(manager, block, owner, BeaconPlusChunkMode.forFieldArea(effectiveArea))) {
                    player.sendMessage(ChatColor.RED
                            + "Extra Range would make the active chunk coverage exceed the Beacon Plus safety cap.");
                    return;
                }
            }
        }

        BeaconPlusRuntime.setConfiguredEffects(block.getLocation(), enabled);
''',
    'Extra Range loader synchronization',
)

# Activator ON now loads the selected/effective area; 3x3 is therefore the default.
beacon = replace_once(
    beacon,
    '''        BeaconPlusChunkMode current = manager.getChunkMode(block.getLocation());
        BeaconPlusChunkMode next =
                current == BeaconPlusChunkMode.OFF ? BeaconPlusChunkMode.SINGLE : BeaconPlusChunkMode.OFF;
''',
    '''        BeaconPlusChunkMode current = manager.getChunkMode(block.getLocation());
        EnumSet<BeaconPlusEffect> effects = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        BeaconPlusFieldArea effectiveArea = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), effects);
        BeaconPlusChunkMode next = current == BeaconPlusChunkMode.OFF
                ? BeaconPlusChunkMode.forFieldArea(effectiveArea)
                : BeaconPlusChunkMode.OFF;
''',
    'Activator follows field area',
)

# Replace old Activator coverage cycling with the shared Effect Area selector.
beacon = replace_between(
    beacon,
    '''    private void cycleActivatorCoverage(Player player, Block block, UUID owner) {
''',
    '''    private void disableAll(Player player, Block block, UUID owner) {
''',
    '''    private void cycleFieldArea(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            player.sendMessage(ChatColor.RED + "Beacon Plus area controls are not ready.");
            return;
        }

        BeaconPlusFieldArea current = manager.getFieldArea(block.getLocation());
        BeaconPlusFieldArea next = current.next();
        EnumSet<BeaconPlusEffect> effects = BeaconPlusRuntime.getConfiguredEffects(block.getLocation());
        BeaconPlusFieldArea effectiveNext = effects.contains(BeaconPlusEffect.EXTRA_RANGE) ? next.expand() : next;

        if (manager.getChunkMode(block.getLocation()) != BeaconPlusChunkMode.OFF
                && !setChunkMode(manager, block, owner, BeaconPlusChunkMode.forFieldArea(effectiveNext))) {
            player.sendMessage(ChatColor.RED + "The requested effect area would exceed the Beacon Plus chunk safety cap.");
            return;
        }

        StorageCacheUtils.setData(block.getLocation(), BeaconPlusManager.FIELD_AREA_KEY, next.name());
        BeaconPlusPowerState.markUnpowered(block.getLocation());
        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
        BeaconPlusRuntime.observe(block);

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.25F);
        player.sendMessage(ChatColor.AQUA + "Beacon Plus effect area: " + ChatColor.WHITE + next.getDisplayName());
        if (effectiveNext != next) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Extra Range expands the active field to "
                    + ChatColor.WHITE + effectiveNext.getDisplayName() + ChatColor.LIGHT_PURPLE + ".");
        }
        openMenu(player, block, owner);
    }

''',
    'shared field area cycle',
)

# Status is source-aware and now shows selected/effective chunk coverage instead of a block-radius.
beacon = replace_between(
    beacon,
    '''    private ItemStack createStatusItem(Block block, EnumSet<BeaconPlusEffect> enabled, BeaconPlusChunkMode chunkMode) {
''',
    '''    private ItemStack createEffectItem(
''',
    '''    private ItemStack createStatusItem(
            Block block,
            EnumSet<BeaconPlusEffect> enabled,
            BeaconPlusChunkMode chunkMode,
            BeaconPlusFieldArea selectedArea,
            BeaconPlusFieldArea effectiveArea) {
        BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
        int tier = BeaconPlusPowerSource.getPyramidTier(block);
        int effectCount = enabled.size();
        int energyCost = calculateFieldEnergyCost(enabled);
        long storedEnergy = getChargeLong(block.getLocation());
        boolean sourceReady = BeaconPlusPowerSource.isSourceReady(block, powerMode);
        boolean hasFieldWork = energyCost > 0;
        boolean enoughEnergy = powerMode != BeaconPlusPowerMode.SLIMEFUN_ENERGY || storedEnergy >= energyCost;
        boolean fieldReady = hasFieldWork && sourceReady && enoughEnergy;
        Material icon = fieldReady ? Material.NETHER_STAR : powerMode.getIcon();

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Power source: " + ChatColor.AQUA + powerMode.getDisplayName());
        if (powerMode == BeaconPlusPowerMode.BEACON_BLOCKS) {
            lore.add(ChatColor.GRAY + "Beacon pyramid tier: " + (tier > 0 ? ChatColor.GREEN : ChatColor.RED) + tier);
            lore.add(ChatColor.DARK_GRAY + "Uses normal vanilla beacon pyramid/sky rules for power.");
        } else {
            lore.add(ChatColor.GRAY + "Field energy: " + ChatColor.YELLOW + storedEnergy + "/" + ENERGY_CAPACITY + " J");
            lore.add(ChatColor.GRAY + "Current field draw: " + ChatColor.YELLOW + energyCost + " J/s");
        }
        lore.add(ChatColor.GRAY + "Selected effect area: " + ChatColor.AQUA + selectedArea.getDisplayName());
        lore.add(ChatColor.GRAY + "Effective effect area: " + ChatColor.GREEN + effectiveArea.getDisplayName());
        lore.add(ChatColor.GRAY + "Enabled effects: " + ChatColor.GOLD + effectCount + "/30");
        lore.add(ChatColor.GRAY + "Activator chunk loading: "
                + (chunkMode == BeaconPlusChunkMode.OFF ? ChatColor.RED + "OFF" : ChatColor.GREEN + chunkMode.getDisplayName()));

        if (!hasFieldWork) {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.YELLOW + "IDLE");
            lore.add(ChatColor.YELLOW + "Reason: no powered field effect currently needs power.");
        } else if (fieldReady) {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.GREEN + "ACTIVE");
        } else {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.RED + "NOT POWERED");
            if (!sourceReady) {
                lore.add(ChatColor.RED + "Reason: Beacon Blocks mode needs a valid pyramid and sky activation.");
            } else if (!enoughEnergy) {
                lore.add(ChatColor.RED + "Reason: needs at least " + energyCost + " J for the next field pulse.");
            }
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "Use Effect Area to choose 1x1, 3x3 or 5x5 chunks.");
        return createMenuItem(icon, ChatColor.GOLD + "Beacon Plus Status", lore);
    }

''',
    'chunk-area status item',
)

# Add current area to every effect button. Buttons remain individually toggleable.
beacon = replace_once(
    beacon,
    '''            BeaconPlusChunkMode chunkMode,
            BeaconPlusPowerMode powerMode) {
''',
    '''            BeaconPlusChunkMode chunkMode,
            BeaconPlusPowerMode powerMode,
            BeaconPlusFieldArea effectiveArea) {
''',
    'effect item area parameter',
)
beacon = replace_once(
    beacon,
    '''        lore.add(
                ChatColor.GRAY + "Status: " + (shownActive ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        if (effect == BeaconPlusEffect.ACTIVATOR) {
''',
    '''        lore.add(
                ChatColor.GRAY + "Status: " + (shownActive ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        lore.add(ChatColor.GRAY + "Affected area: " + ChatColor.AQUA + effectiveArea.getDisplayName());
        if (effect == BeaconPlusEffect.ACTIVATOR) {
''',
    'effect affected area lore',
)
beacon = replace_once(
    beacon,
    '''            lore.add(ChatColor.GRAY + "Coverage: " + ChatColor.AQUA + chunkMode.getDisplayName());
            lore.add(ChatColor.DARK_GRAY + "Bounded by server-wide loader safety caps.");
''',
    '''            lore.add(ChatColor.GRAY + "Chunk loading: " + ChatColor.AQUA + chunkMode.getDisplayName());
            lore.add(ChatColor.DARK_GRAY + "When enabled, keeps the effective effect area loaded.");
''',
    'Activator area lore',
)

beacon = replace_between(
    beacon,
    '''    private ItemStack createActivatorCoverageItem(BeaconPlusChunkMode chunkMode) {
''',
    '''    static int calculateFieldEnergyCost(Set<BeaconPlusEffect> configured) {
''',
    '''    private ItemStack createFieldAreaItem(
            BeaconPlusFieldArea selectedArea, BeaconPlusFieldArea effectiveArea, BeaconPlusChunkMode chunkMode) {
        return createMenuItem(
                Material.LODESTONE,
                ChatColor.AQUA + "Effect Area: " + ChatColor.WHITE + effectiveArea.getDisplayName(),
                List.of(
                        ChatColor.GRAY + "All enabled Beacon Plus effects use this chunk area.",
                        ChatColor.GRAY + "Selected: " + ChatColor.WHITE + selectedArea.getDisplayName(),
                        ChatColor.GRAY + "Effective: " + ChatColor.GREEN + effectiveArea.getDisplayName(),
                        ChatColor.GRAY + "Activator: "
                                + (chunkMode == BeaconPlusChunkMode.OFF ? ChatColor.RED + "OFF" : ChatColor.GREEN + "ON"),
                        "",
                        ChatColor.GRAY + "1x1 -> 3x3 -> 5x5 -> 1x1",
                        ChatColor.GRAY + "Default: " + ChatColor.AQUA + "3x3 Chunks",
                        ChatColor.DARK_GRAY + "Extra Range expands one tier, capped at 5x5.",
                        "",
                        ChatColor.YELLOW + "Click to change affected area"));
    }

''',
    'field area menu item',
)
beacon_path.write_text(beacon, encoding='utf-8')

# -----------------------------------------------------------------------------
# Runtime: periodic player/mob/tile/crop effects all use the same exact chunk area.
# Gravity Well is exactly 5x the old normal pull (0.30 -> 1.50) and remains one
# pull per one-second field pulse. Players and armor stands are excluded.
# -----------------------------------------------------------------------------
runtime_path = base / 'BeaconPlusRuntime.java'
runtime = runtime_path.read_text(encoding='utf-8')
runtime = runtime.replace('    private static final int EXTRA_RANGE_BLOCKS = 20;\n', '')
runtime = runtime.replace('    private static final int CROP_SAMPLES_PER_PULSE = 48;\n', '    private static final int CROP_SAMPLES_PER_CHUNK = 8;\n')
runtime = replace_once(
    runtime,
    '''        long gameTime = block.getWorld().getGameTime();

        double range = getRange(block);
        if (range <= 0.0D) {
            refreshNearbyPlayerStates(block, 96.0D);
            return;
        }

        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(data.getData(EFFECTS_KEY));
''',
    '''        long gameTime = block.getWorld().getGameTime();

        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(data.getData(EFFECTS_KEY));
''',
    'remove radius-first runtime gate',
)
runtime = replace_once(
    runtime,
    '''        int power = effects.contains(BeaconPlusEffect.EXTRA_POWER) ? 1 : 0;
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        Collection<Entity> entities = getEntities(block, center, range);
''',
    '''        int power = effects.contains(BeaconPlusEffect.EXTRA_POWER) ? 1 : 0;
        BeaconPlusFieldArea area = getEffectiveFieldArea(block.getLocation(), effects);
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        Collection<Entity> entities = getEntitiesInArea(block, area);
''',
    'runtime field area collection',
)
runtime = replace_once(
    runtime,
    '''        if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) || effects.contains(BeaconPlusEffect.SPAWNERS)) {
            applyTileEntityBoosts(block, center, range, effects, power);
        }
        if (effects.contains(BeaconPlusEffect.CROPS) && gameTime % 40L < PULSE_INTERVAL_TICKS) {
            applyCropBoost(block, center, range, power);
        }
''',
    '''        if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) || effects.contains(BeaconPlusEffect.SPAWNERS)) {
            applyTileEntityBoosts(block, area, effects, power);
        }
        if (effects.contains(BeaconPlusEffect.CROPS) && gameTime % 40L < PULSE_INTERVAL_TICKS) {
            applyCropBoost(block, area, power);
        }
''',
    'tile and crop shared area',
)

# Replace radius helpers with exact chunk-aligned field helpers while preserving a radius scan for cleanup only.
runtime = replace_between(
    runtime,
    '''    private static boolean isEffectEnabled(Location location, BeaconPlusEffect effect) {
''',
    '''    private static void applyPlayerEffects(Player player, EnumSet<BeaconPlusEffect> effects, int power, long gameTime) {
''',
    '''    static BeaconPlusFieldArea getEffectiveFieldArea(Location location, Set<BeaconPlusEffect> effects) {
        BeaconPlusFieldArea selected = BeaconPlusFieldArea.fromStored(
                StorageCacheUtils.getData(location, BeaconPlusManager.FIELD_AREA_KEY));
        return effects.contains(BeaconPlusEffect.EXTRA_RANGE) ? selected.expand() : selected;
    }

    private static Collection<Entity> getEntitiesInArea(Block block, BeaconPlusFieldArea area) {
        if (Slimefun.getSchedulerService().isFolia()) {
            // Periodic cross-region entity mutation must stay region-local on Folia.
            return List.of(block.getChunk().getEntities());
        }

        List<Entity> result = new ArrayList<>();
        for (Chunk chunk : getLoadedChunksInArea(block, area)) {
            for (Entity entity : chunk.getEntities()) {
                result.add(entity);
            }
        }
        return result;
    }

    private static Collection<Entity> getEntitiesInRadius(Block block, Location center, double range) {
        if (Slimefun.getSchedulerService().isFolia()) {
            List<Entity> result = new ArrayList<>();
            double rangeSquared = range * range;
            for (Entity entity : block.getChunk().getEntities()) {
                if (entity.getLocation().distanceSquared(center) <= rangeSquared) {
                    result.add(entity);
                }
            }
            return result;
        }
        return block.getWorld().getNearbyEntities(center, range, range, range);
    }

''',
    'shared area runtime helpers',
)
runtime = runtime.replace('for (Entity entity : getEntities(block, center, range)) {', 'for (Entity entity : getEntitiesInRadius(block, center, range)) {', 1)

# Strong Gravity Well pull.
runtime = replace_once(
    runtime,
    '''    private static void pullEntity(Entity entity, Location center, int power) {
        Vector delta = center.toVector().subtract(entity.getLocation().toVector());
        if (delta.lengthSquared() < 0.25D) {
            return;
        }

        Vector pull = delta.normalize().multiply(0.30D + 0.12D * power);
        pull.setY(Math.max(-0.60D, Math.min(0.60D, pull.getY())));
        entity.setVelocity(entity.getVelocity().multiply(0.75D).add(pull));
    }
''',
    '''    static double getGravityWellPullStrength(int power) {
        return power > 0 ? 2.10D : 1.50D;
    }

    private static void pullEntity(Entity entity, Location center, int power) {
        Vector delta = center.toVector().subtract(entity.getLocation().toVector());
        if (delta.lengthSquared() < 0.25D) {
            return;
        }

        Vector pull = delta.normalize().multiply(getGravityWellPullStrength(power));
        pull.setY(Math.max(-1.25D, Math.min(1.25D, pull.getY())));
        entity.setVelocity(entity.getVelocity().multiply(0.75D).add(pull));
    }
''',
    '5x Gravity Well pull',
)

# Tile effects cover every loaded chunk in the selected area, not a circular sub-radius.
runtime = replace_between(
    runtime,
    '''    private static void applyTileEntityBoosts(
''',
    '''    private static void boostFurnace(Furnace furnace, int power) {
''',
    '''    private static void applyTileEntityBoosts(
            Block beaconBlock, BeaconPlusFieldArea area, EnumSet<BeaconPlusEffect> effects, int power) {
        int inspected = 0;
        for (Chunk chunk : getLoadedChunksInArea(beaconBlock, area)) {
            for (BlockState state : chunk.getTileEntities()) {
                if (++inspected > MAX_TILE_ENTITIES_PER_PULSE) {
                    return;
                }

                if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) && state instanceof Furnace furnace) {
                    boostFurnace(furnace, power);
                }
                if (effects.contains(BeaconPlusEffect.SPAWNERS) && state instanceof CreatureSpawner spawner) {
                    boostSpawner(spawner, power);
                }
            }
        }
    }

''',
    'tile entity chunk area',
)

# Crops sample across the entire selected chunk square.
runtime = replace_between(
    runtime,
    '''    private static void applyCropBoost(Block beaconBlock, Location center, double range, int power) {
''',
    '''    private static List<Chunk> getLoadedChunksInRange(Block beaconBlock, double range) {
''',
    '''    private static void applyCropBoost(Block beaconBlock, BeaconPlusFieldArea area, int power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int centerChunkX = beaconBlock.getX() >> 4;
        int centerChunkZ = beaconBlock.getZ() >> 4;
        int radius = area.getRadius();
        int minX = (centerChunkX - radius) << 4;
        int maxX = ((centerChunkX + radius) << 4) + 15;
        int minZ = (centerChunkZ - radius) << 4;
        int maxZ = ((centerChunkZ + radius) << 4) + 15;
        int samples = area.getChunkCount() * (CROP_SAMPLES_PER_CHUNK + power * 4);

        for (int i = 0; i < samples; i++) {
            int x = random.nextInt(minX, maxX + 1);
            int z = random.nextInt(minZ, maxZ + 1);
            int y = beaconBlock.getY() + random.nextInt(-8, 9);

            if (y < beaconBlock.getWorld().getMinHeight() || y >= beaconBlock.getWorld().getMaxHeight()) {
                continue;
            }
            if (!beaconBlock.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            if (Slimefun.getSchedulerService().isFolia()
                    && (x >> 4 != beaconBlock.getX() >> 4 || z >> 4 != beaconBlock.getZ() >> 4)) {
                continue;
            }

            Block target = beaconBlock.getWorld().getBlockAt(x, y, z);
            if (target.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1 + power));
                target.setBlockData(ageable, false);
            }
        }
    }

''',
    'crop chunk area',
)
runtime = replace_between(
    runtime,
    '''    private static List<Chunk> getLoadedChunksInRange(Block beaconBlock, double range) {
''',
    '''    private static void repairInventory(PlayerInventory inventory, int amount) {
''',
    '''    private static List<Chunk> getLoadedChunksInArea(Block beaconBlock, BeaconPlusFieldArea area) {
        List<Chunk> chunks = new ArrayList<>();
        if (Slimefun.getSchedulerService().isFolia()) {
            chunks.add(beaconBlock.getChunk());
            return chunks;
        }

        int centerChunkX = beaconBlock.getX() >> 4;
        int centerChunkZ = beaconBlock.getZ() >> 4;
        int radius = area.getRadius();
        World world = beaconBlock.getWorld();
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                if (world.isChunkLoaded(x, z)) {
                    chunks.add(world.getChunkAt(x, z));
                }
            }
        }
        return chunks;
    }

''',
    'loaded chunks by field area',
)
runtime_path.write_text(runtime, encoding='utf-8')

# -----------------------------------------------------------------------------
# Recently-powered state for event-driven/persistent effects uses the same exact
# chunk area, so cooldown, XP, immortality, flight, scale, etc. agree with the GUI.
# -----------------------------------------------------------------------------
state_path = base / 'BeaconPlusPowerState.java'
state = state_path.read_text(encoding='utf-8')
state = state.replace('    private static final double EXTRA_RANGE_BLOCKS = 20.0D;\n', '')
state = replace_once(
    state,
    '''        double range = BeaconPlusPowerSource.getBaseRange(block);
        if (effects.contains(BeaconPlusEffect.EXTRA_RANGE) && range > 0.0D) {
            range += EXTRA_RANGE_BLOCKS;
        }
        if (range <= 0.0D) {
            markUnpowered(block.getLocation());
            return;
        }

        int power = effects.contains(BeaconPlusEffect.EXTRA_POWER) ? 1 : 0;
        POWERED_BEACONS.put(
                BeaconKey.from(block.getLocation()),
                new PoweredBeacon(System.currentTimeMillis(), EnumSet.copyOf(effects), range, power));
''',
    '''        BeaconPlusFieldArea area = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), effects);
        int power = effects.contains(BeaconPlusEffect.EXTRA_POWER) ? 1 : 0;
        POWERED_BEACONS.put(
                BeaconKey.from(block.getLocation()),
                new PoweredBeacon(System.currentTimeMillis(), EnumSet.copyOf(effects), area, power));
''',
    'powered state stores field area',
)
state = replace_once(
    state,
    '''            if (Slimefun.getSchedulerService().isFolia()
                    && (key.x() >> 4 != target.getBlockX() >> 4 || key.z() >> 4 != target.getBlockZ() >> 4)) {
                continue;
            }
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }

            Location center = new Location(world, key.x() + 0.5D, key.y() + 0.5D, key.z() + 0.5D);
            if (center.distanceSquared(target) > powered.range() * powered.range()) {
                continue;
            }
''',
    '''            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            if (!powered.area().containsChunk(key.x(), key.z(), target.getBlockX(), target.getBlockZ())) {
                continue;
            }
''',
    'event effects exact chunk area',
)
state = replace_once(
    state,
    '''        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        double rangeSquared = powered.range() * powered.range();
        for (Entity entity : getEntities(block, center, powered.range())) {
            if (entity instanceof Player player && player.getLocation().distanceSquared(center) <= rangeSquared) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 0, true, false, true));
            }
        }
''',
    '''        for (Entity entity : getEntitiesInArea(block, powered.area())) {
            if (entity instanceof Player player) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 0, true, false, true));
            }
        }
''',
    'invisibility shared field area',
)
# Keep radius helper for reconciliation and add exact area helper.
state = replace_once(
    state,
    '''    private static Collection<Entity> getEntities(Block block, Location center, double range) {
''',
    '''    private static Collection<Entity> getEntitiesInArea(Block block, BeaconPlusFieldArea area) {
        if (Slimefun.getSchedulerService().isFolia()) {
            return List.of(block.getChunk().getEntities());
        }

        List<Entity> result = new ArrayList<>();
        World world = block.getWorld();
        int centerChunkX = block.getX() >> 4;
        int centerChunkZ = block.getZ() >> 4;
        int radius = area.getRadius();
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    continue;
                }
                for (Entity entity : world.getChunkAt(x, z).getEntities()) {
                    result.add(entity);
                }
            }
        }
        return result;
    }

    private static Collection<Entity> getEntities(Block block, Location center, double range) {
''',
    'power state area entity helper',
)
state = replace_once(
    state,
    '''    private record PoweredBeacon(long paidAtMillis, EnumSet<BeaconPlusEffect> effects, double range, int power) {}
''',
    '''    private record PoweredBeacon(
            long paidAtMillis, EnumSet<BeaconPlusEffect> effects, BeaconPlusFieldArea area, int power) {}
''',
    'powered beacon area record',
)
state_path.write_text(state, encoding='utf-8')

# -----------------------------------------------------------------------------
# Effect text reflects the stronger/shared-area behavior.
# -----------------------------------------------------------------------------
effect_path = base / 'BeaconPlusEffect.java'
effect = effect_path.read_text(encoding='utf-8')
effect = replace_once(
    effect,
    '"Pull all nearby non-player mobs and loose items toward the beacon once per second."),',
    '"Strongly yank mobs and loose items toward the beacon once per second."),',
    'Gravity Well description',
)
effect = replace_once(
    effect,
    'EXTRA_RANGE("extra_range", "Extra Range", Material.SPYGLASS, "Extend Beacon Plus effect radius."),',
    'EXTRA_RANGE("extra_range", "Extra Range", Material.SPYGLASS, "Expand the selected effect area one tier, up to 5x5 chunks."),',
    'Extra Range description',
)
effect = replace_once(
    effect,
    'ACTIVATOR("activator", "Activator", Material.RESPAWN_ANCHOR, "Keep the configured beacon chunks loaded."),',
    'ACTIVATOR("activator", "Activator", Material.RESPAWN_ANCHOR, "Keep the current Beacon Plus effect-area chunks loaded."),',
    'Activator description',
)
effect_path.write_text(effect, encoding='utf-8')

# -----------------------------------------------------------------------------
# Regression tests: 3x3 default, 5x5 max/aliases, loader mapping and 5x gravity.
# -----------------------------------------------------------------------------
(test_base / 'BeaconPlusFieldAreaTest.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeaconPlusFieldAreaTest {

    @Test
    void missingAndUnknownAreaDefaultsToThreeByThree() {
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored(null));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored(""));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored("not-real"));
    }

    @Test
    void areaAliasesAndCycleSupportOneThreeAndFiveByFive() {
        assertEquals(BeaconPlusFieldArea.CHUNK_1X1, BeaconPlusFieldArea.fromStored("1x1"));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored("3x3"));
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.fromStored("5x5"));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.CHUNK_1X1.next());
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.AREA_3X3.next());
        assertEquals(BeaconPlusFieldArea.CHUNK_1X1, BeaconPlusFieldArea.AREA_5X5.next());
    }

    @Test
    void extraRangeExpandsOneTierButNeverBeyondFiveByFive() {
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.CHUNK_1X1.expand());
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.AREA_3X3.expand());
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.AREA_5X5.expand());
    }

    @Test
    void threeByThreeContainsAdjacentChunksButNotTwoChunksAway() {
        assertTrue(BeaconPlusFieldArea.AREA_3X3.containsChunk(0, 0, 16, 0));
        assertTrue(BeaconPlusFieldArea.AREA_3X3.containsChunk(0, 0, -16, 16));
        assertFalse(BeaconPlusFieldArea.AREA_3X3.containsChunk(0, 0, 32, 0));
    }
}
''',
    encoding='utf-8',
)

chunk_test_path = test_base / 'BeaconPlusChunkModeTest.java'
chunk_test = chunk_test_path.read_text(encoding='utf-8')
chunk_test = replace_once(
    chunk_test,
    '''    void chunkModeCycleRemainsBounded() {
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.OFF.next());
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.SINGLE.next());
        assertEquals(BeaconPlusChunkMode.OFF, BeaconPlusChunkMode.AREA_3X3.next());
    }
''',
    '''    void chunkModeCycleRemainsBounded() {
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.OFF.next());
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.SINGLE.next());
        assertEquals(BeaconPlusChunkMode.AREA_5X5, BeaconPlusChunkMode.AREA_3X3.next());
        assertEquals(BeaconPlusChunkMode.OFF, BeaconPlusChunkMode.AREA_5X5.next());
    }

    @Test
    void loaderModeMapsToSharedEffectArea() {
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.forFieldArea(BeaconPlusFieldArea.CHUNK_1X1));
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.forFieldArea(BeaconPlusFieldArea.AREA_3X3));
        assertEquals(BeaconPlusChunkMode.AREA_5X5, BeaconPlusChunkMode.forFieldArea(BeaconPlusFieldArea.AREA_5X5));
        assertEquals(BeaconPlusChunkMode.AREA_5X5, BeaconPlusChunkMode.fromStored("5x5"));
    }
''',
    '5x5 loader tests',
)
chunk_test_path.write_text(chunk_test, encoding='utf-8')

effect_test_path = test_base / 'BeaconPlusEffectTest.java'
effect_test = effect_test_path.read_text(encoding='utf-8')
effect_test = replace_once(
    effect_test,
    '''    @Test
    void electricityCostRulesRemainBoundedAndPredictable() {
''',
    '''    @Test
    void gravityWellIsFiveTimesStrongerThanLegacyNormalPull() {
        assertEquals(1.50D, BeaconPlusRuntime.getGravityWellPullStrength(0));
        assertEquals(2.10D, BeaconPlusRuntime.getGravityWellPullStrength(1));
    }

    @Test
    void electricityCostRulesRemainBoundedAndPredictable() {
''',
    'Gravity Well strength test',
)
effect_test_path.write_text(effect_test, encoding='utf-8')

# -----------------------------------------------------------------------------
# Structural assertions before Gradle runs.
# -----------------------------------------------------------------------------
beacon_check = beacon_path.read_text(encoding='utf-8')
runtime_check = runtime_path.read_text(encoding='utf-8')
state_check = state_path.read_text(encoding='utf-8')
for marker in (
    'FIELD_AREA_SLOT',
    'createFieldAreaItem',
    'cycleFieldArea',
    'BeaconPlusFieldArea.DEFAULT.name()',
    'Affected area:',
    'BeaconPlusChunkMode.forFieldArea',
):
    if marker not in beacon_check:
        raise SystemExit(f'Missing Beacon Plus field-area GUI marker: {marker}')

if beacon_check.count('menu.addMenuClickHandler(slot,') != 1:
    raise SystemExit('Per-effect click handler loop changed unexpectedly; all effects must remain toggleable')
if 'getEntitiesInArea(block, area)' not in runtime_check:
    raise SystemExit('Periodic effects are not using the shared field area')
if 'getGravityWellPullStrength(power)' not in runtime_check:
    raise SystemExit('Gravity Well strong pull was not installed')
if 'powered.area().containsChunk' not in state_check:
    raise SystemExit('Event-driven effects are not using the shared field area')

print('Beacon Plus now keeps all 30 effects individually toggleable, defaults coverage to 3x3 chunks, supports 1x1/3x3/5x5, and uses a 5x stronger Gravity Well pull.')
