package TomDang.example.velocityOrchestrator.party;

import java.time.Instant;
import java.util.UUID;

public record PartyInvite(UUID partyId, UUID inviter, Instant expiresAt) {
    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}
