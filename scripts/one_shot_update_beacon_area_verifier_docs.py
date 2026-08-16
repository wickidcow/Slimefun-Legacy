from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path('.')

# -----------------------------------------------------------------------------
# Static verifier: protect the NEW Beacon Plus contract rather than the old
# radius/weak-Gravity/3x3-loader-only implementation.
# -----------------------------------------------------------------------------
verifier_path = root / 'scripts/verify_adventurers_curios.py'
verifier = verifier_path.read_text(encoding='utf-8')

verifier = replace_once(
    verifier,
    '''        "beacon_chunk_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusChunkMode.java",
        "beacon_support_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusSupportMode.java",
''',
    '''        "beacon_chunk_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusChunkMode.java",
        "beacon_field_area": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusFieldArea.java",
        "beacon_gravity": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusGravity.java",
        "beacon_support_mode": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusSupportMode.java",
''',
    'verifier file inventory',
)

verifier = replace_once(
    verifier,
    '''            "Configured, but not active:",
            "ACTIVATOR_COVERAGE_SLOT",
''',
    '''            "Configured, but not active:",
            "FIELD_AREA_SLOT",
            "createFieldAreaItem",
            "cycleFieldArea",
            "Affected area:",
            "BeaconPlusFieldArea.DEFAULT.name()",
''',
    'Beacon Plus menu area invariants',
)

verifier = replace_once(
    verifier,
    '''            "MAX_TILE_ENTITIES_PER_PULSE = 96",
            "CROP_SAMPLES_PER_PULSE = 48",
            "EXTRA_RANGE_BLOCKS = 20",
            "world.isChunkLoaded",
''',
    '''            "MAX_TILE_ENTITIES_PER_PULSE = 96",
            "CROP_SAMPLES_PER_CHUNK = 8",
            "getEffectiveFieldArea",
            "getEntitiesInArea",
            "world.isChunkLoaded",
''',
    'runtime area invariants',
)
verifier = replace_once(
    verifier,
    '''            "applyCropBoost(",
            "0.30D + 0.12D * power",
            "Math.max(-0.60D, Math.min(0.60D, pull.getY()))",
''',
    '''            "applyCropBoost(",
            "BeaconPlusGravity.getPullStrength(power)",
            "Math.max(-1.25D, Math.min(1.25D, pull.getY()))",
''',
    'Gravity Well verifier invariants',
)

verifier = replace_once(
    verifier,
    '''        chunk_mode = read(root, files["beacon_chunk_mode"])
        for token in ('OFF("Off", 0, false)', 'SINGLE("This Chunk", 0, true)', 'AREA_3X3("3x3 Area", 1, true)', '"KEEP_CHUNK_LOADED"', '"CHUNK_ACTIVATOR"'):
            require(token in chunk_mode, f"Beacon Plus Activator mode invariant is missing: {token}", failures)

        manager = read(root, files["beacon_manager"])
''',
    '''        chunk_mode = read(root, files["beacon_chunk_mode"])
        for token in (
            'OFF("Off", 0, false)',
            'SINGLE("1x1 Chunks", 0, true)',
            'AREA_3X3("3x3 Chunks", 1, true)',
            'AREA_5X5("5x5 Chunks", 2, true)',
            "forFieldArea",
            '"KEEP_CHUNK_LOADED"',
            '"CHUNK_ACTIVATOR"',
        ):
            require(token in chunk_mode, f"Beacon Plus Activator mode invariant is missing: {token}", failures)

        field_area = read(root, files["beacon_field_area"])
        for token in (
            'CHUNK_1X1("1x1 Chunks", 0)',
            'AREA_3X3("3x3 Chunks", 1)',
            'AREA_5X5("5x5 Chunks", 2)',
            "DEFAULT = AREA_3X3",
            "BeaconPlusFieldArea expand()",
            "containsChunk(",
            'case "AREA_5X5", "5X5", "LARGE" -> AREA_5X5',
        ):
            require(token in field_area, f"Beacon Plus shared effect-area invariant is missing: {token}", failures)

        gravity = read(root, files["beacon_gravity"])
        for token in (
            "NORMAL_PULL = 1.50D",
            "EXTRA_POWER_PULL = 2.10D",
            "getPullStrength(int power)",
        ):
            require(token in gravity, f"Beacon Plus Gravity Well strength invariant is missing: {token}", failures)

        manager = read(root, files["beacon_manager"])
''',
    'chunk/field/gravity verifier block',
)

verifier = replace_once(
    verifier,
    '''            'ITEM_ID = "BEACON_PLUS"',
            "MAX_ACTIVE_BEACONS = 64",
''',
    '''            'ITEM_ID = "BEACON_PLUS"',
            'FIELD_AREA_KEY = "beacon_plus_field_area"',
            "getFieldArea(",
            "BeaconPlusFieldArea.DEFAULT.name()",
            "MAX_ACTIVE_BEACONS = 64",
''',
    'manager field-area invariants',
)

verifier = replace_once(
    verifier,
    '''            "BEACON_PLUS:",
            "enabled: false",
            "maximum **64 active Beacon Plus loaders**",
''',
    '''            "BEACON_PLUS:",
            "enabled: false",
            "Slimefun Electricity",
            "Beacon Blocks",
            "3x3 Chunks",
            "5x5 Chunks",
            "maximum **64 active Beacon Plus loaders**",
''',
    'documentation area/power invariants',
)

verifier = replace_once(
    verifier,
    '''        "- event-driven bonuses require a recently successful paid energy pulse\n"
        "- crop and tile-entity work is capped and unloaded chunks are not scanned for normal field effects\n"
        "- Activator uses reference-counted plugin chunk tickets with 64-beacon and 256-chunk global caps\n"
''',
    '''        "- all 30 effects remain independently toggleable and use a shared 1x1/3x3/5x5 chunk area\n"
        "- 3x3 is the default effect area and Extra Range expands one tier up to 5x5\n"
        "- event-driven bonuses require a recently successful pulse from the selected valid power source\n"
        "- Gravity Well uses the shared effect area and a 5x stronger normal pull than the prior native setting\n"
        "- crop and tile-entity work is capped and unloaded chunks are not scanned for normal field effects\n"
        "- Activator loads the effective effect area with reference-counted tickets and global safety caps\n"
''',
    'verifier success report',
)

verifier_path.write_text(verifier, encoding='utf-8')

# -----------------------------------------------------------------------------
# Admin/player documentation: dual power, shared chunk area, 5x Gravity Well.
# -----------------------------------------------------------------------------
docs_path = root / 'docs/ADVENTURERS_CURIOS.md'
docs = docs_path.read_text(encoding='utf-8')

docs = replace_once(
    docs,
    '''Normal field effects require the Beacon Plus block to sit on a valid vanilla beacon pyramid and have Slimefun Energy available. Player potion effects are applied as normal, non-ambient effects with visible HUD icons and particles so Strength, Resistance, Regeneration, Haste, Speed, Luck, Water Breathing and Jump Boost are obvious while active. Its base field range is the range reported by the vanilla/Paper beacon. **Extra Range** adds 20 blocks. **Extra Power** increases supported effect strength by one tier and is treated as a +50% energy overclock.
''',
    '''The menu has two independent controls in addition to the 30 effect toggles. **Power Source** chooses either **Slimefun Electricity** or **Beacon Blocks**. Electricity mode works without a vanilla pyramid and pays the configured field cost once per second. Beacon Blocks mode uses normal vanilla beacon pyramid/sky activation as its power requirement and consumes no Slimefun Energy. **Effect Area** chooses the chunk-aligned area used by every enabled effect: **1x1 Chunks**, **3x3 Chunks**, or **5x5 Chunks**. New and migrated beacons default to **3x3 Chunks**. Player potion effects are applied as normal, non-ambient effects with visible HUD icons and particles. **Extra Range** expands the selected Effect Area by one tier, capped at 5x5. **Extra Power** increases supported effect strength by one tier and increases electricity-mode field draw by 50%.
''',
    'docs dual power and effect area overview',
)

docs = replace_once(
    docs,
    '''21. **Gravity Well** — pulls hostile mobs and loose item entities toward the beacon at roughly 3× the original native-port force, on a resilient once-per-second cadence.
''',
    '''21. **Gravity Well** — strongly pulls Bukkit mobs, including Endermen and passive/hostile AI mobs, plus loose item entities toward the beacon once per second. Players and armor stands are excluded. Normal pull strength is 1.50 versus the prior 0.30 setting, exactly 5× the previous normal pull; Extra Power raises it to 2.10.
''',
    'docs Gravity Well behavior',
)
docs = replace_once(
    docs,
    '''28. **Extra Range** — adds 20 blocks to the active beacon field range.
29. **Activator** — keeps selected chunks loaded using bounded Paper plugin chunk tickets.
''',
    '''28. **Extra Range** — expands the selected Effect Area by one tier: 1x1 becomes 3x3, 3x3 becomes 5x5, and 5x5 remains capped at 5x5.
29. **Activator** — when enabled, keeps the current effective Effect Area loaded using bounded Paper plugin chunk tickets.
''',
    'docs range and activator behavior',
)

docs = replace_once(
    docs,
    '''- Field work is paid once per staggered one-second pulse.
- Each enabled field effect costs **16 J per pulse** before Extra Power. Extra Power itself and Activator are excluded from the base effect count.
- When **Extra Power** is enabled, the aggregate field-energy cost is multiplied by **1.50**, so the stronger mode uses exactly **50% more machine energy**.
''',
    '''- Field work runs on a staggered one-second pulse.
- In **Slimefun Electricity** mode, each enabled field effect costs **16 J per pulse** before Extra Power. Extra Power itself and Activator are excluded from the base effect count.
- In **Beacon Blocks** mode, a valid vanilla beacon pyramid/sky activation powers the field and no Slimefun Energy is consumed.
- When **Extra Power** is enabled in electricity mode, the aggregate field-energy cost is multiplied by **1.50**, so the stronger mode uses exactly **50% more machine energy**.
''',
    'docs dual power energy balance',
)
docs = replace_once(
    docs,
    '''- If the buffer cannot pay a field pulse, periodic field work is skipped and event-driven Beacon Plus bonuses stop treating that beacon as powered until a later pulse succeeds.
''',
    '''- In electricity mode, if the buffer cannot pay a field pulse, periodic field work is skipped and event-driven Beacon Plus bonuses stop treating that beacon as powered until a later pulse succeeds. Beacon Blocks mode instead depends on the vanilla pyramid/sky power condition.
''',
    'docs power failure behavior',
)

docs = replace_once(
    docs,
    '''### Activator modes and safety

Activator is controlled from the same menu. Coverage can be:

- **Off**
- **This Chunk**
- **3x3 Area**

The loader is deliberately hard-bounded:
''',
    '''### Effect Area, Activator, and safety

**Effect Area** is controlled from the same menu and applies to every enabled Beacon Plus effect. The choices are:

- **1x1 Chunks**
- **3x3 Chunks** — default
- **5x5 Chunks**

Every individual effect remains independently toggleable. The Status item and each effect button display the current affected area. **Extra Range** expands the selected area one tier, capped at 5x5.

**Activator** remains a separate ON/OFF effect. When Activator is enabled, it keeps the current effective Effect Area loaded. Changing Effect Area or Extra Range while Activator is enabled updates the chunk-loader coverage as long as the global safety cap is not exceeded.

The loader is deliberately hard-bounded:
''',
    'docs effect area and activator section',
)
docs = replace_once(
    docs,
    '''- Activator locations and coverage modes persist in `plugins/Slimefun/adventurers-curios-beacons.properties`
''',
    '''- Activator locations/load states persist in `plugins/Slimefun/adventurers-curios-beacons.properties`; the selected Effect Area is stored on the placed Beacon Plus
''',
    'docs persistence wording',
)
docs = replace_once(
    docs,
    '''Historical public mode names `KEEP_CHUNK_LOADED` and `CHUNK_ACTIVATOR` are accepted as migration aliases for **This Chunk**.
''',
    '''Historical public loader mode names `KEEP_CHUNK_LOADED` and `CHUNK_ACTIVATOR` remain accepted as migration aliases for 1x1 loader coverage. Existing Beacon Plus blocks without a stored Effect Area migrate safely to the 3x3 default.
''',
    'docs migration wording',
)
docs = replace_once(
    docs,
    '''- Event-driven effects consult a short-lived powered-state cache populated only after the Beacon Plus successfully pays its energy pulse.
''',
    '''- Event-driven effects consult a short-lived powered-state cache populated only after the Beacon Plus completes a successful pulse using its selected valid power source.
''',
    'docs powered-state wording',
)
docs = replace_once(
    docs,
    '''1. **Diagnose or disable one placed Beacon Plus:** open its menu and read **Beacon Plus Status**. It now reports `ACTIVE` or `NOT POWERED` and explains whether the pyramid or stored Energy is blocking the field. The owner or an operator can click **Disable All Effects**. This also turns its Activator off.
''',
    '''1. **Diagnose or disable one placed Beacon Plus:** open its menu and read **Beacon Plus Status**. It reports the selected Power Source, selected/effective Effect Area, Activator state, `ACTIVE`/`NOT POWERED`, and whether electricity or the Beacon Blocks pyramid condition is blocking the field. The owner or an operator can click **Disable All Effects**. This also turns its Activator off.
''',
    'docs status diagnostics',
)

docs_path.write_text(docs, encoding='utf-8')

# Fail fast on stale documentation/verifier contracts.
verifier_check = verifier_path.read_text(encoding='utf-8')
docs_check = docs_path.read_text(encoding='utf-8')
for stale in (
    'ACTIVATOR_COVERAGE_SLOT',
    'CROP_SAMPLES_PER_PULSE = 48',
    'EXTRA_RANGE_BLOCKS = 20',
    '0.30D + 0.12D * power',
    'SINGLE("This Chunk", 0, true)',
    'AREA_3X3("3x3 Area", 1, true)',
):
    if stale in verifier_check:
        raise SystemExit(f'Stale Beacon Plus verifier invariant survived: {stale}')

for marker in (
    'FIELD_AREA_SLOT',
    'NORMAL_PULL = 1.50D',
    'AREA_5X5("5x5 Chunks", 2, true)',
    '"3x3 Chunks"',
    '"5x5 Chunks"',
):
    if marker not in verifier_check:
        raise SystemExit(f'New Beacon Plus verifier invariant missing: {marker}')

for marker in (
    '**Slimefun Electricity**',
    '**Beacon Blocks**',
    '**3x3 Chunks** — default',
    '**5x5 Chunks**',
    'exactly 5× the previous normal pull',
    'Every individual effect remains independently toggleable.',
):
    if marker not in docs_check:
        raise SystemExit(f'New Beacon Plus documentation marker missing: {marker}')

print('Beacon Plus static verifier and Adventurer Curios documentation updated for shared effect areas and 5x Gravity Well.')
