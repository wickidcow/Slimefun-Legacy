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
        ItemStack tin = SlimefunItem.getById("TIN_INGOT").getItem();
        ItemStack silver = SlimefunItem.getById("SILVER_INGOT").getItem();
        require(tin.getType() == silver.getType() && !tin.isSimilar(silver) && !silver.isSimilar(tin),
                "distinct registered ingot IDs sharing a vanilla material became stack-compatible");
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
            verifyLegacyStyleRecovery(caseFolder, id, oldApi, modern, registered.getItem(), true);
            require(expected.equals(observed),
                    "old Bukkit text API fixture provenance classification drifted after restart");
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
        Bukkit.getLogger().info("SFL_LEGACY_BUKKIT_TEXT_" + id + "_SEED=" + classification);
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
        require(Arrays.equals(legacyBytes, oldApi.serializeAsBytes())
                        && Arrays.equals(currentBytes, modern.serializeAsBytes()),
                "old Bukkit text API probe modified its detached inputs");
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
        require(Arrays.equals(originalBytes, verified.serializeAsBytes())
                        && repaired.getAmount() == 31
                        && repaired.isSimilar(fresh) && fresh.isSimilar(repaired)
                        && matchesDoctorBackup(verified, repaired, fresh, template),
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
        // Reviewed six-runtime baseline at 25a7d561: all seven source-derived
        // fixtures preserve plain text. A future exact native match is welcome;
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
