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
        plugin.getLogger().info("[Registry] put " + p.getUniqueId() + " -> " + run.runId);
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

    public synchronized Run getRun(Player p) {
        UUID pid = p.getUniqueId();

        Run existing = playerToRun.get(pid);
        if (existing != null) return existing;

        String runIdStr = registry.getRunId(pid);
        if (runIdStr == null) return null;

        UUID runId = UUID.fromString(runIdStr);

        // Create a local view of the run on this server
        Run run = runs.computeIfAbsent(runId, id -> new Run(id, System.currentTimeMillis()));
        run.players.add(pid);
        playerToRun.put(pid, run);

        return run;
    }

    public int activeRuns() {
        return runs.size();
    }

    public void cleanupOfflinePlayers() {
        // IMPORTANT:
        // "Offline" here only means "not on THIS Paper server".
        // In a Velocity network, that often means the player switched servers.
        for (UUID pid : new ArrayList<>(playerToRun.keySet())) {
            Player onlineHere = Bukkit.getPlayer(pid);
            if (onlineHere == null || !onlineHere.isOnline()) {
                Run run = playerToRun.remove(pid);
                if (run != null) {
                    run.players.remove(pid);

                    // DO NOT touch the shared registry here.
                    // The player might be online on another server (dungeon1, etc.)
                    plugin.getLogger().info("[Run] LOCAL_REMOVE (switch/offline) runId=" + run.runId + " playerUUID=" + pid);
                }
            }
        }
    }
}