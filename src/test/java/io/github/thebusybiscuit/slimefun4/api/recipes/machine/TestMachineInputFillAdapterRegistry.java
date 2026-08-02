package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestMachineInputFillAdapterRegistry {

    private static final NamespacedKey LOW_KEY = new NamespacedKey("slimefun", "test_input_adapter_low");
    private static final NamespacedKey HIGH_KEY = new NamespacedKey("slimefun", "test_input_adapter_high");

    @AfterEach
    void cleanRegistry() {
        MachineInputFillAdapterRegistry.unregister(LOW_KEY);
        MachineInputFillAdapterRegistry.unregister(HIGH_KEY);
    }

    @Test
    void ordersAdaptersByPriorityAndReplacesMatchingKeys() {
        TestAdapter low = new TestAdapter(LOW_KEY, 1);
        TestAdapter high = new TestAdapter(HIGH_KEY, 20);
        MachineInputFillAdapterRegistry.register(low);
        MachineInputFillAdapterRegistry.register(high);

        List<MachineInputFillAdapter> adapters = MachineInputFillAdapterRegistry.getAdapters();
        int highIndex = adapters.indexOf(high);
        int lowIndex = adapters.indexOf(low);
        assertTrue(highIndex >= 0 && lowIndex >= 0 && highIndex < lowIndex);

        TestAdapter replacement = new TestAdapter(LOW_KEY, 30);
        assertSame(low, MachineInputFillAdapterRegistry.register(replacement));
        assertSame(replacement, MachineInputFillAdapterRegistry.unregister(LOW_KEY));
    }

    private record TestAdapter(NamespacedKey key, int priority) implements MachineInputFillAdapter {

        @Override
        public @Nonnull NamespacedKey getKey() {
            return key;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem machine) {
            return false;
        }

        @Override
        public boolean supportsRecipe(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe) {
            return false;
        }

        @Override
        public @Nullable MachineInputFillRecipe resolve(
                @Nonnull SlimefunItem machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives) {
            return null;
        }
    }
}
