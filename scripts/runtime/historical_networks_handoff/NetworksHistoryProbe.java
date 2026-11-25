package com.wickidcow.doctorfixture;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * True old saved Networks addon item sources, not synthetic old text.
 * Only writes a disposable world chest on seed with the published 1.0.65
 * plugin. The candidate and replay boot NEVER write the world chest.
 */
public final class NetworksHistoryProbe extends JavaPlugin {
    private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
    private static final NamespacedKey OWNER = new NamespacedKey("networks_history_probe", "owner");
    private static final int MAX_ITEMS = 53;
    private static final int CHEST_SLOTS = 54;
    private static final int STONE_SLOT = 53;

    @Override
    public void onEnable() {
        NativeNbtStructuralEvidence.selfTest();
        Objects.requireNonNull(getCommand("sflnetworkshistory")).setExecutor(this::command);
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !(args[0].equals("seed") || args[0].equals("stabilize")
                || args[0].equals("baseline") || args[0].equals("candidate") || args[0].equals("replay"))) {
            sender.sendMessage("sflnetworkshistory seed|stabilize|baseline|candidate|replay");
            return true;
        }
        try {
            switch (args[0]) {
                case "seed" -> seed();
                case "stabilize" -> stabilize();
                case "baseline" -> baseline();
                case "candidate" -> compare(false);
                case "replay" -> compare(true);
                default -> throw new IllegalArgumentException("unknown command");
            }
            Files.writeString(data().resolve(args[0] + ".pass"), "PASS " + args[0] + "\n");
            getLogger().info("SFL_TRUE_NETWORKS_" + args[0].toUpperCase() + "_PASS");
        } catch (Exception | LinkageError failure) {
            Throwable cause = failure;
            for (int i = 0; i < 6 && cause instanceof java.lang.reflect.InvocationTargetException; i++) {
                Throwable next = ((java.lang.reflect.InvocationTargetException) cause).getTargetException();
                if (next == null || next == cause) break;
                cause = next;
            }
            String safe = cause instanceof SourceInvariantFailure
                    ? cause.getMessage() : cause.getClass().getSimpleName();
            getLogger().severe("SFL_TRUE_NETWORKS_FAIL " + args[0] + " invariant=" + safe);
            try {
                Files.writeString(data().resolve(args[0] + ".fail"), safe + "\n");
            } catch (Exception ignored) {
                // External runner also fails if a PASS marker is missing.
            }
        }
        return true;
    }

    private Path data() throws Exception {
        Path base = getDataFolder().toPath();
        Files.createDirectories(base);
        return base;
    }

    // Only our own fixed-source invariant messages are reportable.
    // Runtime/addon exception messages may contain private item metadata.
    private static final class SourceInvariantFailure extends IllegalStateException {
        private SourceInvariantFailure(String description) {
            super(description);
        }
    }

    private static void check(boolean success, String description) {
        if (!success) throw new SourceInvariantFailure(description);
    }

    private static Inventory physicalChest(int part, boolean create) {
        check(part >= 0 && part < 2, "Only two isolated chest blocks are authorized");
        World world = Bukkit.getWorlds().getFirst();
        Block block = world.getBlockAt(8 + 4 * part, 71, 8);
        block.getChunk().load();
        if (create) {
            check(block.getType().isAir(), "Disposable source world location already occupied");
            block.setType(Material.CHEST);
        }
        check(block.getState() instanceof Chest, "Published source physical chest is missing");
        Inventory inventory = ((Chest) block.getState()).getBlockInventory();
        check(inventory.getSize() == 27, "Expected isolated single 27-slot world chest");
        return inventory;
    }

    private static ItemStack sourceSlot(int globalSlot) {
        check(globalSlot >= 0 && globalSlot < CHEST_SLOTS, "Historical source slot outside 54 positions");
        return physicalChest(globalSlot / 27, false).getItem(globalSlot % 27);
    }

    private static List<SlimefunItem> allRegistered() {
        return Slimefun.getRegistry().getAllSlimefunItems().stream()
                .filter(item -> "Networks".equals(item.getAddon().getName()))
                .sorted(Comparator.comparing(SlimefunItem::getId))
                .toList();
    }

    /** Exact old-version registry sample plus original Networks monitor. */
    private static List<SlimefunItem> registered() {
        List<SlimefunItem> all = allRegistered();
        check(all.size() >= 24, "Published Networks registered fewer than 24 items");
        List<SlimefunItem> selected = new ArrayList<>(all.stream().limit(52).toList());
        SlimefunItem monitor = all.stream().filter(item -> "NTW_MONITOR".equals(item.getId()))
                .findFirst().orElseThrow(() ->
                        new SourceInvariantFailure("Published old Networks monitor is not registered"));
        if (!selected.contains(monitor)) selected.add(monitor);
        selected.sort(Comparator.comparing(SlimefunItem::getId));
        check(selected.size() <= MAX_ITEMS, "Networks historical sample exceeds private two-chest budget");
        return List.copyOf(selected);
    }

    private static String itemId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null
                : meta.getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }

    private static ItemStack nativeItem(ItemStack item) {
        return ItemStack.deserializeBytes(item.serializeAsBytes());
    }

    private static boolean nativeSameIgnoringAmount(ItemStack first, ItemStack second) {
        ItemStack a = nativeItem(first);
        ItemStack b = nativeItem(second);
        a.setAmount(1);
        b.setAmount(1);
        return Arrays.equals(a.serializeAsBytes(), b.serializeAsBytes());
    }

    private void seed() throws Exception {
        Path dir = data();
        Path origins = dir.resolve("private-originals");
        check(!Files.exists(origins), "Source originals unexpectedly exist before first boot");
        Files.createDirectory(origins);
        List<SlimefunItem> items = registered();
        check(items.size() >= 24 && items.size() <= MAX_ITEMS,
                "Published Networks v1.0.65 registered item count outside expected 24..53");
        for (int part = 0; part < 2; part++) {
            Inventory chest = physicalChest(part, true);
            check(Arrays.stream(chest.getContents()).allMatch(item -> item == null || item.getType().isAir()),
                    "Disposable world chest unexpectedly contains prior stock");
        }
        List<String> ids = new ArrayList<>();
        long total = 0;
        for (int slot = 0; slot < items.size(); slot++) {
            SlimefunItem item = items.get(slot);
            String id = item.getId();
            ItemStack source = nativeItem(item.getItem());
            check(id.equals(itemId(source)), "Published Networks item ID absent: " + id);
            int amount = Math.min(3, source.getMaxStackSize());
            check(amount > 0, "Invalid published Networks item capacity: " + id);
            source.setAmount(amount);
            byte[] bytes = source.serializeAsBytes();
            physicalChest(slot / 27, false).setItem(slot % 27, source);
            check(Arrays.equals(bytes, sourceSlot(slot).serializeAsBytes()),
                    "Original Networks world write failed readback: " + id);
            Files.write(origins.resolve(slot + ".bin"), bytes);
            ids.add(id);
            total += amount;
        }
        physicalChest(1, false).setItem(26, new ItemStack(Material.STONE, 17));
        check(sourceSlot(STONE_SLOT).getAmount() == 17, "Vanilla control stock missing");
        Files.writeString(dir.resolve("original-item-ids.txt"), String.join("\n", ids) + "\n");
        Files.writeString(dir.resolve("original-count.txt"), String.valueOf(total) + "\n");
        getLogger().info("SFL_TRUE_NETWORKS_SEED items=" + ids.size() + " total=" + total);
    }

    /**
     * The first Paper world-save can canonicalize text component NBT even
     * when Bukkit considers an item unchanged. Reopen the SAME old version
     * before upgrading and establish source identity from the truly persisted
     * physical world, not from a detached pre-save constructor instance.
     *
     * Permit new baseline private receipts ONLY when the old and persisted
     * versions agree on quantity, Slimefun ID, all non-presentation native
     * bytes and Bukkit presentation semantics. Otherwise refuse migration.
     * Never change either physical chest on the stabilization boot.
     */
    private void stabilize() throws Exception {
        Path base = data();
        Path originalDir = base.resolve("private-originals");
        Path seedDir = base.resolve("private-before-world-save");
        check(Files.isDirectory(originalDir) && !Files.exists(seedDir),
                "Old-world source stabilization already performed or source receipts missing");
        List<String> ids = Files.readAllLines(base.resolve("original-item-ids.txt"));
        check(ids.size() >= 24 && ids.size() <= MAX_ITEMS
                        && ids.stream().distinct().count() == ids.size(),
                "Saved old Networks source ID manifest invalid during baseline restart");
        List<ItemStack> loadedRegistry = registered().stream().map(SlimefunItem::getItem).toList();
        check(loadedRegistry.size() == ids.size(),
                "Old Networks registry count changed on independent old-version restart");

        List<byte[]> persisted = new ArrayList<>();
        long total = 0;
        int harmlessSaveNormalizations = 0;
        for (int slot = 0; slot < CHEST_SLOTS; slot++) {
            ItemStack live = sourceSlot(slot);
            if (slot < ids.size()) {
                String id = ids.get(slot);
                check(live != null && id.equals(itemId(live)),
                        "Old Networks item ID disappeared from saved-world slot " + slot + " " + id);
                byte[] oldBytes = Files.readAllBytes(originalDir.resolve(slot + ".bin"));
                ItemStack original = ItemStack.deserializeBytes(oldBytes);
                byte[] observedBytes = live.serializeAsBytes();
                check(id.equals(itemId(original)) && original.getType() == live.getType()
                                && original.getAmount() == live.getAmount(),
                        "Saved-world source type, ID or count changed in old plugin slot " + slot + " " + id);
                if (!Arrays.equals(oldBytes, observedBytes)) {
                    ItemStack originalNoText = nativeItem(original);
                    ItemStack observedNoText = nativeItem(live);
                    originalNoText.editMeta(meta -> {
                        meta.displayName(null);
                        meta.lore(null);
                    });
                    observedNoText.editMeta(meta -> {
                        meta.displayName(null);
                        meta.lore(null);
                    });
                    check(original.isSimilar(live)
                                    && Arrays.equals(originalNoText.serializeAsBytes(),
                                            observedNoText.serializeAsBytes()),
                            "Saved-world native normalization changed functional data or text in old plugin slot "
                                    + slot + " " + id);
                    harmlessSaveNormalizations++;
                }
                total += live.getAmount();
                persisted.add(observedBytes);
            } else if (slot == STONE_SLOT) {
                check(live != null && live.getType() == Material.STONE && live.getAmount() == 17,
                        "Old-world vanilla control changed after source restart");
            } else {
                check(live == null || live.getType().isAir(),
                        "Old-world source gained unexpected chest stock");
            }
        }
        check(total == Long.parseLong(Files.readString(base.resolve("original-count.txt")).trim()),
                "Old-world quantities changed during same-version Paper persistence");
        // All sampled sources have passed conservation before replacing the
        // private reference files. Preserve the pre-save bytes independently.
        Files.createDirectory(seedDir);
        for (int slot = 0; slot < persisted.size(); slot++) {
            Path previous = originalDir.resolve(slot + ".bin");
            Files.copy(previous, seedDir.resolve(slot + ".bin"));
            Files.write(previous, persisted.get(slot));
        }
        Files.writeString(base.resolve("world-save-normalization-count.txt"),
                "registered=" + persisted.size()
                        + ";strict_cosmetic_normalizations=" + harmlessSaveNormalizations + "\n");
        assertOriginalChest(ids);
        getLogger().info("SFL_TRUE_NETWORKS_STABILIZED_OLD_WORLD_PASS items="
                + persisted.size() + " cosmetic_save_normalizations=" + harmlessSaveNormalizations);
    }

    /**
     * One additional independent same-version server restart must preserve
     * the pinned old-world bytes before we attribute ANY drift to an addon
     * upgrade or Doctor candidate. This stage never changes world stock.
     */
    private void baseline() throws Exception {
        List<String> ids = Files.readAllLines(data().resolve("original-item-ids.txt"));
        check(ids.size() >= 24 && ids.size() <= MAX_ITEMS,
                "Published old-version saved-world manifest unexpectedly changed");
        int reordered = assertOriginalChest(ids);
        getLogger().info("SFL_TRUE_NETWORKS_SAME_VERSION_BASELINE_VALIDATED"
                + " typed_equal_raw_reordered=" + reordered);
    }

    /**
     * Read-only provenance: a different byte ordering is allowed only when
     * the COMPLETE typed NBT value and full Bukkit ItemMeta are identical.
     * Original historical bytes remain on disk without normalization; this
     * predicate is not used by the production Doctor repair engine.
     */
    private static boolean sameTypedHistoricalValue(byte[] original, ItemStack observed) {
        if (observed == null) return false;
        byte[] observedBytes = observed.serializeAsBytes();
        if (Arrays.equals(original, observedBytes)) return true;
        if (!Boolean.TRUE.equals(NativeNbtStructuralEvidence.equivalent(original, observedBytes))) {
            return false;
        }
        ItemStack recorded = ItemStack.deserializeBytes(original);
        return recorded != null
                && recorded.getType() == observed.getType()
                && recorded.getAmount() == observed.getAmount()
                && Objects.equals(itemId(recorded), itemId(observed))
                && recorded.isSimilar(observed)
                && Objects.equals(recorded.getItemMeta(), observed.getItemMeta());
    }

    private int assertOriginalChest(List<String> ids) throws Exception {
        int rawReorderedOnly = 0;
        for (int slot = 0; slot < CHEST_SLOTS; slot++) {
            ItemStack live = sourceSlot(slot);
            if (slot < ids.size()) {
                byte[] original = Files.readAllBytes(data().resolve("private-originals").resolve(slot + ".bin"));
                check(live != null, "True saved Networks source missing in world slot " + slot);
                boolean nativeEqual = Arrays.equals(original, live.serializeAsBytes());
                boolean idEqual = ids.get(slot).equals(itemId(live));
                if (!nativeEqual || !idEqual) {
                    ItemStack recorded = ItemStack.deserializeBytes(original);
                    ItemStack recordedPlain = nativeItem(recorded);
                    ItemStack observedPlain = nativeItem(live);
                    recordedPlain.editMeta(meta -> {
                        meta.displayName(null);
                        meta.lore(null);
                    });
                    observedPlain.editMeta(meta -> {
                        meta.displayName(null);
                        meta.lore(null);
                    });
                    boolean functionalBytesSame = Arrays.equals(
                            recordedPlain.serializeAsBytes(), observedPlain.serializeAsBytes());
                    boolean oldSimilarity = recorded.isSimilar(live);
                    boolean amountSame = recorded.getAmount() == live.getAmount();
                    // Distinguish a changed on-disk item from native serialization
                    // canonicalization on the newer Paper/Slimefun/addon runtime.
                    // Preserve the original private bytes and still fail closed.
                    byte[] reserializedOriginal = recorded.serializeAsBytes();
                    boolean sourceCanonicalizedOnRead = !Arrays.equals(original, reserializedOriginal);
                    boolean canonicalizedSourceMatchesWorld =
                            Arrays.equals(reserializedOriginal, live.serializeAsBytes());
                    ItemMeta sourceMeta = recorded.getItemMeta();
                    ItemMeta worldMeta = live.getItemMeta();
                    boolean componentNameSame = sourceMeta != null && worldMeta != null
                            && Objects.equals(sourceMeta.displayName(), worldMeta.displayName());
                    boolean componentLoreSame = sourceMeta != null && worldMeta != null
                            && Objects.equals(sourceMeta.lore(), worldMeta.lore());
                    boolean completeMetaSame = Objects.equals(sourceMeta, worldMeta);
                    // A byte mismatch despite equal ItemMeta may reflect an
                    // unstable serialization representation. Compare detached
                    // native round trips only; never normalize physical stock.
                    boolean repeatSourceBytesStable =
                            Arrays.equals(reserializedOriginal, recorded.serializeAsBytes());
                    boolean repeatWorldBytesStable =
                            Arrays.equals(live.serializeAsBytes(), live.serializeAsBytes());
                    ItemStack sourceRoundTrip = nativeItem(recorded);
                    ItemStack worldRoundTrip = nativeItem(live);
                    boolean oneRoundTripSourceMatchesWorld = Arrays.equals(
                            sourceRoundTrip.serializeAsBytes(), worldRoundTrip.serializeAsBytes());
                    ItemStack secondSourceRoundTrip = nativeItem(sourceRoundTrip);
                    ItemStack secondWorldRoundTrip = nativeItem(worldRoundTrip);
                    boolean twoRoundTripsSourceMatchesWorld = Arrays.equals(
                            secondSourceRoundTrip.serializeAsBytes(), secondWorldRoundTrip.serializeAsBytes());
                    // Diagnostic ONLY: exact typed NBT comparison after sorting
                    // compound names, preserving every tag value and list order.
                    // Null = unsupported; neither result authorizes item repair.
                    Boolean nativeStructuralEquality = NativeNbtStructuralEvidence.equivalent(
                            original, live.serializeAsBytes());
                    if (idEqual && amountSame && oldSimilarity && functionalBytesSame
                            && completeMetaSame && Boolean.TRUE.equals(nativeStructuralEquality)
                            && sameTypedHistoricalValue(original, live)) {
                        // Physical slot has no typed-data loss. This is NOT an
                        // exact-byte identity claim and not a repair authorization.
                        rawReorderedOnly++;
                        continue;
                    }
                    // Only public ID plus boolean categories. Never print names, lore,
                    // serialized bytes, typed owner data or PDC values.
                    throw new SourceInvariantFailure(
                            "Published old world item drift slot=" + slot
                                    + " id=" + ids.get(slot)
                                    + " oldIdSame=" + idEqual
                                    + " amountSame=" + amountSame
                                    + " nativeSimilarity=" + oldSimilarity
                                    + " nonPresentationBytesSame=" + functionalBytesSame
                                    + " sourceCanonicalizedOnRead=" + sourceCanonicalizedOnRead
                                    + " canonicalizedSourceMatchesWorld=" + canonicalizedSourceMatchesWorld
                                    + " componentNameSame=" + componentNameSame
                                    + " componentLoreSame=" + componentLoreSame
                                    + " completeMetaSame=" + completeMetaSame
                                    + " repeatSourceBytesStable=" + repeatSourceBytesStable
                                    + " repeatWorldBytesStable=" + repeatWorldBytesStable
                                    + " oneRoundTripSourceMatchesWorld=" + oneRoundTripSourceMatchesWorld
                                    + " twoRoundTripsSourceMatchesWorld=" + twoRoundTripsSourceMatchesWorld
                                    + " nativeNbtReadable=" + (nativeStructuralEquality != null)
                                    + " nativeNbtStructurallyEqual=" + Boolean.TRUE.equals(nativeStructuralEquality));
                }
            } else if (slot == STONE_SLOT) {
                check(live != null && live.getType() == Material.STONE && live.getAmount() == 17,
                        "Unrelated vanilla stock changed after restart");
            } else {
                check(live == null || live.getType().isAir(),
                        "Unexpected physical chest stock inserted");
            }
        }
        return rawReorderedOnly;
    }

    private void compare(boolean replay) throws Exception {
        Path base = data();
        List<String> ids = Files.readAllLines(base.resolve("original-item-ids.txt"));
        check(ids.size() >= 24 && ids.size() <= MAX_ITEMS
                        && ids.stream().distinct().count() == ids.size(),
                "Private historical source manifest malformed");
        var all = allRegistered().stream().collect(Collectors.toMap(SlimefunItem::getId, item -> item));
        check(all.size() >= ids.size(), "New Networks version silently lost source registrations");
        for (String id : ids) check(all.containsKey(id), "Published addon removed saved item ID " + id);
        int rawReorderedOnly = assertOriginalChest(ids);

        Class<?> update = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method resolver = update.getDeclaredMethod("registeredTemplate", ItemStack.class);
        Method prepare = doctor.getDeclaredMethod(
                "prepare", ItemStack.class, ItemStack.class, ItemStack.class);
        Method matches = doctor.getDeclaredMethod(
                "matchesSavedBackup", ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
        Method backup = doctor.getDeclaredMethod("backup", Path.class, UUID.class, byte[].class);
        Method read = doctor.getDeclaredMethod("readVerifiedBackup", Path.class, UUID.class, UUID.class);
        for (Method m : List.of(resolver, prepare, matches, backup, read)) m.setAccessible(true);

        Path receipts = base.resolve("private-doctor-receipts");
        UUID operator;
        if (replay) {
            check(Files.isDirectory(receipts), "Private evidence directory missing after restart");
            operator = UUID.fromString(Files.readString(base.resolve("receipt-owner-uuid.txt")).trim());
        } else {
            check(!Files.exists(receipts), "First candidate boot already has receipt directory");
            Files.createDirectory(receipts);
            operator = UUID.randomUUID();
            Files.writeString(base.resolve("receipt-owner-uuid.txt"), operator.toString());
        }
        StringBuilder report = new StringBuilder("item_id\tclassification\n");
        long currentTotal = 0;
        int exact = 0, eligible = 0, refused = 0;
        int similarTypedEqual = 0, similarTypedDifferent = 0, similarTypedUnreadable = 0;
        for (int slot = 0; slot < ids.size(); slot++) {
            String id = ids.get(slot);
            ItemStack old = nativeItem(sourceSlot(slot));
            byte[] original = Files.readAllBytes(base.resolve("private-originals").resolve(slot + ".bin"));
            check(sameTypedHistoricalValue(original, old),
                    "Old Networks item changed typed value before comparison: " + id);
            ItemStack template = all.get(id).getItem();
            byte[] unchangedTemplate = template.serializeAsBytes();
            ItemStack fresh = nativeItem(template);
            ItemStack resolved = (ItemStack) resolver.invoke(null, old);
            String classification;
            ItemStack proposed = null;
            if (resolved == null || !id.equals(itemId(resolved))) {
                classification = "UNRESOLVED";
                refused++;
            } else if (nativeSameIgnoringAmount(old, fresh)) {
                classification = "EXACT_NATIVE_MATCH";
                exact++;
                check(prepare.invoke(null, old, fresh, resolved) == null,
                        "Doctor tried to rewrite an identical Networks item " + id);
            } else if (old.isSimilar(fresh)) {
                // These historical items already pass Bukkit stack similarity,
                // yet raw native bytes differ. Distinguish unordered compound
                // serialization from truly different typed payloads without
                // changing any source, registered template or saved chest.
                ItemStack normalizedOld = nativeItem(old);
                ItemStack normalizedFresh = nativeItem(fresh);
                normalizedOld.setAmount(1);
                normalizedFresh.setAmount(1);
                Boolean sameTyped = NativeNbtStructuralEvidence.equivalent(
                        normalizedOld.serializeAsBytes(), normalizedFresh.serializeAsBytes());
                if (Boolean.TRUE.equals(sameTyped)) {
                    classification = "SIMILAR_TYPED_EQUAL_RAW_DRIFT";
                    similarTypedEqual++;
                } else if (Boolean.FALSE.equals(sameTyped)) {
                    classification = "SIMILAR_TYPED_DIFFERENCE";
                    similarTypedDifferent++;
                } else {
                    classification = "SIMILAR_TYPED_UNREADABLE";
                    similarTypedUnreadable++;
                }
                refused++;
            } else {
                proposed = (ItemStack) prepare.invoke(null, old, fresh, resolved);
                if (proposed == null) {
                    classification = "REFUSED_NON_PRESENTATION";
                    refused++;
                } else {
                    check(proposed.getAmount() == old.getAmount()
                                    && proposed.isSimilar(fresh)
                                    && nativeSameIgnoringAmount(proposed, fresh)
                                    && (boolean) matches.invoke(null, old, proposed, fresh, resolved),
                            "Doctor changed true historic Networks functional item " + id);
                    ItemStack attacker = nativeItem(old);
                    attacker.editMeta(meta -> meta.getPersistentDataContainer().set(
                            OWNER, PersistentDataType.LONG, 114911L));
                    check(prepare.invoke(null, attacker, fresh, resolved) == null,
                            "Doctor accepted typed-owner Networks item " + id);
                    ItemStack custom = nativeItem(old);
                    custom.editMeta(meta -> meta.displayName(Component.text("Player custom item")));
                    check(prepare.invoke(null, custom, fresh, resolved) == null,
                            "Doctor accepted meaningful renamed Networks item " + id);
                    classification = "STRICT_STYLE_REPAIRABLE";
                    eligible++;
                }
            }
            Path witness = receipts.resolve(slot + ".receipt");
            Path proposedFile = receipts.resolve(slot + ".proposed");
            if (!replay && proposed != null) {
                Path binary = (Path) backup.invoke(null, base, operator, original);
                UUID receiptId = UUID.fromString(binary.getFileName().toString()
                        .substring((operator.toString() + "-").length(),
                                binary.getFileName().toString().length() - 4));
                ItemStack verified = (ItemStack) read.invoke(null, base, operator, receiptId);
                check(Arrays.equals(original, verified.serializeAsBytes()),
                        "Real-addon exact original Doctor backup failed readback");
                Files.writeString(witness, receiptId.toString());
                Files.write(proposedFile, proposed.serializeAsBytes());
            } else if (replay && proposed != null) {
                UUID receiptId = UUID.fromString(Files.readString(witness).trim());
                ItemStack verified = (ItemStack) read.invoke(null, base, operator, receiptId);
                check(Arrays.equals(original, verified.serializeAsBytes())
                                && Arrays.equals(proposed.serializeAsBytes(), Files.readAllBytes(proposedFile)),
                        "Real-addon original backup or proposed native bytes changed across restart");
            } else if (replay) {
                check(!Files.exists(witness) && !Files.exists(proposedFile),
                        "Unapproved historical addon source gained a repair receipt");
            }
            // The physical chest must remain unchanged under assertOriginalChest().
            // A detached Bukkit copy can reorder NBT compound fields without
            // changing the full typed value; never waive the template check.
            check(sameTypedHistoricalValue(original, old)
                            && Arrays.equals(unchangedTemplate, template.serializeAsBytes()),
                    "Candidate Doctor or addon test modified typed historical source or template: " + id);
            currentTotal += old.getAmount();
            report.append(id).append('\t').append(classification).append('\n');
        }
        check(currentTotal == Long.parseLong(Files.readString(base.resolve("original-count.txt")).trim()),
                "Networks original saved quantities changed across runtime boot");
        check(exact + eligible + refused == ids.size(), "Historical Networks count partition failed");
        check(similarTypedEqual + similarTypedDifferent + similarTypedUnreadable <= refused,
                "Typed NBT observations must not inflate the refused item count");
        Path reportFile = base.resolve("classification.tsv");
        if (replay) {
            check(Files.readString(reportFile).equals(report.toString()),
                    "Networks current Doctor eligibility classifications changed over restart");
        } else {
            Files.writeString(reportFile, report.toString());
        }
        assertOriginalChest(ids);
        getLogger().info("SFL_TRUE_NETWORKS_DIAGNOSTIC_" + (replay ? "RESTART" : "CANDIDATE")
                + "_PASS source_items=" + ids.size() + " exact=" + exact + " strict_repairable="
                + eligible + " refused=" + refused + " quantity=" + currentTotal
                + " raw_order_only=" + rawReorderedOnly
                + " similar_typed_equal=" + similarTypedEqual
                + " similar_typed_different=" + similarTypedDifferent
                + " similar_typed_unreadable=" + similarTypedUnreadable);
    }
}
