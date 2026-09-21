package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.api.runtime.MachineRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
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

/** Administrative Slimefun Doctor controls exposed from the Guide settings menu. */
final class DoctorGuideMenu {

    private static final int PLAYER_PAGE_SIZE = 45;

    private DoctorGuideMenu() {}

    static void open(@Nonnull Player player, @Nullable ItemStack guide) {
        ItemStack returnGuide =
                guide == null ? SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE) : guide.clone();
        ExternalResourcePackService packService = new ExternalResourcePackService(Slimefun.instance());
        var textures = Slimefun.getItemTextureService();
        DoctorGuideAssistant.Recommendation recommendation = DoctorGuideAssistant.recommend();

        ChestMenu menu = new ChestMenu("&4&lSlimefun Doctor Console");
        menu.setSize(45);
        menu.setEmptySlotsClickable(false);
        fill(menu, 45);

        menu.addItem(
                4,
                menuItem(
                        recommendation.issueCount() > 0 ? Material.COMPASS : Material.RECOVERY_COMPASS,
                        recommendation.issueCount() > 0
                                ? "&e&lRecommended Fix: " + recommendation.title()
                                : "&a&lDoctor Assistant: " + recommendation.title(),
                        "",
                        "&7" + recommendation.detail(),
                        "",
                        recommendation.issueCount() > 0
                                ? "&7Affected/attention count: &e" + recommendation.issueCount()
                                : "&aNo immediate repair lane is required.",
                        "",
                        "&eClick to open the recommended safe next step"));
        menu.addMenuClickHandler(4, (clickedPlayer, slot, item, action) -> {
            routeRecommendation(clickedPlayer, returnGuide, recommendation);
            return false;
        });

        addEnablePackButton(menu, packService, returnGuide);
        addUpgradeItemsButton(menu, returnGuide);
        addDisablePackButton(menu, packService, returnGuide);
        addRemoveModelsButton(menu, returnGuide);

        menu.addItem(
                19,
                menuItem(
                        Material.MAP,
                        "&bResource Pack Preflight",
                        "",
                        "&7Check sender state, URL/SHA-1 validity,",
                        "&7external/combined pack managers and model mappings.",
                        "",
                        "&8Legacy sender OFF is valid when another system",
                        "&8delivers a combined pack with matching models.",
                        "&eClick to inspect"));
        menu.addMenuClickHandler(19, (clickedPlayer, slot, item, action) -> {
            openResourcePackPreflight(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                21,
                menuItem(
                        Material.PLAYER_HEAD,
                        "&6Player & Item Repair",
                        "",
                        "&7Inspect or repair one item/player instead of",
                        "&7starting a server-wide Doctor repair.",
                        "",
                        "&7Includes hand inspection, hand repair,",
                        "&7inventory inspection and online-player repair.",
                        "&eClick to open"));
        menu.addMenuClickHandler(21, (clickedPlayer, slot, item, action) -> {
            openPlayerItemRepair(clickedPlayer, returnGuide);
            return false;
        });

        long dependencyProblems = DoctorGuideAssistant.dependencyProblemCount();
        long addonFailures = DoctorGuideAssistant.addonRuntimeFailureCount();
        menu.addItem(
                23,
                menuItem(
                        dependencyProblems > 0 || addonFailures > 0 ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                        "&dAddon & Dependency Health",
                        "",
                        "&7Required dependency problems: "
                                + (dependencyProblems == 0 ? "&a0" : "&c" + dependencyProblems),
                        "&7Active addon callback records: "
                                + (addonFailures == 0 ? "&a0" : "&e" + addonFailures),
                        "",
                        "&7Compatibility, dependency, addon Doctor,",
                        "&7registry and cross-fork API diagnostics.",
                        "&eClick to inspect"));
        menu.addMenuClickHandler(23, (clickedPlayer, slot, item, action) -> {
            openAddonDependencyHealth(clickedPlayer, returnGuide);
            return false;
        });

        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        menu.addItem(
                25,
                menuItem(
                        machines.getActiveMachineFailures() > 0 || machines.getPausedMachineCircuits() > 0
                                ? Material.REDSTONE_TORCH
                                : Material.LEVER,
                        "&cRuntime Recovery",
                        "",
                        "&7Machine failures: &e" + machines.getActiveMachineFailures(),
                        "&7Paused circuits: &e" + machines.getPausedMachineCircuits(),
                        "&7Pending writes: &e" + storage.getPendingWrites(),
                        "&7External failures: &e"
                                + Slimefun.getExternalIntegrationService().getActiveFailureCount(),
                        "",
                        "&eClick for diagnostics and guarded retries"));
        menu.addMenuClickHandler(25, (clickedPlayer, slot, item, action) -> {
            openRuntimeRecovery(clickedPlayer, returnGuide);
            return false;
        });

        addOtherDoctorFixesButton(menu, returnGuide);

        menu.addItem(
                30,
                menuItem(
                        Material.PAPER,
                        "&fDoctor Support Summary",
                        "",
                        "&7One compact view for issue reports:",
                        "&7version, storage, machines, pack/model state,",
                        "&7dependencies, integrations and last Doctor scan.",
                        "",
                        "&eClick to open"));
        menu.addMenuClickHandler(30, (clickedPlayer, slot, item, action) -> {
            openSupportSummary(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                32,
                menuItem(
                        Material.BOOK,
                        "&fDoctor Safety Rules",
                        "",
                        "&aGreen/read-only actions &7inspect only.",
                        "&eYellow actions &7route to a specialist lane.",
                        "&cConfirmed actions &7can change stored items/settings.",
                        "",
                        "&8Legacy pack delivery and model mappings are independent.",
                        "&8Custom/non-matching model values stay protected."));
        menu.addItem(
                34,
                menuItem(
                        Material.CLOCK,
                        "&bRefresh Doctor Console",
                        "",
                        "&7Refresh all live counts and recommendations.",
                        "&eClick to refresh"));
        menu.addMenuClickHandler(34, (clickedPlayer, slot, item, action) -> {
            open(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                36,
                menuItem(
                        Material.ARROW,
                        "&fBack to Guide Settings",
                        "",
                        "&7Return to Slimefun Guide settings."));
        menu.addMenuClickHandler(36, (clickedPlayer, slot, item, action) -> {
            SlimefunGuideSettings.openSettings(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                40,
                menuItem(
                        Material.REDSTONE,
                        "&fLive Doctor Overview",
                        "",
                        "&7Legacy sender: " + (packService.isDeliveryEnabled() ? "&aEnabled" : "&7Disabled"),
                        "&7Bundled mappings active: &e" + textures.getHostedPackEnabledMappingCount(),
                        "&7Mappings available: &e" + textures.getHostedPackEnableCandidateCount(),
                        "&7Custom mappings preserved: &e" + textures.getHostedPackCustomMappingCount(),
                        "&7Previous clean shutdown: " + (storage.wasPreviousShutdownClean() ? "&aYes" : "&eNo"),
                        "&7Pending writes: &e" + storage.getPendingWrites()));

        menu.open(player);
    }

    private static void routeRecommendation(
            @Nonnull Player player,
            @Nonnull ItemStack returnGuide,
            @Nonnull DoctorGuideAssistant.Recommendation recommendation) {
        switch (recommendation.action()) {
            case RUN_SCAN -> runCommand(player, "slimefun doctor scan");
            case SHOW_STATUS -> runCommand(player, "slimefun doctor status");
            case UPGRADE_PACK_ITEMS -> openUpgradeConfirmation(player, returnGuide);
            case REPAIR_PRESENTATION -> openGeneralRepairConfirmation(player, returnGuide);
            case SCHEMA_MIGRATION -> runCommand(player, "slimefun doctor migrations schemas scan");
            case LEGACY_MIGRATION -> runCommand(player, "slimefun doctor migrations plan");
            case ITEM_MODEL_SCAN -> runCommand(player, "slimefun doctor item-models scan");
            case RUNTIME_HEALTH -> openRuntimeRecovery(player, returnGuide);
            case DEPENDENCY_HEALTH -> openAddonDependencyHealth(player, returnGuide);
            case INTEGRATION_HEALTH -> runCommand(player, "slimefun doctor integrations");
            case SUPPORT_SUMMARY, NONE -> openSupportSummary(player, returnGuide);
        }
    }

    private static void addEnablePackButton(
            @Nonnull ChestMenu menu,
            @Nonnull ExternalResourcePackService service,
            @Nonnull ItemStack returnGuide) {
        boolean enabled = service.isDeliveryEnabled();
        menu.addItem(
                10,
                menuItem(
                        enabled ? Material.LIME_DYE : Material.GREEN_DYE,
                        enabled ? "&aResource Pack Sender Enabled" : "&aEnable Legacy Pack Sender",
                        "",
                        "&7Turns only Slimefun Legacy's pack sender ON.",
                        "&7Eligible online players receive the configured pack.",
                        "",
                        "&8This does not add or change item-model mappings.",
                        enabled ? "&8Already enabled." : "&eClick to enable"));
        menu.addMenuClickHandler(10, (player, slot, item, action) -> {
            if (!service.setDeliveryEnabled(true)) {
                player.sendMessage(ChatColor.RED + "Could not save resource-pack.enabled in configSFLAddons.yml.");
                return false;
            }
            player.sendMessage(ChatColor.GREEN + "Slimefun Legacy resource-pack delivery is enabled.");
            open(player, returnGuide);
            return false;
        });
    }

    private static void addUpgradeItemsButton(@Nonnull ChestMenu menu, @Nonnull ItemStack returnGuide) {
        int candidates = Slimefun.getItemTextureService().getHostedPackEnableCandidateCount();
        menu.addItem(
                12,
                menuItem(
                        Material.SMITHING_TABLE,
                        "&6Upgrade Items for Resource Pack",
                        "",
                        "&7Adds Slimefun Legacy's exact bundled model IDs",
                        "&7only where item-models.yml is currently 0.",
                        "&7Then updates reachable stored Slimefun items.",
                        "",
                        "&7Mappings available: &e" + candidates,
                        "&cServer-wide item change. Full backup first.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(12, (player, slot, item, action) -> {
            openUpgradeConfirmation(player, returnGuide);
            return false;
        });
    }

    private static void addDisablePackButton(
            @Nonnull ChestMenu menu,
            @Nonnull ExternalResourcePackService service,
            @Nonnull ItemStack returnGuide) {
        boolean enabled = service.isDeliveryEnabled();
        menu.addItem(
                14,
                menuItem(
                        Material.RED_DYE,
                        enabled ? "&cDisable Legacy Pack Sender" : "&7Legacy Pack Sender Disabled",
                        "",
                        "&7Turns only Slimefun Legacy's pack sender OFF.",
                        "&7Removes only Legacy's pack UUID from online players.",
                        "",
                        "&aSafe with an external/combined pack sender.",
                        "&8This does NOT remove Slimefun item-model mappings.",
                        enabled ? "&eClick to disable" : "&8Already disabled."));
        menu.addMenuClickHandler(14, (player, slot, item, action) -> {
            if (!service.setDeliveryEnabled(false)) {
                player.sendMessage(ChatColor.RED + "Could not save resource-pack.enabled in configSFLAddons.yml.");
                return false;
            }
            player.sendMessage(ChatColor.YELLOW + "Slimefun Legacy resource-pack delivery is disabled.");
            player.sendMessage(
                    ChatColor.GRAY
                            + "Item-model mappings were left unchanged for custom/combined resource-pack compatibility.");
            open(player, returnGuide);
            return false;
        });
    }

    private static void addRemoveModelsButton(@Nonnull ChestMenu menu, @Nonnull ItemStack returnGuide) {
        int mappingCandidates = Slimefun.getItemTextureService().getHostedPackRemovalCandidateCount();
        menu.addItem(
                16,
                menuItem(
                        Material.GRINDSTONE,
                        "&dRemove Resource-Pack Item Models",
                        "",
                        "&7Use only when you intentionally want Legacy's",
                        "&7exact bundled model mappings removed.",
                        "",
                        "&7Exact bundled mappings to remove: &e" + mappingCandidates,
                        "&aDo NOT use just because Legacy's sender is OFF.",
                        "&8External/combined packs may still need these mappings.",
                        "&8Custom/non-matching model values are preserved.",
                        "&cServer-wide recovery. Full backup first.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(16, (player, slot, item, action) -> {
            openRemoveModelsConfirmation(player, returnGuide);
            return false;
        });
    }

    private static void openResourcePackPreflight(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ExternalResourcePackService service = new ExternalResourcePackService(Slimefun.instance());
        var textures = Slimefun.getItemTextureService();
        String managers = DoctorGuideAssistant.detectedPackManagers();
        boolean externalManager = !"None detected".equals(managers);

        ChestMenu menu = subMenu("&b&lResource Pack Preflight", 36);

        menu.addItem(
                10,
                menuItem(
                        service.isDeliveryEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "&fLegacy Pack Sender",
                        "",
                        "&7State: " + (service.isDeliveryEnabled() ? "&aEnabled" : "&7Disabled"),
                        "&7Required: " + (service.isRequired() ? "&eYes" : "&aNo"),
                        "",
                        service.isDeliveryEnabled()
                                ? "&7Legacy is currently sending its configured pack."
                                : "&7Legacy is not currently sending a pack.",
                        "&8This state alone does not decide item-model cleanup."));

        menu.addItem(
                12,
                menuItem(
                        externalManager ? Material.CHEST : Material.ENDER_CHEST,
                        "&fExternal / Combined Pack Setup",
                        "",
                        "&7Known pack managers detected: &e" + managers,
                        "",
                        "&aSupported setup:",
                        "&7Keep Legacy sender OFF when another plugin/proxy",
                        "&7delivers your combined pack, while keeping Slimefun",
                        "&7model mappings ON if that pack contains the models.",
                        "",
                        "&8Detection is informational; proxies/server config",
                        "&8may deliver packs without a detected plugin."));

        menu.addItem(
                14,
                menuItem(
                        service.isConfiguredUrlValid() && service.isConfiguredSha1Valid()
                                ? Material.EMERALD
                                : Material.REDSTONE,
                        "&fConfigured Legacy Pack",
                        "",
                        "&7URL: " + (service.isConfiguredUrlValid() ? "&aValid" : "&cInvalid"),
                        "&7SHA-1: "
                                + (service.isConfiguredSha1Valid()
                                        ? (service.hasConfiguredSha1() ? "&aValid" : "&7Not set (optional)")
                                        : "&cInvalid"),
                        "&7Effective URL:",
                        "&8" + abbreviate(service.getEffectivePackUrl(), 46),
                        "",
                        "&7Legacy can also send your own hosted custom pack",
                        "&7if you point resource-pack.url at that combined ZIP."));

        menu.addItem(
                16,
                menuItem(
                        Material.COMPARATOR,
                        "&fSlimefun Item-Model Mapping State",
                        "",
                        "&7Bundled active: &e" + textures.getHostedPackEnabledMappingCount(),
                        "&7Bundled available (0): &e" + textures.getHostedPackEnableCandidateCount(),
                        "&7Custom/non-matching preserved: &e" + textures.getHostedPackCustomMappingCount(),
                        "&7Exact bundled removal candidates: &e" + textures.getHostedPackRemovalCandidateCount(),
                        "",
                        "&8Doctor cannot inspect the contents of another plugin's",
                        "&8pack. Keep mappings when your combined pack uses them."));

        menu.addItem(
                22,
                menuItem(
                        Material.FIREWORK_ROCKET,
                        "&bTest Configured Legacy Pack on Me",
                        "",
                        "&7Sends the configured Legacy pack only to you.",
                        "&7Works even if global Legacy delivery is disabled.",
                        "",
                        "&8Does not enable the sender globally.",
                        "&8Does not change your saved opt-in preference.",
                        "&8Does not alter item-model mappings.",
                        "&eClick to test"));
        menu.addMenuClickHandler(22, (clickedPlayer, slot, item, action) -> {
            boolean sent = service.testForPlayer(clickedPlayer);
            clickedPlayer.sendMessage(sent
                    ? ChatColor.GREEN + "Sent the configured Slimefun Legacy pack to you for testing."
                    : ChatColor.RED + "The configured Slimefun Legacy pack could not be sent. Check URL/SHA-1 and console.");
            return false;
        });

        menu.addItem(
                24,
                menuItem(
                        Material.WRITABLE_BOOK,
                        "&eWhich setup should I use?",
                        "",
                        "&aLegacy sends official/custom pack:",
                        "&7Sender ON. Keep matching model mappings ON.",
                        "",
                        "&aItemsAdder/Oraxen/proxy sends combined pack:",
                        "&7Legacy sender OFF. Keep Slimefun mappings ON",
                        "&7when your combined pack contains those models.",
                        "",
                        "&eNo pack contains Slimefun models:",
                        "&7Only then consider removing bundled mappings,",
                        "&7after a backup and Doctor scan."));

        addBack(menu, 27, "&fBack to Doctor Console", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openPlayerItemRepair(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ChestMenu menu = subMenu("&6&lPlayer & Item Repair", 36);

        menu.addItem(
                10,
                menuItem(
                        Material.SPYGLASS,
                        "&bInspect Item in Hand",
                        "",
                        "&7Read-only inspection of the item you are holding.",
                        "&7No metadata or stored data is changed.",
                        "&eClick to inspect"));
        menu.addMenuClickHandler(10, (clickedPlayer, slot, item, action) -> {
            ItemDoctorReport report =
                    Slimefun.getItemDoctorService().inspectItem(clickedPlayer.getInventory().getItemInMainHand(), false);
            sendInspection(clickedPlayer, "Held item", report);
            return false;
        });

        menu.addItem(
                12,
                menuItem(
                        Material.ANVIL,
                        "&6Repair Item in Hand",
                        "",
                        "&7Runs the existing safe presentation repair",
                        "&7against your held Slimefun item only.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(12, (clickedPlayer, slot, item, action) -> {
            openTargetedRepairConfirmation(clickedPlayer, returnGuide, null, true);
            return false;
        });

        menu.addItem(
                14,
                menuItem(
                        Material.CHEST,
                        "&bInspect My Inventory",
                        "",
                        "&7Read-only inspection of your inventory",
                        "&7and ender chest.",
                        "&eClick to inspect"));
        menu.addMenuClickHandler(14, (clickedPlayer, slot, item, action) -> {
            ItemDoctorReport report = Slimefun.getItemDoctorService().inspectPlayer(clickedPlayer, false);
            sendInspection(clickedPlayer, "Your inventory + ender chest", report);
            return false;
        });

        menu.addItem(
                16,
                menuItem(
                        Material.ENDER_CHEST,
                        "&6Repair My Inventory",
                        "",
                        "&7Safely repairs eligible presentation data in",
                        "&7your inventory and ender chest only.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(16, (clickedPlayer, slot, item, action) -> {
            openTargetedRepairConfirmation(clickedPlayer, returnGuide, clickedPlayer, false);
            return false;
        });

        menu.addItem(
                22,
                menuItem(
                        Material.PLAYER_HEAD,
                        "&eRepair Online Player...",
                        "",
                        "&7Choose an online player and confirm a targeted",
                        "&7inventory + ender-chest presentation repair.",
                        "&eClick to choose player"));
        menu.addMenuClickHandler(22, (clickedPlayer, slot, item, action) -> {
            openPlayerRepairPicker(clickedPlayer, returnGuide, 0);
            return false;
        });

        addBack(menu, 27, "&fBack to Doctor Console", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openPlayerRepairPicker(
            @Nonnull Player viewer, @Nonnull ItemStack returnGuide, int requestedPage) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (players.size() + PLAYER_PAGE_SIZE - 1) / PLAYER_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        ChestMenu menu = subMenu("&6Online Player Repair " + (page + 1) + "/" + pages, 54);
        int start = page * PLAYER_PAGE_SIZE;
        int end = Math.min(players.size(), start + PLAYER_PAGE_SIZE);
        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            int slot = i - start;
            menu.addItem(slot, playerHead(target));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                if (!target.isOnline()) {
                    clickedPlayer.sendMessage(ChatColor.RED + "That player is no longer online.");
                    openPlayerRepairPicker(clickedPlayer, returnGuide, page);
                    return false;
                }
                openTargetedRepairConfirmation(clickedPlayer, returnGuide, target, false);
                return false;
            });
        }

        menu.addItem(45, menuItem(Material.ARROW, "&fBack", "", "&7Return to Player & Item Repair."));
        menu.addMenuClickHandler(45, (clickedPlayer, slot, item, action) -> {
            openPlayerItemRepair(clickedPlayer, returnGuide);
            return false;
        });

        if (page > 0) {
            menu.addItem(48, menuItem(Material.ARROW, "&ePrevious Page"));
            menu.addMenuClickHandler(48, (clickedPlayer, slot, item, action) -> {
                openPlayerRepairPicker(clickedPlayer, returnGuide, page - 1);
                return false;
            });
        }
        menu.addItem(50, menuItem(Material.PAPER, "&fPage " + (page + 1) + " / " + pages, "", "&7Online: &e" + players.size()));
        if (page + 1 < pages) {
            menu.addItem(52, menuItem(Material.ARROW, "&eNext Page"));
            menu.addMenuClickHandler(52, (clickedPlayer, slot, item, action) -> {
                openPlayerRepairPicker(clickedPlayer, returnGuide, page + 1);
                return false;
            });
        }

        menu.open(viewer);
    }

    private static void openTargetedRepairConfirmation(
            @Nonnull Player viewer,
            @Nonnull ItemStack returnGuide,
            @Nullable Player target,
            boolean handOnly) {
        ChestMenu menu = confirmationMenu("&6&lConfirm Targeted Repair");
        String targetName = handOnly ? "your held item" : target == null ? "unknown player" : target.getName();

        menu.addItem(
                11,
                menuItem(
                        Material.LIME_CONCRETE,
                        "&aConfirm Targeted Repair",
                        "",
                        "&7Target: &f" + targetName,
                        handOnly
                                ? "&7Repairs only safely recoverable presentation on your held item."
                                : "&7Repairs eligible presentation in inventory + ender chest.",
                        "",
                        "&8This does not run the server-wide Doctor repair."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            clickedPlayer.closeInventory();
            if (handOnly) {
                clickedPlayer.performCommand("slimefun doctor hand");
            } else if (target != null && target.isOnline()) {
                clickedPlayer.performCommand("slimefun doctor inventory " + target.getName());
            } else {
                clickedPlayer.sendMessage(ChatColor.RED + "That player is no longer online.");
            }
            return false;
        });

        menu.addItem(15, menuItem(Material.BARRIER, "&cCancel", "", "&7Return without changing anything."));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            openPlayerItemRepair(clickedPlayer, returnGuide);
            return false;
        });
        menu.open(viewer);
    }

    private static void openAddonDependencyHealth(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        long dependencies = DoctorGuideAssistant.dependencyProblemCount();
        long addonFailures = DoctorGuideAssistant.addonRuntimeFailureCount();
        ChestMenu menu = subMenu("&d&lAddon & Dependency Health", 36);

        addCommandButton(
                menu,
                10,
                Material.COMPARATOR,
                "&dAddon Compatibility",
                "slimefun doctor compatibility",
                "&7Inspect installed addon compatibility evidence.",
                "&7Active callback records: &e" + addonFailures);
        addCommandButton(
                menu,
                12,
                dependencies > 0 ? Material.REDSTONE : Material.EMERALD,
                "&cRequired Dependencies",
                "slimefun doctor dependencies",
                "&7Find missing/disabled hard plugin dependencies.",
                "&7Current problems: " + (dependencies == 0 ? "&a0" : "&c" + dependencies));
        addCommandButton(
                menu,
                14,
                Material.ENCHANTED_BOOK,
                "&bAddon Doctor Scan",
                "slimefun doctor addons scan",
                "&7Run all registered addon Doctor providers read-only.");
        addCommandButton(
                menu,
                16,
                Material.BOOKSHELF,
                "&eRegistry Health",
                "slimefun doctor registry",
                "&7Show registered items, groups, tickers and addon ownership.");
        addCommandButton(
                menu,
                22,
                Material.NETHER_STAR,
                "&bCross-Fork API Evidence",
                "slimefun doctor compatibility api",
                "&7Inspect the active addon API compatibility facade.");

        addBack(menu, 27, "&fBack to Doctor Console", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openRuntimeRecovery(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        ChestMenu menu = subMenu("&c&lRuntime Recovery", 45);

        addCommandButton(
                menu,
                10,
                Material.REDSTONE_TORCH,
                "&cRuntime & Storage Status",
                "slimefun doctor status",
                "&7Pending writes: &e" + storage.getPendingWrites(),
                "&7Active machine failures: &e" + machines.getActiveMachineFailures(),
                "&7Paused machine circuits: &e" + machines.getPausedMachineCircuits());
        addCommandButton(
                menu,
                12,
                Material.BEACON,
                "&bCore Health",
                "slimefun doctor core",
                "&7Lifecycle, readiness, scheduler, registry and storage.");
        addCommandButton(
                menu,
                14,
                Material.GRASS_BLOCK,
                "&aWorld / Chunk Health",
                "slimefun doctor chunks",
                "&7Chunk lifecycle, block storage and ticker correlation.");
        addCommandButton(
                menu,
                16,
                Material.IRON_PICKAXE,
                "&eMachine Failure Detail",
                "slimefun doctor runtime",
                "&7List currently isolated/failing machine locations.");
        addCommandButton(
                menu,
                20,
                Material.HOPPER,
                "&dExternal Integrations",
                "slimefun doctor integrations",
                "&7Inspect Rebar/Pylon/external adapter status and failures.");

        menu.addItem(
                22,
                menuItem(
                        Material.LEVER,
                        "&eRetry Looked-at Machine",
                        "",
                        "&7Clears runtime isolation only for the machine",
                        "&7you are looking at within 8 blocks.",
                        "",
                        "&8Does not rewrite stored machine data.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(22, (clickedPlayer, slot, item, action) -> {
            openRuntimeRetryConfirmation(clickedPlayer, returnGuide, false);
            return false;
        });

        menu.addItem(
                24,
                menuItem(
                        Material.REDSTONE_BLOCK,
                        "&cRetry All Isolated Machines",
                        "",
                        "&7Clears current runtime isolation state for all",
                        "&7Slimefun machines so normal tickers can retry.",
                        "",
                        "&8Does not rewrite machine storage or recipes.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(24, (clickedPlayer, slot, item, action) -> {
            openRuntimeRetryConfirmation(clickedPlayer, returnGuide, true);
            return false;
        });

        menu.addItem(
                30,
                menuItem(
                        Material.REPEATER,
                        "&eReload External Integration Adapters",
                        "",
                        "&7Clears temporary adapter isolation and refreshes",
                        "&7external integration discovery.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(30, (clickedPlayer, slot, item, action) -> {
            openIntegrationReloadConfirmation(clickedPlayer, returnGuide);
            return false;
        });

        addBack(menu, 36, "&fBack to Doctor Console", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openRuntimeRetryConfirmation(
            @Nonnull Player player, @Nonnull ItemStack returnGuide, boolean all) {
        ChestMenu menu = confirmationMenu(all ? "&c&lConfirm Retry All" : "&e&lConfirm Machine Retry");
        menu.addItem(
                11,
                menuItem(
                        Material.LIME_CONCRETE,
                        all ? "&aConfirm Retry All Machines" : "&aConfirm Targeted Retry",
                        "",
                        all
                                ? "&7Clears isolation state for all currently paused/failing machines."
                                : "&7Clears isolation for the machine you are looking at.",
                        "&8Normal ticker execution decides what happens next.",
                        "&8Stored machine data is not rewritten."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            runCommand(clickedPlayer, all ? "slimefun doctor runtime retry all" : "slimefun doctor runtime retry");
            return false;
        });
        menu.addItem(15, menuItem(Material.BARRIER, "&cCancel"));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            openRuntimeRecovery(clickedPlayer, returnGuide);
            return false;
        });
        menu.open(player);
    }

    private static void openIntegrationReloadConfirmation(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ChestMenu menu = confirmationMenu("&e&lConfirm Integration Reload");
        menu.addItem(
                11,
                menuItem(
                        Material.LIME_CONCRETE,
                        "&aConfirm Adapter Reload",
                        "",
                        "&7Clears temporary external-adapter isolation",
                        "&7and refreshes integration discovery.",
                        "&8No plugin is installed/enabled/disabled by this action."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            runCommand(clickedPlayer, "slimefun doctor integrations reload");
            return false;
        });
        menu.addItem(15, menuItem(Material.BARRIER, "&cCancel"));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            openRuntimeRecovery(clickedPlayer, returnGuide);
            return false;
        });
        menu.open(player);
    }

    private static void addOtherDoctorFixesButton(@Nonnull ChestMenu menu, @Nonnull ItemStack returnGuide) {
        menu.addItem(
                28,
                menuItem(
                        Material.ENCHANTED_BOOK,
                        "&bOther Doctor Fixes",
                        "",
                        "&7Alternative diagnostics and specialist lanes.",
                        "&7Includes scans, storage integrity, upgrade",
                        "&7readiness, migrations and names/lore repair.",
                        "&eClick to open"));
        menu.addMenuClickHandler(28, (player, slot, item, action) -> {
            openOtherDoctorFixes(player, returnGuide);
            return false;
        });
    }

    private static void openOtherDoctorFixes(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ChestMenu menu = subMenu("&b&lOther Doctor Fixes", 45);

        addCommandButton(
                menu,
                10,
                Material.SPYGLASS,
                "&bFull Doctor Scan",
                "slimefun doctor scan",
                "&7Read-only server-wide scan and classification.",
                "&aBest first step when the cause is unclear.");

        menu.addItem(
                12,
                menuItem(
                        Material.NAME_TAG,
                        "&6Repair Names & Lore",
                        "",
                        "&7Repairs safely recoverable Slimefun display",
                        "&7names/lore while preserving item identity/data.",
                        "&cServer-wide item change. Full backup first.",
                        "&eClick for confirmation"));
        menu.addMenuClickHandler(12, (clickedPlayer, slot, item, action) -> {
            openGeneralRepairConfirmation(clickedPlayer, returnGuide);
            return false;
        });

        addCommandButton(
                menu,
                14,
                Material.COMPARATOR,
                "&eScan Item Models",
                "slimefun doctor item-models scan",
                "&7Read-only stale bundled item-model inspection.");
        addCommandButton(
                menu,
                16,
                Material.WRITABLE_BOOK,
                "&dLegacy-ID Migration Plan",
                "slimefun doctor migrations plan",
                "&7Correlate unknown/legacy IDs with addon providers.");
        addCommandButton(
                menu,
                20,
                Material.BARREL,
                "&eStorage Integrity Status",
                "slimefun doctor storage status",
                "&7Inspect guarded storage-integrity recovery state.");
        addCommandButton(
                menu,
                22,
                Material.NETHER_STAR,
                "&bUpgrade Readiness",
                "slimefun doctor upgrade",
                "&7Read-only core/addon/runtime upgrade readiness.");
        addCommandButton(
                menu,
                24,
                Material.LECTERN,
                "&dSchema Migration Scan",
                "slimefun doctor migrations schemas scan",
                "&7Probe addon-owned same-ID schema migrations read-only.");
        addCommandButton(
                menu,
                30,
                Material.REDSTONE_TORCH,
                "&cRuntime & Storage Health",
                "slimefun doctor status",
                "&7Shutdown, pending writes, machine isolation and scan state.");

        addBack(menu, 36, "&fBack to Doctor Console", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openSupportSummary(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ExternalResourcePackService packs = new ExternalResourcePackService(Slimefun.instance());
        StorageRuntimeSnapshot storage = Slimefun.getStorageRuntimeService().getSnapshot();
        MachineRuntimeSnapshot machines = Slimefun.getMachineRuntimeService().getSnapshot();
        ItemDoctorReport last = Slimefun.getItemDoctorService().getLastReport();
        var textures = Slimefun.getItemTextureService();
        DoctorGuideAssistant.Recommendation recommendation = DoctorGuideAssistant.recommend();

        ChestMenu menu = subMenu("&f&lDoctor Support Summary", 45);
        menu.addItem(
                10,
                menuItem(
                        Material.BOOK,
                        "&fPlatform",
                        "",
                        "&7Slimefun Legacy: &e" + Slimefun.instance().getPluginMeta().getVersion(),
                        "&7Server: &e" + Bukkit.getName(),
                        "&7Java: &e" + System.getProperty("java.version")));
        menu.addItem(
                12,
                menuItem(
                        storage.getPendingWrites() == 0 ? Material.CHEST : Material.REDSTONE,
                        "&fStorage",
                        "",
                        "&7Ready: " + (storage.isReady() ? "&aYes" : "&cNo"),
                        "&7Previous clean shutdown: " + (storage.wasPreviousShutdownClean() ? "&aYes" : "&eNo"),
                        "&7Pending writes: &e" + storage.getPendingWrites()));
        menu.addItem(
                14,
                menuItem(
                        machines.getActiveMachineFailures() == 0 ? Material.FURNACE : Material.BLAST_FURNACE,
                        "&fMachines",
                        "",
                        "&7Active failures: &e" + machines.getActiveMachineFailures(),
                        "&7Paused circuits: &e" + machines.getPausedMachineCircuits(),
                        "&7Observed failures: &e" + machines.getObservedMachineFailures()));
        menu.addItem(
                16,
                menuItem(
                        Material.MAP,
                        "&fPack & Item Models",
                        "",
                        "&7Legacy sender: " + (packs.isDeliveryEnabled() ? "&aEnabled" : "&7Disabled"),
                        "&7Known external pack managers: &e" + DoctorGuideAssistant.detectedPackManagers(),
                        "&7Bundled mappings active: &e" + textures.getHostedPackEnabledMappingCount(),
                        "&7Mappings available: &e" + textures.getHostedPackEnableCandidateCount(),
                        "&7Custom mappings preserved: &e" + textures.getHostedPackCustomMappingCount(),
                        "",
                        "&8Sender OFF + mappings ON is valid for a combined pack."));

        menu.addItem(
                20,
                menuItem(
                        DoctorGuideAssistant.dependencyProblemCount() == 0 ? Material.EMERALD : Material.REDSTONE,
                        "&fDependencies & Addons",
                        "",
                        "&7Required dependency problems: "
                                + (DoctorGuideAssistant.dependencyProblemCount() == 0
                                        ? "&a0"
                                        : "&c" + DoctorGuideAssistant.dependencyProblemCount()),
                        "&7Active addon callback records: &e" + DoctorGuideAssistant.addonRuntimeFailureCount(),
                        "&7External integration failures: &e"
                                + Slimefun.getExternalIntegrationService().getActiveFailureCount()));

        if (last == null) {
            menu.addItem(
                    22,
                    menuItem(
                            Material.PAPER,
                            "&fLast Doctor Scan",
                            "",
                            "&7No server-wide Doctor traversal has completed.",
                            "&eRecommended: run Full Doctor Scan."));
        } else {
            menu.addItem(
                    22,
                    menuItem(
                            Material.PAPER,
                            "&fLast Doctor " + (last.isRepairMode() ? "Repair" : "Scan"),
                            "",
                            "&7Complete: " + (last.isComplete() ? "&aYes" : "&eNo"),
                            "&7Slimefun stacks: &e" + last.getSlimefunStacks(),
                            "&7Unknown IDs: &e" + last.getUnknownIds(),
                            "&7Item-model candidates: &e" + last.getItemModelCandidates(),
                            "&7Legacy-ID candidates: &e" + last.getLegacyMigrationCandidates(),
                            "&7Schema candidates: &e" + last.getSchemaMigrationCandidates(),
                            "&7Failures: &e" + last.getFailures()));
        }

        menu.addItem(
                24,
                menuItem(
                        recommendation.issueCount() > 0 ? Material.COMPASS : Material.RECOVERY_COMPASS,
                        "&fRecommended Next Step",
                        "",
                        "&e" + recommendation.title(),
                        "&7" + recommendation.detail(),
                        "",
                        "&eClick to follow recommendation"));
        menu.addMenuClickHandler(24, (clickedPlayer, slot, item, action) -> {
            routeRecommendation(clickedPlayer, returnGuide, recommendation);
            return false;
        });

        menu.addItem(
                30,
                menuItem(
                        Material.WRITABLE_BOOK,
                        "&bPrint Full Support Report to Chat",
                        "",
                        "&7Runs /sf doctor report with the full compact",
                        "&7support snapshot and Doctor next steps.",
                        "&eClick to print"));
        menu.addMenuClickHandler(30, (clickedPlayer, slot, item, action) -> {
            runCommand(clickedPlayer, "slimefun doctor report");
            return false;
        });

        addBack(menu, 36, "&fBack to Doctor Console", () -> open(player, returnGuide));
        menu.open(player);
    }

    private static void openGeneralRepairConfirmation(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ChestMenu menu = confirmationMenu("&6&lConfirm Doctor Repair");
        menu.addItem(
                11,
                menuItem(
                        Material.LIME_CONCRETE,
                        "&aConfirm Names & Lore Repair",
                        "",
                        "&7Runs:",
                        "&f/sf doctor repair confirm",
                        "",
                        "&7Repairs only safely recoverable Slimefun",
                        "&7presentation data across reachable storage.",
                        "&cOnly continue after a full backup."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            runCommand(clickedPlayer, "slimefun doctor repair confirm");
            return false;
        });
        menu.addItem(15, menuItem(Material.BARRIER, "&cCancel", "", "&7Return to Other Doctor Fixes."));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            openOtherDoctorFixes(clickedPlayer, returnGuide);
            return false;
        });
        menu.open(player);
    }

    private static void openUpgradeConfirmation(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        ChestMenu menu = confirmationMenu("&6&lConfirm Item Upgrade");
        menu.addItem(
                11,
                menuItem(
                        Material.LIME_CONCRETE,
                        "&aConfirm Upgrade Items",
                        "",
                        "&7Runs:",
                        "&f/sf doctor item-models enable-pack confirm",
                        "",
                        "&7Updates exact bundled mappings and reachable",
                        "&7Slimefun ItemStacks for packs using Legacy models.",
                        "&cOnly continue after a full backup."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            runCommand(clickedPlayer, "slimefun doctor item-models enable-pack confirm");
            return false;
        });
        addCancel(menu, returnGuide);
        menu.open(player);
    }

    private static void openRemoveModelsConfirmation(@Nonnull Player player, @Nonnull ItemStack returnGuide) {
        int mappingCandidates = Slimefun.getItemTextureService().getHostedPackRemovalCandidateCount();
        ChestMenu menu = confirmationMenu("&d&lConfirm Model Cleanup");

        if (mappingCandidates > 0) {
            menu.addItem(
                    11,
                    menuItem(
                            Material.RED_CONCRETE,
                            "&cConfirm Remove Bundled Mappings",
                            "",
                            "&7Resets only exact Legacy bundled mappings to 0.",
                            "&aDo not use merely because Legacy's sender is disabled.",
                            "&7External/combined packs may still depend on them.",
                            "",
                            "&7Afterward: stop normally, restart, scan, then",
                            "&7repair stale stored item models if appropriate.",
                            "&cOnly continue after a full backup."));
            menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
                runCommand(clickedPlayer, "slimefun doctor item-models remove-resourcepack-texture-ids confirm");
                return false;
            });
        } else {
            menu.addItem(
                    11,
                    menuItem(
                            Material.GRINDSTONE,
                            "&dConfirm Clean Stored Item Models",
                            "",
                            "&7Runs the guarded stored-item model cleanup.",
                            "&7Eligible stale exact bundled data is removed.",
                            "&8Custom/non-matching model data is preserved.",
                            "&cOnly continue after a full backup."));
            menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
                runCommand(clickedPlayer, "slimefun doctor item-models repair confirm");
                return false;
            });
        }

        addCancel(menu, returnGuide);
        menu.open(player);
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

    private static void sendInspection(
            @Nonnull Player player, @Nonnull String label, @Nonnull ItemDoctorReport report) {
        player.sendMessage(ChatColor.GOLD + "Slimefun Doctor — " + label);
        player.sendMessage(ChatColor.GRAY + "Slimefun stacks: " + ChatColor.YELLOW + report.getSlimefunStacks());
        player.sendMessage(ChatColor.GRAY + "Presentation findings: " + ChatColor.YELLOW
                + (report.getCjkStacks() + report.getCjkBlocks()));
        player.sendMessage(ChatColor.GRAY + "Unknown IDs: " + ChatColor.YELLOW + report.getUnknownIds());
        player.sendMessage(
                ChatColor.GRAY + "Unresolved templates: " + ChatColor.YELLOW + report.getUnresolvedTemplates());
        player.sendMessage(
                ChatColor.GRAY + "Item-model candidates: " + ChatColor.YELLOW + report.getItemModelCandidates());
        player.sendMessage(ChatColor.GRAY + "No changes were made.");
    }

    private static ItemStack playerHead(@Nonnull Player player) {
        ItemStack item = menuItem(
                Material.PLAYER_HEAD,
                "&e" + player.getName(),
                "",
                "&7Click to review a targeted repair for this",
                "&7player's inventory and ender chest.");
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ChestMenu subMenu(@Nonnull String title, int size) {
        ChestMenu menu = new ChestMenu(title);
        menu.setSize(size);
        menu.setEmptySlotsClickable(false);
        fill(menu, size);
        return menu;
    }

    private static ChestMenu confirmationMenu(@Nonnull String title) {
        return subMenu(title, 27);
    }

    private static void fill(@Nonnull ChestMenu menu, int size) {
        for (int slot = 0; slot < size; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
    }

    private static void addCancel(@Nonnull ChestMenu menu, @Nonnull ItemStack returnGuide) {
        menu.addItem(
                15,
                menuItem(
                        Material.BARRIER,
                        "&cCancel",
                        "",
                        "&7Return to Slimefun Doctor without making changes."));
        menu.addMenuClickHandler(15, (clickedPlayer, slot, item, action) -> {
            open(clickedPlayer, returnGuide);
            return false;
        });
    }

    private static String abbreviate(@Nonnull String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static ItemStack menuItem(@Nonnull Material material, @Nonnull String name, @Nonnull String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.stream(lore)
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList());
        item.setItemMeta(meta);
        return item;
    }
}
