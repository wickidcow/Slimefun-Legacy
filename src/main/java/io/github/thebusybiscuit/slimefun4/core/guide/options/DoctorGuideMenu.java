package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Administrative Slimefun Doctor controls exposed from the Guide settings menu. */
final class DoctorGuideMenu {

    private DoctorGuideMenu() {}

    static void open(@Nonnull Player player, @Nullable ItemStack guide) {
        ItemStack returnGuide =
                guide == null ? SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE) : guide.clone();
        ExternalResourcePackService packService = new ExternalResourcePackService(Slimefun.instance());
        var textures = Slimefun.getItemTextureService();

        ChestMenu menu = new ChestMenu("&4&lSlimefun Doctor");
        menu.setSize(27);
        menu.setEmptySlotsClickable(false);

        for (int slot = 0; slot < 27; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        menu.addItem(
                4,
                menuItem(
                        Material.REDSTONE,
                        "&c&lResource Pack Doctor",
                        "",
                        "&7Legacy pack sender: "
                                + (packService.isDeliveryEnabled() ? "&aEnabled" : "&cDisabled"),
                        "&7Bundled model mappings active: &e" + textures.getHostedPackEnabledMappingCount(),
                        "&7Bundled mappings available: &e" + textures.getHostedPackEnableCandidateCount(),
                        "&7Custom mappings preserved: &e" + textures.getHostedPackCustomMappingCount(),
                        "",
                        "&8These controls affect only Slimefun Legacy's pack/mappings."));

        addEnablePackButton(menu, packService, returnGuide);
        addUpgradeItemsButton(menu, returnGuide);
        addDisablePackButton(menu, packService, returnGuide);
        addRemoveModelsButton(menu, returnGuide);

        menu.addItem(
                18,
                menuItem(
                        Material.ARROW,
                        "&fBack to Guide Settings",
                        "",
                        "&7Return to Slimefun Guide settings."));
        menu.addMenuClickHandler(18, (clickedPlayer, slot, item, action) -> {
            SlimefunGuideSettings.openSettings(clickedPlayer, returnGuide);
            return false;
        });

        menu.addItem(
                22,
                menuItem(
                        Material.BOOK,
                        "&fWhat each Doctor action does",
                        "",
                        "&aEnable Resource Pack",
                        "&7Turns Slimefun Legacy pack delivery on.",
                        "&8Does not change item models.",
                        "",
                        "&6Upgrade Items for Resource Pack",
                        "&7Adds exact bundled mappings to zero IDs and",
                        "&7updates reachable stored Slimefun items.",
                        "",
                        "&cDisable Resource Pack",
                        "&7Stops Legacy pack delivery and removes only",
                        "&7Legacy's pack UUID from online players.",
                        "&8Does not change item models.",
                        "",
                        "&dRemove Resource-Pack Item Models",
                        "&7Safely removes exact bundled mappings/models.",
                        "&8Custom/non-matching models are preserved."));

        menu.open(player);
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
                        enabled ? "&aResource Pack Enabled" : "&aEnable Resource Pack",
                        "",
                        "&7Turns Slimefun Legacy's pack sender ON.",
                        "&7Eligible online players receive the pack immediately.",
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
        var textures = Slimefun.getItemTextureService();
        int candidates = textures.getHostedPackEnableCandidateCount();

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
                        enabled ? "&cDisable Resource Pack" : "&7Resource Pack Disabled",
                        "",
                        "&7Turns Slimefun Legacy's pack sender OFF.",
                        "&7Removes only Legacy's pack UUID from online players.",
                        "",
                        "&8This does not alter item-models.yml or stored items.",
                        enabled ? "&eClick to disable" : "&8Already disabled."));

        menu.addMenuClickHandler(14, (player, slot, item, action) -> {
            if (!service.setDeliveryEnabled(false)) {
                player.sendMessage(ChatColor.RED + "Could not save resource-pack.enabled in configSFLAddons.yml.");
                return false;
            }

            player.sendMessage(ChatColor.YELLOW + "Slimefun Legacy resource-pack delivery is disabled.");
            open(player, returnGuide);
            return false;
        });
    }

    private static void addRemoveModelsButton(@Nonnull ChestMenu menu, @Nonnull ItemStack returnGuide) {
        var textures = Slimefun.getItemTextureService();
        int mappingCandidates = textures.getHostedPackRemovalCandidateCount();

        menu.addItem(
                16,
                menuItem(
                        Material.GRINDSTONE,
                        "&dRemove Resource-Pack Item Models",
                        "",
                        mappingCandidates > 0
                                ? "&7Step 1: reset exact Legacy bundled mappings to 0."
                                : "&7Mappings are already clear; clean stale bundled",
                        mappingCandidates > 0
                                ? "&7Restart normally, then return here to clean stored items."
                                : "&7model data from reachable stored Slimefun items.",
                        "",
                        "&7Exact bundled mappings to remove: &e" + mappingCandidates,
                        "&8Custom/non-matching model values are preserved.",
                        "&cServer-wide recovery. Full backup first.",
                        "&eClick for confirmation"));

        menu.addMenuClickHandler(16, (player, slot, item, action) -> {
            openRemoveModelsConfirmation(player, returnGuide);
            return false;
        });
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
                        "&7Slimefun ItemStacks for the Legacy resource pack.",
                        "",
                        "&cOnly continue after a full backup."));
        menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
            clickedPlayer.closeInventory();
            clickedPlayer.performCommand("slimefun doctor item-models enable-pack confirm");
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
                            "&7Runs:",
                            "&f/sf doctor item-models",
                            "&fremove-resourcepack-texture-ids confirm",
                            "",
                            "&7Resets only exact Legacy bundled mappings to 0.",
                            "&7Afterward, stop normally, restart, and return",
                            "&7to this button to clean stale stored item models.",
                            "",
                            "&cOnly continue after a full backup."));
            menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
                clickedPlayer.closeInventory();
                clickedPlayer.performCommand("slimefun doctor item-models remove-resourcepack-texture-ids confirm");
                return false;
            });
        } else {
            menu.addItem(
                    11,
                    menuItem(
                            Material.GRINDSTONE,
                            "&dConfirm Clean Stored Item Models",
                            "",
                            "&7Runs:",
                            "&f/sf doctor item-models repair confirm",
                            "",
                            "&7Removes eligible stale bundled model data",
                            "&7from reachable stored Slimefun ItemStacks.",
                            "",
                            "&8Custom/non-matching model data is preserved.",
                            "&cOnly continue after a full backup."));
            menu.addMenuClickHandler(11, (clickedPlayer, slot, item, action) -> {
                clickedPlayer.closeInventory();
                clickedPlayer.performCommand("slimefun doctor item-models repair confirm");
                return false;
            });
        }

        addCancel(menu, returnGuide);
        menu.open(player);
    }

    private static ChestMenu confirmationMenu(@Nonnull String title) {
        ChestMenu menu = new ChestMenu(title);
        menu.setSize(27);
        menu.setEmptySlotsClickable(false);

        for (int slot = 0; slot < 27; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        return menu;
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
