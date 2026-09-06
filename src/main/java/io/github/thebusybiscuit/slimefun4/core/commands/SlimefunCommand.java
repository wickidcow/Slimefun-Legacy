package io.github.thebusybiscuit.slimefun4.core.commands;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.StorageIntegrityRepairPlan;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.StorageIntegrityRepairVerification;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.StorageIntegrityScanner;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegrityConfirmationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegritySnapshot;
import io.github.thebusybiscuit.slimefun4.core.commands.subcommands.SlimefunSubCommands;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * This {@link CommandExecutor} holds the functionality of our {@code /slimefun} command.
 *
 * @author TheBusyBiscuit
 *
 */
public class SlimefunCommand implements CommandExecutor, Listener {

    private static final int STORAGE_PLAN_PAGE_SIZE = 20;

    private boolean registered = false;
    private final Slimefun plugin;
    private final List<SubCommand> commands = new LinkedList<>();
    private final Map<SubCommand, Integer> commandUsage = new HashMap<>();

    /**
     * Creates a new instance of {@link SlimefunCommand}
     *
     * @param plugin
     *            The instance of our {@link Slimefun}
     */
    public SlimefunCommand(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Validate.isTrue(!registered, "Slimefun's subcommands have already been registered!");

        registered = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getCommand("slimefun").setExecutor(this);
        plugin.getCommand("slimefun").setTabCompleter(new SlimefunTabCompleter(this));
        commands.addAll(SlimefunSubCommands.getAllCommands(this));
    }

    public @Nonnull Slimefun getPlugin() {
        return plugin;
    }

    /**
     * Returns a heatmap of how often certain commands are used.
     *
     * @return A {@link Map} holding the amount of times each command was run
     */
    public @Nonnull Map<SubCommand, Integer> getCommandUsage() {
        return commandUsage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0) {
            for (SubCommand command : commands) {
                if (args[0].equalsIgnoreCase(command.getName())) {
                    command.recordUsage(commandUsage);
                    if (command.getName().equalsIgnoreCase("doctor")
                            && args.length > 1
                            && args[1].equalsIgnoreCase("storage")) {
                        runStorageDoctor(sender, args);
                    } else if (command.getName().equalsIgnoreCase("doctor")
                            && args.length > 1
                            && args[1].equalsIgnoreCase("ie2")) {
                        runInfinityExpansionDoctor(sender, args);
                    } else {
                        command.onExecute(sender, args);
                    }
                    return true;
                }
            }
        }

        sendHelp(sender);

        /*
         * We could just return true here, but if there's no subcommands,
         * then something went horribly wrong anyway.
         * This will also stop sonarcloud from nagging about
         * this always returning true...
         */
        return !commands.isEmpty();
    }

    private void runStorageDoctor(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("status")) {
            sendStorageIntegrityStatus(sender);
            return;
        }
        if (action.equals("plan")) {
            sendStorageRepairPlan(sender, args);
            return;
        }
        if (action.equals("verify")) {
            startStorageRepairVerification(sender, args);
            return;
        }
        if (!action.equals("scan")) {
            sender.sendMessage(ChatColors.color("&eUsage: /sf doctor storage <status|scan|plan|verify> [page|fingerprint]"));
            return;
        }

        var databaseManager = Slimefun.getDatabaseManager();
        if (databaseManager == null || databaseManager.getBlockDataController() == null) {
            sender.sendMessage(ChatColors.color("&cSlimefun block storage is not ready yet."));
            return;
        }

        var scan = StorageIntegrityScanner.startScan(databaseManager.getBlockDataController());
        if (scan == null) {
            sender.sendMessage(ChatColors.color("&eA storage integrity scan or verification is already running."));
            return;
        }

        sender.sendMessage(ChatColors.color("&6Slimefun Storage Integrity"));
        sender.sendMessage(ChatColors.color("&aStarted a read-only backend ownership scan."));
        sender.sendMessage(ChatColors.color(
                "&7No world blocks are scanned or force-loaded, and no storage rows will be changed."));

        scan.whenComplete((snapshot, failure) -> Slimefun.runSync(() -> {
            if (failure != null) {
                String message = failure.getMessage();
                sender.sendMessage(ChatColors.color("&cStorage integrity scan failed: &f"
                        + failure.getClass().getSimpleName()
                        + (message == null || message.isBlank() ? "" : " &8- &7" + message)));
                sender.sendMessage(ChatColors.color("&eAny previous two-pass confirmation was invalidated."));
                return;
            }
            sender.sendMessage(ChatColors.color("&aStorage integrity scan completed."));
            sendStorageIntegritySnapshot(sender, snapshot);
        }));
    }

    private void sendStorageIntegrityStatus(@Nonnull CommandSender sender) {
        sender.sendMessage(ChatColors.color("&6Slimefun Storage Integrity"));
        if (StorageIntegrityScanner.isScanRunning()) {
            sender.sendMessage(ChatColors.color("&eA read-only storage scan or verification is currently running."));
        }

        StorageIntegritySnapshot snapshot = StorageIntegrityScanner.getLastSnapshot();
        if (snapshot == null) {
            sender.sendMessage(ChatColors.color("&7No completed storage integrity scan is available yet."));
            sender.sendMessage(ChatColors.color("&7Run &e/sf doctor storage scan &7to inspect the active backend."));
            return;
        }
        sendStorageIntegritySnapshot(sender, snapshot);
        sendStorageRepairVerificationStatus(sender);
    }

    private void sendStorageIntegritySnapshot(
            @Nonnull CommandSender sender, @Nonnull StorageIntegritySnapshot snapshot) {
        sender.sendMessage(ChatColors.color("&7Duration: &e" + snapshot.getDurationMillis() + "ms"));
        sender.sendMessage(ChatColors.color("&7Normal block roots: &e" + snapshot.getBlockRecords()
                + " &8| &7data owners: &e" + snapshot.getBlockDataOwners()
                + " &8| &7inventory owners: &e" + snapshot.getBlockInventoryOwners()));
        sender.sendMessage(ChatColors.color("&7Normal orphan owners: data &e" + snapshot.getOrphanBlockDataOwners()
                + " &8| &7inventory &e" + snapshot.getOrphanBlockInventoryOwners()));
        sender.sendMessage(ChatColors.color("&7Universal roots: &e" + snapshot.getUniversalRecords()
                + " &8| &7data owners: &e" + snapshot.getUniversalDataOwners()
                + " &8| &7inventory owners: &e" + snapshot.getUniversalInventoryOwners()));
        sender.sendMessage(ChatColors.color("&7Universal orphan owners: data &e"
                + snapshot.getOrphanUniversalDataOwners() + " &8| &7inventory &e"
                + snapshot.getOrphanUniversalInventoryOwners()));

        if (snapshot.isClean()) {
            sender.sendMessage(ChatColors.color("&aNo orphan secondary storage owners were found."));
        } else {
            sender.sendMessage(ChatColors.color("&eFound &6" + snapshot.getTotalOrphanOwners()
                    + " &eorphan owner reference(s). These are candidates only; nothing was deleted."));
            sendStorageOwnerSamples(
                    sender,
                    "Block data",
                    snapshot.getOrphanBlockDataOwnerSamples(),
                    snapshot.getOrphanBlockDataOwners());
            sendStorageOwnerSamples(
                    sender,
                    "Block inventory",
                    snapshot.getOrphanBlockInventoryOwnerSamples(),
                    snapshot.getOrphanBlockInventoryOwners());
            sendStorageOwnerSamples(
                    sender,
                    "Universal data",
                    snapshot.getOrphanUniversalDataOwnerSamples(),
                    snapshot.getOrphanUniversalDataOwners());
            sendStorageOwnerSamples(
                    sender,
                    "Universal inventory",
                    snapshot.getOrphanUniversalInventoryOwnerSamples(),
                    snapshot.getOrphanUniversalInventoryOwners());
        }

        sender.sendMessage(ChatColors.color("&7Queued writes at scan boundaries: &e"
                + snapshot.getPendingWritesAtStart() + " &8-> &e" + snapshot.getPendingWritesAtEnd()));
        sender.sendMessage(ChatColors.color("&7Deferred delayed writes: &e"
                + formatStorageWriteCount(snapshot.getPendingDelayedWritesAtStart())
                + " &8-> &e"
                + formatStorageWriteCount(snapshot.getPendingDelayedWritesAtEnd())
                + " &8| &7delayed saving: "
                + (snapshot.isDelayedSavingEnabled() ? "&eEnabled" : "&aDisabled")));

        sendStorageConfirmationStatus(sender, snapshot, StorageIntegrityScanner.getConfirmationSnapshot());
        sender.sendMessage(ChatColors.color(
                "&8This diagnostic is observational only. It does not delete, migrate, repair, or force-load data."));
    }

    private void sendStorageConfirmationStatus(
            @Nonnull CommandSender sender,
            @Nonnull StorageIntegritySnapshot snapshot,
            @Nonnull StorageIntegrityConfirmationSnapshot confirmation) {
        if (!snapshot.wasStorageQuietAtBoundaries()) {
            sender.sendMessage(ChatColors.color("&cTwo-pass confirmation: 0/2. &eStorage was not proven quiet."));
            if (snapshot.getPendingDelayedWritesAtStart() < 0 || snapshot.getPendingDelayedWritesAtEnd() < 0) {
                sender.sendMessage(ChatColors.color(
                        "&7Delayed-saving mutations are not currently enumerable, so confirmation fails closed."));
            }
            sender.sendMessage(ChatColors.color(
                    "&7Repeat the scan only after both active and deferred storage writes can be proven quiet."));
            return;
        }

        if (confirmation.isConfirmed()) {
            if (snapshot.isClean()) {
                sender.sendMessage(ChatColors.color(
                        "&aTwo-pass confirmation: 2/2. Two matching quiet scans confirm a clean ownership snapshot."));
            } else {
                sender.sendMessage(ChatColors.color("&aTwo-pass confirmation: 2/2. &f"
                        + confirmation.getCandidateOwners() + " &astable orphan owner reference(s) matched exactly."));
            }
            sender.sendMessage(ChatColors.color(
                    "&7Use &e/sf doctor storage plan &7to render the confirmed read-only candidate plan."));
            return;
        }

        if (confirmation.didCandidateSetChange()) {
            sender.sendMessage(ChatColors.color(
                    "&eTwo-pass confirmation: 1/2. The exact candidate set changed, so confirmation restarted."));
        } else {
            sender.sendMessage(ChatColors.color(
                    "&eTwo-pass confirmation: 1/2. First quiet ownership snapshot recorded."));
        }

        long waitMillis = confirmation.getRemainingWaitMillis(System.currentTimeMillis());
        if (waitMillis > 0L) {
            long waitSeconds = (waitMillis + 999L) / 1000L;
            sender.sendMessage(ChatColors.color("&7Wait at least &e" + waitSeconds
                    + "s &7before another matching quiet scan can become pass 2/2."));
        } else {
            sender.sendMessage(ChatColors.color(
                    "&7A second exact matching quiet scan can now advance confirmation to 2/2."));
        }
    }

    private void sendStorageRepairPlan(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (StorageIntegrityScanner.isScanRunning()) {
            sender.sendMessage(ChatColors.color(
                    "&eA storage integrity scan or verification is running. Wait for it to finish before rendering a plan."));
            return;
        }

        int requestedPage = 1;
        if (args.length > 3) {
            try {
                requestedPage = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(ChatColors.color("&eUsage: /sf doctor storage plan [page]"));
                return;
            }
        }

        StorageIntegrityRepairPlan plan = StorageIntegrityScanner.getConfirmedRepairPlan();
        if (plan == null) {
            StorageIntegrityConfirmationSnapshot confirmation = StorageIntegrityScanner.getConfirmationSnapshot();
            sender.sendMessage(ChatColors.color("&6Slimefun Storage Repair Plan"));
            sender.sendMessage(ChatColors.color("&eNo confirmed plan is available."));
            sender.sendMessage(ChatColors.color("&7Current confirmation: &e" + confirmation.getQuietPasses()
                    + "/2&7. Two exact matching quiet scans are required before a plan can be rendered."));
            sender.sendMessage(ChatColors.color(
                    "&7Run &e/sf doctor storage scan &7and inspect &e/sf doctor storage status&7."));
            return;
        }

        List<String> entries = new ArrayList<>(plan.getTotalCandidateReferences());
        appendStoragePlanEntries(entries, "BLOCK_DATA", plan.getBlockDataOwners());
        appendStoragePlanEntries(entries, "BLOCK_INVENTORY", plan.getBlockInventoryOwners());
        appendStoragePlanEntries(entries, "UNIVERSAL_DATA", plan.getUniversalDataOwners());
        appendStoragePlanEntries(entries, "UNIVERSAL_INVENTORY", plan.getUniversalInventoryOwners());

        int pageCount = Math.max(1, (entries.size() + STORAGE_PLAN_PAGE_SIZE - 1) / STORAGE_PLAN_PAGE_SIZE);
        if (requestedPage < 1 || requestedPage > pageCount) {
            sender.sendMessage(ChatColors.color("&cPlan page must be between 1 and " + pageCount + "."));
            return;
        }

        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - plan.getSourceScanCompletedAtMillis()) / 1000L);
        sender.sendMessage(ChatColors.color("&6Slimefun Storage Repair Plan &8[&aREAD ONLY&8]"));
        sender.sendMessage(ChatColors.color("&7Fingerprint: &e" + plan.getFingerprint()));
        sender.sendMessage(ChatColors.color("&7Source scan age: &e" + ageSeconds + "s"));
        sender.sendMessage(ChatColors.color("&7Candidates: block data &e" + plan.getBlockDataOwnerCount()
                + " &8| &7block inventory &e" + plan.getBlockInventoryOwnerCount()
                + " &8| &7universal data &e" + plan.getUniversalDataOwnerCount()
                + " &8| &7universal inventory &e" + plan.getUniversalInventoryOwnerCount()));

        if (plan.isEmpty()) {
            sender.sendMessage(ChatColors.color("&aThe confirmed plan is empty. There are no orphan owners to remove."));
        } else {
            sender.sendMessage(ChatColors.color("&7Plan entries: &e" + plan.getTotalCandidateReferences()
                    + " &8| &7page &e" + requestedPage + "&7/&e" + pageCount));
            int start = (requestedPage - 1) * STORAGE_PLAN_PAGE_SIZE;
            int end = Math.min(entries.size(), start + STORAGE_PLAN_PAGE_SIZE);
            for (int i = start; i < end; i++) {
                sender.sendMessage(ChatColors.color(entries.get(i)));
            }
            if (pageCount > 1) {
                sender.sendMessage(ChatColors.color("&7Use &e/sf doctor storage plan <page> &7to inspect every entry."));
            }
            sender.sendMessage(ChatColors.color(
                    "&7Final preflight: &e/sf doctor storage verify " + plan.getFingerprint()));
        }

        sender.sendMessage(ChatColors.color(
                "&8Each entry is a scope-qualified orphan owner whose secondary rows would be repair candidates."));
        sender.sendMessage(ChatColors.color(
                "&8Nothing was deleted, migrated, rewritten, force-loaded, or repaired. Verification is also read-only."));
    }

    private void startStorageRepairVerification(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColors.color("&eUsage: /sf doctor storage verify <full-fingerprint>"));
            sender.sendMessage(ChatColors.color("&7Render the current fingerprint with &e/sf doctor storage plan&7."));
            return;
        }

        String fingerprint = args[3].trim().toLowerCase(Locale.ROOT);
        if (!isSha256Fingerprint(fingerprint)) {
            sender.sendMessage(ChatColors.color("&cThe verification fingerprint must be exactly 64 hexadecimal characters."));
            return;
        }

        var databaseManager = Slimefun.getDatabaseManager();
        if (databaseManager == null || databaseManager.getBlockDataController() == null) {
            sender.sendMessage(ChatColors.color("&cSlimefun block storage is not ready yet."));
            return;
        }

        var verification = StorageIntegrityScanner.startRepairVerification(
                databaseManager.getBlockDataController(), fingerprint);
        if (verification == null) {
            sender.sendMessage(ChatColors.color("&eA storage integrity scan or verification is already running."));
            return;
        }

        if (!verification.isDone()) {
            sender.sendMessage(ChatColors.color("&6Slimefun Storage Repair Preflight"));
            sender.sendMessage(ChatColors.color("&aStarted a fresh read-only fingerprint revalidation scan."));
            sender.sendMessage(ChatColors.color(
                    "&7The exact candidate set and both write queues must remain quiet and match the confirmed plan."));
        }

        verification.whenComplete((result, failure) -> Slimefun.runSync(() -> {
            if (failure != null) {
                String message = failure.getMessage();
                sender.sendMessage(ChatColors.color("&cStorage repair preflight failed: &f"
                        + failure.getClass().getSimpleName()
                        + (message == null || message.isBlank() ? "" : " &8- &7" + message)));
                sender.sendMessage(ChatColors.color("&eThe two-pass confirmation was invalidated."));
                return;
            }
            sendStorageRepairVerificationResult(sender, result);
        }));
    }

    private void sendStorageRepairVerificationResult(
            @Nonnull CommandSender sender, @Nonnull StorageIntegrityRepairVerification verification) {
        sender.sendMessage(ChatColors.color("&6Slimefun Storage Repair Preflight"));
        switch (verification.getStatus()) {
            case VERIFIED -> {
                long remainingSeconds =
                        (verification.getRemainingValidityMillis(System.currentTimeMillis()) + 999L) / 1000L;
                sender.sendMessage(ChatColors.color("&aVERIFIED: the full fingerprint still matches the exact candidate set."));
                sender.sendMessage(ChatColors.color("&7Fingerprint: &e" + verification.getExpectedFingerprint()));
                sender.sendMessage(ChatColors.color("&7Storage was quiet at both fresh scan boundaries."));
                sender.sendMessage(ChatColors.color("&7Preflight validity: up to &e" + remainingSeconds
                        + "s&7. Any new integrity scan invalidates it."));
                sender.sendMessage(ChatColors.color(
                        "&8This still performs no cleanup. A destructive repair must re-check read/write barriers immediately before mutation."));
            }
            case FINGERPRINT_REJECTED -> {
                sender.sendMessage(ChatColors.color("&cREJECTED: that fingerprint does not match the current confirmed plan."));
                sender.sendMessage(ChatColors.color("&7Render the plan again with &e/sf doctor storage plan&7."));
            }
            case EMPTY_PLAN -> sender.sendMessage(ChatColors.color(
                    "&aNo repair preflight is needed because the confirmed candidate plan is empty."));
            case STORAGE_NOT_QUIET -> {
                sender.sendMessage(ChatColors.color("&cFAILED: storage was not quiet during the revalidation scan."));
                StorageIntegritySnapshot scan = verification.getVerificationScan();
                if (scan != null) {
                    sender.sendMessage(ChatColors.color("&7Queued writes: &e" + scan.getPendingWritesAtStart()
                            + " &8-> &e" + scan.getPendingWritesAtEnd()
                            + " &8| &7deferred: &e" + formatStorageWriteCount(scan.getPendingDelayedWritesAtStart())
                            + " &8-> &e" + formatStorageWriteCount(scan.getPendingDelayedWritesAtEnd())));
                }
                sender.sendMessage(ChatColors.color("&7The two-pass confirmation was reset; scan again after storage is quiet."));
            }
            case CANDIDATE_SET_CHANGED -> {
                sender.sendMessage(ChatColors.color("&cFAILED: the exact orphan candidate set changed during revalidation."));
                if (verification.getObservedFingerprint() != null) {
                    sender.sendMessage(ChatColors.color("&7Observed fingerprint: &e" + verification.getObservedFingerprint()));
                }
                sender.sendMessage(ChatColors.color(
                        "&7Confirmation restarted at 1/2. Review the new scan before considering repair."));
            }
            case CONFIRMATION_INVALIDATED -> {
                sender.sendMessage(ChatColors.color("&cFAILED: there is no longer a valid 2/2 confirmation for this plan."));
                sender.sendMessage(ChatColors.color(
                        "&7Run two exact matching quiet scans again, then render a new plan and fingerprint."));
            }
        }
    }

    private void sendStorageRepairVerificationStatus(@Nonnull CommandSender sender) {
        StorageIntegrityRepairVerification verification = StorageIntegrityScanner.getRepairVerificationSnapshot();
        if (verification == null) {
            sender.sendMessage(ChatColors.color("&7Repair preflight: &fNot run for the current scan state."));
            return;
        }

        if (verification.isCurrent(System.currentTimeMillis())) {
            long remainingSeconds =
                    (verification.getRemainingValidityMillis(System.currentTimeMillis()) + 999L) / 1000L;
            sender.sendMessage(ChatColors.color("&7Repair preflight: &aVerified &8| &7expires in &e"
                    + remainingSeconds + "s &8| &7fingerprint &e"
                    + verification.getExpectedFingerprint().substring(0, 12)));
        } else {
            sender.sendMessage(ChatColors.color("&7Repair preflight: &e" + verification.getStatus()));
        }
    }

    private boolean isSha256Fingerprint(String fingerprint) {
        if (fingerprint.length() != 64) {
            return false;
        }
        for (int i = 0; i < fingerprint.length(); i++) {
            if (Character.digit(fingerprint.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private void appendStoragePlanEntries(List<String> entries, String scope, List<String> owners) {
        for (String owner : owners) {
            entries.add("&8- &7[&e" + scope + "&7] &f" + owner);
        }
    }

    private String formatStorageWriteCount(int count) {
        return count < 0 ? "unknown" : Integer.toString(count);
    }

    private void sendStorageOwnerSamples(
            @Nonnull CommandSender sender, @Nonnull String label, @Nonnull List<String> samples, int total) {
        if (samples.isEmpty()) {
            return;
        }
        sender.sendMessage(ChatColors.color("&8- &7" + label + " samples (&e" + samples.size() + "&7/&e" + total
                + "&7): &f" + String.join("&8, &f", samples)));
    }

    private void runInfinityExpansionDoctor(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.doctor")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        if (Bukkit.getPluginCommand("ie2") == null) {
            sender.sendMessage(ChatColors.color("&cInfinityExpansion2 is not installed or its /ie2 command is unavailable."));
            return;
        }

        String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
        if (!List.of("status", "scan", "migrate", "refresh").contains(action)) {
            sender.sendMessage(ChatColors.color("&eUsage: /sf doctor ie2 <status|scan|migrate|refresh>"));
            return;
        }

        if (!Bukkit.dispatchCommand(sender, "ie2 doctor " + action)) {
            sender.sendMessage(ChatColors.color("&cInfinityExpansion2 Doctor did not accept the migration command."));
        }
    }

    public void sendHelp(@Nonnull CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColors.color("&aSlimefun &2v" + Slimefun.getVersion()));
        sender.sendMessage("");

        for (SubCommand cmd : commands) {
            if (!cmd.isHidden()) {
                sender.sendMessage(ChatColors.color("&3/sf " + cmd.getName() + " &b") + cmd.getDescription(sender));
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getMessage().equalsIgnoreCase("/help slimefun")) {
            sendHelp(e.getPlayer());
            e.setCancelled(true);
        }
    }

    /**
     * This returns A {@link List} containing every possible {@link SubCommand} of this {@link Command}.
     *
     * @return A {@link List} containing every possible {@link SubCommand}
     */
    public @Nonnull List<String> getSubCommandNames() {
        // @formatter:off
        return commands.stream().map(SubCommand::getName).collect(Collectors.toList());
        // @formatter:on
    }
}
