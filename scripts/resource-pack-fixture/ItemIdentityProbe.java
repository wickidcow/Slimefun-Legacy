package io.github.wickidcow.validation;

import com.google.gson.GsonBuilder;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Native identity acceptance in a disposable server; synthetic repair is not proof of live historical data. */
final class ItemIdentityProbe {
    private static final NamespacedKey OWNER = new NamespacedKey("identity_probe", "owner");

    private ItemIdentityProbe() {}

    static void verifyOutputRestart(Path folder) throws Exception {
        // Missing or unreadable captured outputs must fail, never fall back to registered templates.
        byte[] craftedBytes = Files.readAllBytes(folder.resolve("identity-crafted-wire.bin"));
        byte[] givenBytes = Files.readAllBytes(folder.resolve("identity-given-wire.bin"));
        ItemStack crafted = ItemStack.deserializeBytes(craftedBytes);
        ItemStack given = ItemStack.deserializeBytes(givenBytes);
        var registered = SlimefunItem.getById("COPPER_WIRE");
        require(registered != null, "missing wire registration after restart");
        ItemStack template = registered.getItem();
        byte[] before = template.serializeAsBytes();
        require(crafted.getAmount() == 8 && given.getAmount() == 8, "persisted actual output quantities");
        require(crafted.isSimilar(given) && given.isSimilar(crafted), "persisted craft/give identity");
        require(template.isSimilar(crafted) && crafted.isSimilar(template)
                        && template.getItemMeta().equals(crafted.getItemMeta()),
                "persisted crafted output differs from current template");
        require(flag(inspect(crafted, given), "isSimilar"), "doctor persisted producer identity");
        var inventory = Bukkit.createInventory(null, 9);
        require(inventory.addItem(crafted, given).isEmpty(), "persisted outputs overflowed");
        var stacks = Arrays.stream(inventory.getContents())
                .filter(item -> item != null && !item.getType().isAir()).toList();
        require(stacks.size() == 1 && stacks.getFirst().getAmount() == 16,
                "persisted actual outputs did not merge without loss/duplication");
        require(Arrays.equals(before, template.serializeAsBytes()), "restart mutated wire template");
        require(Arrays.equals(craftedBytes, Files.readAllBytes(folder.resolve("identity-crafted-wire.bin")))
                        && Arrays.equals(givenBytes, Files.readAllBytes(folder.resolve("identity-given-wire.bin"))),
                "restart verification rewrote saved producer evidence");
        verifyCapturedCompressorRestart(folder, "CARBON", "identity-produced-carbon.bin", 9);
        verifyCapturedCompressorRestart(
                folder, "COMPRESSED_CARBON", "identity-produced-compressed-carbon.bin", 1);
    }

    /**
     * Only genuine captured Compressor output slots from the connected-player handler.
     * Verify their typed metadata, exact quantities and vanilla stacking after a JVM restart.
     * No world, player, item, or saved capture is modified.
     */
    private static void verifyCapturedCompressorRestart(Path folder, String itemId, String filename, int amount)
            throws Exception {
        byte[] capturedBytes = Files.readAllBytes(folder.resolve(filename));
        ItemStack produced = ItemStack.deserializeBytes(capturedBytes);
        SlimefunItem registered = SlimefunItem.getById(itemId);
        require(registered != null, "missing registered Compressor result after restart: " + itemId);
        ItemStack template = registered.getItem();
        byte[] templateBefore = template.serializeAsBytes();
        require(
                produced.getAmount() == amount
                        && produced.isSimilar(template)
                        && template.isSimilar(produced)
                        && produced.getItemMeta().equals(template.getItemMeta()),
                "persisted Compressor output identity/quantity: " + itemId);
        ItemStack currentReference = ItemStack.deserializeBytes(templateBefore);
        currentReference.setAmount(amount);
        var inventory = Bukkit.createInventory(null, 9);
        require(
                inventory.addItem(produced, currentReference).isEmpty(),
                "persisted Compressor output merge overflow: " + itemId);
        var stacks = Arrays.stream(inventory.getContents())
                .filter(item -> item != null && !item.getType().isAir())
                .toList();
        require(
                stacks.size() == 1 && stacks.getFirst().getAmount() == amount * 2,
                "persisted Compressor output merge lost or duplicated items: " + itemId);
        require(
                Arrays.equals(templateBefore, template.serializeAsBytes())
                        && Arrays.equals(capturedBytes, Files.readAllBytes(folder.resolve(filename))),
                "Compressor restart validation modified template or captured item: " + itemId);
    }

    static void verify(Path folder, boolean restart) throws Exception {
        ItemStack reference = fixture();
        if (restart) {
            ItemStack persisted = ItemStack.deserializeBytes(Files.readAllBytes(folder.resolve("identity-item.bin")));
            require(reference.isSimilar(persisted) && persisted.getAmount() == 17, "saved item identity/quantity");
            require(flag(inspect(reference, persisted), "isSimilar"), "doctor saved item identity");
            verifyHistoricalPresentation(folder, true);
            return;
        }

        byte[] untouched = reference.serializeAsBytes();
        ItemStack noClone = new ItemStack(reference) {
            @Override
            public ItemStack clone() {
                throw new AssertionError("Diagnostic invoked subclass clone hook");
            }
        };
        require(flag(inspect(reference, noClone), "isSimilar"), "read without subclass clone hooks");
        ItemStack copy = reference.clone();
        copy.setAmount(31);
        Object equal = inspect(reference, copy);
        require(flag(equal, "isSimilar") && flag(equal, "customModelDataSame"), "quantity-independent clone");
        require(Arrays.equals(untouched, reference.serializeAsBytes()) && copy.getAmount() == 31,
                "diagnostic mutated input");
        require(reference.isSimilar(ItemStack.deserializeBytes(untouched)), "native component byte round trip");
        var inventory = Bukkit.createInventory(null, 9);
        require(inventory.addItem(reference.clone(), copy).isEmpty(), "native inventory capacity");
        require(inventory.getItem(0).getAmount() == 48 && inventory.getItem(1) == null, "native inventory merge");

        for (String field : List.of("floats", "flags", "strings", "colors")) {
            ItemStack changed = reference.clone();
            changed.editMeta(meta -> {
                var model = meta.getCustomModelDataComponent();
                switch (field) {
                    case "floats" -> model.setFloats(List.of(2200095.0F));
                    case "flags" -> model.setFlags(List.of(false));
                    case "strings" -> model.setStrings(List.of("other"));
                    case "colors" -> model.setColors(List.of(Color.BLUE));
                    default -> throw new IllegalArgumentException(field);
                }
                meta.setCustomModelDataComponent(model);
            });
            byte[] before = changed.serializeAsBytes();
            Object different = inspect(reference, changed);
            require(!reference.isSimilar(changed) && !flag(different, "isSimilar"), field + " native mismatch");
            require(!flag(different, "customModelDataSame"), field + " diagnostic mismatch");
            inventory.clear();
            require(inventory.addItem(reference.clone(), changed.clone()).isEmpty(), field + " inventory capacity");
            require(inventory.getItem(0).getAmount() == 17 && inventory.getItem(1).getAmount() == 17,
                    field + " mismatched items merged");
            require(Arrays.equals(before, changed.serializeAsBytes()), field + " input mutated");
        }

        ItemStack typed = reference.clone();
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.INTEGER, 77));
        Object pdc = inspect(reference, typed);
        require(flag(pdc, "pdcKeysSame") && !flag(pdc, "isSimilar") && !flag(pdc, "itemMetaSame"),
                "typed PDC distinction");
        ItemStack decorated = reference.clone();
        decorated.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, false)));
        require(!flag(inspect(reference, decorated), "displayNameSame"), "Adventure decoration distinction");

        SlimefunItem registered = SlimefunItem.getById("SMALL_URANIUM");
        require(registered != null, "registered SMALL_URANIUM missing");
        ItemStack template = registered.getItem();
        byte[] templateBefore = template.serializeAsBytes();
        require(flag(inspect(template, template.clone()), "isSimilar"), "registered template clone");
        // SlimefunItemStack is a registered subclass, not CraftItemStack.
        // Native Paper must classify a Craft held item against this raw template
        // without calling CraftItemStack.isSimilar(SlimefunItemStack).
        ItemStack historicAgainstRegistered = ItemStack.deserializeBytes(templateBefore);
        historicAgainstRegistered.editMeta(meta -> {
            var name = meta.displayName();
            if (name == null) throw new IllegalStateException("registered SMALL_URANIUM lacks display name");
            meta.displayName(name.decoration(TextDecoration.BOLD,
                    name.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
                            ? TextDecoration.State.FALSE
                            : TextDecoration.State.TRUE));
        });
        require(flag(historical(historicAgainstRegistered, template), "candidate"),
                "native historical classification rejected safe registered subclass template");
        // The server-wide scanner runs across real machine/storage inventories,
        // not only held stacks. Test a counted old CraftItemStack against the
        // live addon-owned registered SlimefunItemStack without clone hooks.
        historicAgainstRegistered.setAmount(37);
        require(
                "PRESENTATION_ONLY_CANDIDATE".equals(scanHistorical(historicAgainstRegistered, template)),
                "native read-only scanner rejected registered subclass / 37-count cosmetic candidate");
        ItemStack differentTypedPdc = ItemStack.deserializeBytes(historicAgainstRegistered.serializeAsBytes());
        differentTypedPdc.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 99L));
        require(
                "OTHER_DIFFERENCE".equals(scanHistorical(differentTypedPdc, template)),
                "native scanner mislabeled a typed-PDC mismatch as cosmetic");
        require(
                historicAgainstRegistered.getAmount() == 37 && template.getAmount() == 1,
                "native read-only scanner changed source/reference quantity");
        require(Arrays.equals(templateBefore, template.serializeAsBytes()), "registered template mutated");
        require(Arrays.equals(untouched, reference.serializeAsBytes()), "reference mutated by control cases");
        verifyHistoricalPresentation(folder, false);
        verifyRegisteredCloneIdentity(folder);
        Files.write(folder.resolve("identity-item.bin"), untouched);
        var items = Slimefun.getRegistry().getAllSlimefunItems().stream()
                .map(item -> Map.of("item_id", item.getId(), "plugin", item.getAddon().getName(),
                        "plugin_version", item.getAddon().getPluginVersion()))
                .sorted(java.util.Comparator.comparing(item -> item.get("item_id")))
                .toList();
        var registry = Map.of("schema", 1, "software", Bukkit.getName(),
                "minecraft", Bukkit.getMinecraftVersion(), "server_version", Bukkit.getVersion(), "items", items);
        Files.writeString(folder.resolve("identity-registry.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(registry));
    }

    private static void verifyRegisteredCloneIdentity(Path folder) throws Exception {
        var items = Slimefun.getRegistry().getAllSlimefunItems().stream()
                .sorted(java.util.Comparator.comparing(SlimefunItem::getId)).toList();
        require(!items.isEmpty(), "registered clone identity probe has no items");
        var results = new java.util.ArrayList<Map<String, Object>>();
        var failures = new java.util.ArrayList<String>();
        for (var item : items) {
            ItemStack template = item.getItem();
            byte[] before = template.serializeAsBytes();
            // Intentionally exercise the public production clone implementation.
            // SlimefunItemStack.clone reapplies item data/textures in its constructor.
            ItemStack first = template.clone();
            ItemStack second = first.clone();
            boolean unchanged = Arrays.equals(before, template.serializeAsBytes());
            boolean firstExact = Arrays.equals(before, first.serializeAsBytes());
            boolean secondExact = Arrays.equals(before, second.serializeAsBytes());
            boolean amounts = template.getAmount() == first.getAmount()
                    && template.getAmount() == second.getAmount();
            boolean passed = unchanged && firstExact && secondExact && amounts;
            results.add(Map.of("item_id", item.getId(), "plugin", item.getAddon().getName(),
                    "template_unchanged", unchanged, "first_clone_exact", firstExact,
                    "second_clone_exact", secondExact, "quantity_preserved", amounts,
                    "status", passed ? "PASS" : "FAIL"));
            if (!passed) {
                failures.add(item.getId());
            }
        }
        // IDs and booleans only; never publish item payloads or private typed PDC.
        var report = Map.of("schema", 1, "scope", "current_registered_template_clone_only",
                "software", Bukkit.getName(), "server_version", Bukkit.getVersion(),
                "historical_world_acceptance", "UNTESTED", "producer_coverage", "UNTESTED",
                "items", results);
        Files.writeString(folder.resolve("identity-registry-clone.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report));
        require(failures.isEmpty(), "registered clone identity drift: " + failures);
        Bukkit.getLogger().info("SFL_REGISTERED_CLONE_IDENTITY_PASS items=" + results.size());
    }

    private static void verifyRegisteredCarbonHistory(Path folder, String id, boolean restart)
            throws Exception {
        var registered = SlimefunItem.getById(id);
        require(registered != null, "Core must register actual historical item ID: " + id);
        Path caseFolder = folder.resolve("identity-history-" + id.toLowerCase(java.util.Locale.ROOT));
        Path oldFile = caseFolder.resolve("old.bin");
        Path referenceFile = caseFolder.resolve("current.bin");
        Path repairedFile = caseFolder.resolve("repaired.bin");

        if (restart) {
            byte[] oldBytes = Files.readAllBytes(oldFile);
            byte[] currentBytes = Files.readAllBytes(referenceFile);
            ItemStack old = ItemStack.deserializeBytes(oldBytes);
            ItemStack current = ItemStack.deserializeBytes(currentBytes);
            ItemStack repaired = ItemStack.deserializeBytes(Files.readAllBytes(repairedFile));
            require(old.getAmount() == 63 && current.getAmount() == 1 && repaired.getAmount() == 63,
                    "native " + id + " quantity changed on second boot");
            require(!old.isSimilar(current) && repaired.isSimilar(current),
                    "native " + id + " lost old-vs-current identity across restart");
            var storage = Bukkit.createInventory(null, 9);
            require(storage.addItem(repaired, current).isEmpty()
                            && storage.getItem(0).getAmount() == 64 && storage.getItem(1) == null,
                    "native " + id + " could not physically merge 63+1 into 64 after restart");
            try (var entries = Files.list(caseFolder.resolve("recovery/identity"))) {
                var backups = entries.filter(Files::isRegularFile).toList();
                require(backups.size() == 1 && Arrays.equals(oldBytes, Files.readAllBytes(backups.getFirst())),
                        "native " + id + " old source bytes not intact across restart");
                String filename = backups.getFirst().getFileName().toString();
                java.util.UUID owner = java.util.UUID.fromString(filename.substring(0, 36));
                java.util.UUID receipt = java.util.UUID.fromString(filename.substring(37, 73));
                ItemStack verified = readDoctorBackup(caseFolder, owner, receipt);
                require(Arrays.equals(oldBytes, verified.serializeAsBytes())
                                && matchesDoctorBackup(verified, repaired, current, current),
                        "native " + id + " verified rollback identity or amount changed after restart");
                ItemStack changedQuantity = ItemStack.deserializeBytes(repaired.serializeAsBytes());
                changedQuantity.setAmount(62);
                require(!matchesDoctorBackup(verified, changedQuantity, current, current),
                        "native " + id + " rollback would accept changed quantity");
                require(!matchesDoctorBackup(verified, repaired, old, current),
                        "native " + id + " rollback would accept unregistered old reference");
            }
            require(Arrays.equals(oldBytes, Files.readAllBytes(oldFile))
                            && Arrays.equals(currentBytes, Files.readAllBytes(referenceFile)),
                    "native " + id + " test changed its immutable old/current snapshots");
            return;
        }

        Files.createDirectories(caseFolder);
        byte[] templateBytes = registered.getItem().serializeAsBytes();
        ItemStack current = ItemStack.deserializeBytes(templateBytes);
        current.setAmount(1);
        ItemStack old = ItemStack.deserializeBytes(templateBytes);
        old.setAmount(63);
        var display = old.getItemMeta().displayName();
        require(display != null, "native " + id + " registered item lacks display name");
        boolean originalBold = display.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        old.editMeta(meta -> meta.displayName(meta.displayName()
                .decoration(TextDecoration.BOLD, !originalBold)));
        byte[] oldBytes = old.serializeAsBytes();
        byte[] currentBytes = current.serializeAsBytes();
        require(!old.isSimilar(current), "native " + id + " synthetic old styling did not differ");
        Object classification = historical(old, current);
        require(flag(classification, "candidate") && flag(classification, "nonPresentationSame"),
                "native " + id + " cosmetic candidate altered functional typed components");
        ItemStack repaired = repairCandidate(old, current, current);
        require(repaired != null && repaired.isSimilar(current) && repaired.getAmount() == 63,
                "native " + id + " exact original typed data could not be styling repaired");
        Path saved = backupHistorical(caseFolder, oldBytes);
        require(Arrays.equals(oldBytes, Files.readAllBytes(saved)),
                "native " + id + " original backing bytes were modified");
        Files.write(oldFile, oldBytes);
        Files.write(referenceFile, currentBytes);
        Files.write(repairedFile, repaired.serializeAsBytes());
        require(Arrays.equals(templateBytes, registered.getItem().serializeAsBytes())
                        && Arrays.equals(oldBytes, old.serializeAsBytes())
                        && Arrays.equals(currentBytes, current.serializeAsBytes()),
                "native " + id + " altered registered/current/old input item");
    }

    /**
     * Disposable native Paper reproduction of a saved shop item with a different
     * plain-text lore. The real AlbionMC item bytes are NOT available here; do
     * not claim this synthetic variant certifies all old shops.
     */
    private static void verifyStaticCarbonadoLore(Path folder, boolean restart) throws Exception {
        var registered = SlimefunItem.getById("CARBONADO");
        require(registered != null, "missing real registered CARBONADO template");
        Path caseFolder = folder.resolve("identity-history-carbonado-static-lore");
        Path oldFile = caseFolder.resolve("old.bin");
        Path freshFile = caseFolder.resolve("fresh.bin");
        Path repairedFile = caseFolder.resolve("repaired.bin");

        if (restart) {
            byte[] oldBytes = Files.readAllBytes(oldFile);
            byte[] freshBytes = Files.readAllBytes(freshFile);
            ItemStack original = ItemStack.deserializeBytes(oldBytes);
            ItemStack fresh = ItemStack.deserializeBytes(freshBytes);
            ItemStack repaired = ItemStack.deserializeBytes(Files.readAllBytes(repairedFile));
            require(original.getAmount() == 63 && fresh.getAmount() == 1 && repaired.getAmount() == 63,
                    "native historical CARBONADO lore repair amount changed after restart");
            require(!original.isSimilar(fresh) && repaired.isSimilar(fresh),
                    "native historical CARBONADO old/new identity lost after restart");
            var storage = Bukkit.createInventory(null, 9);
            require(storage.addItem(repaired, fresh).isEmpty()
                            && storage.getItem(0).getAmount() == 64 && storage.getItem(1) == null,
                    "native historical CARBONADO repaired 63+1 would not stack after restart");
            try (var files = Files.list(caseFolder.resolve("recovery/identity"))) {
                var backups = files.filter(Files::isRegularFile).toList();
                require(backups.size() == 1 && Arrays.equals(oldBytes, Files.readAllBytes(backups.getFirst())),
                        "native historical CARBONADO original backup altered across restart");
                String filename = backups.getFirst().getFileName().toString();
                java.util.UUID owner = java.util.UUID.fromString(filename.substring(0, 36));
                java.util.UUID receipt = java.util.UUID.fromString(filename.substring(37, 73));
                ItemStack verified = readDoctorBackup(caseFolder, owner, receipt);
                require(Arrays.equals(oldBytes, verified.serializeAsBytes())
                                && matchesDoctorBackup(verified, repaired, fresh, fresh),
                        "native CARBONADO static-lore backup/rollback verification failed after restart");
                ItemStack changed = ItemStack.deserializeBytes(repaired.serializeAsBytes());
                changed.setAmount(62);
                require(!matchesDoctorBackup(verified, changed, fresh, fresh),
                        "native CARBONADO static-lore rollback accepted changed quantity");
            }
            require(Arrays.equals(oldBytes, Files.readAllBytes(oldFile))
                            && Arrays.equals(freshBytes, Files.readAllBytes(freshFile)),
                    "native CARBONADO static-lore verification modified immutable snapshots");
            return;
        }

        Files.createDirectories(caseFolder);
        byte[] registeredBytes = registered.getItem().serializeAsBytes();
        ItemStack fresh = ItemStack.deserializeBytes(registeredBytes);
        fresh.setAmount(1);
        ItemStack old = ItemStack.deserializeBytes(registeredBytes);
        old.setAmount(63);
        java.util.List<Component> canonicalLore = fresh.getItemMeta().lore();
        require(canonicalLore != null, "registered CARBONADO lore missing");
        java.util.List<Component> oldLore = canonicalLore.stream()
                .filter(component -> !net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(component)
                        .isBlank())
                .toList();
        require(!oldLore.isEmpty() && oldLore.size() < canonicalLore.size(),
                "registered CARBONADO has no blank-line legacy variant to test");
        old.editMeta(meta -> meta.lore(oldLore));
        byte[] oldBytes = old.serializeAsBytes();
        byte[] freshBytes = fresh.serializeAsBytes();
        Object before = historical(old, fresh);
        require(!flag(before, "plainTextSame") && flag(before, "nonPresentationSame")
                        && !flag(before, "candidate") && !old.isSimilar(fresh),
                "native historical CARBONADO plain lore mismatch classified as safe by default");
        require(repairCandidate(old, fresh, fresh) == null,
                "native standard Doctor repair bypassed CARBONADO plain-text guard");
        ItemStack fixed = repairStaticLoreCandidate(old, fresh, fresh);
        require(fixed != null && fixed.getAmount() == 63 && fixed.isSimilar(fresh),
                "native explicit CARBONADO lore recovery failed strict current template equivalence");
        ItemStack typed = ItemStack.deserializeBytes(oldBytes);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 777L));
        require(repairStaticLoreCandidate(typed, fresh, fresh) == null,
                "native CARBONADO static-lore recovery would overwrite typed ownership metadata");
        ItemStack named = ItemStack.deserializeBytes(oldBytes);
        named.editMeta(meta -> meta.displayName(Component.text("Private renamed Carbonado")));
        require(repairStaticLoreCandidate(named, fresh, fresh) == null,
                "native CARBONADO static-lore recovery would erase a player-custom name");
        Path backup = backupHistorical(caseFolder, oldBytes);
        require(Arrays.equals(oldBytes, Files.readAllBytes(backup)),
                "native CARBONADO static-lore original bytes not durably backed up");
        Files.write(oldFile, oldBytes);
        Files.write(freshFile, freshBytes);
        Files.write(repairedFile, fixed.serializeAsBytes());
        require(Arrays.equals(registeredBytes, registered.getItem().serializeAsBytes())
                        && Arrays.equals(oldBytes, old.serializeAsBytes())
                        && Arrays.equals(freshBytes, fresh.serializeAsBytes()),
                "native CARBONADO static-lore fixture mutated original or registered templates");
    }

    /** Synthetic export durability controls; never substitutes for original AlbionMC evidence. */
    private static void verifyEvidenceExport(Path folder, boolean restart) throws Exception {
        Path root = folder.resolve("identity-export-fixture");
        Path receipts = root.resolve("receipts.txt");
        Path mainFile = root.resolve("expected-main.bin");
        Path offFile = root.resolve("expected-offhand.bin");
        if (!restart) {
            Files.createDirectory(root);
            SlimefunItem registered = SlimefunItem.getById("CARBONADO");
            require(registered != null, "export fixture CARBONADO registration missing");
            byte[] templateBytes = registered.getItem().serializeAsBytes();
            ItemStack main = ItemStack.deserializeBytes(templateBytes);
            main.setAmount(1);
            ItemStack off = ItemStack.deserializeBytes(templateBytes);
            off.setAmount(63);
            off.editMeta(meta -> {
                meta.lore(List.of(Component.text("Synthetic private custom lore, never repairable")));
                meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 75123L);
            });
            byte[] mainBytes = main.serializeAsBytes();
            byte[] offBytes = off.serializeAsBytes();
            java.util.UUID owner = java.util.UUID.randomUUID();
            Class<?> exporter = Class.forName(
                    "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorIdentityEvidenceExport");
            Method capture = exporter.getDeclaredMethod(
                    "capture", Path.class, java.util.UUID.class, ItemStack.class, ItemStack.class);
            capture.setAccessible(true);
            Object evidence = capture.invoke(null, root, owner, main, off);
            Method mainReceipt = evidence.getClass().getDeclaredMethod("mainReceipt");
            Method offReceipt = evidence.getClass().getDeclaredMethod("offhandReceipt");
            mainReceipt.setAccessible(true);
            offReceipt.setAccessible(true);
            String first = mainReceipt.invoke(evidence).toString();
            String second = offReceipt.invoke(evidence).toString();
            require(!first.equals(second), "export receipts must be independent");
            require(Arrays.equals(mainBytes, main.serializeAsBytes())
                            && Arrays.equals(offBytes, off.serializeAsBytes())
                            && Arrays.equals(templateBytes, registered.getItem().serializeAsBytes()),
                    "export mutated inputs or registered template");
            Files.write(mainFile, mainBytes);
            Files.write(offFile, offBytes);
            Files.write(receipts, List.of(owner.toString(), first, second));
        }
        // Restart must consume the original receipts and originals, with no fallback or recapture.
        byte[] receiptBytes = Files.readAllBytes(receipts);
        List<String> ids = Files.readAllLines(receipts);
        require(ids.size() == 3 && !ids.get(1).equals(ids.get(2)), "export receipt index invalid");
        java.util.UUID owner = java.util.UUID.fromString(ids.get(0));
        Path directory = root.resolve("recovery/identity");
        try (var files = Files.list(directory)) {
            require(files.count() == 2, "export must retain exactly two independent original backups");
        }
        for (int index = 0; index < 2; index++) {
            java.util.UUID receipt = java.util.UUID.fromString(ids.get(index + 1));
            byte[] expected = Files.readAllBytes(index == 0 ? mainFile : offFile);
            Path backup = directory.resolve(owner + "-" + receipt + ".bin");
            require(Arrays.equals(expected, Files.readAllBytes(backup)), "export backup original bytes changed");
            ItemStack restored = readDoctorBackup(root, owner, receipt);
            require(Arrays.equals(expected, restored.serializeAsBytes())
                            && restored.getAmount() == (index == 0 ? 1 : 63),
                    "export native reload changed metadata or quantity");
            if (index == 1) {
                require(Long.valueOf(75123L).equals(restored.getItemMeta().getPersistentDataContainer()
                                .get(OWNER, PersistentDataType.LONG)),
                        "export lost typed owner data");
            }
            require(Arrays.equals(expected, Files.readAllBytes(backup)), "export readback rewrote backup");
        }
        verifyPrivateEvidenceReplay(root, owner, java.util.UUID.fromString(ids.get(2)),
                java.util.UUID.fromString(ids.get(1)), "FUNCTIONAL_METADATA", restart);
        require(Arrays.equals(receiptBytes, Files.readAllBytes(receipts)), "export receipt index changed");
        Bukkit.getLogger().info("IDENTITY_EXPORT_SYNTHETIC_" + (restart ? "RESTART" : "SEED") + "_PASS");
    }

    /**
     * Emulates the pre-Adventure SlimefunItemStack text setters from v4.1.55
     * against representative ACTUAL registered modern templates on native Paper.
     * This is an API-path diagnostic, not a captured old AlbionMC item.
     * The result can be MATCH, text/presentation drift, or other metadata;
     * do not invent a mismatch when current Bukkit setters serialize equally.
     */
    @SuppressWarnings("deprecation")
    private static void verifyOldBukkitTextApiMatrix(Path folder, boolean restart) throws Exception {
        // Raw names/lore reviewed against v4.1.55 commit
        // 0b2d862a111f87b94288c51297cd3d5795eeef51, implementation/SlimefunItems.java.
        // Only the historical TEXT setter path is emulated; other metadata comes
        // from today's registered template. These are not full old-world items.
        verifyOldBukkitTextApi(folder, restart, "CARBONADO", "&b&lCarbonado", List.of("", "&7&o\"Black Diamond\""));
        verifyOldBukkitTextApi(folder, restart, "COPPER_DUST", "&6Copper Dust", List.of());
        verifyOldBukkitTextApi(folder, restart, "COPPER_INGOT", "&bCopper Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "TIN_INGOT", "&bTin Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "SILVER_INGOT", "&bSilver Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "CARBON", "&eCarbon", List.of());
        verifyOldBukkitTextApi(folder, restart, "COMPRESSED_CARBON", "&cCompressed Carbon", List.of());
        // Reviewed unchanged static item definitions across v4.1.55 and v4.1.81.
        // Widen the identity contract to resources, intermediate components,
        // alloys, gold grades, and dusts (not just the reported CARBONADO).
        verifyOldBukkitTextApi(folder, restart, "CARBON_CHUNK", "&4Carbon Chunk", List.of());
        verifyOldBukkitTextApi(folder, restart, "RAW_CARBONADO", "&bRaw Carbonado", List.of());
        verifyOldBukkitTextApi(folder, restart, "BASIC_CIRCUIT_BOARD", "&bBasic Circuit Board", List.of());
        verifyOldBukkitTextApi(folder, restart, "ADVANCED_CIRCUIT_BOARD", "&bAdvanced Circuit Board", List.of());
        verifyOldBukkitTextApi(folder, restart, "REINFORCED_ALLOY_INGOT", "&b&lReinforced Alloy Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "ALUMINUM_INGOT", "&bAluminum Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "LEAD_INGOT", "&bLead Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "STEEL_INGOT", "&bSteel Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "NICKEL_INGOT", "&bNickel Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "COBALT_INGOT", "&9Cobalt Ingot", List.of());
        verifyOldBukkitTextApi(folder, restart, "GOLD_18K", "&fGold Ingot &7(18-Carat)", List.of());
        verifyOldBukkitTextApi(folder, restart, "GOLD_22K", "&fGold Ingot &7(22-Carat)", List.of());
        verifyOldBukkitTextApi(folder, restart, "GOLD_24K", "&fGold Ingot &7(24-Carat)", List.of());
        verifyOldBukkitTextApi(folder, restart, "GOLD_DUST", "&6Gold Dust", List.of());
        verifyOldBukkitTextApi(folder, restart, "SILVER_DUST", "&6Silver Dust", List.of());
        verifyOldBukkitTextApi(folder, restart, "ALUMINUM_DUST", "&6Aluminum Dust", List.of());
        verifyOldBukkitTextApi(folder, restart, "LEAD_DUST", "&6Lead Dust", List.of());
        verifyOldBukkitTextApi(folder, restart, "ZINC_DUST", "&6Zinc Dust", List.of());
        verifyOldBukkitTextApi(folder, restart, "MAGNESIUM_DUST", "&6Magnesium", List.of());
        ItemStack tin = SlimefunItem.getById("TIN_INGOT").getItem();
        ItemStack silver = SlimefunItem.getById("SILVER_INGOT").getItem();
        require(tin.getType() == silver.getType() && !tin.isSimilar(silver) && !silver.isSimilar(tin),
                "distinct registered ingot IDs sharing a vanilla material became stack-compatible");
        ItemStack gold18 = SlimefunItem.getById("GOLD_18K").getItem();
        ItemStack gold24 = SlimefunItem.getById("GOLD_24K").getItem();
        require(gold18.getType() == gold24.getType()
                        && !gold18.isSimilar(gold24) && !gold24.isSimilar(gold18),
                "distinct registered gold grades sharing a vanilla material became stack-compatible");
        ItemStack silverDust = SlimefunItem.getById("SILVER_DUST").getItem();
        ItemStack aluminumDust = SlimefunItem.getById("ALUMINUM_DUST").getItem();
        require(silverDust.getType() == aluminumDust.getType()
                        && !silverDust.isSimilar(aluminumDust) && !aluminumDust.isSimilar(silverDust),
                "distinct registered dust IDs sharing a vanilla material became stack-compatible");
    }

    @SuppressWarnings("deprecation")
    private static void verifyOldBukkitTextApi(
            Path folder, boolean restart, String id, String name, List<String> lore) throws Exception {
        var registered = SlimefunItem.getById(id);
        require(registered != null, "historical Bukkit text API probe missing registration: " + id);
        Path caseFolder = folder.resolve("identity-old-bukkit-text-api-" + id.toLowerCase(java.util.Locale.ROOT));
        Path legacyFile = caseFolder.resolve("legacy-api.bin");
        Path currentFile = caseFolder.resolve("current.bin");
        Path classificationFile = caseFolder.resolve("classification.txt");
        Path alternativesFile = caseFolder.resolve("constructor-alternatives.txt");
        if (restart) {
            byte[] legacyBytes = Files.readAllBytes(legacyFile);
            byte[] currentBytes = Files.readAllBytes(currentFile);
            ItemStack oldApi = ItemStack.deserializeBytes(legacyBytes);
            ItemStack modern = ItemStack.deserializeBytes(currentBytes);
            require(oldApi.getAmount() == 31 && modern.getAmount() == 1,
                    "old Bukkit text API fixture quantities changed on separate Paper boot");
            String expected = Files.readString(classificationFile);
            String observed = classifyOldBukkitTextApi(oldApi, modern);
            Bukkit.getLogger().info("SFL_LEGACY_BUKKIT_TEXT_" + id + "_RESTART=" + observed);
            requireLegacyTextCompatibility(id, observed);
            ItemStack registeredAfterRestart = registered.getItem();
            if ("STEEL_INGOT".equals(id)) {
                // This same Doctor integration deliberately clears STEEL_INGOT's
                // resource-pack model mapping between boot 1 and boot 2.
                // Preserve the seed-era reference for validating a previously
                // saved repair; the NEW template must refuse the OLD model.
                require(Slimefun.getItemTextureService().getModelData(id) == 0,
                        "Doctor checkpoint did not retire the historical steel model");
                require(!sameNativeItemIgnoringCount(modern, registeredAfterRestart),
                        "Doctor checkpoint did not change the model-dependent STEEL_INGOT template");
                require(repairCandidate(oldApi, registeredAfterRestart, registeredAfterRestart) == null,
                        "Historical styling migration improperly bypassed Steel Ingot model transition");
                Bukkit.getLogger().info("SFL_LEGACY_STEEL_MODEL_TRANSITION_EXPECTED_RESTART_PASS");
            } else {
                require(sameNativeItemIgnoringCount(modern, registeredAfterRestart),
                        "Unexplained current registered item drift across restart: " + id);
            }
            // The rollback receipt is bound to the EXACT source reference
            // captured in boot 1, not the deliberately changed template in
            // boot 2. Do not authorize conversion to an unverified new model.
            verifyLegacyStyleRecovery(caseFolder, id, oldApi, modern, modern, true);
            require(expected.equals(observed),
                    "old Bukkit text API fixture provenance classification drifted after restart");
            String alternatives = compareConstructorAlternatives(oldApi, modern, name, lore);
            require(Files.readString(alternativesFile).equals(alternatives),
                    "legacy Bukkit/Adventure constructor alternatives changed after restart: " + id);
            require(Arrays.equals(legacyBytes, oldApi.serializeAsBytes())
                            && Arrays.equals(currentBytes, modern.serializeAsBytes()),
                    "old Bukkit text API fixture bytes changed during separate Paper restart");
            return;
        }
        Files.createDirectories(caseFolder);
        byte[] registeredBytes = registered.getItem().serializeAsBytes();
        ItemStack modern = ItemStack.deserializeBytes(registeredBytes);
        modern.setAmount(1);
        ItemStack oldApi = ItemStack.deserializeBytes(registeredBytes);
        oldApi.setAmount(31);
        oldApi.editMeta(meta -> {
            // The reviewed old definitions start without lore. Do not inherit
            // current lore here: that would conceal future added text on IDs
            // whose historical constructor received no lore arguments.
            meta.lore(null);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).toList());
            }
        });
        byte[] legacyBytes = oldApi.serializeAsBytes();
        byte[] currentBytes = modern.serializeAsBytes();
        String classification = classifyOldBukkitTextApi(oldApi, modern);
        String alternatives = compareConstructorAlternatives(oldApi, modern, name, lore);
        Bukkit.getLogger().info("SFL_LEGACY_BUKKIT_TEXT_" + id + "_SEED=" + classification);
        Bukkit.getLogger().info("SFL_LEGACY_CONSTRUCTOR_ALTERNATIVES_" + id + "=" + alternatives);
        requireLegacyTextCompatibility(id, classification);
        verifyLegacyStyleRecovery(caseFolder, id, oldApi, modern, registered.getItem(), false);
        // Negative controls make this a semantic regression gate, not merely a
        // record of whatever the current constructor happens to produce.
        ItemStack changedText = ItemStack.deserializeBytes(currentBytes);
        changedText.editMeta(meta -> meta.displayName(Component.text("Changed meaningful name")));
        require("MEANINGFUL_TEXT".equals(classifyOldBukkitTextApi(changedText, modern)),
                "legacy API classification ignored changed meaningful name: " + id);
        ItemStack changedLore = ItemStack.deserializeBytes(currentBytes);
        changedLore.editMeta(meta -> meta.lore(List.of(Component.text("Custom player lore"))));
        require("MEANINGFUL_TEXT".equals(classifyOldBukkitTextApi(changedLore, modern)),
                "legacy API classification ignored changed meaningful lore: " + id);
        ItemStack changedOwner = ItemStack.deserializeBytes(legacyBytes);
        changedOwner.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 913L));
        require("OTHER_METADATA".equals(classifyOldBukkitTextApi(changedOwner, modern)),
                "legacy API classification ignored typed owner difference: " + id);
        ItemStack integerOwner = ItemStack.deserializeBytes(legacyBytes);
        integerOwner.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.INTEGER, 913));
        require("OTHER_METADATA".equals(classifyOldBukkitTextApi(integerOwner, changedOwner)),
                "legacy API classification conflated INTEGER and LONG owner values: " + id);
        require(Arrays.equals(registeredBytes, registered.getItem().serializeAsBytes()),
                "old Bukkit text API probe mutated registered template");
        Files.write(legacyFile, legacyBytes);
        Files.write(currentFile, currentBytes);
        Files.writeString(classificationFile, classification);
        Files.writeString(alternativesFile, alternatives);
        require(Arrays.equals(legacyBytes, oldApi.serializeAsBytes())
                        && Arrays.equals(currentBytes, modern.serializeAsBytes()),
                "old Bukkit text API probe modified its detached inputs");
    }

    /**
     * Diagnoses whether historical Bukkit String setter metadata can be built
     * identically with the modern Adventure setter, either with default
     * decoration state or with the current explicit italic=false override.
     * This does NOT change registration or approve a real-world migration.
     */
    @SuppressWarnings("deprecation")
    private static String compareConstructorAlternatives(
            ItemStack oldApi, ItemStack modern, String rawName, List<String> rawLore) {
        byte[] oldBytes = oldApi.serializeAsBytes();
        byte[] modernBytes = modern.serializeAsBytes();
        ItemStack adventureDefault = ItemStack.deserializeBytes(modernBytes);
        adventureDefault.setAmount(oldApi.getAmount());
        ItemStack adventureNoItalic = ItemStack.deserializeBytes(modernBytes);
        adventureNoItalic.setAmount(oldApi.getAmount());
        var parser = LegacyComponentSerializer.legacySection();
        String name = ChatColor.translateAlternateColorCodes('&', rawName);
        List<Component> plainLore = rawLore.stream()
                .<Component>map(line -> parser.deserialize(ChatColor.translateAlternateColorCodes('&', line)))
                .toList();
        adventureDefault.editMeta(meta -> {
            meta.displayName(parser.deserialize(name));
            meta.lore(rawLore.isEmpty() ? null : plainLore);
        });
        adventureNoItalic.editMeta(meta -> {
            meta.displayName(parser.deserialize(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(rawLore.isEmpty()
                    ? null
                    : plainLore.stream()
                            .map(line -> line.decoration(TextDecoration.ITALIC, false))
                            .toList());
        });
        boolean defaultMatches = oldApi.isSimilar(adventureDefault);
        boolean forcedNoItalicMatches = oldApi.isSimilar(adventureNoItalic);
        require(Arrays.equals(oldBytes, oldApi.serializeAsBytes())
                        && Arrays.equals(modernBytes, modern.serializeAsBytes()),
                "legacy setter alternative comparison mutated a source item");
        return "DEFAULT_" + (defaultMatches ? "MATCH" : "DIFFERENT")
                + "_ITALIC_OFF_" + (forcedNoItalicMatches ? "MATCH" : "DIFFERENT");
    }

    /** Count-normalized, exact native identity comparison; never ignores typed data. */
    private static boolean sameNativeItemIgnoringCount(ItemStack first, ItemStack second) {
        ItemStack one = ItemStack.deserializeBytes(first.serializeAsBytes());
        ItemStack two = ItemStack.deserializeBytes(second.serializeAsBytes());
        one.setAmount(1);
        two.setAmount(1);
        return Arrays.equals(one.serializeAsBytes(), two.serializeAsBytes());
    }

    private static void verifyLegacyStyleRecovery(
            Path folder, String id, ItemStack old, ItemStack fresh, ItemStack template, boolean restart)
            throws Exception {
        // Exercise the existing opt-in candidate/backup code only on disposable
        // source-derived fixtures. Never grant migration eligibility to real items.
        if (old.isSimilar(fresh)) {
            return;
        }
        byte[] originalBytes = old.serializeAsBytes();
        byte[] freshBytes = fresh.serializeAsBytes();
        byte[] templateBytes = template.serializeAsBytes();
        Path repairedFile = folder.resolve("style-repaired.bin");
        Path receiptFile = folder.resolve("style-backup-name.txt");
        ItemStack repaired;
        if (restart) {
            repaired = ItemStack.deserializeBytes(Files.readAllBytes(repairedFile));
        } else {
            repaired = repairCandidate(old, fresh, template);
            require(repaired != null, "legacy style candidate refused: " + id);
            Path backup = backupHistorical(folder, originalBytes);
            Files.writeString(receiptFile, backup.getFileName().toString());
            Files.write(repairedFile, repaired.serializeAsBytes());
        }
        String filename = Files.readString(receiptFile);
        java.util.UUID owner = java.util.UUID.fromString(filename.substring(0, 36));
        java.util.UUID receipt = java.util.UUID.fromString(filename.substring(37, 73));
        ItemStack verified = readDoctorBackup(folder, owner, receipt);
        boolean backupExact = Arrays.equals(originalBytes, verified.serializeAsBytes());
        boolean quantitiesPreserved = repaired.getAmount() == 31 && verified.getAmount() == 31;
        boolean stackingCompatible = repaired.isSimilar(fresh) && fresh.isSimilar(repaired);
        boolean templateStable = sameNativeItemIgnoringCount(fresh, template);
        boolean rollbackCompatible = matchesDoctorBackup(verified, repaired, fresh, template);
        if (!backupExact || !quantitiesPreserved || !stackingCompatible || !templateStable || !rollbackCompatible) {
            // Metadata may be owner/private: emit classification flags only,
            // not item names, lore, PDC values, or native serialized bytes.
            Bukkit.getLogger().warning("SFL_LEGACY_STYLE_RECOVERY_REFUSED_" + id
                    + " backup=" + backupExact + " count=" + quantitiesPreserved
                    + " stacks=" + stackingCompatible + " registeredTemplateStable=" + templateStable
                    + " rollback=" + rollbackCompatible);
        }
        require(backupExact && quantitiesPreserved && stackingCompatible && templateStable && rollbackCompatible,
                "legacy style backup/restart/rollback identity mismatch: " + id);
        ItemStack changedAmount = ItemStack.deserializeBytes(repaired.serializeAsBytes());
        changedAmount.setAmount(30);
        require(!matchesDoctorBackup(verified, changedAmount, fresh, template),
                "legacy style rollback accepted changed quantity: " + id);
        var inventory = Bukkit.createInventory(null, 9);
        require(inventory.addItem(ItemStack.deserializeBytes(repaired.serializeAsBytes()),
                        ItemStack.deserializeBytes(freshBytes)).isEmpty()
                        && inventory.getItem(0).getAmount() == 32 && inventory.getItem(1) == null,
                "legacy style repair failed exact 31+1 merge: " + id);
        require(Arrays.equals(originalBytes, old.serializeAsBytes())
                        && Arrays.equals(freshBytes, fresh.serializeAsBytes())
                        && Arrays.equals(templateBytes, template.serializeAsBytes()),
                "legacy style recovery changed input/template bytes: " + id);
        Bukkit.getLogger().info("SFL_LEGACY_STYLE_RECOVERY_" + id + (restart ? "_RESTART_PASS" : "_SEED_PASS"));
    }

    private static void requireLegacyTextCompatibility(String id, String classification) {
        // Reviewed initial seven-fixture/six-runtime baseline at 25a7d561.
        // Additional source-derived fixtures must meet the same strict rule on
        // the current native server; do not grandfather a new text regression.
        // A future exact native match is welcome;
        // new blank-line, meaningful-text or functional-data drift must fail CI.
        // This does not certify genuine old-world bytes or approve any repair.
        require("MATCH".equals(classification) || "STYLE_OR_COMPONENT".equals(classification),
                "legacy text constructor compatibility regressed for " + id + ": " + classification);
    }

    private static String classifyOldBukkitTextApi(ItemStack oldApi, ItemStack modern) throws Exception {
        if (oldApi.isSimilar(modern)) {
            return "MATCH";
        }
        Object evidence = historical(oldApi, modern);
        if (!flag(evidence, "nonPresentationSame")) {
            return "OTHER_METADATA";
        }
        if (flag(evidence, "plainTextSame")) {
            return "STYLE_OR_COMPONENT";
        }
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorIdentityCommand");
        Method plain = doctor.getDeclaredMethod("inspectPlainPresentation", ItemStack.class, ItemStack.class);
        Method blank = doctor.getDeclaredMethod("sameNonblankPlainLore", ItemStack.class, ItemStack.class);
        plain.setAccessible(true);
        blank.setAccessible(true);
        boolean sameName = flag(plain.invoke(null, oldApi, modern), "nameSame");
        return sameName && (Boolean) blank.invoke(null, oldApi, modern)
                ? "BLANK_LORE_LAYOUT" : "MEANINGFUL_TEXT";
    }

    /** Native Paper demonstration for #578: classification only, never a historical repair. */
    /**
     * Native 2-boot acceptance of the *actual live-batch engine* using the
     * registered CARBONADO source on disposable Paper. The old style is
     * synthetic; a real owner's saved shop/chest byte capture is still needed.
     */
    private static void verifyLivePlayerInventoryBatch(Path folder, boolean restart) throws Exception {
        var registered = SlimefunItem.getById("CARBONADO");
        require(registered != null, "missing CARBONADO for native live-batch acceptance");
        Path directory = folder.resolve("identity-live-player-batch");
        Path oldFile = directory.resolve("original.bin");
        Path freshFile = directory.resolve("fresh.bin");
        Path updatedFile = directory.resolve("updated.bin");
        Path receiptFile = directory.resolve("owner-receipt.txt");

        if (restart) {
            byte[] oldBytes = Files.readAllBytes(oldFile);
            byte[] freshBytes = Files.readAllBytes(freshFile);
            byte[] updatedBytes = Files.readAllBytes(updatedFile);
            ItemStack original = ItemStack.deserializeBytes(oldBytes);
            ItemStack fresh = ItemStack.deserializeBytes(freshBytes);
            ItemStack updated = ItemStack.deserializeBytes(updatedBytes);
            require(original.getAmount() == 63 && fresh.getAmount() == 1 && updated.getAmount() == 63,
                    "native live-batch CARBONADO amounts changed after restart");
            require(!original.isSimilar(fresh) && updated.isSimilar(fresh),
                    "native live-batch did not preserve original mismatch/repaired identity across restart");
            String[] tokens = Files.readString(receiptFile).trim().split(":", -1);
            require(tokens.length == 2, "native live-batch private receipt malformed");
            java.util.UUID owner = java.util.UUID.fromString(tokens[0]);
            java.util.UUID receipt = java.util.UUID.fromString(tokens[1]);
            ItemStack saved = readDoctorBackup(directory, owner, receipt);
            require(Arrays.equals(oldBytes, saved.serializeAsBytes())
                            && matchesDoctorBackup(saved, updated, fresh, fresh),
                    "native live-batch original receipt or exact rollback eligibility lost across restart");
            var inventory = Bukkit.createInventory(null, 9);
            require(inventory.addItem(updated, fresh).isEmpty()
                            && inventory.getItem(0).getAmount() == 64 && inventory.getItem(1) == null,
                    "native live-batch repaired CARBONADO 63+1 merge failed after restart");
            require(Arrays.equals(oldBytes, Files.readAllBytes(oldFile))
                            && Arrays.equals(updatedBytes, Files.readAllBytes(updatedFile)),
                    "native live-batch old/new evidence changed during restart");
            Bukkit.getLogger().info("SFL_LIVE_BATCH_CARBONADO_RESTART_PASS");
            return;
        }

        Files.createDirectories(directory);
        byte[] registeredBytes = registered.getItem().serializeAsBytes();
        ItemStack fresh = ItemStack.deserializeBytes(registeredBytes);
        fresh.setAmount(1);
        ItemStack old = ItemStack.deserializeBytes(registeredBytes);
        old.setAmount(63);
        var display = old.getItemMeta().displayName();
        require(display != null, "native live-batch registered CARBONADO name unavailable");
        boolean wasBold = display.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, !wasBold)));
        byte[] oldBytes = old.serializeAsBytes();
        byte[] freshBytes = fresh.serializeAsBytes();
        require(!old.isSimilar(fresh), "native live-batch synthetic legacy style must differ");

        var inventory = Bukkit.createInventory(null, 36);
        inventory.setItem(0, old);
        inventory.setItem(1, fresh);
        java.util.function.Function<ItemStack, ItemStack> resolver = observed -> registered.getItem();
        Class<?> engine = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Method previewMethod = engine.getDeclaredMethod(
                "preview", org.bukkit.inventory.Inventory.class, java.util.function.Function.class);
        previewMethod.setAccessible(true);
        Object plan = previewMethod.invoke(null, inventory, resolver);
        Method eligibleMethod = plan.getClass().getDeclaredMethod("eligible");
        eligibleMethod.setAccessible(true);
        require((int) eligibleMethod.invoke(plan) == 1, "native live-batch expected one safe style candidate");

        java.util.UUID owner = java.util.UUID.randomUUID();
        Method apply = engine.getDeclaredMethod(
                "apply", org.bukkit.inventory.Inventory.class, plan.getClass(),
                java.util.function.Function.class, Path.class, java.util.UUID.class);
        apply.setAccessible(true);
        var receipts = (java.util.List<?>) apply.invoke(null, inventory, plan, resolver, directory, owner);
        require(receipts.size() == 1, "native live-batch expected one exact-original private backup");
        Object firstReceipt = receipts.getFirst();
        Method receiptId = firstReceipt.getClass().getDeclaredMethod("id");
        receiptId.setAccessible(true);
        java.util.UUID receipt = (java.util.UUID) receiptId.invoke(firstReceipt);
        ItemStack updated = inventory.getItem(0);
        require(updated != null && updated.getAmount() == 63 && updated.isSimilar(fresh),
                "native live-batch verified player slot did not become current registered CARBONADO");
        require(Arrays.equals(oldBytes, readDoctorBackup(directory, owner, receipt).serializeAsBytes()),
                "native live-batch original receipt was not exact");
        require(Arrays.equals(oldBytes, old.serializeAsBytes())
                        && Arrays.equals(registeredBytes, registered.getItem().serializeAsBytes()),
                "native live-batch altered old source or global registered template");
        Files.write(oldFile, oldBytes);
        Files.write(freshFile, freshBytes);
        Files.write(updatedFile, updated.serializeAsBytes());
        Files.writeString(receiptFile, owner + ":" + receipt);
        Bukkit.getLogger().info("SFL_LIVE_BATCH_CARBONADO_SEED_PASS");
    }

    /**
     * Native two-boot acceptance of the private container export engine, using
     * a synthetic 54-slot inventory and an actual registered CARBONADO template.
     * Restart reopens saved receipts; this does not test physical world-chest
     * persistence or any QuickShop record. No production inventory is used.
     */
    private static void verifyNativeChestEvidence(Path folder, boolean restart) throws Exception {
        SlimefunItem registered = SlimefunItem.getById("CARBONADO");
        require(registered != null, "native chest export registered CARBONADO unavailable");
        Path directory = folder.resolve("identity-chest-private-evidence");
        Path originalFile = directory.resolve("old-chest.bin");
        Path currentFile = directory.resolve("current-template.bin");
        Path receiptFile = directory.resolve("receipts.txt");

        if (restart) {
            byte[] oldBytes = Files.readAllBytes(originalFile);
            byte[] currentBytes = Files.readAllBytes(currentFile);
            ItemStack old = ItemStack.deserializeBytes(oldBytes);
            ItemStack current = ItemStack.deserializeBytes(currentBytes);
            ItemStack registeredNative = ItemStack.deserializeBytes(registered.getItem().serializeAsBytes());
            require(old.getAmount() == 63 && current.getAmount() == 1
                            && !old.isSimilar(current) && current.isSimilar(registeredNative),
                    "native chest evidence original/current identity changed after restart");
            String[] tokens = Files.readString(receiptFile).trim().split(":", -1);
            require(tokens.length == 3, "native chest evidence receipt is malformed");
            java.util.UUID owner = java.util.UUID.fromString(tokens[0]);
            java.util.UUID oldReceipt = java.util.UUID.fromString(tokens[1]);
            java.util.UUID templateReceipt = java.util.UUID.fromString(tokens[2]);
            require(Arrays.equals(oldBytes, readDoctorBackup(directory, owner, oldReceipt).serializeAsBytes())
                            && Arrays.equals(
                                    currentBytes,
                                    readDoctorBackup(directory, owner, templateReceipt).serializeAsBytes()),
                    "native chest evidence private original/template bytes changed after restart");
            verifyPrivateEvidenceReplay(directory, owner, oldReceipt, templateReceipt,
                    "STYLE_ONLY_CANDIDATE", true);
            verifyPhysicalChestPersistence(directory, true, oldBytes, currentBytes, registeredNative.serializeAsBytes());
            verifyPhysicalChestStyleMigration(directory, true, oldBytes, currentBytes, registeredNative.serializeAsBytes());
            require(Arrays.equals(oldBytes, Files.readAllBytes(originalFile))
                            && Arrays.equals(currentBytes, Files.readAllBytes(currentFile)),
                    "native chest evidence source files were rewritten after restart");
            Bukkit.getLogger().info("SFL_CHEST_ITEM_EVIDENCE_RESTART_PASS");
            return;
        }

        Files.createDirectories(directory);
        byte[] registeredBytes = registered.getItem().serializeAsBytes();
        ItemStack current = ItemStack.deserializeBytes(registeredBytes);
        current.setAmount(1);
        ItemStack old = ItemStack.deserializeBytes(registeredBytes);
        old.setAmount(63);
        var name = old.getItemMeta().displayName();
        require(name != null, "native chest evidence CARBONADO display name missing");
        boolean bold = name.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, !bold)));
        byte[] oldBytes = old.serializeAsBytes();
        byte[] currentBytes = current.serializeAsBytes();
        var chest = Bukkit.createInventory(null, 54);
        chest.setItem(53, old);
        java.util.UUID owner = java.util.UUID.randomUUID();

        Class<?> engine = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Method export = engine.getDeclaredMethod(
                "captureContainer",
                org.bukkit.inventory.Inventory.class,
                int.class,
                java.util.function.Function.class,
                Path.class,
                java.util.UUID.class);
        export.setAccessible(true);
        java.util.function.Function<ItemStack, ItemStack> resolver = observed -> registered.getItem();
        Object receipts = export.invoke(null, chest, 53, resolver, directory, owner);
        Method oldId = receipts.getClass().getDeclaredMethod("mainReceipt");
        Method templateId = receipts.getClass().getDeclaredMethod("offhandReceipt");
        oldId.setAccessible(true);
        templateId.setAccessible(true);
        java.util.UUID originalReceipt = (java.util.UUID) oldId.invoke(receipts);
        java.util.UUID currentReceipt = (java.util.UUID) templateId.invoke(receipts);
        require(Arrays.equals(oldBytes, readDoctorBackup(directory, owner, originalReceipt).serializeAsBytes())
                        && Arrays.equals(
                                currentBytes,
                                readDoctorBackup(directory, owner, currentReceipt).serializeAsBytes()),
                "native chest evidence private receipts failed exact-byte seed readback");
        verifyPrivateEvidenceReplay(directory, owner, originalReceipt, currentReceipt,
                "STYLE_ONLY_CANDIDATE", false);
        verifyPhysicalChestPersistence(directory, false, oldBytes, currentBytes, registeredBytes);
        verifyPhysicalChestStyleMigration(directory, false, oldBytes, currentBytes, registeredBytes);
        require(Arrays.equals(oldBytes, chest.getItem(53).serializeAsBytes())
                        && chest.getItem(52) == null
                        && Arrays.equals(registeredBytes, registered.getItem().serializeAsBytes()),
                "native chest evidence export modified old stock, adjacent slots or registered template");
        Files.write(originalFile, oldBytes);
        Files.write(currentFile, currentBytes);
        Files.writeString(receiptFile, owner + ":" + originalReceipt + ":" + currentReceipt);
        Bukkit.getLogger().info("SFL_CHEST_ITEM_EVIDENCE_SEED_PASS");
    }


    /**
     * Disposable native WORLD chest, not Bukkit.createInventory. The first boot
     * seeds source-derived old/current CARBONADO plus one typed-owner negative
     * control. The second boot reopens the same physical chunk and proves exact
     * stock/quantity/PDC persistence. Calls run on the owning 0,0 Folia region.
     * This is NOT a genuine historical AlbionMC chest or QuickShop transaction.
     */
    private static void verifyPhysicalChestPersistence(
            Path directory, boolean restart, byte[] oldBytes, byte[] currentBytes, byte[] registeredBytes)
            throws Exception {
        Path fixture = directory.resolve("physical-world-chest");
        var block = Bukkit.getWorlds().getFirst().getBlockAt(3, 180, 3);
        if (restart) {
            require(block.getType() == Material.CHEST, "disposable physical chest was not persisted");
        } else {
            require(block.getType().isAir(), "physical chest fixture position is occupied");
            block.setType(Material.CHEST);
        }
        require(block.getState() instanceof org.bukkit.block.Chest, "physical chest block state missing");
        var contents = ((org.bukkit.block.Chest) block.getState()).getBlockInventory();
        require(contents.getSize() == 27, "physical fixture must be one vanilla chest");

        Path savedOld = fixture.resolve("old.bin");
        Path savedCurrent = fixture.resolve("current.bin");
        Path savedTyped = fixture.resolve("typed-owner.bin");
        Path receiptsFile = fixture.resolve("receipts.txt");
        if (restart) {
            byte[] typedBytes = Files.readAllBytes(savedTyped);
            require(Arrays.equals(oldBytes, Files.readAllBytes(savedOld))
                            && Arrays.equals(currentBytes, Files.readAllBytes(savedCurrent)),
                    "physical chest evidence files were changed");
            require(contents.getItem(26) != null && contents.getItem(25) != null
                            && contents.getItem(24) != null && contents.getItem(23) == null
                            && Arrays.equals(oldBytes, contents.getItem(26).serializeAsBytes())
                            && Arrays.equals(currentBytes, contents.getItem(25).serializeAsBytes())
                            && Arrays.equals(typedBytes, contents.getItem(24).serializeAsBytes()),
                    "actual saved chest lost/rewrote/duplicated item metadata or slots");
            require(contents.getItem(26).getAmount() == 63
                            && contents.getItem(25).getAmount() == 1
                            && contents.getItem(24).getAmount() == 9
                            && Long.valueOf(75123L).equals(contents.getItem(24).getItemMeta()
                                    .getPersistentDataContainer().get(OWNER, PersistentDataType.LONG)),
                    "physical chest count or typed owner changed across restart");
            var receiptLines = Files.readAllLines(receiptsFile);
            require(receiptLines.size() == 3, "physical chest original receipts missing");
            var owner = java.util.UUID.fromString(receiptLines.get(0));
            verifyPrivateEvidenceReplay(fixture, owner,
                    java.util.UUID.fromString(receiptLines.get(1)),
                    java.util.UUID.fromString(receiptLines.get(2)),
                    "STYLE_ONLY_CANDIDATE", true);
            verifySyntheticShopMatcherReplay(
                    oldBytes, currentBytes, typedBytes, contents.getItem(26), contents.getItem(25),
                    contents.getItem(24), true);
            require(Arrays.equals(registeredBytes, SlimefunItem.getById("CARBONADO").getItem().serializeAsBytes()),
                    "physical chest restart altered registered template");
            Bukkit.getLogger().info("SFL_PHYSICAL_CHEST_RESTART_PASS");
            return;
        }

        ItemStack old = ItemStack.deserializeBytes(oldBytes);
        ItemStack current = ItemStack.deserializeBytes(currentBytes);
        ItemStack typed = ItemStack.deserializeBytes(oldBytes);
        typed.setAmount(9);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 75123L));
        byte[] typedBytes = typed.serializeAsBytes();
        contents.setItem(26, old);
        contents.setItem(25, current);
        contents.setItem(24, typed);
        require(Arrays.equals(oldBytes, contents.getItem(26).serializeAsBytes())
                        && Arrays.equals(currentBytes, contents.getItem(25).serializeAsBytes())
                        && Arrays.equals(typedBytes, contents.getItem(24).serializeAsBytes())
                        && contents.getItem(23) == null,
                "physical chest seed failed to retain all three source stacks");
        Files.createDirectories(fixture);
        Files.write(savedOld, oldBytes);
        Files.write(savedCurrent, currentBytes);
        Files.write(savedTyped, typedBytes);
        java.util.UUID owner = java.util.UUID.randomUUID();
        Class<?> engine = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Method capture = engine.getDeclaredMethod(
                "captureContainer", org.bukkit.inventory.Inventory.class, int.class,
                java.util.function.Function.class, Path.class, java.util.UUID.class);
        capture.setAccessible(true);
        java.util.function.Function<ItemStack, ItemStack> resolve =
                observed -> SlimefunItem.getById("CARBONADO").getItem();
        Object receiptPair = capture.invoke(null, contents, 26, resolve, fixture, owner);
        Method first = receiptPair.getClass().getDeclaredMethod("mainReceipt");
        Method second = receiptPair.getClass().getDeclaredMethod("offhandReceipt");
        first.setAccessible(true);
        second.setAccessible(true);
        var originalReceipt = (java.util.UUID) first.invoke(receiptPair);
        var referenceReceipt = (java.util.UUID) second.invoke(receiptPair);
        verifyPrivateEvidenceReplay(fixture, owner, originalReceipt, referenceReceipt,
                "STYLE_ONLY_CANDIDATE", false);
        verifySyntheticShopMatcherReplay(
                oldBytes, currentBytes, typedBytes, contents.getItem(26), contents.getItem(25),
                contents.getItem(24), false);
        require(Arrays.equals(oldBytes, contents.getItem(26).serializeAsBytes())
                        && Arrays.equals(typedBytes, contents.getItem(24).serializeAsBytes())
                        && Arrays.equals(registeredBytes, SlimefunItem.getById("CARBONADO").getItem().serializeAsBytes()),
                "physical chest evidence capture changed stock, typed owner or registered template");
        Files.write(receiptsFile, List.of(owner.toString(), originalReceipt.toString(), referenceReceipt.toString()));
        Bukkit.getLogger().info("SFL_PHYSICAL_CHEST_SEED_PASS");
    }


    /**
     * Isolated native physical-chest write/restart proof for the existing
     * bounded player-inventory repair engine. This is intentionally NOT
     * exposed as a production chest command. The test creates its own
     * fresh single chest and never touches an existing QuickShop record.
     *
     * A separate read-only physical chest fixture still verifies untouched
     * historical stock. The migrated test slot must preserve its exact
     * original private receipt, amount and registered Slimefun identity,
     * while the functional typed-PDC negative control remains unchanged.
     */
    private static void verifyPhysicalChestStyleMigration(
            Path directory, boolean restart, byte[] oldBytes, byte[] currentBytes, byte[] registeredBytes)
            throws Exception {
        var block = Bukkit.getWorlds().getFirst().getBlockAt(8, 180, 3);
        Path evidence = directory.resolve("physical-chest-style-migration");
        Path migratedFile = evidence.resolve("migrated.bin");
        Path negativeFile = evidence.resolve("typed-negative.bin");
        Path receiptFile = evidence.resolve("receipt.txt");
        if (restart) {
            require(block.getType() == Material.CHEST, "migrated physical chest did not persist");
        } else {
            require(block.getType().isAir(), "migrated fixture requires an empty disposable block");
            block.setType(Material.CHEST);
        }
        var chest = (org.bukkit.block.Chest) block.getState();
        var slots = chest.getBlockInventory();
        require(slots.getSize() == 27, "migration fixture must target a single 27-slot chest");
        ItemStack current = ItemStack.deserializeBytes(currentBytes);
        ItemStack typed = ItemStack.deserializeBytes(oldBytes);
        typed.setAmount(9);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 75123L));
        byte[] typedBytes = typed.serializeAsBytes();
        require(Arrays.equals(registeredBytes, SlimefunItem.getById("CARBONADO").getItem().serializeAsBytes()),
                "physical chest migration registry changed");

        if (restart) {
            String[] tokens = Files.readString(receiptFile).trim().split(":", -1);
            require(tokens.length == 2, "physical chest migration receipt missing");
            java.util.UUID owner = java.util.UUID.fromString(tokens[0]);
            java.util.UUID receipt = java.util.UUID.fromString(tokens[1]);
            ItemStack migrated = slots.getItem(0);
            require(migrated != null && migrated.getAmount() == 63
                            && Arrays.equals(Files.readAllBytes(migratedFile), migrated.serializeAsBytes())
                            && migrated.isSimilar(current),
                    "saved physical chest style migration lost repaired identity or amount");
            require(slots.getItem(1) != null
                            && Arrays.equals(currentBytes, slots.getItem(1).serializeAsBytes())
                            && slots.getItem(2) != null
                            && Arrays.equals(typedBytes, slots.getItem(2).serializeAsBytes())
                            && Arrays.equals(typedBytes, Files.readAllBytes(negativeFile))
                            && slots.getItem(3) == null,
                    "physical chest migration changed fresh stock, typed-PDC stock or a spare slot");
            ItemStack backedUp = readDoctorBackup(evidence, owner, receipt);
            require(Arrays.equals(oldBytes, backedUp.serializeAsBytes())
                            && matchesDoctorBackup(backedUp, migrated, current,
                                    ItemStack.deserializeBytes(registeredBytes)),
                    "physical chest migration original bytes or postrestart rollback proof failed");
            var merge = Bukkit.createInventory(null, 9);
            require(merge.addItem(migrated.clone(), current.clone()).isEmpty()
                            && merge.getItem(0).getAmount() == 64 && merge.getItem(1) == null,
                    "persisted repaired physical chest 63+1 native merge did not conserve items");
            Bukkit.getLogger().info("SFL_PHYSICAL_CHEST_STYLE_MIGRATION_RESTART_PASS");
            return;
        }

        ItemStack old = ItemStack.deserializeBytes(oldBytes);
        require(!old.isSimilar(current), "physical migration source is not a historical style mismatch");
        slots.setItem(0, old);
        slots.setItem(1, current);
        slots.setItem(2, typed);
        require(Arrays.equals(oldBytes, slots.getItem(0).serializeAsBytes())
                        && Arrays.equals(typedBytes, slots.getItem(2).serializeAsBytes()),
                "physical migration fixture failed to seed exact native stock");
        java.util.function.Function<ItemStack, ItemStack> resolver =
                observed -> SlimefunItem.getById("CARBONADO").getItem();
        Class<?> engine = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorLiveItemUpdate");
        Method preview = engine.getDeclaredMethod(
                "preview", org.bukkit.inventory.Inventory.class, java.util.function.Function.class);
        preview.setAccessible(true);
        Object plan = preview.invoke(null, slots, resolver);
        Method eligible = plan.getClass().getDeclaredMethod("eligible");
        eligible.setAccessible(true);
        require((int) eligible.invoke(plan) == 1,
                "physical migration must offer only the historical style stack, never typed PDC");
        Method apply = engine.getDeclaredMethod(
                "apply", org.bukkit.inventory.Inventory.class, plan.getClass(),
                java.util.function.Function.class, Path.class, java.util.UUID.class);
        apply.setAccessible(true);
        Files.createDirectories(evidence);
        java.util.UUID owner = java.util.UUID.randomUUID();
        var receipts = (java.util.List<?>) apply.invoke(null, slots, plan, resolver, evidence, owner);
        require(receipts.size() == 1, "physical chest migration must back up exactly one original stack");
        Method receiptId = receipts.getFirst().getClass().getDeclaredMethod("id");
        receiptId.setAccessible(true);
        java.util.UUID receipt = (java.util.UUID) receiptId.invoke(receipts.getFirst());
        ItemStack migrated = slots.getItem(0);
        require(migrated != null && migrated.getAmount() == 63 && migrated.isSimilar(current)
                        && Arrays.equals(oldBytes, readDoctorBackup(evidence, owner, receipt).serializeAsBytes())
                        && Arrays.equals(currentBytes, slots.getItem(1).serializeAsBytes())
                        && Arrays.equals(typedBytes, slots.getItem(2).serializeAsBytes())
                        && slots.getItem(3) == null
                        && Arrays.equals(registeredBytes, SlimefunItem.getById("CARBONADO")
                                .getItem().serializeAsBytes()),
                "native physical chest update changed original backup, amount, typed PDC or other stock");
        Files.write(migratedFile, migrated.serializeAsBytes());
        Files.write(negativeFile, typedBytes);
        Files.writeString(receiptFile, owner + ":" + receipt);
        Bukkit.getLogger().info("SFL_PHYSICAL_CHEST_STYLE_MIGRATION_SEED_PASS");
    }

    /**
     * Tests the production QuickShop triage classifier with native Paper/Folia
     * ItemStack.isSimilar semantics on both JVM boots using physical chest stock.
     * The predicate only models QuickShop's default similarity matcher: NO
     * QuickShop-Hikari binary/API or persisted shop record is loaded by this test.
     */
    private static void verifySyntheticShopMatcherReplay(
            byte[] oldBytes, byte[] currentBytes, byte[] typedBytes,
            ItemStack oldStock, ItemStack currentStock, ItemStack typedStock, boolean restart)
            throws Exception {
        Class<?> inspector = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorQuickShopReadOnly");
        Method compare = inspector.getDeclaredMethod(
                "compare", ItemStack.class, ItemStack.class, ItemStack.class, java.util.function.Predicate.class);
        compare.setAccessible(true);
        ItemStack old = ItemStack.deserializeBytes(oldBytes);
        ItemStack current = ItemStack.deserializeBytes(currentBytes);
        java.util.function.Predicate<ItemStack> matcher = old::isSimilar;

        Object oldAndOld = compare.invoke(null, old, oldStock, current, matcher);
        require("STYLE_ONLY_CANDIDATE".equals(shopDifference(oldAndOld, "saleDifference"))
                        && "STYLE_ONLY_CANDIDATE".equals(shopDifference(oldAndOld, "stockDifference"))
                        && flag(oldAndOld, "saleMatchesStock")
                        && !flag(oldAndOld, "matcherAcceptsFresh")
                        && flag(oldAndOld, "matcherAcceptsStock"),
                "native QuickShop model failed historical sale/reference/stock classification");

        Object oldAndFresh = compare.invoke(null, old, currentStock, current, matcher);
        require("STYLE_ONLY_CANDIDATE".equals(shopDifference(oldAndFresh, "saleDifference"))
                        && "CURRENT".equals(shopDifference(oldAndFresh, "stockDifference"))
                        && !flag(oldAndFresh, "saleMatchesStock")
                        && !flag(oldAndFresh, "matcherAcceptsFresh")
                        && !flag(oldAndFresh, "matcherAcceptsStock"),
                "native QuickShop model treated fresh stock as historical shop reference");

        Object oldAndOwned = compare.invoke(null, old, typedStock, current, matcher);
        require("STYLE_ONLY_CANDIDATE".equals(shopDifference(oldAndOwned, "saleDifference"))
                        && "FUNCTIONAL_METADATA".equals(shopDifference(oldAndOwned, "stockDifference"))
                        && !flag(oldAndOwned, "saleMatchesStock")
                        && !flag(oldAndOwned, "matcherAcceptsStock"),
                "native QuickShop model ignored a typed owner PDC difference");

        // Exercise the production cached-YAML parser on the real Paper/Folia
        // ItemStack serializer on BOTH JVM boots. Unlike the mock matcher
        // model above, the source here is the actual world-persisted chest.
        Method compareCache = inspector.getDeclaredMethod(
                "compareCached", String.class, ItemStack.class, ItemStack.class, ItemStack.class);
        compareCache.setAccessible(true);
        org.bukkit.configuration.file.YamlConfiguration cachedYaml =
                new org.bukkit.configuration.file.YamlConfiguration();
        cachedYaml.set("item", old);
        String originalCache = cachedYaml.saveToString();
        Object cacheOldStock = compareCache.invoke(null, originalCache, old, oldStock, current);
        require("STYLE_ONLY_CANDIDATE".equals(shopDifference(cacheOldStock, "cachedDifference"))
                        && flag(cacheOldStock, "cachedMatchesView")
                        && flag(cacheOldStock, "cachedMatchesStock"),
                "native QuickShop cache parser lost historical matching old chest stock");
        cachedYaml.set("item", current);
        Object cacheFresh = compareCache.invoke(null, cachedYaml.saveToString(), old, currentStock, current);
        require("CURRENT".equals(shopDifference(cacheFresh, "cachedDifference"))
                        && !flag(cacheFresh, "cachedMatchesView")
                        && flag(cacheFresh, "cachedMatchesStock"),
                "native QuickShop cache parser confused modern internal item with event-processed old view");
        cachedYaml.set("item", typedStock);
        Object cacheTyped = compareCache.invoke(null, cachedYaml.saveToString(), old, typedStock, current);
        require("FUNCTIONAL_METADATA".equals(shopDifference(cacheTyped, "cachedDifference"))
                        && !flag(cacheTyped, "cachedMatchesView")
                        && flag(cacheTyped, "cachedMatchesStock"),
                "native QuickShop cache parser discarded typed owner metadata");

        ItemStack wrongId = ItemStack.deserializeBytes(oldBytes);
        wrongId.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("slimefun", "slimefun_item"),
                PersistentDataType.STRING, "CARBON_CHUNK"));
        require(wrongId.getType() == old.getType(), "native wrong-ID negative control changed material");
        boolean refusedWrongId = false;
        try {
            compare.invoke(null, old, wrongId, current, matcher);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            refusedWrongId = exception.getCause() instanceof java.io.IOException;
        }
        require(refusedWrongId, "native QuickShop model accepted a different Slimefun ID");

        cachedYaml.set("item", wrongId);
        boolean rejectedWrongCachedId = false;
        try {
            compareCache.invoke(null, cachedYaml.saveToString(), old, oldStock, current);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            rejectedWrongCachedId = exception.getCause() instanceof java.io.IOException;
        }
        require(rejectedWrongCachedId, "native QuickShop cache accepted same-material different Slimefun ID");
        require(Arrays.equals(oldBytes, old.serializeAsBytes())
                        && Arrays.equals(currentBytes, current.serializeAsBytes())
                        && Arrays.equals(oldBytes, oldStock.serializeAsBytes())
                        && Arrays.equals(currentBytes, currentStock.serializeAsBytes())
                        && Arrays.equals(typedBytes, typedStock.serializeAsBytes()),
                "native QuickShop cache deserialization changed a persisted source item");
        Bukkit.getLogger().info("SFL_QUICKSHOP_CACHED_ITEM_MODEL_"
                + (restart ? "RESTART" : "SEED") + "_PASS");

        boolean refusedMatcherMutation = false;
        java.util.function.Predicate<ItemStack> mutating = candidate -> {
            candidate.setAmount(candidate.getAmount() + 1);
            return true;
        };
        try {
            compare.invoke(null, old, oldStock, current, mutating);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            refusedMatcherMutation = exception.getCause() instanceof java.io.IOException;
        }
        require(refusedMatcherMutation, "native QuickShop model trusted a mutating external matcher");

        require(Arrays.equals(oldBytes, old.serializeAsBytes())
                        && Arrays.equals(currentBytes, current.serializeAsBytes())
                        && Arrays.equals(oldBytes, oldStock.serializeAsBytes())
                        && Arrays.equals(currentBytes, currentStock.serializeAsBytes())
                        && Arrays.equals(typedBytes, typedStock.serializeAsBytes()),
                "native QuickShop diagnostic mutated historical stock or reference");
        Bukkit.getLogger().info("SFL_QUICKSHOP_MATCHER_MODEL_" + (restart ? "RESTART" : "SEED") + "_PASS");
    }

    private static String shopDifference(Object comparison, String accessor) throws Exception {
        Method method = comparison.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(comparison).toString();
    }

    /** Replays existing synthetic receipts on each JVM; never recaptures or repairs an item. */
    private static void verifyPrivateEvidenceReplay(
            Path directory, java.util.UUID owner, java.util.UUID originalReceipt,
            java.util.UUID referenceReceipt, String expectedDifference, boolean restart) throws Exception {
        SlimefunItem registered = SlimefunItem.getById("CARBONADO");
        require(registered != null, "private evidence replay registered CARBONADO unavailable");
        byte[] registeredBytes = registered.getItem().serializeAsBytes();
        Path backups = directory.resolve("recovery/identity");
        Path originalPath = backups.resolve(owner + "-" + originalReceipt + ".bin");
        Path referencePath = backups.resolve(owner + "-" + referenceReceipt + ".bin");
        byte[] originalBytes = Files.readAllBytes(originalPath);
        byte[] referenceBytes = Files.readAllBytes(referencePath);

        Class<?> audit = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackEvidenceAudit");
        Method inspect = audit.getDeclaredMethod("inspect", Path.class, java.util.UUID.class,
                java.util.UUID.class, java.util.UUID.class, java.util.function.Function.class);
        inspect.setAccessible(true);
        java.util.function.Function<ItemStack, ItemStack> resolver = ignored -> registered.getItem();
        Object assessment = inspect.invoke(null, directory, owner, originalReceipt, referenceReceipt, resolver);
        Method difference = assessment.getClass().getDeclaredMethod("savedDifference");
        difference.setAccessible(true);
        require(expectedDifference.equals(difference.invoke(assessment).toString())
                        && flag(assessment, "savedReferenceStillRegistered")
                        && !flag(assessment, "historicalMatchesCurrent")
                        && flag(assessment, "strictStyleOnlyCandidate")
                                == "STYLE_ONLY_CANDIDATE".equals(expectedDifference),
                "private evidence replay classification/eligibility drifted: " + expectedDifference);

        ItemStack drifted = ItemStack.deserializeBytes(registeredBytes);
        drifted.editMeta(meta -> meta.lore(List.of(Component.text("Synthetic changed registration"))));
        byte[] driftedBytes = drifted.serializeAsBytes();
        java.util.function.Function<ItemStack, ItemStack> changed = ignored -> drifted;
        Object afterDrift = inspect.invoke(null, directory, owner, originalReceipt, referenceReceipt, changed);
        require(expectedDifference.equals(difference.invoke(afterDrift).toString())
                        && !flag(afterDrift, "savedReferenceStillRegistered")
                        && !flag(afterDrift, "historicalMatchesCurrent")
                        && !flag(afterDrift, "strictStyleOnlyCandidate"),
                "private evidence replay trusted a changed registered template");
        java.util.function.Function<ItemStack, ItemStack> missing = ignored -> null;
        Object afterRemoval = inspect.invoke(null, directory, owner, originalReceipt, referenceReceipt, missing);
        require(expectedDifference.equals(difference.invoke(afterRemoval).toString())
                        && !flag(afterRemoval, "savedReferenceStillRegistered")
                        && !flag(afterRemoval, "historicalMatchesCurrent")
                        && !flag(afterRemoval, "strictStyleOnlyCandidate"),
                "private evidence replay treated missing registration as repair permission");

        boolean refusedOtherOwner = false;
        try {
            inspect.invoke(null, directory, java.util.UUID.randomUUID(), originalReceipt, referenceReceipt, resolver);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            require(exception.getCause() instanceof java.io.IOException,
                    "private evidence wrong-owner refusal had an unexpected cause");
            refusedOtherOwner = true;
        }
        require(refusedOtherOwner, "private evidence replay accepted another operator's receipt");
        require(Arrays.equals(originalBytes, Files.readAllBytes(originalPath))
                        && Arrays.equals(referenceBytes, Files.readAllBytes(referencePath))
                        && Arrays.equals(registeredBytes, registered.getItem().serializeAsBytes())
                        && Arrays.equals(driftedBytes, drifted.serializeAsBytes()),
                "private evidence replay modified original bytes or a registered template");
        try (var files = Files.list(backups)) {
            require(files.count() == 2, "private evidence replay created or removed a receipt");
        }
        Bukkit.getLogger().info("SFL_PRIVATE_EVIDENCE_REPLAY_" + expectedDifference
                + (restart ? "_RESTART_PASS" : "_SEED_PASS"));
    }

    private static void verifyHistoricalPresentation(Path folder, boolean restart) throws Exception {
        // Exercise both reported AlbionMC IDs using current actual registered
        // Paper item templates in addition to the purpose-built fixture.
        // These are synthetic styling controls, NOT historic world originals.
        for (String id : List.of("RAW_CARBONADO", "CARBON_CHUNK")) {
            verifyRegisteredCarbonHistory(folder, id, restart);
        }
        verifyStaticCarbonadoLore(folder, restart);
        verifyOldBukkitTextApiMatrix(folder, restart);
        verifyEvidenceExport(folder, restart);
        verifyLivePlayerInventoryBatch(folder, restart);
        verifyNativeChestEvidence(folder, restart);
        Path oldFile = folder.resolve("identity-historical-old.bin");
        Path freshFile = folder.resolve("identity-historical-fresh.bin");
        if (restart) {
            byte[] oldBytes = Files.readAllBytes(oldFile);
            byte[] freshBytes = Files.readAllBytes(freshFile);
            ItemStack old = ItemStack.deserializeBytes(oldBytes);
            ItemStack fresh = ItemStack.deserializeBytes(freshBytes);
            require(old.getAmount() == 63 && fresh.getAmount() == 1, "historical restart quantities");
            require(!old.isSimilar(fresh), "historical mismatch survived restart");
            Object check = historical(old, fresh);
            require(flag(check, "plainTextSame") && flag(check, "nonPresentationSame")
                            && flag(check, "candidate"),
                    "historical cosmetic candidate survived persisted item bytes");
            require(capacity(old) == 1, "historical restart free-capacity check");
            ItemStack repaired = ItemStack.deserializeBytes(
                    Files.readAllBytes(folder.resolve("identity-historical-repaired.bin")));
            require(repaired.getAmount() == 63 && repaired.isSimilar(fresh),
                    "repaired historical item retained amount and current identity after separate Paper boot");
            var merged = Bukkit.createInventory(null, 9);
            require(merged.addItem(repaired, fresh).isEmpty(), "repaired native restart inventory merge overflow");
            require(merged.getItem(0).getAmount() == 64 && merged.getItem(1) == null,
                    "repaired historical stack did not natively merge into exact 64");
            var backupDirectory = folder.resolve("recovery/identity");
            try (var entries = Files.list(backupDirectory)) {
                var backups = entries.filter(Files::isRegularFile).toList();
                require(backups.size() == 1 && Arrays.equals(oldBytes, Files.readAllBytes(backups.get(0))),
                        "persisted original historical backup did not survive restart byte-for-byte");
                String name = backups.getFirst().getFileName().toString();
                java.util.UUID owner = java.util.UUID.fromString(name.substring(0, 36));
                java.util.UUID receipt = java.util.UUID.fromString(name.substring(37, 73));
                ItemStack verified = readDoctorBackup(folder, owner, receipt);
                require(Arrays.equals(oldBytes, verified.serializeAsBytes()),
                        "native postrestart owner-scoped backup did not restore exact original bytes");
                require(matchesDoctorBackup(verified, repaired, fresh, fresh),
                        "native postrestart backup/held-item verification failed");
                ItemStack modified = ItemStack.deserializeBytes(repaired.serializeAsBytes());
                modified.setAmount(62);
                require(!matchesDoctorBackup(verified, modified, fresh, fresh),
                        "postrestart backup verification ignored changed quantity");
                modified = ItemStack.deserializeBytes(repaired.serializeAsBytes());
                modified.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.INTEGER, 77));
                require(!matchesDoctorBackup(verified, modified, fresh, fresh),
                        "postrestart backup verification ignored typed PDC mismatch");
            }
            require(Arrays.equals(oldBytes, Files.readAllBytes(oldFile))
                            && Arrays.equals(freshBytes, Files.readAllBytes(freshFile)),
                    "read-only historical restart modified persisted evidence");
            return;
        }

        ItemStack fresh = fixture();
        fresh.setAmount(1);
        ItemStack old = new ItemStack(fresh);
        old.setAmount(63);
        old.editMeta(meta -> meta.displayName(meta.displayName().decoration(TextDecoration.BOLD, false)));
        byte[] oldBytes = old.serializeAsBytes();
        byte[] freshBytes = fresh.serializeAsBytes();
        require(!old.isSimilar(fresh), "historical decoration mismatch must prevent vanilla stacking");
        Object check = historical(old, fresh);
        require(flag(check, "plainTextSame") && flag(check, "nonPresentationSame")
                        && flag(check, "candidate"),
                "historical display-only candidate has strict non-presentation equality");
        require(capacity(old) == 1, "historical 63/64 has exactly one free space");
        ItemStack full = new ItemStack(old);
        full.setAmount(64);
        require(capacity(full) == 0, "historical 64/64 full-capacity control");

        ItemStack typed = new ItemStack(old);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.INTEGER, 77));
        Object unsafe = historical(typed, fresh);
        require(flag(unsafe, "plainTextSame") && !flag(unsafe, "nonPresentationSame")
                        && !flag(unsafe, "candidate"),
                "typed PDC difference was incorrectly marked cosmetic");
        require(repairCandidate(typed, fresh, fresh) == null,
                "repair allowed a historical typed PDC difference");
        require(repairCandidate(old, fresh, old) == null,
                "repair accepted a reference which does not match the registered template");
        ItemStack repaired = repairCandidate(old, fresh, fresh);
        require(repaired != null && repaired.getAmount() == 63 && repaired.isSimilar(fresh),
                "synthetic historical presentation repair did not preserve strict identity and quantity");
        require(!old.isSimilar(fresh), "repair mutated original historical input");
        Path backup = backupHistorical(folder, oldBytes);
        require(Arrays.equals(oldBytes, Files.readAllBytes(backup)),
                "repair backup changed the original bytes");
        require(ItemStack.deserializeBytes(Files.readAllBytes(backup)).isSimilar(old),
                "repair backup lost original native metadata");
        require(Arrays.equals(oldBytes, old.serializeAsBytes())
                        && Arrays.equals(freshBytes, fresh.serializeAsBytes()),
                "synthetic repair mutated historical or reference input");
        Files.write(folder.resolve("identity-historical-repaired.bin"), repaired.serializeAsBytes());
        Files.write(oldFile, oldBytes);
        Files.write(freshFile, freshBytes);
    }

    private static ItemStack repairCandidate(ItemStack old, ItemStack fresh, ItemStack template)
            throws Exception {
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method method = doctor.getDeclaredMethod("prepare", ItemStack.class, ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(null, old, fresh, template);
    }

    private static ItemStack repairStaticLoreCandidate(ItemStack old, ItemStack fresh, ItemStack template)
            throws Exception {
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method method = doctor.getDeclaredMethod("prepareStaticLore", ItemStack.class, ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(null, old, fresh, template);
    }

    private static Path backupHistorical(Path folder, byte[] original) throws Exception {
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method method = doctor.getDeclaredMethod("backup", Path.class, java.util.UUID.class, byte[].class);
        method.setAccessible(true);
        return (Path) method.invoke(null, folder, java.util.UUID.randomUUID(), original);
    }

    private static ItemStack readDoctorBackup(Path folder, java.util.UUID owner, java.util.UUID receipt)
            throws Exception {
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method method = doctor.getDeclaredMethod(
                "readVerifiedBackup", Path.class, java.util.UUID.class, java.util.UUID.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(null, folder, owner, receipt);
    }

    private static boolean matchesDoctorBackup(
            ItemStack original, ItemStack repaired, ItemStack fresh, ItemStack template) throws Exception {
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method method = doctor.getDeclaredMethod(
                "matchesSavedBackup", ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, original, repaired, fresh, template);
    }

    private static Object historical(ItemStack old, ItemStack fresh) throws Exception {
        Class<?> doctor = Class.forName("io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorIdentityCommand");
        Method method = doctor.getDeclaredMethod("inspectHistoricalPresentation", ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return method.invoke(null, old, fresh);
    }

    private static String scanHistorical(ItemStack actual, ItemStack template) throws Exception {
        Class<?> scanner = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.services.stability.HistoricalItemIdentityScanner");
        Method method = scanner.getDeclaredMethod("inspect", ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return String.valueOf(method.invoke(null, actual, template));
    }

    private static int capacity(ItemStack stack) throws Exception {
        Class<?> doctor = Class.forName("io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorIdentityCommand");
        Method method = doctor.getDeclaredMethod("availableCapacity", ItemStack.class);
        method.setAccessible(true);
        return (int) method.invoke(null, stack);
    }

    private static ItemStack fixture() {
        ItemStack item = new ItemStack(Material.PAPER, 17);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Identity fixture").decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(Component.text("Native component preservation")));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.LONG, 77L);
            var model = meta.getCustomModelDataComponent();
            model.setFloats(List.of(2200094.0F));
            model.setFlags(List.of(true));
            model.setStrings(List.of("identity"));
            model.setColors(List.of(Color.RED));
            meta.setCustomModelDataComponent(model);
        });
        return item;
    }

    private static Object inspect(ItemStack left, ItemStack right) throws Exception {
        Class<?> doctor = Class.forName("io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorIdentityCommand");
        Method method = doctor.getDeclaredMethod("inspect", ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return method.invoke(null, left, right);
    }

    private static boolean flag(Object comparison, String name) throws Exception {
        Method method = comparison.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(comparison);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native identity: " + message);
    }
}
