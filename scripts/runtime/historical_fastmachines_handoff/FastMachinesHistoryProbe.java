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
 * True old saved FastMachines addon item sources, not synthetic old text.
 * Only writes a disposable world chest on seed with the published 1.0.4
 * plugin. The candidate and replay boot NEVER write the world chest.
 */
public final class FastMachinesHistoryProbe extends JavaPlugin {
    private static final NamespacedKey ID = new NamespacedKey("slimefun", "slimefun_item");
    private static final NamespacedKey OWNER = new NamespacedKey("fastmachines_history_probe", "owner");
    private static final int MAX_ITEMS = 24;
    private static final int CHEST_SLOTS = 27;
    private static final int STONE_SLOT = 26;

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("sflfastmachineshistory")).setExecutor(this::command);
    }

    private boolean command(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !(args[0].equals("seed") || args[0].equals("candidate")
                || args[0].equals("replay"))) {
            sender.sendMessage("sflfastmachineshistory seed|candidate|replay");
            return true;
        }
        try {
            switch (args[0]) {
                case "seed" -> seed();
                case "candidate" -> compare(false);
                case "replay" -> compare(true);
                default -> throw new IllegalArgumentException("unknown command");
            }
            Files.writeString(data().resolve(args[0] + ".pass"), "PASS " + args[0] + "\n");
            getLogger().info("SFL_TRUE_FASTMACHINES_" + args[0].toUpperCase() + "_PASS");
        } catch (Exception | LinkageError failure) {
            Throwable cause = failure;
            for (int i = 0; i < 6 && cause instanceof java.lang.reflect.InvocationTargetException; i++) {
                Throwable next = ((java.lang.reflect.InvocationTargetException) cause).getTargetException();
                if (next == null || next == cause) break;
                cause = next;
            }
            String safe = cause.getClass().getSimpleName();
            getLogger().severe("SFL_TRUE_FASTMACHINES_FAIL " + args[0] + " type=" + safe);
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

    private static void check(boolean success, String description) {
        if (!success) throw new IllegalStateException(description);
    }

    private static Inventory physicalChest(boolean create) {
        World world = Bukkit.getWorlds().getFirst();
        Block block = world.getBlockAt(8, 71, 8);
        block.getChunk().load();
        if (create) {
            check(block.getType().isAir(), "Disposable source world location already occupied");
            block.setType(Material.CHEST);
        }
        check(block.getState() instanceof Chest, "Published source physical chest is missing");
        Inventory inventory = ((Chest) block.getState()).getBlockInventory();
        check(inventory.getSize() == CHEST_SLOTS, "Expected single 27-slot world chest");
        return inventory;
    }

    private static List<SlimefunItem> registered() {
        return Slimefun.getRegistry().getAllSlimefunItems().stream()
                .filter(item -> "FastMachines".equals(item.getAddon().getName()))
                .sorted(Comparator.comparing(SlimefunItem::getId))
                .toList();
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
        check(items.size() >= 12 && items.size() <= MAX_ITEMS,
                "Published FastMachines v1.0.4 registered item count outside expected 12..24");
        Inventory chest = physicalChest(true);
        check(Arrays.stream(chest.getContents()).allMatch(item -> item == null || item.getType().isAir()),
                "Disposable world chest unexpectedly contains prior stock");
        List<String> ids = new ArrayList<>();
        long total = 0;
        for (int slot = 0; slot < items.size(); slot++) {
            SlimefunItem item = items.get(slot);
            String id = item.getId();
            ItemStack source = nativeItem(item.getItem());
            check(id.equals(itemId(source)), "Published FastMachines item ID absent: " + id);
            int amount = Math.min(3, source.getMaxStackSize());
            check(amount > 0, "Invalid published FastMachines item capacity: " + id);
            source.setAmount(amount);
            byte[] bytes = source.serializeAsBytes();
            chest.setItem(slot, source);
            check(Arrays.equals(bytes, chest.getItem(slot).serializeAsBytes()),
                    "Original FastMachines world write failed readback: " + id);
            Files.write(origins.resolve(slot + ".bin"), bytes);
            ids.add(id);
            total += amount;
        }
        chest.setItem(STONE_SLOT, new ItemStack(Material.STONE, 17));
        check(chest.getItem(STONE_SLOT).getAmount() == 17, "Vanilla control stock missing");
        Files.writeString(dir.resolve("original-item-ids.txt"), String.join("\n", ids) + "\n");
        Files.writeString(dir.resolve("original-count.txt"), String.valueOf(total) + "\n");
        getLogger().info("SFL_TRUE_FASTMACHINES_SEED items=" + ids.size() + " total=" + total);
    }

    private void assertOriginalChest(List<String> ids) throws Exception {
        Inventory inventory = physicalChest(false);
        for (int slot = 0; slot < CHEST_SLOTS; slot++) {
            ItemStack live = inventory.getItem(slot);
            if (slot < ids.size()) {
                byte[] original = Files.readAllBytes(data().resolve("private-originals").resolve(slot + ".bin"));
                check(live != null && Arrays.equals(original, live.serializeAsBytes())
                                && ids.get(slot).equals(itemId(live)),
                        "True saved FastMachines source changed in world slot " + slot);
            } else if (slot == STONE_SLOT) {
                check(live != null && live.getType() == Material.STONE && live.getAmount() == 17,
                        "Unrelated vanilla stock changed after restart");
            } else {
                check(live == null || live.getType().isAir(),
                        "Unexpected physical chest stock inserted");
            }
        }
    }

    private void compare(boolean replay) throws Exception {
        Path base = data();
        List<String> ids = Files.readAllLines(base.resolve("original-item-ids.txt"));
        check(ids.size() >= 12 && ids.size() <= MAX_ITEMS
                        && ids.stream().distinct().count() == ids.size(),
                "Private historical source manifest malformed");
        var all = registered().stream().collect(Collectors.toMap(SlimefunItem::getId, item -> item));
        check(all.size() >= ids.size(), "New FastMachines version silently lost source registrations");
        for (String id : ids) check(all.containsKey(id), "Published addon removed saved item ID " + id);
        assertOriginalChest(ids);

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
        Inventory chest = physicalChest(false);
        StringBuilder report = new StringBuilder("item_id\tclassification\n");
        long currentTotal = 0;
        int exact = 0, eligible = 0, refused = 0;
        for (int slot = 0; slot < ids.size(); slot++) {
            String id = ids.get(slot);
            ItemStack old = nativeItem(chest.getItem(slot));
            byte[] original = Files.readAllBytes(base.resolve("private-originals").resolve(slot + ".bin"));
            check(Arrays.equals(original, old.serializeAsBytes()),
                    "Old FastMachines item changed before comparison: " + id);
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
                        "Doctor tried to rewrite an identical FastMachines item " + id);
            } else if (old.isSimilar(fresh)) {
                // Bukkit isSimilar can ignore future opaque components.
                classification = "SIMILAR_NATIVE_DRIFT";
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
                            "Doctor changed true historic FastMachines functional item " + id);
                    ItemStack attacker = nativeItem(old);
                    attacker.editMeta(meta -> meta.getPersistentDataContainer().set(
                            OWNER, PersistentDataType.LONG, 114911L));
                    check(prepare.invoke(null, attacker, fresh, resolved) == null,
                            "Doctor accepted typed-owner FastMachines item " + id);
                    ItemStack custom = nativeItem(old);
                    custom.editMeta(meta -> meta.displayName(Component.text("Player custom item")));
                    check(prepare.invoke(null, custom, fresh, resolved) == null,
                            "Doctor accepted meaningful renamed FastMachines item " + id);
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
            check(Arrays.equals(original, old.serializeAsBytes())
                            && Arrays.equals(unchangedTemplate, template.serializeAsBytes()),
                    "Candidate Doctor or addon test modified historical source or global template: " + id);
            currentTotal += old.getAmount();
            report.append(id).append('\t').append(classification).append('\n');
        }
        check(currentTotal == Long.parseLong(Files.readString(base.resolve("original-count.txt")).trim()),
                "FastMachines original saved quantities changed across runtime boot");
        check(exact + eligible + refused == ids.size(), "Historical FastMachines count partition failed");
        Path reportFile = base.resolve("classification.tsv");
        if (replay) {
            check(Files.readString(reportFile).equals(report.toString()),
                    "FastMachines current Doctor eligibility classifications changed over restart");
        } else {
            Files.writeString(reportFile, report.toString());
        }
        assertOriginalChest(ids);
        getLogger().info("SFL_TRUE_FASTMACHINES_DIAGNOSTIC_" + (replay ? "RESTART" : "CANDIDATE")
                + "_PASS source_items=" + ids.size() + " exact=" + exact + " strict_repairable="
                + eligible + " refused=" + refused + " quantity=" + currentTotal);
    }
}
