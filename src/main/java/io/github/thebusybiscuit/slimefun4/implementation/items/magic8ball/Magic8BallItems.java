package io.github.thebusybiscuit.slimefun4.implementation.items.magic8ball;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Legacy-compatible item definitions for the built-in Magic 8 Ball module. */
public final class Magic8BallItems {

    public static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dX"
            + "Jlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGYyNDEyZDYzZGQyZTBlMjMyMjMwYWJkMDRhOGY3MjZlNTFjOTNhNzI2ODZj"
            + "N2RhZTE3MjJjNDY3N2Y5ZjU0OCJ9fX0=";

    public static final SlimefunItemStack MAGIC_8_BALL_FRAGMENT = new SlimefunItemStack(
            "MAGIC_8_BALL_FRAGMENT",
            Material.ECHO_SHARD,
            "&fMagic &68 &fBall &bFragment",
            "",
            "Emits traces of &6knowledge",
            "");

    public static final SlimefunItemStack MAGIC_8_BALL = new SlimefunItemStack(
            "MAGIC_8_BALL",
            HEAD_TEXTURE,
            "&fMagic &68 &fBall",
            "",
            "&fCan answer anything ..",
            "&f.. maybe, my sources says so",
            "",
            "&eLeft Click&7 on air to use",
            "&eRight Click&7 to use (on block)",
            "");

    public static final ItemGroup ITEM_GROUP = new ItemGroup(
            Objects.requireNonNull(NamespacedKey.fromString("magic8ball:main_group")), createItemGroupIcon());

    static {
        applyLegacyMeta(MAGIC_8_BALL_FRAGMENT);
        applyLegacyMeta(MAGIC_8_BALL);
    }

    private Magic8BallItems() {}

    @SuppressWarnings("deprecation")
    private static ItemStack createItemGroupIcon() {
        ItemStack icon = MAGIC_8_BALL.clone();
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName("§eMagic 8 Ball");
        icon.setItemMeta(meta);
        return icon;
    }

    private static void applyLegacyMeta(SlimefunItemStack item) {
        item.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
    }
}
