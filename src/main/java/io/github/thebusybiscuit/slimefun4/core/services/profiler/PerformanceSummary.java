package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import io.github.thebusybiscuit.slimefun4.core.services.profiler.inspectors.PlayerPerformanceInspector;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class PerformanceSummary {

    // The threshold at which a Block or Chunk is significant enough to appear in /sf timings
    private static final int VISIBILITY_THRESHOLD = 260_000;
    private static final int MIN_ITEMS = 6;
    private static final int MAX_ITEMS = 20;

    private final SlimefunProfiler profiler;
    private final PerformanceRating rating;
    private final long totalElapsedTime;
    private final int totalTickedBlocks;
    private final float percentage;
    private final int tickRate;

    private final Map<String, Long> chunks;
    private final Map<String, Long> plugins;
    private final Map<String, Long> items;

    PerformanceSummary(@Nonnull SlimefunProfiler profiler, long totalElapsedTime, int totalTickedBlocks) {
        this.profiler = profiler;
        this.rating = profiler.getPerformance();
        this.percentage = profiler.getPercentageOfTick();
        this.totalElapsedTime = totalElapsedTime;
        this.totalTickedBlocks = totalTickedBlocks;
        this.tickRate = profiler.getTickRate();

        chunks = profiler.getByChunk();
        plugins = profiler.getByPlugin();
        items = profiler.getByItem();
    }

    public void send(@Nonnull PerformanceInspector sender) {
        sender.sendMessage("");
        sender.sendMessage(legacy(Component.text("===== Slimefun Performance Profiler =====", NamedTextColor.GREEN)));
        sender.sendMessage(legacy(Component.text("Total tick time: ", NamedTextColor.GOLD)
                .append(Component.text(NumberUtils.getAsMillis(totalElapsedTime), NamedTextColor.YELLOW))));
        sender.sendMessage(legacy(Component.text("Ticker runtime: ", NamedTextColor.GOLD)
                .append(Component.text(
                        NumberUtils.roundDecimalNumber(tickRate / 20.0) + "s (" + tickRate + " ticks)",
                        NamedTextColor.YELLOW))));
        sender.sendMessage(legacy(Component.text("Performance rating: ", NamedTextColor.GOLD)
                .append(getPerformanceRating())));
        sender.sendMessage("");

        summarizeTimings(totalTickedBlocks, "block", sender, items, profiler::getBlocksOfId, entry -> {
            int count = profiler.getBlocksOfId(entry.getKey());
            String time = NumberUtils.getAsMillis(entry.getValue());
            String message = entry.getKey() + " - " + count + "x (%s)";
            SlimefunProfiler.ItemTimingStats stats = profiler.getItemTimingStats(entry.getKey());
            String distribution = " | P95: " + NumberUtils.getAsMillis(stats.p95Nanos())
                    + " | Max: " + NumberUtils.getAsMillis(stats.maxNanos());
            if (!stats.hottestLocation().isEmpty()) {
                distribution += " @ " + stats.hottestLocation();
            }

            if (count <= 1) {
                return String.format(message, time + distribution);
            }

            String average = NumberUtils.getAsMillis(entry.getValue() / count);

            if (sender.getOrderType() == SummaryOrderType.AVERAGE) {
                return String.format(message, average + " | Total: " + time + distribution);
            } else {
                return String.format(message, time + " | Average: " + average + distribution);
            }
        });

        sendPhaseDiagnostics(sender);

        summarizeTimings(chunks.size(), "chunk", sender, chunks, profiler::getBlocksInChunk, entry -> {
            int count = profiler.getBlocksInChunk(entry.getKey());
            String time = NumberUtils.getAsMillis(entry.getValue());
            String hotspot = profiler.getHottestBlockInChunk(entry.getKey());
            String chunkLabel = formatChunkLabel(entry.getKey());
            String hotspotLabel = hotspot.isEmpty() ? "" : " | hotspot " + hotspot;

            return chunkLabel + hotspotLabel + " - " + count + " block" + (count != 1 ? 's' : "") + " (" + time + ")";
        });

        summarizeTimings(plugins.size(), "plugin", sender, plugins, profiler::getBlocksFromPlugin, entry -> {
            int count = profiler.getBlocksFromPlugin(entry.getKey());
            String total = NumberUtils.getAsMillis(entry.getValue());
            String average = NumberUtils.getAsMillis(entry.getValue() / Math.max(1, count));
            SlimefunProfiler.PluginTimingStats stats = profiler.getPluginTimingStats(entry.getKey());
            String hotspot = "";
            if (!stats.hottestItemId().isEmpty()) {
                hotspot = " | Hotspot: " + stats.hottestItemId();
                if (!stats.hottestLocation().isEmpty()) {
                    hotspot += " @ " + stats.hottestLocation();
                }
            }

            return entry.getKey() + " - " + count + " block" + (count != 1 ? 's' : "")
                    + " (" + total + " | Avg: " + average
                    + " | P95: " + NumberUtils.getAsMillis(stats.p95Nanos())
                    + " | Max: " + NumberUtils.getAsMillis(stats.maxNanos())
                    + hotspot + ")";
        });

        if (sender.isVerbose()) {
            sender.sendMessage("");
            sender.sendMessage(profiler.getThreadPoolStatus());
        }
    }

    private void sendPhaseDiagnostics(@Nonnull PerformanceInspector sender) {
        Map<String, List<SlimefunProfiler.PhaseTimingStats>> groups = profiler.getPhaseTimingStats();
        if (groups.isEmpty()) {
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(legacy(Component.text("Internal phase diagnostics", NamedTextColor.GOLD)));

        for (Map.Entry<String, List<SlimefunProfiler.PhaseTimingStats>> group : groups.entrySet()) {
            sender.sendMessage(legacy(Component.text(group.getKey(), NamedTextColor.YELLOW)));
            int shown = 0;
            for (SlimefunProfiler.PhaseTimingStats phase : group.getValue()) {
                if (shown++ >= 8) {
                    break;
                }

                long samples = Math.max(1L, phase.samples());
                sender.sendMessage(legacy(Component.text("  " + phase.phase() + " - ", NamedTextColor.GRAY)
                        .append(Component.text(NumberUtils.getAsMillis(phase.totalNanos()), NamedTextColor.YELLOW))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(phase.samples() + "x", NamedTextColor.GRAY))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(
                                "avg " + NumberUtils.getAsMillis(phase.totalNanos() / samples),
                                NamedTextColor.GRAY))));
            }
        }
    }

    @Nonnull
    private String formatChunkLabel(@Nonnull String chunk) {
        int coordinatesStart = chunk.lastIndexOf(" (");
        if (coordinatesStart <= 0) {
            return chunk;
        }

        return chunk.substring(0, coordinatesStart) + " chunk" + chunk.substring(coordinatesStart);
    }

    @ParametersAreNonnullByDefault
    private void summarizeTimings(
            int count,
            String name,
            PerformanceInspector inspector,
            Map<String, Long> map,
            ToIntFunction<String> sampleCountResolver,
            Function<Map.Entry<String, Long>, String> formatter) {
        Set<Entry<String, Long>> entrySet = map.entrySet();
        List<Entry<String, Long>> results = inspector.getOrderType().sort(entrySet, sampleCountResolver);
        String prefix = count + " " + name + (count != 1 ? 's' : "");

        if (inspector instanceof PlayerPerformanceInspector playerPerformanceInspector) {
            Component component = summarizeAsComponent(count, prefix, results, formatter);
            playerPerformanceInspector.sendMessage(component);
        } else {
            String text = summarizeAsString(inspector, count, prefix, results, formatter);
            inspector.sendMessage(text);
        }
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    private Component summarizeAsComponent(
            int count,
            String prefix,
            List<Map.Entry<String, Long>> results,
            Function<Entry<String, Long>, String> formatter) {
        Component component = Component.text(prefix, NamedTextColor.YELLOW);

        if (count > 0) {
            Component hoverText = Component.empty();
            int shownEntries = 0;
            int hiddenEntries = 0;

            for (Map.Entry<String, Long> entry : results) {
                if (shownEntries < MAX_ITEMS && (shownEntries < MIN_ITEMS || entry.getValue() > VISIBILITY_THRESHOLD)) {
                    hoverText = hoverText
                            .append(Component.newline())
                            .append(Component.text(formatter.apply(entry), NamedTextColor.YELLOW));
                    shownEntries++;
                } else {
                    hiddenEntries++;
                }
            }

            if (hiddenEntries > 0) {
                hoverText = hoverText
                        .append(Component.newline())
                        .append(Component.newline())
                        .append(Component.text("+ ", NamedTextColor.RED))
                        .append(Component.text(hiddenEntries + " more", NamedTextColor.GOLD));
            }

            Component hoverComponent = Component.text("  (Hover here for more info)", NamedTextColor.GRAY)
                    .hoverEvent(HoverEvent.showText(hoverText));
            component = component.append(hoverComponent);
        }

        return component;
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    private String summarizeAsString(
            PerformanceInspector inspector,
            int count,
            String prefix,
            List<Entry<String, Long>> results,
            Function<Entry<String, Long>, String> formatter) {
        int shownEntries = 0;
        int hiddenEntries = 0;

        StringBuilder builder = new StringBuilder(legacy(Component.text(prefix, NamedTextColor.GOLD)));

        if (count > 0) {
            for (Map.Entry<String, Long> entry : results) {
                if (inspector.isVerbose()
                        || (shownEntries < MAX_ITEMS
                                && (shownEntries < MIN_ITEMS || entry.getValue() > VISIBILITY_THRESHOLD))) {
                    builder.append(legacy(Component.text(
                            "\n  " + plainLegacy(formatter.apply(entry)),
                            NamedTextColor.YELLOW)));
                    shownEntries++;
                } else {
                    hiddenEntries++;
                }
            }

            if (hiddenEntries > 0) {
                builder.append(legacy(Component.text(
                        "\n+ " + hiddenEntries + " more...",
                        NamedTextColor.YELLOW)));
            }
        }

        return builder.toString();
    }

    @Nonnull
    private Component getPerformanceRating() {
        int filled = Math.min(20, Math.max(0, (int) Math.min(percentage, 100) / 5));
        int rest = 20 - filled;

        return Component.text(":".repeat(filled), percentageColor(100 - Math.min(percentage, 100)))
                .append(Component.text(":".repeat(rest) + " - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(ChatUtils.humanize(rating.name()), ratingColor(rating)))
                .append(Component.text(
                        " (" + NumberUtils.roundDecimalNumber(percentage) + "%)",
                        NamedTextColor.GRAY));
    }

    private static NamedTextColor percentageColor(float percentage) {
        if (percentage < 16.0F) {
            return NamedTextColor.DARK_RED;
        } else if (percentage < 32.0F) {
            return NamedTextColor.RED;
        } else if (percentage < 48.0F) {
            return NamedTextColor.GOLD;
        } else if (percentage < 64.0F) {
            return NamedTextColor.YELLOW;
        } else if (percentage < 80.0F) {
            return NamedTextColor.DARK_GREEN;
        } else {
            return NamedTextColor.GREEN;
        }
    }

    private static NamedTextColor ratingColor(PerformanceRating rating) {
        return switch (rating) {
            case UNKNOWN -> NamedTextColor.WHITE;
            case GOOD, FINE -> NamedTextColor.DARK_GREEN;
            case OKAY -> NamedTextColor.GREEN;
            case MODERATE -> NamedTextColor.YELLOW;
            case SEVERE -> NamedTextColor.RED;
            case HURTFUL, BAD -> NamedTextColor.DARK_RED;
        };
    }

    private static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private static String plainLegacy(String value) {
        return PlainTextComponentSerializer.plainText()
                .serialize(LegacyComponentSerializer.legacySection().deserialize(value));
    }
}
