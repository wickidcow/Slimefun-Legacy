package com.xzavier0722.mc.plugin.slimefun4.autocrafter;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import java.util.HashMap;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.block.Block;

/**
 *
 * This manager provide accessibility to custom interactors.
 *
 * @author Xzavier0722
 *
 * @see CrafterInteractable
 * @see CrafterInteractorHandler
 *
 */
public class CrafterInteractorManager {

    private static final Map<String, CrafterInteractorHandler> handlers = new HashMap<>();

    /**
     * Reuses the storage lookup when callers immediately follow {@link #hasInterator(Block)}
     * with {@link #getInteractor(Block)} for the same block.
     *
     * <p>The cached value is consumed once and never survives a mismatched lookup. This keeps
     * custom interactor registration and live block data as the source of truth while avoiding
     * duplicate storage-cache reads in hot Auto Crafter tick paths.</p>
     */
    private static final ThreadLocal<PendingLookup> pendingLookup = new ThreadLocal<>();

    /**
     * Register the specific slimefun item as crafter interactor.
     * @param id: the id of the slimefun item that will be registered as interactor.
     * @param handler: way to get the {@link CrafterInteractable} implementation.
     *
     * @see CrafterInteractorHandler
     */
    public static void register(String id, CrafterInteractorHandler handler) {
        handlers.put(id, handler);
    }

    public static CrafterInteractorHandler getHandler(String id) {
        return handlers.get(id);
    }

    public static CrafterInteractable getInteractor(Block b) {
        PendingLookup pending = pendingLookup.get();
        pendingLookup.remove();

        if (pending != null && pending.block == b) {
            return pending.handler.getInteractor(pending.menu);
        }

        var blockData = StorageCacheUtils.getBlock(b.getLocation());
        if (blockData == null) {
            return null;
        }

        CrafterInteractorHandler handler = handlers.get(blockData.getSfId());
        return handler == null ? null : handler.getInteractor(blockData.getBlockMenu());
    }

    public static boolean hasInterator(Block b) {
        pendingLookup.remove();

        var blockData = StorageCacheUtils.getBlock(b.getLocation());
        if (blockData == null) {
            return false;
        }

        CrafterInteractorHandler handler = handlers.get(blockData.getSfId());
        if (handler == null) {
            return false;
        }

        pendingLookup.set(new PendingLookup(b, handler, blockData.getBlockMenu()));
        return true;
    }

    private static final class PendingLookup {
        private final Block block;
        private final CrafterInteractorHandler handler;
        private final BlockMenu menu;

        private PendingLookup(Block block, CrafterInteractorHandler handler, BlockMenu menu) {
            this.block = block;
            this.handler = handler;
            this.menu = menu;
        }
    }
}
