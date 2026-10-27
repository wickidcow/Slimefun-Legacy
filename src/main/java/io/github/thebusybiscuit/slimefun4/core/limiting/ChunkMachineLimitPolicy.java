package io.github.thebusybiscuit.slimefun4.core.limiting;

/**
 * Deterministic, side-effect-free admission policy for new Slimefun machines.
 *
 * <p>Only registrations with an active BlockTicker consume a machine slot.
 * Existing machines are grandfathered: this policy never stops their ticker,
 * mutates stored items or deletes blocks. If a chunk is already at or above
 * its quota, another placement is refused until a machine is removed.</p>
 */
public final class ChunkMachineLimitPolicy {

    public static final int DEFAULT_MAX_MACHINES_PER_CHUNK = 256;

    private ChunkMachineLimitPolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Invalid server configuration cannot silently disable protection or
     * turn into an unlimited quota.
     *
     * @param configured maximum machines from config.yml
     * @return the configured positive limit, or the documented default
     */
    public static int effectiveLimit(int configured) {
        return configured >= 1 ? configured : DEFAULT_MAX_MACHINES_PER_CHUNK;
    }

    /**
     * Returns whether this new ticking machine would exceed the chunk limit.
     * The count is sourced from the core ticker registry and therefore includes
     * normal and UniversalBlock ticker registrations from maintained addons.
     */
    public static boolean rejects(
            boolean enabled, boolean tickingMachine, boolean authorizedBypass, int current, int configuredLimit) {
        return enabled
                && tickingMachine
                && !authorizedBypass
                && current >= effectiveLimit(configuredLimit);
    }
}
