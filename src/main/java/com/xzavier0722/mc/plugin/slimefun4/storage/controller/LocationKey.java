package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import java.util.Objects;
import org.bukkit.Location;

public class LocationKey extends ScopeKey {
    private final String locationKey;

    public LocationKey(DataScope scope, Location location) {
        this(scope, LocationUtils.getLocKey(location));
    }

    LocationKey(DataScope scope, String locationKey) {
        super(scope);
        this.locationKey = Objects.requireNonNull(locationKey, "Location key must not be null");
    }

    @Override
    protected String getKeyStr() {
        return scope + "/" + locationKey;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this
                || (obj instanceof LocationKey other
                        && scope == other.scope
                        && locationKey.equals(other.locationKey));
    }
}
