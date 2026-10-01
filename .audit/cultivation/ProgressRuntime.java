package io.github.wickidcow.regression;

import dev.sefiraat.cultivation.Cultivation;
import dev.sefiraat.cultivation.api.utils.LevelType;
import dev.sefiraat.cultivation.api.utils.StatisticUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class ProgressRuntime extends JavaPlugin {
    private static final UUID OWNER = UUID.fromString("2f017f3a-8442-4ef2-9fba-456789abcdef");
    private static final String DISCOVERY = "CULTIVATION_OLD_PLANT";
    private static final int EXPERIENCE = Integer.MAX_VALUE - 13;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            String mode = System.getProperty("cultivation.progressTest", "missing");
            try {
                var addon = Bukkit.getPluginManager().getPlugin("Cultivation");
                check(addon != null, "Cultivation missing");
                if (mode.equals("refuse")) {
                    check(!addon.isEnabled(), "Unreadable player data must stop the addon");
                    check(Cultivation.getConfigManager() == null, "Incomplete manager was published");
                    check(Cultivation.getPlantRegistry() == null, "Plant registry initialized after failed data load");
                    check(Cultivation.getRunnableManager() == null, "Tasks initialized after failed data load");
                } else {
                    check(addon.isEnabled(), "Expected enabled Cultivation");
                    if (mode.equals("seed")) {
                        StatisticUtils.setExp(OWNER, LevelType.HORTICULTURALIST, Integer.MAX_VALUE - 20);
                        StatisticUtils.incrementExp(OWNER, LevelType.HORTICULTURALIST, 7);
                        StatisticUtils.setExp(OWNER, LevelType.ORCHARDIST, 123456);
                        StatisticUtils.unlockDiscovery(OWNER, DISCOVERY);
                        Cultivation.getConfigManager().getCodex().set(OWNER + ".PLANT.BREEDING.CULTIVATION_UNKNOWN.UNLOCKED", false);
                        Cultivation.getConfigManager().getExp().set(OWNER + ".unknown_external", "Owner's original value");
                        Cultivation.getConfigManager().getExp().set(OWNER + ".opaque_long", Long.MAX_VALUE);
                        verify();
                        Cultivation.getConfigManager().saveAll();
                    } else if (mode.equals("verify")) {
                        verify();
                        Cultivation.getConfigManager().saveAll();
                    } else if (!mode.equals("control")) {
                        throw new AssertionError("Unknown mode: " + mode);
                    }
                }
                getLogger().info("CULTIVATION_PROGRESS_" + mode.toUpperCase() + "_PASS");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "CULTIVATION_PROGRESS_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private void verify() {
        var exp = Cultivation.getConfigManager().getExp();
        var codex = Cultivation.getConfigManager().getCodex();
        check(exp.getInt(OWNER + ".HORTICULTURALIST.EXP") == EXPERIENCE, "Existing experience changed");
        check(exp.getInt(OWNER + ".ORCHARDIST.EXP") == 123456, "Tree experience changed");
        check("Owner's original value".equals(exp.getString(OWNER + ".unknown_external")), "Unknown owner data changed");
        check(exp.getLong(OWNER + ".opaque_long") == Long.MAX_VALUE, "Long value changed");
        check(StatisticUtils.isDiscovered(OWNER, DISCOVERY), "Existing discovery changed");
        check(!StatisticUtils.isDiscovered(OWNER, "CULTIVATION_UNKNOWN"), "Locked discovery was granted");
        check(codex.contains(OWNER + ".PLANT.BREEDING.CULTIVATION_UNKNOWN.UNLOCKED"), "Explicit false key lost");
        check("Owner's config".equals(Cultivation.getInstance().getConfig().getString("owner-note")), "Owner configuration changed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
