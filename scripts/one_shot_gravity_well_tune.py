from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path('.')
base = root / 'src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios'

runtime_path = base / 'BeaconPlusRuntime.java'
runtime = runtime_path.read_text(encoding='utf-8')

runtime = replace_once(
    runtime,
    '''    private static final int EXTRA_RANGE_BLOCKS = 20;
    private static final int MAX_TILE_ENTITIES_PER_PULSE = 96;
''',
    '''    private static final int EXTRA_RANGE_BLOCKS = 20;
    private static final double GRAVITY_WELL_MIN_RANGE_BLOCKS = 48.0D;
    private static final double GRAVITY_WELL_RANGE_MULTIPLIER = 3.0D;
    private static final double GRAVITY_WELL_MAX_RANGE_BLOCKS = 96.0D;
    private static final double GRAVITY_WELL_PULL_NORMAL = 1.50D;
    private static final double GRAVITY_WELL_PULL_EXTRA = 2.10D;
    private static final int MAX_TILE_ENTITIES_PER_PULSE = 96;
''',
    'Gravity Well tuning constants',
)

runtime = replace_once(
    runtime,
    '''            if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, effects, power, center);
            }
            if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)
                    && (entity instanceof Mob || entity instanceof Item)) {
                pullEntity(entity, center, power);
            }
        }

        if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) || effects.contains(BeaconPlusEffect.SPAWNERS)) {
''',
    '''            if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, effects, power, center);
            }
        }

        if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)) {
            double gravityWellRange = calculateGravityWellRange(range);
            for (Entity entity : getGravityWellEntities(block, center, gravityWellRange)) {
                pullEntity(entity, center, power);
            }
        }

        if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) || effects.contains(BeaconPlusEffect.SPAWNERS)) {
''',
    'dedicated extended Gravity Well pass',
)

runtime = replace_once(
    runtime,
    '''    private static Collection<Entity> getEntities(Block block, Location center, double range) {
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
    '''    private static Collection<Entity> getEntities(Block block, Location center, double range) {
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

    static double calculateGravityWellRange(double fieldRange) {
        double expanded = Math.max(GRAVITY_WELL_MIN_RANGE_BLOCKS, fieldRange * GRAVITY_WELL_RANGE_MULTIPLIER);
        return Math.min(GRAVITY_WELL_MAX_RANGE_BLOCKS, expanded);
    }

    private static Collection<Entity> getGravityWellEntities(Block block, Location center, double range) {
        if (Slimefun.getSchedulerService().isFolia()) {
            // Cross-region entity access is unsafe on Folia, so keep the existing single-region boundary there.
            List<Entity> result = new ArrayList<>();
            double rangeSquared = range * range;
            for (Entity entity : block.getChunk().getEntities()) {
                if ((entity instanceof Mob || entity instanceof Item)
                        && entity.getLocation().distanceSquared(center) <= rangeSquared) {
                    result.add(entity);
                }
            }
            return result;
        }

        return block.getWorld()
                .getNearbyEntities(
                        center,
                        range,
                        range,
                        range,
                        entity -> entity instanceof Mob || entity instanceof Item);
    }

''',
    'Gravity Well entity collector and range helper',
)

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
        return power > 0 ? GRAVITY_WELL_PULL_EXTRA : GRAVITY_WELL_PULL_NORMAL;
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
    'five-times stronger Gravity Well pull',
)

runtime_path.write_text(runtime, encoding='utf-8')

effect_path = base / 'BeaconPlusEffect.java'
effect = effect_path.read_text(encoding='utf-8')
effect = replace_once(
    effect,
    '"Pull all nearby non-player mobs and loose items toward the beacon once per second."),',
    '"Strongly pull mobs and loose items from an extended 48-96 block well once per second."),',
    'Gravity Well menu description',
)
effect_path.write_text(effect, encoding='utf-8')

test_path = root / 'src/test/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/BeaconPlusEffectTest.java'
test = test_path.read_text(encoding='utf-8')
test = replace_once(
    test,
    '''    @Test
    void electricityCostRulesRemainBoundedAndPredictable() {
''',
    '''    @Test
    void gravityWellUsesExtendedRangeAndFiveTimesPullStrength() {
        assertEquals(48.0D, BeaconPlusRuntime.calculateGravityWellRange(10.0D));
        assertEquals(90.0D, BeaconPlusRuntime.calculateGravityWellRange(30.0D));
        assertEquals(96.0D, BeaconPlusRuntime.calculateGravityWellRange(50.0D));
        assertEquals(1.50D, BeaconPlusRuntime.getGravityWellPullStrength(0));
        assertEquals(2.10D, BeaconPlusRuntime.getGravityWellPullStrength(1));
    }

    @Test
    void electricityCostRulesRemainBoundedAndPredictable() {
''',
    'Gravity Well regression test',
)
test_path.write_text(test, encoding='utf-8')

# Fail fast if any requested behavior failed to install.
runtime_check = runtime_path.read_text(encoding='utf-8')
for marker in (
    'GRAVITY_WELL_MIN_RANGE_BLOCKS = 48.0D',
    'GRAVITY_WELL_RANGE_MULTIPLIER = 3.0D',
    'GRAVITY_WELL_MAX_RANGE_BLOCKS = 96.0D',
    'GRAVITY_WELL_PULL_NORMAL = 1.50D',
    'GRAVITY_WELL_PULL_EXTRA = 2.10D',
    'getGravityWellEntities(block, center, gravityWellRange)',
    'entity -> entity instanceof Mob || entity instanceof Item',
):
    if marker not in runtime_check:
        raise SystemExit(f'Missing Gravity Well tuning marker: {marker}')

print('Gravity Well tuned to 5x pull strength with a dedicated 48-96 block extended range.')
