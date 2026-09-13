package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Executes one consumed, fingerprint-approved same-ID schema plan against live ItemStacks. */
public final class LegacyItemSchemaMigrationExecutor {

    private final LegacyItemSchemaMigrationPlan plan;
    private final Map<String, LegacyItemSchemaProbe> probesByItemId;
    private final Map<String, LegacyItemSchemaMigrator> migratorsByType;
    private final AtomicLong authorizedCandidates = new AtomicLong();
    private final AtomicLong migrated = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    LegacyItemSchemaMigrationExecutor(
            @Nonnull LegacyItemSchemaMigrationPlan plan,
            @Nonnull Map<String, LegacyItemSchemaProbe> probesByItemId,
            @Nonnull Map<String, LegacyItemSchemaMigrator> migratorsByType) {
        this.plan = plan;
        this.probesByItemId = Map.copyOf(probesByItemId);
        this.migratorsByType = Map.copyOf(migratorsByType);
    }

    public @Nonnull LegacyItemSchemaMigrationPlan getPlan() { return plan; }
    public long getAuthorizedCandidates() { return authorizedCandidates.get(); }
    public long getMigrated() { return migrated.get(); }
    public long getSkipped() { return skipped.get(); }
    public long getFailures() { return failures.get(); }

    boolean inspectItem(@Nullable ItemStack item, @Nonnull ItemDoctorReport report) {
        if (item == null || item.getType() == Material.AIR) return false;
        report.stackScanned();
        try {
            Optional<String> storedId = Slimefun.getItemDataService().getItemData(item);
            if (storedId.isEmpty()) return false;
            report.slimefunStackFound();
            return migrateCandidate(item, storedId.get(), report);
        } catch (RuntimeException | LinkageError exception) {
            failures.incrementAndGet();
            report.failure();
            Slimefun.logger().log(Level.WARNING, "Schema migration skipped a failing live ItemStack.", exception);
            return false;
        }
    }

    private boolean migrateCandidate(ItemStack item, String slimefunId, ItemDoctorReport report) {
        LegacyItemSchemaProbe probe = probesByItemId.get(slimefunId);
        if (probe == null) return false;
        LegacyItemSchemaCandidate candidate = probe.probeItem(item.clone(), slimefunId);
        if (candidate == null || candidate.getValidationClaim() == null) return false;

        LegacyItemSchemaMigrationPlan.Authorization authorization = plan.findAuthorization(
                slimefunId, candidate.getCandidateType(), candidate.getValidationClaim());
        if (authorization == null) return false;

        LegacyItemSchemaMigrator migrator = migratorsByType.get(candidate.getCandidateType());
        if (migrator == null) {
            failures.incrementAndGet();
            report.failure();
            return false;
        }

        authorizedCandidates.incrementAndGet();
        ItemStack original = item.clone();
        try {
            boolean changed = migrator.migrateItem(
                    item,
                    slimefunId,
                    candidate.getCandidateType(),
                    candidate.getValidationClaim(),
                    authorization.migrationPayload());

            Optional<String> resultingId = Slimefun.getItemDataService().getItemData(item);
            boolean invalidMutation = resultingId.isEmpty()
                    || !slimefunId.equals(resultingId.get())
                    || item.getAmount() != original.getAmount()
                    || (!changed && !item.equals(original));
            if (invalidMutation) {
                restore(item, original);
                failures.incrementAndGet();
                report.failure();
                Slimefun.logger().warning(
                        "Addon schema migrator violated the same-ID ItemStack contract; the original item was restored.");
                return false;
            }

            if (changed && !item.equals(original)) {
                migrated.incrementAndGet();
                report.stackRepaired();
                return true;
            }

            skipped.incrementAndGet();
            return false;
        } catch (RuntimeException | LinkageError exception) {
            restore(item, original);
            failures.incrementAndGet();
            report.failure();
            Slimefun.logger().log(
                    Level.WARNING,
                    "Addon schema migrator failed; the original live ItemStack was restored.",
                    exception);
            return false;
        }
    }

    private static void restore(ItemStack target, ItemStack original) {
        target.setType(original.getType());
        target.setAmount(original.getAmount());
        target.setItemMeta(original.getItemMeta());
    }
}
