package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineType;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Legacy-compatible item definitions for the built-in ExtraTools module. */
public final class ExtraToolsItems {

    public static final ItemGroup ITEM_GROUP = new ItemGroup(
            Objects.requireNonNull(NamespacedKey.fromString("extratools:extra_tools")), createItemGroupIcon());

    public static final SlimefunItemStack HAMMER =
            new SlimefunItemStack("HAMMER", Material.IRON_PICKAXE, "&cHammer", "", "&9Pulverizes blocks");

    public static final SlimefunItemStack GOLD_TRANSMUTER = new SlimefunItemStack(
            "GOLD_TRANSMUTER",
            Material.YELLOW_TERRACOTTA,
            "&6Gold Transmuter",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            LoreBuilder.powerBuffer(256),
            LoreBuilder.powerPerSecond(18));

    public static final SlimefunItemStack ELECTRIC_COMPOSTER = new SlimefunItemStack(
            "ELECTRIC_COMPOSTER",
            Material.MAGENTA_TERRACOTTA,
            "&cElectric Composter",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            "&8⇨ &7Speed: 1x",
            LoreBuilder.powerBuffer(256),
            LoreBuilder.powerPerSecond(18));

    public static final SlimefunItemStack ELECTRIC_COMPOSTER_2 = new SlimefunItemStack(
            "ELECTRIC_COMPOSTER_2",
            Material.MAGENTA_TERRACOTTA,
            "&cElectric Composter &7(&eII&7)",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            "&8⇨ &7Speed: 4x",
            LoreBuilder.powerBuffer(256),
            LoreBuilder.powerPerSecond(50));

    public static final SlimefunItemStack COBBLESTONE_GENERATOR = new SlimefunItemStack(
            "COBBLESTONE_GENERATOR",
            Material.POLISHED_ANDESITE,
            "&cCobblestone Generator",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            LoreBuilder.powerBuffer(512),
            LoreBuilder.powerPerSecond(32));

    public static final SlimefunItemStack VAPORIZER = new SlimefunItemStack(
            "VAPORIZER",
            Material.RED_STAINED_GLASS,
            "&cVaporizer",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            LoreBuilder.powerBuffer(256),
            LoreBuilder.powerPerSecond(32));

    public static final SlimefunItemStack CONCRETE_FACTORY = new SlimefunItemStack(
            "CONCRETE_FACTORY",
            Material.BLACK_CONCRETE,
            "&4Concrete Factory",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            LoreBuilder.powerBuffer(256),
            LoreBuilder.powerPerSecond(16));

    public static final SlimefunItemStack PULVERIZER = new SlimefunItemStack(
            "PULVERIZER",
            Material.ORANGE_TERRACOTTA,
            "&cPulverizer",
            "",
            LoreBuilder.machine(MachineTier.ADVANCED, MachineType.MACHINE),
            "&8⇨ &7Speed: 4x",
            LoreBuilder.powerBuffer(256),
            LoreBuilder.powerPerSecond(50));

    private ExtraToolsItems() {}

    @SuppressWarnings("deprecation")
    private static ItemStack createItemGroupIcon() {
        ItemStack icon = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "Extra Tools");
        icon.setItemMeta(meta);
        return icon;
    }
}
