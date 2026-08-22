package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Player-owned Resonance Beacon power progression. Unlocks survive breaking or replacing a Resonance Beacon.
 */
final class BeaconPlusProgression {

    private static final String FILE_NAME = "adventurers-curios-beacon-progress.yml";
    private static File file;
    private static YamlConfiguration data;

    private BeaconPlusProgression() {}

    static synchronized int getUnlockedTier(UUID owner, BeaconPlusEffect effect) {
        ensureLoaded();
        if (owner == null || effect == null || !effect.isConfigurable()) {
            return 0;
        }
        return clamp(data.getInt(path(owner, effect), 0), 0, BeaconPlusConfig.getMaxTier());
    }

    /**
     * Repairs an older configured-power record that predates the player progression file. This helper never lowers an
     * existing tier and is only called after the runtime has proven that the power was already configured on a beacon.
     */
    static synchronized int ensureMinimumTier(UUID owner, BeaconPlusEffect effect, int minimumTier) {
        ensureLoaded();
        if (owner == null || effect == null || !effect.isConfigurable()) {
            return 0;
        }

        int maximum = BeaconPlusConfig.getMaxTier();
        int target = clamp(minimumTier, 0, maximum);
        int current = clamp(data.getInt(path(owner, effect), 0), 0, maximum);
        if (current >= target) {
            return current;
        }

        data.set(path(owner, effect), target);
        save();
        return target;
    }

    static synchronized PurchaseResult purchaseNextTier(Player buyer, UUID owner, BeaconPlusEffect effect) {
        ensureLoaded();
        if (owner == null || effect == null || !effect.isConfigurable()) {
            return PurchaseResult.failure("This Resonance Beacon does not have a valid owner.");
        }
        if (!BeaconPlusConfig.isPowerEnabled(effect)) {
            return PurchaseResult.failure("That power is disabled by the server administrator.");
        }
        if (!owner.equals(buyer.getUniqueId()) && !(buyer.isOp() && BeaconPlusConfig.operatorCanSponsorUpgrades())) {
            return PurchaseResult.failure("Only the Resonance Beacon owner can purchase power upgrades.");
        }

        int current = getUnlockedTier(owner, effect);
        int maximum = BeaconPlusConfig.getMaxTier();
        if (current >= maximum) {
            return PurchaseResult.failure(effect.getDisplayName() + " is already at Tier " + maximum + ".");
        }

        int next = current + 1;
        boolean free = buyer.getGameMode() == GameMode.CREATIVE && BeaconPlusConfig.creativeBypassesCost();
        if (!free) {
            BeaconPlusConfig.PaymentMode mode = BeaconPlusConfig.getPaymentMode(effect);
            if (mode == BeaconPlusConfig.PaymentMode.MONEY) {
                double cost = BeaconPlusConfig.getMoneyCost(effect, next);
                if (cost > 0.0D) {
                    Economy economy = getEconomy();
                    if (economy == null) {
                        return PurchaseResult.failure(
                                "This upgrade uses money, but no Vault economy provider is available.");
                    }
                    if (!economy.has(buyer, cost)) {
                        return PurchaseResult.failure(
                                "You need " + economy.format(cost) + " to buy Tier " + next + ".");
                    }
                    EconomyResponse response = economy.withdrawPlayer(buyer, cost);
                    if (!response.transactionSuccess()) {
                        return PurchaseResult.failure("The economy transaction failed: " + response.errorMessage);
                    }
                }
            } else {
                int levels = BeaconPlusConfig.getExperienceCost(effect, next);
                if (buyer.getLevel() < levels) {
                    return PurchaseResult.failure(
                            "You need " + levels + " experience levels to buy Tier " + next + ".");
                }
                buyer.setLevel(buyer.getLevel() - levels);
            }
        }

        data.set(path(owner, effect), next);
        save();
        return PurchaseResult.success(next);
    }

    static String describeCost(BeaconPlusEffect effect, int tier) {
        if (BeaconPlusConfig.getPaymentMode(effect) == BeaconPlusConfig.PaymentMode.MONEY) {
            return String.format(java.util.Locale.ROOT, "%.2f money", BeaconPlusConfig.getMoneyCost(effect, tier));
        }
        int levels = BeaconPlusConfig.getExperienceCost(effect, tier);
        return levels + (levels == 1 ? " experience level" : " experience levels");
    }

    static synchronized void shutdown() {
        if (data != null) {
            save();
        }
        data = null;
        file = null;
    }

    private static Economy getEconomy() {
        RegisteredServiceProvider<Economy> registration =
                Slimefun.instance().getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private static void ensureLoaded() {
        if (data != null) {
            return;
        }
        file = new File(Slimefun.instance().getDataFolder(), FILE_NAME);
        data = YamlConfiguration.loadConfiguration(file);
    }

    private static void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Could not create " + parent);
            }
            data.save(file);
        } catch (IOException exception) {
            Slimefun.instance()
                    .getLogger()
                    .log(Level.SEVERE, "Could not save Resonance Beacon player progression.", exception);
        }
    }

    private static String path(UUID owner, BeaconPlusEffect effect) {
        return "players." + owner + "." + effect.getId();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record PurchaseResult(boolean success, int newTier, String error) {
        private static PurchaseResult success(int tier) {
            return new PurchaseResult(true, tier, null);
        }

        private static PurchaseResult failure(String error) {
            return new PurchaseResult(false, 0, error);
        }
    }
}
