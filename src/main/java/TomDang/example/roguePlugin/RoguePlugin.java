package TomDang.example.roguePlugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RoguePlugin extends JavaPlugin {

    private RunManager runManager;

    @Override
    public void onEnable() {
        runManager = new RunManager(this);

        // /start
        getCommand("start").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }

            RunManager.Run run = runManager.startRunFor(p);
            p.sendMessage(ChatColor.GREEN + "Run started! " + ChatColor.YELLOW + run.runId);
            p.sendMessage(ChatColor.GRAY + "(Phase 1) No instance spawning yet — just state tracking.");
            return true;
        });

        // /lobby
        getCommand("lobby").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }

            boolean left = runManager.leaveRun(p, "/lobby");
            if (left) {
                p.sendMessage(ChatColor.AQUA + "You left your run and returned to lobby state.");
            } else {
                p.sendMessage(ChatColor.GRAY + "You are not currently in a run.");
            }
            return true;
        });

        // /run
        getCommand("run").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }

            RunManager.Run run = runManager.getRun(p);
            if (run == null) {
                p.sendMessage(ChatColor.GRAY + "No active run.");
            } else {
                p.sendMessage(ChatColor.YELLOW + "Run: " + run.runId);
                p.sendMessage(ChatColor.GRAY + "Age: " + run.age().toMinutes() + "m, Players in run: " + run.players.size());
                p.sendMessage(ChatColor.GRAY + "Active runs on this server: " + runManager.activeRuns());
            }
            return true;
        });

        // Cleanup task (handles disconnects)
        Bukkit.getScheduler().runTaskTimer(this, runManager::cleanupOfflinePlayers, 20L * 10, 20L * 10);

        getLogger().info("RoguePlugin enabled (Phase 1 RunManager).");
    }
}