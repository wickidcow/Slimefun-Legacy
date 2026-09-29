package io.github.thebusybiscuit.slimefun4.utils;

import io.github.bakedlibs.dough.common.CommonPatterns;
import io.github.bakedlibs.dough.items.ItemMetaSnapshot;
import io.github.bakedlibs.dough.skins.PlayerHead;
import io.github.bakedlibs.dough.skins.PlayerSkin;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunItemSpawnEvent;
import io.github.thebusybiscuit.slimefun4.api.exceptions.PrematureCodeException;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSpawnReason;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.ComparisonResult;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.MatchContext;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactive;
import io.github.thebusybiscuit.slimefun4.core.attributes.Soulbound;
import io.github.thebusybiscuit.slimefun4.core.debug.Debug;
import io.github.thebusybiscuit.slimefun4.core.debug.TestCase;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientPedestal;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.CapacitorTextureUpdateTask;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * This utility class holds method that are directly linked to Slimefun.
 * It provides a very crucial method for {@link ItemStack} comparison, as well as a simple method
 * to check if an {@link ItemStack} is {@link Soulbound} or not.
 *
 * @author TheBusyBiscuit
 * @author Walshy
 * @author Sfiguz7
 */
public final class SlimefunUtils {

    private static final String NO_PICKUP_KEY = "no_pickup";
    private static final String SOULBOUND_LORE = LegacyComponentSerializer.legacySection()
            .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize("&7Soulbound"));

    private SlimefunUtils() {}

    /**
     * This method quickly returns whether an {@link Item} was marked as "no_pickup" by
     * a Slimefun device.
     *
     * @param item
     *            The {@link Item} to query
     * @return Whether the {@link Item} is excluded from being picked up
     */
    public static boolean hasNoPickupFlag(@Nonnull Item item) {
        return item.getPersistentDataContainer().has(noPickupKey(), PersistentDataType.STRING);
    }

    /**
     * This will prevent the given {@link Item} from being picked up.
     * This is useful for display items which the {@link AncientPedestal} uses.
     *
     * @param item
     *            The {@link Item} to prevent from being picked up
     * @param context
     *            The context in which this {@link Item} was flagged
     */
    public static void markAsNoPickup(@Nonnull Item item, @Nonnull String context) {
        item.getPersistentDataContainer().set(noPickupKey(), PersistentDataType.STRING, context);
        /*
         * Max the pickup delay - This makes it so no Player can pick up items ever without need for an event.
         * It is also an indication used by third-party plugins to know if it's a custom item.
         * Fixes #3203
         */
        item.setPickupDelay(Short.MAX_VALUE);
    }

    /**
     * Clears Slimefun's persistent no-pickup marker from the supplied item entity.
     *
     * @param item
     *            The {@link Item} to unmark
     */
    public static void clearNoPickupFlag(@Nonnull Item item) {
        item.getPersistentDataContainer().remove(noPickupKey());
    }

    private static @Nonnull NamespacedKey noPickupKey() {
        return new NamespacedKey(Slimefun.instance(), NO_PICKUP_KEY);
    }

    /**
     * This method checks whether the given {@link ItemStack} is considered {@link Soulbound}.
     *
     * @param item
     *            The {@link ItemStack} to check for
     * @return Whether the given item is soulbound
     */
    public static boolean isSoulbound(@Nullable ItemStack item) {
        return isSoulbound(item, null);
    }

    /**
     * This method checks whether the given {@link ItemStack} is considered {@link Soulbound}.
     * If the provided item is a {@link SlimefunItem} then this method will also check that the item
     * is enabled in the provided {@link World}.
     * If the provided item is {@link Soulbound} through the {@link SlimefunItems#SOULBOUND_RUNE}, then this
     * method will also check that the {@link SlimefunItems#SOULBOUND_RUNE} is enabled in the provided {@link World}
     *
     * @param item
     *            The {@link ItemStack} to check for
     * @param world
     *            The {@link World} to check if the {@link SlimefunItem} is enabled in if applicable.
     *            If {@code null} then this will not do a world check.
     * @return Whether the given item is soulbound
     */
    public static boolean isSoulbound(@Nullable ItemStack item, @Nullable World world) {
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;

            SlimefunItem rune = SlimefunItems.SOULBOUND_RUNE.getItem();
            if (rune != null
                    && !rune.isDisabled()
                    && (world == null || !rune.isDisabledIn(world))
                    && hasSoulboundFlag(meta)) {
                return true;
            }

            SlimefunItem sfItem = SlimefunItem.getByItem(item);

            if (sfItem instanceof Soulbound) {
                if (world != null) {
                    return !sfItem.isDisabledIn(world);
                } else {
                    return !sfItem.isDisabled();
                }
            } else if (meta != null) {
                List<String> lore = legacyLore(meta);
                return lore != null && lore.contains(SOULBOUND_LORE);
            }
        }
        return false;
    }

    private static boolean hasSoulboundFlag(@Nullable ItemMeta meta) {
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey key = Slimefun.getRegistry().getSoulboundDataKey();

            return container.has(key, PersistentDataType.BYTE);
        }

        return false;
    }

    /**
     * Toggles an {@link ItemStack} to be Soulbound.<br>
     * If true is passed, this will add the {@link #SOULBOUND_LORE} and
     * add a {@link NamespacedKey} to the item so it can be quickly identified
     * by {@link #isSoulbound(ItemStack)}.<br>
     * If false is passed, this property will be removed.
     *
     * @param item
     *            The {@link ItemStack} you want to add/remove Soulbound from.
     * @param makeSoulbound
     *            If the item should be soulbound.
     *
     * @see #isSoulbound(ItemStack)
     */
    public static void setSoulbound(@Nullable ItemStack item, boolean makeSoulbound) {
        if (item == null || item.getType() == Material.AIR) {
            throw new IllegalArgumentException("A soulbound item cannot be null or air!");
        }

        boolean isSoulbound = isSoulbound(item);
        ItemMeta meta = item.getItemMeta();

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = Slimefun.getRegistry().getSoulboundDataKey();

        if (makeSoulbound && !isSoulbound) {
            container.set(key, PersistentDataType.BYTE, (byte) 1);
        }

        if (!makeSoulbound && isSoulbound) {
            container.remove(key);
        }

        List<Component> currentLore = meta.lore();
        List<Component> lore = currentLore == null ? new ArrayList<>() : new ArrayList<>(currentLore);

        if (makeSoulbound && !isSoulbound) {
            lore.add(LegacyComponentSerializer.legacySection().deserialize(SOULBOUND_LORE));
        }

        if (!makeSoulbound && isSoulbound) {
            lore.removeIf(line -> SOULBOUND_LORE.equals(LegacyComponentSerializer.legacySection().serialize(line)));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * This method checks whether the given {@link ItemStack} is radioactive.
     *
     * @param item
     *            The {@link ItemStack} to check
     *
     * @return Whether this {@link ItemStack} is radioactive or not
     */
    public static boolean isRadioactive(@Nullable ItemStack item) {
        return SlimefunItem.getByItem(item) instanceof Radioactive;
    }

    /**
     * This method returns an {@link ItemStack} for the given texture.
     * The result will be a Player Head with this texture.
     *
     * @param texture
     *            The texture for this head (base64 or hash)
     *
     * @return An {@link ItemStack} with this Head texture
     */
    public static @Nonnull ItemStack getCustomHead(@Nonnull String texture) {
        Validate.notNull(texture, "The provided texture is null");

        if (Slimefun.instance() == null) {
            throw new PrematureCodeException("You cannot instantiate a custom head before Slimefun was loaded.");
        }

        if (Slimefun.getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            // com.mojang.authlib.GameProfile does not exist in a Test Environment
            return new ItemStack(Material.PLAYER_HEAD);
        }

        String base64 = texture;

        if (CommonPatterns.HEXADECIMAL.matcher(texture).matches()) {
            base64 = Base64.getEncoder()
                    .encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
                                    + texture
                                    + "\"}}}")
                            .getBytes(StandardCharsets.UTF_8));
        }

        PlayerSkin skin = PlayerSkin.fromBase64(base64);
        return PlayerHead.getItemStack(skin);
    }

    public static boolean containsSimilarItem(Inventory inventory, ItemStack item, boolean checkLore) {
        if (inventory == null || item == null) {
            return false;
        }

        // Performance optimization
        if (!(item instanceof SlimefunItemStack)) {
            item = ItemStackWrapper.wrap(item);
        }

        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            if (isItemSimilar(stack, item, checkLore, false)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isItemSimilar(@Nullable ItemStack item, @Nullable ItemStack sfitem, boolean checkLore) {
        return isItemSimilar(item, sfitem, checkLore, true, true, true);
    }

    public static boolean isItemSimilar(
            @Nullable ItemStack item, @Nullable ItemStack sfitem, boolean checkLore, boolean checkAmount) {
        return isItemSimilar(item, sfitem, checkLore, checkAmount, true, true);
    }

    public static boolean isItemSimilar(
            @Nullable ItemStack item,
            @Nullable ItemStack sfitem,
            boolean checkLore,
            boolean checkAmount,
            boolean checkDistinctiveItem) {
        return isItemSimilar(item, sfitem, checkLore, checkAmount, checkDistinctiveItem, true);
    }

    public static boolean isItemSimilar(
            @Nullable ItemStack item,
            @Nullable ItemStack sfitem,
            boolean checkLore,
            boolean checkAmount,
            boolean checkDistinctiveItem,
            boolean checkCustomModelData) {
        ComparisonResult comparison = Slimefun.getItemStackService().matches(item, sfitem, MatchContext.GENERIC);
        if (comparison == ComparisonResult.MATCH) {
            return true;
        }

        if (comparison == ComparisonResult.NO_MATCH) {
            return false;
        }

        return isItemSimilarWithoutVirtualItems(
                item, sfitem, checkLore, checkAmount, checkDistinctiveItem, checkCustomModelData);
    }

    public static boolean isItemSimilarWithoutVirtualItems(
            @Nullable ItemStack item, @Nullable ItemStack sfitem, boolean checkLore, boolean checkAmount) {
        return isItemSimilarWithoutVirtualItems(item, sfitem, checkLore, checkAmount, true, true);
    }

    public static boolean isItemSimilarWithoutVirtualItems(
            @Nullable ItemStack item,
            @Nullable ItemStack sfitem,
            boolean checkLore,
            boolean checkAmount,
            boolean checkDistinctiveItem,
            boolean checkCustomModelData) {
        if (item == null) {
            return sfitem == null;
        } else if (sfitem == null
                || item.getType() != sfitem.getType()
                || checkAmount && item.getAmount() < sfitem.getAmount()) {
            return false;
        } else if (checkDistinctiveItem
                && sfitem instanceof SlimefunItemStack stackOne
                && item instanceof SlimefunItemStack stackTwo) {
            if (stackOne.getItemId().equals(stackTwo.getItemId())) {
                /*
                 * PR #3417
                 *
                 * Some items can't rely on just IDs matching and will implement Distinctive Item
                 * in which case we want to use the method provided to compare
                 */
                Optional<DistinctiveItem> optionalDistinctive = getDistinctiveItem(stackOne.getItemId());
                if (optionalDistinctive.isPresent()) {
                    /*
                     * Compare the actual stacks passed to this method. Comparing
                     * shared Slimefun item templates makes two distinct instances
                     * (such as different backpacks) appear identical.
                     */
                    return compareDistinctiveStacks(optionalDistinctive.get(), sfitem, item);
                }
                return true;
            }
            return false;
        } else if (item.hasItemMeta()) {
            ItemMeta itemMeta = item.getItemMeta();

            if (sfitem instanceof SlimefunItemStack sfItemStack) {
                String id = Slimefun.getItemDataService().getItemData(itemMeta).orElse(null);

                if (id != null) {
                    // to fix issue #976
                    if (id.equals(sfItemStack.getItemId())) {
                        if (checkDistinctiveItem) {
                            /*
                             * PR #3417
                             *
                             * Some items can't rely on just IDs matching and will implement Distinctive Item
                             * in which case we want to use the method provided to compare
                             */
                            Optional<DistinctiveItem> optionalDistinctive = getDistinctiveItem(id);
                            if (optionalDistinctive.isPresent()) {
                                ItemMeta sfItemMeta = sfitem.getItemMeta();
                                return optionalDistinctive.get().canStack(sfItemMeta, itemMeta);
                            }
                        }
                        return true;
                    }
                    return id.equals(sfItemStack.getItemId());
                }

                ItemMetaSnapshot meta = ((SlimefunItemStack) sfitem).getItemMetaSnapshot();
                return equalsItemMeta(itemMeta, meta, checkLore);
            } else {
                // issue # 1178 should compare sfid even if the second one isn't a ItemStackWrapper
                if (sfitem.hasItemMeta()) {
                    ItemMeta possibleSfItemMeta = sfitem.getItemMeta();
                    String id =
                            Slimefun.getItemDataService().getItemData(itemMeta).orElse(null);
                    String possibleItemId = Slimefun.getItemDataService()
                            .getItemData(possibleSfItemMeta)
                            .orElse(null);
                    // Prioritize SlimefunItem id comparison over ItemMeta comparison
                    if (id != null && possibleItemId != null) {
                        /*
                         * PR #3417
                         *
                         * Some items can't rely on just IDs matching and will implement Distinctive Item
                         * in which case we want to use the method provided to compare
                         */
                        // to fix issue #976
                        var match = id.equals(possibleItemId);
                        if (match) {
                            Optional<DistinctiveItem> optionalDistinctive = getDistinctiveItem(id);
                            if (optionalDistinctive.isPresent()) {
                                return optionalDistinctive.get().canStack(possibleSfItemMeta, itemMeta);
                            }
                        }
                        return match;
                    } else {
                        return equalsItemMeta(itemMeta, possibleSfItemMeta, checkLore, checkCustomModelData);
                    }
                } else {
                    return false;
                }
            }

        } else {
            return !sfitem.hasItemMeta();
        }
    }

    private static @Nonnull Optional<DistinctiveItem> getDistinctiveItem(@Nonnull String id) {
        SlimefunItem slimefunItem = SlimefunItem.getById(id);
        if (slimefunItem instanceof DistinctiveItem distinctiveItem) {
            return Optional.of(distinctiveItem);
        }
        return Optional.empty();
    }

    static boolean compareDistinctiveStacks(
            @Nonnull DistinctiveItem distinctiveItem, @Nonnull ItemStack first, @Nonnull ItemStack second) {
        return distinctiveItem.canStack(first.getItemMeta(), second.getItemMeta());
    }

    private static @Nullable String legacyName(@Nonnull ItemMeta meta) {
        Component name = meta.displayName();
        return name == null ? null : LegacyComponentSerializer.legacySection().serialize(name);
    }

    private static @Nullable List<String> legacyLore(@Nonnull ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null
                ? null
                : lore.stream().map(LegacyComponentSerializer.legacySection()::serialize).toList();
    }

    private static boolean equalsItemMeta(
            @Nonnull ItemMeta itemMeta, @Nonnull ItemMetaSnapshot itemMetaSnapshot, boolean checkLore) {
        return equalsItemMeta(itemMeta, itemMetaSnapshot, checkLore, false);
    }

    private static boolean equalsItemMeta(
            @Nonnull ItemMeta itemMeta,
            @Nonnull ItemMetaSnapshot itemMetaSnapshot,
            boolean checkLore,
            boolean checkCustomModelCheck) {
        Optional<String> displayName = itemMetaSnapshot.getDisplayName();
        String currentDisplayName = legacyName(itemMeta);

        if ((currentDisplayName != null) != displayName.isPresent()) {
            return false;
        } else if (currentDisplayName != null
                && displayName.isPresent()
                && !currentDisplayName.equals(displayName.get())) {
            return false;
        } else if (checkLore) {
            Optional<List<String>> itemLore = itemMetaSnapshot.getLore();
            List<String> currentLore = legacyLore(itemMeta);

            if (currentLore != null && itemLore.isPresent() && !equalsLore(currentLore, itemLore.get())) {
                return false;
            } else if ((currentLore != null) != itemLore.isPresent()) {
                return false;
            }
        }

        if (!checkCustomModelCheck) {
            return true;
        }

        // Fixes #3133: name and lore are not enough
        return matchesLegacyCustomModelData(itemMeta, itemMetaSnapshot.getCustomModelData());
    }

    private static boolean equalsItemMeta(@Nonnull ItemMeta itemMeta, @Nonnull ItemMeta sfitemMeta, boolean checkLore) {
        return equalsItemMeta(itemMeta, sfitemMeta, checkLore, true);
    }

    private static boolean equalsItemMeta(
            @Nonnull ItemMeta itemMeta,
            @Nonnull ItemMeta sfitemMeta,
            boolean checkLore,
            boolean checkCustomModelCheck) {
        String itemDisplayName = legacyName(itemMeta);
        String slimefunDisplayName = legacyName(sfitemMeta);
        if ((itemDisplayName != null) != (slimefunDisplayName != null)) {
            Debug.log(TestCase.CARGO_INPUT_TESTING, "  Comparing has display name failed");
            return false;
        } else if (itemDisplayName != null && !itemDisplayName.equals(slimefunDisplayName)) {
            Debug.log(TestCase.CARGO_INPUT_TESTING, "  Comparing display name failed");
            return false;
        } else if (checkLore) {
            List<String> itemLore = legacyLore(itemMeta);
            List<String> slimefunLore = legacyLore(sfitemMeta);

            if (itemLore != null && slimefunLore != null) {
                if (!equalsLore(itemLore, slimefunLore)) {
                    Debug.log(TestCase.CARGO_INPUT_TESTING, "  Comparing lore failed");
                    return false;
                }
            } else if ((itemLore != null) != (slimefunLore != null)) {
                Debug.log(TestCase.CARGO_INPUT_TESTING, "  Comparing has lore failed");
                return false;
            }
        }

        if (checkCustomModelCheck && !hasSameCustomModelData(itemMeta, sfitemMeta)) {
            // Fixes #3133: name and lore are not enough
            return false;
        }

        if (itemMeta instanceof PotionMeta potionMeta && sfitemMeta instanceof PotionMeta sfPotionMeta) {
            if (!potionMeta.hasBasePotionType() && !sfPotionMeta.hasBasePotionType()) {
                return true;
            }

            return potionMeta.hasBasePotionType()
                    && sfPotionMeta.hasBasePotionType()
                    && potionMeta.getBasePotionType() == sfPotionMeta.getBasePotionType();
        }

        Debug.log(TestCase.CARGO_INPUT_TESTING, "  All meta checked.");

        return true;
    }

    private static boolean matchesLegacyCustomModelData(
            @Nonnull ItemMeta itemMeta, @Nonnull OptionalInt legacyCustomModelData) {
        if (!itemMeta.hasCustomModelDataComponent()) {
            return legacyCustomModelData.isEmpty();
        }

        var component = itemMeta.getCustomModelDataComponent();
        List<Float> floats = component.getFloats();
        if (legacyCustomModelData.isEmpty()
                || floats.size() != 1
                || !component.getFlags().isEmpty()
                || !component.getStrings().isEmpty()
                || !component.getColors().isEmpty()) {
            return false;
        }

        return Float.compare(floats.get(0), legacyCustomModelData.getAsInt()) == 0;
    }

    private static boolean hasSameCustomModelData(@Nonnull ItemMeta first, @Nonnull ItemMeta second) {
        boolean firstHasComponent = first.hasCustomModelDataComponent();
        boolean secondHasComponent = second.hasCustomModelDataComponent();
        if (firstHasComponent != secondHasComponent) {
            return false;
        }
        if (!firstHasComponent) {
            return true;
        }

        var firstComponent = first.getCustomModelDataComponent();
        var secondComponent = second.getCustomModelDataComponent();
        return firstComponent.getFloats().equals(secondComponent.getFloats())
                && firstComponent.getFlags().equals(secondComponent.getFlags())
                && firstComponent.getStrings().equals(secondComponent.getStrings())
                && firstComponent.getColors().equals(secondComponent.getColors());
    }

    /**
     * This checks if the two provided lores are equal.
     * This method will ignore any lines such as the soulbound one.
     *
     * @param lore1
     *            The first lore
     * @param lore2
     *            The second lore
     *
     * @return Whether the two lores are equal
     */
    public static boolean equalsLore(@Nonnull List<String> lore1, @Nonnull List<String> lore2) {
        Validate.notNull(lore1, "Cannot compare lore that is null!");
        Validate.notNull(lore2, "Cannot compare lore that is null!");

        List<String> longerList = lore1.size() > lore2.size() ? lore1 : lore2;
        List<String> shorterList = lore1.size() > lore2.size() ? lore2 : lore1;

        int a = 0;
        int b = 0;

        for (; a < longerList.size(); a++) {
            if (isLineIgnored(longerList.get(a))) {
                continue;
            }

            while (shorterList.size() > b && isLineIgnored(shorterList.get(b))) {
                b++;
            }

            if (b >= shorterList.size()) {
                return false;
            } else if (longerList.get(a).equals(shorterList.get(b))) {
                b++;
            } else {
                return false;
            }
        }

        while (shorterList.size() > b && isLineIgnored(shorterList.get(b))) {
            b++;
        }

        return b == shorterList.size();
    }

    private static boolean isLineIgnored(@Nonnull String line) {
        return line.equals(SOULBOUND_LORE);
    }

    @Deprecated(forRemoval = true)
    public static void updateCapacitorTexture(@Nonnull Location l, int charge, int capacity) {
        Validate.notNull(l, "Cannot update a texture for null");
        Validate.isTrue(capacity > 0, "Capacity must be greater than zero!");
        updateCapacitorTexture(l, (double) charge / capacity);
    }

    public static void updateCapacitorTexture(@Nonnull Location l, double percentage) {

        Slimefun.runSyncAt(l, new CapacitorTextureUpdateTask(l, percentage));
    }

    /**
     * This checks whether the {@link Player} is able to use the given {@link ItemStack}.
     * It will always return <code>true</code> for non-Slimefun items.
     * <p>
     * If you already have an instance of {@link SlimefunItem}, please use {@link SlimefunItem#canUse(Player, boolean)}.
     *
     * @param p
     *            The {@link Player}
     * @param item
     *            The {@link ItemStack} to check
     * @param sendMessage
     *            Whether to send a message response to the {@link Player}
     *
     * @return Whether the {@link Player} is able to use that item.
     */
    public static boolean canPlayerUseItem(@Nonnull Player p, @Nullable ItemStack item, boolean sendMessage) {
        Validate.notNull(p, "The player cannot be null");

        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        if (sfItem != null) {
            return sfItem.canUse(p, sendMessage);
        } else {
            return true;
        }
    }

    /**
     * Helper method to spawn an {@link ItemStack}.
     * This method automatically calls a {@link SlimefunItemSpawnEvent} to allow
     * other plugins to catch the item being dropped.
     *
     * @param loc
     *            The {@link Location} where to drop the item
     * @param item
     *            The {@link ItemStack} to drop
     * @param reason
     *            The {@link ItemSpawnReason} why the item is being dropped
     * @param addRandomOffset
     *            Whether a random offset should be added (see {@link World#dropItemNaturally(Location, ItemStack)})
     * @param player
     *            The player that caused this {@link SlimefunItemSpawnEvent}
     *
     * @return The dropped {@link Item} (or null if the {@link SlimefunItemSpawnEvent} was cancelled)
     */
    @ParametersAreNonnullByDefault
    public static @Nullable Item spawnItem(
            Location loc, ItemStack item, ItemSpawnReason reason, boolean addRandomOffset, @Nullable Player player) {
        SlimefunItemSpawnEvent event = new SlimefunItemSpawnEvent(player, loc, item, reason);
        Slimefun.instance().getServer().getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            World world = event.getLocation().getWorld();

            if (addRandomOffset) {
                return world.dropItemNaturally(event.getLocation(), event.getItemStack());
            } else {
                return world.dropItem(event.getLocation(), event.getItemStack());
            }
        } else {
            return null;
        }
    }

    /**
     * Helper method to spawn an {@link ItemStack}.
     * This method automatically calls a {@link SlimefunItemSpawnEvent} to allow
     * other plugins to catch the item being dropped.
     *
     * @param loc
     *            The {@link Location} where to drop the item
     * @param item
     *            The {@link ItemStack} to drop
     * @param reason
     *            The {@link ItemSpawnReason} why the item is being dropped
     * @param addRandomOffset
     *            Whether a random offset should be added (see {@link World#dropItemNaturally(Location, ItemStack)})
     *
     * @return The dropped {@link Item} (or null if the {@link SlimefunItemSpawnEvent} was cancelled)
     */
    @ParametersAreNonnullByDefault
    public static @Nullable Item spawnItem(
            Location loc, ItemStack item, ItemSpawnReason reason, boolean addRandomOffset) {
        return spawnItem(loc, item, reason, addRandomOffset, null);
    }

    /**
     * Helper method to spawn an {@link ItemStack}.
     * This method automatically calls a {@link SlimefunItemSpawnEvent} to allow
     * other plugins to catch the item being dropped.
     *
     * @param loc
     *            The {@link Location} where to drop the item
     * @param item
     *            The {@link ItemStack} to drop
     * @param reason
     *            The {@link ItemSpawnReason} why the item is being dropped
     *
     * @return The dropped {@link Item} (or null if the {@link SlimefunItemSpawnEvent} was cancelled)
     */
    @ParametersAreNonnullByDefault
    public static @Nullable Item spawnItem(Location loc, ItemStack item, ItemSpawnReason reason) {
        return spawnItem(loc, item, reason, false);
    }

    /**
     * Helper method to check if an Inventory is empty (has no items in "storage").
     * If the MC version is 1.16 or above
     * this will call {@link Inventory#isEmpty()} (Which calls MC code resulting in a faster method).
     *
     * @param inventory
     *            The {@link Inventory} to check.
     *
     * @return True if the inventory is empty and false otherwise
     */
    public static boolean isInventoryEmpty(@Nonnull Inventory inventory) {
        if (Slimefun.getMinecraftVersion().isAtLeast(MinecraftVersion.MINECRAFT_1_16)) {
            return inventory.isEmpty();
        } else {
            for (ItemStack is : inventory.getStorageContents()) {
                if (is != null && !is.getType().isAir()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Check whether the item is a kind of Dust or not.
     *
     * @param item The item need to check.
     * @return Is the item a kind of Dust
     */
    public static boolean isDust(@Nonnull ItemStack item) {
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        return sfItem != null && sfItem.getId().endsWith("_DUST");
    }
}
