package TomDang.example.velocityOrchestrator;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class InstanceManager {
    private final ProxyServer proxy;
    private final OrchestratorClient orch;

    private final long pollIntervalMs;
    private final Duration spawnTimeout;
    private final String serverNamePrefix;

    // runId -> instance
    private final Map<String, Live> live = new ConcurrentHashMap<>();

    // Owner (leader/solo starter) for listing
    private final Map<String, UUID> ownerByRunId = new ConcurrentHashMap<>();
    private final Set<String> activeRunIds = ConcurrentHashMap.newKeySet();

    // NEW: bindings for gating + party runs
    private final Map<UUID, String> runByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> runByParty  = new ConcurrentHashMap<>();
    private final Map<String, UUID> partyByRunId = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> membersByRunId = new ConcurrentHashMap<>();
    private final Map<UUID, String> creatingRunByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, String> creatingRunByParty = new ConcurrentHashMap<>();

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

    // === Helpers for run server naming ===
    public boolean isRunServerName(String serverName) {
        return serverName != null && serverName.startsWith(serverNamePrefix);
    }

    public Optional<String> runIdFromServerName(String serverName) {
        if (!isRunServerName(serverName)) return Optional.empty();
        return Optional.of(serverName.substring(serverNamePrefix.length()));
    }

    // === Lookup ===
    public Optional<String> getRunForParty(UUID partyId) {
        return Optional.ofNullable(runByParty.get(partyId));
    }

    public Optional<String> getRunForPlayer(UUID player) {
        return Optional.ofNullable(runByPlayer.get(player));
    }

    public boolean isPlayerAllowedOnRun(UUID player, String runId) {
        return runId != null && runId.equals(runByPlayer.get(player));
    }

    // === Start/bind run ===
    // Keep old signature (solo/backwards compatibility)
    public void markRunStarted(String runId, UUID owner) {
        markRunStarted(runId, owner, null, List.of(owner));
    }

    // New signature used by party-aware /runstart
    public void markRunStarted(String runId, UUID owner, UUID partyIdOrNull, Collection<UUID> members) {
        ownerByRunId.put(runId, owner);
        activeRunIds.add(runId);

        if (partyIdOrNull != null) {
            runByParty.put(partyIdOrNull, runId);
            partyByRunId.put(runId, partyIdOrNull);
        }

        Set<UUID> set = ConcurrentHashMap.newKeySet();
        set.addAll(members);
        membersByRunId.put(runId, set);

        for (UUID m : members) {
            runByPlayer.put(m, runId);
        }
    }

    // === Spawn/register ===
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

    // === Cleanup ===
    public void cleanup(String runId) {
        clearCreateLockForRun(runId);

        Live l = live.remove(runId);

        // Unbind members even if server wasn't live
        Set<UUID> members = membersByRunId.remove(runId);
        if (members != null) {
            for (UUID m : members) runByPlayer.remove(m, runId);
        }

        // Unbind party
        UUID partyId = partyByRunId.remove(runId);
        if (partyId != null) runByParty.remove(partyId, runId);

        markRunEnded(runId);

        if (l == null) return;

        proxy.unregisterServer(l.info);

        CompletableFuture.runAsync(() -> {
            try { orch.delete(l.instanceId); }
            catch (Exception ignored) {}
        });
    }

    private record Live(String runId, String instanceId, ServerInfo info, RegisteredServer server) {}

    public void markRunEnded(String runId) {
        activeRunIds.remove(runId);
        ownerByRunId.remove(runId);
    }

    public List<String> getActiveRunIdsFor(UUID owner) {
        List<String> out = new ArrayList<>();
        for (String runId : activeRunIds) {
            if (owner.equals(ownerByRunId.get(runId))) out.add(runId);
        }
        return out;
    }

    // owner-based "their run" (leader-only start makes this fine)
    public Optional<String> getRunFor(UUID owner) {
        for (String runId : activeRunIds) {
            if (owner.equals(ownerByRunId.get(runId))) return Optional.of(runId);
        }
        return Optional.empty();
    }

    public Optional<Set<UUID>> getMembersForRun(String runId) {
        Set<UUID> s = membersByRunId.get(runId);
        return s == null ? Optional.empty() : Optional.of(Set.copyOf(s));
    }

    public Optional<String> getCreatingRunForOwner(UUID owner) {
        return Optional.ofNullable(creatingRunByOwner.get(owner));
    }

    public Optional<String> getCreatingRunForParty(UUID partyId) {
        return Optional.ofNullable(creatingRunByParty.get(partyId));
    }

    /**
     * Atomically reserves creation for this owner/party.
     * Returns false if owner/party already has a creating run or an active run.
     */
    public boolean tryBeginCreate(String runId, UUID owner, UUID partyIdOrNull) {
        // If already active, deny
        if (getRunFor(owner).isPresent()) return false;
        if (partyIdOrNull != null && runByParty.containsKey(partyIdOrNull)) return false;

        // Owner creating lock
        if (creatingRunByOwner.putIfAbsent(owner, runId) != null) return false;

        // Party creating lock + rollback owner lock if party locked
        if (partyIdOrNull != null) {
            if (creatingRunByParty.putIfAbsent(partyIdOrNull, runId) != null) {
                creatingRunByOwner.remove(owner, runId);
                return false;
            }
        }
        return true;
    }

    /** Clears creation locks (call on success OR failure). */
    public void clearCreateLock(String runId, UUID ownerOrNull, UUID partyIdOrNull) {
        if (ownerOrNull != null) creatingRunByOwner.remove(ownerOrNull, runId);
        if (partyIdOrNull != null) creatingRunByParty.remove(partyIdOrNull, runId);
    }

    /** Convenience: clear lock by runId using current bindings if present. */
    public void clearCreateLockForRun(String runId) {
        UUID owner = ownerByRunId.get(runId);
        UUID party = partyByRunId.get(runId);

        if (owner != null) creatingRunByOwner.remove(owner, runId);
        if (party != null) creatingRunByParty.remove(party, runId);
    }

}
