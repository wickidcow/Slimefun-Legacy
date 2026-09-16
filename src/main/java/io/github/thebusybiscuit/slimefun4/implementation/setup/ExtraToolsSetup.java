package io.github.thebusybiscuit.slimefun4.implementation.setup;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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
import java.util.function.Supplier;
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

        // Preserve the standalone addon's dedicated top-level guide page even if an individual
        // legacy item id collides. Each conflicting item is skipped below instead of hiding all
        // remaining ExtraTools content.
        ExtraToolsItems.ITEM_GROUP.register(plugin);

        int itemCountBefore = Slimefun.getRegistry().getAllSlimefunItems().size();
        int researchCountBefore = Slimefun.getRegistry().getResearches().size();
        int researchId = 4100;

        researchId = registerItem(plugin, researchId, "HAMMER", "hammer", "Hammer", 3, Hammer::new);
        researchId = registerItem(
                plugin,
                researchId,
                "GOLD_TRANSMUTER",
                "gold_transmuter",
                "Gold Transmuter",
                12,
                GoldTransmuter::new);
        researchId = registerItem(
                plugin,
                researchId,
                "ELECTRIC_COMPOSTER",
                "electric_composter",
                "Electric Composter",
                18,
                () -> new ElectricComposter(ElectricComposter.Tier.ONE) {
                    @Override
                    public int getEnergyConsumption() {
                        return 9;
                    }

                    @Override
                    public int getSpeed() {
                        return 1;
                    }
                });
        researchId = registerItem(
                plugin,
                researchId,
                "ELECTRIC_COMPOSTER_2",
                "electric_composter_2",
                "Electric Composter II",
                18,
                () -> new ElectricComposter(ElectricComposter.Tier.TWO) {
                    @Override
                    public int getEnergyConsumption() {
                        return 25;
                    }

                    @Override
                    public int getSpeed() {
                        return 4;
                    }
                });
        researchId = registerItem(
                plugin,
                researchId,
                "COBBLESTONE_GENERATOR",
                "cobblestone_generator",
                "Cobblestone Generator",
                40,
                CobblestoneGenerator::new);
        researchId = registerItem(
                plugin, researchId, "VAPORIZER", "vaporizer", "Vaporizer", 18, Vaporizer::new);
        researchId = registerItem(
                plugin,
                researchId,
                "CONCRETE_FACTORY",
                "concrete_factory",
                "Concrete Factory",
                12,
                ConcreteFactory::new);
        registerItem(plugin, researchId, "PULVERIZER", "pulverizer", "Pulverizer", 18, Pulverizer::new);

        int itemsAdded = Slimefun.getRegistry().getAllSlimefunItems().size() - itemCountBefore;
        int researchesAdded = Slimefun.getRegistry().getResearches().size() - researchCountBefore;
        Slimefun.logger()
                .log(
                        Level.INFO,
                        "Registered {0} built-in ExtraTools items and {1} legacy-compatible researches.",
                        new Object[] {itemsAdded, researchesAdded});

        if (itemsAdded < ITEM_IDS.size()) {
            Slimefun.logger()
                    .warning("Built-in ExtraTools registered " + itemsAdded + " of " + ITEM_IDS.size()
                            + " items; any skipped legacy item ids were reported above.");
        }
        if (itemsAdded == 0) {
            Slimefun.logger()
                    .warning("Built-in ExtraTools has no registered items, so its Extra Tools guide page will be hidden.");
        }
    }

    private static int registerItem(
            Slimefun plugin,
            int previousResearchId,
            String itemId,
            String researchKey,
            String researchName,
            int researchCost,
            Supplier<? extends SlimefunItem> itemFactory) {
        int researchId = previousResearchId + 1;

        if (Slimefun.getRegistry().getSlimefunItemIds().containsKey(itemId)) {
            Slimefun.logger().warning("Skipping built-in ExtraTools item because its id is already registered: " + itemId);
            return researchId;
        }

        SlimefunItem item = itemFactory.get();
        item.register(plugin);
        registerResearch(researchId, researchKey, researchName, researchCost, item);
        return researchId;
    }

    private static void registerResearch(int id, String key, String name, int cost, SlimefunItem item) {
        NamespacedKey namespacedKey =
                Objects.requireNonNull(NamespacedKey.fromString(LEGACY_RESEARCH_NAMESPACE + ':' + key));
        Research research = new Research(namespacedKey, id, name, cost);
        research.addItems(item);
        research.register();
    }
}
