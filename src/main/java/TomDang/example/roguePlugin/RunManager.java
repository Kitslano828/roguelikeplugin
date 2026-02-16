package TomDang.example.roguePlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RunManager {

    public static final class Run {
        public final UUID runId;
        public final long startedAtMs;
        public final Set<UUID> players = ConcurrentHashMap.newKeySet();

        public Run(UUID runId, long startedAtMs) {
            this.runId = runId;
            this.startedAtMs = startedAtMs;
        }

        public Duration age() {
            return Duration.ofMillis(System.currentTimeMillis() - startedAtMs);
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Run> playerToRun = new ConcurrentHashMap<>();
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private final SharedRunRegistry registry;

    public RunManager(JavaPlugin plugin, SharedRunRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public synchronized Run startRunFor(Player p) {
        // If already in a run, return existing
        Run existing = playerToRun.get(p.getUniqueId());
        if (existing != null) return existing;

        Run run = new Run(UUID.randomUUID(), System.currentTimeMillis());
        run.players.add(p.getUniqueId());

        runs.put(run.runId, run);
        registry.put(p.getUniqueId(), run.runId);
        playerToRun.put(p.getUniqueId(), run);

        plugin.getLogger().info("[Run] START runId=" + run.runId + " player=" + p.getName());
        return run;
    }

    public synchronized boolean leaveRun(Player p, String reason) {
        UUID pid = p.getUniqueId();
        Run run = playerToRun.remove(pid);
        if (run == null) return false;

        run.players.remove(pid);
        registry.remove(pid);

        plugin.getLogger().info("[Run] LEAVE runId=" + run.runId + " player=" + p.getName() + " reason=" + reason);

        // If run is empty, end it
        if (run.players.isEmpty()) {
            runs.remove(run.runId);
            plugin.getLogger().info("[Run] END runId=" + run.runId + " duration=" + run.age().toMinutes() + "m");
        }
        return true;
    }

    public Run getRun(Player p) {
        return playerToRun.get(p.getUniqueId());
    }

    public int activeRuns() {
        return runs.size();
    }

    public void cleanupOfflinePlayers() {
        // Phase 1: If someone disconnects, remove them from run
        for (UUID pid : new ArrayList<>(playerToRun.keySet())) {
            Player online = Bukkit.getPlayer(pid);
            if (online == null || !online.isOnline()) {
                Run run = playerToRun.remove(pid);
                if (run != null) {
                    run.players.remove(pid);
                    registry.remove(pid);
                    plugin.getLogger().info("[Run] OFFLINE_REMOVE runId=" + run.runId + " playerUUID=" + pid);
                    if (run.players.isEmpty()) {
                        runs.remove(run.runId);
                        plugin.getLogger().info("[Run] END runId=" + run.runId + " duration=" + run.age().toMinutes() + "m");
                    }
                }
            }
        }
    }
}