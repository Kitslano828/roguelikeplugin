package TomDang.example.roguePlugin.stats;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class StatsListener implements Listener {
    private final StatsService stats;
    private final PlayerLeaseService leases;
    private final Plugin plugin;

    public StatsListener(StatsService stats, PlayerLeaseService leases, Plugin plugin) {
        this.stats = stats;
        this.leases = leases;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // Acquire lease first (sync, cheap)
        boolean ok = leases.acquire(p.getUniqueId());
        if (!ok) {
            p.kick(Component.text("Profile is busy on another server. Please reconnect."));
            return;
        }

        stats.load(p.getUniqueId()).whenComplete((profile, err) -> {
            if (err != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    leases.release(p.getUniqueId());
                    p.kick(Component.text("Failed to load profile."));
                });
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> stats.apply(p), 2L);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        stats.saveAsync(p.getUniqueId());
        stats.unload(p.getUniqueId());
        leases.release(p.getUniqueId());
    }
}

