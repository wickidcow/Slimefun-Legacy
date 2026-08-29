package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Server-owner configuration for the native Adventurer's Curios Resonance Beacon.
 *
 * <p>All settings live under {@code SlimefunLegacyAddition.PoweredBeacon} in {@code configSFLAddons.yml}.
 */
final class BeaconPlusConfig {

    static final String ROOT = "SlimefunLegacyAddition.PoweredBeacon";
    static final String BEACON_DATA_ROOT = ROOT + ".BeaconData";
    private static final int MAX_TIER = 3;

    private BeaconPlusConfig() {}

    static void installDefaults() {
        var config = CuriositiesConfig.getConfig();
        config.setDefaultValue(ROOT + ".enabled", true);
        config.setDefaultValue(ROOT + ".chunk-loading-enabled", true);
        config.setDefaultValue(ROOT + ".electric-operation.enabled", true);
        config.setDefaultValue(ROOT + ".electric-operation.capacity", 4096);
        config.setDefaultValue(ROOT + ".electric-operation.base-joules-per-pulse", 16);
        config.setDefaultValue(ROOT + ".electric-operation.tier-joules-per-pulse", 4);
        config.setDefaultValue(ROOT + ".electric-operation.activator-tier-surcharge-joules-per-pulse", 16);
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
            config.setDefaultValue(path + ".enabled", true);
            config.setDefaultValue(path + ".payment-mode", "INHERIT");
            for (int tier = 1; tier <= MAX_TIER; tier++) {
                config.setDefaultValue(path + ".experience-costs.tier-" + tier, defaultExperienceCost(effect, tier));
                config.setDefaultValue(path + ".money-costs.tier-" + tier, defaultMoneyCost(effect, tier));
            }
        }

        config.save();
    }

    static boolean isEnabled() {
        return CuriositiesConfig.getConfig().getBoolean(ROOT + ".enabled");
    }

    static boolean isPowerEnabled(BeaconPlusEffect effect) {
        return effect.isConfigurable()
                && CuriositiesConfig.getConfig().getBoolean(powerPath(effect) + ".enabled");
    }

    static boolean isElectricOperationEnabled() {
        return CuriositiesConfig.getConfig().getBoolean(ROOT + ".electric-operation.enabled");
    }

    static int getEnergyCapacity() {
        int configured = CuriositiesConfig.getConfig().getInt(ROOT + ".electric-operation.capacity");
        return configured > 0 ? configured : 4096;
    }

    static int getEnergyBaseCostPerPulse() {
        return Math.max(
                0,
                CuriositiesConfig.getConfig().getInt(ROOT + ".electric-operation.base-joules-per-pulse"));
    }

    static int getEnergyTierCostPerPulse() {
        return Math.max(
                0,
                CuriositiesConfig.getConfig().getInt(ROOT + ".electric-operation.tier-joules-per-pulse"));
    }

    static int getEnergyActivatorTierSurchargePerPulse() {
        return Math.max(
                0,
                CuriositiesConfig.getConfig()
                        .getInt(ROOT + ".electric-operation.activator-tier-surcharge-joules-per-pulse"));
    }

    static int getMaxTier() {
        return clamp(CuriositiesConfig.getConfig().getInt(ROOT + ".progression.max-tier"), 1, MAX_TIER);
    }

    static boolean creativeBypassesCost() {
        return CuriositiesConfig.getConfig().getBoolean(ROOT + ".progression.creative-bypass-cost");
    }

    static boolean operatorCanSponsorUpgrades() {
        return CuriositiesConfig.getConfig().getBoolean(ROOT + ".progression.operator-can-sponsor-upgrades");
    }

    static boolean isBeaconDataEnabled() {
        return CuriositiesConfig.getConfig().getBoolean(BEACON_DATA_ROOT + ".enabled");
    }

    static boolean shouldImportExistingBeaconData() {
        return isBeaconDataEnabled()
                && CuriositiesConfig.getConfig().getBoolean(BEACON_DATA_ROOT + ".import-existing");
    }

    static boolean shouldMirrorBeaconData() {
        return isBeaconDataEnabled()
                && CuriositiesConfig.getConfig().getBoolean(BEACON_DATA_ROOT + ".mirror-native-beacons");
    }

    static boolean shouldBootstrapLegacyActivators() {
        return BeaconPlusChunkLoadingControl.isEnabled()
                && shouldImportExistingBeaconData()
                && CuriositiesConfig.getConfig().getBoolean(BEACON_DATA_ROOT + ".bootstrap-legacy-activators");
    }

    static boolean shouldHonorOverriddenRange() {
        return CuriositiesConfig.getConfig().getBoolean(BEACON_DATA_ROOT + ".honor-overridden-range");
    }

    static String getBeaconDataStorageType() {
        String value = CuriositiesConfig.getConfig().getString(BEACON_DATA_ROOT + ".storage-type");
        return value == null ? "WORLD" : value.trim().toUpperCase(Locale.ROOT);
    }

    static String getBeaconDataFolderName() {
        String value = CuriositiesConfig.getConfig().getString(BEACON_DATA_ROOT + ".folder-name");
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\")) {
            return "BeaconData";
        }
        return value.trim();
    }

    static PaymentMode getPaymentMode(BeaconPlusEffect effect) {
        PaymentMode mode = PaymentMode.parse(
                CuriositiesConfig.getConfig().getString(powerPath(effect) + ".payment-mode"));
        if (mode == PaymentMode.INHERIT) {
            mode = PaymentMode.parse(
                    CuriositiesConfig.getConfig().getString(ROOT + ".progression.payment-mode"));
        }
        return mode == PaymentMode.MONEY ? PaymentMode.MONEY : PaymentMode.EXPERIENCE;
    }

    static int getExperienceCost(BeaconPlusEffect effect, int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return Math.max(
                0,
                CuriositiesConfig.getConfig()
                        .getInt(powerPath(effect) + ".experience-costs.tier-" + safeTier));
    }

    static double getMoneyCost(BeaconPlusEffect effect, int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return Math.max(
                0.0D,
                CuriositiesConfig.getConfig()
                        .getDouble(powerPath(effect) + ".money-costs.tier-" + safeTier));
    }

    static double getMaterialPower(Material material) {
        if (material == null) {
            return 0.0D;
        }
        return Math.max(
                0.0D,
                CuriositiesConfig.getConfig().getDouble(ROOT + ".pyramid.material-power." + material.name()));
    }

    /**
     * Captures the small pyramid configuration surface once for a complete physical pyramid inspection.
     *
     * <p>A four-layer beacon contains up to 164 mineral blocks. Reading the YAML-backed configuration for every
     * block turned each Resonance Beacon pulse into hundreds of synchronized config lookups and temporary path
     * strings. The snapshot deliberately lives for only one inspection, so direct config reloads are still visible
     * on the very next inspection while all blocks in that inspection share the same settings.
     */
    static PyramidSettings getPyramidSettings() {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        int maxTier = clamp(config.getInt(ROOT + ".progression.max-tier"), 1, MAX_TIER);
        return new PyramidSettings(
                maxTier,
                readMaterialPower(config, Material.IRON_BLOCK),
                readMaterialPower(config, Material.GOLD_BLOCK),
                readMaterialPower(config, Material.EMERALD_BLOCK),
                readMaterialPower(config, Material.DIAMOND_BLOCK),
                readMaterialPower(config, Material.NETHERITE_BLOCK),
                readRequiredPyramidTier(config, 1),
                readRequiredPyramidTier(config, 2),
                readRequiredPyramidTier(config, 3),
                readRequiredAverageMaterialPower(config, 1),
                readRequiredAverageMaterialPower(config, 2),
                readRequiredAverageMaterialPower(config, 3));
    }

    static int getRequiredPyramidTier(int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return clamp(
                CuriositiesConfig.getConfig()
                        .getInt(ROOT + ".pyramid.tier-requirements." + safeTier + ".min-pyramid-tier"),
                1,
                4);
    }

    static double getRequiredAverageMaterialPower(int tier) {
        int safeTier = clamp(tier, 1, getMaxTier());
        return Math.max(
                0.0D,
                CuriositiesConfig.getConfig()
                        .getDouble(ROOT + ".pyramid.tier-requirements." + safeTier + ".min-average-material-power"));
    }

    static String getConfigKey(BeaconPlusEffect effect) {
        return effect.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static double readMaterialPower(CuriositiesConfig config, Material material) {
        return Math.max(0.0D, config.getDouble(ROOT + ".pyramid.material-power." + material.name()));
    }

    private static int readRequiredPyramidTier(CuriositiesConfig config, int tier) {
        return clamp(
                config.getInt(ROOT + ".pyramid.tier-requirements." + tier + ".min-pyramid-tier"),
                1,
                4);
    }

    private static double readRequiredAverageMaterialPower(CuriositiesConfig config, int tier) {
        return Math.max(
                0.0D,
                config.getDouble(ROOT + ".pyramid.tier-requirements." + tier + ".min-average-material-power"));
    }

    private static String powerPath(BeaconPlusEffect effect) {
        return ROOT + ".powers." + getConfigKey(effect);
    }

    private static int defaultExperienceCost(BeaconPlusEffect effect, int tier) {
        int base =
                switch (effect) {
                    case FLYING, IMMORTALITY_FIELD, ACTIVATOR -> 25;
                    case EXTRA_POWER,
                            EXTRA_RANGE,
                            AUTO_REPAIR,
                            EXPERIENCE_BOOSTER,
                            COOLDOWN_REDUCTION,
                            RADIATION_ABSORBER -> 15;
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

    record PyramidSettings(
            int maxTier,
            double ironPower,
            double goldPower,
            double emeraldPower,
            double diamondPower,
            double netheritePower,
            int tier1RequiredPyramid,
            int tier2RequiredPyramid,
            int tier3RequiredPyramid,
            double tier1RequiredAverage,
            double tier2RequiredAverage,
            double tier3RequiredAverage) {

        double materialPower(Material material) {
            if (material == null) {
                return 0.0D;
            }
            return switch (material) {
                case IRON_BLOCK -> ironPower;
                case GOLD_BLOCK -> goldPower;
                case EMERALD_BLOCK -> emeraldPower;
                case DIAMOND_BLOCK -> diamondPower;
                case NETHERITE_BLOCK -> netheritePower;
                default -> 0.0D;
            };
        }

        int requiredPyramidTier(int tier) {
            return switch (clamp(tier, 1, maxTier)) {
                case 1 -> tier1RequiredPyramid;
                case 2 -> tier2RequiredPyramid;
                default -> tier3RequiredPyramid;
            };
        }

        double requiredAverageMaterialPower(int tier) {
            return switch (clamp(tier, 1, maxTier)) {
                case 1 -> tier1RequiredAverage;
                case 2 -> tier2RequiredAverage;
                default -> tier3RequiredAverage;
            };
        }
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
