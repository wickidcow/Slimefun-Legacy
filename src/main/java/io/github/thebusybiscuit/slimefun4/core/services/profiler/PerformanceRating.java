package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;

/**
 * This enum is used to quantify Slimefun's performance impact. This way we can assign a
 * "grade" to each timings report and also use this for metrics collection.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunProfiler
 *
 */
public enum PerformanceRating implements Predicate<Float> {

    // Thresholds might change in the future!

    UNKNOWN(NamedTextColor.WHITE, -1),

    GOOD(NamedTextColor.DARK_GREEN, 10),
    FINE(NamedTextColor.DARK_GREEN, 20),
    OKAY(NamedTextColor.GREEN, 30),
    MODERATE(NamedTextColor.YELLOW, 55),
    SEVERE(NamedTextColor.RED, 85),
    HURTFUL(NamedTextColor.DARK_RED, 500),
    BAD(NamedTextColor.DARK_RED, Float.MAX_VALUE);

    private final NamedTextColor color;
    private final float threshold;

    PerformanceRating(@Nonnull NamedTextColor color, float threshold) {
        Validate.notNull(color, "Color cannot be null");
        this.color = color;
        this.threshold = threshold;
    }

    @Override
    public boolean test(@Nullable Float value) {
        if (value == null) {
            // null will only test true for UNKNOWN
            return threshold < 0;
        }

        return value <= threshold;
    }

    /**
     * Returns the Adventure color for this rating.
     *
     * @return the modern text color
     */
    public @Nonnull NamedTextColor getTextColor() {
        return color;
    }

    /**
     * Legacy Bukkit color compatibility accessor.
     *
     * @return the equivalent Bukkit chat color
     */
    @SuppressWarnings("deprecation")
    public @Nonnull ChatColor getColor() {
        if (color == NamedTextColor.DARK_GREEN) {
            return ChatColor.DARK_GREEN;
        } else if (color == NamedTextColor.GREEN) {
            return ChatColor.GREEN;
        } else if (color == NamedTextColor.YELLOW) {
            return ChatColor.YELLOW;
        } else if (color == NamedTextColor.RED) {
            return ChatColor.RED;
        } else if (color == NamedTextColor.DARK_RED) {
            return ChatColor.DARK_RED;
        } else {
            return ChatColor.WHITE;
        }
    }
}
