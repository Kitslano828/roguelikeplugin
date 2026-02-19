package TomDang.example.roguePlugin.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.*;

public final class PlayerLeaseService {

    private final Path leaseFile;
    private final String serverId;
    private final long leaseMillis;

    public PlayerLeaseService(Path leaseFile, String serverId, long leaseMillis) {
        this.leaseFile = leaseFile;
        this.serverId = serverId;
        this.leaseMillis = leaseMillis;
    }

    public void ensureExists() {
        try {
            Files.createDirectories(leaseFile.getParent());
            if (!Files.exists(leaseFile)) {
                writeProps(new Properties());
            }
        } catch (Exception ignored) {}
    }

    /** Acquire or renew lease. Returns true if this server owns the lease after call. */
    public synchronized boolean acquire(UUID playerId) {
        try {
            Properties props = readProps();
            long now = System.currentTimeMillis();

            String key = playerId.toString();
            String val = props.getProperty(key);

            Lease current = Lease.parse(val);

            // If no lease or expired -> take it
            if (current == null || current.expiresAt <= now) {
                props.setProperty(key, new Lease(serverId, now + leaseMillis).encode());
                writeProps(props);
                return true;
            }

            // If already ours -> renew
            if (serverId.equals(current.ownerServerId)) {
                props.setProperty(key, new Lease(serverId, now + leaseMillis).encode());
                writeProps(props);
                return true;
            }

            // Someone else owns it and it is not expired
            return false;

        } catch (Exception e) {
            // Fail CLOSED (safer) so we don't corrupt shared profile state
            return false;
        }
    }

    /** Release lease if owned by this server. */
    public synchronized void release(UUID playerId) {
        try {
            Properties props = readProps();
            String key = playerId.toString();
            Lease cur = Lease.parse(props.getProperty(key));
            if (cur != null && serverId.equals(cur.ownerServerId)) {
                props.remove(key);
                writeProps(props);
            }
        } catch (Exception ignored) {}
    }

    private Properties readProps() throws IOException {
        ensureExists();
        Properties p = new Properties();
        String content = Files.readString(leaseFile, StandardCharsets.UTF_8);
        // Properties.load requires InputStream; simplest is temp file string -> bytes
        try (var in = new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            p.load(in);
        }
        return p;
    }

    private void writeProps(Properties p) throws IOException {
        Files.createDirectories(leaseFile.getParent());

        Path tmp = leaseFile.resolveSibling(leaseFile.getFileName().toString() + ".tmp");
        StringBuilder sb = new StringBuilder();
        sb.append("# playerUUID=ownerServerId|expiresEpochMillis  (generated ").append(Instant.now()).append(")\n");
        for (String name : p.stringPropertyNames()) {
            sb.append(name).append("=").append(p.getProperty(name)).append("\n");
        }

        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Files.move(tmp, leaseFile, REPLACE_EXISTING, ATOMIC_MOVE);
    }

    private record Lease(String ownerServerId, long expiresAt) {
        String encode() { return ownerServerId + "|" + expiresAt; }

        static Lease parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String[] parts = raw.split("\\|");
            if (parts.length != 2) return null;
            try {
                return new Lease(parts[0], Long.parseLong(parts[1]));
            } catch (Exception e) {
                return null;
            }
        }
    }
}
