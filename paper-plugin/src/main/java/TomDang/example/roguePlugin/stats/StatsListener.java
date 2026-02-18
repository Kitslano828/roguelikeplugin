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
    private final Plugin plugin;

    public StatsListener(StatsService stats, Plugin plugin) {
        this.stats = stats;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        stats.load(p.getUniqueId()).whenComplete((profile, err) -> {
            if (err != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        p.kick(Component.text("Failed to load profile.")));
                return;
            }

            // Apply AFTER join init finishes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                stats.apply(p);
            }, 2L); // 2 ticks is usually enough
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        stats.saveAsync(p.getUniqueId());
        stats.unload(p.getUniqueId());
    }
}
