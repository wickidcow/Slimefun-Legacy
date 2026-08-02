package io.github.thebusybiscuit.slimefun4.compatibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import java.lang.reflect.Method;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class TestLegacyCompatibilityBridges {

    @Test
    void retainsOldConfigJvmSignatures() throws ReflectiveOperationException {
        assertMethod(BlockTicker.class, "tick", Block.class, SlimefunItem.class, Config.class);
        assertMethod(EnergyNetComponent.class, "getCharge", Location.class, Config.class);
        assertMethod(EnergyNetProvider.class, "getGeneratedOutput", Location.class, Config.class);
        assertMethod(EnergyNetProvider.class, "willExplode", Location.class, Config.class);

        Method locationInfo = assertMethod(BlockStorage.class, "getLocationInfo", Location.class);
        assertNotNull(locationInfo);
        assertFalse(Config.class.getAnnotation(Deprecated.class).forRemoval());
    }

    @Test
    void exposesModernDataContainerOverloads() throws ReflectiveOperationException {
        assertMethod(BlockTicker.class, "tick", Block.class, SlimefunItem.class, ASlimefunDataContainer.class);
        assertMethod(BlockTicker.class, "tick", Block.class, SlimefunItem.class, SlimefunBlockData.class);
        assertMethod(EnergyNetComponent.class, "getChargeLong", Location.class, ASlimefunDataContainer.class);
        assertMethod(EnergyNetProvider.class, "getGeneratedOutputLong", Location.class, ASlimefunDataContainer.class);
        assertMethod(EnergyNetProvider.class, "willExplode", Location.class, ASlimefunDataContainer.class);
    }

    private static Method assertMethod(Class<?> owner, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method method = owner.getMethod(name, parameters);
        assertNotNull(method);
        return method;
    }
}
