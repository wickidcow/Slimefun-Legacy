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
                || !(args[0].equals("seed") || args[0].equals("verify") || args[0].equals("candidate"))) {
            sender.sendMessage("sflhistoricprobe seed|verify|candidate");
            return true;
        }
        String phase = args[0];
        try {
            if (phase.equals("seed")) {
                seed();
            } else if (phase.equals("verify")) {
                verify();
            } else {
                candidate();
            }
            getLogger().info("SFL_HISTORICAL_CORE_" + phase.toUpperCase() + "_PASS");
        } catch (Exception | LinkageError error) {
            try {
                Files.writeString(folder().resolve(phase + ".fail"),
                        error.getClass().getSimpleName() + ": " + error.getMessage());
            } catch (IOException ignored) {
                // The Python native harness still fails on a missing PASS marker.
            }
            getLogger().severe("SFL_HISTORICAL_CORE_FAIL " + phase + ": " + error);
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
        Files.writeString(data.resolve("candidate.pass"),
                "PASS actual published v55 source against candidate Doctor detached repairs, world unchanged\n");
    }

}
