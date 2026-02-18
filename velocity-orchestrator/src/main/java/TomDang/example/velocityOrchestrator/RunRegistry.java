package TomDang.example.velocityOrchestrator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class RunRegistry {
    private final Path path;

    public RunRegistry(Path path) {
        this.path = path;
    }

    public synchronized Properties load() throws IOException {
        Properties p = new Properties();
        if (!Files.exists(path)) return p;

        try (InputStream in = Files.newInputStream(path)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    public synchronized void save(Properties p) throws IOException {
        Files.createDirectories(path.getParent());

        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "playerUUID=runId");
        }

        // Atomic replace (Windows supported)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public synchronized void bindPlayer(UUID player, String runId) throws IOException {
        Properties p = load();
        p.setProperty(player.toString(), runId);
        save(p);
    }

    public synchronized void unbindPlayer(UUID player) throws IOException {
        Properties p = load();
        p.remove(player.toString());
        save(p);
    }

    public synchronized Optional<String> getRunForPlayer(UUID player) throws IOException {
        Properties p = load();
        return Optional.ofNullable(p.getProperty(player.toString()));
    }

    public synchronized void bindPlayers(Collection<UUID> players, String runId) throws IOException {
        Properties p = load();
        for (UUID u : players) p.setProperty(u.toString(), runId);
        save(p);
    }

    public synchronized void unbindPlayers(Collection<UUID> players) throws IOException {
        Properties p = load();
        for (UUID u : players) p.remove(u.toString());
        save(p);
    }
}
