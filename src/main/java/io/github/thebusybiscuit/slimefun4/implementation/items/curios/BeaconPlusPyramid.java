package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * Reads the physical mineral pyramid under a Resonance Beacon and converts it into a bounded three-tier power source.
 */
final class BeaconPlusPyramid {

    private static final int MAX_LAYERS = 4;

    private BeaconPlusPyramid() {}

    static Profile inspect(Block beaconBlock) {
        BlockState state = beaconBlock.getState();
        int vanillaTier = state instanceof Beacon beacon ? beacon.getTier() : 0;
        if (vanillaTier <= 0) {
            return Profile.empty();
        }

        EnumMap<Material, Integer> counts = new EnumMap<>(Material.class);
        int completedLayers = 0;
        int totalBlocks = 0;
        double totalPower = 0.0D;

        for (int layer = 1; layer <= MAX_LAYERS; layer++) {
            int radius = layer;
            EnumMap<Material, Integer> layerCounts = new EnumMap<>(Material.class);
            int layerBlocks = 0;
            double layerPower = 0.0D;
            boolean complete = true;

            for (int x = -radius; x <= radius && complete; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Material material = beaconBlock
                            .getWorld()
                            .getBlockAt(beaconBlock.getX() + x, beaconBlock.getY() - layer, beaconBlock.getZ() + z)
                            .getType();
                    double materialPower = BeaconPlusConfig.getMaterialPower(material);
                    if (materialPower <= 0.0D) {
                        complete = false;
                        break;
                    }

                    layerBlocks++;
                    layerPower += materialPower;
                    layerCounts.merge(material, 1, Integer::sum);
                }
            }

            if (!complete) {
                break;
            }

            completedLayers = layer;
            totalBlocks += layerBlocks;
            totalPower += layerPower;
            layerCounts.forEach((material, count) -> counts.merge(material, count, Integer::sum));
        }

        int usableLayers = Math.min(vanillaTier, completedLayers);
        if (usableLayers <= 0 || totalBlocks <= 0) {
            return Profile.empty();
        }

        double averagePower = totalPower / totalBlocks;
        int naturalTier = 0;
        for (int tier = 1; tier <= BeaconPlusConfig.getMaxTier(); tier++) {
            if (usableLayers >= BeaconPlusConfig.getRequiredPyramidTier(tier)
                    && averagePower >= BeaconPlusConfig.getRequiredAverageMaterialPower(tier)) {
                naturalTier = tier;
            }
        }

        Material dominant = counts.entrySet().stream()
                .max(Map.Entry.<Material, Integer>comparingByValue()
                        .thenComparing(entry -> BeaconPlusConfig.getMaterialPower(entry.getKey())))
                .map(Map.Entry::getKey)
                .orElse(Material.IRON_BLOCK);

        return new Profile(usableLayers, averagePower, naturalTier, dominant, totalBlocks);
    }

    record Profile(
            int completedLayers,
            double averageMaterialPower,
            int naturalPowerTier,
            Material dominantMaterial,
            int blocks) {
        static Profile empty() {
            return new Profile(0, 0.0D, 0, Material.AIR, 0);
        }

        String dominantMaterialName() {
            if (dominantMaterial == Material.AIR) {
                return "None";
            }
            String[] parts =
                    dominantMaterial.name().toLowerCase(java.util.Locale.ROOT).split("_");
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return result.toString();
        }
    }
}
