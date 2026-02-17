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

import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.Locale;

@Plugin(
        id = "rogue_orchestrator",
        name = "Rogue Orchestrator",
        version = "1.0.0"
)
public final class VelocityOrchestrator {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private final Gson gson = new Gson();
    private InstanceManager instances;

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

        this.instances = new InstanceManager(
                proxy,
                new OrchestratorClient(baseUrl),
                pollMs,
                Duration.ofMillis(timeoutMs),
                prefix
        );

        proxy.getCommandManager().register("runstart", new RunStart(instances));
        proxy.getCommandManager().register("runend", new RunEnd(instances));

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
            Files.writeString(p, gson.toJson(cfg));
            return cfg;
        }
        return gson.fromJson(Files.readString(p), JsonObject.class);
    }

    private static final class RunStart implements SimpleCommand {
        private final InstanceManager instances;
        private RunStart(InstanceManager instances) { this.instances = instances; }

        @Override
        public void execute(Invocation inv) {
            if (!(inv.source() instanceof Player player)) {
                inv.source().sendMessage(Component.text("Players only."));
                return;
            }

            String runId = UUID.randomUUID().toString();
            instances.markRunStarted(runId, player.getUniqueId());
            player.sendMessage(Component.text("Spawning run " + runId + "..."));

            instances.spawnRegister(runId)
                    .thenCompose(server -> player.createConnectionRequest(server).connect())
                    .whenComplete((res, err) -> {
                        if (err != null) {
                            player.sendMessage(Component.text("Failed: " + err.getMessage()));
                            instances.cleanup(runId);
                        } else {
                            player.sendMessage(Component.text("Connected: " + runId));
                            player.sendMessage(Component.text("Use /runend " + runId + " to cleanup."));
                        }
                    });
        }
    }

    private static final class RunEnd implements SimpleCommand {
        private final InstanceManager instances;
        private RunEnd(InstanceManager instances) { this.instances = instances; }

        @Override
        public void execute(Invocation inv) {
            // If player runs /runend with no args, end THEIR active run
            if (inv.arguments().length < 1) {
                if (inv.source() instanceof Player p) {
                    instances.getRunFor(p.getUniqueId()).ifPresentOrElse(runId -> {
                        instances.cleanup(runId);
                        p.sendMessage(Component.text("Cleanup requested for " + runId));
                    }, () -> p.sendMessage(Component.text("No active run found for you.")));
                } else {
                    inv.source().sendMessage(Component.text("Usage: /runend <runId>"));
                }
                return;
            }

            String runId = inv.arguments()[0];
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
}