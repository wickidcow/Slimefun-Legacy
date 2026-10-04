package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.PersistedItemStorageMaintenance;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
import io.github.thebusybiscuit.slimefun4.core.services.ResourcePackOwnershipMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Explicit, restart-resumable installation/removal of the ID pack and known Legacy model overrides. */
public final class ResourcePackDoctorService {
    public static final String ID_PACK_URL =
            "https://github.com/wickidcow/SFL_RP_Official/releases/download/v4.1.0-id-preview.1/SlimefunLegacyRP.zip";
    public static final String ID_PACK_SHA1 = "0d667fe74db4a9456aa5a603d71921bfc5a0d6fe";
    private final Slimefun plugin;
    private final ItemDoctorService doctor;
    private final AtomicBoolean sweepActive = new AtomicBoolean();
    private volatile ResourcePackModelExecutor deferredExecutor;
    private volatile Job job;
    private volatile boolean stopping;

    ResourcePackDoctorService(Slimefun plugin, ItemDoctorService doctor) {
        this.plugin = plugin;
        this.doctor = doctor;
    }

    void register() {
        Bukkit.getPluginManager()
                .registerEvents((Listener) new ResourcePackDoctorListener(this.plugin, this), (Plugin) this.plugin);
        Path state = this.stateFile();
        if (!Files.exists(state, new LinkOption[0])) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(state.toFile());
            if (yaml.getInt("schema") != 1) {
                throw new IOException("Unsupported Doctor operation state");
            }
            String operation = yaml.getString("operation", "");
            if (!operation.equals("install") && !operation.equals("uninstall")) {
                throw new IOException("Invalid Doctor operation");
            }
            String phase = yaml.getString("phase", "prepared");
            if (!List.of("prepared", "awaiting-restart", "active").contains(phase)) {
                throw new IOException("Invalid Doctor operation phase");
            }
            Path root = this.plugin
                    .getDataFolder()
                    .toPath()
                    .resolve("backups/resource-pack-doctor")
                    .toAbsolutePath();
            Path backup = root.resolve(yaml.getString("backup", "")).normalize();
            if (!backup.getParent().equals(root) || !Files.isDirectory(backup, new LinkOption[0])) {
                throw new IOException("Doctor operation backup is missing or outside its backup directory");
            }
            this.job = new Job(
                    operation,
                    ResourcePackOwnershipMode.parse(yaml.getString("ownership")),
                    phase,
                    new ResourcePackDoctorBackup(backup));
            Slimefun.getSchedulerService().runLater(this::resumeAfterStartup, 40L);
        } catch (Exception | LinkageError failure) {
            this.plugin
                    .getLogger()
                    .log(Level.SEVERE, "Resource-pack Doctor could not resume; stored items were preserved.", failure);
        }
    }

    private void resumeAfterStartup() {
        if (this.stopping || this.job == null) {
            return;
        }
        if (!Slimefun.getRegistryRuntimeService().getSnapshot().isInitialRegistrationFinalized()) {
            this.plugin
                    .getLogger()
                    .warning(
                            "Resource-pack Doctor is waiting for item registration. Use /sf doctor resource-pack resume.");
            return;
        }
        BeginResult result = this.resume(report -> this.plugin.getLogger().info(report.description()));
        this.plugin.getLogger().info("Resource-pack Doctor resume: " + result.message());
    }

    public synchronized BeginResult begin(String operation, Consumer<SweepResult> completion) {
        if (!operation.equals("install") && !operation.equals("uninstall")) {
            return new BeginResult(false, "Unknown resource-pack operation.", null);
        }
        if (this.stopping || this.sweepActive.get() || this.doctor.isServerRunActive()) {
            return new BeginResult(false, "A Doctor run is active or the server is stopping.", null);
        }
        if (!Slimefun.getRegistryRuntimeService().getSnapshot().isInitialRegistrationFinalized()) {
            return new BeginResult(false, "Wait until Slimefun item registration has finished.", null);
        }
        Job previousJob = this.job;
        ResourcePackModelExecutor previousExecutor = this.deferredExecutor;
        boolean authorizationSaved = false;
        this.deferredExecutor = null;
        try {
            CuriositiesConfig.getConfig();
            ResourcePackDoctorBackup backup =
                    ResourcePackDoctorBackup.create(this.plugin.getDataFolder().toPath(), operation);
            ResourcePackOwnershipMode current = new ExternalResourcePackService(this.plugin).getOwnershipMode();
            ResourcePackOwnershipMode target = current == ResourcePackOwnershipMode.EXTERNAL
                    ? current
                    : (operation.equals("install") ? ResourcePackOwnershipMode.LEGACY : ResourcePackOwnershipMode.NONE);
            this.job = new Job(operation, target, "prepared", backup);
            this.saveState();
            authorizationSaved = true;
            return this.finishSetup(completion);
        } catch (IOException | LinkageError | RuntimeException failure) {
            if (!authorizationSaved) {
                this.job = previousJob;
                this.deferredExecutor = previousExecutor;
            }
            this.plugin
                    .getLogger()
                    .log(
                            Level.SEVERE,
                            "Resource-pack Doctor setup stopped; inspect its backup before resuming.",
                            failure);
            return new BeginResult(
                    false,
                    "Setup failed; no item cleanup started. Inspect the log and use resource-pack resume.",
                    this.backupPath());
        }
    }

    public synchronized BeginResult resume(Consumer<SweepResult> completion) {
        if (this.job == null) {
            return new BeginResult(false, "No saved install/uninstall operation exists.", null);
        }
        if (this.stopping || this.sweepActive.get() || this.doctor.isServerRunActive()) {
            return new BeginResult(false, "A Doctor run is active or the server is stopping.", this.backupPath());
        }
        if (!Slimefun.getRegistryRuntimeService().getSnapshot().isInitialRegistrationFinalized()) {
            return new BeginResult(false, "Wait until Slimefun item registration has finished.", this.backupPath());
        }
        try {
            return this.finishSetup(completion);
        } catch (IOException | LinkageError | RuntimeException failure) {
            this.deferredExecutor = null;
            this.plugin.getLogger().log(Level.SEVERE, "Resource-pack Doctor resume stopped safely.", failure);
            return new BeginResult(
                    false, "Resume failed; no new item cleanup started. Inspect the server log.", this.backupPath());
        }
    }

    private BeginResult finishSetup(Consumer<SweepResult> completion) throws IOException {
        int mismatches;
        ExternalResourcePackService sender = new ExternalResourcePackService(this.plugin);
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        if (this.job.phase().equals("prepared")) {
            if (!config.setDoctorResourcePack(
                    this.job.owner() == ResourcePackOwnershipMode.EXTERNAL
                            ? this.job.owner()
                            : ResourcePackOwnershipMode.NONE,
                    false,
                    sender.getEffectivePackUrl(),
                    this.configuredSha1())) {
                throw new IOException("Could not persist the disabled Legacy sender");
            }
            sender.refreshDelivery();
            Slimefun.getItemTextureService()
                    .removeHostedPackMappingsChecked(this.job.backup().directory());
            this.job = this.job.withPhase("awaiting-restart");
            this.saveState();
        }
        if ((mismatches = Slimefun.getItemTextureService().getHostedPackTemplateMismatchCount()) != 0) {
            this.deferredExecutor = null;
            return new BeginResult(
                    true,
                    "Mappings were reset. Stop normally and restart; cleanup resumes after restart (" + mismatches
                            + " old item template(s)).",
                    this.backupPath());
        }
        if (!this.job.phase().equals("active")) {
            boolean install = this.job.operation().equals("install");
            if (!config.setDoctorResourcePack(
                    this.job.owner(),
                    install && this.job.owner() == ResourcePackOwnershipMode.LEGACY,
                    install ? ID_PACK_URL : sender.getEffectivePackUrl(),
                    install ? ID_PACK_SHA1 : this.configuredSha1())) {
                throw new IOException("Could not persist the resource-pack sender settings");
            }
            this.job = this.job.withPhase("active");
            this.saveState();
            sender.refreshDelivery();
        }
        this.deferredExecutor = new ResourcePackModelExecutor(true, this.job.backup()::item);
        if (!this.startSweep(true, completion)) {
            return new BeginResult(
                    false,
                    "Setup is saved; cleanup is deferred. Use resource-pack resume when Doctor is idle.",
                    this.backupPath());
        }
        return new BeginResult(
                true,
                "Started " + this.job.operation() + " cleanup. Future join/load cleanup remains authorized.",
                this.backupPath());
    }

    public boolean scan(Consumer<SweepResult> completion) {
        if (!Slimefun.getRegistryRuntimeService().getSnapshot().isInitialRegistrationFinalized()) {
            return false;
        }
        return this.startSweep(false, completion);
    }

    private boolean startSweep(boolean repair, Consumer<SweepResult> completion) {
        if (this.stopping || !this.sweepActive.compareAndSet(false, true)) {
            return false;
        }
        ResourcePackModelExecutor executor =
                repair ? this.deferredExecutor : new ResourcePackModelExecutor(false, (id, item) -> {});
        boolean started = this.doctor.startResourcePackRun(
                executor,
                repair,
                loaded -> this.scanPersisted(executor, (ItemDoctorReport) loaded, repair, completion, 0));
        if (!started) {
            this.sweepActive.set(false);
        }
        return started;
    }

    private void scanPersisted(
            ResourcePackModelExecutor executor,
            ItemDoctorReport loaded,
            boolean repair,
            Consumer<SweepResult> completion,
            int attempt) {
        Slimefun.getSchedulerService().runAsync(() -> {
            ItemDoctorReport persisted = new ItemDoctorReport(repair);
            long scanned = 0L;
            long rewritten = 0L;
            long deferred = 0L;
            boolean busy = false;
            try {
                if (this.stopping) {
                    throw new IOException("Server is stopping");
                }
                PersistedItemStorageMaintenance maintenance = new PersistedItemStorageMaintenance(
                        Slimefun.getDatabaseManager().getBlockDataController());
                PersistedItemStorageMaintenance.SnapshotResult snapshot = maintenance.snapshot();
                boolean bl = busy = !snapshot.available();
                if (!busy) {
                    ArrayList<PersistedItemStorageMaintenance.RewriteRequest> requests =
                            new ArrayList<PersistedItemStorageMaintenance.RewriteRequest>();
                    for (PersistedItemStorageMaintenance.PersistedItemRecord record : snapshot.records()) {
                        ++scanned;
                        if (this.stopping) {
                            throw new IOException("Server is stopping");
                        }
                        ItemStack item = record.deserializeItem();
                        if (item == null) {
                            if (record.storedValue().isBinary()
                                    && record.storedValue().binaryCopy().length == 0) continue;
                            persisted.failure();
                            ++deferred;
                            continue;
                        }
                        if (!executor.inspectItem(item, persisted, record.identity()) || !repair) continue;
                        PersistedItemStorageMaintenance.StoredItemValue replacement =
                                PersistedItemStorageMaintenance.StoredItemValue.current(item);
                        if (!item.equals((Object) replacement.deserialize())) {
                            throw new IOException("Rewritten database item failed round-trip");
                        }
                        this.job.backup().row(record.identity(), record.storedValue());
                        requests.add(new PersistedItemStorageMaintenance.RewriteRequest(record, replacement));
                    }
                    if (repair && !requests.isEmpty()) {
                        PersistedItemStorageMaintenance.RewriteSummary result = maintenance.rewrite(requests);
                        rewritten = result.rewritten();
                        deferred += (long) (result.stale() + result.loaded() + result.missing());
                        busy = result.busy();
                        if (result.failures() != 0 || !result.rollbackComplete()) {
                            persisted.failure();
                        }
                    }
                }
            } catch (IOException | LinkageError | RuntimeException failure) {
                persisted.failure();
                this.plugin
                        .getLogger()
                        .log(
                                Level.WARNING,
                                "Resource-pack Doctor preserved unprocessed storage; use resume to retry.",
                                failure);
            }
            if (busy && attempt < 3 && !this.stopping) {
                Slimefun.getSchedulerService()
                        .runLater(() -> this.scanPersisted(executor, loaded, repair, completion, attempt + 1), 20L);
                return;
            }
            if (busy) {
                persisted.failure();
                ++deferred;
            }
            persisted.markComplete();
            SweepResult result = new SweepResult(loaded, persisted, scanned, rewritten, deferred, busy);
            Slimefun.getSchedulerService().run(() -> {
                this.sweepActive.set(false);
                completion.accept(result);
            });
        });
    }

    public String status() {
        Job current = this.job;
        if (current == null) {
            return "No authorized install/uninstall cleanup." + (this.sweepActive.get() ? " (scan running)" : "");
        }
        return current.operation() + ": " + current.phase() + (this.sweepActive.get() ? " (scan running)" : "")
                + (this.deferredExecutor == null ? " (join/load cleanup paused)" : " (join/load cleanup active)");
    }

    public Path backupPath() {
        return this.job == null ? null : this.job.backup().directory();
    }

    public boolean isSweepActive() {
        return this.sweepActive.get();
    }

    ResourcePackModelExecutor deferredExecutor() {
        return this.stopping ? null : this.deferredExecutor;
    }

    void shutdown() {
        this.stopping = true;
        this.deferredExecutor = null;
    }

    private String configuredSha1() {
        String value = CuriositiesConfig.getConfig().getString("resource-pack.sha1");
        return value == null ? "" : value;
    }

    private Path stateFile() {
        return this.plugin.getDataFolder().toPath().resolve("resource-pack-doctor.yml");
    }

    private void saveState() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", (Object) 1);
        yaml.set("operation", (Object) this.job.operation());
        yaml.set("ownership", (Object) this.job.owner().configValue());
        yaml.set("phase", (Object) this.job.phase());
        yaml.set("backup", (Object) this.job.backup().directory().getFileName().toString());
        ResourcePackDoctorBackup.atomicWrite(this.stateFile(), yaml.saveToString());
    }

    private record Job(
            String operation, ResourcePackOwnershipMode owner, String phase, ResourcePackDoctorBackup backup) {
        Job withPhase(String phase) {
            return new Job(this.operation, this.owner, phase, this.backup);
        }
    }

    public record BeginResult(boolean accepted, String message, Path backup) {}

    public record SweepResult(
            ItemDoctorReport loaded,
            ItemDoctorReport persisted,
            long persistedRows,
            long rewrittenRows,
            long deferredRows,
            boolean storageBusy) {
        public String description() {
            return "Resource-pack Doctor pass: " + this.loaded.getScannedStacks() + " live/backpack stacks scanned, "
                    + this.loaded.getItemModelRepairs() + " model overrides removed; " + this.persistedRows
                    + " unloaded inventory rows scanned, " + this.rewrittenRows + " rows saved, " + this.deferredRows
                    + " rows deferred; " + (this.loaded.getFailures() + this.persisted.getFailures()) + " failures.";
        }
    }
}
