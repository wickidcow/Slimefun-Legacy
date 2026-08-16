from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path('.')
setup_path = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersCuriosSetup.java'
setup = setup_path.read_text(encoding='utf-8')

setup = replace_once(
    setup,
    '''import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlus;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.DungeonChalk;\n''',
    '''import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BastionResonator;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlus;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.ChunkStabilizer;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.DungeonChalk;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.EmergencyFlare;\n''',
    'Curios imports A',
)
setup = replace_once(
    setup,
    '''import io.github.thebusybiscuit.slimefun4.implementation.items.curios.MinersCanary;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.StormGlass;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfindersCompass;\n''',
    '''import io.github.thebusybiscuit.slimefun4.implementation.items.curios.MinersCanary;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.StormGlass;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.SurveyorsRod;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfarersLodestone;\nimport io.github.thebusybiscuit.slimefun4.implementation.items.curios.WayfindersCompass;\n''',
    'Curios imports B',
)

new_items = '''        SlimefunItemStack wayfarersLodestone = new SlimefunItemStack(
                "ADVENTURERS_WAYFARERS_LODESTONE",
                Material.LODESTONE,
                "&6Wayfarer's Lodestone",
                "&7Opens a biome travel menu and searches",
                "&7from a randomized probe for safe terrain.",
                "",
                "&eRight Click &7to choose a biome",
                "&eSneak & Right Click &7to cancel an active search",
                "&8Successful travel cooldown: 3 minutes");

        SlimefunItemStack bastionResonator = new SlimefunItemStack(
                "ADVENTURERS_BASTION_RESONATOR",
                Material.RECOVERY_COMPASS,
                "&6Bastion Resonator",
                "&7Tunes itself to the nearest Bastion Remnant",
                "&7without intentionally generating unexplored terrain.",
                "",
                "&eRight Click in the Nether &7to resonate",
                "&8Search radius: 128 chunks");

        SlimefunItemStack emergencyFlare = new SlimefunItemStack(
                "ADVENTURERS_EMERGENCY_FLARE",
                Material.FIREWORK_ROCKET,
                "&cEmergency Flare",
                "&7Launches a bright reusable expedition marker.",
                "&7Modes: Help, Rally Point, and Danger.",
                "",
                "&eRight Click &7to launch",
                "&eSneak & Right Click &7to change mode",
                "&8Marker: ~20 seconds • Cooldown: 45 seconds");

        SlimefunItemStack surveyorsRod = new SlimefunItemStack(
                "ADVENTURERS_SURVEYORS_ROD",
                Material.BLAZE_ROD,
                "&6Surveyor's Rod",
                "&7Reports detailed field, chunk, region,",
                "&7light, biome, entity and block-entity data.",
                "",
                "&eRight Click &7for a chunk survey",
                "&eSneak & Right Click a block &7for block detail");

        SlimefunItemStack chunkStabilizer = new SlimefunItemStack(
                "ADVENTURERS_CHUNK_STABILIZER",
                Material.HEART_OF_THE_SEA,
                "&bChunk Stabilizer",
                "&7Performs a read-only chunk stability scan",
                "&7for entity and block-entity concentrations.",
                "",
                "&eRight Click &7to scan the target chunk",
                "&eSneak & Right Click &7for extra diagnostics",
                "&8Never removes entities or blocks");

'''
setup = replace_once(
    setup,
    '        SlimefunItemStack advancedHazmatHelmet = new SlimefunItemStack(\n',
    new_items + '        SlimefunItemStack advancedHazmatHelmet = new SlimefunItemStack(\n',
    'new Curios item definitions',
)

registrations = '''        new WayfarersLodestone(
                        fieldCuriosities,
                        wayfarersLodestone,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.ENDER_PEARL),
                            new ItemStack(Material.COMPASS),
                            new ItemStack(Material.ENDER_PEARL),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.LODESTONE),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.ECHO_SHARD),
                            new ItemStack(Material.NETHERITE_INGOT),
                            new ItemStack(Material.ECHO_SHARD)
                        })
                .register(plugin);

        new BastionResonator(
                        fieldCuriosities,
                        bastionResonator,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.GILDED_BLACKSTONE),
                            new ItemStack(Material.ECHO_SHARD),
                            new ItemStack(Material.GILDED_BLACKSTONE),
                            new ItemStack(Material.CRYING_OBSIDIAN),
                            new ItemStack(Material.RECOVERY_COMPASS),
                            new ItemStack(Material.CRYING_OBSIDIAN),
                            new ItemStack(Material.GOLD_INGOT),
                            new ItemStack(Material.ENDER_EYE),
                            new ItemStack(Material.GOLD_INGOT)
                        })
                .register(plugin);

        new EmergencyFlare(
                        fieldCuriosities,
                        emergencyFlare,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.GLOWSTONE_DUST),
                            new ItemStack(Material.FIREWORK_STAR),
                            new ItemStack(Material.GLOWSTONE_DUST),
                            new ItemStack(Material.REDSTONE),
                            new ItemStack(Material.FIREWORK_ROCKET),
                            new ItemStack(Material.REDSTONE),
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.GUNPOWDER),
                            new ItemStack(Material.PAPER)
                        })
                .register(plugin);

        new SurveyorsRod(
                        fieldCuriosities,
                        surveyorsRod,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.COPPER_INGOT),
                            new ItemStack(Material.SPYGLASS),
                            new ItemStack(Material.COPPER_INGOT),
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.BLAZE_ROD),
                            new ItemStack(Material.PAPER),
                            new ItemStack(Material.REDSTONE),
                            new ItemStack(Material.COMPASS),
                            new ItemStack(Material.REDSTONE)
                        })
                .register(plugin);

        new ChunkStabilizer(
                        fieldCuriosities,
                        chunkStabilizer,
                        RecipeType.ENHANCED_CRAFTING_TABLE,
                        new ItemStack[] {
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.REDSTONE_BLOCK),
                            new ItemStack(Material.AMETHYST_SHARD),
                            new ItemStack(Material.OBSERVER),
                            new ItemStack(Material.HEART_OF_THE_SEA),
                            new ItemStack(Material.OBSERVER),
                            new ItemStack(Material.IRON_BLOCK),
                            new ItemStack(Material.CLOCK),
                            new ItemStack(Material.IRON_BLOCK)
                        })
                .register(plugin);

'''
setup = replace_once(
    setup,
    '        new HazardProtectionArmorPiece(\n',
    registrations + '        new HazardProtectionArmorPiece(\n',
    'new Curios registrations',
)
setup_path.write_text(setup, encoding='utf-8')

# -----------------------------------------------------------------------------
# Focused static verifier coverage
# -----------------------------------------------------------------------------
verifier_path = root / 'scripts/verify_adventurers_curios.py'
verifier = verifier_path.read_text(encoding='utf-8')
verifier = replace_once(
    verifier,
    '''        "journal": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExpeditionJournal.java",\n        "beacon": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java",\n''',
    '''        "journal": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ExpeditionJournal.java",\n        "wayfarer": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/WayfarersLodestone.java",\n        "bastion": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BastionResonator.java",\n        "flare": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/EmergencyFlare.java",\n        "surveyor": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/SurveyorsRod.java",\n        "stabilizer": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/ChunkStabilizer.java",\n        "beacon": "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlus.java",\n''',
    'verifier file map',
)

verifier = replace_once(
    verifier,
    '''            '"BEACON_PLUS"',\n            "new WayfindersCompass(",\n''',
    '''            '"BEACON_PLUS"',\n            '"ADVENTURERS_WAYFARERS_LODESTONE"',\n            '"ADVENTURERS_BASTION_RESONATOR"',\n            '"ADVENTURERS_EMERGENCY_FLARE"',\n            '"ADVENTURERS_SURVEYORS_ROD"',\n            '"ADVENTURERS_CHUNK_STABILIZER"',\n            "new WayfindersCompass(",\n''',
    'verifier item IDs',
)
verifier = replace_once(
    verifier,
    '''            "new BeaconPlus(",\n            "30 independently toggleable effects",\n''',
    '''            "new BeaconPlus(",\n            "new WayfarersLodestone(",\n            "new BastionResonator(",\n            "new EmergencyFlare(",\n            "new SurveyorsRod(",\n            "new ChunkStabilizer(",\n            "30 independently toggleable effects",\n''',
    'verifier constructors',
)
verifier = verifier.replace(
    'require(setup.count("RecipeType.ENHANCED_CRAFTING_TABLE") >= 8, "All eight Curios need real recipes", failures)',
    'require(setup.count("RecipeType.ENHANCED_CRAFTING_TABLE") >= 13, "All thirteen Curios need real recipes", failures)',
)

journal_block = '''        journal = read(root, files["journal"])
        for token in ("MAX_RECORDED_BIOMES = 128", '"expedition_journal_biomes"', "PersistentDataType.STRING", "getBiome().getKey().getKey()"):
            require(token in journal, f"Expedition Journal invariant is missing: {token}", failures)

'''
new_checks = journal_block + '''        wayfarer = read(root, files["wayfarer"])
        for token in (
            "new ChestMenu(\"&6&lWayfarer's Lodestone\", 54)",
            "SEARCH_RADIUS_BLOCKS = 4_096",
            "RANDOM_PROBE_RADIUS_BLOCKS = 10_000",
            "MAX_SEARCH_PROBES = 3",
            "COOLDOWN_MILLIS = 180_000L",
            "world.locateNearestBiome(",
            "HORIZONTAL_SEARCH_INTERVAL",
            "VERTICAL_SEARCH_INTERVAL",
            "player.teleportAsync(safe)",
            "world.getWorldBorder().isInside(destination)",
            "runAt(probe",
            "runForLater(",
            "player.isSneaking()",
        ):
            require(token in wayfarer, f"Wayfarer's Lodestone invariant is missing: {token}", failures)
        for forbidden in ("loadChunk(", "setChunkForceLoaded", "setForceLoaded"):
            require(forbidden not in wayfarer, f"Wayfarer's Lodestone must not force chunk loading while searching: {forbidden}", failures)

        bastion = read(root, files["bastion"])
        for token in (
            "SEARCH_RADIUS_CHUNKS = 128",
            "COOLDOWN_MILLIS = 30_000L",
            "World.Environment.NETHER",
            "Registry.STRUCTURE_TYPE",
            'NamespacedKey.minecraft("bastion_remnant")',
            "world.locateNearestStructure(origin, bastion, SEARCH_RADIUS_CHUNKS, false)",
            "compassMeta.setLodestone(target)",
            "compassMeta.setLodestoneTracked(false)",
        ):
            require(token in bastion, f"Bastion Resonator invariant is missing: {token}", failures)
        require("loadChunk(" not in bastion, "Bastion Resonator must not generate/load chunks for its search", failures)

        flare = read(root, files["flare"])
        for token in (
            "FLARE_PULSES = 40",
            "PULSE_DELAY_TICKS = 10L",
            "COOLDOWN_MILLIS = 45_000L",
            "FireworkEffect.builder()",
            "Particle.DUST",
            "Particle.END_ROD",
            "runAtLater(origin",
            "FlareMode next()",
            "player.isSneaking()",
        ):
            require(token in flare, f"Emergency Flare invariant is missing: {token}", failures)

        surveyor = read(root, files["surveyor"])
        for token in (
            "Math.floorDiv(chunk.getX(), 32)",
            "Math.floorDiv(chunk.getZ(), 32)",
            "chunk.getEntities()",
            "chunk.getTileEntities()",
            "chunk.isForceLoaded()",
            "block.getLightFromBlocks()",
            "block.getLightFromSky()",
            "world.isChunkLoaded",
            "runAt(target",
            "runFor(",
        ):
            require(token in surveyor, f"Surveyor's Rod invariant is missing: {token}", failures)
        require("loadChunk(" not in surveyor, "Surveyor's Rod must not load chunks for a survey", failures)

        stabilizer = read(root, files["stabilizer"])
        for token in (
            "BUSY_SCORE = 120",
            "HEAVY_SCORE = 300",
            "CRITICAL_SCORE = 800",
            "chunk.getEntities()",
            "chunk.getTileEntities()",
            "instanceof ArmorStand",
            "instanceof Item",
            "instanceof Minecart",
            "Material.HOPPER",
            "calculateScore(",
            "very high armor-stand density",
            "This scan is read-only",
            "world.isChunkLoaded",
        ):
            require(token in stabilizer, f"Chunk Stabilizer invariant is missing: {token}", failures)
        for forbidden in ("entity.remove(", "breakNaturally(", "setType(", "loadChunk("):
            require(forbidden not in stabilizer, f"Chunk Stabilizer must remain read-only: {forbidden}", failures)

'''
verifier = replace_once(verifier, journal_block, new_checks, 'new Curios verifier checks')

verifier = replace_once(
    verifier,
    '''            "No proprietary BeaconPlus runtime classes or source code are copied",\n''',
    '''            "No proprietary BeaconPlus runtime classes or source code are copied",\n            "Wayfarer's Lodestone",\n            "Bastion Resonator",\n            "Emergency Flare",\n            "Surveyor's Rod",\n            "Chunk Stabilizer",\n            "randomized probe",\n            "findUnexplored=false",\n            "read-only",\n''',
    'docs verifier tokens',
)
verifier = verifier.replace(
    '"- eight built-in Curios are registered before registry finalization\\n"',
    '"- thirteen built-in Curios are registered before registry finalization\\n"',
)
verifier_path.write_text(verifier, encoding='utf-8')

# -----------------------------------------------------------------------------
# Documentation
# -----------------------------------------------------------------------------
docs_path = root / 'docs/ADVENTURERS_CURIOS.md'
docs = docs_path.read_text(encoding='utf-8')
insert_marker = '''### Expedition Journal

A player-carried biome log with a bounded number of persistent discoveries.

'''
new_docs = insert_marker + '''### Wayfarer's Lodestone

A reusable biome-travel curio with a locked 54-slot destination menu.

- Presents dimension-appropriate biome choices in the Overworld, Nether, and End.
- Each search starts from a **randomized probe** up to 10,000 blocks from the user's origin, then uses Paper's biome locator with a bounded 4,096-block search radius.
- The search uses at most three probes and does not deliberately brute-force/generate hundreds of chunks.
- After a biome is located, the destination is checked again for solid, non-liquid, non-hazardous standing space and the world border before `teleportAsync` is attempted.
- Successful travel has a three-minute per-player cooldown; failed searches do not consume the cooldown.
- Sneak-right-click cancels an active search.

### Bastion Resonator

A Nether-only structure compass.

- Resolves the modern `minecraft:bastion_remnant` structure type from the server registry.
- Searches up to 128 chunks and calls the structure locator with **findUnexplored=false**, so the item does not intentionally generate unexplored terrain just to find a Bastion.
- Tunes its Recovery Compass lodestone target to the located Bastion and reports direction and approximate distance.
- Uses a 30-second per-player cooldown to prevent structure-locator spam.

### Emergency Flare

A reusable visual expedition signal.

- Sneak-right-click cycles **Help**, **Rally Point**, and **Danger** modes.
- Right-click launches a colored large firework and a matching particle column.
- The particle marker pulses for about 20 seconds and the item has a 45-second cooldown.
- In normal dimensions the flare is moved above local terrain when practical; in the Nether it stays near the user's vertical level rather than being placed above the bedrock roof.

### Surveyor's Rod

A deeper diagnostic companion to the Explorer's Spyglass.

- Right-click reports world coordinates, biome, chunk coordinates, region-file coordinates, surface height, entity count, block-entity count, and force-loaded state.
- Sneak-right-clicking a block reports block type, biome, block light, sky light, and total light.
- It refuses to load an unloaded chunk merely to inspect it.
- Region/chunk work is scheduled at the target location and the immutable report is returned to the player on the player's scheduler.

### Chunk Stabilizer

A read-only stability scanner intended to expose problematic chunks before they become severe FPS/TPS problems.

- Scores total entities, armor stands, dropped items, minecarts, projectiles, XP orbs, block entities, and hoppers.
- Reports **STABLE**, **BUSY**, **HEAVY**, or **CRITICAL** and highlights extreme armor-stand/item/entity concentrations.
- Sneak-right-click includes extra entity-class counts.
- It never removes entities, breaks blocks, changes block types, or loads an unloaded chunk to perform a scan.

'''
docs = replace_once(docs, insert_marker, new_docs, 'Curios docs insertion')
docs = docs.replace('- Emergency Parachute\n', '- Emergency Parachute\n')
docs_path.write_text(docs, encoding='utf-8')

# -----------------------------------------------------------------------------
# Focused unit tests that do not require a live Bukkit server.
# -----------------------------------------------------------------------------
test_path = root / 'src/test/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/CuriosExpeditionUtilityTest.java'
test_path.parent.mkdir(parents=True, exist_ok=True)
test_path.write_text('''package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CuriosExpeditionUtilityTest {

    @Test
    void wayfarerAndBastionSearchesStayBounded() {
        assertEquals(4_096, WayfarersLodestone.SEARCH_RADIUS_BLOCKS);
        assertEquals(10_000, WayfarersLodestone.RANDOM_PROBE_RADIUS_BLOCKS);
        assertEquals(3, WayfarersLodestone.MAX_SEARCH_PROBES);
        assertEquals(180_000L, WayfarersLodestone.COOLDOWN_MILLIS);
        assertEquals(128, BastionResonator.SEARCH_RADIUS_CHUNKS);
        assertEquals(30_000L, BastionResonator.COOLDOWN_MILLIS);
    }

    @Test
    void flareModesCycleAndDefaultSafely() {
        assertEquals(EmergencyFlare.FlareMode.HELP, EmergencyFlare.FlareMode.fromStored(null));
        assertEquals(EmergencyFlare.FlareMode.RALLY, EmergencyFlare.FlareMode.HELP.next());
        assertEquals(EmergencyFlare.FlareMode.DANGER, EmergencyFlare.FlareMode.RALLY.next());
        assertEquals(EmergencyFlare.FlareMode.HELP, EmergencyFlare.FlareMode.DANGER.next());
        assertEquals(EmergencyFlare.FlareMode.HELP, EmergencyFlare.FlareMode.fromStored("not-real"));
    }

    @Test
    void chunkStabilityBandsAndArmorStandWeightAreProtected() {
        assertEquals(ChunkStabilizer.StabilityBand.STABLE, ChunkStabilizer.StabilityBand.fromScore(119));
        assertEquals(ChunkStabilizer.StabilityBand.BUSY, ChunkStabilizer.StabilityBand.fromScore(120));
        assertEquals(ChunkStabilizer.StabilityBand.HEAVY, ChunkStabilizer.StabilityBand.fromScore(300));
        assertEquals(ChunkStabilizer.StabilityBand.CRITICAL, ChunkStabilizer.StabilityBand.fromScore(800));

        int runawayArmorStandScore = ChunkStabilizer.calculateScore(5_000, 5_000, 0, 0, 0, 0, 0, 0);
        assertTrue(runawayArmorStandScore >= ChunkStabilizer.CRITICAL_SCORE);
    }
}
''', encoding='utf-8')

# Final structural assertions before Gradle.
setup_check = setup_path.read_text(encoding='utf-8')
for item_id in (
    'ADVENTURERS_WAYFARERS_LODESTONE',
    'ADVENTURERS_BASTION_RESONATOR',
    'ADVENTURERS_EMERGENCY_FLARE',
    'ADVENTURERS_SURVEYORS_ROD',
    'ADVENTURERS_CHUNK_STABILIZER',
):
    if item_id not in setup_check:
        raise SystemExit(f'Missing new Curios item registration: {item_id}')
if setup_check.count('RecipeType.ENHANCED_CRAFTING_TABLE') < 13:
    raise SystemExit('Expected at least thirteen enhanced-crafting Curios registrations')

print('Expedition utility Curios wired into setup, verifier, docs and tests.')
