package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Optional addon-owned migration service for legacy Slimefun item and block identifiers.
 *
 * <p>Addons register an implementation through Bukkit's {@code ServicesManager}. Slimefun Legacy
 * uses this provider only as a controlled delegation boundary: the addon remains responsible for
 * understanding and migrating its own item metadata, block records, menus and persistent storage.
 * Slimefun core must not infer or rewrite addon-specific persistence formats.</p>
 *
 * <p>Implementations must not force-load chunks solely for a migration pass. A repair pass should
 * only mutate state that is already in the provider's supported loaded scope.</p>
 */
@SlimefunAPI
public interface LegacyItemMigrationProvider {

    /** Human-readable migration name shown in Doctor output. */
    @Nonnull
    String getMigrationName();

    /**
     * Returns the legacy ID to current ID mappings owned by this provider.
     *
     * <p>The returned mappings must agree with the mappings the addon publishes through
     * {@code SlimefunRegistry#registerLegacySlimefunItemId}. Slimefun Legacy validates this
     * agreement before allowing a repair pass.</p>
     */
    @Nonnull
    Map<String, String> getLegacyItemMappings();

    /**
     * Scans the provider's supported loaded scope and optionally performs its migration.
     *
     * @param repair whether safe addon-owned migration should be performed
     * @return an immutable migration report
     */
    @Nonnull
    AddonDoctorReport runMigration(boolean repair);
}
