package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

/** Pure Gravity Well tuning values, deliberately independent of Bukkit plugin initialization. */
final class BeaconPlusGravity {

    static final double NORMAL_PULL = 1.50D;
    static final double EXTRA_POWER_PULL = 2.10D;

    private BeaconPlusGravity() {}

    static double getPullStrength(int power) {
        return power > 0 ? EXTRA_POWER_PULL : NORMAL_PULL;
    }
}
