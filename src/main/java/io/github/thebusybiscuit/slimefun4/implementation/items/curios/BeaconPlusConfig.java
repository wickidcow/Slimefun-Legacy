package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Server-owner configuration for the native Adventurer's Curios Resonance Beacon.
 *
 * <p>All settings live under {@code SlimefunLegacyAddition.PoweredBeacon} in Slimefun's normal config.yml.
 */
final class BeaconPlusConfig {

    static final String ROOT = "SlimefunLegacyAddition.PoweredBeacon";
    static final String BEACON_DATA_ROOT = ROOT + ".BeaconData";
    private static final int MAX_TIER = 3;

    private BeaconPlusConfig() {}

    static void installDefaults() {
        Config config = Slimefun.getCfg();
        config.setDefaultValue(ROOT + ".enabled", true);
        config.setDefaultValue(ROOT + ".progression.max-tier", MAX_TIER);
        config.setDefaultValue(ROOT + ".progression.payment-mode", "EXPERIENCE");
        config.setDefaultValue(ROOT + ".progression.creative-bypass-cost", true);
        config.setDefaultValue(ROOT + ".progression.operator-can-sponsor-upgrades", false);

        // Exact BeaconPlus WORLD-storage compatibility: <world>/BeaconData/<chunkX>.<chunkZ>.json
        config.setDefaultValue(BEACON_DATA_ROOT + ".enabled", true);
        config.setDefaultValue(BEACON_DATA_ROOT + ".storage-type", "WORLD");
        config.setDefaultValue(BEACON_DATA_ROOT + ".folder-name", "BeaconData");
        config.setDefaultValue(BEACON_DATA_ROOT + ".import-existing", true);
        config.setDefaultValue(BEACON_DATA_ROOT + ".mirror-native-beacons", true);
        config.setDefaultValue(BEACON_DATA_ROOT + ".bootstrap-legacy-activators", true);
        config.setDefaultValue(BEACON_DATA_ROOT + ".honor-overridden-range", true);

        config.setDefaultValue(ROOT + ".pyramid.material-power.IRON_BLOCK", 1.0D);
        config.setDefaultValue(ROOT + ".pyramid.material-power.GOLD_BLOCK", 2.0D);
        config.setDefaultValue(ROOT + ".pyramid.material-power.EMERALD_BLOCK", 3.0D);
        config.setDefaultValue(ROOT + ".pyramid.material-power.DIAMOND_BLOCK", 4.0D);
        config.setDefaultValue(ROOT + ".pyramid.material-power.NETHERITE_BLOCK", 5.0D);

        config.setDefaultValue(ROOT + ".pyramid.tier-requirements.1.min-pyramid-tier", 1);
        config.setDefaultValue(ROOT + ".pyramid.tier-requirements.1.min-average-material-power", 1.0D);
        config.setDefaultValue(ROOT + ".pyramid.tier-requirements.2.min-pyramid-tier", 2);
        config.setDefaultValue(ROOT + ".pyramid.tier-requirements.2.min-average-material-power", 3.0D);
        config.setDefaultValue(ROOT + ".pyramid.tier-requirements.3.min-pyramid-tier", 3);
        config.setDefaultValue(ROOT + ".pyramid.tier-requirements.3.min-average-material-power", 4.0D);

        for (BeaconPlusEffect effect : BeaconPlusEffect.configurableValues()) {
            String path = powerPath(effect);
            boolean enabledByDefault = effect != BeaconPlusEffect.FLYING && effect != BeaconPlusEffect.IMMORTALITY_FIELD;
            config.setDefaultValue(path + ".enabled", enabledByDefault);
            config.setDefaultValue(path + ".payment-mode", "INHERIT");
            for (int tier = 1; tier <= MAX_TIER; tier++) {
                config.setDefaultValue(path + ".experience-costs.tier-" + tier, defaultExperienceCost(effect, tier));
                config.setDefaultValue(path + ".money-costs.tier-" + tier, defaultMoneyCost(effect, tier));
            }
        }

        config.save();
    }

    static boolean isEnabled() {
        return Slimefun.getCfg().getBoolean(ROOT + ".enabled");
    }

    static boolean isPowerEnabled(BeaconPlusEffect effect) {
        return effect.isConfigurable() && Slimefun.getCfg().getBoolean(powerPath(effect) + ".enabled");
    }

    static int getMaxTier() {
        return clamp(Slimefun.getCfg().getInt(ROOT + ".progression.max-tier"), 1, MAX_TIER);
    }

    static boolean creativeBypassesCost() {
        return Slimefun.getCfg().getBoolean(ROOT + ".progression.creative-bypass-cost");
    }

    static boolean operatorCanSponsorUpgrades() {
        return Slimefun.getCfg().getBoolean(ROOT + ".progression.operator-can-sponsor-upgrades");
    }

    static boolean isBeaconDataEnabled() {
        return Slimefun.getCfg().getBoolean(BEACON_DATA_ROOT + ".enabled");
    }

    static boolean shouldImportExistingBeaconData() {
        return isBeaconDataEnabled() && Slimefun.getCfg().getBoolean(BEACON_DATA_ROOT + ".import-existing");
    }

    static boolean shouldMirrorBeaconData() {
        return isBeaconDataEnabled() && Slimefun.getCfg().getBoolean(BEACON_DATA_ROOT + ".mirror-native-beacons");
    }

    static boolean shouldBootstrapLegacyActivators() {
        return shouldImportExistingBeaconData()
                && Slimefun.getCfg().getBoolean(BEACON_DATA_ROOT + ".bootstrap-legacy-activators");
    }

    static boolean shouldHonorOverriddenRange() {
        return Slimefun.getCfg().getBoolean(BEACON_DATA_ROOT + ".honor-overridden-range");
    }

    static String getBeaconDataStorageType() {
        String value = Slimefun.getCfg().getString(BEACON_DATA_ROOT + ".storage-type");
        return value == null ? "WORLD" : value.trim().toUpperCase(Locale.ROOT);
    }

    static String getBeaconDataFolderName() {
        String value = Slimefun.getCfg().getString(BEACON_DATA_ROOT + ".folder-name");
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\")) {
            return "BeaconData";
        }
        return value.trim();
    }

    static PaymentMode getPaymentMode(BeaconPlusEffect effect) {
        PaymentMode mode = PaymentMode.parse(Slimefun.getCfg().getString(powerPath(effect) + ".payment-mode"));
        if (mode == PaymentMode.INHERIT) {
            mode = PaymentMode.parse(Slimefun.getCfg().getString(ROOT + ".progression.payment-mode"));
        }
        return mode == PaymentMode.MONEY ? PaymentMode.MONEY : PaymentMode.EXPERIENCE;
    }

    static int getExperienceCost(BeaconPlusEffect effect, int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return Math.max(0, Slimefun.getCfg().getInt(powerPath(effect) + ".experience-costs.tier-" + safeTier));
    }

    static double getMoneyCost(BeaconPlusEffect effect, int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return Math.max(0.0D, Slimefun.getCfg().getDouble(powerPath(effect) + ".money-costs.tier-" + safeTier));
    }

    static double getMaterialPower(Material material) {
        if (material == null) {
            return 0.0D;
        }
        return Math.max(0.0D, Slimefun.getCfg().getDouble(ROOT + ".pyramid.material-power." + material.name()));
    }

    static int getRequiredPyramidTier(int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return clamp(
                Slimefun.getCfg().getInt(ROOT + ".pyramid.tier-requirements." + safeTier + ".min-pyramid-tier"),
                1,
                4);
    }

    static double getRequiredAverageMaterialPower(int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return Math.max(
                0.0D,
                Slimefun.getCfg().getDouble(
                        ROOT + ".pyramid.tier-requirements." + safeTier + ".min-average-material-power"));
    }

    static String getConfigKey(BeaconPlusEffect effect) {
        return effect.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String powerPath(BeaconPlusEffect effect) {
        return ROOT + ".powers." + getConfigKey(effect);
    }

    private static int defaultExperienceCost(BeaconPlusEffect effect, int tier) {
        int base = switch (effect) {
            case FLYING, IMMORTALITY_FIELD, ACTIVATOR -> 25;
            case EXTRA_POWER, EXTRA_RANGE, AUTO_REPAIR, EXPERIENCE_BOOSTER, COOLDOWN_REDUCTION -> 15;
            case REGENERATION, RESISTANCE, PEACEFUL, GRAVITY_WELL, SPAWNERS, CROPS -> 10;
            default -> 5;
        };
        return switch (tier) {
            case 1 -> base;
            case 2 -> base * 2 + 5;
            default -> base * 4;
        };
    }

    private static double defaultMoneyCost(BeaconPlusEffect effect, int tier) {
        return defaultExperienceCost(effect, tier) * 40.0D;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    enum PaymentMode {
        INHERIT,
        EXPERIENCE,
        MONEY;

        private static PaymentMode parse(String value) {
            if (value == null || value.isBlank()) {
                return INHERIT;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return INHERIT;
            }
        }
    }
}
