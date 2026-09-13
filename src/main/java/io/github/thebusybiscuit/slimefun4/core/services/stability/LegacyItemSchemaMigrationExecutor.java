package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Executes one consumed, fingerprint-approved same-ID schema plan against live ItemStacks. */
public final class LegacyItemSchemaMigrationExecutor {

    private static final int MAX_CONTAINER_DEPTH = 4;

    private final LegacyItemSchemaMigrationPlan plan;
    private final Map<String, LegacyItemSchemaProbe> probesByItemId;
    private final Map<String, LegacyItemSchemaMigrator> migratorsByType;
    private final Map<LegacyItemSchemaMigrationPlan.Authorization, AtomicLong> remainingAuthorizations = new HashMap<>();
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
        for (LegacyItemSchemaMigrationPlan.Authorization authorization : plan.authorizations()) {
            remainingAuthorizations.put(authorization, new AtomicLong(authorization.candidateCount()));
        }
    }

    public @Nonnull LegacyItemSchemaMigrationPlan getPlan() { return plan; }
    public long getAuthorizedCandidates() { return authorizedCandidates.get(); }
    public long getMigrated() { return migrated.get(); }
    public long getSkipped() { return skipped.get(); }
    public long getFailures() { return failures.get(); }

    boolean inspectInventory(@Nonnull Inventory inventory, @Nonnull ItemDoctorReport report) {
        return inspectInventory(inventory, report, 0);
    }

    private boolean inspectInventory(@Nonnull Inventory inventory, @Nonnull ItemDoctorReport report, int depth) {
        report.inventoryScanned();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (inspectItem(item, report, depth)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    boolean inspectItem(@Nullable ItemStack item, @Nonnull ItemDoctorReport report) {
        return inspectItem(item, report, 0);
    }

    private boolean inspectItem(@Nullable ItemStack item, @Nonnull ItemDoctorReport report, int depth) {
        if (item == null || item.getType() == Material.AIR) return false;
        report.stackScanned();
        boolean changed = false;
        try {
            Optional<String> storedId = Slimefun.getItemDataService().getItemData(item);
            if (storedId.isPresent()) {
                report.slimefunStackFound();
                changed = migrateCandidate(item, storedId.get(), report);
            }
            if (depth < MAX_CONTAINER_DEPTH) {
                changed |= inspectNestedItems(item, report, depth + 1);
            }
            return changed;
        } catch (RuntimeException | LinkageError exception) {
            failures.incrementAndGet();
            report.failure();
            Slimefun.logger().log(Level.WARNING, "Schema migration skipped a failing live ItemStack.", exception);
            return changed;
        }
    }

    private boolean inspectNestedItems(ItemStack item, ItemDoctorReport report, int depth) {
        boolean changed = false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
            boolean nestedChanged = false;
            for (ItemStack nested : contents) {
                nestedChanged |= inspectItem(nested, report, depth);
            }
            if (nestedChanged) {
                bundleMeta.setItems(contents);
                item.setItemMeta(bundleMeta);
                changed = true;
            }
        }

        meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof Container container) {
                if (inspectInventory(container.getInventory(), report, depth)) {
                    blockStateMeta.setBlockState(container);
                    item.setItemMeta(blockStateMeta);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean migrateCandidate(ItemStack item, String slimefunId, ItemDoctorReport report) {
        LegacyItemSchemaProbe probe = probesByItemId.get(slimefunId);
        if (probe == null) return false;
        LegacyItemSchemaCandidate candidate = probe.probeItem(item.clone(), slimefunId);
        if (candidate == null || candidate.getValidationClaim() == null) return false;

        LegacyItemSchemaMigrationPlan.Authorization authorization = plan.findAuthorization(
                slimefunId, candidate.getCandidateType(), candidate.getValidationClaim());
        if (authorization == null || !consumeAuthorization(authorization)) {
            skipped.incrementAndGet();
            return false;
        }

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

    private boolean consumeAuthorization(LegacyItemSchemaMigrationPlan.Authorization authorization) {
        AtomicLong remaining = remainingAuthorizations.get(authorization);
        if (remaining == null) return false;
        while (true) {
            long value = remaining.get();
            if (value <= 0L) return false;
            if (remaining.compareAndSet(value, value - 1L)) return true;
        }
    }

    private static void restore(ItemStack target, ItemStack original) {
        target.setType(original.getType());
        target.setAmount(original.getAmount());
        target.setItemMeta(original.getItemMeta());
    }
}
