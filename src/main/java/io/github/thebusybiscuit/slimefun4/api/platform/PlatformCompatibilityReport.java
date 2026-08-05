package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Immutable result of checking addon platform requirements against the running server.
 */
@SlimefunAPI
public final class PlatformCompatibilityReport {

    private static final PlatformCompatibilityReport COMPATIBLE = new PlatformCompatibilityReport(List.of());

    private final List<String> incompatibilities;

    private PlatformCompatibilityReport(@Nonnull List<String> incompatibilities) {
        this.incompatibilities = List.copyOf(Objects.requireNonNull(incompatibilities, "incompatibilities"));
    }

    public static @Nonnull PlatformCompatibilityReport compatible() {
        return COMPATIBLE;
    }

    public static @Nonnull PlatformCompatibilityReport incompatible(@Nonnull List<String> incompatibilities) {
        Objects.requireNonNull(incompatibilities, "incompatibilities");
        return incompatibilities.isEmpty() ? COMPATIBLE : new PlatformCompatibilityReport(incompatibilities);
    }

    public boolean isCompatible() {
        return incompatibilities.isEmpty();
    }

    public @Nonnull List<String> getIncompatibilities() {
        return incompatibilities;
    }

    public @Nonnull String describe() {
        return isCompatible() ? "Compatible" : String.join("; ", incompatibilities);
    }
}
