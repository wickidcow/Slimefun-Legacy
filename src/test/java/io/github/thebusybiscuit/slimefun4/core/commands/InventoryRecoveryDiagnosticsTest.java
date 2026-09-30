package io.github.thebusybiscuit.slimefun4.core.commands;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryRecoverySnapshot;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryRecoverySnapshot.Entry;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryRecoverySnapshot.Kind;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class InventoryRecoveryDiagnosticsTest {
    @Test
    void deniedPermissionDoesNotEvenCaptureIdentities() {
        var sender = new Sender(false);
        InventoryRecoveryDiagnostics.execute(sender.proxy, args(), () -> {
            throw new AssertionError("Unauthorized capture");
        });
        assertTrue(sender.joined().contains("permission"));
    }

    @Test
    void rejectsMalformedOrExcessArgumentsBeforeCapturingState() {
        for (String page : List.of("0", "-1", "11", "999999999999999999", "a", "1\n", "+1", "01")) {
            var sender = new Sender(true);
            InventoryRecoveryDiagnostics.execute(sender.proxy, args(page), () -> {
                throw new AssertionError("Invalid capture");
            });
            assertTrue(sender.joined().contains("Usage:"), page);
        }
        var sender = new Sender(true);
        InventoryRecoveryDiagnostics.execute(
                sender.proxy, new String[] {"doctor", "storage", "recovery", "1", "confirm"}, () -> {
                    throw new AssertionError("Extra argument");
                });
        assertTrue(sender.joined().contains("Usage:"));
    }

    @Test
    void missingControllersNeverBecomeAGreenEmptyReport() {
        var sender = new Sender(true);
        InventoryRecoveryDiagnostics.execute(sender.proxy, args(), () -> null);
        assertTrue(sender.joined().contains("unavailable"));
        assertFalse(sender.joined().contains("No incomplete"));
    }

    @Test
    void emptyInMemoryReportDoesNotClaimHistoricalCompatibility() throws Exception {
        var sender = new Sender(true);
        InventoryRecoveryDiagnostics.execute(sender.proxy, args(), () -> empty());
        assertTrue(sender.joined().contains("No incomplete loads"));
        assertTrue(sender.joined().contains("not a persisted-item scan"));
        assertFalse(sender.joined().contains("READY"));
    }

    @Test
    void validReportCapturesOnceAndNeverAutomaticallyRetriesOrRepairs() throws Exception {
        var snapshot = snapshot(List.of(new Entry(Kind.UNIVERSAL_MIGRATION, "world;1:64:2", "saved-uuid")));
        var sender = new Sender(true);
        var captures = new AtomicInteger();
        InventoryRecoveryDiagnostics.execute(sender.proxy, args(), () -> {
            captures.incrementAndGet();
            return snapshot;
        });
        assertEquals(1, captures.get());
        assertTrue(sender.joined().contains("saved-uuid"));
        assertTrue(sender.joined().contains("never retries migration, clears holds"));
        assertTrue(sender.joined().contains("may already be committed"));
        assertEquals("saved-uuid", snapshot.entries().getFirst().destination());
    }

    @Test
    void sanitizesOnlyTheDisplayedCopyOfAnOwnerKey() throws Exception {
        String original = "world&a\u00a7c\n\t\u202e\u2028\u2029;1:64:2";
        var snapshot = snapshot(List.of(new Entry(Kind.BLOCK_LOAD, original, null)));
        String report = String.join("\n", InventoryRecoveryDiagnostics.render(snapshot, 1));
        assertTrue(report.contains("world?a?c?????;1:64:2"));
        assertEquals(original, snapshot.entries().getFirst().owner());
    }

    @Test
    void limitsLongDisplayKeysWithoutShorteningTheStoredSnapshot() throws Exception {
        String identity = "x".repeat(2000);
        var snapshot = snapshot(List.of(new Entry(Kind.BLOCK_LOAD, identity, null)));
        assertEquals(163, InventoryRecoveryDiagnostics.display(identity).length());
        assertEquals(identity, snapshot.entries().getFirst().owner());
    }

    @Test
    void refusesPagesOutsideTheCapturedSample() throws Exception {
        var sender = new Sender(true);
        InventoryRecoveryDiagnostics.execute(sender.proxy, args("2"), InventoryRecoveryDiagnosticsTest::empty);
        assertTrue(sender.joined().contains("outside the current sample"));
    }

    @Test
    void rendersAtMostTwentyDetailsAndExplainsThatCountsCanOverlap() throws Exception {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 45; i++) entries.add(new Entry(Kind.BLOCK_LOAD, "world;" + i + ":64:0", null));
        var lines = InventoryRecoveryDiagnostics.render(snapshot(entries), 2);
        assertEquals(20, lines.stream().filter(line -> line.startsWith("&8- ")).count());
        assertTrue(String.join("\n", lines).contains("not unique inventory totals"));
        assertTrue(String.join("\n", lines).contains("pages can change"));
    }

    @Test
    void explainsPresentationRepairDoesNotClearSavedItemRecoveryHolds() throws Exception {
        var snapshot = snapshot(List.of(new Entry(Kind.BACKPACK_LOAD, "old-backpack-id", null)));
        String report = String.join("\n", InventoryRecoveryDiagnostics.render(snapshot, 1));
        assertTrue(report.contains("repairs presentation"));
        assertTrue(report.contains("does not clear these holds or rebuild corrupt items"));
        assertTrue(report.contains("normal complete loading must succeed"));
    }

    private static String[] args(String... page) {
        return page.length == 0
                ? new String[] {"doctor", "storage", "recovery"}
                : new String[] {"doctor", "storage", "recovery", page[0]};
    }

    private static InventoryRecoverySnapshot empty() {
        try {
            return snapshot(List.of());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static InventoryRecoverySnapshot snapshot(List<Entry> entries) throws ReflectiveOperationException {
        EnumMap<Kind, Long> counts = new EnumMap<>(Kind.class);
        entries.forEach(entry -> counts.merge(entry.kind(), 1L, Long::sum));
        var constructor = InventoryRecoverySnapshot.class.getDeclaredConstructor(Map.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(counts, entries);
    }

    private static final class Sender {
        private final List<String> messages = new ArrayList<>();
        private final CommandSender proxy;

        private Sender(boolean allowed) {
            proxy = (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[] {CommandSender.class},
                    (object, method, values) -> {
                        if (method.getName().equals("hasPermission")) {
                            assertEquals("slimefun.command.doctor", values[0]);
                            return allowed;
                        }
                        if (method.getName().equals("sendMessage")) {
                            assertEquals(1, values.length);
                            Component message = assertInstanceOf(Component.class, values[0]);
                            messages.add(
                                    PlainTextComponentSerializer.plainText().serialize(message));
                            return null;
                        }
                        throw new AssertionError("Unexpected command operation: " + method.getName());
                    });
        }

        private String joined() {
            return String.join("\n", messages);
        }
    }
}
