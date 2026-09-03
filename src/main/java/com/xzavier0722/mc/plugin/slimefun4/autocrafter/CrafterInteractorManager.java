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
     * <p>The cached value is consumed once and never survives a mismatched lookup. Only the
     * live block-menu lookup is reused; the handler is resolved again when requested so addon
     * registration remains authoritative.</p>
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
            CrafterInteractorHandler handler = handlers.get(pending.slimefunId);
            return handler == null ? null : handler.getInteractor(pending.menu);
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

        String slimefunId = blockData.getSfId();
        if (!handlers.containsKey(slimefunId)) {
            return false;
        }

        pendingLookup.set(new PendingLookup(b, slimefunId, blockData.getBlockMenu()));
        return true;
    }

    private static final class PendingLookup {
        private final Block block;
        private final String slimefunId;
        private final BlockMenu menu;

        private PendingLookup(Block block, String slimefunId, BlockMenu menu) {
            this.block = block;
            this.slimefunId = slimefunId;
            this.menu = menu;
        }
    }
}
