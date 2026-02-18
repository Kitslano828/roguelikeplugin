package TomDang.example.roguePlugin.stats;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StatsListener implements Listener {
    private final StatsService stats;

    public StatsListener(StatsService stats) {
        this.stats = stats;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        stats.load(p.getUniqueId()).whenComplete((profile, err) -> {
            if (err != null) {
                p.kick(Component.text("Failed to load profile."));
                return;
            }
            // Apply on main thread
            org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    () -> stats.apply(p)
            );
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        stats.saveAsync(p.getUniqueId());
        stats.unload(p.getUniqueId());
    }
}
