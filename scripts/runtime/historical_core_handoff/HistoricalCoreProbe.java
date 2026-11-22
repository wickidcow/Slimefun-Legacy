package com.wickidcow.doctorfixture;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Source items are minted by the ACTUAL published v4.1.55 plugin and then
 * observed, without writes, on v4.1.81 in a second JVM with the saved world.
 * This plugin has no compile-time dependency on any Slimefun API.
 */
public final class HistoricalCoreProbe extends JavaPlugin {
    private static final String[] IDS = {
            "CARBONADO", "CARBON_CHUNK", "RAW_CARBONADO", "STEEL_INGOT"
    };
    private static final int[] AMOUNTS = {63, 8, 7, 12};

    @Override
    public void onEnable() {
        PluginCommand command = getCommand("sflhistoricprobe");
        if (command == null) {
            throw new IllegalStateException("Historical handoff command is missing");
        }
        command.setExecutor(this::command);
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1
                || !(args[0].equals("seed") || args[0].equals("verify") || args[0].equals("candidate")
                        || args[0].equals("replay"))) {
            sender.sendMessage("sflhistoricprobe seed|verify|candidate|replay");
            return true;
        }
        String phase = args[0];
        try {
            if (phase.equals("seed")) {
                seed();
            } else if (phase.equals("verify")) {
                verify();
            } else if (phase.equals("candidate")) {
                candidate();
            } else {
                replay();
            }
            getLogger().info("SFL_HISTORICAL_CORE_" + phase.toUpperCase() + "_PASS");
        } catch (Exception | LinkageError error) {
            // Reflection wrappers obscure real plugin/test exceptions. Unwrap
            // the exact diagnostic without printing source item bytes, owner
            // metadata or PDC contents; only error type + safe error message.
            Throwable cause = error;
            for (int depth = 0; depth < 8
                    && cause instanceof java.lang.reflect.InvocationTargetException; depth++) {
                Throwable target = ((java.lang.reflect.InvocationTargetException) cause).getTargetException();
                if (target == null || target == cause) {
                    break;
                }
                cause = target;
            }
            String failure = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            try {
                Files.writeString(folder().resolve(phase + ".fail"), failure);
            } catch (IOException ignored) {
                // The Python native harness still fails on a missing PASS marker.
            }
            getLogger().severe("SFL_HISTORICAL_CORE_FAIL " + phase + ": " + failure);
        }
        return true;
    }

    private Path folder() throws IOException {
        Path path = getDataFolder().toPath();
        Files.createDirectories(path);
        return path;
    }

    private static void require(boolean valid, String issue) throws IOException {
        if (!valid) {
            throw new IOException(issue);
        }
    }

    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String version() throws IOException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Slimefun");
        require(plugin != null && plugin.isEnabled(), "Published Slimefun was not enabled");
        return plugin.getDescription().getVersion();
    }

    private static ItemStack fresh(String id) throws Exception {
        Class<?> cls = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
        Object entry = cls.getMethod("getById", String.class).invoke(null, id);
        if (entry == null) {
            return null;
        }
        Object item = entry.getClass().getMethod("getItem").invoke(entry);
        require(item instanceof ItemStack, "Non-Bukkit registered item: " + id);
        ItemStack result = (ItemStack) item;
        byte[] bytes = result.serializeAsBytes();
        require(bytes.length > 0 && bytes.length <= 256 * 1024
                        && Arrays.equals(bytes, ItemStack.deserializeBytes(bytes).serializeAsBytes()),
                "Unstable native registered item: " + id);
        return ItemStack.deserializeBytes(bytes);
    }

    private static Inventory chest(boolean first) throws IOException {
        var block = Bukkit.getWorlds().getFirst().getBlockAt(8, 180, 8);
        if (first) {
            require(block.getType().isAir(), "Historical test-only chest site is occupied");
            block.setType(Material.CHEST);
        }
        require(block.getType() == Material.CHEST && block.getState() instanceof Chest,
                "Independent JVM did not reopen test-only physical chest");
        Inventory inv = ((Chest) block.getState()).getBlockInventory();
        require(inv.getSize() == 27, "Historical handoff requires single chest");
        return inv;
    }

    private void seed() throws Exception {
        require(version().contains("4.1.55"), "First JVM did not load published v4.1.55");
        Path data = folder();
        require(!Files.exists(data.resolve("original.tsv")), "Original receipt already exists");
        Inventory inv = chest(true);
        StringBuilder receipt = new StringBuilder("v4.1.55\n");
        int count = 0;
        for (int i = 0; i < IDS.length; i++) {
            ItemStack old = fresh(IDS[i]);
            if (old == null) {
                require(i > 1, "Missing mandatory historical registered ID: " + IDS[i]);
                receipt.append(IDS[i]).append("\tABSENT\n");
                continue;
            }
            count++;
            old.setAmount(AMOUNTS[i]);
            byte[] bytes = old.serializeAsBytes();
            inv.setItem(i, old);
            require(inv.getItem(i) != null
                            && Arrays.equals(bytes, inv.getItem(i).serializeAsBytes()),
                    "Old registered stack failed to seed physical chest");
            Files.write(data.resolve(IDS[i] + ".bin"), bytes);
            receipt.append(IDS[i]).append("\t").append(i).append("\t")
                    .append(AMOUNTS[i]).append("\t").append(hash(bytes)).append("\n");
        }
        require(count >= 2, "Insufficient published historical source items");
        inv.setItem(26, new ItemStack(Material.STONE, 17));
        Files.writeString(data.resolve("original.tsv"), receipt.toString());
        Files.writeString(data.resolve("seed.pass"), "PASS published v4.1.55 original world chest\n");
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plainLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        return lore == null ? List.of() : lore.stream().map(HistoricalCoreProbe::plain).toList();
    }

    private static boolean customModelSame(ItemMeta oldMeta, ItemMeta newMeta) {
        return oldMeta.hasCustomModelData() == newMeta.hasCustomModelData()
                && (!oldMeta.hasCustomModelData()
                        || oldMeta.getCustomModelData() == newMeta.getCustomModelData());
    }

    /**
     * Exact complete native-byte comparison after removing only the two
     * presentation fields from detached, amount-normalized stacks. This
     * retains every typed PDC key/value, material/model, skull, enchantment,
     * future component and opaque field in the resulting native witness.
     */
    private static boolean sameNativeNonPresentation(ItemStack left, ItemStack right) {
        ItemStack a = ItemStack.deserializeBytes(left.serializeAsBytes());
        ItemStack b = ItemStack.deserializeBytes(right.serializeAsBytes());
        a.setAmount(1);
        b.setAmount(1);
        a.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        b.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        return Arrays.equals(a.serializeAsBytes(), b.serializeAsBytes());
    }

    /**
     * The only preview edit targets a detached copy of the genuine persisted
     * old source item. The original world chest and private receipt are never
     * written by this comparison.
     */
    private static ItemStack previewPresentationOnly(ItemStack old, ItemStack current) {
        ItemStack preview = ItemStack.deserializeBytes(old.serializeAsBytes());
        ItemMeta referenceMeta = current.getItemMeta();
        if (referenceMeta == null) {
            throw new IllegalStateException("Registered current template has no metadata");
        }
        preview.editMeta(meta -> {
            meta.displayName(referenceMeta.displayName());
            meta.lore(referenceMeta.lore());
        });
        return preview;
    }

    private void verify() throws Exception {
        require(version().contains("4.1.81"), "Second JVM did not load published v4.1.81");
        Path data = folder();
        String[] lines = Files.readString(data.resolve("original.tsv")).trim().split("\n");
        require(lines.length == IDS.length + 1 && lines[0].equals("v4.1.55"),
                "Historical source manifest missing or changed");
        Inventory inv = chest(false);
        require(inv.getItem(26) != null && inv.getItem(26).getType() == Material.STONE
                        && inv.getItem(26).getAmount() == 17,
                "Vanilla world stock changed between genuine released cores");
        int count = 0;
        StringBuilder report = new StringBuilder(
                "id\told_sha256\tfresh_sha256\tis_similar\tmaterial_same\tquantity"
                        + "\tname_component_same\tlore_component_same\tplain_name_same"
                        + "\tplain_lore_same\tlegacy_name_same\tlegacy_lore_same"
                        + "\tpdc_key_set_same\tcustom_model_data_same\titem_model_same"
                        + "\tenchantments_same\told_lore_lines\tnew_lore_lines"
                        + "\told_fresh_nonpresentation_native_exact"
                        + "\tpreview_matches_current_is_similar"
                        + "\tpreview_preserves_original_nonpresentation"
                        + "\tpreview_matches_current_nonpresentation_native"
                        + "\tpreview_same_original_quantity"
                        + "\ttyped_pdc_negative_refused\n");
        for (int i = 0; i < IDS.length; i++) {
            String id = IDS[i];
            if (lines[i + 1].equals(id + "\tABSENT")) {
                require(inv.getItem(i) == null, "Missing v55 item was created unexpectedly");
                continue;
            }
            String[] fields = lines[i + 1].split("\t", -1);
            require(fields.length == 4 && fields[0].equals(id)
                            && Integer.parseInt(fields[1]) == i
                            && Integer.parseInt(fields[2]) == AMOUNTS[i],
                    "Invalid historical item manifest row");
            byte[] original = Files.readAllBytes(data.resolve(id + ".bin"));
            require(hash(original).equals(fields[3]), "Old native receipt digest changed");
            ItemStack stored = inv.getItem(i);
            require(stored != null && stored.getAmount() == AMOUNTS[i]
                            && Arrays.equals(original, stored.serializeAsBytes()),
                    "Real historical world stack changed across core upgrade");
            ItemStack current = fresh(id);
            require(current != null, "New version lost historical registered ID: " + id);
            count++;
            ItemMeta oldMeta = stored.getItemMeta();
            ItemMeta newMeta = current.getItemMeta();
            require(oldMeta != null && newMeta != null, "Missing old or current item metadata");
            List<Component> oldLore = oldMeta.lore();
            List<Component> freshLore = newMeta.lore();

            ItemStack detached = previewPresentationOnly(stored, current);
            boolean sourceFunctionalExact = sameNativeNonPresentation(stored, current);
            boolean detachedSimilar = detached.isSimilar(current);
            boolean detachedPreserves = sameNativeNonPresentation(stored, detached);
            boolean detachedFunctionalExact = sameNativeNonPresentation(detached, current);
            boolean quantityConserved = detached.getAmount() == AMOUNTS[i];
            require(quantityConserved && detachedPreserves
                            && Arrays.equals(original, inv.getItem(i).serializeAsBytes()),
                    "Detached style-only preview changed source functional state or chest: " + id);

            // Safety negative: same presentation and same base item, but a
            // *typed* owner value changes. The native non-presentation guard
            // must reject that even if its numeric value looks equal.
            NamespacedKey syntheticOwner = new NamespacedKey(
                    "sfl_historic_probe", "typed_owner");
            ItemStack longOwner = ItemStack.deserializeBytes(current.serializeAsBytes());
            ItemStack intOwner = ItemStack.deserializeBytes(current.serializeAsBytes());
            longOwner.editMeta(meta -> meta.getPersistentDataContainer()
                    .set(syntheticOwner, PersistentDataType.LONG, 42L));
            intOwner.editMeta(meta -> meta.getPersistentDataContainer()
                    .set(syntheticOwner, PersistentDataType.INTEGER, 42));
            boolean typedRejected = !sameNativeNonPresentation(longOwner, intOwner)
                    && !longOwner.isSimilar(intOwner);
            require(typedRejected, "Typed owner-PDC mismatch was not rejected: " + id);

            report.append(id).append("\t").append(hash(original)).append("\t")
                    .append(hash(current.serializeAsBytes())).append("\t")
                    .append(stored.isSimilar(current)).append("\t")
                    .append(stored.getType() == current.getType()).append("\t")
                    .append(AMOUNTS[i]).append("\t")
                    .append(Objects.equals(oldMeta.displayName(), newMeta.displayName())).append("\t")
                    .append(Objects.equals(oldLore, freshLore)).append("\t")
                    .append(plain(oldMeta.displayName()).equals(plain(newMeta.displayName()))).append("\t")
                    .append(plainLore(oldMeta).equals(plainLore(newMeta))).append("\t")
                    .append(Objects.equals(oldMeta.getDisplayName(), newMeta.getDisplayName())).append("\t")
                    .append(Objects.equals(oldMeta.getLore(), newMeta.getLore())).append("\t")
                    .append(oldMeta.getPersistentDataContainer().getKeys()
                            .equals(newMeta.getPersistentDataContainer().getKeys())).append("\t")
                    .append(customModelSame(oldMeta, newMeta)).append("\t")
                    .append(Objects.equals(oldMeta.getItemModel(), newMeta.getItemModel())).append("\t")
                    .append(oldMeta.getEnchants().equals(newMeta.getEnchants())).append("\t")
                    .append(oldLore == null ? -1 : oldLore.size()).append("\t")
                    .append(freshLore == null ? -1 : freshLore.size()).append("\t")
                    .append(sourceFunctionalExact).append("\t")
                    .append(detachedSimilar).append("\t")
                    .append(detachedPreserves).append("\t")
                    .append(detachedFunctionalExact).append("\t")
                    .append(quantityConserved).append("\t")
                    .append(typedRejected).append("\n");
        }
        require(count >= 2, "Not enough source-to-current item pairs");
        for (int i = IDS.length; i < 26; i++) {
            require(inv.getItem(i) == null, "Unrelated empty physical chest slot changed");
        }
        Files.writeString(data.resolve("comparison.tsv"), report.toString());
        Files.writeString(data.resolve("verify.pass"), "PASS published old/new JVM native world conservation\n");
    }
    /**
     * THIRD separate JVM with an unreleased exact-PR Doctor core, after
     * genuinely registered 4.1.55 source and published 4.1.81 observation.
     *
     * <p>Run the real production historical repair classifier on detached
     * old/current pairs only; the original loaded physical chest is never
     * modified, even when the candidate would pass.
     */
    private void candidate() throws Exception {
        require(version().contains("4.1.81"), "Candidate Doctor is not built on the expected version floor");
        Path data = folder();
        require(Files.isRegularFile(data.resolve("seed.pass"))
                        && Files.isRegularFile(data.resolve("verify.pass")),
                "Third-boot Doctor gate requires both actual published-core JVM evidence receipts");
        Inventory inventory = chest(false);
        require(inventory.getSize() == 27
                        && inventory.getItem(26) != null
                        && inventory.getItem(26).getType() == Material.STONE
                        && inventory.getItem(26).getAmount() == 17,
                "Real historic chest or unrelated vanilla stock changed before candidate Doctor");
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method prepare = doctor.getDeclaredMethod(
                "prepare", ItemStack.class, ItemStack.class, ItemStack.class);
        prepare.setAccessible(true);
        Method backup = doctor.getDeclaredMethod(
                "matchesSavedBackup", ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
        backup.setAccessible(true);

        StringBuilder report = new StringBuilder(
                "id\tprepare_accepted\tcandidate_similar\tfunctional_original_preserved"
                        + "\tfunctional_fresh_equal\tquantity_same\tverified_backup_match"
                        + "\ttyped_extra_pdc_rejected\tcross_id_same_material_rejected\n");
        for (int i = 0; i < IDS.length; i++) {
            String id = IDS[i];
            Path originalFile = data.resolve(id + ".bin");
            if (!Files.isRegularFile(originalFile)) {
                require(inventory.getItem(i) == null, "Missing historic source unexpectedly appeared: " + id);
                continue;
            }
            byte[] originalBytes = Files.readAllBytes(originalFile);
            ItemStack old = inventory.getItem(i);
            require(old != null && old.getAmount() == AMOUNTS[i]
                            && Arrays.equals(old.serializeAsBytes(), originalBytes),
                    "Original historic item bytes changed on third candidate JVM: " + id);
            ItemStack fresh = fresh(id);
            require(fresh != null && !old.isSimilar(fresh),
                    "Candidate did not reproduce old-vs-current mismatch: " + id);
            byte[] referenceBytes = fresh.serializeAsBytes();

            // Actual unmodified Slimefun Doctor implementation under test.
            Object result = prepare.invoke(null, old, fresh, fresh);
            boolean accepted = result instanceof ItemStack;
            ItemStack preview = accepted ? (ItemStack) result : null;
            boolean compatible = accepted && preview.isSimilar(fresh);
            boolean preserved = accepted && sameNativeNonPresentation(old, preview);
            boolean equivalent = accepted && sameNativeNonPresentation(preview, fresh);
            boolean conserved = accepted && preview.getAmount() == AMOUNTS[i];
            boolean backedUp = accepted && Boolean.TRUE.equals(
                    backup.invoke(null, old, preview, fresh, fresh));

            ItemStack wrongTyped = ItemStack.deserializeBytes(originalBytes);
            wrongTyped.editMeta(meta -> meta.getPersistentDataContainer().set(
                    new NamespacedKey("sfl_historic_probe", "typed_owner"),
                    PersistentDataType.INTEGER, 42));
            boolean typedRefused = prepare.invoke(null, wrongTyped, fresh, fresh) == null;

            boolean wrongIdRefused = true;
            if (id.equals("CARBON_CHUNK")) {
                ItemStack other = fresh("RAW_CARBONADO");
                require(other != null && other.getType() == fresh.getType(),
                        "Real cross-ID negative must use the SAME Bukkit material");
                wrongIdRefused = prepare.invoke(null, old, other, other) == null;
            }

            require(accepted && compatible && preserved && equivalent
                            && conserved && backedUp && typedRefused && wrongIdRefused,
                    "Current Doctor rejected safe authentic v55 source or unsafe controls: " + id
                            + " [accepted=" + accepted + ",compatible=" + compatible
                            + ",preserved=" + preserved + ",equivalent=" + equivalent
                            + ",conserved=" + conserved + ",backup=" + backedUp
                            + ",typed=" + typedRefused + ",crossId=" + wrongIdRefused + "]");
            require(Arrays.equals(inventory.getItem(i).serializeAsBytes(), originalBytes)
                            && Arrays.equals(fresh.serializeAsBytes(), referenceBytes),
                    "Core Doctor mutated original chest or registered template: " + id);
            report.append(id).append("\t").append(accepted).append("\t")
                    .append(compatible).append("\t").append(preserved).append("\t")
                    .append(equivalent).append("\t").append(conserved).append("\t")
                    .append(backedUp).append("\t").append(typedRefused).append("\t")
                    .append(wrongIdRefused).append("\n");
        }
        for (int i = IDS.length; i < 26; i++) {
            require(inventory.getItem(i) == null,
                    "Doctor candidate caused an unrelated empty physical chest slot to change");
        }
        Files.writeString(data.resolve("candidate-comparison.tsv"), report.toString());
        verifyActualLiveBatchInMemory(data, inventory);
        Files.writeString(data.resolve("candidate.pass"),
                "PASS actual published v55 source against candidate Doctor detached repairs, world unchanged\n");
    }

    /**
     * Real v55 world items -> exact candidate's production batch engine.
     * The INVENTORY is new disposable Bukkit memory, not a world/player/shop
     * inventory. The real persisted world chest is only ever read from.
     * The registeredTemplate resolver is the real plugin method (not an
     * injected test shortcut); source IDs and PDC are guarded in production.
     */
    private void verifyActualLiveBatchInMemory(Path data, Inventory originalChest) throws Exception {
        Class<?> live = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Method registry = live.getDeclaredMethod("registeredTemplate", ItemStack.class);
        registry.setAccessible(true);
        Function<ItemStack, ItemStack> resolver = observed -> {
            try {
                return (ItemStack) registry.invoke(null, observed);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Production historical registry lookup failed", error);
            }
        };

        Inventory disposable = Bukkit.createInventory(null, 36);
        int beforeItems = 0;
        int accepted = 0;
        for (int index = 0; index < IDS.length; index++) {
            Path source = data.resolve(IDS[index] + ".bin");
            if (!Files.isRegularFile(source)) {
                continue;
            }
            ItemStack actual = ItemStack.deserializeBytes(Files.readAllBytes(source));
            require(originalChest.getItem(index) != null
                            && Arrays.equals(actual.serializeAsBytes(),
                                    originalChest.getItem(index).serializeAsBytes()),
                    "Genuine v55 physical chest does not match source: " + IDS[index]);
            ItemStack registered = fresh(IDS[index]);
            require(registered != null && !actual.isSimilar(registered),
                    "Actual historical item is not an old/current mismatch: " + IDS[index]);
            require(resolver.apply(actual) != null,
                    "Production live-batch registry resolver rejected genuine old source: " + IDS[index]);
            disposable.setItem(index, actual);
            disposable.setItem(10 + index, registered);
            beforeItems += actual.getAmount() + registered.getAmount();
            accepted++;
        }
        require(accepted == IDS.length, "Expected all four actual public historical core items");
        // Identical old ID/text/material, but extra typed ownership PDC is
        // functional data and MUST NOT be replaced.
        ItemStack owned = ItemStack.deserializeBytes(
                Files.readAllBytes(data.resolve("CARBON_CHUNK.bin")));
        owned.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("sfl_historic_probe", "typed_owner"),
                PersistentDataType.INTEGER, 787001));
        byte[] ownedNative = owned.serializeAsBytes();
        disposable.setItem(20, owned);
        disposable.setItem(35, new ItemStack(Material.STONE, 17));
        beforeItems += owned.getAmount() + 17;

        Method preview = live.getDeclaredMethod(
                "preview", Inventory.class, Function.class);
        preview.setAccessible(true);
        Object plan = preview.invoke(null, disposable, resolver);
        Method eligible = plan.getClass().getDeclaredMethod("eligible");
        eligible.setAccessible(true);
        Method entries = plan.getClass().getDeclaredMethod("entries");
        entries.setAccessible(true);
        int allowed = (int) eligible.invoke(plan);
        List<?> candidates = (List<?>) entries.invoke(plan);
        require(allowed == IDS.length && candidates.size() == IDS.length,
                "Production batch did not find exactly four genuine historical items: found " + allowed);
        Method planSlot = candidates.getFirst().getClass().getDeclaredMethod("slot");
        planSlot.setAccessible(true);
        for (int index = 0; index < candidates.size(); index++) {
            require((int) planSlot.invoke(candidates.get(index)) == index,
                    "Unexpected live update slot or typed-PDC item entered plan");
        }

        UUID owner = UUID.randomUUID();
        Path privateOriginals = data.resolve("candidate-batch-receipts");
        Method apply = live.getDeclaredMethod(
                "apply", Inventory.class, plan.getClass(), Function.class, Path.class, UUID.class);
        apply.setAccessible(true);
        List<?> receipts = (List<?>) apply.invoke(
                null, disposable, plan, resolver, privateOriginals, owner);
        require(receipts.size() == IDS.length, "Production live batch did not produce four private receipts");
        Method receiptSlot = receipts.getFirst().getClass().getDeclaredMethod("slot");
        receiptSlot.setAccessible(true);
        Method receiptId = receipts.getFirst().getClass().getDeclaredMethod("id");
        receiptId.setAccessible(true);
        Class<?> repair = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method restore = repair.getDeclaredMethod(
                "readVerifiedBackup", Path.class, UUID.class, UUID.class);
        restore.setAccessible(true);
        Method savedMatch = repair.getDeclaredMethod(
                "matchesSavedBackup", ItemStack.class, ItemStack.class,
                ItemStack.class, ItemStack.class);
        savedMatch.setAccessible(true);

        int afterItems = 0;
        HashSet<UUID> uniqueReceipts = new HashSet<>();
        StringBuilder receiptIndex = new StringBuilder(owner + "\n");
        for (int index = 0; index < IDS.length; index++) {
            require((int) receiptSlot.invoke(receipts.get(index)) == index,
                    "Live batch receipt bound to wrong slot");
            UUID receipt = (UUID) receiptId.invoke(receipts.get(index));
            require(uniqueReceipts.add(receipt), "Live batch generated repeated private receipt UUID");
            ItemStack updated = disposable.getItem(index);
            ItemStack current = fresh(IDS[index]);
            ItemStack saved = (ItemStack) restore.invoke(
                    null, privateOriginals, owner, receipt);
            byte[] originalBytes = Files.readAllBytes(data.resolve(IDS[index] + ".bin"));
            require(updated != null
                            && updated.getAmount() == AMOUNTS[index]
                            && updated.isSimilar(current)
                            && Arrays.equals(saved.serializeAsBytes(), originalBytes)
                            && sameNativeNonPresentation(saved, updated)
                            && Boolean.TRUE.equals(savedMatch.invoke(
                                    null, saved, updated, current, current)),
                    "Actual production live-batch update/backup failed: " + IDS[index]);
            Files.write(data.resolve("candidate-updated-" + IDS[index] + ".bin"),
                    updated.serializeAsBytes());
            receiptIndex.append(IDS[index]).append("\t").append(receipt).append("\n");
            afterItems += updated.getAmount()
                    + disposable.getItem(10 + index).getAmount();
            require(Arrays.equals(originalBytes, originalChest.getItem(index).serializeAsBytes()),
                    "Batch wrote to immutable genuine v55 world chest");
        }
        require(Arrays.equals(disposable.getItem(20).serializeAsBytes(), ownedNative),
                "Production batch modified typed owner-PDC negative");
        require(disposable.getItem(35).getAmount() == 17,
                "Production batch changed unrelated vanilla inventory stock");
        afterItems += disposable.getItem(20).getAmount() + disposable.getItem(35).getAmount();
        require(beforeItems == afterItems,
                "Actual historical multi-item live batch duplicated/lost inventory quantities");
        Files.writeString(data.resolve("candidate-batch-receipt-index.txt"),
                receiptIndex.toString());
        getLogger().info("SFL_HISTORICAL_CORE_LIVE_BATCH_PASS eligible=" + allowed
                + " backups=" + uniqueReceipts.size()
                + " native_quantity=" + afterItems + " typed_pdc_rejected=true");
    }

    /**
     * Fourth, independent Java server process. Verify source world chest, all
     * private native original-byte backups and produced after-images remain
     * intact after a clean restart. Do not write to the chest at any phase.
     */
    private void replay() throws Exception {
        require(version().contains("4.1.81"), "Fourth JVM did not load candidate Doctor");
        Path data = folder();
        require(Files.isRegularFile(data.resolve("candidate.pass")),
                "Fourth JVM missing candidate native validation");
        Inventory chest = chest(false);
        require(chest.getItem(26) != null
                        && chest.getItem(26).getType() == Material.STONE
                        && chest.getItem(26).getAmount() == 17,
                "Fourth JVM physical-world vanilla stock changed");
        String[] receiptLines = Files.readString(
                data.resolve("candidate-batch-receipt-index.txt")).trim().split("\n");
        require(receiptLines.length == IDS.length + 1,
                "Fourth JVM did not reopen all four private batch receipts");
        UUID owner = UUID.fromString(receiptLines[0]);
        Path backups = data.resolve("candidate-batch-receipts");
        Class<?> repair = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method restore = repair.getDeclaredMethod(
                "readVerifiedBackup", Path.class, UUID.class, UUID.class);
        restore.setAccessible(true);
        Method matches = repair.getDeclaredMethod(
                "matchesSavedBackup", ItemStack.class, ItemStack.class,
                ItemStack.class, ItemStack.class);
        matches.setAccessible(true);
        HashSet<UUID> receipts = new HashSet<>();
        int expectedTotal = 0;
        for (int index = 0; index < IDS.length; index++) {
            String[] entry = receiptLines[index + 1].split("\t", -1);
            require(entry.length == 2 && entry[0].equals(IDS[index]),
                    "Fourth JVM receipt/source ID mismatch");
            UUID receipt = UUID.fromString(entry[1]);
            require(receipts.add(receipt), "Fourth JVM private receipt UUID repeated");
            byte[] original = Files.readAllBytes(data.resolve(IDS[index] + ".bin"));
            ItemStack stored = chest.getItem(index);
            require(stored != null
                            && stored.getAmount() == AMOUNTS[index]
                            && Arrays.equals(stored.serializeAsBytes(), original),
                    "Fourth JVM changed historical world source stock " + IDS[index]);
            ItemStack backup = (ItemStack) restore.invoke(null, backups, owner, receipt);
            ItemStack updated = ItemStack.deserializeBytes(Files.readAllBytes(
                    data.resolve("candidate-updated-" + IDS[index] + ".bin")));
            ItemStack current = fresh(IDS[index]);
            require(Arrays.equals(backup.serializeAsBytes(), original)
                            && updated.isSimilar(current)
                            && updated.getAmount() == backup.getAmount()
                            && sameNativeNonPresentation(backup, updated)
                            && Boolean.TRUE.equals(matches.invoke(
                                    null, backup, updated, current, current)),
                    "Fourth JVM lost typed item, canonical output or private original receipt: " + IDS[index]);
            expectedTotal += updated.getAmount();
        }
        require(expectedTotal == 90, "Fourth JVM quantity conservation regression");
        for (int index = IDS.length; index < 26; index++) {
            require(chest.getItem(index) == null,
                    "Fourth JVM gained an unrelated item in historical physical chest");
        }
        Files.writeString(data.resolve("replay.pass"),
                "PASS four real-JVM historical live-batch exact original receipts and identity\n");
        getLogger().info("SFL_HISTORICAL_CORE_LIVE_BATCH_REPLAY_PASS four_receipts=4"
                + " quantities=" + expectedTotal + " physical_chest_unchanged=true");
    }


}
