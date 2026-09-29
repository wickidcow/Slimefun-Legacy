package io.github.thebusybiscuit.slimefun4.utils.compatibility;

import org.bukkit.inventory.ItemFlag;

public final class VersionedItemFlag {

    /**
     * Compatibility bridge for Bukkit's former broad "additional tooltip" flag.
     *
     * <p>The modern TooltipDisplay API requires callers to enumerate individual data components.
     * Slimefun uses this constant on generic guide/category items where the concrete component set
     * is not known in advance, so replacing it with a blanket component list could hide names,
     * lore, or addon-provided presentation data.
     */
    @SuppressWarnings("deprecation")
    public static final ItemFlag HIDE_ADDITIONAL_TOOLTIP = ItemFlag.HIDE_ADDITIONAL_TOOLTIP;

    private VersionedItemFlag() {}
}
