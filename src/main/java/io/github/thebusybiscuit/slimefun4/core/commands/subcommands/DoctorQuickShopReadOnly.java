package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
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
    // Hikari stores the shop's internal item as Bukkit YAML; this is not a
    // downloaded SQL row. Bound parsing before asking Bukkit to deserialize it.
    private static final int MAX_CACHED_YAML_CHARS = 1024 * 1024;
    private static final NamespacedKey SLIMEFUN_ID = new NamespacedKey("slimefun", "slimefun_item");

    private DoctorQuickShopReadOnly() {}

    static void run(Player player, String slotText) {
        run(player, slotText, null);
    }

    static void export(Slimefun plugin, Player player, String slotText) {
        run(player, slotText, plugin.getDataFolder().toPath());
    }

    private static void run(Player player, String slotText, Path privateEvidenceFolder) {
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
                        message = inspectShop(
                                location, chest, slot, privateEvidenceFolder, player.getUniqueId());
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
     * Read only the public runtime QuickShop view and a detached prospective
     * saveToInfoStorage projection. Neither authenticates a saved SQL row.
     * No database lookup, setter, update, trade or inventory wrapper mutation.
     * The supplied location is the owning physical chest's block location.
     */
    private static String inspectShop(
            Location location, Chest chest, int slot, Path privateEvidenceFolder, UUID operator)
            throws IOException, ReflectiveOperationException {
        // Resolve the actual Hikari core by its upstream plugin.yml name,
        // never an unrelated QuickShop companion/addon with a similar name.
        Plugin quickShop = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        if (quickShop == null || !quickShop.isEnabled()) {
            throw new IOException("QuickShop-Hikari is not installed or enabled");
        }
        ClassLoader loader = quickShop.getClass().getClassLoader();
        Class<?> apiType = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, loader);
        Class<?> managerType = Class.forName("com.ghostchu.quickshop.api.shop.ShopManager", true, loader);
        Class<?> shopType = Class.forName("com.ghostchu.quickshop.api.shop.Shop", true, loader);
        Class<?> storageType = Class.forName("com.ghostchu.quickshop.api.shop.ShopInfoStorage", true, loader);
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
        Method cachedGetter = shopType.getMethod("itemSerializeString");
        Method prospectiveGetter = shopType.getMethod("saveToInfoStorage");
        Method prospectiveItemGetter = storageType.getMethod("getItem");
        Method prospectiveOwnerGetter = storageType.getMethod("getOwner");
        Method prospectivePriceGetter = storageType.getMethod("getPrice");
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
        // Official Hikari ContainerShop.saveToInfoStorage() constructs an
        // in-memory export DTO; it does NOT save it to SQL. Never print or
        // persist the opaque encoded item, owner or price from this projection.
        ProspectiveStorage prospective = readProspective(
                shop, prospectiveGetter, prospectiveItemGetter,
                prospectiveOwnerGetter, prospectivePriceGetter, ownerKey, price);
        var inventory = chest.getBlockInventory();
        ItemStack stock = inventory.getItem(slot);
        byte[] stockBytes = stock == null ? null : stock.serializeAsBytes();
        SlimefunItem registered = SlimefunItem.getByItem(saleItem);
        ItemStack current = registered == null ? null : registered.getItem();
        if (registered == null || !Objects.equals(registered.getId(), itemId(saleItem))) {
            throw new IOException("Unknown or unresolved Slimefun sale reference");
        }
        byte[] registeredBytes = current.serializeAsBytes();
        // getItem() may be altered by a ShopItemEvent(RETRIEVE) listener.
        // The cached Bukkit YAML comes from QuickShop's internal stored
        // item. Neither representation authenticates a persisted SQL row.
        String cachedYaml = (String) cachedGetter.invoke(shop);
        CachedComparison cache = compareCached(cachedYaml, saleItem, stock, current);
        Result comparison = compare(saleItem, stock, current, candidate -> {
            try {
                return (Boolean) matches.invoke(shop, candidate);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("QuickShop matcher unavailable", exception);
            }
        });
        // Refuse any intervening cached-record change BEFORE writing private
        // receipts. A later race is checked again after disk I/O.
        if (!Objects.equals(cachedYaml, cachedGetter.invoke(shop))) {
            throw new IOException("QuickShop cached sale item changed during read-only comparison");
        }
        // Only private native evidence receipts are written; never shop,
        // chest, database, owner or price. The cached source is captured
        // separately from the event-processed getItem() view. No stock is
        // required for unlimited AdminShops.
        ShopEvidence receipts = privateEvidenceFolder == null
                ? null
                : captureEvidence(privateEvidenceFolder, operator, saleItem, stock, current);
        ShopEvidence cachedReceipts = privateEvidenceFolder == null
                ? null
                : captureEvidence(privateEvidenceFolder, operator, cache.item(), null, current);
        // The matcher is third-party code; reject any concurrent shop, stock
        // or registered-template change, including quantity and private fields.
        ItemStack stockAfter = inventory.getItem(slot);
        Object ownerAfter = ownerGetter.invoke(shop);
        ProspectiveStorage prospectiveAfter = readProspective(
                shop, prospectiveGetter, prospectiveItemGetter,
                prospectiveOwnerGetter, prospectivePriceGetter, ownerKey, price);
        if (!prospective.equals(prospectiveAfter)
                || !Arrays.equals(saleBytes, ((ItemStack) saleGetter.invoke(shop)).serializeAsBytes())
                || !Arrays.equals(stockBytes, stockAfter == null ? null : stockAfter.serializeAsBytes())
                || !Arrays.equals(registeredBytes, current.serializeAsBytes())
                || !Objects.equals(ownerKey, ownerAfter == null ? null : serializeOwner.invoke(ownerAfter))
                || !Objects.equals(cachedYaml, cachedGetter.invoke(shop))
                || Double.doubleToLongBits(price)
                        != Double.doubleToLongBits(((Number) priceGetter.invoke(shop)).doubleValue())) {
            throw new IOException("QuickShop or chest changed while inspecting");
        }
        String report = "&6QuickShop stock slot &e" + slot + " &8| &7Runtime sale view vs current: &e"
                + comparison.saleDifference() + " &8| &7Internal cached item vs current: &e"
                + cache.cachedDifference() + " &8| &7Cached vs runtime view (ignore count): &e"
                + yesNo(cache.cachedMatchesView()) + " &8| &7Cached vs stock (ignore count): &e"
                + (comparison.stockPresent() ? yesNo(cache.cachedMatchesStock()) : "NO_STOCK") + " &8| &7Chest stock vs current: &e"
                + (comparison.stockPresent() ? comparison.stockDifference() : "EMPTY")
                + " &8| &7Typed sale/current: &e" + comparison.saleTypedCurrent()
                + " &8| &7Typed cached/current: &e" + cache.cachedTypedCurrent()
                + " &8| &7Typed cached/runtime: &e" + cache.cachedTypedRuntime()
                + " &8| &7Typed stock/current: &e"
                + (comparison.stockPresent() ? comparison.stockTypedCurrent() : "NO_STOCK")
                + " &8| &7Typed sale/stock: &e"
                + (comparison.stockPresent() ? comparison.saleTypedStock() : "NO_STOCK")
                + " &8| &7Sale item vs stock (native): &e"
                + (comparison.stockPresent() ? yesNo(comparison.saleMatchesStock()) : "NO_STOCK")
                + " &8| &7QuickShop matches fresh: &e" + yesNo(comparison.matcherAcceptsFresh())
                + " &8| &7QuickShop matches stock: &e"
                + (comparison.stockPresent() ? yesNo(comparison.matcherAcceptsStock()) : "NO_STOCK")
                + " &8| &7Prospective encoded save item/owner/price: &eSTABLE"
                + " &8| &cNOT an authenticated SQL row. READ-ONLY; no shop or stock changed, no repair authorized.";
        if (receipts != null) {
            report += " &8| &aPRIVATE evidence: &7sale &e" + receipts.sale()
                    + " &7current &e" + receipts.current()
                    + (receipts.stock() == null
                            ? " &7stock EMPTY"
                            : " &7stock &e" + receipts.stock()
                                    + " &7stock-reference &e" + receipts.stockReference());
            report += " &8| &7cached-item &e" + cachedReceipts.sale()
                    + " &7cached-reference &e" + cachedReceipts.current();
            report += " &8| &cPRIVATE item receipts; back up separately. Cached YAML is not the DB row.";
        }
        return report;
    }


    /**
     * Calls only Hikari's public in-memory export DTO builder. Its encoded
     * item is deliberately treated as opaque: neither its bytes nor an owner
     * identifier are logged or copied into a public report. A projection is
     * NOT an independently verified persisted QuickShop database record.
     */
    private static ProspectiveStorage readProspective(
            Object shop, Method export, Method item, Method owner, Method price,
            String expectedOwner, double expectedPrice)
            throws IOException, ReflectiveOperationException {
        Object record = export.invoke(shop);
        if (record == null) {
            throw new IOException("QuickShop did not provide a prospective save record");
        }
        return checkedProspective(
                (String) item.invoke(record),
                (String) owner.invoke(record),
                ((Number) price.invoke(record)).doubleValue(),
                expectedOwner, expectedPrice);
    }

    static ProspectiveStorage checkedProspective(
            String encodedItem, String storedOwner, double storedPrice,
            String actualOwner, double actualPrice) throws IOException {
        if (encodedItem == null || encodedItem.isBlank()
                || encodedItem.length() > MAX_CACHED_YAML_CHARS) {
            throw new IOException("QuickShop prospective saved item missing or too large");
        }
        if (storedOwner == null || storedOwner.isBlank()
                || !Objects.equals(storedOwner, actualOwner)) {
            throw new IOException("QuickShop prospective owner disagrees with live shop");
        }
        if (!Double.isFinite(storedPrice) || !Double.isFinite(actualPrice)
                || Double.doubleToLongBits(storedPrice) != Double.doubleToLongBits(actualPrice)) {
            throw new IOException("QuickShop prospective price disagrees with live shop");
        }
        return new ProspectiveStorage(encodedItem, storedOwner, Double.doubleToLongBits(storedPrice));
    }

    record ProspectiveStorage(String encodedItem, String owner, long priceBits) {}

    /**
     * Export the exact historical QuickShop sale template and, when present,
     * selected physical stock independently against the current registration.
     * Only the private operator-owned Doctor recovery files may be created;
     * a shop/DB/stock/price/owner is NEVER written.
     */
    static ShopEvidence captureEvidence(
            Path privateFolder, UUID owner, ItemStack sale, ItemStack stock, ItemStack reference)
            throws IOException {
        if (privateFolder == null || owner == null) {
            throw new IOException("Missing operator-scoped private recovery destination");
        }
        byte[] saleBytes = nativeBytes(sale);
        byte[] referenceBytes = nativeBytes(reference);
        byte[] stockBytes = stock == null || stock.getType().isAir() ? null : nativeBytes(stock);
        ItemStack saleNative = ItemStack.deserializeBytes(saleBytes);
        ItemStack referenceNative = ItemStack.deserializeBytes(referenceBytes);
        ItemStack stockNative = stockBytes == null ? null : ItemStack.deserializeBytes(stockBytes);
        if (!sameIdentity(saleNative, referenceNative)
                || stockNative != null && !sameIdentity(stockNative, referenceNative)) {
            throw new IOException("Private shop evidence must have one exact registered Slimefun identity");
        }

        var savedSale = DoctorIdentityEvidenceExport.capture(privateFolder, owner, sale, reference);
        DoctorIdentityEvidenceExport.Evidence savedStock = stockNative == null
                ? null
                : DoctorIdentityEvidenceExport.capture(privateFolder, owner, stock, reference);
        if (!Arrays.equals(saleBytes, sale.serializeAsBytes())
                || !Arrays.equals(referenceBytes, reference.serializeAsBytes())
                || stockBytes != null && !Arrays.equals(stockBytes, stock.serializeAsBytes())) {
            throw new IOException("Shop evidence changed during private capture; do not trust these receipts");
        }
        return new ShopEvidence(
                savedSale.mainReceipt(),
                savedSale.offhandReceipt(),
                savedStock == null ? null : savedStock.mainReceipt(),
                savedStock == null ? null : savedStock.offhandReceipt());
    }

    record ShopEvidence(UUID sale, UUID current, UUID stock, UUID stockReference) {}

    /**
     * Parse QuickShop's cached internal-item YAML as a distinct, read-only
     * source from the ShopItemEvent(RETRIEVE) runtime view. This is a cache,
     * NOT a snapshot of the durable SQL record. Refuse unknown IDs, malformed
     * YAML, unexpected additional root properties and oversized strings.
     *
     * <p>Only detached copies are compared; no legacy item is normalized.
     */
    static CachedComparison compareCached(
            String yaml, ItemStack runtimeView, ItemStack stock, ItemStack registeredTemplate) throws IOException {
        if (yaml == null || yaml.isBlank() || yaml.length() > MAX_CACHED_YAML_CHARS) {
            throw new IOException("Missing or oversized QuickShop cached item serialization");
        }
        ItemStack cachedItem;
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yaml);
            if (config.getKeys(false).size() != 1 || !config.contains("item")) {
                throw new IOException("QuickShop cache must contain only the serialized item");
            }
            cachedItem = config.getItemStack("item");
        } catch (InvalidConfigurationException | RuntimeException exception) {
            throw new IOException("QuickShop cache item could not be safely decoded", exception);
        }
        byte[] cachedBytes = nativeBytes(cachedItem);
        byte[] viewBytes = nativeBytes(runtimeView);
        byte[] currentBytes = nativeBytes(registeredTemplate);
        byte[] stockBytes = stock == null || stock.getType().isAir() ? null : nativeBytes(stock);
        ItemStack cached = ItemStack.deserializeBytes(cachedBytes);
        ItemStack view = ItemStack.deserializeBytes(viewBytes);
        ItemStack current = ItemStack.deserializeBytes(currentBytes);
        ItemStack stocked = stockBytes == null ? null : ItemStack.deserializeBytes(stockBytes);
        if (!sameIdentity(cached, current) || !sameIdentity(cached, view)
                || stocked != null && !sameIdentity(cached, stocked)) {
            throw new IOException("Cached QuickShop Slimefun ID/material disagrees with the live shop or stock");
        }
        var difference = classify(cached, current);
        boolean matchesView = DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(cached, view);
        boolean matchesStock = stocked != null
                && DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(cached, stocked);
        if (!Arrays.equals(viewBytes, runtimeView.serializeAsBytes())
                || !Arrays.equals(currentBytes, registeredTemplate.serializeAsBytes())
                || stockBytes != null && !Arrays.equals(stockBytes, stock.serializeAsBytes())) {
            throw new IOException("QuickShop cache source comparison raced an item change");
        }
        // Detached read-only typed evidence; only enum categories leave this method.
        // There is still no authenticated SQL read or shop update.
        return new CachedComparison(
                cached, difference, matchesView, matchesStock,
                DoctorNativeNbtEvidence.compare(cached, current),
                DoctorNativeNbtEvidence.compare(cached, view),
                stocked == null ? null : DoctorNativeNbtEvidence.compare(cached, stocked));
    }

    record CachedComparison(
            ItemStack item,
            DoctorHistoricalStackEvidenceAudit.Difference cachedDifference,
            boolean cachedMatchesView,
            boolean cachedMatchesStock,
            DoctorNativeNbtEvidence.Verdict cachedTypedCurrent,
            DoctorNativeNbtEvidence.Verdict cachedTypedRuntime,
            DoctorNativeNbtEvidence.Verdict cachedTypedStock) {}

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
            // Hikari's matcher is optional third-party code. The only objects
            // supplied to it are detached copies; a matcher that changes even
            // those test inputs cannot provide a stable diagnostic verdict.
            boolean matchCurrent = matchesWithoutMutation(configuredShopMatcher, referenceBytes);
            boolean matchStock = stockNative != null
                    && matchesWithoutMutation(configuredShopMatcher, stockBytes);
            boolean sameSaleStock = stockNative != null
                    && DoctorHistoricalStackRepair.sameNativeBytesIgnoringAmount(saleNative, stockNative);
            if (!Arrays.equals(saleBytes, saleItem.serializeAsBytes())
                    || !Arrays.equals(referenceBytes, registeredTemplate.serializeAsBytes())
                    || stockBytes != null && !Arrays.equals(stockBytes, stock.serializeAsBytes())) {
                throw new IOException("Item source changed during QuickShop read-only classification");
            }
            return new Result(
                    saved, stored, stockNative != null, sameSaleStock, matchCurrent, matchStock,
                    DoctorNativeNbtEvidence.compare(saleNative, referenceNative),
                    stockNative == null ? null : DoctorNativeNbtEvidence.compare(stockNative, referenceNative),
                    stockNative == null ? null : DoctorNativeNbtEvidence.compare(saleNative, stockNative));
        } catch (RuntimeException exception) {
            throw new IOException("QuickShop comparison could not complete safely", exception);
        }
    }

    private static boolean matchesWithoutMutation(Predicate<ItemStack> matcher, byte[] exact)
            throws IOException {
        ItemStack candidate = ItemStack.deserializeBytes(exact);
        boolean verdict = matcher.test(candidate);
        if (!Arrays.equals(exact, candidate.serializeAsBytes())) {
            throw new IOException("QuickShop matcher modified its detached comparison item");
        }
        return verdict;
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
            // The same physical historical item can serialize an unordered
            // native compound with a different key order after an independent
            // read. Refuse true typed drift, but don't mislabel harmless
            // compound ordering as a modified QuickShop source. This is only
            // an operator-initiated READ, never permission to save that copy.
            byte[] detachedBytes = copy.serializeAsBytes();
            boolean exact = Arrays.equals(bytes, detachedBytes);
            boolean sameTyped = !exact
                    && DoctorNativeNbtEvidence.compareNativeBytes(bytes, detachedBytes)
                            == DoctorNativeNbtEvidence.Verdict.SAME_TYPED_NBT;
            if (copy.getType() != item.getType()
                    || copy.getAmount() != item.getAmount()
                    || (!exact
                            && (!sameTyped
                                    || !item.isSimilar(copy)
                                    || !Objects.equals(item.getItemMeta(), copy.getItemMeta())))) {
                throw new IOException("QuickShop native source changed typed identity on read");
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
            boolean matcherAcceptsStock,
            DoctorNativeNbtEvidence.Verdict saleTypedCurrent,
            DoctorNativeNbtEvidence.Verdict stockTypedCurrent,
            DoctorNativeNbtEvidence.Verdict saleTypedStock) {}
}
