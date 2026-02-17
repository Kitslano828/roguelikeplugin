package TomDang.example.velocityOrchestrator;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

public final class InstanceManager {
    private final ProxyServer proxy;
    private final OrchestratorClient orch;

    private final long pollIntervalMs;
    private final Duration spawnTimeout;
    private final String serverNamePrefix;

    // runId -> instance
    private final Map<String, Live> live = new ConcurrentHashMap<>();

    public InstanceManager(
            ProxyServer proxy,
            OrchestratorClient orch,
            long pollIntervalMs,
            Duration spawnTimeout,
            String serverNamePrefix
    ) {
        this.proxy = proxy;
        this.orch = orch;
        this.pollIntervalMs = pollIntervalMs;
        this.spawnTimeout = spawnTimeout;
        this.serverNamePrefix = serverNamePrefix;
    }

    public CompletableFuture<RegisteredServer> spawnRegister(String runId) {
        Live existing = live.get(runId);
        if (existing != null) return CompletableFuture.completedFuture(existing.server);

        return CompletableFuture.supplyAsync(() -> {
            try {
                OrchestratorClient.InstanceInfo created = orch.spawn(runId);
                OrchestratorClient.InstanceInfo ready = waitReady(created.instanceId());

                String serverName = serverNamePrefix + runId;
                ServerInfo info = new ServerInfo(
                        serverName,
                        new InetSocketAddress(ready.host(), ready.port())
                );

                RegisteredServer reg = proxy.registerServer(info);
                live.put(runId, new Live(runId, created.instanceId(), info, reg));
                return reg;

            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    private OrchestratorClient.InstanceInfo waitReady(String instanceId) throws Exception {
        Instant deadline = Instant.now().plus(spawnTimeout);

        while (Instant.now().isBefore(deadline)) {
            OrchestratorClient.InstanceInfo cur = orch.get(instanceId);
            if ("READY".equalsIgnoreCase(cur.status())) return cur;
            if ("FAILED".equalsIgnoreCase(cur.status())) {
                throw new IllegalStateException("Instance failed to start: " + instanceId);
            }
            Thread.sleep(pollIntervalMs);
        }
        throw new TimeoutException("Instance not READY within timeout: " + instanceId);
    }

    public Optional<RegisteredServer> getServer(String runId) {
        Live l = live.get(runId);
        return l == null ? Optional.empty() : Optional.of(l.server);
    }

    public void cleanup(String runId) {
        Live l = live.remove(runId);
        if (l == null) return;

        proxy.unregisterServer(l.info);

        // Best-effort delete
        CompletableFuture.runAsync(() -> {
            try { orch.delete(l.instanceId); }
            catch (Exception ignored) {}
        });
    }

    private record Live(String runId, String instanceId, ServerInfo info, RegisteredServer server) {}
}