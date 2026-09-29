package io.github.thebusybiscuit.slimefun4.implementation.items.magical;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@link KnowledgeTome} allows you to copy every unlocked {@link Research}
 * from one {@link Player} to another.
 *
 * @author TheBusyBiscuit
 *
 */
public class KnowledgeTome extends SimpleSlimefunItem<ItemUseHandler> {

    @ParametersAreNonnullByDefault
    public KnowledgeTome(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return e -> {
            Player p = e.getPlayer();
            ItemStack item = e.getItem();

            e.setUseBlock(Result.DENY);

            ItemMeta im = item.getItemMeta();
            List<Component> currentLore = im.lore();
            if (currentLore == null || currentLore.size() < 2 || currentLore.get(1) == null) {
                p.sendMessage(Component.text("This Tome of Knowledge has invalid owner data.", NamedTextColor.RED));
                return;
            }

            List<Component> lore = new java.util.ArrayList<>(currentLore);
            if (PlainTextComponentSerializer.plainText().serialize(lore.get(1)).isEmpty()) {
                lore.set(0, LegacyComponentSerializer.legacySection()
                        .deserialize(ChatColors.color("&7Owner: &b" + p.getName())));
                lore.set(1, Component.text(p.getUniqueId().toString(), NamedTextColor.BLACK));
                im.lore(lore);
                item.setItemMeta(im);
                SoundEffect.TOME_OF_KNOWLEDGE_USE_SOUND.playFor(p);
                return;
            }

            String serializedOwner = PlainTextComponentSerializer.plainText().serialize(lore.get(1));
            if (serializedOwner.isBlank()) {
                p.sendMessage(Component.text("This Tome of Knowledge has invalid owner data.", NamedTextColor.RED));
                return;
            }

            final UUID uuid;
            try {
                uuid = UUID.fromString(serializedOwner.trim());
            } catch (IllegalArgumentException ignored) {
                p.sendMessage(Component.text("This Tome of Knowledge has invalid owner data.", NamedTextColor.RED));
                return;
            }

            if (p.getUniqueId().equals(uuid)) {
                Slimefun.getLocalization().sendMessage(p, "messages.no-tome-yourself");
                return;
            }

            ItemStack singleTome = item.clone();
            singleTome.setAmount(1);

            PlayerProfile.get(
                    p,
                    profile -> PlayerProfile.fromUUID(uuid, owner -> {
                        if (p.getGameMode() != GameMode.CREATIVE
                                && !p.getInventory().removeItem(singleTome).isEmpty()) {
                            // The player no longer owns the tome that initiated this asynchronous transfer.
                            // Do not grant research for an item that could not be committed.
                            return;
                        }

                        for (Research research : owner.getResearches()) {
                            research.unlock(p, true);
                        }
                    }));
        };
    }
}
