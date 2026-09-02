package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight cumulative timing buckets for Energy Regulator network hot paths. */
public final class EnergyNetPerformance {

    enum Section {
        TOTAL("Total network tick"),
        DISCOVERY("Discovery/classification"),
        GENERATORS("Generators"),
        CAPACITORS("Capacitor supply"),
        CONSUMERS("Consumers"),
        REDISTRIBUTION("Storage redistribution"),
        TRANSPORT("Transport state"),
        HOLOGRAM("Hologram");

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

    private EnergyNetPerformance() {}

    static void record(Section section, long nanos) {
        if (nanos < 0L) {
            return;
        }
        NANOS.get(section).add(nanos);
        SAMPLES.get(section).increment();
    }

    public static List<Entry> snapshot(boolean reset) {
        List<Entry> entries = new ArrayList<>(Section.values().length);
        for (Section section : Section.values()) {
            long nanos = reset ? NANOS.get(section).sumThenReset() : NANOS.get(section).sum();
            long samples = reset ? SAMPLES.get(section).sumThenReset() : SAMPLES.get(section).sum();
            entries.add(new Entry(section.displayName(), nanos, samples));
        }
        return List.copyOf(entries);
    }

    public record Entry(String name, long nanos, long samples) {
        public double totalMillis() {
            return nanos / 1_000_000.0D;
        }

        public double averageMillis() {
            return samples <= 0L ? 0.0D : totalMillis() / samples;
        }
    }
}
