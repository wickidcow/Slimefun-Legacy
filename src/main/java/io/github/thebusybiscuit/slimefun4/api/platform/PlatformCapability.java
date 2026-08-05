package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/**
 * Runtime capabilities detected from the current server instead of inferred from a hard-coded version string.
 *
 * <p>Addons should prefer these capabilities over checking a Paper, Purpur, Folia, or Minecraft version directly.
 */
@SlimefunAPI
public enum PlatformCapability {
    PAPER_API("Paper API"),
    REGION_SCHEDULER_API("Region scheduler API"),
    GLOBAL_REGION_SCHEDULER_API("Global region scheduler API"),
    ASYNC_SCHEDULER_API("Async scheduler API"),
    REGION_OWNED_EXECUTION("Region-owned execution"),
    ASYNC_CHUNK_LOADING("Async chunk loading"),
    ADVENTURE_COMPONENT_MESSAGES("Adventure component messages"),
    DATA_COMPONENT_API("Data component API"),
    PLAYER_PICK_BLOCK_EVENT("Player pick-block event");

    private final String displayName;

    PlatformCapability(String displayName) {
        this.displayName = displayName;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }
}
