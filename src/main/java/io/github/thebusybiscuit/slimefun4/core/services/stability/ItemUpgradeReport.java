package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/** Thread-safe progress and result counters for a Slimefun item-upgrade run. */
public final class ItemUpgradeReport {

    private static final int SAMPLE_LIMIT = 20;

    private final boolean repairMode;
    private final long startedAtNanos = System.nanoTime();
    private final AtomicBoolean complete = new AtomicBoolean();
    private final AtomicLong inventories = new AtomicLong();
    private final AtomicLong backpacks = new AtomicLong();
    private final AtomicLong scannedStacks = new AtomicLong();
    private final AtomicLong slimefunStacks = new AtomicLong();
    private final AtomicLong readyIdUpgrades = new AtomicLong();
    private final AtomicLong rewrittenIds = new AtomicLong();
    private final AtomicLong missingTargets = new AtomicLong();
    private final AtomicLong materialMismatches = new AtomicLong();
    private final AtomicLong unknownUnmappedIds = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final ItemDoctorReport presentationReport;
    private final Set<String> readySamples = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> blockedSamples = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> unknownSamples = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<String, AtomicLong> readyByAddon = new ConcurrentHashMap<>();
    private final Set<String> candidateLegacyIds = ConcurrentHashMap.newKeySet();
    private volatile long completedAtNanos;

    public ItemUpgradeReport(boolean repairMode) {
        this.repairMode = repairMode;
        presentationReport = new ItemDoctorReport(repairMode);
    }

    void inventoryScanned() {
        inventories.incrementAndGet();
    }

    void backpackScanned() {
        backpacks.incrementAndGet();
    }

    void stackScanned() {
        scannedStacks.incrementAndGet();
    }

    void slimefunStackFound() {
        slimefunStacks.incrementAndGet();
    }

    void readyIdUpgrade(@Nonnull String legacyId, @Nonnull String targetId, @Nonnull String addonName) {
        readyIdUpgrades.incrementAndGet();
        candidateLegacyIds.add(legacyId);
        readyByAddon.computeIfAbsent(addonName, ignored -> new AtomicLong()).incrementAndGet();
        addSample(readySamples, legacyId + " -> " + targetId + " [" + addonName + "]");
    }

    void idRewritten() {
        rewrittenIds.incrementAndGet();
    }

    void missingTarget(@Nonnull String legacyId, @Nonnull String targetId) {
        missingTargets.incrementAndGet();
        addSample(blockedSamples, legacyId + " -> " + targetId + " [TARGET MISSING]");
    }

    void materialMismatch(@Nonnull String legacyId, @Nonnull String targetId, @Nonnull String detail) {
        materialMismatches.incrementAndGet();
        addSample(blockedSamples, legacyId + " -> " + targetId + " [MATERIAL CHANGED: " + detail + "]");
    }

    void unknownUnmapped(@Nonnull String itemId) {
        unknownUnmappedIds.incrementAndGet();
        addSample(unknownSamples, itemId);
    }

    void failure() {
        failures.incrementAndGet();
    }

    void markComplete() {
        if (complete.compareAndSet(false, true)) {
            presentationReport.markComplete();
            completedAtNanos = System.nanoTime();
        }
    }

    public boolean isRepairMode() {
        return repairMode;
    }

    public boolean isComplete() {
        return complete.get();
    }

    public long getInventories() {
        return inventories.get();
    }

    public long getBackpacks() {
        return backpacks.get();
    }

    public long getScannedStacks() {
        return scannedStacks.get();
    }

    public long getSlimefunStacks() {
        return slimefunStacks.get();
    }

    public long getReadyIdUpgrades() {
        return readyIdUpgrades.get();
    }

    public long getRewrittenIds() {
        return rewrittenIds.get();
    }

    public long getMissingTargets() {
        return missingTargets.get();
    }

    public long getMaterialMismatches() {
        return materialMismatches.get();
    }

    public long getUnknownUnmappedIds() {
        return unknownUnmappedIds.get();
    }

    public long getFailures() {
        return failures.get() + presentationReport.getFailures();
    }

    public long getPresentationCandidates() {
        return presentationReport.getCjkStacks();
    }

    public long getPresentationRepairs() {
        return presentationReport.getRepairedStacks();
    }

    public long getUnresolvedPresentations() {
        return presentationReport.getUnresolvedTemplates();
    }

    public @Nonnull ItemDoctorReport getPresentationReport() {
        return presentationReport;
    }

    public @Nonnull List<String> getReadySamples() {
        return snapshot(readySamples);
    }

    public @Nonnull List<String> getBlockedSamples() {
        return snapshot(blockedSamples);
    }

    public @Nonnull List<String> getUnknownSamples() {
        return snapshot(unknownSamples);
    }

    public @Nonnull Set<String> getCandidateLegacyIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(candidateLegacyIds));
    }

    public @Nonnull Map<String, Long> getReadyByAddon() {
        Map<String, Long> snapshot = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        readyByAddon.forEach((addon, count) -> snapshot.put(addon, count.get()));
        return Collections.unmodifiableMap(snapshot);
    }

    public long getDurationMillis() {
        long end = isComplete() ? completedAtNanos : System.nanoTime();
        return Math.max(0L, (end - startedAtNanos) / 1_000_000L);
    }

    public boolean hasSafeWork() {
        return getReadyIdUpgrades() > 0 || getPresentationCandidates() > 0;
    }

    private static void addSample(Set<String> samples, String value) {
        synchronized (samples) {
            if (samples.size() < SAMPLE_LIMIT) {
                samples.add(value);
            }
        }
    }

    private static List<String> snapshot(Set<String> samples) {
        synchronized (samples) {
            return Collections.unmodifiableList(new ArrayList<>(samples));
        }
    }
}
