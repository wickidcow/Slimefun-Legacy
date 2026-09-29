package city.norain.slimefun4.compatibillty;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.bukkit.ExplosionResult;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;

@UtilityClass
public class VersionedEvent {

    public BlockExplodeEvent newBlockExplodeEvent(Block block, List<Block> affectedBlock, float yield) {
        return new BlockExplodeEvent(block, block.getState(), affectedBlock, yield, ExplosionResult.DESTROY);
    }

    public Inventory getTopInventory(InventoryEvent event) {
        return event.getView().getTopInventory();
    }

    public Inventory getClickedInventory(InventoryClickEvent event) {
        return event.getClickedInventory();
    }
}
