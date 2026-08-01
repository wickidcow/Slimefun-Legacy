package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestMachineRecipeProviderRegistry {

    private static final NamespacedKey LOW_KEY = new NamespacedKey("slimefun", "test_machine_provider_low");
    private static final NamespacedKey HIGH_KEY = new NamespacedKey("slimefun", "test_machine_provider_high");

    @AfterEach
    void cleanRegistry() {
        MachineRecipeProviderRegistry.unregister(LOW_KEY);
        MachineRecipeProviderRegistry.unregister(HIGH_KEY);
    }

    @Test
    void ordersProvidersByPriorityAndReplacesMatchingKeys() {
        TestProvider low = new TestProvider(LOW_KEY, 1);
        TestProvider high = new TestProvider(HIGH_KEY, 20);
        MachineRecipeProviderRegistry.register(low);
        MachineRecipeProviderRegistry.register(high);

        List<MachineRecipeProvider> providers = MachineRecipeProviderRegistry.getProviders();
        int highIndex = providers.indexOf(high);
        int lowIndex = providers.indexOf(low);
        assertTrue(highIndex >= 0 && lowIndex >= 0 && highIndex < lowIndex);

        TestProvider replacement = new TestProvider(LOW_KEY, 30);
        assertSame(low, MachineRecipeProviderRegistry.register(replacement));
        assertSame(replacement, MachineRecipeProviderRegistry.unregister(LOW_KEY));
    }

    private record TestProvider(NamespacedKey key, int priority) implements MachineRecipeProvider {

        @Override
        public @Nonnull NamespacedKey getKey() {
            return key;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return false;
        }

        @Override
        public @Nonnull List<MachineRecipeDisplay> getRecipes(
                @Nonnull SlimefunItem item, @Nonnull World world) {
            return List.of();
        }
    }
}
