package io.github.thebusybiscuit.slimefun4.utils.compatibility;

import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.inventory.ItemFlag;

public class VersionedItemFlag {

    @Nullable
    public static final ItemFlag HIDE_ADDITIONAL_TOOLTIP;

    static {
        MinecraftVersion version = Slimefun.getMinecraftVersion();

        if (version.isAtLeast(MinecraftVersion.MINECRAFT_1_20_5)) {
            HIDE_ADDITIONAL_TOOLTIP = getKey("HIDE_ADDITIONAL_TOOLTIP");
        } else {
            ItemFlag legacy = getKey("HIDE_POTION_EFFECTS");
            HIDE_ADDITIONAL_TOOLTIP = legacy != null ? legacy : getKey("HIDE_ADDITIONAL_TOOLTIP");
        }
    }

    @Nullable private static ItemFlag getKey(@Nonnull String key) {
        try {
            Field field = ItemFlag.class.getDeclaredField(key);
            return (ItemFlag) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }
}
