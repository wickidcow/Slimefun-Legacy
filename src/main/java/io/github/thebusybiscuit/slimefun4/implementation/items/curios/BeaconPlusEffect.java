package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;

/**
 * Toggleable effects exposed by the native Slimefun Legacy Resonance Beacon menu.
 */
public enum BeaconPlusEffect {
    FURNACE_BOOSTER("furnace", "Furnace Booster", Material.FURNACE, "Boost nearby furnace cooking speed."),
    STRENGTH("strength", "Strength Effect", Material.IRON_SWORD, "Give Strength to players in range."),
    REGENERATION("regeneration", "Regeneration Effect", Material.GOLDEN_APPLE, "Regenerate players in range."),
    RESISTANCE("resistance", "Resistance Effect", Material.SHIELD, "Give Resistance to players in range."),
    FAST_DIGGING("fast_digging", "Fast Digging", Material.GOLDEN_PICKAXE, "Give Haste to players in range."),
    CURE("cure", "Cure", Material.MILK_BUCKET, "Cleanse harmful potion effects."),
    CROPS("crops", "Crops", Material.WHEAT, "Accelerate nearby crop growth."),
    SPAWNERS("spawners", "Spawners", Material.SPAWNER, "Reduce nearby spawner delay."),
    SLOWDOWN("slowdown", "Slowdown", Material.SOUL_SAND, "Slow hostile monsters in range."),
    SPEED("speed", "Speed", Material.SUGAR, "Give Speed to players in range."),
    PEACEFUL("peaceful", "Peaceful", Material.WHITE_WOOL, "Stop hostile mobs targeting players in range."),
    NIGHT_VISION("night_vision", "Nightvision", Material.ENDER_EYE, "Give Night Vision to players in range."),
    FLYING("flying", "Flying", Material.FEATHER, "Allow survival flight while inside the field."),
    EXPERIENCE_BOOSTER(
            "experience_booster",
            "Experience Booster",
            Material.EXPERIENCE_BOTTLE,
            "Increase earned experience in range."),
    LUCK("luck", "Luck", Material.RABBIT_FOOT, "Give Luck to players in range."),
    BURNER("burner", "Burner", Material.FLINT_AND_STEEL, "Ignite nearby undead monsters."),
    WATER_BREATHING("water_breathing", "Water Breathing", Material.TURTLE_HELMET, "Give Water Breathing in range."),
    FIRE_EXTINGUISHER(
            "fire_extinguisher", "Fire Extinguisher", Material.WATER_BUCKET, "Extinguish burning players in range."),
    RADIATION_ABSORBER(
            "radiation_absorber",
            "Radiation Absorber",
            Material.HEAVY_CORE,
            "Absorb radiation exposure and suppress radiation symptoms in range."),
    POISON("poison", "Poison", Material.SPIDER_EYE, "Poison nearby hostile monsters."),
    GRAVITY_WELL(
            "gravity_well",
            "Gravity Well",
            Material.HEART_OF_THE_SEA,
            "Pull nearby non-player entities toward the beacon."),
    JUMP("jump", "Jump", Material.RABBIT_FOOT, "Give Jump Boost to players in range."),
    EXP_GAIN("exp_gain", "Exp Gain", Material.SCULK, "Passively grant a small amount of experience."),
    COOLDOWN_REDUCTION(
            "cooldown_reduction", "Cooldown Reduction", Material.CLOCK, "Reduce item cooldowns received in range."),
    IMMORTALITY_FIELD(
            "immortality_field",
            "Immortality Field",
            Material.TOTEM_OF_UNDYING,
            "Chance to prevent otherwise fatal damage."),
    EXTRA_POWER("extra_power", "Extra Power", Material.NETHER_STAR, "Increase the strength of supported effects."),
    EXTRA_RANGE("extra_range", "Extra Range", Material.SPYGLASS, "Extend Resonance Beacon effect radius."),
    ACTIVATOR("activator", "Activator", Material.RESPAWN_ANCHOR, "Keep the configured beacon chunks loaded."),
    AUTO_REPAIR("auto_repair", "Auto Repair", Material.ANVIL, "Slowly repair damaged tools, weapons and armor."),

    /**
     * Migration tombstone only. Scale was present briefly during development but is not an approved Curio power.
     * It is deliberately excluded from parsing, serialization and the configuration menu so old stored values are
     * harmless and disappear the next time the beacon is saved.
     */
    SCALE("scale", "Scale (Legacy Disabled)", Material.BARRIER, "Disabled legacy development value.");

    private static final BeaconPlusEffect[] CONFIGURABLE_VALUES =
            Arrays.stream(values()).filter(BeaconPlusEffect::isConfigurable).toArray(BeaconPlusEffect[]::new);
    private static final Map<String, BeaconPlusEffect> BY_ID = Arrays.stream(CONFIGURABLE_VALUES)
            .collect(Collectors.toUnmodifiableMap(BeaconPlusEffect::getId, effect -> effect));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String description;

    BeaconPlusEffect(String id, String displayName, Material icon, String description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public boolean isConfigurable() {
        return this != SCALE;
    }

    public static BeaconPlusEffect[] configurableValues() {
        return CONFIGURABLE_VALUES.clone();
    }

    public static EnumSet<BeaconPlusEffect> parse(String stored) {
        EnumSet<BeaconPlusEffect> result = EnumSet.noneOf(BeaconPlusEffect.class);
        if (stored == null || stored.isBlank()) {
            return result;
        }

        for (String token : stored.split(",")) {
            BeaconPlusEffect effect = BY_ID.get(token.trim().toLowerCase(Locale.ROOT));
            if (effect != null) {
                result.add(effect);
            }
        }
        return result;
    }

    public static String serialize(Set<BeaconPlusEffect> effects) {
        return effects.stream()
                .filter(BeaconPlusEffect::isConfigurable)
                .map(BeaconPlusEffect::getId)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
