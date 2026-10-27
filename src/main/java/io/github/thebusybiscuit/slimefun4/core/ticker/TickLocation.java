package io.github.thebusybiscuit.slimefun4.core.ticker;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Location;

@Getter
public class TickLocation {
    private final BlockPosition position;
    private final UUID uuid;

    public TickLocation(BlockPosition position) {
        this.position = position;
        uuid = null;
    }

    public TickLocation(BlockPosition position, UUID uuid) {
        this.position = position;
        this.uuid = uuid;
    }

    /**
     * Registration identity is the physical block position plus its optional
     * UniversalBlock UUID. The ticker registry is a concurrent Set, so value
     * equality is necessary: a second chunk-load/registration for the SAME
     * machine must not schedule a second callback every cycle.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TickLocation that)) {
            return false;
        }
        return position.equals(that.position) && Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return 31 * position.hashCode() + Objects.hashCode(uuid);
    }

    public boolean isUniversal() {
        return uuid != null;
    }

    public Location getLocation() {
        return position.toLocation();
    }
}
