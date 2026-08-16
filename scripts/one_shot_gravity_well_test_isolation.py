from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path('.')
base = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'
test_base = root / 'src/test/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'

# Keep Gravity Well tuning independently testable without initializing BeaconPlusRuntime,
# whose static Bukkit NamespacedKey state requires a live Slimefun plugin instance.
(base / 'BeaconPlusGravity.java').write_text(
    '''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

/** Pure Gravity Well tuning values, deliberately independent of Bukkit plugin initialization. */
final class BeaconPlusGravity {

    static final double NORMAL_PULL = 1.50D;
    static final double EXTRA_POWER_PULL = 2.10D;

    private BeaconPlusGravity() {}

    static double getPullStrength(int power) {
        return power > 0 ? EXTRA_POWER_PULL : NORMAL_PULL;
    }
}
''',
    encoding='utf-8',
)

runtime_path = base / 'BeaconPlusRuntime.java'
runtime = runtime_path.read_text(encoding='utf-8')
runtime = replace_once(
    runtime,
    '''    static double getGravityWellPullStrength(int power) {
        return power > 0 ? 2.10D : 1.50D;
    }

''',
    '',
    'remove runtime-owned Gravity Well constants',
)
runtime = replace_once(
    runtime,
    'Vector pull = delta.normalize().multiply(getGravityWellPullStrength(power));',
    'Vector pull = delta.normalize().multiply(BeaconPlusGravity.getPullStrength(power));',
    'runtime Gravity Well helper call',
)
runtime_path.write_text(runtime, encoding='utf-8')

test_path = test_base / 'BeaconPlusEffectTest.java'
test = test_path.read_text(encoding='utf-8')
test = replace_once(
    test,
    '''        assertEquals(1.50D, BeaconPlusRuntime.getGravityWellPullStrength(0));
        assertEquals(2.10D, BeaconPlusRuntime.getGravityWellPullStrength(1));
''',
    '''        assertEquals(1.50D, BeaconPlusGravity.getPullStrength(0));
        assertEquals(2.10D, BeaconPlusGravity.getPullStrength(1));
''',
    'plugin-independent Gravity Well regression test',
)
test_path.write_text(test, encoding='utf-8')

runtime_check = runtime_path.read_text(encoding='utf-8')
test_check = test_path.read_text(encoding='utf-8')
if 'BeaconPlusGravity.getPullStrength(power)' not in runtime_check:
    raise SystemExit('Runtime is not using the isolated Gravity Well helper')
if 'BeaconPlusRuntime.getGravityWellPullStrength' in test_check:
    raise SystemExit('Gravity Well unit test still initializes BeaconPlusRuntime')
if 'BeaconPlusGravity.getPullStrength(0)' not in test_check:
    raise SystemExit('Gravity Well regression test is not using the isolated helper')

print('Gravity Well pull tuning isolated from Bukkit plugin initialization for deterministic unit testing.')
