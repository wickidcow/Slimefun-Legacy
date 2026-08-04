package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/** Immutable result returned by an addon doctor provider. */
@SlimefunAPI
public final class AddonDoctorReport {

    private final String addonName;
    private final boolean repairMode;
    private final long scannedEntries;
    private final long issuesFound;
    private final long repairedEntries;
    private final long failures;
    private final List<String> details;

    public AddonDoctorReport(
            @Nonnull String addonName,
            boolean repairMode,
            @Nonnegative long scannedEntries,
            @Nonnegative long issuesFound,
            @Nonnegative long repairedEntries,
            @Nonnegative long failures,
            @Nonnull List<String> details) {
        this.addonName = requireName(addonName);
        this.repairMode = repairMode;
        this.scannedEntries = requireNonnegative(scannedEntries, "scannedEntries");
        this.issuesFound = requireNonnegative(issuesFound, "issuesFound");
        this.repairedEntries = requireNonnegative(repairedEntries, "repairedEntries");
        this.failures = requireNonnegative(failures, "failures");

        Objects.requireNonNull(details, "details");
        List<String> detailCopy = new ArrayList<>(details.size());
        for (String detail : details) {
            detailCopy.add(Objects.requireNonNull(detail, "details cannot contain null entries"));
        }
        this.details = Collections.unmodifiableList(detailCopy);
    }

    @Nonnull
    public String getAddonName() {
        return addonName;
    }

    public boolean isRepairMode() {
        return repairMode;
    }

    public long getScannedEntries() {
        return scannedEntries;
    }

    public long getIssuesFound() {
        return issuesFound;
    }

    public long getRepairedEntries() {
        return repairedEntries;
    }

    public long getFailures() {
        return failures;
    }

    @Nonnull
    public List<String> getDetails() {
        return details;
    }

    private static String requireName(String value) {
        String name = Objects.requireNonNull(value, "addonName").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("addonName cannot be blank");
        }
        return name;
    }

    private static long requireNonnegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }
}
