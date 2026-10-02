package audit;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LinkedKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LocationKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedSavingLooperTask;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedTask;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable real-controller/database probe with a deliberately controlled queue interleaving. */
public final class DelayedSaveProbe extends JavaPlugin {
    private static final String ID = "ENHANCED_CRAFTING_TABLE";
    private static final String OWNER = "2f017f3a-8442-4ef2-9fba-456789abcdef";

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                boolean original = Boolean.getBoolean("delayed.audit.original");
                boolean read = Boolean.getBoolean("delayed.audit.read");
                BlockDataController controller = Slimefun.getDatabaseManager().getBlockDataController();
                Location location = new Location(Bukkit.getWorlds().getFirst(), 8, 64, 8);
                Location neighbor = location.clone().add(1, 0, 0);
                if (read) {
                    verifyStored(controller, location, neighbor, original);
                } else {
                    writeControlledRace(controller, location, neighbor, original);
                }
                String marker = original ? "DELAYED_ORIGINAL_REPRODUCED" : "DELAYED_REPLACEMENT_PRESERVED";
                String phase = read ? "read" : "write";
                Files.writeString(Path.of("delayed-" + phase + "-result.txt"), marker + "\nphase=" + phase + "\n");
                getLogger().info(marker + " phase=" + phase);
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "DELAYED_REPLACEMENT_PROBE_FAILED", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 60L);
    }

    private static void writeControlledRace(BlockDataController controller, Location location, Location neighbor,
            boolean original) throws Exception {
        controller.setDelayedSavingEnable(false);
        location.getBlock().setType(Material.CRAFTING_TABLE);
        neighbor.getBlock().setType(Material.CRAFTING_TABLE);
        SlimefunBlockData data = controller.createBlock(location, ID);
        SlimefunBlockData other = controller.createBlock(neighbor, ID);
        data.setData("owner", OWNER);
        data.setData("opaque-count", "9007199254740993");
        data.setData("empty-value", "");
        data.setData("latest-value", "seed");
        data.setData("latest-empty", "seed");
        data.setData("deleted-value", "seed");
        other.setData("owner", "neighbor-unchanged");
        other.setData("latest-value", "neighbor-value");

        var looperField = BlockDataController.class.getDeclaredField("looperTask");
        looperField.setAccessible(true);
        TaskHandle prior = (TaskHandle) looperField.get(controller);
        if (prior != null) prior.cancel();
        controller.initDelayedSaving(Slimefun.instance(), 300, 300);

        Map<LinkedKey, DelayedTask> tasks = queue(controller);
        // Actual write submission adds DATA_VALUE to its mutable RecordKey; an earlier
        // deletion does not. Use deletion-to-write transitions so the old and replacement
        // queue keys genuinely remain equal through the observed completion window.
        data.removeData("latest-value");
        data.removeData("latest-empty");
        data.removeData("deleted-value");
        LinkedKey updatedKey = key(location, data.getKey(), "latest-value");
        LinkedKey emptyKey = key(location, data.getKey(), "latest-empty");
        LinkedKey deletedKey = key(location, data.getKey(), "deleted-value");
        wrap(tasks, updatedKey, () -> data.setData("latest-value", "second-snapshot"));
        wrap(tasks, emptyKey, () -> data.setData("latest-empty", ""));
        wrap(tasks, deletedKey, () -> {
            data.setData("deleted-value", "temporary");
            data.removeData("deleted-value");
        });
        DelayedSavingLooperTask looper;
        if (original) {
            looper = new DelayedSavingLooperTask(0, () -> new HashMap<>(tasks), tasks::remove);
        } else {
            var factory = BlockDataController.class.getDeclaredMethod("createDelayedSavingLooper", int.class);
            factory.setAccessible(true);
            looper = (DelayedSavingLooperTask) factory.invoke(controller, 0);
        }
        looper.run();
        require(tasks.containsKey(updatedKey) == !original, "Unexpected replacement-write queue state");
        require(tasks.containsKey(emptyKey) == !original, "Unexpected empty-string replacement queue state");
        require(tasks.containsKey(deletedKey) == !original, "Unexpected coalesced deletion queue state");
        looper.run();
        require(!tasks.containsKey(updatedKey) && !tasks.containsKey(emptyKey) && !tasks.containsKey(deletedKey),
                "Deferred replacements did not drain");
        require("second-snapshot".equals(data.getData("latest-value")), "Fixture did not update the live cache");
        require("".equals(data.getData("latest-empty")), "Fixture did not store the live empty value");
        require(data.getData("deleted-value") == null, "Fixture did not remove the live cache value");
        // Normal Slimefun shutdown drains the actual database writer.
    }

    private static void wrap(Map<LinkedKey, DelayedTask> tasks, LinkedKey key, Runnable replace) {
        DelayedTask original = Objects.requireNonNull(tasks.get(key), "Expected actual controller task");
        tasks.put(key, new DelayedTask(0, TimeUnit.SECONDS, () -> {
            require(original.runNow(), "Actual database submission failed");
            replace.run();
        }));
    }

    private static void verifyStored(BlockDataController controller, Location location, Location neighbor,
            boolean original) throws Exception {
        SlimefunBlockData data = Objects.requireNonNull(controller.getBlockData(location));
        SlimefunBlockData other = Objects.requireNonNull(controller.getBlockData(neighbor));
        if (!data.isDataLoaded()) controller.loadBlockData(data);
        if (!other.isDataLoaded()) controller.loadBlockData(other);
        require(ID.equals(data.getSfId()) && ID.equals(other.getSfId()), "Block identities changed");
        require(OWNER.equals(data.getData("owner")), "Owner value changed");
        require("9007199254740993".equals(data.getData("opaque-count")), "Opaque old value changed");
        require("".equals(data.getData("empty-value")), "Unrelated empty string changed");
        require("neighbor-unchanged".equals(other.getData("owner")), "Neighbor owner changed");
        require("neighbor-value".equals(other.getData("latest-value")), "Neighbor value changed");
        require(Objects.equals(original ? null : "second-snapshot", data.getData("latest-value")),
                "Persisted replacement write differs from the expected original/corrected behavior");
        require(Objects.equals(original ? null : "", data.getData("latest-empty")),
                "Persisted empty-string replacement differs from the expected original/corrected behavior");
        require(data.getData("deleted-value") == null, "Intentional deletion was resurrected");
        Files.writeString(Path.of("delayed-database-observation.txt"),
                "id=" + data.getSfId() + "\nowner=" + data.getData("owner")
                + "\nlatest-value=" + data.getData("latest-value")
                + "\nlatest-empty=" + data.getData("latest-empty")
                + "\nlatest-empty-present=" + data.getDataKeys().contains("latest-empty")
                + "\ndeleted-value=" + data.getData("deleted-value")
                + "\nopaque-count=" + data.getData("opaque-count")
                + "\nempty-value=" + data.getData("empty-value")
                + "\nneighbor=" + other.getAllData() + "\n");
    }

    @SuppressWarnings("unchecked") // Known controller field in a disposable regression server only.
    private static Map<LinkedKey, DelayedTask> queue(BlockDataController controller) throws Exception {
        var field = BlockDataController.class.getDeclaredField("delayedWriteTasks");
        field.setAccessible(true);
        return (Map<LinkedKey, DelayedTask>) field.get(controller);
    }

    private static LinkedKey key(Location location, String storedLocation, String name) {
        var record = new RecordKey(DataScope.BLOCK_DATA);
        record.addCondition(FieldKey.LOCATION, storedLocation);
        record.addCondition(FieldKey.DATA_KEY, name);
        return new LinkedKey(new LocationKey(DataScope.NONE, location), record);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
