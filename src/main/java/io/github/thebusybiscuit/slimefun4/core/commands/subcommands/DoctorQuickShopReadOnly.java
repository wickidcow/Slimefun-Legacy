package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Manual read-only QuickShop-Hikari diagnosis, deliberately independent of
 * any compile-time optional QuickShop dependency. This never saves or changes
 * a shop, its item reference, its world chest, owner, price or transaction.
 *
 * <p>It uses only public QuickShop API methods and a region-owned single chest.
 * A missing/changed API, a different Slimefun identity or a racing stock
 * update is an explicit refusal, never a candidate for repair.
 */
final class DoctorQuickShopReadOnly {
    private static final int MAX_STACK_BYTES = 256 * 1024;
    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");

    private DoctorQuickShopReadOnly() {}

    static void run(Player player, String slotText) {
        if (!player.isOp()) {
            send(player, "&cQuickShop inspection requires an operator.");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(slotText);
            if (slot < 0 || slot >= 27) {
                throw new NumberFormatException("Outside single chest");
            }
        } catch (NumberFormatException exception) {
            send(player, "&cSpecify a single-chest stock slot from 0 to 26.");
            return;
        }
        Block aimed = player.getTargetBlockExact(6);
        if (aimed == null || aimed.getType().isAir()) {
            send(player, "&cLook at a loaded single-chest QuickShop within six blocks.");
            return;
        }
        Location location = aimed.getLocation();
        Slimefun.getSchedulerService().runAt(location, () -> {
            String message;
            try {
                if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    message = "&cTarget chunk unloaded. No shop data inspected.";
                } else {
                    BlockState state = location.getBlock().getState();
                    if (!(state instanceof Chest chest)
                            || !(state.getBlockData() instanceof org.bukkit.block.data.type.Chest data)
                            || data.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
                        message = "&cOnly loaded, single physical chests are supported. No shop data changed.";
                    } else {
                        message = inspectShop(location, chest, slot);
                    }
                }
            } catch (IOException | ReflectiveOperationException | LinkageError | RuntimeException exception) {
                message = "&cQuickShop item comparison unavailable or unsafe. No shop or chest data changed.";
            }
            String result = message;
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (player.isOnline()) {
                    send(player, result);
                }
            });
        });
    }

    /**
     * Read the public runtime QuickShop shop record only; no database lookup,
     * saveToInfoStorage, setItem, update, trade or inventory wrapper mutation.
     * The supplied location is the owning physical chest's block location.
     */
    private static String inspectShop(Location location, Chest chest, int slot)
            throws IOException, ReflectiveOperationException {
        Plugin quickShop = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(plugin -> plugin.isEnabled() && plugin.getName().toLowerCase(java.util.Locale.ROOT)
                        .contains("quickshop"))
                .findFirst()
                .orElseThrow(() -> new IOException("QuickShop is not installed or enabled"));
        ClassLoader loader = quickShop.getClass().getClassLoader();
        Class<?> apiType = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, loader);
        Class<?> managerType = Class.forName("com.ghostchu.quickshop.api.shop.ShopManager", true, loader);
        Class<?> shopType = Class.forName("com.ghostchu.quickshop.api.shop.Shop", true, loader);
        Class<?> userType = Class.forName("com.ghostchu.quickshop.api.obj.QUser", true, loader);
        Object api = apiType.getMethod("getInstance").invoke(null);
        Object manager = apiType.getMethod("getShopManager").invoke(api);
        Object shop = managerType.getMethod("getShop", Location.class).invoke(manager, location);
        if (shop == null) {
            throw new IOException("No directly attached shop at selected chest");
        }
        Location shopLocation = (Location) shopType.getMethod("bukkitLocation").invoke(shop);
        if (shopLocation == null
                || !Objects.equals(shopLocation.getWorld(), location.getWorld())
                || shopLocation.getBlockX() != location.getBlockX()
                || shopLocation.getBlockY() != location.getBlockY()
                || shopLocation.getBlockZ() != location.getBlockZ()) {
            throw new IOException("Shop location did not match the selected physical chest");
        }

        Method saleGetter = shopType.getMethod("getItem");
        Method priceGetter = shopType.getMethod("getPrice");
        Method ownerGetter = shopType.getMethod("getOwner");
        Method matches = shopType.getMethod("matches", ItemStack.class);
        Method serializeOwner = userType.getMethod("serialize");
        ItemStack saleItem = (ItemStack) saleGetter.invoke(shop);
        if (saleItem == null || saleItem.getType().isAir()) {
            throw new IOException("Shop has no saved sale-item reference");
        }
        byte[] saleBytes = saleItem.serializeAsBytes();
        Object owner = ownerGetter.invoke(shop);
        if (owner == null) {
            throw new IOException("Missing shop owner");
        }
        String ownerKey = (String) serializeOwner.invoke(owner);
        if (ownerKey == null) {
            throw new IOException("Missing owner identity");
        }
        double price = ((Number) priceGetter.invoke(shop)).doubleValue();
        if (!Double.isFinite(price)) {
            throw new IOException("Invalid price");
        }
        var inventory = chest.getBlockInventory();
        ItemStack stock = inventory.getItem(slot);
        byte[] stockBytes = stock == null ? null : stock.serializeAsBytes();
        SlimefunItem registered = SlimefunItem.getByItem(saleItem);
        ItemStack current = registered == null ? null : registered.getItem();
        if (registered == null || !Objects.equals(registered.getId(), itemId(saleItem))) {
            throw new IOException("Unknown or unresolved Slimefun sale reference");
        }
        byte[] registeredBytes = current.serializeAsBytes();
        Result comparison = compare(saleItem, stock, current, candidate -> {
            try {
                return (Boolean) matches.invoke(shop, candidate);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("QuickShop matcher unavailable", exception);
            }
        });
        // The matcher is third-party code; reject any concurrent shop, stock
        // or registered-template change, including quantity and private fields.
        ItemStack stockAfter = inventory.getItem(slot);
        Object ownerAfter = ownerGetter.invoke(shop);
        if (!Arrays.equals(saleBytes, ((ItemStack) saleGetter.invoke(shop)).serializeAsBytes())
                || !Arrays.equals(stockBytes, stockAfter == null ? null : stockAfter.serializeAsBytes())
                || !Arrays.equals(registeredBytes, current.serializeAsBytes())
                || !Objects.equals(ownerKey, ownerAfter == null ? null : serializeOwner.invoke(ownerAfter))
                || Double.doubleToLongBits(price)
                        != Double.doubleToLongBits(((Number) priceGetter.invoke(shop)).doubleValue())) {
            throw new IOException("QuickShop or chest changed while inspecting");
        }
        return "&6QuickShop stock slot &e" + slot + " &8| &7Saved sale-item vs current: &e"
                + comparison.saleDifference() + " &8| &7Chest stock vs current: &e"
                + (comparison.stockPresent() ? comparison.stockDifference() : "EMPTY")
                + " &8| &7Sale item vs stock (native): &e"
                + (comparison.stockPresent() ? yesNo(comparison.saleMatchesStock()) : "NO_STOCK")
                + " &8| &7QuickShop matches fresh: &e" + yesNo(comparison.matcherAcceptsFresh())
                + " &8| &7QuickShop matches stock: &e"
                + (comparison.stockPresent() ? yesNo(comparison.matcherAcceptsStock()) : "NO_STOCK")
                + " &8| &cREAD-ONLY; no shop or stock changed, no repair authorized.";
    }

    /**
     * Tests three independently captured native items, with a provided matcher
     * accepting ONLY detached copies. Different Slimefun IDs/materials or
     * differing typed metadata are never silently normalized.
     */
    static Result compare(
            ItemStack saleItem, ItemStack stock, ItemStack registeredTemplate,
            Predicate<ItemStack> configuredShopMatcher) throws IOException {
        if (configuredShopMatcher == null) {
            throw new IOException("Missing shop matcher");
        }
        byte[] saleBytes = nativeBytes(saleItem);
        byte[] referenceBytes = nativeBytes(registeredTemplate);
        byte[] stockBytes = stock == null || stock.getType().isAir() ? null : nativeBytes(stock);
        ItemStack saleNative = ItemStack.deserializeBytes(saleBytes);
        ItemStack referenceNative = ItemStack.deserializeBytes(referenceBytes);
        ItemStack stockNative = stockBytes == null ? null : ItemStack.deserializeBytes(stockBytes);
        if (!sameIdentity(saleNative, referenceNative)
                || stockNative != null && !sameIdentity(stockNative, referenceNative)) {
            throw new IOException("Different or unknown Slimefun item IDs/materials");
        }
        try {
            var saved = classify(saleNative, referenceNative);
            var stored = stockNative == null ? null : classify(stockNative, referenceNative);
            boolean matchCurrent = configuredShopMatcher.test(ItemStack.deserializeBytes(referenceBytes));
            boolean matchStock = stockNative != null
                    && configuredShopMatcher.test(ItemStack.deserializeBytes(stockBytes));
            boolean sameSaleStock = stockNative != null
                    && DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(saleNative, stockNative);
            if (!Arrays.equals(saleBytes, saleItem.serializeAsBytes())
                    || !Arrays.equals(referenceBytes, registeredTemplate.serializeAsBytes())
                    || stockBytes != null && !Arrays.equals(stockBytes, stock.serializeAsBytes())) {
                throw new IOException("Item source changed during QuickShop read-only classification");
            }
            return new Result(saved, stored, stockNative != null, sameSaleStock, matchCurrent, matchStock);
        } catch (RuntimeException exception) {
            throw new IOException("QuickShop comparison could not complete safely", exception);
        }
    }

    private static DoctorHistoricalStackEvidenceAudit.Difference classify(ItemStack old, ItemStack current) {
        if (DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(old, current)) {
            return DoctorHistoricalStackEvidenceAudit.Difference.CURRENT;
        }
        var presentation = DoctorIdentityCommand.inspectHistoricalPresentation(old, current);
        if (!presentation.nonPresentationSame()) {
            return DoctorHistoricalStackEvidenceAudit.Difference.FUNCTIONAL_METADATA;
        }
        var plain = DoctorIdentityCommand.inspectPlainPresentation(old, current);
        if (plain.nameSame() && !plain.loreSame() && DoctorIdentityCommand.sameNonblankPlainLore(old, current)) {
            return DoctorHistoricalStackEvidenceAudit.Difference.BLANK_LORE_LAYOUT;
        }
        if (!presentation.plainTextSame()) {
            return DoctorHistoricalStackEvidenceAudit.Difference.MEANINGFUL_TEXT;
        }
        return DoctorHistoricalStackRepair.prepare(old, current, current) == null
                ? DoctorHistoricalStackEvidenceAudit.Difference.UNSAFE
                : DoctorHistoricalStackEvidenceAudit.Difference.STYLE_ONLY_CANDIDATE;
    }

    private static boolean sameIdentity(ItemStack a, ItemStack b) {
        return a != null && b != null && a.getType() == b.getType()
                && itemId(a) != null && Objects.equals(itemId(a), itemId(b));
    }

    private static String itemId(ItemStack stack) {
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(SLIMEFUN_ID, PersistentDataType.STRING);
    }

    private static byte[] nativeBytes(ItemStack item) throws IOException {
        if (item == null || item.getType().isAir()) {
            throw new IOException("Missing source item");
        }
        try {
            byte[] bytes = item.serializeAsBytes();
            if (bytes.length == 0 || bytes.length > MAX_STACK_BYTES) {
                throw new IOException("Source item exceeds safe native byte budget");
            }
            ItemStack copy = ItemStack.deserializeBytes(bytes);
            if (copy.getType() != item.getType()
                    || copy.getAmount() != item.getAmount()
                    || !Arrays.equals(bytes, copy.serializeAsBytes())) {
                throw new IOException("Item native serialization is not stable");
            }
            return bytes;
        } catch (RuntimeException exception) {
            throw new IOException("Native source item unreadable", exception);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static void send(CommandSender sender, String text) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }

    record Result(
            DoctorHistoricalStackEvidenceAudit.Difference saleDifference,
            DoctorHistoricalStackEvidenceAudit.Difference stockDifference,
            boolean stockPresent,
            boolean saleMatchesStock,
            boolean matcherAcceptsFresh,
            boolean matcherAcceptsStock) {}
}
