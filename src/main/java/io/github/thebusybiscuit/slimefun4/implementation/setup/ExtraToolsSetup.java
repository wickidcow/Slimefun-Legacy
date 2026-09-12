package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.CobblestoneGenerator;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.ConcreteFactory;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.ElectricComposter;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.ExtraToolsItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.GoldTransmuter;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.Hammer;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.Pulverizer;
import io.github.thebusybiscuit.slimefun4.implementation.items.extratools.Vaporizer;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Integrates the historical ExtraTools addon as optional Slimefun Legacy content. */
final class ExtraToolsSetup {

    private static final String LEGACY_RESEARCH_NAMESPACE = "extratools";
    private static final List<String> ITEM_IDS = List.of(
            "HAMMER",
            "GOLD_TRANSMUTER",
            "ELECTRIC_COMPOSTER",
            "ELECTRIC_COMPOSTER_2",
            "COBBLESTONE_GENERATOR",
            "VAPORIZER",
            "CONCRETE_FACTORY",
            "PULVERIZER");

    private ExtraToolsSetup() {}

    static void setup(Slimefun plugin) {
        Plugin standaloneExtraTools = Bukkit.getPluginManager().getPlugin("ExtraTools");
        if (standaloneExtraTools != null) {
            Slimefun.logger()
                    .log(
                            Level.INFO,
                            "Standalone ExtraTools detected; built-in ExtraTools content will not be registered.");
            return;
        }

        List<String> collisions = ITEM_IDS.stream()
                .filter(Slimefun.getRegistry().getSlimefunItemIds()::containsKey)
                .toList();
        if (!collisions.isEmpty()) {
            Slimefun.logger()
                    .warning("Built-in ExtraTools was not registered because these item ids already exist: "
                            + String.join(", ", collisions));
            return;
        }

        int itemCountBefore = Slimefun.getRegistry().getAllSlimefunItems().size();
        int researchCountBefore = Slimefun.getRegistry().getResearches().size();
        int researchId = 4100;

        new Hammer().register(plugin);
        registerResearch(++researchId, "hammer", "Hammer", 3, ExtraToolsItems.HAMMER);

        new GoldTransmuter().register(plugin);
        registerResearch(++researchId, "gold_transmuter", "Gold Transmuter", 12, ExtraToolsItems.GOLD_TRANSMUTER);

        new ElectricComposter(ElectricComposter.Tier.ONE) {
            @Override
            public int getEnergyConsumption() {
                return 9;
            }

            @Override
            public int getSpeed() {
                return 1;
            }
        }.register(plugin);
        registerResearch(
                ++researchId,
                "electric_composter",
                "Electric Composter",
                18,
                ExtraToolsItems.ELECTRIC_COMPOSTER);

        new ElectricComposter(ElectricComposter.Tier.TWO) {
            @Override
            public int getEnergyConsumption() {
                return 25;
            }

            @Override
            public int getSpeed() {
                return 4;
            }
        }.register(plugin);
        registerResearch(
                ++researchId,
                "electric_composter_2",
                "Electric Composter II",
                18,
                ExtraToolsItems.ELECTRIC_COMPOSTER_2);

        new CobblestoneGenerator().register(plugin);
        registerResearch(
                ++researchId,
                "cobblestone_generator",
                "Cobblestone Generator",
                40,
                ExtraToolsItems.COBBLESTONE_GENERATOR);

        new Vaporizer().register(plugin);
        registerResearch(++researchId, "vaporizer", "Vaporizer", 18, ExtraToolsItems.VAPORIZER);

        new ConcreteFactory().register(plugin);
        registerResearch(
                ++researchId,
                "concrete_factory",
                "Concrete Factory",
                12,
                ExtraToolsItems.CONCRETE_FACTORY);

        new Pulverizer().register(plugin);
        registerResearch(++researchId, "pulverizer", "Pulverizer", 18, ExtraToolsItems.PULVERIZER);

        int itemsAdded = Slimefun.getRegistry().getAllSlimefunItems().size() - itemCountBefore;
        int researchesAdded = Slimefun.getRegistry().getResearches().size() - researchCountBefore;
        Slimefun.logger()
                .log(
                        Level.INFO,
                        "Registered {0} built-in ExtraTools items and {1} legacy-compatible researches.",
                        new Object[] {itemsAdded, researchesAdded});
    }

    private static void registerResearch(
            int id,
            String key,
            String name,
            int cost,
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack item) {
        NamespacedKey namespacedKey =
                Objects.requireNonNull(NamespacedKey.fromString(LEGACY_RESEARCH_NAMESPACE + ':' + key));
        new Research(namespacedKey, id, name, cost).addItems(item).register();
    }
}
