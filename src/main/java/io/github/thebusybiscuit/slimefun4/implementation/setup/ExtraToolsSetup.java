package io.github.thebusybiscuit.slimefun4.implementation.setup;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.ToolUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.LoreBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * Native Slimefun Legacy integration for the historical ExtraTools addon.
 *
 * <p>The original global item ids and research namespace are intentionally preserved so existing
 * ExtraTools items remain recognizable after removing the standalone addon.</p>
 */
final class ExtraToolsSetup {

    private static final String EXTERNAL_ADDON_NAME = "ExtraTools";
    private static final String RESEARCH_NAMESPACE = "extratools";
    private static final List<String> CANONICAL_IDS = List.of(
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
        Plugin standaloneExtraTools = Bukkit.getPluginManager().getPlugin(EXTERNAL_ADDON_NAME);
        if (standaloneExtraTools != null) {
            Slimefun.logger()
                    .log(
                            Level.INFO,
                            "Standalone ExtraTools detected; built-in ExtraTools compatibility content will not be registered.");
            return;
        }

        for (String id : CANONICAL_IDS) {
            if (SlimefunItem.getById(id) != null) {
                Slimefun.logger()
                        .warning("Built-in ExtraTools was skipped because Slimefun item id '" + id
                                + "' is already registered.");
                return;
            }
        }

        ItemGroup group = new ItemGroup(new NamespacedKey(plugin, "extra_tools"), createGroupIcon(), 3);
        int itemCountBefore = Slimefun.getRegistry().getAllSlimefunItems().size();
        int researchCountBefore = Slimefun.getRegistry().getResearches().size();
        int researchId = 4100;

        SlimefunItemStack hammerStack = new SlimefunItemStack(
                "HAMMER", Material.IRON_PICKAXE, "&cHammer", "", "&9Pulverizes blocks");
        Hammer hammer = new Hammer(
                group,
                hammerStack,
                RecipeType.MAGIC_WORKBENCH,
                new ItemStack[] {
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.IRON_INGOT),
                    new ItemStack(Material.STICK),
                    new ItemStack(Material.IRON_INGOT),
                    null,
                    new ItemStack(Material.STICK),
                    null
                });
        hammer.register(plugin);
        registerResearch("hammer", ++researchId, "Hammer", 3, hammer);

        SlimefunItemStack goldTransmuterStack = machineStack(
                "GOLD_TRANSMUTER",
                Material.YELLOW_TERRACOTTA,
                "&6Gold Transmuter",
                256,
                LoreBuilder.powerPerSecond(18));
        AContainer goldTransmuter = new AContainer(
                group,
                goldTransmuterStack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    null,
                    SlimefunItems.SILVER_INGOT,
                    null,
                    SlimefunItems.ELECTRIC_MOTOR,
                    SlimefunItems.GOLD_24K_BLOCK,
                    SlimefunItems.ELECTRIC_MOTOR,
                    new ItemStack(Material.GOLDEN_PICKAXE),
                    SlimefunItems.MEDIUM_CAPACITOR,
                    new ItemStack(Material.GOLDEN_PICKAXE)
                }) {
            @Override
            public ItemStack getProgressBar() {
                return new ItemStack(Material.GOLDEN_PICKAXE);
            }

            @Override
            public String getInventoryTitle() {
                return "&6Gold Transmuter";
            }

            @Override
            public String getMachineIdentifier() {
                return "GOLD_TRANSMUTER";
            }
        };
        configure(goldTransmuter, 256, 9, 1);
        goldTransmuter.registerRecipe(7, SlimefunItems.GOLD_24K_BLOCK, new ItemStack(Material.GOLD_BLOCK));
        goldTransmuter.registerRecipe(2, SlimefunItems.GOLD_4K, new ItemStack(Material.GOLD_NUGGET, 4));
        goldTransmuter.registerRecipe(2, SlimefunItems.GOLD_6K, new ItemStack(Material.GOLD_NUGGET, 9));
        goldTransmuter.registerRecipe(3, SlimefunItems.GOLD_8K, new ItemStack(Material.GOLD_NUGGET, 13));
        goldTransmuter.registerRecipe(3, SlimefunItems.GOLD_10K, new ItemStack(Material.GOLD_NUGGET, 18));
        goldTransmuter.registerRecipe(4, SlimefunItems.GOLD_12K, new ItemStack(Material.GOLD_NUGGET, 22));
        goldTransmuter.registerRecipe(4, SlimefunItems.GOLD_14K, new ItemStack(Material.GOLD_NUGGET, 27));
        goldTransmuter.registerRecipe(5, SlimefunItems.GOLD_16K, new ItemStack(Material.GOLD_NUGGET, 31));
        goldTransmuter.registerRecipe(5, SlimefunItems.GOLD_18K, new ItemStack(Material.GOLD_NUGGET, 36));
        goldTransmuter.registerRecipe(6, SlimefunItems.GOLD_20K, new ItemStack(Material.GOLD_NUGGET, 40));
        goldTransmuter.registerRecipe(6, SlimefunItems.GOLD_22K, new ItemStack(Material.GOLD_NUGGET, 45));
        goldTransmuter.registerRecipe(7, SlimefunItems.GOLD_24K, new ItemStack(Material.GOLD_NUGGET, 49));
        goldTransmuter.registerRecipe(2, new ItemStack(Material.GOLD_INGOT), SlimefunItems.GOLD_DUST);
        goldTransmuter.register(plugin);
        registerResearch("gold_transmuter", ++researchId, "Gold Transmuter", 12, goldTransmuter);

        SlimefunItemStack composterOneStack = machineStack(
                "ELECTRIC_COMPOSTER",
                Material.MAGENTA_TERRACOTTA,
                "&cElectric Composter",
                256,
                "&8⇨ &7Speed: 1x",
                LoreBuilder.powerPerSecond(18));
        AContainer composterOne = createComposter(
                group,
                composterOneStack,
                new ItemStack[] {
                    SlimefunItems.GILDED_IRON,
                    SlimefunItems.MAGNESIUM_INGOT,
                    SlimefunItems.GILDED_IRON,
                    SlimefunItems.ELECTRIC_MOTOR,
                    SlimefunItems.COMPOSTER,
                    SlimefunItems.ELECTRIC_MOTOR,
                    new ItemStack(Material.IRON_HOE),
                    SlimefunItems.MEDIUM_CAPACITOR,
                    new ItemStack(Material.IRON_HOE)
                },
                "ELECTRIC_COMPOSTER_ONE",
                "&cElectric Composter");
        configure(composterOne, 256, 9, 1);
        registerComposterRecipes(composterOne);
        composterOne.register(plugin);
        registerResearch("electric_composter", ++researchId, "Electric Composter", 18, composterOne);

        SlimefunItemStack composterTwoStack = machineStack(
                "ELECTRIC_COMPOSTER_2",
                Material.MAGENTA_TERRACOTTA,
                "&cElectric Composter &7(&eII&7)",
                256,
                "&8⇨ &7Speed: 4x",
                LoreBuilder.powerPerSecond(50));
        AContainer composterTwo = createComposter(
                group,
                composterTwoStack,
                new ItemStack[] {
                    SlimefunItems.HARDENED_METAL_INGOT,
                    SlimefunItems.BLISTERING_INGOT_3,
                    SlimefunItems.HARDENED_METAL_INGOT,
                    SlimefunItems.ELECTRIC_MOTOR,
                    composterOneStack,
                    SlimefunItems.ELECTRIC_MOTOR,
                    new ItemStack(Material.DIAMOND_HOE),
                    SlimefunItems.LARGE_CAPACITOR,
                    new ItemStack(Material.DIAMOND_HOE)
                },
                "ELECTRIC_COMPOSTER_TWO",
                "&cElectric Composter &7(&eII&7)");
        configure(composterTwo, 256, 25, 4);
        registerComposterRecipes(composterTwo);
        composterTwo.register(plugin);
        registerResearch("electric_composter_2", ++researchId, "Electric Composter II", 18, composterTwo);

        SlimefunItemStack cobbleStack = machineStack(
                "COBBLESTONE_GENERATOR",
                Material.POLISHED_ANDESITE,
                "&cCobblestone Generator",
                512,
                LoreBuilder.powerPerSecond(36));
        CobblestoneGenerator cobblestoneGenerator = new CobblestoneGenerator(
                group,
                cobbleStack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER,
                    SlimefunItems.MAGNESIUM_INGOT,
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER,
                    new ItemStack(Material.WATER_BUCKET),
                    SlimefunItems.BLISTERING_INGOT_3,
                    new ItemStack(Material.LAVA_BUCKET),
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER,
                    SlimefunItems.BIG_CAPACITOR,
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER
                });
        cobblestoneGenerator.register(plugin);
        registerResearch("cobblestone_generator", ++researchId, "Cobblestone Generator", 40, cobblestoneGenerator);

        SlimefunItemStack vaporizerStack = machineStack(
                "VAPORIZER",
                Material.RED_STAINED_GLASS,
                "&cVaporizer",
                256,
                LoreBuilder.powerPerSecond(32));
        AContainer vaporizer = new AContainer(
                group,
                vaporizerStack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    new ItemStack(Material.MAGMA_BLOCK),
                    SlimefunItems.ELECTRIC_MOTOR,
                    new ItemStack(Material.MAGMA_BLOCK),
                    SlimefunItems.HEATING_COIL,
                    SlimefunItems.FLUID_PUMP,
                    SlimefunItems.HEATING_COIL,
                    new ItemStack(Material.MAGMA_BLOCK),
                    SlimefunItems.MEDIUM_CAPACITOR,
                    new ItemStack(Material.MAGMA_BLOCK)
                }) {
            @Override
            public ItemStack getProgressBar() {
                return new ItemStack(Material.IRON_HOE);
            }

            @Override
            public String getInventoryTitle() {
                return "&cVaporizer";
            }

            @Override
            public String getMachineIdentifier() {
                return "VAPORIZER";
            }

            @Override
            public List<ItemStack> getDisplayRecipes() {
                List<ItemStack> display = new ArrayList<>(recipes.size() * 2);
                for (MachineRecipe recipe : recipes) {
                    display.add(recipe.getInput()[0]);
                    display.add(recipe.getOutput()[recipe.getOutput().length - 1]);
                }
                return display;
            }
        };
        configure(vaporizer, 256, 16, 1);
        vaporizer.registerRecipe(
                8,
                new ItemStack[] {new ItemStack(Material.WATER_BUCKET)},
                new ItemStack[] {new ItemStack(Material.BUCKET), amount(SlimefunItems.SALT, 4)});
        vaporizer.registerRecipe(
                8,
                new ItemStack[] {new ItemStack(Material.LAVA_BUCKET)},
                new ItemStack[] {new ItemStack(Material.BUCKET), amount(SlimefunItems.SULFATE, 16)});
        vaporizer.registerRecipe(3, new ItemStack(Material.MAGMA_BLOCK), SlimefunItems.SULFATE);
        vaporizer.register(plugin);
        registerResearch("vaporizer", ++researchId, "Vaporizer", 18, vaporizer);

        SlimefunItemStack concreteStack = machineStack(
                "CONCRETE_FACTORY",
                Material.BLACK_CONCRETE,
                "&4Concrete Factory",
                256,
                LoreBuilder.powerPerSecond(16));
        AContainer concreteFactory = new AContainer(
                group,
                concreteStack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    new ItemStack(Material.WATER_BUCKET),
                    SlimefunItems.GILDED_IRON,
                    new ItemStack(Material.WATER_BUCKET),
                    SlimefunItems.ADVANCED_CIRCUIT_BOARD,
                    SlimefunItems.ELECTRIC_MOTOR,
                    SlimefunItems.ADVANCED_CIRCUIT_BOARD,
                    new ItemStack(Material.WATER_BUCKET),
                    SlimefunItems.SMALL_CAPACITOR,
                    new ItemStack(Material.WATER_BUCKET)
                }) {
            @Override
            public ItemStack getProgressBar() {
                return new ItemStack(Material.IRON_SHOVEL);
            }

            @Override
            public String getInventoryTitle() {
                return "&cConcrete Factory";
            }

            @Override
            public String getMachineIdentifier() {
                return "CONCRETE_FACTORY";
            }
        };
        configure(concreteFactory, 256, 8, 1);
        registerConcreteRecipes(concreteFactory);
        concreteFactory.register(plugin);
        registerResearch("concrete_factory", ++researchId, "Concrete Factory", 12, concreteFactory);

        SlimefunItemStack pulverizerStack = machineStack(
                "PULVERIZER",
                Material.ORANGE_TERRACOTTA,
                "&cPulverizer",
                256,
                "&8⇨ &7Speed: 4x",
                LoreBuilder.powerPerSecond(50));
        AContainer pulverizer = new AContainer(
                group,
                pulverizerStack,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    SlimefunItems.SILICON,
                    SlimefunItems.HARDENED_METAL_INGOT,
                    SlimefunItems.SILICON,
                    SlimefunItems.ELECTRIC_MOTOR,
                    SlimefunItems.STEEL_PLATE,
                    SlimefunItems.ELECTRIC_MOTOR,
                    new ItemStack(Material.IRON_PICKAXE),
                    SlimefunItems.MEDIUM_CAPACITOR,
                    new ItemStack(Material.IRON_PICKAXE)
                }) {
            @Override
            public ItemStack getProgressBar() {
                return new ItemStack(Material.IRON_PICKAXE);
            }

            @Override
            public String getInventoryTitle() {
                return "&cPulverizer";
            }

            @Override
            public String getMachineIdentifier() {
                return "PULVERIZER";
            }
        };
        configure(pulverizer, 256, 25, 4);
        registerPulverizerRecipes(pulverizer);
        pulverizer.register(plugin);
        registerResearch("pulverizer", ++researchId, "Pulverizer", 18, pulverizer);

        int itemsAdded = Slimefun.getRegistry().getAllSlimefunItems().size() - itemCountBefore;
        int researchesAdded = Slimefun.getRegistry().getResearches().size() - researchCountBefore;
        Slimefun.logger()
                .log(
                        Level.INFO,
                        "Registered {0} built-in ExtraTools items and {1} legacy-compatible researches.",
                        new Object[] {itemsAdded, researchesAdded});
    }

    private static AContainer createComposter(
            ItemGroup group, SlimefunItemStack stack, ItemStack[] recipe, String identifier, String title) {
        return new AContainer(group, stack, RecipeType.ENHANCED_CRAFTING_TABLE, recipe) {
            @Override
            public ItemStack getProgressBar() {
                return new ItemStack(Material.WOODEN_HOE);
            }

            @Override
            public String getInventoryTitle() {
                return title;
            }

            @Override
            public String getMachineIdentifier() {
                return identifier;
            }
        };
    }

    private static void configure(AContainer machine, int capacity, int energyConsumption, int speed) {
        machine.setCapacity(capacity);
        machine.setEnergyConsumption(energyConsumption);
        machine.setProcessingSpeed(speed);
    }

    private static void registerComposterRecipes(AContainer machine) {
        for (Material leaves : Tag.LEAVES.getValues()) {
            machine.registerRecipe(8, new ItemStack(leaves, 8), new ItemStack(Material.DIRT));
        }
        for (Material sapling : Tag.SAPLINGS.getValues()) {
            machine.registerRecipe(8, new ItemStack(sapling, 8), new ItemStack(Material.DIRT));
        }
        machine.registerRecipe(8, new ItemStack(Material.STONE, 4), new ItemStack(Material.NETHERRACK));
        machine.registerRecipe(8, new ItemStack(Material.SAND, 2), new ItemStack(Material.SOUL_SAND));
        machine.registerRecipe(8, new ItemStack(Material.WHEAT, 4), new ItemStack(Material.NETHER_WART));
    }

    private static void registerConcreteRecipes(AContainer machine) {
        Material[] powders = {
            Material.WHITE_CONCRETE_POWDER,
            Material.ORANGE_CONCRETE_POWDER,
            Material.MAGENTA_CONCRETE_POWDER,
            Material.LIGHT_BLUE_CONCRETE_POWDER,
            Material.YELLOW_CONCRETE_POWDER,
            Material.LIME_CONCRETE_POWDER,
            Material.PINK_CONCRETE_POWDER,
            Material.GRAY_CONCRETE_POWDER,
            Material.LIGHT_GRAY_CONCRETE_POWDER,
            Material.CYAN_CONCRETE_POWDER,
            Material.PURPLE_CONCRETE_POWDER,
            Material.BLUE_CONCRETE_POWDER,
            Material.BROWN_CONCRETE_POWDER,
            Material.GREEN_CONCRETE_POWDER,
            Material.RED_CONCRETE_POWDER,
            Material.BLACK_CONCRETE_POWDER
        };
        Material[] concrete = {
            Material.WHITE_CONCRETE,
            Material.ORANGE_CONCRETE,
            Material.MAGENTA_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE,
            Material.PINK_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE,
            Material.PURPLE_CONCRETE,
            Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.RED_CONCRETE,
            Material.BLACK_CONCRETE
        };

        for (int i = 0; i < powders.length; i++) {
            machine.registerRecipe(4, new ItemStack(powders[i], 8), new ItemStack(concrete[i], 8));
        }
    }

    private static void registerPulverizerRecipes(AContainer machine) {
        Material[] toSand = {
            Material.STONE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.COBBLESTONE,
            Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE,
            Material.TUFF,
            Material.CALCITE,
            Material.GRAVEL,
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL
        };

        for (Material material : toSand) {
            machine.registerRecipe(8, new ItemStack(material, 4), new ItemStack(Material.SAND));
        }
        machine.registerRecipe(8, new ItemStack(Material.NETHERRACK, 4), new ItemStack(Material.SOUL_SAND));
    }

    private static SlimefunItemStack machineStack(
            String id, Material material, String name, int capacity, String... extraLore) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(LoreBuilder.machine(
                io.github.thebusybiscuit.slimefun4.core.attributes.MachineTier.ADVANCED,
                io.github.thebusybiscuit.slimefun4.core.attributes.MachineType.MACHINE));
        for (String line : extraLore) {
            lore.add(line);
        }
        lore.add(LoreBuilder.powerBuffer(capacity));
        return new SlimefunItemStack(id, material, name, lore.toArray(String[]::new));
    }

    private static ItemStack createGroupIcon() {
        ItemStack icon = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "Extra Tools");
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack amount(ItemStack original, int amount) {
        ItemStack copy = original.clone();
        copy.setAmount(amount);
        return copy;
    }

    private static void registerResearch(String key, int id, String name, int cost, SlimefunItem item) {
        NamespacedKey namespacedKey =
                Objects.requireNonNull(NamespacedKey.fromString(RESEARCH_NAMESPACE + ':' + key));
        Research research = new Research(namespacedKey, id, name, cost);
        research.addItems(item);
        research.register();
    }

    private static final class Hammer extends SimpleSlimefunItem<ToolUseHandler> {

        private Hammer(ItemGroup group, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
            super(group, item, recipeType, recipe);
        }

        @Override
        public ToolUseHandler getItemHandler() {
            return (event, tool, fortune, drops) -> {
                if (!Slimefun.getPermissionsService().hasPermission(event.getPlayer(), this)) {
                    return;
                }

                ItemStack drop = getHammerDrop(event.getBlock());
                if (drop != null) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);
                    event.setDropItems(false);
                }
            };
        }

        private ItemStack getHammerDrop(Block block) {
            return switch (block.getType()) {
                case STONE, GRANITE, DIORITE, ANDESITE, COBBLESTONE, DEEPSLATE, COBBLED_DEEPSLATE, TUFF, CALCITE ->
                    new ItemStack(Material.GRAVEL);
                case GRAVEL, GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL -> new ItemStack(Material.SAND);
                case IRON_ORE, DEEPSLATE_IRON_ORE -> SlimefunItems.IRON_DUST.clone();
                case GOLD_ORE, DEEPSLATE_GOLD_ORE -> SlimefunItems.GOLD_DUST.clone();
                case COPPER_ORE, DEEPSLATE_COPPER_ORE -> SlimefunItems.COPPER_DUST.clone();
                case NETHERRACK -> new ItemStack(Material.SOUL_SAND);
                default -> null;
            };
        }
    }

    private static final class CobblestoneGenerator extends SimpleSlimefunItem<BlockTicker>
            implements InventoryBlock, EnergyNetComponent {

        private static final int ENERGY_CONSUMPTION = 32;
        private static final int[] OUTPUT_SLOTS = {24, 25};
        private static final int[] BORDER = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 36, 37,
            38, 39, 40, 41, 42, 43, 44
        };
        private static final int[] OUTPUT_BORDER = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

        private boolean productionTick;

        private CobblestoneGenerator(
                ItemGroup group, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
            super(group, item, recipeType, recipe);
            createPreset(this, getItemName(), this::constructMenu);
            addItemHandler(onBlockBreak());
        }

        private void constructMenu(BlockMenuPreset preset) {
            for (int slot : BORDER) {
                preset.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }
            for (int slot : OUTPUT_BORDER) {
                preset.addItem(slot, ChestMenuUtils.getOutputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
            }

            for (int slot : OUTPUT_SLOTS) {
                preset.addMenuClickHandler(slot, new ChestMenu.AdvancedMenuClickHandler() {
                    @Override
                    public boolean onClick(Player player, int clickedSlot, ItemStack cursor, ClickAction action) {
                        return false;
                    }

                    @Override
                    public boolean onClick(
                            InventoryClickEvent event,
                            Player player,
                            int clickedSlot,
                            ItemStack cursor,
                            ClickAction action) {
                        return cursor == null || cursor.getType() == Material.AIR;
                    }
                });
            }
        }

        private BlockBreakHandler onBlockBreak() {
            return new SimpleBlockBreakHandler() {
                @Override
                public void onBlockBreak(Block block) {
                    BlockMenu menu = StorageCacheUtils.getMenu(block.getLocation());
                    if (menu != null) {
                        menu.dropItems(block.getLocation(), OUTPUT_SLOTS);
                    }
                }
            };
        }

        @Override
        public int[] getInputSlots() {
            return new int[0];
        }

        @Override
        public int[] getOutputSlots() {
            return OUTPUT_SLOTS.clone();
        }

        @Override
        public EnergyNetComponentType getEnergyComponentType() {
            return EnergyNetComponentType.CONSUMER;
        }

        @Override
        public int getCapacity() {
            return 512;
        }

        @Override
        public BlockTicker getItemHandler() {
            return new BlockTicker() {
                @Override
                public void uniqueTick() {
                    productionTick = !productionTick;
                }

                @Override
                public void tick(Block block, SlimefunItem item, SlimefunBlockData data) {
                    if (!productionTick || getChargeLong(block.getLocation(), data) < ENERGY_CONSUMPTION) {
                        return;
                    }

                    BlockMenu menu = StorageCacheUtils.getMenu(block.getLocation());
                    if (menu == null) {
                        return;
                    }

                    ItemStack cobblestone = new ItemStack(Material.COBBLESTONE);
                    if (menu.fits(cobblestone, OUTPUT_SLOTS)) {
                        removeCharge(block.getLocation(), ENERGY_CONSUMPTION, data);
                        menu.pushItem(cobblestone, OUTPUT_SLOTS);
                    }
                }

                @Override
                public boolean isSynchronized() {
                    return true;
                }
            };
        }
    }
}
