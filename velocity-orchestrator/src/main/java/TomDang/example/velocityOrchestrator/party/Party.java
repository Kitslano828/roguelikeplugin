package TomDang.example.velocityOrchestrator.party;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Party {
    private final UUID partyId;
    private volatile UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();

    public Party(UUID partyId, UUID leader) {
        this.partyId = partyId;
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID partyId() { return partyId; }
    public UUID leader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public Set<UUID> membersView() { return Collections.unmodifiableSet(members); }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }

    public void add(UUID uuid) { members.add(uuid); }
    public void remove(UUID uuid) { members.remove(uuid); }

    public int size() { return members.size(); }
}
