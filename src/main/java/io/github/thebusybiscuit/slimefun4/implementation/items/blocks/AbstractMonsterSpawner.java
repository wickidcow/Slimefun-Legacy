package io.github.thebusybiscuit.slimefun4.implementation.items.blocks;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang.Validate;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This is a parent class for the {@link BrokenSpawner} and {@link RepairedSpawner}
 * to provide some utility methods.
 *
 * @author TheBusyBiscuit
 *
 * @see BrokenSpawner
 * @see RepairedSpawner
 *
 */
public abstract class AbstractMonsterSpawner extends SlimefunItem implements DistinctiveItem {

    @ParametersAreNonnullByDefault
    AbstractMonsterSpawner(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    /**
     * This method tries to obtain an {@link EntityType} from a given {@link ItemStack}.
     * The provided {@link ItemStack} must be a {@link RepairedSpawner} item.
     *
     * @param item
     *            The {@link ItemStack} to extract the {@link EntityType} from
     *
     * @return An {@link Optional} describing the result
     */
    @Nonnull
    public Optional<EntityType> getEntityType(@Nonnull ItemStack item) {
        Validate.notNull(item, "The Item cannot be null");

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.hasBlockState()
                && blockStateMeta.getBlockState() instanceof CreatureSpawner spawner) {
            EntityType type = spawner.getSpawnedType();
            if (type != null) {
                return Optional.of(type);
            }
        }

        List<Component> lore = meta.lore();
        if (lore == null) {
            return Optional.empty();
        }

        for (Component line : lore) {
            String normalized = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!normalized.toLowerCase(Locale.ROOT).startsWith("type:") || normalized.contains("<")) {
                continue;
            }

            String serializedType = normalized
                    .substring(normalized.indexOf(':') + 1)
                    .trim()
                    .replace(' ', '_')
                    .toUpperCase(Locale.ROOT);
            try {
                return Optional.of(EntityType.valueOf(serializedType));
            } catch (IllegalArgumentException ignored) {
                // Translated or malformed legacy lore is not authoritative. The item doctor
                // will skip it unless a safe BlockState-backed entity type can be recovered.
            }
        }

        return Optional.empty();
    }

    /**
     * Updates the entity type presentation on an existing spawner item while preserving all other metadata.
     *
     * @param item
     *            The existing Slimefun spawner item
     * @param type
     *            The entity type to retain
     */
    public void refreshEntityTypePresentation(@Nonnull ItemStack item, @Nonnull EntityType type) {
        Validate.notNull(type, "The EntityType cannot be null");
        applyEntityTypePresentation(item, type);
    }

    private void applyEntityTypePresentation(@Nonnull ItemStack item, @Nullable EntityType type) {
        Validate.notNull(item, "The Item cannot be null");

        ItemMeta meta = item.getItemMeta();
        if (type != null && type.isSpawnable() && meta instanceof BlockStateMeta stateMeta) {
            BlockState state = stateMeta.getBlockState();
            if (state instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(type);
                stateMeta.setBlockState(state);
            }
        }

        List<Component> currentLore = meta.lore();
        List<Component> lore = currentLore == null ? new ArrayList<>() : new ArrayList<>(currentLore);
        String typeName = type == null ? "None" : ChatUtils.humanize(type.name());
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            Component currentLine = lore.get(i);
            String legacy = LegacyComponentSerializer.legacySection().serialize(currentLine);
            String plain = PlainTextComponentSerializer.plainText().serialize(currentLine);
            if (legacy.contains("<Type>") || legacy.contains("<type>")) {
                lore.set(i, LegacyComponentSerializer.legacySection()
                        .deserialize(legacy.replace("<Type>", typeName).replace("<type>", typeName)));
                replaced = true;
                break;
            }
            if (plain.trim().toLowerCase(Locale.ROOT).startsWith("type:")) {
                lore.set(i, Component.text("Type: " + typeName, NamedTextColor.GRAY));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lore.add(Component.text("Type: " + typeName, NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * This method returns a finished {@link ItemStack} of this {@link SlimefunItem}, modified
     * to hold and represent the given {@link EntityType}.
     *
     * @param type
     *            The {@link EntityType} to apply
     *
     * @return An {@link ItemStack} for this {@link SlimefunItem} holding that {@link EntityType}
     */
    @Nonnull
    public ItemStack getItemForEntityType(@Nullable EntityType type) {
        ItemStack item = getItem().clone();
        applyEntityTypePresentation(item, type);
        return item;
    }
    // to fix the bug of stacking two BROKEN_SPAWNER/REINFORCED_SPAWNER containing different EntityType using cargo or
    // machine
    public boolean canStack(@Nonnull ItemMeta itemMetaOne, @Nonnull ItemMeta itemMetaTwo) {
        if (itemMetaOne instanceof BlockStateMeta blockStateMeta1
                && itemMetaTwo instanceof BlockStateMeta blockStateMeta2) {
            if (blockStateMeta1.hasBlockState() && blockStateMeta2.hasBlockState()) {
                // BlockState.equals do not compare these data
                if (blockStateMeta1.getBlockState() instanceof CreatureSpawner spawner1
                        && blockStateMeta2.getBlockState() instanceof CreatureSpawner spawner2) {
                    return spawner1.getSpawnedType() == spawner2.getSpawnedType();
                }
            } else {
                return blockStateMeta1.hasBlockState() == blockStateMeta2.hasBlockState();
            }
        }
        return false;
    }
}
