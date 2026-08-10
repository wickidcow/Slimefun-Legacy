package io.github.thebusybiscuit.slimefun4.core;

import io.github.bakedlibs.dough.collections.KeyMap;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced.LegacyGuideBootstrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.api.BlockInfoConfig;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.apache.commons.lang.Validate;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * This class houses a lot of instances of {@link Map} and {@link List} that hold
 * various mappings and collections related to {@link SlimefunItem}.
 *
 * @author TheBusyBiscuit
 */
public final class SlimefunRegistry {

    private final Map<String, SlimefunItem> slimefunIds = new HashMap<>();
    private final List<SlimefunItem> slimefunItems = new ArrayList<>();
    private final List<SlimefunItem> enabledItems = new ArrayList<>();
    private final List<ItemGroup> categories = new ArrayList<>();
    private final List<MultiBlock> multiblocks = new LinkedList<>();
    private final List<Research> researches = new LinkedList<>();
    private final List<String> researchRanks = new ArrayList<>();
    private final Set<UUID> researchingPlayers = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> tickers = new HashSet<>();
    private final Set<SlimefunItem> radioactive = new HashSet<>();
    private final Set<ItemStack> barterDrops = new HashSet<>();

    private NamespacedKey soulboundKey;
    private NamespacedKey itemChargeKey;
    private NamespacedKey guideKey;

    private final KeyMap<GEOResource> geoResources = new KeyMap<>();
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, BlockInfoConfig> chunks = new HashMap<>();
    private final Map<SlimefunGuideMode, SlimefunGuideImplementation> guides = new EnumMap<>(SlimefunGuideMode.class);
    private final Map<EntityType, Set<ItemStack>> mobDrops = new EnumMap<>(EntityType.class);
    private final Map<String, BlockMenuPreset> blockMenuPresets = new HashMap<>();
    private final Map<Class<? extends ItemHandler>, Set<ItemHandler>> globalItemHandlers = new HashMap<>();

    public void load(@Nonnull Slimefun plugin) {
        Validate.notNull(plugin, "The Plugin cannot be null!");

        soulboundKey = new NamespacedKey(plugin, "soulbound");
        itemChargeKey = new NamespacedKey(plugin, "item_charge");
        guideKey = new NamespacedKey(plugin, "slimefun_guide_mode");

        LegacyGuideBootstrap.register(plugin, guides);

        var cfg = Slimefun.getConfigManager().getPluginConfig();
        researchRanks.addAll(cfg.getStringList("research-ranks"));
    }

    @Nonnull
    public List<ItemGroup> getAllItemGroups() {
        return categories;
    }

    public @Nonnull List<SlimefunItem> getAllSlimefunItems() {
        return slimefunItems;
    }

    public @Nonnull List<SlimefunItem> getDisabledSlimefunItems() {
        List<SlimefunItem> allItems = new ArrayList<>(getAllSlimefunItems());
        return new ArrayList<>(
                allItems.stream().filter(SlimefunItem::isDisabled).toList());
    }

    @Nonnull
    public List<SlimefunItem> getEnabledSlimefunItems() {
        return enabledItems;
    }

    @Nonnull
    public List<Research> getResearches() {
        return researches;
    }

    @Nonnull
    public Set<UUID> getCurrentlyResearchingPlayers() {
        return researchingPlayers;
    }

    @Nonnull
    public List<String> getResearchRanks() {
        return researchRanks;
    }

    @Nonnull
    public List<MultiBlock> getMultiBlocks() {
        return multiblocks;
    }

    @Nonnull
    public SlimefunGuideImplementation getSlimefunGuide(@Nonnull SlimefunGuideMode mode) {
        Validate.notNull(mode, "The Guide mode cannot be null");
        SlimefunGuideImplementation guide = guides.get(mode);
        if (guide == null) {
            throw new IllegalStateException("Slimefun Guide '" + mode + "' has no registered implementation.");
        }
        return guide;
    }

    @Nonnull
    public Map<EntityType, Set<ItemStack>> getMobDrops() {
        return mobDrops;
    }

    @Nonnull
    public Set<ItemStack> getBarteringDrops() {
        return barterDrops;
    }

    @Nonnull
    public Set<SlimefunItem> getRadioactiveItems() {
        return radioactive;
    }

    @Nonnull
    public Set<String> getTickerBlocks() {
        return tickers;
    }

    @Nonnull
    public Map<String, SlimefunItem> getSlimefunItemIds() {
        return slimefunIds;
    }

    @Nonnull
    public Map<String, BlockMenuPreset> getMenuPresets() {
        return blockMenuPresets;
    }

    @Nonnull
    public Map<UUID, PlayerProfile> getPlayerProfiles() {
        return profiles;
    }

    @Nonnull
    public Map<Class<? extends ItemHandler>, Set<ItemHandler>> getGlobalItemHandlers() {
        return globalItemHandlers;
    }

    @Nonnull
    public Set<ItemHandler> getGlobalItemHandlers(@Nonnull Class<? extends ItemHandler> identifier) {
        Validate.notNull(identifier, "The identifier for an ItemHandler cannot be null!");
        return globalItemHandlers.computeIfAbsent(identifier, c -> new HashSet<>());
    }

    @Nonnull
    public Map<String, BlockInfoConfig> getChunks() {
        return chunks;
    }

    @Nonnull
    public KeyMap<GEOResource> getGEOResources() {
        return geoResources;
    }

    @Nonnull
    public NamespacedKey getSoulboundDataKey() {
        return soulboundKey;
    }

    @Nonnull
    public NamespacedKey getItemChargeDataKey() {
        return itemChargeKey;
    }

    @Nonnull
    public NamespacedKey getGuideDataKey() {
        return guideKey;
    }

    @Deprecated
    public boolean isFreeCreativeResearchingEnabled() {
        return Slimefun.getConfigManager().isFreeCreativeResearchingEnabled();
    }
}
