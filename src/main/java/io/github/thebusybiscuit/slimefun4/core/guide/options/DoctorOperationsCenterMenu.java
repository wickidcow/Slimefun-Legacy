package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
import io.github.thebusybiscuit.slimefun4.core.services.ResourcePackOwnershipMode;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.ProxyDiagnosticsService;
import io.github.thebusybiscuit.slimefun4.core.services.compatibility.ProxyDiagnosticsSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Read-mostly operations dashboard layered on top of the existing Slimefun Doctor services.
 *
 * <p>The dashboard does not create a second repair system. Diagnostic buttons route into existing commands and
 * server-wide mutation remains behind the established Doctor confirmation/fingerprint gates.</p>
 */
final class DoctorOperationsCenterMenu {

    private static final int PLAYER_PAGE_SIZE = 45;

    private DoctorOperationsCenterMenu() {}

    private static boolean requireRecoveryAccess(@Nonnull Player player) {
        if (player.hasPermission("slimefun.command.doctor")) {
            return true;
        }

        player.sendMessage(ChatColor.RED
                + "The Slimefun Recovery Center is restricted to server operators/admins.");
        return false;
    }

    static void open(@Nonnull Player player, @Nullable ItemStack guide) {
        if (!requireRecoveryAccess(player)) {
            return;
        }
        ItemStack returnGuide =
                guide == null ? SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE) : guide.clone();

        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        ItemDoctorReport current = Slimefun.getItemDoctorService().getCurrentReport();
        ItemDoctorReport last = Slimefun.getItemDoctorService().getLastReport();
        ProxyDiagnosticsSnapshot proxy = new ProxyDiagnosticsService(Slimefun.instance()).inspect();
        ExternalResourcePackService packs = new ExternalResourcePackService(Slimefun.instance());

        long itemIssues = itemIssueCount(last);
        long dependencyIssues = DoctorGuideAssistant.dependencyProblemCount();
        long addonCompatibilityIssues = DoctorGuideAssistant.addonCompatibilityAttentionCount();
        long addonRuntimeIssues = DoctorGuideAssistant.addonRuntimeFailureCount();
        long addonIssues = dependencyIssues + addonCompatibilityIssues + addonRuntimeIssues;
        long storageIssues = (storage.isReady() ? 0 : 1)
                + storage.getPendingWrites()
                + (storage.wasPreviousShutdownClean() ? 0 : 1)
                + pendingBackpackSaves()
                + uncertainBackpacks();
        long machineIssues = machines.getActiveMachineFailures()
                + machines.getPausedMachineCircuits()
                + (machines.isPaused() ? 1 : 0)
                + (machines.isHalted() ? 1 : 0);
        long packIssues = packIssueCount(packs);
        long proxyIssues = proxy.getFailures().size();

        ChestMenu menu = subMenu("&3&lAdvanced System Health", 54);

        menu.addItem(
                4,
                menuItem(
                        totalIssues(itemIssues, storageIssues, machineIssues, addonIssues, proxyIssues, packIssues) == 0
                                ? Material.EMERALD_BLOCK
                                : Material.COMPASS,
                        totalIssues(itemIssues, storageIssues, machineIssues, addonIssues, proxyIssues, packIssues) == 0
                                ? "&a&lSystem Healthy"
                                : "&e&lSystem Attention",
                        "",
                        "&7Unified read-only health dashboard.",
                        "&7Click a status card to inspect its lane.",
                        "",
                        "&7Current Doctor run: "
                                + (current == null ? "&aIdle" : "&e" + current.getModeName() + " running"),
                        "&7Total attention signals: &e"
                                + totalIssues(itemIssues, storageIssues, machineIssues, addonIssues, proxyIssues, packIssues)));

        addStatusCard(
                menu,
                10,
                itemIssues == 0 && last != null ? Material.EMERALD_BLOCK : Material.CHEST,
                "&fItems",
                itemIssues,
                last == null
                        ? new String[] {"&7No completed server-wide Doctor scan yet.", "&eClick for targeted item tools"}
                        : new String[] {
                            "&7Unknown IDs: &e" + last.getUnknownIds(),
                            "&7Model candidates: &e" + last.getItemModelCandidates(),
                            "&7Migration candidates: &e"
                                    + (last.getLegacyMigrationCandidates() + last.getSchemaMigrationCandidates()),
                            "&eClick for targeted item tools"
                        },
                () -> DoctorGuideMenu.openPlayerItemRepair(player, returnGuide));

        addStatusCard(
                menu,
                11,
                storageIssues == 0 ? Material.EMERALD_BLOCK : Material.BARREL,
                "&fStorage",
                storageIssues,
                new String[] {
                    "&7Ready: " + (storage.isReady() ? "&aYes" : "&cNo"),
                    "&7Pending writes: &e" + storage.getPendingWrites(),
                    "&7Backpack saves: &e" + pendingBackpackSaves(),
                    "&7Previous clean shutdown: " + (storage.wasPreviousShutdownClean() ? "&aYes" : "&eNo"),
                    "&eClick for Storage & Persistence"
                },
                () -> openStorageCenter(player, returnGuide));

        addStatusCard(
                menu,
                12,
                machineIssues == 0 ? Material.EMERALD_BLOCK : Material.FURNACE,
                "&fMachines",
                machineIssues,
                new String[] {
                    "&7Ticker: " + tickerState(machines),
                    "&7Active failures: &e" + machines.getActiveMachineFailures(),
                    "&7Paused circuits: &e" + machines.getPausedMachineCircuits(),
                    "&7Ticking locations: &e" + machines.getTickingLocations(),
                    "&eClick for runtime recovery"
                },
                () -> DoctorGuideMenu.openRuntimeRecovery(player, returnGuide));

        addStatusCard(
                menu,
                13,
                addonIssues == 0 ? Material.EMERALD_BLOCK : Material.BOOKSHELF,
                "&fAddons",
                addonIssues,
                new String[] {
                    "&7Dependency problems: &e" + dependencyIssues,
                    "&7Compatibility attention: &e" + addonCompatibilityIssues,
                    "&7Addon callback records: &e" + addonRuntimeIssues,
                    "&eClick for addon/dependency health"
                },
                () -> DoctorGuideMenu.openAddonDependencyHealth(player, returnGuide));

        addStatusCard(
                menu,
                14,
                proxyIssues == 0 ? Material.ENDER_EYE : Material.REDSTONE_BLOCK,
                "&fProxy",
                proxyIssues,
                new String[] {
                    "&7Mode: &e" + forwardingMode(proxy),
                    "&7Warnings: &e" + proxy.getWarnings().size(),
                    "&7Blocking findings: " + (proxyIssues == 0 ? "&a0" : "&c" + proxyIssues),
                    "&eClick for proxy/player identity health"
                },
                () -> openProxyCenter(player, returnGuide));

        addStatusCard(
                menu,
                15,
                packIssues == 0 ? Material.MAP : Material.REDSTONE_BLOCK,
                "&fResource Pack",
                packIssues,
                new String[] {
                    "&7Ownership: &e" + packs.getOwnershipMode(),
                    "&7Legacy sender: " + (packs.isDeliveryEnabled() ? "&aEnabled" : "&7Disabled"),
                    "&7External managers: &e" + DoctorGuideAssistant.detectedPackManagers(),
                    "&7Config contradiction: " + (packs.hasOwnershipContradiction() ? "&cYes" : "&aNo"),
                    "&eClick for pack ownership"
                },
                () -> openPackOwnership(player, returnGuide));

        boolean restartReady = plannedRestartReady(storage);
        menu.addItem(
                22,
                menuItem(
                        restartReady ? Material.LIME_DYE : Material.ORANGE_DYE,
                        restartReady ? "&aPlanned Restart Ready" : "&ePlanned Restart: Wait",
                        "",
                        "&7Storage ready: " + (storage.isReady() ? "&aYes" : "&cNo"),
                        "&7Pending writes: &e" + storage.getPendingWrites(),
                        "&7Backpack save chains: &e" + pendingBackpackSaves(),
                        "&7Doctor traversal: " + (current == null ? "&aIdle" : "&eRunning"),
                        "",
                        restartReady
                                ? "&aDoctor sees no outstanding persistence work."
                                : "&eWait for the listed work before a planned restart.",
                        "&8This is readiness guidance, not a guarantee against",
                        "&8unrelated plugin/server shutdown failures."));

        menu.addItem(
                28,
                menuItem(
                        Material.CLOCK,
                        "&6Performance Health",
                        "",
                        "&7Ticker status, live profiler sample, targeted",
                        "&7machine inspection, pauses and failure detail.",
                        "&eClick to open"));
        menu.addMenuClickHandler(28, (clickedPlayer, slot, item, action) -> {
            openPerformanceCenter(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                30,
                menuItem(
                        Material.NETHER_STAR,
                        "&bUpgrade Center",
                        "",
                        "&7Unified read-only discovery for legacy IDs,",
                        "&7schemas, placed machines and persisted storage.",
                        "&eClick to open"));
        menu.addMenuClickHandler(30, (clickedPlayer, slot, item, action) -> {
            openUpgradeCenter(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                32,
                menuItem(
                        Material.BARREL,
                        "&eStorage & Persistence Center",
                        "",
                        "&7Database health, backup readiness, backpack",
                        "&7persistence and native migration lanes.",
                        "&eClick to open"));
        menu.addMenuClickHandler(32, (clickedPlayer, slot, item, action) -> {
            openStorageCenter(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                34,
                menuItem(
                        Material.ENDER_EYE,
                        "&dProxy & Player Identity",
                        "",
                        "&7Velocity/Bungee forwarding checks and",
                        "&7Slimefun profile/UUID identity verification.",
                        "&eClick to open"));
        menu.addMenuClickHandler(34, (clickedPlayer, slot, item, action) -> {
            openProxyCenter(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                36,
                menuItem(
                        Material.MAP,
                        "&3Resource-Pack Ownership",
                        "",
                        "&7Declare whether Legacy, another system, or no",
                        "&7system owns Slimefun texture delivery.",
                        "&eClick to open"));
        menu.addMenuClickHandler(36, (clickedPlayer, slot, item, action) -> {
            openPackOwnership(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                38,
                menuItem(
                        Material.PAPER,
                        "&fSupport Summary",
                        "",
                        "&7Open the compact Support & Diagnostics summary.",
                        "&eClick to open"));
        menu.addMenuClickHandler(38, (clickedPlayer, slot, item, action) -> {
            DoctorGuideMenu.openSupportSummary(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(45, menuItem(Material.ARROW, "&fBack to Recovery Center"));
        menu.addMenuClickHandler(45, (clickedPlayer, slot, item, action) -> {
            DoctorGuideMenu.open(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                49,
                menuItem(
                        Material.COMPASS,
                        "&bRefresh Advanced System Health",
                        "",
                        "&7Refresh all live status cards.",
                        "&eClick to refresh"));
        menu.addMenuClickHandler(49, (clickedPlayer, slot, item, action) -> {
            open(clickedPlayer, returnGuide);
            return false;
        });

        menu.open(player);
    }

    static void openPerformanceCenter(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        if (!requireRecoveryAccess(player)) {
            return;
        }

        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        var ticker = Slimefun.getTickerTask();
        ChestMenu menu = subMenu("&6&lPerformance Health", 45);

        menu.addItem(
                4,
                menuItem(
                        machines.getActiveMachineFailures() == 0
                                        && machines.getPausedMachineCircuits() == 0
                                        && !machines.isPaused()
                                ? Material.EMERALD_BLOCK
                                : Material.REDSTONE_TORCH,
                        "&fTicker Runtime",
                        "",
                        "&7State: " + tickerState(machines),
                        "&7Rate: &e" + machines.getTickRate() + " tick(s)",
                        "&7Chunks: &e" + machines.getTickingChunks(),
                        "&7Locations: &e" + machines.getTickingLocations(),
                        "&7Target-paused locations: &e" + ticker.getTargetedPausedMachineCount(),
                        "&7Target-paused item types: &e" + ticker.getTargetedPausedItemIds().size(),
                        "&7Circuit pauses: &e" + machines.getPausedMachineCircuits(),
                        "&7Active failures: &e" + machines.getActiveMachineFailures()));

        addCommandButton(menu, 10, Material.COMPARATOR, "&bTicker Status", "slimefun tick query",
                "&7Print the full ticker state.");
        addCommandButton(menu, 12, Material.CLOCK, "&6Live Profiler Sample", "slimefun tick top",
                "&7Collect the next Slimefun ticker profiler sample.",
                "&8Requires the ticker to be running.");
        addCommandButton(menu, 14, Material.LEVER, "&eList Paused Tickers", "slimefun tick frozen",
                "&7Show global and targeted ticker pauses.");
        addCommandButton(menu, 16, Material.SPYGLASS, "&bInspect Looked-at Machine", "slimefun tick at",
                "&7Inspect ticker state for the machine you are looking at.");
        addCommandButton(menu, 20, Material.REDSTONE_TORCH, "&cMachine Failure Detail", "slimefun doctor runtime",
                "&7Show isolated/failing machine locations and causes.");

        menu.addItem(
                22,
                menuItem(
                        machines.isPaused() ? Material.LIME_DYE : Material.RED_DYE,
                        machines.isPaused() ? "&aResume Global Slimefun Ticker" : "&cFreeze Global Slimefun Ticker",
                        "",
                        machines.isPaused()
                                ? "&7Resume new Slimefun machine ticker cycles."
                                : "&7Pause new Slimefun machine ticker cycles.",
                        "&8Already-dispatched work may finish.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(22, (clickedPlayer, slot, item, action) -> {
            openTickerConfirmation(clickedPlayer, returnGuide, !machines.isPaused());
            return false;
        });

        addCommandButton(menu, 24, Material.HOPPER, "&dIntegration Runtime", "slimefun doctor integrations",
                "&7Inspect external adapter failures that may look like machine lag.");

        addBack(menu, 36, "&fBack to Advanced System Health", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openTickerConfirmation(
            @Nonnull Player player, @Nonnull ItemStack returnGuide, boolean freeze) {
        ChestMenu menu = subMenu(freeze ? "&c&lConfirm Ticker Freeze" : "&a&lConfirm Ticker Resume", 27);
        menu.addItem(
                11,
                menuItem(
                        freeze ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                        freeze ? "&cConfirm Global Freeze" : "&aConfirm Global Resume",
                        "",
                        freeze
                                ? "&7Pauses new Slimefun machine ticker cycles."
                                : "&7Resumes normal Slimefun machine ticker cycles.",
                        "&8Machine registrations and stored data are preserved."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            runCommand(clickedPlayer, freeze ? "slimefun tick freeze" : "slimefun tick unfreeze");
            return false;
        });
        menu.addItem(15, menuItem(Material.BARRIER, "&cCancel"));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            openPerformanceCenter(clickedPlayer, returnGuide);
            return false;
        });
        menu.open(player);
    }

    private static void openUpgradeCenter(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ItemDoctorReport report = Slimefun.getItemDoctorService().getLastReport();
        ChestMenu menu = subMenu("&b&lUpgrade Center", 54);

        menu.addItem(
                4,
                menuItem(
                        Material.NETHER_STAR,
                        "&fUpgrade Discovery",
                        "",
                        report == null
                                ? "&7No completed migration-aware Doctor scan is available."
                                : "&7Last scan complete: " + (report.isComplete() ? "&aYes" : "&eNo"),
                        report == null
                                ? "&7Run Upgrade Scan first."
                                : "&7Legacy-ID candidates: &e" + report.getLegacyMigrationCandidates(),
                        report == null
                                ? ""
                                : "&7Schema candidates: &e" + report.getSchemaMigrationCandidates(),
                        report == null
                                ? ""
                                : "&7Placed legacy block IDs: &e" + report.getLegacyBlockIds(),
                        "",
                        "&8This center never executes a migration directly."));

        addCommandButton(menu, 10, Material.COMPARATOR, "&bUpgrade Status", "slimefun doctor upgrade status",
                "&7Show provider counts and current discovery state.");
        addCommandButton(menu, 12, Material.SPYGLASS, "&bUpgrade Scan", "slimefun doctor upgrade scan",
                "&7Start the existing migration-aware read-only Doctor traversal.");
        addCommandButton(menu, 14, Material.WRITABLE_BOOK, "&eUpgrade Plan", "slimefun doctor upgrade plan",
                "&7Build the read-only multi-lane upgrade plan, including persisted storage.");
        addCommandButton(menu, 16, Material.BOOKSHELF, "&dUpgrade Providers", "slimefun doctor upgrade providers",
                "&7List legacy-ID, schema and exact-machine providers.");

        addCommandButton(menu, 20, Material.PAPER, "&eLegacy-ID Plan", "slimefun doctor migrations plan",
                "&7Correlate exact unresolved legacy item IDs.");
        addCommandButton(menu, 22, Material.LECTERN, "&dSame-ID Schema Scan", "slimefun doctor migrations schemas scan",
                "&7Probe addon-owned same-ID schema candidates read-only.",
                "&8A clean scan may create a short-lived execution fingerprint.");
        addCommandButton(menu, 24, Material.FURNACE, "&6Exact Machine Providers", "slimefun doctor migrations blocks status",
                "&7List exact placed-machine migration providers.");
        addCommandButton(menu, 28, Material.BARREL, "&ePersisted Block-ID Status",
                "slimefun doctor migrations schemas blocks status",
                "&7Inspect persisted block/universal identity migration state.");
        addCommandButton(menu, 30, Material.CHEST, "&eStored Payload Status",
                "slimefun doctor migrations schemas storage status",
                "&7Inspect persisted item-payload migration state.");
        addCommandButton(menu, 32, Material.HOPPER, "&eStored Machine Item IDs",
                "slimefun doctor migrations schemas storage ids status",
                "&7Inspect persisted Slimefun Item-ID migration state.");
        addCommandButton(menu, 34, Material.ENDER_CHEST, "&eBackpack Item IDs",
                "slimefun doctor migrations schemas storage backpacks status",
                "&7Inspect persisted backpack Slimefun Item-ID migration state.");

        menu.addItem(
                40,
                menuItem(
                        Material.BARRIER,
                        "&cExecution stays command-gated",
                        "",
                        "&7This GUI intentionally does not execute migration",
                        "&7fingerprints. Review each scan/plan in chat, then",
                        "&7use the exact execution command it prints.",
                        "&8Fingerprints remain short-lived and lane-specific."));

        addBack(menu, 45, "&fBack to Advanced System Health", () -> open(player, returnGuide));
        menu.open(player);
    }

    static void openStorageCenter(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        if (!requireRecoveryAccess(player)) {
            return;
        }

        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        var backup = Slimefun.getBackupService();
        boolean backupEnabled = Slimefun.getCfg().getBoolean("options.backup-data");
        ItemDoctorReport current = Slimefun.getItemDoctorService().getCurrentReport();
        boolean restartReady = plannedRestartReady(storage);
        ChestMenu menu = subMenu("&e&lStorage & Persistence", 54);

        menu.addItem(
                4,
                menuItem(
                        restartReady ? Material.EMERALD_BLOCK : Material.BARREL,
                        "&fPersistence Runtime",
                        "",
                        "&7Ready: " + (storage.isReady() ? "&aYes" : "&cNo"),
                        "&7Block storage: &e" + storage.getBlockStorageType(),
                        "&7Profile storage: &e" + storage.getProfileStorageType(),
                        "&7Pending writes: &e" + storage.getPendingWrites(),
                        "&7Backpack save chains: &e" + pendingBackpackSaves(),
                        "&7Uncertain backpack baselines: &e" + uncertainBackpacks(),
                        "&7Previous clean shutdown: " + (storage.wasPreviousShutdownClean() ? "&aYes" : "&eNo"),
                        "&7Doctor traversal: " + (current == null ? "&aIdle" : "&eRunning")));

        menu.addItem(
                10,
                menuItem(
                        restartReady ? Material.LIME_DYE : Material.ORANGE_DYE,
                        restartReady ? "&aPlanned Restart Ready" : "&ePlanned Restart: Wait",
                        "",
                        "&7Pending writes: &e" + storage.getPendingWrites(),
                        "&7Backpack saves: &e" + pendingBackpackSaves(),
                        "&7Doctor traversal: " + (current == null ? "&aIdle" : "&eRunning"),
                        "",
                        "&8Wait for outstanding persistence work before",
                        "&8a planned restart or migration window."));

        menu.addItem(
                12,
                menuItem(
                        backup.isApplicable() && backupEnabled ? Material.CHEST : Material.GRAY_DYE,
                        "&fShutdown Backup Readiness",
                        "",
                        "&7Applicable: " + (backup.isApplicable() ? "&aYes" : "&7No"),
                        "&7Configured: " + (backupEnabled ? "&aEnabled" : "&eDisabled"),
                        "&7Backup files: &e" + backup.getBackupCount() + "&7/&e" + backup.getMaximumBackups(),
                        "&7Newest backup: &e" + latestBackupAge(backup.getLatestBackupModifiedMillis()),
                        "",
                        "&8Doctor does not expose a live force-backup button.",
                        "&8Existing SQLite backups run after normal database shutdown."));

        addCommandButton(menu, 14, Material.COMPARATOR, "&bStorage Doctor Status", "slimefun doctor storage status",
                "&7Print storage, backpack and backup readiness.");
        addCommandButton(menu, 16, Material.NETHER_STAR, "&bUpgrade Storage Plan", "slimefun doctor upgrade plan",
                "&7Read-only persisted-storage audit across all migration lanes.");

        addCommandButton(menu, 20, Material.BARREL, "&ePersisted Block IDs",
                "slimefun doctor migrations schemas blocks status",
                "&7Inspect persisted block/universal identity state.");
        addCommandButton(menu, 22, Material.CHEST, "&eStored Item Payloads",
                "slimefun doctor migrations schemas storage status",
                "&7Inspect legacy/current persisted item payload formats.");
        addCommandButton(menu, 24, Material.HOPPER, "&eStored Machine Item IDs",
                "slimefun doctor migrations schemas storage ids status",
                "&7Inspect persisted Slimefun Item IDs in machine storage.");
        addCommandButton(menu, 28, Material.ENDER_CHEST, "&eBackpack Item IDs",
                "slimefun doctor migrations schemas storage backpacks status",
                "&7Inspect persisted Slimefun Item IDs in backpacks.");

        addCommandButton(menu, 30, Material.SPYGLASS, "&bScan Persisted Block IDs",
                "slimefun doctor migrations schemas blocks scan",
                "&7Read-only audit that may prepare a guarded fingerprint.");
        addCommandButton(menu, 32, Material.SPYGLASS, "&bScan Stored Item Payloads",
                "slimefun doctor migrations schemas storage scan",
                "&7Read-only audit that may prepare a guarded fingerprint.");
        addCommandButton(menu, 34, Material.SPYGLASS, "&bScan Stored Machine Item IDs",
                "slimefun doctor migrations schemas storage ids scan",
                "&7Read-only audit that may prepare a guarded fingerprint.");
        addCommandButton(menu, 38, Material.SPYGLASS, "&bScan Backpack Item IDs",
                "slimefun doctor migrations schemas storage backpacks scan",
                "&7Read-only audit that may prepare a guarded fingerprint.");

        addBack(menu, 45, "&fBack to Advanced System Health", () -> open(player, returnGuide));
        menu.open(player);
    }

    static void openProxyCenter(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        if (!requireRecoveryAccess(player)) {
            return;
        }

        ProxyDiagnosticsSnapshot snapshot = new ProxyDiagnosticsService(Slimefun.instance()).inspect();
        ChestMenu menu = subMenu("&d&lProxy & Player Identity", 45);

        menu.addItem(
                4,
                menuItem(
                        snapshot.isHealthy() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                        "&fForwarding Health",
                        "",
                        "&7Mode: &e" + forwardingMode(snapshot),
                        "&7Backend online-mode: &e" + snapshot.isBackendOnlineMode(),
                        "&7Velocity modern: " + (snapshot.isVelocityEnabled() ? "&aEnabled" : "&7Disabled"),
                        "&7Velocity secret: "
                                + (snapshot.isVelocitySecretConfigured() ? "&aConfigured" : "&eNot found"),
                        "&7Bungee-compatible: " + (snapshot.isBungeeEnabled() ? "&eEnabled" : "&7Disabled"),
                        "&7Warnings: &e" + snapshot.getWarnings().size(),
                        "&7Blocking findings: "
                                + (snapshot.getFailures().isEmpty() ? "&a0" : "&c" + snapshot.getFailures().size())));

        addCommandButton(menu, 10, Material.COMPARATOR, "&bFull Proxy Diagnostics", "slimefun doctor proxy",
                "&7Print forwarding configuration and blocking findings.");
        menu.addItem(
                12,
                menuItem(
                        Material.PLAYER_HEAD,
                        "&eCheck Online Player Identity...",
                        "",
                        "&7Choose an online player to compare Bukkit UUID",
                        "&7with the Slimefun profile owner UUID and data.",
                        "&eClick to choose player"));
        menu.addMenuClickHandler(12, (clickedPlayer, slot, item, action) -> {
            openProxyPlayerPicker(clickedPlayer, returnGuide, 0);
            return false;
        });
        addCommandButton(menu, 14, Material.PAPER, "&fSupport Report", "slimefun doctor report",
                "&7Print the broader support report alongside proxy evidence.");

        menu.addItem(
                22,
                menuItem(
                        Material.BOOK,
                        "&fWhy this matters",
                        "",
                        "&7Slimefun profiles, researches and backpacks are",
                        "&7UUID-based. Incorrect proxy forwarding can look",
                        "&7like lost/reset player data even when storage is healthy.",
                        "",
                        "&8This diagnostic cannot prove firewall rules or",
                        "&8identify every Bungee-compatible proxy brand."));

        addBack(menu, 36, "&fBack to Advanced System Health", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openProxyPlayerPicker(
            @Nonnull Player viewer, @Nonnull ItemStack returnGuide, int requestedPage) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (players.size() + PLAYER_PAGE_SIZE - 1) / PLAYER_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        ChestMenu menu = subMenu("&dProxy Identity " + (page + 1) + "/" + pages, 54);
        int start = page * PLAYER_PAGE_SIZE;
        int end = Math.min(players.size(), start + PLAYER_PAGE_SIZE);
        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            int slot = i - start;
            menu.addItem(slot, playerHead(target, "&7Click to inspect Slimefun UUID/profile identity."));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                if (!target.isOnline()) {
                    clickedPlayer.sendMessage(ChatColor.RED + "That player is no longer online.");
                    openProxyPlayerPicker(clickedPlayer, returnGuide, page);
                    return false;
                }
                runCommand(clickedPlayer, "slimefun doctor proxy player " + target.getName());
                return false;
            });
        }

        menu.addItem(45, menuItem(Material.ARROW, "&fBack to Proxy Health"));
        menu.addMenuClickHandler(45, (clickedPlayer, slot, item, action) -> {
            openProxyCenter(clickedPlayer, returnGuide);
            return false;
        });
        if (page > 0) {
            menu.addItem(48, menuItem(Material.ARROW, "&ePrevious Page"));
            menu.addMenuClickHandler(48, (clickedPlayer, slot, item, action) -> {
                openProxyPlayerPicker(clickedPlayer, returnGuide, page - 1);
                return false;
            });
        }
        menu.addItem(50, menuItem(Material.PAPER, "&fPage " + (page + 1) + " / " + pages, "", "&7Online: &e" + players.size()));
        if (page + 1 < pages) {
            menu.addItem(52, menuItem(Material.ARROW, "&eNext Page"));
            menu.addMenuClickHandler(52, (clickedPlayer, slot, item, action) -> {
                openProxyPlayerPicker(clickedPlayer, returnGuide, page + 1);
                return false;
            });
        }

        menu.open(viewer);
    }

    static void openPackOwnership(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        if (!requireRecoveryAccess(player)) {
            return;
        }

        ExternalResourcePackService packs = new ExternalResourcePackService(Slimefun.instance());
        ResourcePackOwnershipMode mode = packs.getOwnershipMode();
        var textures = Slimefun.getItemTextureService();
        ChestMenu menu = subMenu("&3&lResource-Pack Ownership", 45);

        menu.addItem(
                4,
                menuItem(
                        packs.hasOwnershipContradiction() ? Material.REDSTONE_BLOCK : Material.MAP,
                        "&fCurrent Ownership",
                        "",
                        "&7Mode: &e" + mode,
                        "&7Raw Legacy sender flag: " + (packs.isSenderFlagEnabled() ? "&aEnabled" : "&7Disabled"),
                        "&7Effective Legacy sender: " + (packs.isDeliveryEnabled() ? "&aEnabled" : "&7Disabled"),
                        "&7Contradiction: " + (packs.hasOwnershipContradiction() ? "&cYes" : "&aNo"),
                        "&7Bundled mappings active: &e" + textures.getHostedPackEnabledMappingCount(),
                        "&7Bundled mappings at 0: &e" + textures.getHostedPackEnableCandidateCount(),
                        "&7Custom mappings preserved: &e" + textures.getHostedPackCustomMappingCount()));

        addOwnershipButton(menu, 10, Material.COMPARATOR, ResourcePackOwnershipMode.AUTO, returnGuide,
                "&fAUTO — Backwards Compatible",
                "&7Infer delivery intent from the Legacy sender flag.",
                "&7Existing servers default here.");
        addOwnershipButton(menu, 12, Material.LIME_DYE, ResourcePackOwnershipMode.LEGACY, returnGuide,
                "&aLEGACY — Slimefun Sends It",
                "&7Legacy sends the configured official/custom ZIP.",
                "&7Selecting this enables Legacy delivery.");
        addOwnershipButton(menu, 14, Material.CHEST, ResourcePackOwnershipMode.EXTERNAL, returnGuide,
                "&bEXTERNAL — Combined Pack",
                "&7ItemsAdder/Oraxen/proxy/server pack owns delivery.",
                "&7Selecting this disables Legacy delivery.",
                "&aSlimefun model mappings may remain enabled.");
        addOwnershipButton(menu, 16, Material.GRAY_DYE, ResourcePackOwnershipMode.NONE, returnGuide,
                "&7NONE — No Slimefun Textures",
                "&7No Slimefun-textured resource pack is intended.",
                "&7Selecting this disables Legacy delivery.",
                "&eThis still does NOT remove item-model mappings.");

        menu.addItem(
                22,
                menuItem(
                        Material.BOOK,
                        "&fOwnership vs Item Models",
                        "",
                        "&7Ownership controls who delivers textures.",
                        "&7Item-model mappings control item appearance/identity data.",
                        "",
                        "&aEXTERNAL + mappings ON is a normal combined-pack setup.",
                        "&eNONE can justify reviewing model cleanup, but cleanup",
                        "&ealways remains a separate confirmed Doctor action."));

        menu.addItem(
                24,
                menuItem(
                        Material.SPYGLASS,
                        "&bOpen Resource Pack Preflight",
                        "",
                        "&7Inspect URL/SHA-1, external managers and mappings.",
                        "&eClick to open"));
        menu.addMenuClickHandler(24, (clickedPlayer, slot, item, action) -> {
            DoctorGuideMenu.openResourcePackPreflight(clickedPlayer, returnGuide);
            return false;
        });

        addBack(menu, 36, "&fBack to Advanced System Health", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void addOwnershipButton(
            @Nonnull ChestMenu menu,
            int slot,
            @Nonnull Material material,
            @Nonnull ResourcePackOwnershipMode mode,
            @Nonnull ItemStack returnGuide,
            @Nonnull String name,
            @Nonnull String... lore) {
        String[] fullLore = Arrays.copyOf(lore, lore.length + 2);
        fullLore[lore.length] = "";
        fullLore[lore.length + 1] = "&eClick for confirmation";
        menu.addItem(slot, menuItem(material, name, fullLore));
        menu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> {
            openOwnershipConfirmation(player, returnGuide, mode);
            return false;
        });
    }

    private static void openOwnershipConfirmation(
            @Nonnull Player player, @Nonnull ItemStack returnGuide, @Nonnull ResourcePackOwnershipMode mode) {
        ChestMenu menu = subMenu("&3&lConfirm Pack Ownership", 27);
        menu.addItem(
                11,
                menuItem(
                        Material.LIME_CONCRETE,
                        "&aConfirm " + mode + " Ownership",
                        "",
                        ownershipEffect(mode),
                        "",
                        "&8No item-model mapping or stored ItemStack is changed."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            ExternalResourcePackService service = new ExternalResourcePackService(Slimefun.instance());
            if (!service.setOwnershipMode(mode)) {
                clickedPlayer.sendMessage(ChatColor.RED + "Could not save resource-pack ownership mode.");
                return false;
            }
            clickedPlayer.sendMessage(ChatColor.GREEN + "Resource-pack ownership mode is now " + mode + ".");
            openPackOwnership(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(15, menuItem(Material.BARRIER, "&cCancel"));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            openPackOwnership(clickedPlayer, returnGuide);
            return false;
        });
        menu.open(player);
    }

    private static String ownershipEffect(ResourcePackOwnershipMode mode) {
        return switch (mode) {
            case AUTO -> "&7Keeps the current sender flag and preserves historical behavior.";
            case LEGACY -> "&7Enables Slimefun Legacy's configured pack sender.";
            case EXTERNAL -> "&7Disables Legacy delivery; another system owns the combined pack.";
            case NONE -> "&7Disables Legacy delivery; no Slimefun textures are declared in use.";
        };
    }

    private static void addStatusCard(
            @Nonnull ChestMenu menu,
            int slot,
            @Nonnull Material material,
            @Nonnull String name,
            long issues,
            @Nonnull String[] lore,
            @Nonnull Runnable action) {
        String[] fullLore = Arrays.copyOf(lore, lore.length + 2);
        fullLore[lore.length] = "";
        fullLore[lore.length + 1] = issues == 0 ? "&aStatus: healthy/clear" : "&eAttention signals: " + issues;
        menu.addItem(slot, menuItem(material, name, fullLore));
        menu.addMenuClickHandler(slot, (player, clickedSlot, item, clickAction) -> {
            action.run();
            return false;
        });
    }

    private static long itemIssueCount(@Nullable ItemDoctorReport report) {
        if (report == null) {
            return 1L;
        }
        return report.getUnknownIds()
                + report.getUnresolvedTemplates()
                + report.getItemModelCandidates()
                + report.getLegacyMigrationCandidates()
                + report.getSchemaMigrationCandidates()
                + report.getUnknownBlockIds()
                + report.getFailures();
    }

    private static long packIssueCount(@Nonnull ExternalResourcePackService packs) {
        long issues = packs.hasOwnershipContradiction() ? 1L : 0L;
        if (packs.getOwnershipMode() == ResourcePackOwnershipMode.LEGACY || packs.isDeliveryEnabled()) {
            if (!packs.isConfiguredUrlValid()) {
                issues++;
            }
            if (!packs.isConfiguredSha1Valid()) {
                issues++;
            }
        }
        return issues;
    }

    private static long totalIssues(long... values) {
        long total = 0L;
        for (long value : values) {
            total += Math.max(0L, value);
        }
        return total;
    }

    private static boolean plannedRestartReady(@Nonnull StorageRuntimeSnapshot storage) {
        return storage.isReady()
                && storage.getPendingWrites() == 0
                && pendingBackpackSaves() == 0
                && Slimefun.getItemDoctorService().getCurrentReport() == null;
    }

    private static int pendingBackpackSaves() {
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        return profiles == null ? 0 : profiles.getPendingBackpackSaveChainCount();
    }

    private static int uncertainBackpacks() {
        var profiles = Slimefun.getDatabaseManager().getProfileDataController();
        return profiles == null ? 0 : profiles.getUncertainBackpackBaselineCount();
    }

    private static String latestBackupAge(long modifiedMillis) {
        if (modifiedMillis <= 0L) {
            return "none found yet";
        }
        long minutes = Math.max(0L, (System.currentTimeMillis() - modifiedMillis) / 60_000L);
        if (minutes < 120L) {
            return minutes + " minute(s) ago";
        }
        long hours = minutes / 60L;
        if (hours < 72L) {
            return hours + " hour(s) ago";
        }
        return (hours / 24L) + " day(s) ago";
    }

    private static String tickerState(@Nonnull MachineRuntimeSnapshot snapshot) {
        if (snapshot.isHalted()) {
            return "&cHALTED";
        }
        if (snapshot.isPaused()) {
            return "&ePAUSED";
        }
        return "&aRUNNING";
    }

    private static String forwardingMode(@Nonnull ProxyDiagnosticsSnapshot snapshot) {
        return switch (snapshot.getForwardingMode()) {
            case VELOCITY_MODERN -> "Velocity modern";
            case BUNGEE_COMPATIBLE -> "Bungee-compatible";
            case CONFLICTING -> "Conflicting";
            case STANDALONE_OR_UNKNOWN -> "Standalone / unknown";
            case UNSAFE_OFFLINE -> "Unsafe offline backend";
        };
    }

    private static ItemStack playerHead(@Nonnull Player player, @Nonnull String... lore) {
        ItemStack item = menuItem(Material.PLAYER_HEAD, "&e" + player.getName(), lore);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void addCommandButton(
            @Nonnull ChestMenu menu,
            int slot,
            @Nonnull Material material,
            @Nonnull String name,
            @Nonnull String command,
            @Nonnull String... lore) {
        String[] fullLore = Arrays.copyOf(lore, lore.length + 2);
        fullLore[lore.length] = "";
        fullLore[lore.length + 1] = "&eClick to run /" + command;
        menu.addItem(slot, menuItem(material, name, fullLore));
        menu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> {
            runCommand(player, command);
            return false;
        });
    }

    private static void addBack(
            @Nonnull ChestMenu menu, int slot, @Nonnull String name, @Nonnull Runnable action) {
        menu.addItem(slot, menuItem(Material.ARROW, name));
        menu.addMenuClickHandler(slot, (player, clickedSlot, item, clickAction) -> {
            action.run();
            return false;
        });
    }

    private static void runCommand(@Nonnull Player player, @Nonnull String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private static ChestMenu subMenu(@Nonnull String title, int size) {
        ChestMenu menu = new ChestMenu(title);
        menu.setSize(size);
        menu.setEmptySlotsClickable(false);
        fill(menu, size);
        return menu;
    }

    private static void fill(@Nonnull ChestMenu menu, int size) {
        for (int slot = 0; slot < size; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
    }

    private static ItemStack menuItem(@Nonnull Material material, @Nonnull String name, @Nonnull String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.stream(lore)
                .filter(line -> line != null)
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList());
        item.setItemMeta(meta);
        return item;
    }
}
