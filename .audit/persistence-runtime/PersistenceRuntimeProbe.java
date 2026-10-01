package io.github.wickidcow.validation;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable CI probe that calls the real plugin's public persistence operations. */
public final class PersistenceRuntimeProbe extends JavaPlugin {
    private static final String FIRST = "00000000-1111-2222-3333-444444444444";
    private static final String SECOND = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String REGISTRY = "io.github.addoncommunity.galactifun.core.managers.StargateRegistry";
    private Plugin target;
    private Path file;
    private String addon;
    private String mode;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                addon = System.getProperty("persistence.addon");
                mode = System.getProperty("persistence.mode");
                check("Galactifun".equals(addon) || "SlimeHUD".equals(addon), "Unknown addon");
                target = Bukkit.getPluginManager().getPlugin(addon);
                check(target != null, "Target plugin was not discovered");
                file = target.getDataFolder().toPath().resolve(addon.equals("Galactifun") ? "stargates.yml" : "player.yml");
                if (mode.equals("new-broken")) {
                    check(!target.isEnabled(), "Unreadable data must stop plugin initialization");
                    check(Arrays.equals(Files.readAllBytes(Path.of("expected-original.bin")), Files.readAllBytes(file)),
                            "Failed load modified the original file");
                } else {
                    check(target.isEnabled(), "Target plugin is disabled on a valid/control path");
                    if (mode.equals("old-broken")) {
                        mutate("control-write");
                        check(!Arrays.equals(Files.readAllBytes(Path.of("expected-original.bin")), Files.readAllBytes(file)),
                                "Original negative control must reproduce an actual overwritten file");
                    } else if (mode.equals("old-produce")) {
                        verifySeed();
                        mutate("producer");
                        verifyProduced();
                    } else if (mode.equals("new-verify")) {
                        verifyProduced();
                        unchangedOperation();
                        check(Arrays.equals(Files.readAllBytes(Path.of("expected-original.bin")), Files.readAllBytes(file)),
                                "A healthy unchanged operation rewrote the file");
                    } else if (mode.equals("new-retry")) {
                        verifyProduced();
                        failedWriteAndRetry();
                    } else if (mode.equals("new-verify-retry")) {
                        verifyProduced();
                        verifyRetry();
                    } else {
                        throw new AssertionError("Unknown mode: " + mode);
                    }
                }
                Files.writeString(Path.of("safety-probe-result.txt"),
                        "PASS\naddon=" + addon + "\nmode=" + mode + "\n");
                getLogger().info("PERSISTENCE_RUNTIME_PASS addon=" + addon + " mode=" + mode);
            } catch (Throwable failure) {
                while (failure instanceof InvocationTargetException wrapped && wrapped.getCause() != null) {
                    failure = wrapped.getCause();
                }
                getLogger().log(java.util.logging.Level.SEVERE, "PERSISTENCE_RUNTIME_FAIL", failure);
                try {
                    Files.writeString(Path.of("safety-probe-result.txt"), "FAIL\n" + failure + "\n");
                } catch (Exception ignored) {
                    getLogger().severe("Unable to write the failure result; absence of evidence also fails this run.");
                }
            } finally {
                Bukkit.shutdown();
            }
        }, 20L);
    }

    private Object store() throws Exception {
        return target.getClass().getMethod("getPlayerData").invoke(target);
    }

    private Class<?> registry() throws ClassNotFoundException {
        return Class.forName(REGISTRY, true, target.getClass().getClassLoader());
    }

    private Object read(String name, Class<?>[] types, Object... arguments) throws Exception {
        Object store = store();
        return store.getClass().getMethod(name, types).invoke(store, arguments);
    }

    private void set(String path, Object value) throws Exception {
        Object store = store();
        store.getClass().getMethod("set", String.class, Object.class).invoke(store, path, value);
    }

    private void saveStore() throws Exception {
        Object store = store();
        store.getClass().getMethod("save").invoke(store);
    }

    private void register(String address, int x, int y, int z) throws Exception {
        World world = Bukkit.getWorld("world");
        check(world != null, "Expected real overworld missing");
        registry().getMethod("register", String.class, Location.class)
                .invoke(null, address, new Location(world, x, y, z));
    }

    private void location(String address, String world, int x, int y, int z) throws Exception {
        Object result = registry().getMethod("resolve", String.class).invoke(null, address);
        check(result instanceof Optional<?>, "Unexpected registry return type");
        Object value = ((Optional<?>) result).orElseThrow(() -> new AssertionError("Missing gate " + address));
        check(value instanceof Location, "Resolved value is not a Location");
        Location actual = (Location) value;
        check(actual.getWorld() != null && world.equals(actual.getWorld().getName()), "Gate world changed");
        check(actual.getBlockX() == x && actual.getBlockY() == y && actual.getBlockZ() == z,
                "Gate coordinates changed for " + address);
    }

    private YamlConfiguration yaml() throws Exception {
        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(Files.readString(file));
        return result;
    }

    private void verifySeed() throws Exception {
        if (addon.equals("Galactifun")) {
            location("deadbeef", "world", -37, 70, 9001);
            Object absent = registry().getMethod("resolve", String.class).invoke(null, "ffffffff");
            check(absent instanceof Optional<?> && ((Optional<?>) absent).isEmpty(), "Unloaded world must stay unresolved");
            String expected = Integer.toHexString("world--37-70-9001".hashCode());
            Object actual = registry().getMethod("addressFor", String.class, int.class, int.class, int.class)
                    .invoke(null, "world", -37, 70, 9001);
            check(expected.equals(actual), "Address derivation changed");
            YamlConfiguration data = yaml();
            check(data.getInt("gates.ffffffff.x") == Integer.MIN_VALUE, "Signed X lost");
            check(data.getInt("gates.ffffffff.z") == Integer.MAX_VALUE, "Signed Z lost");
            check(data.getInt("gates.ffffffff.y") == -64, "Signed Y lost");
            check("unloaded_planet".equals(data.getString("gates.ffffffff.world")), "Unloaded world identity lost");
        } else {
            check(Boolean.FALSE.equals(read("getBoolean", new Class<?>[]{String.class, boolean.class}, FIRST + ".waila", true)),
                    "First user's disabled preference lost");
            check(Boolean.TRUE.equals(read("getBoolean", new Class<?>[]{String.class, boolean.class}, SECOND + ".waila", false)),
                    "Second user's enabled preference lost");
            String expectedDisplay = mode.equals("new-verify-retry") ? "actionbar" : "bossbar";
            check(expectedDisplay.equals(read("getString", new Class<?>[]{String.class, String.class}, SECOND + ".display", "missing")),
                    "Second user's display preference lost");
        }
        YamlConfiguration data = yaml();
        check(data.getLong("future.count") == Long.MAX_VALUE, "Unknown long value lost");
        check(Double.doubleToRawLongBits(data.getDouble("future.charge")) == Double.doubleToRawLongBits(123.4567),
                "Unknown floating-point value changed");
        check(data.getStringList("future.labels").equals(List.of("keep", "me")), "Unknown list lost");
        check("ExactOwner".equals(data.getString("future.owner")), "Unknown owner value lost");
    }

    private void verifyProduced() throws Exception {
        verifySeed();
        if (addon.equals("Galactifun")) {
            location("abc123", "world", -71, 71, 902);
        } else {
            check("bossbar".equals(read("getString", new Class<?>[]{String.class, String.class}, FIRST + ".display", "missing")),
                    "Old writer's changed preference lost");
        }
    }

    private void mutate(String operation) throws Exception {
        if (addon.equals("Galactifun")) {
            register(operation.equals("producer") ? "abc123" : "control", -71, 71, 902);
        } else {
            set(FIRST + ".display", "bossbar");
            saveStore();
        }
    }

    private void unchangedOperation() throws Exception {
        if (addon.equals("Galactifun")) {
            register("abc123", -71, 71, 902);
        } else {
            set(FIRST + ".display", "bossbar");
            saveStore();
        }
    }

    private void failedWriteAndRetry() throws Exception {
        byte[] original = Files.readAllBytes(file);
        Path backup = file.resolveSibling(file.getFileName() + ".probe-backup");
        Files.move(file, backup);
        Files.createDirectory(file);
        Path child = file.resolve("keep.txt");
        Files.writeString(child, "retained");
        try {
            if (addon.equals("Galactifun")) {
                register("retrygate", 123, 72, -456);
            } else {
                set(SECOND + ".display", "actionbar");
                saveStore();
            }
            check(Arrays.equals(original, Files.readAllBytes(backup)), "Failed write changed previous bytes");
            check("retained".equals(Files.readString(child)), "Failed write replaced an existing directory");
        } finally {
            Files.delete(child);
            Files.delete(file);
            Files.move(backup, file);
        }
        if (addon.equals("Galactifun")) {
            // Identical registration must retry, not incorrectly treat the dirty value as already saved.
            register("retrygate", 123, 72, -456);
        } else {
            saveStore();
        }
        verifyRetry();
    }

    private void verifyRetry() throws Exception {
        YamlConfiguration data = yaml();
        if (addon.equals("Galactifun")) {
            location("retrygate", "world", 123, 72, -456);
            check(data.getInt("gates.retrygate.x") == 123 && data.getInt("gates.retrygate.z") == -456,
                    "Retried gate was not persisted");
        } else {
            check("actionbar".equals(data.getString(SECOND + ".display")), "Retried preference not persisted");
        }
        try (var entries = Files.list(file.getParent())) {
            check(entries.noneMatch(entry -> entry.getFileName().toString().endsWith(".tmp")), "Staging file leaked");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
