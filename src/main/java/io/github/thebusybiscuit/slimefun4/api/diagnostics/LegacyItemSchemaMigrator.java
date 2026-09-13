package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

/**
 * Optional addon-owned mutator for an approved same-ID legacy item schema.
 *
 * <p>Slimefun invokes this only from an explicit, fingerprint-authorized Doctor execution pass. Before this method
 * is called, core re-runs the owning addon's schema probe against a clone of the live item. The current Slimefun
 * item ID, candidate type and opaque evidence claim must match the short-lived approved plan.</p>
 *
 * <p>The migrator may mutate only the supplied {@link ItemStack}. Database, world, chunk and player-state changes
 * belong in a dedicated addon migration provider, not this item-schema API. Slimefun core persists the mutated stack
 * back to the inventory, entity, nested container or backpack from which it was read.</p>
 */
@SlimefunAPI
public interface LegacyItemSchemaMigrator {

    /** Candidate types this mutator understands. Wildcards are intentionally unsupported. */
    @Nonnull
    Set<String> getSupportedCandidateTypes();

    /**
     * Migrates one live item whose schema evidence was approved by a Doctor plan.
     *
     * @param item live mutable ItemStack; never retain this reference after returning
     * @param slimefunItemId current Slimefun item ID observed during execution
     * @param candidateType addon-owned schema candidate type
     * @param validationClaim opaque evidence claim freshly reproduced by the addon probe
     * @param migrationPayload private payload produced by VERIFIED external validation; for READY item-local
     *        candidates this is a core-owned marker and the addon must read migration state from {@code item}
     * @return {@code true} when the ItemStack was changed, otherwise {@code false}
     */
    boolean migrateItem(
            @Nonnull ItemStack item,
            @Nonnull String slimefunItemId,
            @Nonnull String candidateType,
            @Nonnull String validationClaim,
            @Nonnull String migrationPayload);
}
