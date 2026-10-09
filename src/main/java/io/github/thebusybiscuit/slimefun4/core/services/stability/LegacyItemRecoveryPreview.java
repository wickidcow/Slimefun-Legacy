package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.core.services.stability.KnownLegacyItemIdCatalog.Hint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds a read-only, addon-grouped recovery preview from an Item Doctor snapshot.
 *
 * <p>Only addon-declared legacy candidates have exact per-ID stack counts.
 * Unknown CJK-presentation IDs are bounded samples, not an exhaustive inventory.
 * No identity hint, recommendation, or declared mapping here authorizes a repair.
 */
public final class LegacyItemRecoveryPreview {

    private LegacyItemRecoveryPreview() {}

    /**
     * Classifies historical IDs without interacting with inventories, storage, or registry mutation APIs.
     *
     * @param exactCandidates per-ID declared legacy candidates counted by Doctor
     * @param unknownSamples bounded sample of unknown IDs (not exact per-ID counts)
     * @param declaredMappings currently registered addon-owned legacy-ID mappings
     * @param historicalLookup read-only lookup of verified historical addon identity
     * @param currentlyRegistered lookup of IDs already registered in the live Slimefun registry
     * @return immutable entries grouped by their known source addon and sorted by item ID
     */
    public static @Nonnull List<Entry> build(
            @Nonnull Map<String, Long> exactCandidates,
            @Nonnull List<String> unknownSamples,
            @Nonnull Map<String, String> declaredMappings,
            @Nonnull Function<String, Optional<Hint>> historicalLookup,
            @Nonnull Predicate<String> currentlyRegistered) {

        Objects.requireNonNull(exactCandidates);
        Objects.requireNonNull(unknownSamples);
        Objects.requireNonNull(declaredMappings);
        Objects.requireNonNull(historicalLookup);
        Objects.requireNonNull(currentlyRegistered);

        TreeSet<String> ids = new TreeSet<>();
        for (String id : exactCandidates.keySet()) {
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        for (String id : unknownSamples) {
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }

        List<Entry> results = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }

            boolean exact = exactCandidates.containsKey(id);
            boolean declared = declaredMappings.containsKey(id);
            // A scan may have been taken before an addon was reinstalled. Never label
            // such a now-registered, otherwise-unmapped ID as an unresolved orphan.
            if (!exact && !declared && currentlyRegistered.test(id)) {
                continue;
            }

            Hint historical = historicalLookup.apply(id).orElse(null);
            String source = historical == null
                    ? (declared ? "Unattributed declared mapping" : "Unknown addon")
                    : groupFor(historical.source());
            String declaredTarget = declaredMappings.get(id);
            // Never present a historical hint as the replacement target of a stale declared mapping.
            String target = declared ? declaredTarget : (!exact && historical != null ? historical.targetId() : null);
            long stackCount = exact && exactCandidates.get(id) != null ? exactCandidates.get(id) : 0L;
            String status;
            String nextStep;
            if (declared) {
                if (declaredTarget == null || declaredTarget.isBlank()) {
                    status = "MAPPING CHANGED";
                    nextStep = "Re-scan and verify the owning addon's mapping before any migration.";
                } else if (currentlyRegistered.test(declaredTarget)) {
                    status = "DECLARED TARGET READY";
                    nextStep = "Review migrations plan/providers; any execution must be addon-owned and validated.";
                } else {
                    status = "DECLARED TARGET MISSING";
                    nextStep = "Restore the target addon first; do not rewrite missing IDs.";
                }
            } else if (exact) {
                status = "MAPPING CHANGED";
                nextStep = "The scan's declared mapping is no longer registered. Re-scan before planning.";
            } else if (historical != null) {
                status = "HISTORICAL ONLY";
                nextStep = recommendedAction(source, id);
            } else {
                status = "UNIDENTIFIED";
                nextStep = "Identify the original addon from backups or old JARs; do not guess a replacement.";
            }

            results.add(new Entry(source, id, target, status, nextStep, exact, stackCount));
        }

        results.sort(Comparator.comparing(Entry::addon).thenComparing(Entry::itemId));
        return List.copyOf(results);
    }

    private static String groupFor(String source) {
        if (source.startsWith("EnderPanda")) {
            return "EnderPanda";
        }
        if (source.startsWith("Slimefun LuckyBlocks")) {
            return "LuckyBlocks";
        }
        if (source.startsWith("InfinityExpansion")) {
            return "InfinityExpansion";
        }
        if (source.startsWith("Slimefun4")) {
            return "Slimefun4";
        }
        return source;
    }

    private static String recommendedAction(String addon, String id) {
        return switch (addon) {
            case "EnderPanda" -> "SHULKER_HELMET".equals(id)
                    ? "Use Slimefun Legacy with the restored helmet ID, then re-scan."
                    : "Install a compatible EnderPanda addon; no substitute ID is approved.";
            case "LuckyBlocks" ->
                    "Install SF_LuckyBlocks v1.0.4+; Lucky gear name repair uses /luckyrestore.";
            case "InfinityExpansion" ->
                    "Review migrations plan/providers; only a verified addon provider may migrate.";
            case "Slimefun4" ->
                    "Manually review the retired machine; no safe automatic placed-block conversion.";
            default ->
                    "Verify the source and replacement behavior before attempting any migration.";
        };
    }

    public record Entry(
            @Nonnull String addon,
            @Nonnull String itemId,
            @Nullable String targetId,
            @Nonnull String status,
            @Nonnull String nextStep,
            boolean exactCount,
            long stackCount) {}
}
