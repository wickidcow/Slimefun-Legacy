package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import city.norain.slimefun4.utils.SlimefunPoolExecutor;
import city.norain.slimefun4.utils.StringUtil;
import com.google.common.util.concurrent.AtomicDouble;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.apache.commons.lang.Validate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * The {@link SlimefunProfiler} works closely to the {@link TickerTask} and is
 * responsible for monitoring that task.
 * It collects timings data for any ticked {@link Block} and the corresponding {@link SlimefunItem}.
 * This allows developers to identify laggy {@link SlimefunItem SlimefunItems} or {@link SlimefunAddon SlimefunAddons}.
 * But it also enables Server Admins to locate lag-inducing areas on the {@link Server}.
 *
 * @author TheBusyBiscuit
 * @see TickerTask
 */
public class SlimefunProfiler {

    /**
     * A minecraft server tick is 50ms and Slimefun ticks are stretched
     * across two ticks (sync and async blocks), so we use 100ms as a reference here
     */
    private static final int MAX_TICK_DURATION = 100;
    private static final int MAX_FINISH_WAIT_ITERATIONS = 4000;

    /**
     * Our internal instance of {@link SlimefunThreadFactory}, it provides the naming
     * convention for our {@link Thread} pool and also the count of this pool.
     */
    private final SlimefunThreadFactory threadFactory = new SlimefunThreadFactory(2);

    /**
     * This is our {@link Thread} pool to evaluate timings data.
     * We cannot use the {@link BukkitScheduler} here because we need to evaluate
     * this data in split seconds.
     * So we cannot simply wait until the next server tick for this.
     */
    private final ExecutorService executor =
            Executors.newFixedThreadPool(threadFactory.getThreadCount(), threadFactory);

    /**
     * All possible values of {@link PerformanceRating}.
     * We cache these for fast access since Enum#values creates
     * an array everytime it is called.
     */
    private final PerformanceRating[] performanceRatings = PerformanceRating.values();

    /**
     * This boolean marks whether we are currently profiling or not.
     */
    @Getter
    private volatile boolean isProfiling = false;

    /**
     * True while the active sample only needs aggregate analytics telemetry.
     */
    private volatile boolean telemetryProfiling = false;

    /**
     * Prevents a new sample from reusing profiler state while delayed entries from the
     * previous sample are still being finalized.
     */
    private volatile boolean finishing = false;
    private volatile boolean pendingExplicitStart = false;

    /**
     * This {@link AtomicInteger} holds the amount of detailed blocks that still need to be
     * profiled.
     */
    private final AtomicInteger queued = new AtomicInteger(0);

    private final AtomicInteger telemetryQueued = new AtomicInteger(0);
    private final AtomicLong telemetryElapsedTime = new AtomicLong();
    private final AtomicInteger telemetryEntries = new AtomicInteger();

    private final List<SlimefunPoolExecutor> threadPools = new CopyOnWriteArrayList<>();

    private long totalElapsedTime;

    private final Map<ProfiledBlock, Long> timings = new ConcurrentHashMap<>();
    private final Queue<PerformanceInspector> requests = new ConcurrentLinkedQueue<>();

    private final AtomicLong totalMsTicked = new AtomicLong();
    private final AtomicInteger millisecondSamples = new AtomicInteger();
    private final AtomicLong totalNsTicked = new AtomicLong();
    private final AtomicInteger nanosecondSamples = new AtomicInteger();
    private final AtomicDouble averageTimingsPerMachine = new AtomicDouble();

    /**
     * This method terminates the {@link SlimefunProfiler}.
     * We need to call this method when the {@link Server} shuts down to prevent any
     * of our {@link Thread Threads} from being kept alive.
     */
    public void kill() {
        executor.shutdown();
    }

    /**
     * This method starts detailed profiling, data from previous detailed runs will be cleared.
     */
    public synchronized void start() {
        if (finishing) {
            // Preserve explicit start semantics without allowing two generations to share queues/maps.
            pendingExplicitStart = true;
            return;
        }

        startDetailed();
    }

    private void startDetailed() {
        telemetryProfiling = false;
        isProfiling = true;
        queued.set(0);
        timings.clear();
    }

    /**
     * Starts a lightweight aggregate-only sample for periodic analytics telemetry.
     * This mode avoids creating {@link ProfiledBlock} instances and executor tasks for every entry.
     * Existing detailed timing data remains untouched.
     */
    @SlimefunInternal
    public synchronized void startTelemetry() {
        if (isProfiling || finishing) {
            return;
        }

        telemetryElapsedTime.set(0L);
        telemetryEntries.set(0);
        telemetryQueued.set(0);
        telemetryProfiling = true;
        isProfiling = true;
    }

    /**
     * Starts a ticker-cycle sample only when a summary is waiting to be produced.
     * Explicit calls to {@link #start()} remain unconditional for compatibility.
     *
     * @return whether the current ticker cycle should collect detailed profiler entries
     */
    public boolean startIfRequested() {
        if (isProfiling) {
            return !telemetryProfiling;
        }

        if (finishing || requests.isEmpty()) {
            return false;
        }

        synchronized (this) {
            if (isProfiling) {
                return !telemetryProfiling;
            }

            if (finishing || requests.isEmpty()) {
                return false;
            }

            startDetailed();
            return true;
        }
    }

    /**
     * This method starts a new profiler entry.
     *
     * @return A timestamp, best fed back into {@link #closeEntry(Location, SlimefunItem, long)}
     */
    public long newEntry() {
        if (!isProfiling) {
            return 0;
        }

        if (telemetryProfiling) {
            telemetryQueued.incrementAndGet();
        } else {
            queued.incrementAndGet();
        }
        return System.nanoTime();
    }

    /**
     * This method schedules a given amount of entries for the future.
     * Be careful to {@link #closeEntry(Location, SlimefunItem, long)} all of them again!
     * No {@link PerformanceSummary} will be sent until all entries were closed.
     *
     * If the specified amount is negative, scheduled entries will be removed
     *
     * @param amount The amount of entries that should be scheduled. Can be negative
     */
    public void scheduleEntries(int amount) {
        if (isProfiling) {
            if (telemetryProfiling) {
                telemetryQueued.getAndAdd(amount);
            } else {
                queued.getAndAdd(amount);
            }
        }
    }

    /**
     * Cancels one profiler entry that was scheduled or opened but could not be
     * completed, for example when a ticker's {@code update()} method throws.
     * Keeping this count accurate prevents profiler reports from waiting until
     * timeout for a sample that will never arrive.
     */
    public void cancelScheduledEntry() {
        if (telemetryProfiling) {
            telemetryQueued.updateAndGet(value -> Math.max(0, value - 1));
        } else {
            queued.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    int getQueuedEntries() {
        return telemetryProfiling ? telemetryQueued.get() : queued.get();
    }

    /**
     * This method closes a previously started entry.
     * Make sure to call {@link #newEntry()} to get the timestamp in advance.
     *
     * @param l         The {@link Location} of our {@link Block}
     * @param item      The {@link SlimefunItem} at this {@link Location}
     * @param timestamp The timestamp marking the start of this entry, you can retrieve it using {@link #newEntry()}
     * @return The total timings of this entry
     */
    public long closeEntry(@Nonnull Location l, @Nonnull SlimefunItem item, long timestamp) {
        if (timestamp == 0) {
            return 0;
        }

        Validate.notNull(l, "Location must not be null!");
        Validate.notNull(item, "You need to specify a SlimefunItem!");

        long elapsedTime = System.nanoTime() - timestamp;

        if (telemetryProfiling) {
            telemetryElapsedTime.addAndGet(elapsedTime);
            telemetryEntries.incrementAndGet();
            telemetryQueued.decrementAndGet();
            return elapsedTime;
        }

        executor.execute(() -> {
            ProfiledBlock block = new ProfiledBlock(l, item);

            // Merge (if we have multiple samples for whatever reason)
            timings.merge(block, elapsedTime, Long::sum);
            queued.decrementAndGet();
        });

        return elapsedTime;
    }

    /**
     * This stops the active profiling sample.
     */
    public synchronized void stop() {
        if (!isProfiling) {
            return;
        }

        boolean telemetry = telemetryProfiling;
        isProfiling = false;
        finishing = true;

        if (Slimefun.instance() == null || !Slimefun.instance().isEnabled()) {
            // Slimefun has been disabled
            completeProfileCycle();
            return;
        }

        executor.execute(telemetry ? this::finishTelemetry : this::finishReport);
    }

    public void registerPool(SlimefunPoolExecutor executor) {
        Validate.notNull(executor, "Cannot register a null SlimefunPoolExecutor");

        if (threadPools.contains(executor)) {
            // Already registered
            return;
        }

        threadPools.add(executor);
    }

    private void finishTelemetry() {
        int iterations = MAX_FINISH_WAIT_ITERATIONS;
        while (telemetryQueued.get() > 0 && iterations-- > 0) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Slimefun.logger().log(Level.SEVERE, "A Profiler Thread was interrupted", e);
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (telemetryQueued.get() > 0) {
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Aggregate profiler telemetry timed out while waiting for {0} entries.",
                            telemetryQueued.get());
        }

        long elapsedTime = telemetryElapsedTime.get();
        int entries = telemetryEntries.get();
        averageTimingsPerMachine.getAndSet(entries == 0 ? 0 : (double) elapsedTime / entries);
        recordAggregateSample(elapsedTime);
        completeProfileCycle();
    }

    private void finishReport() {
        // We will only wait for a maximum of this many 1ms sleeps
        int iterations = MAX_FINISH_WAIT_ITERATIONS;

        // Wait for all timing results to come in
        while (!isProfiling && queued.get() > 0) {
            try {
                /*
                 * Since we got more than one Thread in our pool,
                 * blocking this one is (hopefully) completely fine
                 */
                Thread.sleep(1);
                iterations--;

                // If we waited for too long, then we should just abort
                if (iterations <= 0) {
                    Iterator<PerformanceInspector> iterator = requests.iterator();

                    while (iterator.hasNext()) {
                        iterator.next()
                                .sendMessage("Your timings report has timed out, we were still waiting for "
                                        + queued.get() + " samples to be collected :/");
                        iterator.remove();
                    }

                    completeProfileCycle();
                    return;
                }
            } catch (InterruptedException e) {
                Slimefun.logger().log(Level.SEVERE, "A Profiler Thread was interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        if (isProfiling) {
            // A new profiling cycle has already started. start() cleared the live timings map, so
            // reporting now would produce an empty or mixed-cycle summary. Leave pending requests
            // queued for the active cycle instead.
            return;
        }

        totalElapsedTime = timings.values().stream().mapToLong(Long::longValue).sum();

        averageTimingsPerMachine.getAndSet(
                timings.values().stream().mapToLong(Long::longValue).average().orElse(0));

        recordAggregateSample(totalElapsedTime);

        if (!requests.isEmpty()) {
            PerformanceSummary summary = new PerformanceSummary(this, totalElapsedTime, timings.size());
            Iterator<PerformanceInspector> iterator = requests.iterator();

            while (iterator.hasNext()) {
                summary.send(iterator.next());
                iterator.remove();
            }
        }

        completeProfileCycle();
    }

    private void recordAggregateSample(long elapsedTime) {
        /*
         * We log how many milliseconds have been ticked, and how many ticks have passed
         * so AnalyticsService can retrieve the averages without walking detailed timings.
         */
        totalMsTicked.addAndGet(TimeUnit.NANOSECONDS.toMillis(elapsedTime));
        millisecondSamples.incrementAndGet();
        totalNsTicked.addAndGet(elapsedTime);
        nanosecondSamples.incrementAndGet();
    }

    private synchronized void completeProfileCycle() {
        finishing = false;
        telemetryProfiling = false;

        if (pendingExplicitStart) {
            pendingExplicitStart = false;
            startDetailed();
        }
    }

    /**
     * This method requests a summary for the given {@link PerformanceInspector}.
     * The summary will be sent upon the next available moment in time.
     *
     * @param inspector The {@link PerformanceInspector} who shall receive this summary.
     */
    public void requestSummary(@Nonnull PerformanceInspector inspector) {
        Validate.notNull(inspector, "Cannot request a summary for null");

        requests.add(inspector);
    }

    @Nonnull
    protected Map<String, Long> getByItem() {
        Map<String, Long> map = new HashMap<>();

        for (Map.Entry<ProfiledBlock, Long> entry : timings.entrySet()) {
            map.merge(entry.getKey().getId(), entry.getValue(), Long::sum);
        }

        return map;
    }

    @Nonnull
    protected Map<String, Long> getByPlugin() {
        Map<String, Long> map = new HashMap<>();

        for (Map.Entry<ProfiledBlock, Long> entry : timings.entrySet()) {
            map.merge(entry.getKey().getAddon().getName(), entry.getValue(), Long::sum);
        }

        return map;
    }

    @Nonnull
    protected Map<String, Long> getByChunk() {
        Map<String, Long> map = new HashMap<>();

        for (Map.Entry<ProfiledBlock, Long> entry : timings.entrySet()) {
            ProfiledBlock block = entry.getKey();
            String world = block.getWorld().getName();
            int x = block.getChunkX();
            int z = block.getChunkZ();

            map.merge(world + " (" + x + ',' + z + ')', entry.getValue(), Long::sum);
        }

        return map;
    }

    /**
     * Returns the highest-cost individual machine in a profiled chunk.
     * The returned value is formatted as x,y,z so administrators can teleport
     * directly to a real ticking block while still retaining the chunk summary.
     */
    @Nonnull
    protected String getHottestBlockInChunk(@Nonnull String chunk) {
        Validate.notNull(chunk, "The chunk cannot be null!");

        ProfiledBlock hottestBlock = null;
        long hottestTiming = Long.MIN_VALUE;

        for (Map.Entry<ProfiledBlock, Long> entry : timings.entrySet()) {
            ProfiledBlock block = entry.getKey();
            String world = block.getWorld().getName();
            String blockChunk = world + " (" + block.getChunkX() + ',' + block.getChunkZ() + ')';

            if (chunk.equals(blockChunk) && entry.getValue() > hottestTiming) {
                hottestBlock = block;
                hottestTiming = entry.getValue();
            }
        }

        if (hottestBlock == null) {
            return "";
        }

        return hottestBlock.getX() + "," + hottestBlock.getY() + "," + hottestBlock.getZ();
    }

    protected int getBlocksInChunk(@Nonnull String chunk) {
        Validate.notNull(chunk, "The chunk cannot be null!");
        int blocks = 0;

        for (ProfiledBlock block : timings.keySet()) {
            String world = block.getWorld().getName();
            int x = block.getChunkX();
            int z = block.getChunkZ();

            if (chunk.equals(world + " (" + x + ',' + z + ')')) {
                blocks++;
            }
        }

        return blocks;
    }

    protected int getBlocksOfId(@Nonnull String id) {
        Validate.notNull(id, "The id cannot be null!");
        int blocks = 0;

        for (ProfiledBlock block : timings.keySet()) {
            if (block.getId().equals(id)) {
                blocks++;
            }
        }

        return blocks;
    }

    protected int getBlocksFromPlugin(@Nonnull String pluginName) {
        Validate.notNull(pluginName, "The Plugin name cannot be null!");
        int blocks = 0;

        for (ProfiledBlock block : timings.keySet()) {
            if (block.getAddon().getName().equals(pluginName)) {
                blocks++;
            }
        }

        return blocks;
    }

    protected float getPercentageOfTick() {
        float millis = totalElapsedTime / 1000000.0F;
        float fraction = (millis * 100.0F) / MAX_TICK_DURATION;

        return Math.round((fraction * 100.0F) / 100.0F);
    }

    /**
     * This method returns the current {@link PerformanceRating}.
     *
     * @return The current performance grade
     */
    @Nonnull
    public PerformanceRating getPerformance() {
        float percentage = getPercentageOfTick();

        for (PerformanceRating rating : performanceRatings) {
            if (rating.test(percentage)) {
                return rating;
            }
        }

        return PerformanceRating.UNKNOWN;
    }

    @Nonnull
    public String getTime() {
        return NumberUtils.getAsMillis(totalElapsedTime);
    }

    public int getTickRate() {
        return Slimefun.getTickerTask().getTickRate();
    }

    /**
     * This method checks whether the {@link SlimefunProfiler} has collected timings on
     * the given {@link Block}
     *
     * @param b The {@link Block}
     * @return Whether timings of this {@link Block} have been collected
     */
    public boolean hasTimings(@Nonnull Block b) {
        Validate.notNull(b, "Cannot get timings for a null Block");

        return timings.containsKey(new ProfiledBlock(b));
    }

    public String getTime(@Nonnull Block b) {
        Validate.notNull(b, "Cannot get timings for a null Block");

        long time = timings.getOrDefault(new ProfiledBlock(b), 0L);
        return NumberUtils.getAsMillis(time);
    }

    public String getTime(@Nonnull Chunk chunk) {
        Validate.notNull(chunk, "Cannot get timings for a null Chunk");

        long time = getByChunk()
                .getOrDefault(chunk.getWorld().getName() + " (" + chunk.getX() + ',' + chunk.getZ() + ')', 0L);
        return NumberUtils.getAsMillis(time);
    }

    public String getTime(@Nonnull SlimefunItem item) {
        Validate.notNull(item, "Cannot get timings for a null SlimefunItem");

        long time = getByItem().getOrDefault(item.getId(), 0L);
        return NumberUtils.getAsMillis(time);
    }

    /**
     * Get and reset the average millisecond timing for this {@link SlimefunProfiler}.
     *
     * @return The average millisecond timing for this {@link SlimefunProfiler}.
     */
    public long getAndResetAverageTimings() {
        long total = totalMsTicked.getAndSet(0);
        int samples = millisecondSamples.getAndSet(0);
        return samples == 0 ? 0 : total / samples;
    }

    /**
     * Get and reset the average nanosecond timing for this {@link SlimefunProfiler}.
     *
     * @return The average nanosecond timing for this {@link SlimefunProfiler}.
     */
    public double getAndResetAverageNanosecondTimings() {
        long total = totalNsTicked.getAndSet(0);
        int samples = nanosecondSamples.getAndSet(0);
        return samples == 0 ? 0 : (double) total / samples;
    }

    /**
     * Get and reset the average millisecond timing for each machine.
     *
     * @return The average millisecond timing for each machine.
     */
    public double getAverageTimingsPerMachine() {
        return averageTimingsPerMachine.getAndSet(0);
    }

    public String getThreadPoolStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Thread pool state [ Running | Completed | Total Tasks | Queue Size ]\n");

        for (SlimefunPoolExecutor executor : threadPools) {
            sb.append(executor.getName())
                    .append(" (")
                    .append(executor.getCorePoolSize())
                    .append(" / ")
                    .append(executor.getMaximumPoolSize())
                    .append(") ")
                    .append(": ")
                    .append("\n")
                    .append(executor.getActiveCount())
                    .append(" | ")
                    .append(executor.getCompletedTaskCount())
                    .append(" | ")
                    .append(executor.getTaskCount())
                    .append(" | ")
                    .append(executor.getQueue().size())
                    .append("\n");
        }

        return sb.toString();
    }

    public String snapshotThreads() {
        StringBuilder sb = new StringBuilder();
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (SlimefunPoolExecutor threadPool : threadPools) {
            for (long id : threadPool.getRunningThreads()) {
                sb.append(StringUtil.formatDetailedThreadInfo(threadMXBean.getThreadInfo(id, 100)))
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
