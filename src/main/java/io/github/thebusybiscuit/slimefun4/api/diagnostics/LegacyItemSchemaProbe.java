package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

/**
 * Optional addon-owned read-only detector for legacy item metadata that still uses a current Slimefun ID.
 *
 * <p>Addons register implementations through Bukkit's {@code ServicesManager}. Slimefun Legacy only invokes
 * probes during an operator-triggered migration-aware Doctor scan. Probes are not called by automatic join,
 * pickup, inventory-open or chunk-load presentation repair.</p>
 *
 * <p>The supplied {@link ItemStack} is a clone. Implementations must be fast, synchronous and side-effect free:
 * do not perform database, network or filesystem IO, do not load chunks, and do not schedule mutations. Return
 * {@code null} when the item is already current or is not a legacy format owned by this probe.</p>
 */
@SlimefunAPI
public interface LegacyItemSchemaProbe {

    /** Human-readable migration family shown in Doctor output. */
    @Nonnull
    String getMigrationName();

    /**
     * Current Slimefun item IDs whose historical metadata schemas this probe understands.
     *
     * <p>Explicit ownership is required; wildcard probing is intentionally unsupported.</p>
     */
    @Nonnull
    Set<String> getSupportedItemIds();

    /**
     * Inspects a cloned candidate item without modifying live server state.
     *
     * @param item a clone of the item encountered by Doctor
     * @param slimefunId the stored Slimefun item ID
     * @return a legacy schema classification, or {@code null} when no legacy schema is detected
     */
    @Nullable
    LegacyItemSchemaCandidate probeItem(@Nonnull ItemStack item, @Nonnull String slimefunId);
}
