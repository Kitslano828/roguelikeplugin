package TomDang.example.roguePlugin;

import TomDang.example.roguePlugin.mobs.*;
import TomDang.example.roguePlugin.stats.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public final class RoguePlugin extends JavaPlugin {

    private RunManager runManager;
    private SharedRunRegistry registry;
    private StatsService statsService;
    private PlayerLeaseService leaseService;

    private String lobbyServerName;
    private String dungeonServerName;

    @Override
    public void onEnable() {
        // Load config.yml (and create it if missing)
        saveDefaultConfig();

        // NEW: mob templates file
        saveResource("mobs.yml", false);

        FileConfiguration cfg = getConfig();

        String serverId = cfg.getString("server.id", "unknown-server");

        String leasePathStr = cfg.getString(
                "sharedState.playerLeasePath",
                "C:\\mc-rogue-network\\shared\\player-leases.properties"
        );

        long leaseSeconds = cfg.getLong("sharedState.leaseDurationSeconds", 45);
        leaseService = new PlayerLeaseService(Paths.get(leasePathStr), serverId, leaseSeconds * 1000L);
        leaseService.ensureExists();


        lobbyServerName = cfg.getString("servers.lobby", "lobby");
        dungeonServerName = cfg.getString("servers.dungeon", "dungeon1");

        // Shared registry path (shared across all instances on the laptop)
        String registryPathStr = cfg.getString(
                "sharedState.registryPath",
                "C:/Users/Administrator/Desktop/mc-rogue-network/runs/run-registry.properties"
        );

        Path registryPath = Paths.get(registryPathStr);
        registry = new SharedRunRegistry(this, registryPath);
        registry.ensureExists();

        // IMPORTANT: RunManager takes registry
        runManager = new RunManager(this, registry);

        // Needed for sending players between servers on Velocity
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // ===== Player Stats System =====
        String playerDataDirStr = cfg.getString(
                "sharedState.playerDataDir",
                "C:/Users/Administrator/Desktop/mc-rogue-network/shared/playerdata"
        );

        boolean dungeonMode = cfg.getString("sharedState.serverRole", "lobby")
                .equalsIgnoreCase("dungeon");


        statsService = new StatsService(
                Paths.get(playerDataDirStr),
                dungeonMode
        );

        // Register join/quit listener for stats
        getServer().getPluginManager().registerEvents(
                new StatsListener(statsService, leaseService, this),
                this
        );
        getServer().getPluginManager().registerEvents(
                new TomDang.example.roguePlugin.combat.VanillaCombatBlockerListener(),
                this
        );

        // Register /stats command (testing & admin)
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(new StatsCommand(statsService));
        } else {
            getLogger().severe("Command stats missing from plugin.yml");
        }

        // ===== Mob System (NEW) =====
        MobTags.init(this);

        // Mob template provider contributes stats to tagged mobs
        statsService.registerProvider(new MobTemplateStatsProvider(this));

        // Mob health display (number + ❤)
        getServer().getPluginManager().registerEvents(new MobHealthDisplayListener(this), this);

        // Inspector stick GUI
        getServer().getPluginManager().registerEvents(new MobInspectorListener(statsService), this);

        if (getCommand("mobstick") != null) {
            getCommand("mobstick").setExecutor(new MobStickCommand(this));
        } else {
            getLogger().severe("Command mobstick missing from plugin.yml");
        }

        if (getCommand("mobtag") != null) {
            getCommand("mobtag").setExecutor(new MobTagCommand(this, statsService));
        } else {
            getLogger().severe("Command mobtag missing from plugin.yml");
        }

        // =========================
        // YOUR EXISTING COMMANDS (KEPT)
        // =========================

        // /start -> start run state, then send to dungeon server
        if (getCommand("start") != null) {
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
        } else {
            getLogger().severe("Command start missing from plugin.yml");
        }

        // /lobby -> leave run state (if any), then send to lobby server
        if (getCommand("lobby") != null) {
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
        } else {
            getLogger().severe("Command lobby missing from plugin.yml");
        }

        // /run -> status
        if (getCommand("run") != null) {
            getCommand("run").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                RunManager.Run run = runManager.getRun(p);
                if (run == null) {
                    p.sendMessage(ChatColor.GRAY + "No active run.");
                    // Helpful debug: proves the shared file is reachable
                    p.sendMessage(ChatColor.DARK_GRAY + "Registry entries: " + registry.size());
                } else {
                    p.sendMessage(ChatColor.YELLOW + "Run: " + run.runId);
                    p.sendMessage(ChatColor.GRAY + "Age: " + run.age().toMinutes() + "m, Players in run: " + run.players.size());
                    p.sendMessage(ChatColor.GRAY + "Active runs on this server: " + runManager.activeRuns());
                    p.sendMessage(ChatColor.DARK_GRAY + "Registry entries: " + registry.size());
                }
                return true;
            });
        } else {
            getLogger().severe("Command run missing from plugin.yml");
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // refresh at most N per tick so it can't lag
            for (UUID id : statsService.drainDirtyPlayers(50)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    statsService.apply(p);
                }
            }
        }, 10L, 10L); // every 10 ticks


        // Cleanup task (disconnects)
        Bukkit.getScheduler().runTaskTimer(this, runManager::cleanupOfflinePlayers, 20L * 10, 20L * 10);

        getLogger().info("RoguePlugin enabled (runs + stats + mobs).");
    }

    @Override
    public void onDisable() {
        // Safe shutdown for stats IO threads
        if (statsService != null) statsService.shutdown();
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
