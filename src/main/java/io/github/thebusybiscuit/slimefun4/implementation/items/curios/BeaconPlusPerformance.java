package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight cumulative timing buckets for Resonance Beacon hot paths. */
final class BeaconPlusPerformance {

    enum Section {
        STATE("State/cache"),
        ENERGY("Energy"),
        VISUALS("Visuals"),
        PLAYERS("Player effects"),
        MONSTERS("Monster effects"),
        GRAVITY("Gravity Well"),
        TILES("Furnace/spawner"),
        CROPS("Crops");

        private final String displayName;

        Section(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    private static final EnumMap<Section, LongAdder> NANOS = new EnumMap<>(Section.class);
    private static final EnumMap<Section, LongAdder> SAMPLES = new EnumMap<>(Section.class);

    static {
        for (Section section : Section.values()) {
            NANOS.put(section, new LongAdder());
            SAMPLES.put(section, new LongAdder());
        }
    }

    private BeaconPlusPerformance() {}

    static void record(Section section, long nanos) {
        if (nanos < 0L) {
            return;
        }
        NANOS.get(section).add(nanos);
        SAMPLES.get(section).increment();
    }

    static List<Entry> snapshot(boolean reset) {
        List<Entry> entries = new ArrayList<>(Section.values().length);
        for (Section section : Section.values()) {
            long nanos = reset ? NANOS.get(section).sumThenReset() : NANOS.get(section).sum();
            long samples = reset ? SAMPLES.get(section).sumThenReset() : SAMPLES.get(section).sum();
            entries.add(new Entry(section.displayName(), nanos, samples));
        }
        return List.copyOf(entries);
    }

    static void reset() {
        for (Section section : Section.values()) {
            NANOS.get(section).reset();
            SAMPLES.get(section).reset();
        }
    }

    record Entry(String name, long nanos, long samples) {
        double totalMillis() {
            return nanos / 1_000_000.0D;
        }

        double averageMillis() {
            return samples <= 0L ? 0.0D : totalMillis() / samples;
        }
    }
}
