package top.maplex.slimeEasy.machine.butcher;

import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/**
 * In-memory operator view for the existing unregistered machine player.
 *
 * <p>Keep this thin Java subclass on the supported 1.21.11 API surface. Added
 * CraftPlayer overloads are inherited from the server rather than linked to
 * newer-only parameter types by a Kotlin-generated override. The existing
 * native permission implementation remains in FakeServerPlayer; neither this
 * view nor setOp writes the server's operator list.
 */
public final class FakeCraftPlayerBridge extends CraftPlayer {
    public FakeCraftPlayerBridge(CraftServer server, ServerPlayer handle) {
        super(server, handle);
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
        // Deliberately retain the original fake-player no-op; never write ops.json.
    }
}
