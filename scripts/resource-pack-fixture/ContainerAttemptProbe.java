package io.github.wickidcow.validation;

import static io.github.wickidcow.validation.ResourcePackDoctorProbe.require;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Native single-use intent checks in a disposable chest; not a hard-crash or live-world migration. */
final class ContainerAttemptProbe {
    private ContainerAttemptProbe() {}

    static void verify(Path directory, boolean restart, byte[] oldBytes, byte[] freshBytes,
            byte[] registeredBytes) throws Exception {
        verifyAttachedLootGuard(restart);
        var block = Bukkit.getWorlds().getFirst().getBlockAt(8, 180, 10);
        require(Bukkit.isOwnedByCurrentRegion(block), "Single-use chest probe ran outside its owning region");
        Path fixture = directory.resolve("physical-chest-single-use");
        if (restart) {
            require(block.getType() == Material.CHEST, "Single-use chest did not persist across JVM restart");
        } else {
            require(block.getType().isAir(), "Single-use disposable chest location already occupied");
            Files.createDirectory(fixture);
            block.setType(Material.CHEST);
        }
        var contents = ((org.bukkit.block.Chest) block.getState()).getBlockInventory();
        require(contents.getSize() == 27, "Single-use probe requires one physical chest");
        ItemStack typed = ItemStack.deserializeBytes(oldBytes);
        typed.setAmount(9);
        typed.editMeta(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey("identity_probe", "owner"), PersistentDataType.LONG, 75123L));
        byte[][] original = new byte[27][];
        original[0] = oldBytes;
        original[1] = freshBytes;
        original[2] = typed.serializeAsBytes();
        original[26] = new ItemStack(Material.STONE, 17).serializeAsBytes();
        ItemStack updated = ItemStack.deserializeBytes(registeredBytes);
        updated.setAmount(63);
        byte[][] proposed = original.clone();
        proposed[0] = updated.serializeAsBytes();

        Class<?> envelope = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorContainerEvidenceEnvelope");
        Class<?> bindingType = Class.forName(envelope.getName() + "$Binding");
        Object binding = method(bindingType, "from", org.bukkit.Location.class,
                org.bukkit.block.BlockState.class, Inventory.class)
                .invoke(null, block.getLocation(), block.getState(), contents);
        Class<?> journal = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorContainerTransactionJournal");
        Method begin = method(journal, "begin", Path.class, UUID.class, UUID.class,
                bindingType, Inventory.class, Function.class);
        Method apply = method(journal, "applyOnce", Path.class, UUID.class, UUID.class,
                bindingType, Inventory.class, Function.class);
        Function<ItemStack, ItemStack> resolver = observed -> SlimefunItem.getById("CARBONADO").getItem();
        Path ids = fixture.resolve("owner-original-failed-success.txt");
        UUID owner;
        UUID receipt;
        UUID failed;
        UUID success;
        if (restart) {
            String[] tokens = Files.readString(ids).trim().split(":", -1);
            require(tokens.length == 4, "Single-use restart identifiers missing");
            owner = UUID.fromString(tokens[0]);
            receipt = UUID.fromString(tokens[1]);
            failed = UUID.fromString(tokens[2]);
            success = UUID.fromString(tokens[3]);
            require(Arrays.deepEquals(proposed, stock(contents)),
                    "Single-use applied chest lost exact stock, typed owner or quantity on restart");
        } else {
            for (int slot : new int[] {0, 1, 2, 26}) {
                contents.setItem(slot, ItemStack.deserializeBytes(original[slot]));
            }
            require(Arrays.deepEquals(original, stock(contents)), "Single-use chest seed is not byte-exact");
            owner = UUID.randomUUID();
            Object captured = method(envelope, "capture", Path.class, UUID.class, bindingType, Inventory.class)
                    .invoke(null, fixture, owner, binding, contents);
            receipt = id(captured);
            failed = id(begin.invoke(null, fixture, owner, receipt, binding, contents, resolver));
            success = id(begin.invoke(null, fixture, owner, receipt, binding, contents, resolver));
            require(!failed.equals(success), "Independent single-use proposals reused an ID");
            Files.writeString(ids, owner + ":" + receipt + ":" + failed + ":" + success,
                    StandardOpenOption.CREATE_NEW);

            Path marker = marker(fixture, owner, failed);
            AtomicInteger writes = new AtomicInteger();
            Inventory hooked = proxy(contents, marker, (slot, count) -> {
                if (count == 1) contents.setItem(26, new ItemStack(Material.STONE, 16));
            }, writes);
            boolean interrupted = false;
            try {
                apply.invoke(null, fixture, owner, failed, binding, hooked, resolver);
            } catch (InvocationTargetException exception) {
                interrupted = exception.getCause() instanceof IOException;
            }
            byte[][] interruptedStock = original.clone();
            interruptedStock[26] = new ItemStack(Material.STONE, 16).serializeAsBytes();
            require(interrupted && writes.get() == 2 && Arrays.deepEquals(interruptedStock, stock(contents)),
                    "Single-use interrupted writer lost own original or overwrote foreign stock");
            Files.write(fixture.resolve("failed-intent.bin"), Files.readAllBytes(marker),
                    StandardOpenOption.CREATE_NEW);
            // Fixture-only restoration of the injected foreign item. The marker
            // must still refuse replay, even though every original byte returns.
            contents.setItem(26, ItemStack.deserializeBytes(original[26]));
            refuseReplay(apply, fixture, owner, failed, binding, contents, resolver);
            Bukkit.getLogger().info("SFL_PHYSICAL_CHEST_SINGLE_USE_FAULT_PASS");
            apply.invoke(null, fixture, owner, success, binding, contents, resolver);
            require(Arrays.deepEquals(proposed, stock(contents)), "Single-use apply did not conserve full chest");
            Files.write(fixture.resolve("success-intent.bin"), Files.readAllBytes(marker(fixture, owner, success)),
                    StandardOpenOption.CREATE_NEW);
        }

        byte[][] saved = (byte[][]) method(envelope, "openVerifiedOriginal", Path.class, UUID.class,
                UUID.class, bindingType).invoke(null, fixture, owner, receipt, binding);
        require(Arrays.deepEquals(original, saved), "Single-use original native envelope changed");
        require(Arrays.equals(Files.readAllBytes(fixture.resolve("failed-intent.bin")),
                        Files.readAllBytes(marker(fixture, owner, failed)))
                        && Arrays.equals(Files.readAllBytes(fixture.resolve("success-intent.bin")),
                        Files.readAllBytes(marker(fixture, owner, success))),
                "Single-use durable intent changed or disappeared");
        // Simulate an old world-save image in this disposable fixture only.
        // Both the failed and successful attempts must reject it after a new
        // JVM boot, without calling a setter or relying on in-memory state.
        contents.setItem(0, ItemStack.deserializeBytes(oldBytes));
        try {
            require(Arrays.deepEquals(original, stock(contents)), "Single-use replay control is not ORIGINAL");
            refuseReplay(apply, fixture, owner, failed, binding, contents, resolver);
            refuseReplay(apply, fixture, owner, success, binding, contents, resolver);
        } finally {
            contents.setItem(0, ItemStack.deserializeBytes(proposed[0]));
        }
        require(Arrays.deepEquals(proposed, stock(contents))
                        && Arrays.equals(registeredBytes, SlimefunItem.getById("CARBONADO").getItem().serializeAsBytes()),
                "Single-use replay probe changed persisted stock or registered template");
        Bukkit.getLogger().info(restart
                ? "SFL_PHYSICAL_CHEST_SINGLE_USE_RESTART_PASS" : "SFL_PHYSICAL_CHEST_SINGLE_USE_SEED_PASS");
    }

    /**
     * Seed real untouched lootable blocks, then prove the production metadata
     * guard still reports their attached table on an independent JVM boot.
     * Never call getInventory, getBlockInventory or populateLoot on these blocks.
     */
    private static void verifyAttachedLootGuard(boolean restart) throws Exception {
        Class<?> preflight = Class.forName(
                "io.github.thebusybiscuit.slimefun4.core.commands.subcommands.DoctorContainerLocalPreflight");
        Method guard = method(preflight, "hasAttachedLootTable", org.bukkit.block.Container.class);
        var table = Bukkit.getLootTable(NamespacedKey.minecraft("chests/simple_dungeon"));
        require(table != null, "Native dungeon loot table missing");
        Material[] materials = {Material.CHEST, Material.BARREL};
        for (int i = 0; i < materials.length; i++) {
            var block = Bukkit.getWorlds().getFirst().getBlockAt(12 + i, 180, 10);
            require(Bukkit.isOwnedByCurrentRegion(block), "Loot guard probe outside owning region");
            if (!restart) {
                require(block.getType().isAir(), "Disposable loot fixture location already occupied");
                block.setType(materials[i]);
                var empty = (org.bukkit.block.Container) block.getState();
                require(Boolean.FALSE.equals(guard.invoke(null, empty)), "Ordinary container rejected as attached loot");
                var lootable = (org.bukkit.loot.Lootable) empty;
                lootable.setLootTable(table);
                lootable.setSeed(75123L + i);
                require(empty.update(true, false), "Could not persist attached loot fixture");
            }
            require(block.getType() == materials[i], "Loot fixture block changed on restart");
            var current = (org.bukkit.block.Container) block.getState();
            var lootable = (org.bukkit.loot.Lootable) current;
            require(table.equals(lootable.getLootTable()) && lootable.getSeed() == 75123L + i,
                    "Expected untouched loot table and seed before guard");
            require(Boolean.TRUE.equals(guard.invoke(null, current)), "Production guard missed attached loot table");
            var after = (org.bukkit.loot.Lootable) block.getState();
            require(table.equals(after.getLootTable()) && after.getSeed() == 75123L + i,
                    "Read-only loot guard changed attached loot metadata");
        }
        Bukkit.getLogger().info(restart
                ? "SFL_PHYSICAL_CONTAINER_LOOT_GUARD_RESTART_PASS" : "SFL_PHYSICAL_CONTAINER_LOOT_GUARD_SEED_PASS");
    }

    private static void refuseReplay(Method apply, Path fixture, UUID owner, UUID journal, Object binding,
            Inventory contents, Function<ItemStack, ItemStack> resolver) throws Exception {
        byte[][] before = stock(contents);
        Path marker = marker(fixture, owner, journal);
        byte[] intent = Files.readAllBytes(marker);
        AtomicInteger writes = new AtomicInteger();
        Inventory hooked = proxy(contents, marker, (slot, count) -> {
            throw new AssertionError("Single-use replay reached an inventory setter");
        }, writes);
        boolean refusedByMarker = false;
        try {
            apply.invoke(null, fixture, owner, journal, binding, hooked, resolver);
        } catch (InvocationTargetException exception) {
            refusedByMarker = exception.getCause() instanceof FileAlreadyExistsException;
        }
        require(refusedByMarker && writes.get() == 0 && Arrays.deepEquals(before, stock(contents))
                        && Arrays.equals(intent, Files.readAllBytes(marker)),
                "Existing single-use marker did not refuse exact ORIGINAL stock without writes");
    }

    private static Inventory proxy(Inventory target, Path marker, WriteHook hook, AtomicInteger writes) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(),
                new Class<?>[] {Inventory.class}, (proxy, method, arguments) -> {
                    try {
                        if (method.getName().equals("setItem")) {
                            // Check intent before delegating, then inject a
                            // foreign change after the real native setter.
                            int count = writes.incrementAndGet();
                            require(Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) && Files.size(marker) > 0,
                                    "Native setter ran before durable execution intent");
                            Object result = method.invoke(target, arguments);
                            hook.afterWrite((int) arguments[0], count);
                            return result;
                        }
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private static byte[][] stock(Inventory inventory) {
        return Arrays.stream(inventory.getContents())
                .map(item -> item == null || item.getType().isAir() ? null : item.serializeAsBytes())
                .toArray(byte[][]::new);
    }

    private static UUID id(Object record) throws Exception {
        return (UUID) method(record.getClass(), "id").invoke(record);
    }

    private static Path marker(Path fixture, UUID owner, UUID journal) {
        return fixture.resolve("recovery/container-journals/" + owner + "-" + journal + ".attempt");
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method result = type.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }

    @FunctionalInterface
    private interface WriteHook {
        void afterWrite(int slot, int count) throws Exception;
    }
}
