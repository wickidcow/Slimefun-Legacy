from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path('.')
base = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'

beacon_path = base / 'BeaconPlus.java'
beacon = beacon_path.read_text(encoding='utf-8')
beacon = replace_once(
    beacon,
    'menu.addItem(slot, createEffectItem(effect, active, chunkMode));',
    'menu.addItem(slot, createEffectItem(effect, active, chunkMode, powerMode));',
    'effect item power-mode call',
)
beacon = replace_once(
    beacon,
    'private ItemStack createEffectItem(BeaconPlusEffect effect, boolean active, BeaconPlusChunkMode chunkMode) {',
    '''private ItemStack createEffectItem(
            BeaconPlusEffect effect,
            boolean active,
            BeaconPlusChunkMode chunkMode,
            BeaconPlusPowerMode powerMode) {''',
    'effect item power-mode signature',
)
beacon = replace_once(
    beacon,
    '''        } else if (effect == BeaconPlusEffect.EXTRA_POWER) {
            lore.add(ChatColor.LIGHT_PURPLE + "One-time unlock: " + ChatColor.WHITE + EXTRA_POWER_XP_LEVEL_COST
                    + " XP levels");
            lore.add(ChatColor.YELLOW + "Energy overclock: +" + EXTRA_POWER_PERCENT + "%");
            lore.add(ChatColor.DARK_GRAY + "Operators bypass the XP unlock cost.");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Base field cost: " + BASE_ENERGY_PER_EFFECT_PER_PULSE + " J/s");
        }
''',
    '''        } else if (effect == BeaconPlusEffect.EXTRA_POWER) {
            lore.add(ChatColor.LIGHT_PURPLE + "One-time unlock: " + ChatColor.WHITE + EXTRA_POWER_XP_LEVEL_COST
                    + " XP levels");
            lore.add(ChatColor.YELLOW + "Supported effect boost: +" + EXTRA_POWER_PERCENT + "%");
            if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
                lore.add(ChatColor.DARK_GRAY + "Electricity draw: +" + EXTRA_POWER_PERCENT + "% while enabled.");
            } else {
                lore.add(ChatColor.DARK_GRAY + "Beacon Blocks mode consumes no electricity.");
            }
            lore.add(ChatColor.DARK_GRAY + "Operators bypass the XP unlock cost.");
        } else if (powerMode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
            lore.add(ChatColor.DARK_GRAY + "Base field cost: " + BASE_ENERGY_PER_EFFECT_PER_PULSE + " J/s");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Powered by beacon blocks; no electricity consumed.");
        }
''',
    'source-aware effect lore',
)
beacon = beacon.replace('private static int calculateFieldEnergyCost(Set<BeaconPlusEffect> configured) {',
                        'static int calculateFieldEnergyCost(Set<BeaconPlusEffect> configured) {')
beacon_path.write_text(beacon, encoding='utf-8')

runtime_path = base / 'BeaconPlusRuntime.java'
runtime = runtime_path.read_text(encoding='utf-8')
runtime = replace_once(
    runtime,
    'import org.bukkit.entity.Monster;\n',
    'import org.bukkit.entity.Mob;\nimport org.bukkit.entity.Monster;\n',
    'Mob import',
)
runtime = replace_once(
    runtime,
    '''            if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)
                    && (entity instanceof LivingEntity || entity instanceof Item)) {
                pullEntity(entity, center, power);
            }
''',
    '''            if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)
                    && (entity instanceof Mob || entity instanceof Item)) {
                pullEntity(entity, center, power);
            }
''',
    'Gravity Well mob targeting',
)
runtime_path.write_text(runtime, encoding='utf-8')

# Add focused regression tests for the new persisted power mode, 30-effect registry,
# and electricity cost rules. The project build executes all tests.
test_base = root / 'src/test/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'
test_base.mkdir(parents=True, exist_ok=True)
(test_base / 'BeaconPlusPowerModeTest.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeaconPlusPowerModeTest {

    @Test
    void legacyAndUnknownBlocksDefaultToSlimefunElectricity() {
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored(null));
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored(""));
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored("not-a-real-mode"));
    }

    @Test
    void persistedAliasesResolveToExpectedPowerSources() {
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored("electricity"));
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored("slimefun energy"));
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.fromStored("beacon blocks"));
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.fromStored("vanilla"));
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.fromStored("pyramid"));
    }

    @Test
    void powerSourceToggleIsTwoWay() {
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.SLIMEFUN_ENERGY.next());
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.BEACON_BLOCKS.next());
    }
}
''',
    encoding='utf-8',
)

(test_base / 'BeaconPlusEffectTest.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class BeaconPlusEffectTest {

    @Test
    void registryStillContainsExactlyThirtyEffects() {
        assertEquals(30, BeaconPlusEffect.values().length);
    }

    @Test
    void allEffectsRoundTripThroughPersistentStorage() {
        EnumSet<BeaconPlusEffect> all = EnumSet.allOf(BeaconPlusEffect.class);
        assertEquals(all, BeaconPlusEffect.parse(BeaconPlusEffect.serialize(all)));
    }

    @Test
    void everyEffectHasRenderableMenuMetadata() {
        for (BeaconPlusEffect effect : BeaconPlusEffect.values()) {
            assertFalse(effect.getId().isBlank(), effect.name());
            assertFalse(effect.getDisplayName().isBlank(), effect.name());
            assertFalse(effect.getDescription().isBlank(), effect.name());
            assertTrue(effect.getIcon().isItem(), effect.name());
        }
    }

    @Test
    void electricityCostRulesRemainBoundedAndPredictable() {
        assertEquals(16, BeaconPlus.calculateFieldEnergyCost(EnumSet.of(BeaconPlusEffect.STRENGTH)));
        assertEquals(0, BeaconPlus.calculateFieldEnergyCost(EnumSet.of(BeaconPlusEffect.ACTIVATOR)));
        assertEquals(0, BeaconPlus.calculateFieldEnergyCost(EnumSet.of(BeaconPlusEffect.EXTRA_POWER)));
        assertEquals(
                24,
                BeaconPlus.calculateFieldEnergyCost(
                        EnumSet.of(BeaconPlusEffect.STRENGTH, BeaconPlusEffect.EXTRA_POWER)));
        assertEquals(
                48,
                BeaconPlus.calculateFieldEnergyCost(
                        EnumSet.of(
                                BeaconPlusEffect.STRENGTH,
                                BeaconPlusEffect.SPEED,
                                BeaconPlusEffect.EXTRA_POWER)));
    }
}
''',
    encoding='utf-8',
)

# Guard the intended Gravity Well target contract in source.
runtime_check = runtime_path.read_text(encoding='utf-8')
if '(entity instanceof Mob || entity instanceof Item)' not in runtime_check:
    raise SystemExit('Gravity Well must target Bukkit mobs and loose items')
if 'entity instanceof LivingEntity || entity instanceof Item' in runtime_check:
    raise SystemExit('Gravity Well still targets all LivingEntity instances')

beacon_check = beacon_path.read_text(encoding='utf-8')
if 'Powered by beacon blocks; no electricity consumed.' not in beacon_check:
    raise SystemExit('Beacon Blocks effect lore still claims an electricity cost')

print('Final Beacon Plus polish and focused regression tests applied.')
