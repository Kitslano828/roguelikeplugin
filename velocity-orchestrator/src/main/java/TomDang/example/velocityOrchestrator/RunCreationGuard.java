package TomDang.example.velocityOrchestrator;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents spam / race conditions when creating dungeon instances.
 *
 * Policy:
 * - A leader can have only one run in CREATING at a time.
 * - Optionally, a party can have only one run in CREATING at a time.
 */
public final class RunCreationGuard {

    private final ConcurrentHashMap<UUID, Long> leaderCreatingSince = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> partyCreatingSince = new ConcurrentHashMap<>();

    /**
     * Tries to begin a run creation for this leader/party.
     * Returns true only for the first caller; all concurrent callers get false.
     */
    public boolean tryBegin(UUID leaderUuid, String partyIdNullable) {
        long now = System.currentTimeMillis();

        // Leader gate (atomic)
        if (leaderCreatingSince.putIfAbsent(leaderUuid, now) != null) {
            return false;
        }

        // Party gate (atomic) + rollback leader if party is locked
        if (partyIdNullable != null) {
            if (partyCreatingSince.putIfAbsent(partyIdNullable, now) != null) {
                leaderCreatingSince.remove(leaderUuid);
                return false;
            }
        }

        return true;
    }

    /** Call when creation succeeded and you no longer want the "creating" lock. */
    public void endSuccess(UUID leaderUuid, String partyIdNullable) {
        leaderCreatingSince.remove(leaderUuid);
        if (partyIdNullable != null) partyCreatingSince.remove(partyIdNullable);
    }

    /** Call when creation failed/aborted and you want to allow retry. */
    public void endFailure(UUID leaderUuid, String partyIdNullable) {
        leaderCreatingSince.remove(leaderUuid);
        if (partyIdNullable != null) partyCreatingSince.remove(partyIdNullable);
    }

    /** True if leader currently has an in-flight creation. */
    public boolean isLeaderCreating(UUID leaderUuid) {
        return leaderCreatingSince.containsKey(leaderUuid);
    }

    /** Cleanup any stale locks (e.g. instance never became ready). */
    public void cleanupStale(long timeoutMillis) {
        long now = System.currentTimeMillis();

        leaderCreatingSince.entrySet().removeIf(e -> (now - e.getValue()) > timeoutMillis);
        partyCreatingSince.entrySet().removeIf(e -> (now - e.getValue()) > timeoutMillis);
    }
}
