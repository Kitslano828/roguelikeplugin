package TomDang.example.roguePlugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class RoguePlugin extends JavaPlugin {

    private RunManager runManager;

    private String lobbyServerName;
    private String dungeonServerName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();

        lobbyServerName = cfg.getString("servers.lobby", "lobby");
        dungeonServerName = cfg.getString("servers.dungeon", "dungeon1");

        runManager = new RunManager(this);

        // Needed for sending players between servers on Velocity
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // /start -> start run state, then send to dungeon server
        getCommand("start").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }

            RunManager.Run run = runManager.startRunFor(p);
            p.sendMessage(ChatColor.GREEN + "Run started! " + ChatColor.YELLOW + run.runId);
            p.sendMessage(ChatColor.GRAY + "Sending you to dungeon server: " + dungeonServerName + "...");

            connectPlayerToServer(p, dungeonServerName);
            return true;
        });

        // /lobby -> leave run state (if any), then send to lobby server
        getCommand("lobby").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Players only.");
                return true;
            }

            boolean left = runManager.leaveRun(p, "/lobby");
            if (left) {
                p.sendMessage(ChatColor.AQUA + "You left your run.");
            } else {
                p.sendMessage(ChatColor.GRAY + "No active run to leave.");
            }

            p.sendMessage(ChatColor.GRAY + "Sending you to lobby server: " + lobbyServerName + "...");
            connectPlayerToServer(p, lobbyServerName);
            return true;
        });

        // /run -> status
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

        // Cleanup task (disconnects)
        Bukkit.getScheduler().runTaskTimer(this, runManager::cleanupOfflinePlayers, 20L * 10, 20L * 10);

        getLogger().info("RoguePlugin enabled (Phase 2: Velocity server switching).");
    }

    private void connectPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);

            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
        } catch (Exception e) {
            getLogger().severe("Failed to connect player to server '" + serverName + "': " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Failed to send you to server: " + serverName);
        }
    }
}