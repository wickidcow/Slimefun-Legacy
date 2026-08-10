package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class TestAddonRuntimeHealthGuard {

    @Test
    void guardedCallbacksRecordFailuresWithoutThrowing() {
        AtomicInteger failures = new AtomicInteger();
        AddonRuntimeHealthService service = new AddonRuntimeHealthService() {
            @Override
            public void recordFailure(Plugin plugin, String operation, Throwable failure) {
                failures.incrementAndGet();
            }

            @Override
            public List<AddonRuntimeFailureSnapshot> getFailures() {
                return List.of();
            }

            @Override
            public long getObservedFailureCount() {
                return failures.get();
            }

            @Override
            public boolean clear(String pluginName) {
                return false;
            }

            @Override
            public int clearAll() {
                return 0;
            }
        };
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class}, (proxy, method, args) -> null);

        assertTrue(service.runGuarded(plugin, "success", () -> {}));
        assertFalse(service.runGuarded(plugin, "failure", () -> {
            throw new LinkageError("boom");
        }));
        assertEquals(1, failures.get());
        assertEquals(
                "value",
                service.callGuarded(plugin, "call-success", () -> "value").orElseThrow());
        assertTrue(service.callGuarded(plugin, "call-failure", () -> {
                    throw new IllegalStateException("boom");
                })
                .isEmpty());
        assertEquals(2, failures.get());
    }
}
