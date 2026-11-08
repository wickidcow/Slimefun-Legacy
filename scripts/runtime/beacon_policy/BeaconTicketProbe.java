package audit;

import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlusChunkMode;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlusManager;
import io.github.thebusybiscuit.slimefun4.implementation.items.curios.BeaconPlusSupportMode;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** A disposable native-Paper regression plugin. Never package into Slimefun. */
public final class BeaconTicketProbe extends JavaPlugin {
    private static final int X = 2048, Y = 96, Z = 2048;
    private static final int EXTERNAL_X = 144, EXTERNAL_Z = 144;
    private static final UUID OWNER = UUID.fromString("984c4ff4-6ff7-4d36-99a9-64d3e15400bf");

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::verify, 80L);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private void verify() {
        try {
            String phase = Files.readString(Path.of("beacon-policy-phase.txt")).trim();
            require(Bukkit.getOnlinePlayers().isEmpty(), "Probe requires zero online players");
            Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
            require(slimefun != null && slimefun.isEnabled(), "Real Slimefun Legacy must be enabled");
            require(SlimefunItem.getById("BEACON_PLUS") != null, "The actual Resonance Beacon must register");
            World world = Bukkit.getWorlds().get(0);
            Block block = world.getBlockAt(X, Y, Z);
            Location location = block.getLocation();
            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            require(manager != null && manager.isRegistryHealthy(), "Beacon Plus owner manager must be initialized");
            if ("ALWAYS_FIRST".equals(phase)) {
                block.setType(Material.BEACON);
                BlockStorage.store(block, "BEACON_PLUS");
                manager.register(location, OWNER);
                require(manager.updateModes(location, OWNER, BeaconPlusChunkMode.SINGLE, BeaconPlusSupportMode.OFF),
                        "Could not register a real native Beacon Plus Activator mode");
                world.setChunkForceLoaded(EXTERNAL_X, EXTERNAL_Z, true);
            }
            require(block.getType() == Material.BEACON, "Old placed beacon was lost across restart");
            require(manager.getChunkMode(location) == BeaconPlusChunkMode.SINGLE,
                    "Saved Activator mode changed unexpectedly");
            int cx = X >> 4, cz = Z >> 4;
            boolean expect = phase.startsWith("ALWAYS");
            boolean actual = world.getPluginChunkTickets(cx, cz).contains(slimefun);
            require(expect == actual, "Paper plugin chunk ticket mismatch on " + phase
                    + ": expected=" + expect + " actual=" + actual);
            require(!world.isChunkForceLoaded(cx, cz), "New Beacon must not set persistent vanilla force-loaded flag");
            require(world.isChunkForceLoaded(EXTERNAL_X, EXTERNAL_Z),
                    "Unrelated operator force-loaded chunk was modified");
            Path registry = slimefun.getDataFolder().toPath().resolve("adventurers-curios-beacons.properties");
            require(Files.isRegularFile(registry), "Beacon Plus owner registry was not persisted");
            String saved = Files.readString(registry);
            require(saved.contains(OWNER.toString()) && saved.contains(";SINGLE;"),
                    "Stored Beacon Plus owner and Activator mode missing after restart");

            if (phase.equals("ALWAYS_FIRST")) {
                require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "beacon mode off"),
                        "Admin command /beacon mode off is not registered");
                require(!world.getPluginChunkTickets(cx, cz).contains(slimefun),
                        "Admin OFF did not release the active Paper plugin ticket");
                require(manager.getChunkMode(location) == BeaconPlusChunkMode.SINGLE,
                        "Admin OFF reset saved Activator selection");
                require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "beacon mode always"),
                        "Admin command /beacon mode always is not registered");
                require(world.getPluginChunkTickets(cx, cz).contains(slimefun),
                        "Admin ALWAYS did not restore the Paper plugin ticket");
            }

            getLogger().info("BEACON_NATIVE_CHUNK_POLICY_PASS " + phase
                    + " ticket=" + actual + " mode=SINGLE owner=preserved players=0 forceLoaded=false");
        } catch (Throwable error) {
            getLogger().severe("BEACON_NATIVE_CHUNK_POLICY_FAIL "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            error.printStackTrace();
        }
    }
}
