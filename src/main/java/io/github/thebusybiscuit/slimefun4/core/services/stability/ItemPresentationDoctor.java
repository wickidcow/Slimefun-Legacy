package io.github.thebusybiscuit.slimefun4.core.services.stability;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.LimitedUseItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.AbstractMonsterSpawner;
import io.github.thebusybiscuit.slimefun4.implementation.items.magical.KnowledgeTome;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Repairs only the visible name and lore of registered Slimefun items. */
public final class ItemPresentationDoctor {
    private static final int MAX_CONTAINER_DEPTH = 4;
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final String SOULBOUND_LORE = legacy("&7Soulbound");
    private static final String BACKPACK_OWNER_PREFIX = legacy("&7Owner: ");
    private static final String BACKPACK_ID_PREFIX = legacy("&7ID: ");
    private static final Pattern LEGACY_BACKPACK_IDENTITY =
            Pattern.compile("(?i)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})#([0-9]+)");
    private static final Set<String> SAFE_STATIC_ADDON_LORE_IDS =
            Set.of("ELECTRIC_DUST_FABRICATOR", "REINFORCED_FLUFFY_WRENCH");

    private final ItemModelRepairExecutor itemModelInspector = new ItemModelRepairExecutor(false);

    public boolean repairInventory(@Nonnull Inventory inventory, boolean repair, @Nonnull ItemDoctorReport report) {
        return repairInventory(inventory, repair, report, 0);
    }

    private boolean repairInventory(
            @Nonnull Inventory inventory, boolean repair, @Nonnull ItemDoctorReport report, int depth) {
        boolean changed = false;
        report.inventoryScanned();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (inspectItem(item, repair, report, depth)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    public boolean inspectItem(@Nullable ItemStack item, boolean repair, @Nonnull ItemDoctorReport report) {
        return inspectItem(item, repair, report, 0);
    }

    private boolean inspectItem(@Nullable ItemStack item, boolean repair, @Nonnull ItemDoctorReport report, int depth) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        // Slimefun Legacy 4.1.18 Item Doctor failure isolation.
        report.stackScanned();
        boolean changed = false;
        try {
            changed = inspectSlimefunPresentation(item, repair, report);
        } catch (RuntimeException | LinkageError ex) {
            report.failure();
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Item doctor skipped a failing stack [" + describeStack(item)
                                    + "]. The scan will continue.",
                            ex);
        }
        if (depth < MAX_CONTAINER_DEPTH) {
            try {
                changed |= inspectNestedItems(item, repair, report, depth + 1);
            } catch (RuntimeException | LinkageError ex) {
                report.failure();
                Slimefun.logger()
                        .log(
                                Level.WARNING,
                                "Item doctor could not inspect a nested container [" + describeStack(item)
                                        + "]. The scan will continue.",
                                ex);
            }
        }
        return changed;
    }

    private static String describeStack(ItemStack item) {
        String itemId = "<none>";
        try {
            itemId = Slimefun.getItemDataService().getItemData(item).orElse("<none>");
        } catch (RuntimeException | LinkageError ignored) {
            itemId = "<unreadable>";
        }
        return "type=" + item.getType() + ", slimefunId=" + itemId;
    }

    private boolean inspectSlimefunPresentation(ItemStack item, boolean repair, ItemDoctorReport report) {
        Optional<String> storedId = Slimefun.getItemDataService().getItemData(item);
        if (storedId.isEmpty()) {
            return false;
        }

        report.slimefunStackFound();
        String itemId = storedId.get();
        if (!repair) {
            itemModelInspector.inspectCandidate(item, itemId, report);
        }
        SlimefunItem sfItem = SlimefunItem.getById(itemId);
        if (sfItem == null
                && Slimefun.getRegistry().getLegacySlimefunItemIdTarget(itemId).isPresent()) {
            report.legacyMigrationCandidateFound(itemId);
        }

        LegacyItemSchemaProbeService.Session schemaProbes = report.getSchemaProbeSession();
        if (schemaProbes != null) {
            schemaProbes.inspect(item, itemId, report);
        }

        ItemMeta currentMeta = item.getItemMeta();
        String currentName = legacyName(currentMeta);
        List<String> currentLore = legacyLore(currentMeta);
        boolean hasCjkName = currentName != null && ItemDoctorText.containsCjk(currentName);
        boolean hasCjkLore = currentLore != null && ItemDoctorText.containsCjk(currentLore);
        if (!hasCjkName && !hasCjkLore) {
            return false;
        }

        report.cjkStackFound();
        if (sfItem == null) {
            report.unknownIdFound(itemId);
            return repairOrphanedPresentation(item, currentMeta, itemId, hasCjkName, hasCjkLore, repair, report);
        }

        // Dynamic presentation hooks belong to addons. Never run those hooks on
        // the live player item, including during a read-only scan.
        ItemStack presentationItem = item.clone();
        if (presentationItem == item) {
            throw new IllegalStateException("Item clone did not provide an isolated presentation snapshot");
        }
        ItemMeta canonicalMeta = sfItem.getItem().getItemMeta();
        String canonicalName = legacyName(canonicalMeta);
        String repairedName = null;
        if (hasCjkName) {
            if (canonicalName != null && !ItemDoctorText.containsCjk(canonicalName)) {
                repairedName = canonicalName;
            } else {
                repairedName =
                        ItemDoctorText.preserveLeadingFormatting(currentName, ItemDoctorText.humanizeItemId(itemId));
            }
        }

        List<String> canonicalLore = legacyLore(canonicalMeta);
        List<String> repairedLore = currentLore;
        DynamicState state = DynamicState.empty();
        boolean stateCaptured = false;
        boolean loreStillUnresolved = false;

        if (hasCjkLore) {
            boolean canonicalLoreIsEnglish = !ItemDoctorText.containsCjk(canonicalLore);
            if (!canonicalLoreIsEnglish) {
                loreStillUnresolved = true;
            } else {
                try {
                    state = DynamicState.capture(presentationItem, sfItem);
                    stateCaptured = true;
                } catch (RuntimeException | LinkageError ex) {
                    report.failure();
                    Slimefun.logger()
                            .log(
                                    Level.WARNING,
                                    "Item doctor could not safely read dynamic state for Slimefun item " + itemId + '.',
                                    ex);
                }

                boolean entityStateAvailable = !(sfItem instanceof AbstractMonsterSpawner) || state.entityType != null;
                boolean authoritativeLoreRepair = stateCaptured
                        && state.safelyRestorable
                        && entityStateAvailable
                        && (sfItem.getAddon() instanceof Slimefun
                                || SAFE_STATIC_ADDON_LORE_IDS.contains(itemId)
                                || state.hasAuthoritativePresentationRestoration());

                if (authoritativeLoreRepair) {
                    repairedLore = ItemDoctorText.mergeStaticEnglishLore(currentLore, canonicalLore);
                } else if (stateCaptured) {
                    repairedLore = ItemDoctorText.mergeConservativeEnglishLore(
                            currentLore, canonicalLore, state::canRestoreDynamicLine);
                } else {
                    repairedLore =
                            ItemDoctorText.mergeConservativeEnglishLore(currentLore, canonicalLore, ignored -> false);
                }
                loreStillUnresolved = ItemDoctorText.containsCjk(repairedLore);
            }
        }

        if (!repair) {
            if (loreStillUnresolved) {
                report.unresolvedTemplateFound(itemId);
            }
            return false;
        }

        boolean nameChanged = hasCjkName && !Objects.equals(currentName, repairedName);
        boolean loreChanged = hasCjkLore && !Objects.equals(currentLore, repairedLore);
        if (!nameChanged && !loreChanged) {
            if (loreStillUnresolved) {
                report.unresolvedTemplateFound(itemId);
            }
            return false;
        }

        ItemMeta originalMeta = currentMeta.clone();
        try {
            if (nameChanged) {
                setLegacyName(currentMeta, repairedName);
            }
            if (loreChanged) {
                setLegacyLore(currentMeta, repairedLore == null || repairedLore.isEmpty() ? null : repairedLore);
            }
            presentationItem.setItemMeta(currentMeta);

            if (hasCjkLore && stateCaptured && state.safelyRestorable) {
                restoreDynamicPresentation(presentationItem, sfItem, state);
            }

            if (presentationItem.getType() != item.getType()
                    || presentationItem.getAmount() != item.getAmount()
                    || !itemId.equals(Slimefun.getItemDataService()
                            .getItemData(presentationItem)
                            .orElse(null))) {
                throw new IllegalStateException("Presentation hook changed item identity, material or amount");
            }
            ItemMeta presentationMeta = presentationItem.getItemMeta();
            ItemMeta finalMeta = originalMeta.clone();
            finalMeta.displayName(presentationMeta.displayName());
            finalMeta.lore(presentationMeta.lore());
            // Retain the existing legacy-lore recovery behavior for missing
            // dynamic markers, but never overwrite a pre-existing typed value.
            if (stateCaptured && state.safelyRestorable && state.charge != null) {
                presentationMeta
                        .getPersistentDataContainer()
                        .set(Slimefun.getRegistry().getItemChargeDataKey(), PersistentDataType.FLOAT, state.charge);
            }
            presentationMeta.getPersistentDataContainer().copyTo(finalMeta.getPersistentDataContainer(), false);
            if (!item.setItemMeta(finalMeta)) {
                throw new IllegalStateException("Item rejected its repaired presentation metadata");
            }
            String finalName = legacyName(finalMeta);
            List<String> finalLore = legacyLore(finalMeta);
            if ((finalName != null && ItemDoctorText.containsCjk(finalName))
                    || (finalLore != null && ItemDoctorText.containsCjk(finalLore))) {
                report.unresolvedTemplateFound(itemId);
            }
            report.stackRepaired();
            return true;
        } catch (RuntimeException | LinkageError ex) {
            report.failure();
            try {
                item.setItemMeta(originalMeta);
            } catch (RuntimeException | LinkageError rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Item doctor could not repair Slimefun item " + itemId
                                    + "; its original metadata was restored.",
                            ex);
            return false;
        }
    }

    private boolean repairOrphanedPresentation(
            ItemStack item,
            ItemMeta currentMeta,
            String itemId,
            boolean hasCjkName,
            boolean hasCjkLore,
            boolean repair,
            ItemDoctorReport report) {
        // Without the addon there is no authoritative lore template. Never guess, strip or
        // reorder orphaned lore because addons may have stored state in those lines.
        if (hasCjkLore) {
            report.unresolvedTemplateFound(itemId);
        }

        if (!hasCjkName
                || !repair
                || !Slimefun.getCfg().getBoolean("stability.item-doctor.repair-orphaned-item-names")) {
            return false;
        }

        ItemMeta originalMeta = currentMeta.clone();
        try {
            String fallbackName = ItemDoctorText.humanizeItemId(itemId);
            setLegacyName(currentMeta, ItemDoctorText.preserveLeadingFormatting(legacyName(currentMeta), fallbackName));
            item.setItemMeta(currentMeta);
            report.stackRepaired();
            return true;
        } catch (RuntimeException | LinkageError ex) {
            report.failure();
            try {
                item.setItemMeta(originalMeta);
            } catch (RuntimeException | LinkageError rollbackError) {
                ex.addSuppressed(rollbackError);
            }
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Item doctor could not safely repair the display name of orphaned Slimefun item " + itemId
                                    + '.',
                            ex);
            return false;
        }
    }

    private void restoreDynamicPresentation(ItemStack item, SlimefunItem sfItem, DynamicState state) {
        if (sfItem instanceof Rechargeable rechargeable && state.charge != null) {
            rechargeable.setItemCharge(item, state.charge);
        }

        if (sfItem instanceof LimitedUseItem limitedUseItem && state.usesLeft != null) {
            limitedUseItem.restoreUsesLore(item, state.usesLeft);
        }
        if (sfItem instanceof AbstractMonsterSpawner spawner && state.entityType != null) {
            spawner.refreshEntityTypePresentation(item, state.entityType);
        }
        if (state.soulbound) {
            if (!SlimefunUtils.isSoulbound(item)) {
                SlimefunUtils.setSoulbound(item, true);
            }
            ItemMeta meta = item.getItemMeta();
            List<String> existingLore = legacyLore(meta);
            List<String> lore = existingLore == null ? new ArrayList<>() : new ArrayList<>(existingLore);
            if (!lore.contains(SOULBOUND_LORE)) {
                lore.add(SOULBOUND_LORE);
                setLegacyLore(meta, lore);
                item.setItemMeta(meta);
            }
        }
        if (sfItem instanceof KnowledgeTome && state.tomeOwner != null) {
            refreshKnowledgeTomeOwner(item, state.tomeOwner);
        }

        if (state.legacyBackpackIdentity != null) {
            refreshLegacyBackpackIdentity(item, state.legacyBackpackIdentity);
        }
        if (sfItem instanceof SlimefunBackpack || state.backpackPdcBound) {
            refreshBackpackOwner(item);
        }
    }

    private void refreshBackpackOwner(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Optional<String> ownerId = PlayerBackpack.getOwnerUUID(meta);
        if (ownerId.isEmpty()) {
            return;
        }
        String ownerName;
        try {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(ownerId.get()));
            ownerName = owner.getName() == null ? ownerId.get() : owner.getName();
        } catch (IllegalArgumentException ex) {
            ownerName = ownerId.get();
        }
        List<String> existingLore = legacyLore(meta);
        List<String> lore = existingLore == null ? new ArrayList<>() : new ArrayList<>(existingLore);
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line != null && line.startsWith(BACKPACK_OWNER_PREFIX)) {
                lore.set(i, BACKPACK_OWNER_PREFIX + ownerName);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lore.add(BACKPACK_OWNER_PREFIX + ownerName);
        }
        setLegacyLore(meta, lore);
        item.setItemMeta(meta);
    }

    private void refreshLegacyBackpackIdentity(ItemStack item, String identity) {
        Matcher identityMatcher = LEGACY_BACKPACK_IDENTITY.matcher(identity);
        if (!identityMatcher.matches()) {
            return;
        }

        UUID ownerId = UUID.fromString(identityMatcher.group(1));
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        String ownerName = owner.getName() == null ? ownerId.toString() : owner.getName();
        ItemMeta meta = item.getItemMeta();
        List<String> existingLore = legacyLore(meta);
        List<String> lore = existingLore == null ? new ArrayList<>() : new ArrayList<>(existingLore);
        boolean idLineFound = false;
        boolean ownerLineFound = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            String plain = plain(line);
            if (!idLineFound
                    && plain != null
                    && (plain.contains(identity) || plain.trim().startsWith("ID:") || plain.contains("<ID>"))) {
                lore.set(i, BACKPACK_ID_PREFIX + identity);
                idLineFound = true;
            }
            if (!ownerLineFound && line != null && line.startsWith(BACKPACK_OWNER_PREFIX)) {
                lore.set(i, BACKPACK_OWNER_PREFIX + ownerName);
                ownerLineFound = true;
            }
        }
        if (!idLineFound) {
            lore.add(BACKPACK_ID_PREFIX + identity);
        }
        if (!ownerLineFound) {
            lore.add(BACKPACK_OWNER_PREFIX + ownerName);
        }
        setLegacyLore(meta, lore);
        item.setItemMeta(meta);
    }

    private void refreshKnowledgeTomeOwner(ItemStack item, UUID ownerId) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        String ownerName = owner.getName() == null ? ownerId.toString() : owner.getName();
        ItemMeta meta = item.getItemMeta();
        List<String> existingLore = legacyLore(meta);
        List<String> lore = existingLore == null ? new ArrayList<>() : new ArrayList<>(existingLore);
        while (lore.size() < 2) {
            lore.add("");
        }
        lore.set(0, legacy("&7Owner: &b" + ownerName));
        lore.set(1, legacy("&0" + ownerId));
        setLegacyLore(meta, lore);
        item.setItemMeta(meta);
    }

    private boolean inspectNestedItems(ItemStack item, boolean repair, ItemDoctorReport report, int depth) {
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
            for (ItemStack nested : contents) {
                changed |= inspectItem(nested, repair, report, depth);
            }
            if (changed && repair) {
                bundleMeta.setItems(contents);
                item.setItemMeta(bundleMeta);
            }
        }
        meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof Container container) {
                changed |= repairInventory(container.getInventory(), repair, report, depth);
                if (changed && repair) {
                    blockStateMeta.setBlockState(container);
                    item.setItemMeta(blockStateMeta);
                }
            }
        }
        return changed;
    }

    private static @Nonnull String legacy(@Nonnull String value) {
        return LEGACY_SECTION.serialize(LEGACY_AMPERSAND.deserialize(value));
    }

    private static @Nullable String plain(@Nullable String value) {
        return value == null ? null : PLAIN_TEXT.serialize(LEGACY_SECTION.deserialize(value));
    }

    private static @Nullable String legacyName(@Nonnull ItemMeta meta) {
        Component name = meta.displayName();
        return name == null ? null : LEGACY_SECTION.serialize(name);
    }

    private static @Nullable List<String> legacyLore(@Nonnull ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null
                ? null
                : lore.stream().map(LEGACY_SECTION::serialize).toList();
    }

    private static void setLegacyName(@Nonnull ItemMeta meta, @Nullable String value) {
        meta.displayName(
                value == null ? null : LEGACY_SECTION.deserialize(value).decoration(TextDecoration.ITALIC, false));
    }

    private static void setLegacyLore(@Nonnull ItemMeta meta, @Nullable List<String> lore) {
        meta.lore(
                lore == null
                        ? null
                        : lore.stream()
                                .map(line -> LEGACY_SECTION.deserialize(line).decoration(TextDecoration.ITALIC, false))
                                .toList());
    }

    private static final class DynamicState {
        private final Float charge;
        private final Integer usesLeft;
        private final EntityType entityType;
        private final UUID tomeOwner;
        private final String legacyBackpackIdentity;
        private final boolean backpackPdcBound;
        private final boolean soulbound;
        private final boolean safelyRestorable;

        private DynamicState(
                @Nullable Float charge,
                @Nullable Integer usesLeft,
                @Nullable EntityType entityType,
                @Nullable UUID tomeOwner,
                @Nullable String legacyBackpackIdentity,
                boolean backpackPdcBound,
                boolean soulbound,
                boolean safelyRestorable) {
            this.charge = charge;
            this.usesLeft = usesLeft;
            this.entityType = entityType;
            this.tomeOwner = tomeOwner;
            this.legacyBackpackIdentity = legacyBackpackIdentity;
            this.backpackPdcBound = backpackPdcBound;
            this.soulbound = soulbound;
            this.safelyRestorable = safelyRestorable;
        }

        private static DynamicState empty() {
            return new DynamicState(null, null, null, null, null, false, false, true);
        }

        private static DynamicState capture(ItemStack item, SlimefunItem sfItem) {
            ItemMeta meta = item.getItemMeta();
            List<String> lore = legacyLore(meta);

            boolean safelyRestorable = true;
            Float charge = null;
            if (sfItem instanceof Rechargeable rechargeable) {
                Float storedCharge = meta.getPersistentDataContainer()
                        .get(Slimefun.getRegistry().getItemChargeDataKey(), PersistentDataType.FLOAT);
                float maximum = rechargeable.getMaxItemCharge(item);

                // Some legacy addons implement Rechargeable on both electric and durability-only
                // variants. A zero-capacity variant has no charge state to preserve and must not
                // make an otherwise static item unrepairable.
                if (maximum > 0F) {
                    charge = storedCharge != null ? storedCharge : ItemDoctorText.findLegacyCharge(lore);
                    if (charge == null
                            || !Float.isFinite(charge)
                            || !Float.isFinite(maximum)
                            || charge < 0F
                            || charge > maximum) {
                        safelyRestorable = false;
                    }
                } else if (storedCharge != null && Math.abs(storedCharge) > 0.0001F) {
                    safelyRestorable = false;
                }
            }

            Integer usesLeft = null;
            if (sfItem instanceof LimitedUseItem limitedUseItem) {
                var storedUses = limitedUseItem.getStoredUses(item);
                if (storedUses.isPresent()) {
                    usesLeft = storedUses.getAsInt();
                } else {
                    usesLeft = ItemDoctorText.findLegacyUsesLeft(lore);
                    if (usesLeft == null) {
                        usesLeft = limitedUseItem.getMaxUseCount();
                    }
                }
                if (usesLeft < 1 || usesLeft > limitedUseItem.getMaxUseCount()) {
                    safelyRestorable = false;
                }
            }

            EntityType type = null;
            if (sfItem instanceof AbstractMonsterSpawner spawner) {
                type = spawner.getEntityType(item).orElse(null);
            }

            UUID tomeOwner = null;
            if (sfItem instanceof KnowledgeTome) {
                String hiddenOwner = lore != null && lore.size() > 1 ? plain(lore.get(1)) : null;
                boolean unboundTome = hiddenOwner != null && hiddenOwner.isBlank();
                if (lore != null) {
                    for (String line : lore) {
                        String ownerValue = plain(line);
                        try {
                            if (ownerValue != null && !ownerValue.isBlank()) {
                                tomeOwner = UUID.fromString(ownerValue.trim());
                                break;
                            }
                        } catch (IllegalArgumentException ignored) {
                            // Continue until a hidden owner UUID is found.
                        }
                    }
                }
                if (!unboundTome && tomeOwner == null) {
                    safelyRestorable = false;
                }
            }

            boolean backpackPdcBound = PlayerBackpack.getBackpackUUID(meta).isPresent();
            boolean backpackOwnerKnown = PlayerBackpack.getOwnerUUID(meta).isPresent();
            String legacyBackpackIdentity = null;
            if (!backpackPdcBound && lore != null) {
                for (String line : lore) {
                    String plain = plain(line);
                    if (plain == null) {
                        continue;
                    }
                    Matcher matcher = LEGACY_BACKPACK_IDENTITY.matcher(plain);
                    if (matcher.find()) {
                        legacyBackpackIdentity = matcher.group();
                        break;
                    }
                }
            }
            if (backpackPdcBound && !backpackOwnerKnown) {
                safelyRestorable = false;
            }
            if (sfItem instanceof SlimefunBackpack
                    && !backpackPdcBound
                    && backpackOwnerKnown
                    && legacyBackpackIdentity == null) {
                safelyRestorable = false;
            }

            boolean soulbound = SlimefunUtils.isSoulbound(item) || hasLegacyChineseSoulboundLine(lore);
            return new DynamicState(
                    charge,
                    usesLeft,
                    type,
                    tomeOwner,
                    legacyBackpackIdentity,
                    backpackPdcBound,
                    soulbound,
                    safelyRestorable);
        }

        private boolean hasAuthoritativePresentationRestoration() {
            return charge != null
                    || usesLeft != null
                    || entityType != null
                    || tomeOwner != null
                    || legacyBackpackIdentity != null
                    || backpackPdcBound;
        }

        private boolean canRestoreDynamicLine(String line) {
            String plain = plain(line);
            if (plain == null) {
                return false;
            }
            if (legacyBackpackIdentity != null && plain.contains(legacyBackpackIdentity)) {
                return true;
            }
            if (tomeOwner != null && plain.contains(tomeOwner.toString())) {
                return true;
            }
            if (charge != null && ItemDoctorText.findLegacyCharge(List.of(line)) != null) {
                return true;
            }
            return usesLeft != null && ItemDoctorText.findLegacyUsesLeft(List.of(line)) != null;
        }

        private static boolean hasLegacyChineseSoulboundLine(@Nullable List<String> lore) {
            if (lore == null) {
                return false;
            }
            for (String line : lore) {
                String plain = plain(line);
                if (plain == null) {
                    continue;
                }
                String normalized = plain.trim();
                if (normalized.equals("\u7075\u9B42\u7ED1\u5B9A") || normalized.equals("\u9748\u9B42\u7D81\u5B9A")) {
                    return true;
                }
            }
            return false;
        }
    }
}
