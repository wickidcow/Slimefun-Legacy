package city.norain.slimefun4.compatibillty;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.data.BlockData;

@UtilityClass
public class CompatibilityUtil {
    /**
     * Returns the item material used to place the supplied block data.
     *
     * @param blockData the block data to inspect
     * @return the placement material
     */
    public Material getPlacementMaterial(BlockData blockData) {
        return blockData.getPlacementMaterial();
    }

    /**
     * Checks whether the player is connected.
     *
     * <p>Offline-mode servers retain the historical online-state fallback.
     *
     * @param player the offline-player reference
     * @return whether the player is connected or online
     */
    public boolean isConnected(OfflinePlayer player) {
        return Slimefun.instance().getServer().getOnlineMode() ? player.isConnected() : player.isOnline();
    }
}
