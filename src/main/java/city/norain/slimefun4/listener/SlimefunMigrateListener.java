package city.norain.slimefun4.listener;

import com.xzavier0722.mc.plugin.slimefun4.storage.migrator.BlockStorageMigrator;
import com.xzavier0722.mc.plugin.slimefun4.storage.migrator.PlayerProfileMigrator;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class SlimefunMigrateListener implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();

        if ((PlayerProfileMigrator.getInstance().hasOldData()
                        || BlockStorageMigrator.getInstance().hasOldData())
                && p.hasPermission("slimefun.command.migrate")) {
            p.sendMessage(Component.text(
                    "Detected legacy data stored in files. Please run /sf migrate to move the old data into the database!",
                    NamedTextColor.RED));
        }
    }

    public void register(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
}
