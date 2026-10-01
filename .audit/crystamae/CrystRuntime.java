package io.github.wickidcow.regression;

import io.github.sefiraat.crystamaehistoria.CrystamaeHistoria;
import io.github.sefiraat.crystamaehistoria.magic.SpellType;
import io.github.sefiraat.crystamaehistoria.player.PlayerStatistics;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

public final class CrystRuntime extends JavaPlugin {
    private static final UUID OWNER = UUID.fromString("2f017f3a-8442-4ef2-9fba-456789abcdef");
    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            String mode = System.getProperty("crystamae.progressTest", "missing");
            try {
                var addon = Bukkit.getPluginManager().getPlugin("CrystamaeHistoria");
                check(addon != null, "CrystamaeHistoria missing");
                if (mode.equals("refuse")) {
                    check(!addon.isEnabled(), "Unreadable data must stop the addon");
                    check(Slimefun.getRegistry().getAllSlimefunItems().stream().noneMatch(item -> item.getAddon() == addon),
                        "Failed startup registered addon items");
                    check(Bukkit.getScheduler().getPendingTasks().stream().noneMatch(task -> task.getOwner() == addon),
                        "Failed startup retained scheduled work");
                    check(org.bukkit.event.HandlerList.getRegisteredListeners(addon).isEmpty(),
                        "Failed startup retained event listeners");
                } else {
                    check(addon.isEnabled(), "Expected enabled CrystamaeHistoria");
                    check(Slimefun.getRegistry().getAllSlimefunItems().stream().anyMatch(item -> item.getAddon() == addon),
                        "Healthy control did not register actual addon items");
                    if (mode.equals("seed")) {
                        var stats = CrystamaeHistoria.getConfigManager().getPlayerStats();
                        stats.set(OWNER + ".SPELL.HEAL.TIMES_CAST", Integer.MAX_VALUE - 20);
                        PlayerStatistics.unlockSpell(OWNER, SpellType.HEAL.getSpell());
                        PlayerStatistics.addCast(OWNER, SpellType.HEAL.getSpell());
                        stats.set(OWNER + ".STORY.STONE.TIMES_CHRONICLED", 123456);
                        stats.set(OWNER + ".STORY.STONE.TIMES_REALISED", 654321);
                        PlayerStatistics.addChronicle(OWNER, Material.STONE);
                        PlayerStatistics.addRealisation(OWNER, Material.STONE);
                        var definition = CrystamaeHistoria.getStoriesManager().getBlockDefinitionMap().get(Material.STONE);
                        check(definition != null, "Existing stone definition missing");
                        PlayerStatistics.setGilded(OWNER, definition);
                        stats.set(OWNER + ".opaque_long", Long.MAX_VALUE);
                        stats.set(OWNER + ".unknown_external", "Keep original data");
                        verify();
                        CrystamaeHistoria.getConfigManager().saveAll();
                    } else if (mode.equals("verify")) {
                        verify();
                        CrystamaeHistoria.getConfigManager().saveAll();
                    } else if (!mode.equals("control")) {
                        throw new AssertionError("Unknown mode: " + mode);
                    }
                }
                getLogger().info("CRYSTAMAE_PROGRESS_" + mode.toUpperCase() + "_PASS");
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "CRYSTAMAE_PROGRESS_FAIL", failure);
            } finally {
                Bukkit.shutdown();
            }
        }, 40L);
    }

    private void verify() {
        var stats = CrystamaeHistoria.getConfigManager().getPlayerStats();
        check(PlayerStatistics.isUnlocked(OWNER, SpellType.HEAL.getSpell()), "Spell unlock changed");
        check(PlayerStatistics.getCasts(OWNER, SpellType.HEAL.getSpell()) == Integer.MAX_VALUE - 19, "Cast count changed");
        check(PlayerStatistics.getBlockChronicled(OWNER, Material.STONE) == 123457, "Chronicle count changed");
        var definition = CrystamaeHistoria.getStoriesManager().getBlockDefinitionMap().get(Material.STONE);
        check(PlayerStatistics.getBlockRealised(OWNER, definition) == 654322, "Realisation count changed");
        check(PlayerStatistics.isGilded(OWNER, definition), "Gilded state changed");
        check(stats.getLong(OWNER + ".opaque_long") == Long.MAX_VALUE, "Opaque long changed");
        check("Keep original data".equals(stats.getString(OWNER + ".unknown_external")), "Unknown data lost");
        check(!CrystamaeHistoria.getConfigManager().spellEnabled(SpellType.HEAL.getSpell()), "Disabled spell was re-enabled");
        check(!SpellType.HEAL.getSpell().isEnabled(), "Disabled spell changed in runtime");
        check("Owner setting".equals(CrystamaeHistoria.getConfigManager().getSpells().getString("unknown_external")), "Unknown setting lost");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
