from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    return text[:start] + replacement + text[end:]


root = Path('.')
base = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'

# -----------------------------------------------------------------------------
# Dual power-source model
# -----------------------------------------------------------------------------
(base / 'BeaconPlusPowerMode.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import org.bukkit.Material;

/** Selects the source used to power Beacon Plus field effects. */
enum BeaconPlusPowerMode {
    SLIMEFUN_ENERGY("Slimefun Electricity", Material.REDSTONE_BLOCK),
    BEACON_BLOCKS("Beacon Blocks", Material.IRON_BLOCK);

    private final String displayName;
    private final Material icon;

    BeaconPlusPowerMode(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    String getDisplayName() {
        return displayName;
    }

    Material getIcon() {
        return icon;
    }

    BeaconPlusPowerMode next() {
        return this == SLIMEFUN_ENERGY ? BEACON_BLOCKS : SLIMEFUN_ENERGY;
    }

    static BeaconPlusPowerMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            // Existing Beacon Plus blocks predate this setting and were energy machines.
            return SLIMEFUN_ENERGY;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "BEACON_BLOCKS", "BLOCKS", "VANILLA", "VANILLA_BLOCKS", "PYRAMID" -> BEACON_BLOCKS;
            case "SLIMEFUN_ENERGY", "SLIMEFUN", "ENERGY", "ELECTRICITY", "ELECTRIC" -> SLIMEFUN_ENERGY;
            default -> SLIMEFUN_ENERGY;
        };
    }
}
''',
    encoding='utf-8',
)

(base / 'BeaconPlusPowerSource.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import org.bukkit.Location;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/** Shared dual-power rules for Beacon Plus. */
final class BeaconPlusPowerSource {

    static final String POWER_MODE_KEY = "beacon_plus_power_mode";
    static final double ENERGY_MODE_BASE_RANGE = 10.0D;

    private BeaconPlusPowerSource() {}

    static BeaconPlusPowerMode getMode(Location location) {
        return BeaconPlusPowerMode.fromStored(StorageCacheUtils.getData(location, POWER_MODE_KEY));
    }

    static void setMode(Location location, BeaconPlusPowerMode mode) {
        StorageCacheUtils.setData(location, POWER_MODE_KEY, mode.name());
    }

    static boolean isSourceReady(Block block, BeaconPlusPowerMode mode) {
        return mode == BeaconPlusPowerMode.SLIMEFUN_ENERGY || getPyramidTier(block) > 0;
    }

    static int getPyramidTier(Block block) {
        BlockState state = block.getState();
        return state instanceof Beacon beacon ? beacon.getTier() : 0;
    }

    static double getBaseRange(Block block) {
        BeaconPlusPowerMode mode = getMode(block.getLocation());
        if (mode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
            return ENERGY_MODE_BASE_RANGE;
        }

        BlockState state = block.getState();
        if (!(state instanceof Beacon beacon) || beacon.getTier() <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, beacon.getEffectRange());
    }
}
''',
    encoding='utf-8',
)

# -----------------------------------------------------------------------------
# Adventurer's Curios: Curiosities + exactly ONE Containment Armor folder.
# Both Advanced Hazmat and Netherite Containment live in the armor folder.
# -----------------------------------------------------------------------------
setup_path = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java'
setup = setup_path.read_text(encoding='utf-8')
setup = setup.replace('import org.bukkit.inventory.meta.LeatherArmorMeta;\n', '')
setup = replace_once(
    setup,
    '''        SubItemGroup advancedHazmatGear = new SubItemGroup(
                new NamespacedKey(plugin, "advanced_hazmat_gear"), curios, createAdvancedHazmatIcon(), 2);
        SubItemGroup containmentArmor = new SubItemGroup(
                new NamespacedKey(plugin, "netherite_containment_armor"), curios, createContainmentArmorIcon(), 2);
''',
    '''        SubItemGroup containmentArmor = new SubItemGroup(
                new NamespacedKey(plugin, "containment_armor"), curios, createContainmentArmorIcon(), 2);
''',
    'single containment armor subgroup',
)

hazmat_group_refs = setup.count('                        advancedHazmatGear,\n')
if hazmat_group_refs != 4:
    raise SystemExit(f'Advanced Hazmat registration refs: expected 4, found {hazmat_group_refs}')
setup = setup.replace('                        advancedHazmatGear,\n', '                        containmentArmor,\n')

setup = replace_between(
    setup,
    '    private static ItemStack createAdvancedHazmatIcon() {\n',
    '    private static ItemStack createContainmentArmorIcon() {\n',
    '',
    'remove Advanced Hazmat folder icon',
)

setup = replace_once(
    setup,
    '''        meta.setDisplayName(ChatColor.DARK_GRAY + "Netherite Containment Armor");
        meta.setLore(List.of(
                ChatColor.GRAY + "For when you need protection from the world",
                ChatColor.GRAY + "while handling sensitive or hazardous materials"));
''',
    '''        meta.setDisplayName(ChatColor.DARK_GRAY + "Containment Armor");
        meta.setLore(List.of(
                ChatColor.GRAY + "Advanced Hazmat and Netherite Containment gear",
                ChatColor.GRAY + "for sensitive, radioactive and hazardous materials"));
''',
    'containment folder title',
)

recipe_refs = setup.count('                        RecipeType.ENHANCED_CRAFTING_TABLE,\n                        containmentRecipe(')
if recipe_refs != 4:
    raise SystemExit(f'Netherite containment recipe type refs: expected 4, found {recipe_refs}')
setup = setup.replace(
    '                        RecipeType.ENHANCED_CRAFTING_TABLE,\n                        containmentRecipe(',
    '                        RecipeType.ARMOR_FORGE,\n                        containmentRecipe(',
)

setup = replace_once(
    setup,
    '''                "&8Field effects require a powered beacon pyramid",
                "&8and Slimefun Energy; Extra Power costs 30 XP levels",
''',
    '''                "&8Choose Slimefun Electricity or Beacon Blocks for power",
                "&8Extra Power costs 30 XP levels",
''',
    'Beacon Plus guide lore',
)
setup_path.write_text(setup, encoding='utf-8')

# -----------------------------------------------------------------------------
# Beacon Plus: source toggle, source-specific runtime and locked GUI.
# -----------------------------------------------------------------------------
beacon_path = base / 'BeaconPlus.java'
beacon = beacon_path.read_text(encoding='utf-8')
beacon = replace_once(
    beacon,
    '''    private static final int STATUS_SLOT = 4;
    private static final int DISABLE_ALL_SLOT = 47;
''',
    '''    private static final int STATUS_SLOT = 4;
    private static final int POWER_SOURCE_SLOT = 45;
    private static final int DISABLE_ALL_SLOT = 47;
''',
    'power source slot',
)
beacon = replace_once(
    beacon,
    '''                StorageCacheUtils.setData(location, EXTRA_POWER_UNLOCKED_KEY, "false");
''',
    '''                StorageCacheUtils.setData(location, EXTRA_POWER_UNLOCKED_KEY, "false");
                BeaconPlusPowerSource.setMode(location, BeaconPlusPowerMode.SLIMEFUN_ENERGY);
''',
    'default power mode',
)
beacon = replace_once(
    beacon,
    '''                event.getPlayer().sendMessage(ChatColor.GOLD + "Beacon Plus placed. " + ChatColor.GRAY
                        + "Build a beacon pyramid, supply Slimefun Energy, then right click it to configure all 30 effects.");
''',
    '''                event.getPlayer().sendMessage(ChatColor.GOLD + "Beacon Plus placed. " + ChatColor.GRAY
                        + "Right click it to choose Slimefun Electricity or Beacon Blocks and configure all 30 effects.");
''',
    'placement message',
)

tick_start = '''            @Override
            public void tick(Block block, SlimefunItem item, ASlimefunDataContainer data) {
'''
tick_end = '''        };
    }

    private void openMenu'''
tick_replacement = '''            @Override
            public void tick(Block block, SlimefunItem item, ASlimefunDataContainer data) {
                BeaconPlusRuntime.observe(block);

                if (!isFieldPulse(block)) {
                    return;
                }

                EnumSet<BeaconPlusEffect> configured =
                        BeaconPlusEffect.parse(data.getData(BeaconPlusRuntime.EFFECTS_KEY));
                int fieldCost = calculateFieldEnergyCost(configured);
                BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());

                if (fieldCost <= 0 || !BeaconPlusPowerSource.isSourceReady(block, powerMode)) {
                    BeaconPlusPowerState.markUnpowered(block.getLocation());
                    BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
                    return;
                }

                if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
                    long stored = getChargeLong(block.getLocation(), data);
                    if (stored < fieldCost) {
                        BeaconPlusPowerState.markUnpowered(block.getLocation());
                        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
                        return;
                    }
                    removeCharge(block.getLocation(), fieldCost, data);
                }

                BeaconPlusPowerState.markPowered(block, data);
                BeaconPlusRuntime.tick(block, data);
                BeaconPlusPowerState.applyInvisibility(block);
            }
'''
beacon = replace_between(beacon, tick_start, tick_end, tick_replacement, 'dual-power ticker')

beacon = replace_once(
    beacon,
    '''        menu.addItem(STATUS_SLOT, createStatusItem(block, enabled, chunkMode));

        BeaconPlusEffect[] effects = BeaconPlusEffect.values();
''',
    '''        menu.addItem(STATUS_SLOT, createStatusItem(block, enabled, chunkMode));
        menu.addMenuClickHandler(STATUS_SLOT, (pl, slot, item, action) -> false);

        BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
        menu.addItem(POWER_SOURCE_SLOT, createPowerSourceItem(powerMode));
        menu.addMenuClickHandler(POWER_SOURCE_SLOT, (pl, slot, item, action) -> {
            togglePowerSource(pl, block, owner);
            return false;
        });

        BeaconPlusEffect[] effects = BeaconPlusEffect.values();
''',
    'locked status and power toggle',
)

activation_start = '''        if (active && effect != BeaconPlusEffect.ACTIVATOR) {
'''
activation_end = '''        openMenu(player, block, owner);
'''
activation_replacement = '''        if (active && effect != BeaconPlusEffect.ACTIVATOR) {
            int requiredEnergy = calculateFieldEnergyCost(enabled);
            BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
            if (!BeaconPlusPowerSource.isSourceReady(block, powerMode)) {
                player.sendMessage(ChatColor.RED
                        + "Configured, but not active: Beacon Blocks mode needs a valid vanilla beacon pyramid and sky activation.");
            } else if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY
                    && requiredEnergy > 0
                    && getChargeLong(block.getLocation()) < requiredEnergy) {
                player.sendMessage(ChatColor.RED + "Configured, but not active: Beacon Plus needs " + requiredEnergy
                        + " J for its next one-second field pulse.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Field is powered by " + ChatColor.WHITE
                        + powerMode.getDisplayName() + ChatColor.GREEN + ".");
            }
        }
'''
act_pos = beacon.find(activation_start)
if act_pos < 0:
    raise SystemExit('effect activation diagnostics start not found')
act_end = beacon.find(activation_end, act_pos)
if act_end < 0:
    raise SystemExit('effect activation diagnostics end not found')
beacon = beacon[:act_pos] + activation_replacement + beacon[act_end:]

toggle_marker = '''    private void toggleActivator(Player player, Block block, UUID owner) {
'''
toggle_method = '''    private void togglePowerSource(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }

        BeaconPlusPowerMode current = BeaconPlusPowerSource.getMode(block.getLocation());
        BeaconPlusPowerMode next = current.next();
        BeaconPlusPowerSource.setMode(block.getLocation(), next);
        BeaconPlusPowerState.markUnpowered(block.getLocation());
        BeaconPlusPowerState.reconcileNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
        BeaconPlusRuntime.observe(block);

        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7F, 1.2F);
        player.sendMessage(ChatColor.GOLD + "Beacon Plus power source: " + ChatColor.WHITE + next.getDisplayName());
        openMenu(player, block, owner);
    }

'''
beacon = replace_once(beacon, toggle_marker, toggle_method + toggle_marker, 'power toggle method')

status_start = '''    private ItemStack createStatusItem(
'''
status_end = '''    private ItemStack createEffectItem(
'''
status_method = '''    private ItemStack createStatusItem(
            Block block, EnumSet<BeaconPlusEffect> enabled, BeaconPlusChunkMode chunkMode) {
        BeaconPlusPowerMode powerMode = BeaconPlusPowerSource.getMode(block.getLocation());
        int tier = BeaconPlusPowerSource.getPyramidTier(block);
        double range = BeaconPlusPowerSource.getBaseRange(block);
        if (enabled.contains(BeaconPlusEffect.EXTRA_RANGE) && range > 0.0D) {
            range += EXTRA_RANGE_BLOCKS;
        }

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
            lore.add(ChatColor.GRAY + "Beacon pyramid tier: "
                    + (tier > 0 ? ChatColor.GREEN : ChatColor.RED) + tier);
            lore.add(ChatColor.DARK_GRAY + "Uses normal vanilla beacon pyramid/sky rules.");
        } else {
            lore.add(ChatColor.GRAY + "Field energy: " + ChatColor.YELLOW + storedEnergy + "/" + ENERGY_CAPACITY + " J");
            lore.add(ChatColor.GRAY + "Current field draw: " + ChatColor.YELLOW + energyCost + " J/s");
        }
        lore.add(ChatColor.GRAY + "Effective field range: " + ChatColor.AQUA + (int) Math.floor(range) + " blocks");
        lore.add(ChatColor.GRAY + "Enabled effects: " + ChatColor.GOLD + effectCount + "/30");

        if (!hasFieldWork) {
            lore.add(ChatColor.GRAY + "Field state: " + ChatColor.YELLOW + "IDLE");
            lore.add(ChatColor.YELLOW + "Reason: no field effect currently needs power.");
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

        lore.add(ChatColor.GRAY + "Activator: " + ChatColor.AQUA + chunkMode.getDisplayName());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Use the Power Source button to switch modes.");
        return createMenuItem(icon, ChatColor.GOLD + "Beacon Plus Status", lore);
    }

'''
beacon = replace_between(beacon, status_start, status_end, status_method, 'status diagnostics')

coverage_marker = '''    private ItemStack createActivatorCoverageItem(BeaconPlusChunkMode chunkMode) {
'''
power_item_method = '''    private ItemStack createPowerSourceItem(BeaconPlusPowerMode powerMode) {
        BeaconPlusPowerMode other = powerMode.next();
        return createMenuItem(
                powerMode.getIcon(),
                ChatColor.GOLD + "Power Source: " + ChatColor.WHITE + powerMode.getDisplayName(),
                List.of(
                        ChatColor.GRAY + "Choose how this Beacon Plus powers all field effects.",
                        "",
                        powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY
                                ? ChatColor.GRAY + "Consumes Slimefun electricity once per second."
                                : ChatColor.GRAY + "Uses a normal vanilla beacon pyramid; no J is consumed.",
                        ChatColor.GRAY + "Switch to: " + ChatColor.AQUA + other.getDisplayName(),
                        "",
                        ChatColor.YELLOW + "Click to switch power source"));
    }

'''
beacon = replace_once(beacon, coverage_marker, power_item_method + coverage_marker, 'power source menu item')

beacon = beacon.replace(
    '''    private static boolean hasPoweredPyramid(Block block) {
        return block.getState() instanceof Beacon beacon && beacon.getTier() > 0;
    }

''',
    '',
)
beacon = beacon.replace('import org.bukkit.block.Beacon;\n', '')
beacon = beacon.replace('import org.bukkit.block.BlockState;\n', '')
beacon_path.write_text(beacon, encoding='utf-8')

# -----------------------------------------------------------------------------
# Runtime: selected source gates all effects. Gravity Well affects every
# non-player LivingEntity (including Endermen/passive mobs) plus loose items,
# once per one-second Beacon Plus field pulse.
# -----------------------------------------------------------------------------
runtime_path = base / 'BeaconPlusRuntime.java'
runtime = runtime_path.read_text(encoding='utf-8')
runtime = replace_between(
    runtime,
    '''    static boolean hasEffect(Location target, BeaconPlusEffect effect) {
''',
    '''    static void tick(Block block, ASlimefunDataContainer data) {
''',
    '''    static boolean hasEffect(Location target, BeaconPlusEffect effect) {
        return BeaconPlusPowerState.hasPoweredEffect(target, effect);
    }

    /**
     * @return -1 when no currently powered Beacon Plus provides the effect, otherwise 0 or 1 for normal/extra power
     */
    static int getPowerForEffect(Location target, BeaconPlusEffect effect) {
        return BeaconPlusPowerState.getPowerForEffect(target, effect);
    }

''',
    'power-gate runtime queries',
)

runtime = replace_once(
    runtime,
    '''        for (Entity entity : entities) {
            if (entity instanceof Player player) {
                applyPlayerEffects(player, effects, power, gameTime);
            } else if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, effects, power, center);
            } else if (effects.contains(BeaconPlusEffect.GRAVITY_WELL) && entity instanceof Item) {
                pullEntity(entity, center, power);
            }
        }
''',
    '''        for (Entity entity : entities) {
            if (entity instanceof Player player) {
                applyPlayerEffects(player, effects, power, gameTime);
                continue;
            }

            if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, effects, power, center);
            }
            if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)
                    && (entity instanceof LivingEntity || entity instanceof Item)) {
                pullEntity(entity, center, power);
            }
        }
''',
    'Gravity Well all mobs',
)
runtime = runtime.replace(
    '''        if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)) {
            pullEntity(monster, center, power);
        }
''',
    '',
)
runtime = replace_between(
    runtime,
    '''    private static double getRange(Block block) {
''',
    '''    private static Collection<Entity> getEntities(Block block, Location center, double range) {
''',
    '''    private static double getRange(Block block) {
        double range = BeaconPlusPowerSource.getBaseRange(block);
        if (range <= 0.0D) {
            return 0.0D;
        }

        if (isEffectEnabled(block.getLocation(), BeaconPlusEffect.EXTRA_RANGE)) {
            range += EXTRA_RANGE_BLOCKS;
        }
        return range;
    }

''',
    'dual-power field range',
)
runtime = runtime.replace('import org.bukkit.block.Beacon;\n', '')
runtime_path.write_text(runtime, encoding='utf-8')

# -----------------------------------------------------------------------------
# Event-driven effects share the same source-valid powered pulse state.
# -----------------------------------------------------------------------------
state_path = base / 'BeaconPlusPowerState.java'
state = state_path.read_text(encoding='utf-8')
state = state.replace(
    'Tracks Beacon Plus locations that successfully paid their most recent field-energy pulse.',
    'Tracks Beacon Plus locations that successfully completed their most recent powered field pulse.',
)
state = state.replace(
    'This keeps event-driven effects tied to real Slimefun Energy consumption without adding a scheduler per beacon.',
    'This keeps event-driven effects tied to the currently selected and valid Beacon Plus power source.',
)
state = replace_between(
    state,
    '''    static void markPowered(Block block, ASlimefunDataContainer data) {
''',
    '''    static void markUnpowered(Location location) {
''',
    '''    static void markPowered(Block block, ASlimefunDataContainer data) {
        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(data.getData(BeaconPlusRuntime.EFFECTS_KEY));
        effects.remove(BeaconPlusEffect.ACTIVATOR);
        if (effects.isEmpty()) {
            markUnpowered(block.getLocation());
            return;
        }

        double range = BeaconPlusPowerSource.getBaseRange(block);
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
    }

''',
    'dual-power event state',
)
state = state.replace(
    '@return -1 if no energy-paid Beacon Plus currently provides the effect, otherwise 0 or 1 for normal/extra power.',
    '@return -1 if no powered Beacon Plus currently provides the effect, otherwise 0 or 1 for normal/extra power.',
)
state = state.replace('import org.bukkit.block.Beacon;\n', '')
state = state.replace('import org.bukkit.block.BlockState;\n', '')
state_path.write_text(state, encoding='utf-8')

# Explicit Gravity Well contract in the menu/effect metadata.
effect_path = base / 'BeaconPlusEffect.java'
effect_text = effect_path.read_text(encoding='utf-8')
effect_text = replace_once(
    effect_text,
    'GRAVITY_WELL("gravity_well", "Gravity Well", Material.HEART_OF_THE_SEA, "Pull nearby non-player entities toward the beacon."),',
    'GRAVITY_WELL("gravity_well", "Gravity Well", Material.HEART_OF_THE_SEA, "Pull all nearby non-player mobs and loose items toward the beacon once per second."),',
    'Gravity Well description',
)
effect_path.write_text(effect_text, encoding='utf-8')

# -----------------------------------------------------------------------------
# Structural regression checks
# -----------------------------------------------------------------------------
setup_check = setup_path.read_text(encoding='utf-8')
if 'advancedHazmatGear' in setup_check or 'createAdvancedHazmatIcon' in setup_check:
    raise SystemExit('Advanced Hazmat separate subgroup survived cleanup')
if setup_check.count('new SubItemGroup(') != 2:
    raise SystemExit('Adventurer Curios must contain exactly two subgroups: Curiosities + Containment Armor')
if setup_check.count('                        containmentArmor,\n') != 8:
    raise SystemExit('All eight armor pieces must be registered in Containment Armor')
if setup_check.count('                        RecipeType.ARMOR_FORGE,\n                        containmentRecipe(') != 4:
    raise SystemExit('All four Netherite Containment pieces must use the Armor Forge')
if '"Containment Armor"' not in setup_check:
    raise SystemExit('Containment Armor folder title missing')

beacon_check = beacon_path.read_text(encoding='utf-8')
for marker in (
    'POWER_SOURCE_SLOT',
    'BeaconPlusPowerSource.getMode',
    'BeaconPlusPowerMode.SLIMEFUN_ENERGY',
    'BeaconPlusPowerMode.BEACON_BLOCKS',
    'createPowerSourceItem',
    'togglePowerSource',
    'addMenuClickHandler(STATUS_SLOT',
    'menu.setPlayerInventoryClickable(false);',
    'menu.setEmptySlotsClickable(false);',
):
    if marker not in beacon_check:
        raise SystemExit(f'Missing Beacon Plus repair marker: {marker}')

runtime_check = runtime_path.read_text(encoding='utf-8')
if '(entity instanceof LivingEntity || entity instanceof Item)' not in runtime_check:
    raise SystemExit('Gravity Well broad mob handling was not installed')
if 'return BeaconPlusPowerState.hasPoweredEffect' not in runtime_check:
    raise SystemExit('Persistent runtime effects are not power-gated')
if 'POWER_PULSE_INTERVAL_TICKS = 20' not in beacon_check:
    raise SystemExit('Beacon field pulse is no longer exactly once per second')

# Check all 30 effect constants are represented by Beacon Plus implementation code.
enum_body = effect_text.split('public enum BeaconPlusEffect {', 1)[1].split(';', 1)[0]
effects = re.findall(r'^\s*([A-Z][A-Z0-9_]*)\(', enum_body, flags=re.M)
if len(effects) != 30:
    raise SystemExit(f'Expected 30 Beacon Plus effects, found {len(effects)}: {effects}')
all_sources = '\n'.join(p.read_text(encoding='utf-8') for p in base.glob('BeaconPlus*.java'))
missing = [name for name in effects if f'BeaconPlusEffect.{name}' not in all_sources]
if missing:
    raise SystemExit(f'Effects without an implementation/configuration reference: {missing}')

print('Beacon Plus dual-power/effects/GUI repair and Containment Armor guide repair applied successfully.')
