package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * An immutable numeric Minecraft version which does not depend on Slimefun's historical version enum.
 *
 * <p>This type is intended for feature and compatibility checks that must continue to work when Mojang adds a new
 * patch or release line. It accepts release and pre-release strings such as {@code 1.21.11}, {@code 26.1}, and
 * {@code 1.21.2-pre2}. Snapshot identifiers such as {@code 23w31a} are intentionally not guessed.
 */
@SlimefunAPI
public final class MinecraftVersionNumber implements Comparable<MinecraftVersionNumber> {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?\\s*$");

    private final int major;
    private final int minor;
    private final int patch;

    /**
     * Creates a numeric Minecraft version.
     *
     * @param major the major version component
     * @param minor the minor version component
     * @param patch the patch version component
     */
    public MinecraftVersionNumber(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Minecraft version components cannot be negative");
        }

        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Parses a numeric Minecraft release string without guessing snapshot versions.
     *
     * @param value the raw Minecraft version
     * @return the parsed version, or an empty optional when the value is not a numeric release
     */
    public static @Nonnull Optional<MinecraftVersionNumber> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return Optional.of(new MinecraftVersionNumber(major, minor, patch));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    /**
     * Checks this version against another numeric version.
     *
     * @param other the minimum version
     * @return whether this version is equal to or newer than the minimum
     */
    public boolean isAtLeast(@Nonnull MinecraftVersionNumber other) {
        return compareTo(other) >= 0;
    }

    /**
     * Checks whether this version is older than another numeric version.
     *
     * @param other the version to compare against
     * @return whether this version is older
     */
    public boolean isBefore(@Nonnull MinecraftVersionNumber other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(@Nonnull MinecraftVersionNumber other) {
        int majorComparison = Integer.compare(major, other.major);
        if (majorComparison != 0) {
            return majorComparison;
        }

        int minorComparison = Integer.compare(minor, other.minor);
        if (minorComparison != 0) {
            return minorComparison;
        }

        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MinecraftVersionNumber version)) {
            return false;
        }
        return major == version.major && minor == version.minor && patch == version.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public @Nonnull String toString() {
        return major + "." + minor + "." + patch;
    }
}
