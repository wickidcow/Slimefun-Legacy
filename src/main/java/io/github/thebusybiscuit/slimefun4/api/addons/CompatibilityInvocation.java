package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Immutable result from invoking an optional integration method. */
@SlimefunAPI
public final class CompatibilityInvocation<T> {

    private final CompatibilityInvocationStatus status;
    private final T value;
    private final Throwable failure;

    private CompatibilityInvocation(
            CompatibilityInvocationStatus status, @Nullable T value, @Nullable Throwable failure) {
        this.status = Objects.requireNonNull(status, "status");
        this.value = value;
        this.failure = failure;
    }

    public static <T> @Nonnull CompatibilityInvocation<T> success(@Nullable T value) {
        return new CompatibilityInvocation<>(CompatibilityInvocationStatus.SUCCESS, value, null);
    }

    public static <T> @Nonnull CompatibilityInvocation<T> unavailable() {
        return new CompatibilityInvocation<>(CompatibilityInvocationStatus.UNAVAILABLE, null, null);
    }

    public static <T> @Nonnull CompatibilityInvocation<T> failed(@Nonnull Throwable failure) {
        return new CompatibilityInvocation<>(
                CompatibilityInvocationStatus.FAILED, null, Objects.requireNonNull(failure, "failure"));
    }

    public @Nonnull CompatibilityInvocationStatus getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == CompatibilityInvocationStatus.SUCCESS;
    }

    public @Nonnull Optional<T> getValue() {
        return Optional.ofNullable(value);
    }

    public @Nonnull Optional<Throwable> getFailure() {
        return Optional.ofNullable(failure);
    }
}
