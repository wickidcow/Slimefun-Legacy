package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

/** Pure Gravity Well tuning values for a controlled horizontal reverse-knockback effect. */
final class BeaconPlusGravity {

    static final double NORMAL_PULL = 0.45D;
    static final double EXTRA_POWER_PULL = 0.63D;

    private BeaconPlusGravity() {}

    static double getPullStrength(int power) {
        return power > 0 ? EXTRA_POWER_PULL : NORMAL_PULL;
    }
}
