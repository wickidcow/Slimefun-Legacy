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
        require(Arrays.equals(templateBefore, template.serializeAsBytes()), "registered template mutated");
        require(Arrays.equals(untouched, reference.serializeAsBytes()), "reference mutated by control cases");
        verifyHistoricalPresentation(folder, false);
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

    /** Native Paper demonstration for #578: classification only, never a historical repair. */
    private static void verifyHistoricalPresentation(Path folder, boolean restart) throws Exception {
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

    private static Path backupHistorical(Path folder, byte[] original) throws Exception {
        Class<?> doctor = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorHistoricalStackRepair");
        Method method = doctor.getDeclaredMethod("backup", Path.class, java.util.UUID.class, byte[].class);
        method.setAccessible(true);
        return (Path) method.invoke(null, folder, java.util.UUID.randomUUID(), original);
    }

    private static Object historical(ItemStack old, ItemStack fresh) throws Exception {
        Class<?> doctor = Class.forName("io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorIdentityCommand");
        Method method = doctor.getDeclaredMethod("inspectHistoricalPresentation", ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return method.invoke(null, old, fresh);
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
