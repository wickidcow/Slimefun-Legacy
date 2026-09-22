package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.ExternalResourcePackService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import javax.annotation.Nonnull;
import java.util.Arrays;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Player and operator controls for Slimefun Legacy's optional resource-pack sender.
 *
 * <p>The player toggle is intentionally scoped to Slimefun Legacy's own pack UUID. It never removes or changes
 * resource packs owned by ItemsAdder, Oraxen, a proxy, or another plugin. Server-side model-map changes remain
 * guarded Doctor operations with a separate confirmation screen.</p>
 */
final class ResourcePackGuideMenu {

    private static final String DOCTOR_PERMISSION = "slimefun.command.doctor";

    private ResourcePackGuideMenu() {}

    static void open(@Nonnull Player player, @Nullable ItemStack guide) {
        ItemStack returnGuide =
                guide == null ? SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE) : guide.clone();
        ExternalResourcePackService service = new ExternalResourcePackService(Slimefun.instance());

        ChestMenu menu = new ChestMenu("&2&lSlimefun Resource Pack");
        menu.setSize(27);
        menu.setEmptySlotsClickable(false);

        for (int slot = 0; slot < 27; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        addBackButton(menu, returnGuide);
        addStatus(menu, player, service);
        addPlayerToggle(menu, player, service, returnGuide);
        addReloadButton(menu, player, service, returnGuide);
        addAdminDoctorShortcut(menu, player, returnGuide);

        menu.addItem(
                22,
                menuItem(
                        Material.BOOK,
                        "&fWhat these controls change",
                        "",
                        "&7Player On/Off and Reload affect only",
                        "&7Slimefun Legacy's own optional pack UUID.",
                        "",
                        "&7They do not remove ItemsAdder, Oraxen,",
                        "&7proxy, or other plugin-owned packs.",
                        "",
                        "&8Server-wide item texture recovery is available",
                        "&8only to OP/admin users in Recovery Center."),
                ChestMenuUtils.getEmptyClickHandler());

        menu.open(player);
    }

    private static void addBackButton(@Nonnull ChestMenu menu, @Nonnull ItemStack guide) {
        menu.addItem(
                18,
                menuItem(
                        Material.ARROW,
                        "&fBack to Slimefun Guide",
                        "",
                        "&7Return to the current guide."));
        menu.addMenuClickHandler(18, (player, slot, item, action) -> {
            SlimefunGuide.openGuide(player, guide);
            return false;
        });
    }

    private static void addStatus(
            @Nonnull ChestMenu menu, @Nonnull Player player, @Nonnull ExternalResourcePackService service) {
        boolean senderEnabled = service.isDeliveryEnabled();
        boolean required = service.isRequired();
        boolean playerEnabled = service.isPlayerEnabled(player);

        menu.addItem(
                4,
                menuItem(
                        Material.PAINTING,
                        "&b&lResource Pack Status",
                        "",
                        "&7Legacy sender: " + (senderEnabled ? "&aEnabled" : "&cDisabled"),
                        "&7Required by server: " + (required ? "&cYes" : "&aNo"),
                        "&7Your auto-load: " + (playerEnabled ? "&aOn" : "&cOff"),
                        "",
                        senderEnabled
                                ? "&7Use Reload if textures need to be re-applied."
                                : "&8The server owner has Legacy's sender disabled."),
                ChestMenuUtils.getEmptyClickHandler());
    }

    private static void addPlayerToggle(
            @Nonnull ChestMenu menu,
            @Nonnull Player player,
            @Nonnull ExternalResourcePackService service,
            @Nonnull ItemStack returnGuide) {
        boolean enabled = service.isPlayerEnabled(player);
        boolean required = service.isRequired();

        Material material = required ? Material.BARRIER : (enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        String title = required
                ? "&cResource Pack Required"
                : enabled ? "&aAutomatic Resource Pack: ON" : "&7Automatic Resource Pack: OFF";

        menu.addItem(
                10,
                menuItem(
                        material,
                        title,
                        "",
                        required
                                ? "&7This server marks the Slimefun Legacy pack as required."
                                : enabled
                                        ? "&7Click to opt out and remove Legacy's pack."
                                        : "&7Click to opt in and load Legacy's pack on join.",
                        "",
                        "&8This preference is saved per player."));

        menu.addMenuClickHandler(10, (clickedPlayer, slot, item, action) -> {
            if (service.isRequired()) {
                clickedPlayer.sendMessage(ChatColor.RED + "The Slimefun Legacy resource pack is required by this server.");
                return false;
            }

            boolean next = !service.isPlayerEnabled(clickedPlayer);
            service.setPlayerEnabled(clickedPlayer, next);
            if (next) {
                if (service.isDeliveryEnabled()) {
                    clickedPlayer.sendMessage(ChatColor.GREEN + "Slimefun Legacy resource-pack auto-load enabled.");
                } else {
                    clickedPlayer.sendMessage(
                            ChatColor.YELLOW
                                    + "Auto-load preference enabled, but the server's Slimefun Legacy pack sender is disabled.");
                }
            } else {
                clickedPlayer.sendMessage(ChatColor.YELLOW + "Slimefun Legacy resource pack disabled for you.");
            }

            open(clickedPlayer, returnGuide);
            return false;
        });
    }

    private static void addReloadButton(
            @Nonnull ChestMenu menu,
            @Nonnull Player player,
            @Nonnull ExternalResourcePackService service,
            @Nonnull ItemStack returnGuide) {
        boolean senderEnabled = service.isDeliveryEnabled();

        menu.addItem(
                12,
                menuItem(
                        senderEnabled ? Material.CHEST : Material.RED_DYE,
                        senderEnabled ? "&eReload Resource Pack Now" : "&cReload Unavailable",
                        "",
                        senderEnabled
                                ? "&7Re-send Slimefun Legacy's configured pack to your client."
                                : "&7The server owner has resource-pack.enabled set to false.",
                        senderEnabled ? "&7This also turns your auto-load preference on." : "",
                        "",
                        senderEnabled ? "&eClick to reload" : "&8No server setting is changed."));

        menu.addMenuClickHandler(12, (clickedPlayer, slot, item, action) -> {
            if (!service.reloadForPlayer(clickedPlayer)) {
                clickedPlayer.sendMessage(
                        ChatColor.RED + "Slimefun Legacy's resource-pack sender is disabled or unavailable.");
                return false;
            }

            clickedPlayer.sendMessage(ChatColor.GREEN + "Slimefun Legacy resource pack requested again.");
            open(clickedPlayer, returnGuide);
            return false;
        });
    }

    private static void addAdminDoctorShortcut(
            @Nonnull ChestMenu menu, @Nonnull Player player, @Nonnull ItemStack returnGuide) {
        if (!player.hasPermission(DOCTOR_PERMISSION)) {
            return;
        }

        menu.addItem(
                16,
                menuItem(
                        Material.TOTEM_OF_UNDYING,
                        "&6&lSlimefun Recovery Center",
                        "",
                        "&7Open the Recovery Center directly at",
                        "&6Resource Pack & Item Textures&7.",
                        "",
                        "&7Includes sender controls, upgrade scans,",
                        "&7model removal audits and item texture repairs.",
                        "",
                        "&8OP/Admin only • slimefun.command.doctor",
                        "&eClick to open resource-pack recovery"));
        menu.addMenuClickHandler(16, (clickedPlayer, slot, item, action) -> {
            DoctorGuideMenu.openResourcePackRecovery(clickedPlayer, returnGuide);
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
