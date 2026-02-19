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

public final class RoguePlugin extends JavaPlugin {

    private RunManager runManager;
    private SharedRunRegistry registry;
    private StatsService statsService;

    private String lobbyServerName;
    private String dungeonServerName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("mobs.yml", false); // NEW

        FileConfiguration cfg = getConfig();

        lobbyServerName = cfg.getString("servers.lobby", "lobby");
        dungeonServerName = cfg.getString("servers.dungeon", "dungeon1");

        /* =========================
           Shared Run Registry
           ========================= */

        Path registryPath = Paths.get(
                cfg.getString(
                        "sharedState.registryPath",
                        "C:/Users/Administrator/Desktop/mc-rogue-network/runs/run-registry.properties"
                )
        );

        registry = new SharedRunRegistry(this, registryPath);
        registry.ensureExists();

        runManager = new RunManager(this, registry);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        /* =========================
           Stats System
           ========================= */

        boolean dungeonMode = cfg.getString("serverRole", "lobby")
                .equalsIgnoreCase("dungeon");

        Path playerDataDir = Paths.get(
                cfg.getString(
                        "sharedState.playerDataDir",
                        "C:/Users/Administrator/Desktop/mc-rogue-network/shared/playerdata"
                )
        );

        statsService = new StatsService(playerDataDir, dungeonMode);

        // Player stats lifecycle
        getServer().getPluginManager().registerEvents(
                new StatsListener(statsService, this),
                this
        );

        getCommand("stats").setExecutor(new StatsCommand(statsService));

        /* =========================
           Mob System
           ========================= */

        MobTags.init(this);

        statsService.registerProvider(new MobTemplateStatsProvider(this));

        getServer().getPluginManager().registerEvents(
                new MobHealthDisplayListener(this),
                this
        );

        getServer().getPluginManager().registerEvents(
                new MobInspectorListener(statsService),
                this
        );

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


        /* =========================
           Existing Commands (unchanged)
           ========================= */

        // ... your /start, /lobby, /run commands unchanged ...

        Bukkit.getScheduler().runTaskTimer(
                this,
                runManager::cleanupOfflinePlayers,
                20L * 10,
                20L * 10
        );

        getLogger().info("RoguePlugin enabled (Stats + Mobs ready).");
    }

    private void connectPlayerToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);

            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
        } catch (Exception e) {
            getLogger().severe("Failed to connect player: " + e.getMessage());
        }
    }
}
