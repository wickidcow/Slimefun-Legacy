package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.logging.Level;

/** Registers optional addon content bundled with Slimefun Legacy. */
final class LegacyAddonSetup {

    private static final String ADDITIONS_ROOT = "SlimefunLegacyAddition.";

    private LegacyAddonSetup() {}

    static void setup(Slimefun plugin) {
        register(plugin, "ExtraGear", () -> ExtraGearSetup.setup(plugin));
        register(plugin, "ExtraTools", () -> ExtraToolsSetup.setup(plugin));
    }

    private static void register(Slimefun plugin, String module, Runnable registration) {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        String path = ADDITIONS_ROOT + module + ".enabled";
        config.setDefaultValue(path, true);
        config.save();

        if (!config.getBoolean(path)) {
            Slimefun.logger().log(Level.INFO, "Built-in {0} is disabled in configSFLAddons.yml.", module);
            return;
        }

        registration.run();
    }
}
