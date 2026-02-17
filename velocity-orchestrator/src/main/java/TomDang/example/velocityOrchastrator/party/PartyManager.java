package TomDang.example.velocityOrchestrator.party;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyManager {
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyByMember = new ConcurrentHashMap<>(); // player -> partyId
    private final Map<UUID, PartyInvite> invitesByPlayer = new ConcurrentHashMap<>(); // invitee -> invite
    private final Duration inviteTtl;

    public PartyManager(Duration inviteTtl) {
        this.inviteTtl = inviteTtl;
    }

    public Optional<Party> getParty(UUID partyId) {
        return Optional.ofNullable(parties.get(partyId));
    }

    public Optional<Party> getPartyOf(UUID player) {
        UUID partyId = partyByMember.get(player);
        if (partyId == null) return Optional.empty();
        return Optional.ofNullable(parties.get(partyId));
    }

    public Party createParty(UUID leader) {
        // If already in a party, return existing (don’t auto-disband)
        Optional<Party> existing = getPartyOf(leader);
        if (existing.isPresent()) return existing.get();

        UUID partyId = UUID.randomUUID();
        Party p = new Party(partyId, leader);
        parties.put(partyId, p);
        partyByMember.put(leader, partyId);
        return p;
    }

    public void disband(UUID partyId) {
        Party p = parties.remove(partyId);
        if (p == null) return;
        for (UUID m : p.membersView()) {
            partyByMember.remove(m);
        }
        // Clear pending invites to this party
        invitesByPlayer.entrySet().removeIf(e -> e.getValue().partyId().equals(partyId));
    }

    public boolean leave(UUID player) {
        UUID partyId = partyByMember.get(player);
        if (partyId == null) return false;

        Party p = parties.get(partyId);
        if (p == null) {
            partyByMember.remove(player);
            return false;
        }

        p.remove(player);
        partyByMember.remove(player);

        // If leader left, assign new leader or disband
        if (p.leader().equals(player)) {
            UUID newLeader = p.membersView().stream().findFirst().orElse(null);
            if (newLeader == null) {
                parties.remove(partyId);
                return true;
            }
            p.setLeader(newLeader);
        }

        // If party becomes empty, remove it
        if (p.size() == 0) parties.remove(partyId);

        return true;
    }

    public Optional<PartyInvite> getInvite(UUID invitee) {
        PartyInvite inv = invitesByPlayer.get(invitee);
        if (inv == null) return Optional.empty();
        if (inv.expired()) {
            invitesByPlayer.remove(invitee);
            return Optional.empty();
        }
        return Optional.of(inv);
    }

    public boolean invite(UUID inviter, UUID invitee) {
        Party party = createParty(inviter); // if inviter not in party, create one
        if (!party.isLeader(inviter)) return false;

        // don’t invite someone already in party
        if (party.isMember(invitee)) return true;

        invitesByPlayer.put(invitee, new PartyInvite(
                party.partyId(),
                inviter,
                Instant.now().plus(inviteTtl)
        ));
        return true;
    }

    public enum AcceptResult { NO_INVITE, EXPIRED, PARTY_MISSING, JOINED }

    public AcceptResult accept(UUID invitee) {
        PartyInvite inv = invitesByPlayer.get(invitee);
        if (inv == null) return AcceptResult.NO_INVITE;
        if (inv.expired()) {
            invitesByPlayer.remove(invitee);
            return AcceptResult.EXPIRED;
        }

        Party party = parties.get(inv.partyId());
        if (party == null) {
            invitesByPlayer.remove(invitee);
            return AcceptResult.PARTY_MISSING;
        }

        // If invitee already in some party, leave it first (simple rule)
        getPartyOf(invitee).ifPresent(p -> leave(invitee));

        party.add(invitee);
        partyByMember.put(invitee, party.partyId());
        invitesByPlayer.remove(invitee);
        return AcceptResult.JOINED;
    }

    public boolean kick(UUID leader, UUID target) {
        Optional<Party> pOpt = getPartyOf(leader);
        if (pOpt.isEmpty()) return false;

        Party p = pOpt.get();
        if (!p.isLeader(leader)) return false;
        if (!p.isMember(target)) return false;
        if (p.leader().equals(target)) return false; // don’t kick leader

        p.remove(target);
        partyByMember.remove(target);

        if (p.size() == 0) parties.remove(p.partyId());
        return true;
    }
}
