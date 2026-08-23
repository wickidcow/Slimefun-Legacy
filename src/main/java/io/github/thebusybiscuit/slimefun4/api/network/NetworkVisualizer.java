package io.github.thebusybiscuit.slimefun4.api.network;

import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.Spell;
import org.bukkit.entity.Player;

/**
 * This class represents the visualizer task of a given {@link Network}.
 *
 * @author TheBusyBiscuit
 *
 */
class NetworkVisualizer implements Runnable {

    private static final double PLAYER_VISUALIZER_RANGE_SQUARED = 96.0 * 96.0;
    private static final Spell ENERGY_PARTICLE_OPTIONS = new Spell(Color.YELLOW, 1.0F);

    /**
     * The {@link DustOptions} define the {@link Color} and size of non-energy network particles.
     */
    private final DustOptions particleOptions;

    /**
     * This is our {@link Network} instance.
     */
    private final Network network;

    private final boolean energyNetwork;
    @Nullable private final Player viewer;
    private final int maxParticles;
    private int spawnedParticles;

    /**
     * This creates a new global {@link NetworkVisualizer} for the given {@link Network}.
     *
     * @param network
     *            The {@link Network} to visualize
     */
    NetworkVisualizer(@Nonnull Network network, @Nonnull Color color) {
        this(network, color, null, Integer.MAX_VALUE);
    }

    /** Creates a bounded network visualizer that only sends particles to one player. */
    NetworkVisualizer(@Nonnull Network network, @Nonnull Color color, @Nullable Player viewer, int maxParticles) {
        Validate.notNull(network, "The network should not be null.");
        Validate.notNull(color, "The color cannot be null.");
        Validate.isTrue(maxParticles > 0, "The particle budget must be above zero.");

        this.network = network;
        this.energyNetwork = network instanceof EnergyNet;
        this.viewer = viewer;
        this.maxParticles = maxParticles;
        this.particleOptions = new DustOptions(color, viewer == null ? 3F : 1.25F);
    }

    @Override
    public void run() {
        if (viewer != null) {
            if (!viewer.isOnline()
                    || !viewer.getWorld().equals(network.regulator.getWorld())
                    || viewer.getLocation().distanceSquared(network.regulator) > PLAYER_VISUALIZER_RANGE_SQUARED) {
                return;
            }
            spawnParticles(network.regulator);
        }

        for (Location l : network.connectorNodes) {
            if (spawnedParticles >= maxParticles) {
                return;
            }
            spawnParticles(l);
        }

        for (Location l : network.terminusNodes) {
            if (spawnedParticles >= maxParticles) {
                return;
            }
            spawnParticles(l);
        }
    }

    /**
     * This method will spawn the actual particles.
     *
     * @param l
     *            The {@link Location} of our node
     */
    private void spawnParticles(@Nonnull Location l) {
        if (spawnedParticles >= maxParticles) {
            return;
        }

        if (viewer == null) {
            if (!network.isLocationAccessible(l)) {
                return;
            }

            if (energyNetwork) {
                l.getWorld()
                        .spawnParticle(
                                Particle.INSTANT_EFFECT,
                                l.getX() + 0.5,
                                l.getY() + 0.5,
                                l.getZ() + 0.5,
                                1,
                                0,
                                0,
                                0,
                                0,
                                ENERGY_PARTICLE_OPTIONS);
            } else {
                l.getWorld()
                        .spawnParticle(
                                VersionedParticle.DUST,
                                l.getX() + 0.5,
                                l.getY() + 0.5,
                                l.getZ() + 0.5,
                                1,
                                0,
                                0,
                                0,
                                1,
                                particleOptions);
            }
        } else {
            if (!viewer.getWorld().equals(l.getWorld())) {
                return;
            }

            if (energyNetwork) {
                viewer.spawnParticle(
                        Particle.INSTANT_EFFECT,
                        l.getX() + 0.5,
                        l.getY() + 0.5,
                        l.getZ() + 0.5,
                        1,
                        0,
                        0,
                        0,
                        0,
                        ENERGY_PARTICLE_OPTIONS);
            } else {
                viewer.spawnParticle(
                        VersionedParticle.DUST,
                        l.getX() + 0.5,
                        l.getY() + 0.5,
                        l.getZ() + 0.5,
                        1,
                        0,
                        0,
                        0,
                        1,
                        particleOptions);
            }
        }

        spawnedParticles++;
    }
}
