package TomDang.example.velocityOrchestrator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import TomDang.example.velocityOrchestrator.party.PartyManager;
import TomDang.example.velocityOrchestrator.party.PartyCommand;
import TomDang.example.velocityOrchestrator.party.Party;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;

import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Optional;

@Plugin(
        id = "rogue_orchestrator",
        name = "Rogue Orchestrator",
        version = "1.0.0"
)
public final class VelocityOrchestrator {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private PartyManager partyManager;

    private final Gson gson = new Gson();
    private InstanceManager instances;
    private RunRegistry registry;

    @Inject
    public VelocityOrchestrator(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) throws Exception {
        Files.createDirectories(dataDir);

        JsonObject cfg = loadOrCreateConfig();
        String baseUrl = cfg.get("orchestratorBaseUrl").getAsString();
        long pollMs = cfg.get("pollIntervalMs").getAsLong();
        long timeoutMs = cfg.get("spawnTimeoutMs").getAsLong();
        String prefix = cfg.get("serverNamePrefix").getAsString();

        String regPath = cfg.get("runRegistryPath").getAsString();
        this.registry = new RunRegistry(Path.of(regPath));

        this.instances = new InstanceManager(
                proxy,
                new OrchestratorClient(baseUrl),
                pollMs,
                Duration.ofMillis(timeoutMs),
                prefix
        );
        this.partyManager = new PartyManager(Duration.ofMinutes(2)); // invite TTL

        proxy.getCommandManager().register("party", new PartyCommand(proxy, partyManager));

        proxy.getCommandManager().register("runstart", new RunStart(instances, partyManager));
        proxy.getCommandManager().register("runend", new RunEnd(instances, registry));

        logger.info("Rogue Orchestrator loaded. baseUrl={}", baseUrl);
    }

    private JsonObject loadOrCreateConfig() throws Exception {
        Path p = dataDir.resolve("config.json");
        if (!Files.exists(p)) {
            JsonObject cfg = new JsonObject();
            cfg.addProperty("orchestratorBaseUrl", "http://127.0.0.1:9001");
            cfg.addProperty("pollIntervalMs", 500);
            cfg.addProperty("spawnTimeoutMs", 40000);
            cfg.addProperty("serverNamePrefix", "run-");
            cfg.addProperty("runRegistryPath",
                    "C:\\mc-rogue-network\\shared\\run-registry.properties");
            Files.writeString(p, gson.toJson(cfg));
            return cfg;
        }
        return gson.fromJson(Files.readString(p), JsonObject.class);
    }

    private final class RunStart implements SimpleCommand {
        private final InstanceManager instances;
        private final PartyManager parties;

        private RunStart(InstanceManager instances, PartyManager parties) {
            this.instances = instances;
            this.parties = parties;
        }

        @Override
        public void execute(Invocation inv) {
            if (!(inv.source() instanceof Player player)) {
                inv.source().sendMessage(Component.text("Players only."));
                return;
            }

            UUID starter = player.getUniqueId();

            // Determine if starter is in a party
            var partyOpt = parties.getPartyOf(starter);

            // Party rules
            UUID partyId = null;
            List<UUID> members = new ArrayList<>();
            UUID ownerForListing = starter;

            if (partyOpt.isPresent()) {
                Party p = partyOpt.get();

                if (!p.isLeader(starter)) {
                    player.sendMessage(Component.text("Only the party leader can start a run."));
                    return;
                }

                partyId = p.partyId();
                ownerForListing = starter; // leader
                members.addAll(List.copyOf(p.membersView())); // snapshot
            } else {
                members.add(starter);
            }

            // If party already has an active run, just connect everyone
            if (partyId != null) {
                var existingRun = instances.getRunForParty(partyId);
                if (existingRun.isPresent()) {
                    String runId = existingRun.get();
                    connectAll(player, runId, members);
                    return;
                }
            }

            // Otherwise create a new run
            String runId = UUID.randomUUID().toString();
            instances.markRunStarted(runId, ownerForListing, partyId, members);
            try {
                registry.bindPlayers(members, runId);
            } catch (Exception e) {
                player.sendMessage(Component.text("Failed to write run registry."));
                instances.cleanup(runId);
                return;
            }


            player.sendMessage(Component.text("Spawning run " + runId + " for " + members.size() + " player(s)..."));

            instances.spawnRegister(runId)
                    .whenComplete((server, err) -> {
                        if (err != null) {
                            player.sendMessage(Component.text("Failed to spawn: " + err.getMessage()));
                            instances.cleanup(runId);
                            return;
                        }
                        connectAll(player, runId, members);
                    });
        }

        private void connectAll(Player caller, String runId, List<UUID> members) {
            instances.getServer(runId).ifPresentOrElse(server -> {
                for (UUID u : members) {
                    proxy.getPlayer(u).ifPresent(pl -> {
                        pl.createConnectionRequest(server).connect()
                                .whenComplete((res, err) -> {
                                    if (err != null) {
                                        pl.sendMessage(Component.text("Failed to connect to run: " + err.getMessage()));
                                    } else {
                                        pl.sendMessage(Component.text("Connected to run: " + runId));
                                    }
                                });
                    });
                }
                caller.sendMessage(Component.text("Use /runend " + runId + " to cleanup."));
            }, () -> caller.sendMessage(Component.text("Run server not registered yet for " + runId)));
        }
    }


    private static final class RunEnd implements SimpleCommand {
        private final InstanceManager instances;
        private final RunRegistry registry;

        private RunEnd(InstanceManager instances, RunRegistry registry) {
            this.instances = instances;
            this.registry = registry;
        }

        @Override
        public void execute(Invocation inv) {
            if (inv.arguments().length < 1) {
                if (inv.source() instanceof Player p) {
                    instances.getRunFor(p.getUniqueId()).ifPresentOrElse(runId -> {
                        instances.getMembersForRun(runId).ifPresent(members -> {
                            try {
                                registry.unbindPlayers(members);
                            } catch (Exception ignored) {}
                        });
                        instances.cleanup(runId);
                        p.sendMessage(Component.text("Cleanup requested for " + runId));
                    }, () -> p.sendMessage(Component.text("No active run found for you.")));
                } else {
                    inv.source().sendMessage(Component.text("Usage: /runend <runId>"));
                }
                return;
            }

            String runId = inv.arguments()[0];
            instances.getMembersForRun(runId).ifPresent(members -> {
                try {
                    registry.unbindPlayers(members);
                } catch (Exception ignored) {}
            });
            instances.cleanup(runId);
            inv.source().sendMessage(Component.text("Cleanup requested for " + runId));
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<String>> suggestAsync(Invocation inv) {
            if (!(inv.source() instanceof Player p)) {
                return java.util.concurrent.CompletableFuture.completedFuture(List.of());
            }

            String[] args = inv.arguments();
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

            List<String> candidates = instances.getActiveRunIdsFor(p.getUniqueId());
            candidates.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
            return java.util.concurrent.CompletableFuture.completedFuture(candidates);
        }
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        if (instances == null) return;

        String target = event.getOriginalServer().getServerInfo().getName();
        if (!instances.isRunServerName(target)) return;

        Player player = event.getPlayer();
        String runId = instances.runIdFromServerName(target).orElse(null);
        if (runId == null) return;

        if (!instances.isPlayerAllowedOnRun(player.getUniqueId(), runId)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.sendMessage(Component.text("You are not allowed to join that dungeon run."));
        }
    }

}