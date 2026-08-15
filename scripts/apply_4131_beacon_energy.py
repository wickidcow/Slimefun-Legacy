#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Could not locate {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 4.1.31 release metadata (clean numeric version; no maintenance suffix hacks)
# ---------------------------------------------------------------------------
props = read("gradle.properties")
props = re.sub(r"(?m)^projectVersion=.*$", "projectVersion=4.1.31", props)
write("gradle.properties", props)

readme = read("README.md")
readme = re.sub(
    r"Current development release: \*\*4\.1\.30C? — Core Platform Phase 1L \(Release Lifecycle & Upgrade Safety\)\*\*\.",
    "Current development release: **4.1.31 — Core Platform Phase 1L (Release Lifecycle & Upgrade Safety)**.",
    readme,
)
readme = re.sub(
    r"Slimefun Legacy 4\.1\.30C? is tested primarily against",
    "Slimefun Legacy 4.1.31 is tested primarily against",
    readme,
)
write("README.md", readme)

for rel in (
    "compatibility/support-contract.json",
    "compatibility/addon-compatibility-matrix.json",
    "compatibility/cross-fork-api-matrix.json",
    "compatibility/core-api-registry.json",
):
    data = json.loads(read(rel))
    data["release"] = "4.1.31"
    if rel == "compatibility/support-contract.json":
        policy = data.setdefault("compatibility_policy", {})
        policy["resonance_beacon_optional_energy_operation"] = True
        policy["energy_net_inactive_consumers_preserve_existing_behavior"] = True
    write(rel, json.dumps(data, indent=2) + "\n")

baselines = json.loads(read("compatibility/release-baselines.json"))
baselines.setdefault("candidate", {})["version"] = "4.1.31"
write("compatibility/release-baselines.json", json.dumps(baselines, indent=2) + "\n")

for rel in (
    "scripts/verify_core_platform_phase1l.py",
    "scripts/verify_phase1l_release_artifact.py",
    "scripts/verify_core_platform_phase1l_part3.py",
    "scripts/verify_core_platform_phase1l_part4.py",
):
    text = read(rel).replace("4.1.30", "4.1.31")
    write(rel, text)

release_artifact = read("scripts/verify_release_artifact.py")
release_artifact = release_artifact.replace(
    "4.1.30 release candidate must compare against previous stable 4.1.29",
    "4.1.31 release candidate must compare against previous stable 4.1.29",
)
write("scripts/verify_release_artifact.py", release_artifact)


# ---------------------------------------------------------------------------
# EnergyNetComponent: additive opt-in runtime participation hook.
# Existing components inherit true, so normal machines retain old behavior.
# ---------------------------------------------------------------------------
energy_component_path = "src/main/java/io/github/thebusybiscuit/slimefun4/core/attributes/EnergyNetComponent.java"
energy_component = read(energy_component_path)
energy_hook_anchor = '''    @Nonnull
    EnergyNetComponentType getEnergyComponentType();

'''
energy_hook = '''    @Nonnull
    EnergyNetComponentType getEnergyComponentType();

    /**
     * Returns whether this component should currently participate in its energy network at the supplied block.
     *
     * <p>The default remains {@code true} for full backwards compatibility. Location-aware components may override
     * this to pause network transfer without changing their registered component type or forcing a network rebuild.
     *
     * @param l
     *            The component location
     * @param data
     *            The loaded Slimefun block data
     * @return {@code true} when the component should participate in energy transfer
     */
    default boolean isEnergyNetActive(@Nonnull Location l, @Nonnull ASlimefunDataContainer data) {
        Validate.notNull(l, "Location was null!");
        Validate.notNull(data, "Data container was null!");
        return true;
    }

'''
if "default boolean isEnergyNetActive(" not in energy_component:
    energy_component = replace_once(
        energy_component, energy_hook_anchor, energy_hook, "EnergyNetComponent active hook"
    )
write(energy_component_path, energy_component)

energy_net_path = "src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java"
energy_net = read(energy_net_path)
consumer_anchor = '''                    if (!data.isDataLoaded()) {
                        StorageCacheUtils.requestLoad(data);
                        continue;
                    }

                    long capacity = getSafeCapacity(component, loc);
'''
consumer_replacement = '''                    if (!data.isDataLoaded()) {
                        StorageCacheUtils.requestLoad(data);
                        continue;
                    }

                    if (!component.isEnergyNetActive(loc, data)) {
                        continue;
                    }

                    long capacity = getSafeCapacity(component, loc);
'''
if "if (!component.isEnergyNetActive(loc, data))" not in energy_net:
    energy_net = replace_once(energy_net, consumer_anchor, consumer_replacement, "EnergyNet consumer participation hook")
write(energy_net_path, energy_net)


# ---------------------------------------------------------------------------
# Resonance Beacon energy helper.
# ---------------------------------------------------------------------------
energy_helper = '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.block.Block;

/** Optional native Slimefun-energy operating mode for Resonance Beacons. */
final class BeaconPlusEnergy {

    static final String ELECTRIC_MODE_KEY = "beacon_plus_electric_mode";
    private static final String ENERGY_CHARGE_KEY = "energy-charge";
    private static final long PAID_WINDOW_TICKS = 20L;
    private static final Map<BeaconKey, Long> PAID_UNTIL = new ConcurrentHashMap<>();

    private BeaconPlusEnergy() {}

    static boolean isElectricModeSelected(Location location) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(location, ELECTRIC_MODE_KEY));
    }

    static boolean requiresEnergy(Location location) {
        return BeaconPlusConfig.isElectricOperationEnabled() && isElectricModeSelected(location);
    }

    static void setElectricMode(Location location, boolean enabled) {
        StorageCacheUtils.setData(location, ELECTRIC_MODE_KEY, Boolean.toString(enabled));
        PAID_UNTIL.remove(BeaconKey.from(location));
    }

    static long getStoredCharge(Location location) {
        return parseCharge(StorageCacheUtils.getData(location, ENERGY_CHARGE_KEY));
    }

    static long getDemand(Map<BeaconPlusEffect, Integer> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return 0L;
        }

        long tierSum = 0L;
        for (int tier : tiers.values()) {
            tierSum += Math.max(0, tier);
        }

        long demand = BeaconPlusConfig.getEnergyBaseCostPerPulse()
                + tierSum * BeaconPlusConfig.getEnergyTierCostPerPulse();
        int activatorTier = Math.max(0, tiers.getOrDefault(BeaconPlusEffect.ACTIVATOR, 0));
        demand += (long) activatorTier * BeaconPlusConfig.getEnergyActivatorTierSurchargePerPulse();
        return Math.max(0L, demand);
    }

    static boolean hasOperationalPower(Block block, Map<BeaconPlusEffect, Integer> tiers) {
        Location location = block.getLocation();
        if (!requiresEnergy(location)) {
            return true;
        }

        long demand = getDemand(tiers);
        if (demand <= 0L) {
            return true;
        }

        BeaconKey key = BeaconKey.from(location);
        Long paidUntil = PAID_UNTIL.get(key);
        long gameTime = block.getWorld().getGameTime();
        if (paidUntil != null) {
            if (gameTime <= paidUntil) {
                return true;
            }
            PAID_UNTIL.remove(key, paidUntil);
        }

        return getStoredCharge(location) >= demand;
    }

    static boolean consumePulse(Block block, ASlimefunDataContainer data, Map<BeaconPlusEffect, Integer> tiers) {
        Location location = block.getLocation();
        BeaconKey key = BeaconKey.from(location);
        if (!requiresEnergy(location)) {
            PAID_UNTIL.remove(key);
            return true;
        }

        long demand = getDemand(tiers);
        if (demand <= 0L) {
            PAID_UNTIL.put(key, block.getWorld().getGameTime() + PAID_WINDOW_TICKS);
            return true;
        }

        long charge = parseCharge(data.getData(ENERGY_CHARGE_KEY));
        if (charge < demand) {
            PAID_UNTIL.remove(key);
            return false;
        }

        data.setData(ENERGY_CHARGE_KEY, Long.toString(charge - demand));
        PAID_UNTIL.put(key, block.getWorld().getGameTime() + PAID_WINDOW_TICKS);
        return true;
    }

    static void forget(Location location) {
        PAID_UNTIL.remove(BeaconKey.from(location));
    }

    static void shutdown() {
        PAID_UNTIL.clear();
    }

    private static long parseCharge(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record BeaconKey(UUID worldId, int x, int y, int z) {
        private static BeaconKey from(Location location) {
            return new BeaconKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
'''
write(
    "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusEnergy.java",
    energy_helper,
)


# ---------------------------------------------------------------------------
# Beacon config defaults + accessors.
# ---------------------------------------------------------------------------
config_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusConfig.java"
config = read(config_path)
config_defaults_anchor = '''        config.setDefaultValue(ROOT + ".chunk-loading-enabled", true);
        config.setDefaultValue(ROOT + ".progression.max-tier", MAX_TIER);
'''
config_defaults_replacement = '''        config.setDefaultValue(ROOT + ".chunk-loading-enabled", true);
        config.setDefaultValue(ROOT + ".electric-operation.enabled", true);
        config.setDefaultValue(ROOT + ".electric-operation.capacity", 4096);
        config.setDefaultValue(ROOT + ".electric-operation.base-joules-per-pulse", 16);
        config.setDefaultValue(ROOT + ".electric-operation.tier-joules-per-pulse", 4);
        config.setDefaultValue(ROOT + ".electric-operation.activator-tier-surcharge-joules-per-pulse", 16);
        config.setDefaultValue(ROOT + ".progression.max-tier", MAX_TIER);
'''
if ".electric-operation.capacity" not in config:
    config = replace_once(config, config_defaults_anchor, config_defaults_replacement, "Beacon energy config defaults")

config_access_anchor = '''    static boolean isPowerEnabled(BeaconPlusEffect effect) {
        return effect.isConfigurable() && Slimefun.getCfg().getBoolean(powerPath(effect) + ".enabled");
    }

'''
config_access_replacement = '''    static boolean isPowerEnabled(BeaconPlusEffect effect) {
        return effect.isConfigurable() && Slimefun.getCfg().getBoolean(powerPath(effect) + ".enabled");
    }

    static boolean isElectricOperationEnabled() {
        return Slimefun.getCfg().getBoolean(ROOT + ".electric-operation.enabled");
    }

    static int getEnergyCapacity() {
        int configured = Slimefun.getCfg().getInt(ROOT + ".electric-operation.capacity");
        return configured > 0 ? configured : 4096;
    }

    static int getEnergyBaseCostPerPulse() {
        return Math.max(0, Slimefun.getCfg().getInt(ROOT + ".electric-operation.base-joules-per-pulse"));
    }

    static int getEnergyTierCostPerPulse() {
        return Math.max(0, Slimefun.getCfg().getInt(ROOT + ".electric-operation.tier-joules-per-pulse"));
    }

    static int getEnergyActivatorTierSurchargePerPulse() {
        return Math.max(
                0,
                Slimefun.getCfg().getInt(
                        ROOT + ".electric-operation.activator-tier-surcharge-joules-per-pulse"));
    }

'''
if "static boolean isElectricOperationEnabled()" not in config:
    config = replace_once(config, config_access_anchor, config_access_replacement, "Beacon energy config accessors")
write(config_path, config)


# ---------------------------------------------------------------------------
# Beacon item: native EnergyNet consumer + GUI electric mode control.
# ---------------------------------------------------------------------------
beacon_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java"
beacon = read(beacon_path)
if "core.attributes.EnergyNetComponent;" not in beacon:
    beacon = replace_once(
        beacon,
        "import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;\n",
        "import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;\n"
        "import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;\n"
        "import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;\n",
        "Beacon energy imports",
    )
beacon = beacon.replace("public final class BeaconPlus extends SlimefunItem {", "public final class BeaconPlus extends SlimefunItem implements EnergyNetComponent {")
beacon = replace_once(
    beacon,
    "    private static final int DISABLE_ALL_SLOT = 47;\n",
    "    private static final int ELECTRIC_OPERATION_SLOT = 46;\n    private static final int DISABLE_ALL_SLOT = 47;\n",
    "Beacon electric slot",
)

constructor_anchor = '''    @Override
    public void postRegister() {
'''
energy_methods = '''    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public int getCapacity() {
        return BeaconPlusConfig.getEnergyCapacity();
    }

    @Override
    public long getCapacityLong() {
        return BeaconPlusConfig.getEnergyCapacity();
    }

    @Override
    public boolean isEnergyNetActive(@Nonnull Location location, @Nonnull ASlimefunDataContainer data) {
        return BeaconPlusConfig.isElectricOperationEnabled() && BeaconPlusEnergy.isElectricModeSelected(location);
    }

    @Override
    public void postRegister() {
'''
if "public EnergyNetComponentType getEnergyComponentType()" not in beacon:
    beacon = replace_once(beacon, constructor_anchor, energy_methods, "Beacon EnergyNet methods")

place_anchor = '''                StorageCacheUtils.setData(location, BeaconPlusRuntime.EFFECTS_KEY, "");
                StorageCacheUtils.removeData(location, BeaconPlusLegacyDataStore.IMPORTED_KEY);
'''
place_replacement = '''                StorageCacheUtils.setData(location, BeaconPlusRuntime.EFFECTS_KEY, "");
                StorageCacheUtils.setData(location, BeaconPlusEnergy.ELECTRIC_MODE_KEY, Boolean.FALSE.toString());
                StorageCacheUtils.removeData(location, BeaconPlusLegacyDataStore.IMPORTED_KEY);
'''
if "BeaconPlusEnergy.ELECTRIC_MODE_KEY" not in beacon:
    beacon = replace_once(beacon, place_anchor, place_replacement, "Beacon electric default on placement")

break_anchor = '''            public void onBlockBreak(@Nonnull Block block) {
                BeaconPlusRuntime.forget(block.getLocation());
'''
break_replacement = '''            public void onBlockBreak(@Nonnull Block block) {
                BeaconPlusRuntime.forget(block.getLocation());
                BeaconPlusEnergy.forget(block.getLocation());
'''
if "BeaconPlusEnergy.forget(block.getLocation())" not in beacon:
    beacon = replace_once(beacon, break_anchor, break_replacement, "Beacon energy cleanup")

menu_anchor = '''        menu.addItem(
                DISABLE_ALL_SLOT,
'''
menu_replacement = '''        menu.addItem(ELECTRIC_OPERATION_SLOT, createElectricOperationItem(block));
        menu.addMenuClickHandler(ELECTRIC_OPERATION_SLOT, (pl, slot, item, action) -> {
            if (action.isRightClicked()) {
                toggleElectricOperation(pl, block, owner);
            }
            return false;
        });

        menu.addItem(
                DISABLE_ALL_SLOT,
'''
if "createElectricOperationItem(block)" not in beacon:
    beacon = replace_once(beacon, menu_anchor, menu_replacement, "Beacon electric GUI control")

validate_anchor = '''    private boolean validateMenuAction(Player player, Block block, UUID expectedOwner) {
'''
toggle_method = '''    private void toggleElectricOperation(Player player, Block block, UUID owner) {
        if (!validateMenuAction(player, block, owner)) {
            return;
        }
        if (!BeaconPlusConfig.isElectricOperationEnabled()) {
            player.sendMessage(ChatColor.RED + "Electric Resonance Beacon operation is disabled by the server administrator.");
            openMenu(player, block, owner);
            return;
        }

        boolean enabled = !BeaconPlusEnergy.isElectricModeSelected(block.getLocation());
        BeaconPlusEnergy.setElectricMode(block.getLocation(), enabled);
        BeaconPlusRuntime.reconcileActivator(block);
        BeaconPlusRuntime.refreshPlayerState(player);
        player.playSound(
                block.getLocation(),
                enabled ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE,
                0.65F,
                enabled ? 1.55F : 1.0F);
        player.sendMessage(ChatColor.GOLD + "Resonance Beacon electric operation: "
                + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF")
                + ChatColor.GRAY
                + (enabled
                        ? ". Powers now require Slimefun energy."
                        : ". Powers now use normal pyramid-only operation."));
        openMenu(player, block, owner);
    }

    private boolean validateMenuAction(Player player, Block block, UUID expectedOwner) {
'''
if "private void toggleElectricOperation(" not in beacon:
    beacon = replace_once(beacon, validate_anchor, toggle_method, "Beacon electric toggle handler")

pyramid_method_anchor = '''    private ItemStack createPyramidItem(BeaconPlusPyramid.Profile profile) {
'''
electric_item_method = '''    private ItemStack createElectricOperationItem(Block block) {
        boolean available = BeaconPlusConfig.isElectricOperationEnabled();
        boolean selected = BeaconPlusEnergy.isElectricModeSelected(block.getLocation());
        long charge = BeaconPlusEnergy.getStoredCharge(block.getLocation());
        long capacity = BeaconPlusConfig.getEnergyCapacity();
        long demand = BeaconPlusEnergy.getDemand(BeaconPlusRuntime.getPotentialActiveTiers(block));
        boolean powered = !selected || demand <= 0L || BeaconPlusEnergy.hasOperationalPower(
                block, BeaconPlusRuntime.getPotentialActiveTiers(block));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Optional native Slimefun Energy Network operation.");
        lore.add(ChatColor.GRAY + "Mode: " + (selected ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        lore.add(ChatColor.GRAY + "Charge: " + ChatColor.AQUA + charge + ChatColor.GRAY + "/" + capacity + " J");
        lore.add(ChatColor.GRAY + "Current draw: " + ChatColor.YELLOW + demand + " J/second");
        lore.add(ChatColor.GRAY + "Power state: "
                + (powered ? ChatColor.GREEN + "READY" : ChatColor.RED + "INSUFFICIENT ENERGY"));
        lore.add("");
        if (!available) {
            lore.add(ChatColor.RED + "Disabled by server configuration.");
        } else {
            lore.add(ChatColor.YELLOW + "Right click to turn electric operation " + (selected ? "OFF" : "ON"));
            lore.add(ChatColor.DARK_GRAY + "When ON, all powers pause if charge is too low.");
            lore.add(ChatColor.DARK_GRAY + "Activator chunk tickets release until energy returns.");
        }

        return createMenuItem(
                available ? (selected ? Material.REDSTONE_BLOCK : Material.REDSTONE_TORCH) : Material.BARRIER,
                ChatColor.YELLOW + "Electric Operation",
                lore);
    }

    private ItemStack createPyramidItem(BeaconPlusPyramid.Profile profile) {
'''
if "private ItemStack createElectricOperationItem(" not in beacon:
    beacon = replace_once(beacon, pyramid_method_anchor, electric_item_method, "Beacon electric GUI item")
write(beacon_path, beacon)


# ---------------------------------------------------------------------------
# Runtime: compute potential tiers first, then gate/consume one bounded energy
# payment per 20-tick pulse. Event-driven powers see the same paid window.
# ---------------------------------------------------------------------------
runtime_path = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusRuntime.java"
runtime = read(runtime_path)

tick_old = '''        EnumMap<BeaconPlusEffect, Integer> tiers = getActiveTiers(block);
        reconcileActivator(block, tiers.getOrDefault(BeaconPlusEffect.ACTIVATOR, 0));
        double range = getRange(block, tiers);
'''
tick_new = '''        EnumMap<BeaconPlusEffect, Integer> tiers = getPotentialActiveTiers(block);
        if (!BeaconPlusEnergy.consumePulse(block, data, tiers)) {
            reconcileActivator(block, 0);
            BeaconPlusRuntimeEffects.refreshNearbyPlayerStates(block, 64.0D);
            return;
        }
        reconcileActivator(block, tiers.getOrDefault(BeaconPlusEffect.ACTIVATOR, 0));
        double range = getRange(block, tiers);
'''
if "BeaconPlusEnergy.consumePulse(block, data, tiers)" not in runtime:
    runtime = replace_once(runtime, tick_old, tick_new, "Beacon runtime energy pulse gate")

active_old = '''    private static EnumMap<BeaconPlusEffect, Integer> getActiveTiers(Block block) {
        EnumMap<BeaconPlusEffect, Integer> tiers = new EnumMap<>(BeaconPlusEffect.class);
'''
active_new = '''    private static EnumMap<BeaconPlusEffect, Integer> getActiveTiers(Block block) {
        EnumMap<BeaconPlusEffect, Integer> tiers = getPotentialActiveTiers(block);
        if (tiers.isEmpty() || BeaconPlusEnergy.hasOperationalPower(block, tiers)) {
            return tiers;
        }
        return new EnumMap<>(BeaconPlusEffect.class);
    }

    static EnumMap<BeaconPlusEffect, Integer> getPotentialActiveTiers(Block block) {
        EnumMap<BeaconPlusEffect, Integer> tiers = new EnumMap<>(BeaconPlusEffect.class);
'''
if "static EnumMap<BeaconPlusEffect, Integer> getPotentialActiveTiers(Block block)" not in runtime:
    runtime = replace_once(runtime, active_old, active_new, "Beacon potential tier split")

shutdown_old = '''    static void shutdown() {
        BeaconPlusRuntimeEffects.shutdown();
        OBSERVED_BEACONS.clear();
    }
'''
shutdown_new = '''    static void shutdown() {
        BeaconPlusRuntimeEffects.shutdown();
        BeaconPlusEnergy.shutdown();
        OBSERVED_BEACONS.clear();
    }
'''
if "BeaconPlusEnergy.shutdown();" not in runtime:
    runtime = replace_once(runtime, shutdown_old, shutdown_new, "Beacon energy shutdown cleanup")
write(runtime_path, runtime)


# ---------------------------------------------------------------------------
# config.yml: documented electric-operation defaults.
# ---------------------------------------------------------------------------
yaml_path = "src/main/resources/config.yml"
yaml = read(yaml_path)
yaml_anchor = '''    chunk-loading-enabled: true

    # Unlocks are permanent per beacon owner. EXPERIENCE means Minecraft experience levels.
'''
yaml_replacement = '''    chunk-loading-enabled: true

    # Optional per-beacon Slimefun Energy Network operation. Existing/native/imported beacons default OFF.
    # A player can toggle electric operation from the Resonance Beacon GUI. When ON, powers pause
    # if the internal buffer cannot pay the once-per-second pulse cost; Activator tickets are released.
    electric-operation:
      enabled: true
      capacity: 4096
      base-joules-per-pulse: 16
      tier-joules-per-pulse: 4
      activator-tier-surcharge-joules-per-pulse: 16

    # Unlocks are permanent per beacon owner. EXPERIENCE means Minecraft experience levels.
'''
if "electric-operation:\n      enabled: true" not in yaml:
    yaml = replace_once(yaml, yaml_anchor, yaml_replacement, "config.yml electric-operation section")
write(yaml_path, yaml)


# ---------------------------------------------------------------------------
# Curios documentation + verifier.
# ---------------------------------------------------------------------------
docs_path = "docs/ADVENTURERS_CURIOS.md"
docs = read(docs_path)
docs_anchor = '''Purchasing Tier III never bypasses the physical beacon. The effective tier is capped by the pyramid below the beacon.

### Pyramid resonance
'''
docs_replacement = '''Purchasing Tier III never bypasses the physical beacon. The effective tier is capped by the pyramid below the beacon.

### Optional electric operation

Every Resonance Beacon can optionally operate as a native Slimefun Energy Network consumer. Electric operation is **OFF by default**, including existing and BeaconData-imported beacons, so upgrading does not add an energy requirement to an established beacon.

The owner or an operator can toggle **Electric Operation** from the beacon GUI. When enabled:

- the beacon accepts Slimefun energy through the normal Energy Network and stores it in a 4,096 J buffer by default;
- once per 20-tick runtime pulse it pays a configurable base cost plus a small cost for each active power tier;
- Activator adds a configurable tier surcharge because it can hold 1x1, 3x3, or 5x5 chunks loaded;
- if the buffer cannot pay the current pulse, all powers become dormant without losing selections or purchased tiers;
- Activator chunk tickets are released while unpowered and automatically return when enough energy is available again;
- turning electric operation OFF immediately returns the beacon to normal pyramid/progression-only operation.

The capacity and pulse-cost values are configurable under `SlimefunLegacyAddition.PoweredBeacon.electric-operation`. The energy option does not create a 29th power and never bypasses pyramid tier requirements.

### Pyramid resonance
'''
if "### Optional electric operation" not in docs:
    docs = replace_once(docs, docs_anchor, docs_replacement, "Curios electric-operation docs")
write(docs_path, docs)

verifier_path = "scripts/verify_adventurers_curios.py"
verifier = read(verifier_path)
verifier = replace_once(
    verifier,
    '        "runtime_effects": base + "BeaconPlusRuntimeEffects.java",\n',
    '        "runtime_effects": base + "BeaconPlusRuntimeEffects.java",\n        "energy": base + "BeaconPlusEnergy.java",\n',
    "Curios verifier energy file",
)
verifier = verifier.replace(
    '            "material-power.IRON_BLOCK", "material-power.NETHERITE_BLOCK", "tier-requirements.3",\n',
    '            "material-power.IRON_BLOCK", "material-power.NETHERITE_BLOCK", "tier-requirements.3",\n            "electric-operation.capacity", "base-joules-per-pulse",\n',
)
verifier = verifier.replace(
    '            "flying:\\n        enabled: true", "immortality-field:\\n        enabled: true", "auto-repair:",\n',
    '            "flying:\\n        enabled: true", "immortality-field:\\n        enabled: true", "auto-repair:",\n            "electric-operation:", "capacity: 4096",\n',
)
verifier = verifier.replace(
    '            "BeaconPlusLegacyDataStore.start", "BeaconPlusRuntime.reconcileActivator", "Enabled powers:",\n',
    '            "BeaconPlusLegacyDataStore.start", "BeaconPlusRuntime.reconcileActivator", "Enabled powers:",\n            "EnergyNetComponent", "ELECTRIC_OPERATION_SLOT", "isEnergyNetActive",\n',
)
energy_verify_anchor = '''        mode = read(root, files["mode"])
'''
energy_verify = '''        energy = read(root, files["energy"])
        for token in (
            'ELECTRIC_MODE_KEY = "beacon_plus_electric_mode"', "energy-charge", "getDemand(",
            "consumePulse(", "hasOperationalPower(", "Activator",
        ):
            req(token in energy, f"Resonance Beacon energy invariant missing: {token}", failures)
        req("BeaconPlusEnergy.consumePulse(block, data, tiers)" in runtime,
            "Resonance Beacon runtime must pay electric cost once per pulse", failures)
        req("getPotentialActiveTiers" in runtime,
            "Electric operation must preserve configured tiers while energy-gating active tiers", failures)

        mode = read(root, files["mode"])
'''
if 'energy = read(root, files["energy"])' not in verifier:
    verifier = replace_once(verifier, energy_verify_anchor, energy_verify, "Curios verifier energy checks")
write(verifier_path, verifier)


# ---------------------------------------------------------------------------
# Changelog: prepend the actual 4.1.31 gameplay maintenance build.
# ---------------------------------------------------------------------------
changes_path = "EVERYTHING_THAT_CHANGED.md"
changes = read(changes_path)
if not changes.startswith("# Slimefun Legacy 4.1.31"):
    changes = '''# Slimefun Legacy 4.1.31 — Resonance Beacon & Radiation Gear Update

- Fixed the Resonance Beacon GUI so display-only status, pyramid and controls items cannot be picked up or moved.
- Gravity Well now pulls every Bukkit `Enemy` implementation, including Endermen, plus dropped items while preserving Monster-only debuff behavior.
- Added the Advanced Hazmat Suit: a four-piece Armor Forge upgrade with native Slimefun radiation and bee protection, Protection IV and Unbreaking VI; the helmet retains water breathing and the chestplate retains fire resistance.
- Added optional per-beacon **Electric Operation** using the native Slimefun Energy Network. It is OFF by default for backwards compatibility.
- Electric beacons use a configurable 4,096 J buffer and a bounded once-per-second cost derived from active power tiers. Insufficient energy pauses powers without deleting unlocks or selections.
- Activator chunk tickets are released while an electric beacon is unpowered and resume automatically when energy returns.
- Added a backwards-compatible location-aware EnergyNet participation hook; existing energy components inherit the old always-active behavior.
- Resonance Beacon still has exactly 28 powers; Electric Operation is an operating mode, not a new power.

---

''' + changes
write(changes_path, changes)


# ---------------------------------------------------------------------------
# Remove obsolete 4.1.30C scaffolding now that 4.1.31 is numeric.
# ---------------------------------------------------------------------------
for rel in (
    ".github/workflows/apply-4130c-version-verifiers.yml",
    "scripts/apply_4130c_version_verifier_fix.py",
    "scripts/apply_4130c_readme_version.py",
):
    path = ROOT / rel
    if path.exists():
        path.unlink()


# ---------------------------------------------------------------------------
# Final source-level assertions before Gradle/CI does the real validation.
# ---------------------------------------------------------------------------
checks = {
    "gradle.properties": ["projectVersion=4.1.31"],
    beacon_path: [
        "implements EnergyNetComponent",
        "EnergyNetComponentType.CONSUMER",
        "ELECTRIC_OPERATION_SLOT = 46",
        "createElectricOperationItem(block)",
        "isEnergyNetActive(",
    ],
    runtime_path: [
        "BeaconPlusEnergy.consumePulse(block, data, tiers)",
        "getPotentialActiveTiers(Block block)",
        "BeaconPlusEnergy.hasOperationalPower(block, tiers)",
    ],
    energy_component_path: ["default boolean isEnergyNetActive("],
    energy_net_path: ["if (!component.isEnergyNetActive(loc, data))"],
    config_path: ["electric-operation.capacity", "getEnergyActivatorTierSurchargePerPulse"],
    yaml_path: ["electric-operation:", "capacity: 4096"],
    docs_path: ["### Optional electric operation", "Electric Operation"],
}
for rel, needles in checks.items():
    text = read(rel)
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"4.1.31 verification failed: {needle!r} missing from {rel}")

for rel in (
    "compatibility/support-contract.json",
    "compatibility/addon-compatibility-matrix.json",
    "compatibility/cross-fork-api-matrix.json",
    "compatibility/core-api-registry.json",
):
    if json.loads(read(rel)).get("release") != "4.1.31":
        raise SystemExit(f"4.1.31 release metadata mismatch: {rel}")
if json.loads(read("compatibility/release-baselines.json")).get("candidate", {}).get("version") != "4.1.31":
    raise SystemExit("4.1.31 candidate baseline mismatch")

print("Slimefun Legacy 4.1.31 Resonance Beacon energy update staged successfully")
