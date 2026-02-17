package TomDang.example.roguePlugin;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SharedRunRegistry {
    private final JavaPlugin plugin;
    private final Path file;
    private final Object ioLock = new Object();

    public SharedRunRegistry(JavaPlugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void ensureExists() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                saveProps(new Properties());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to ensure registry exists: " + e.getMessage());
        }
    }

    /** Returns runId if present for playerUUID, else null. */
    public String getRunId(UUID playerId) {
        Properties p = loadProps();
        return p.getProperty(playerId.toString());
    }

    /** Map playerUUID -> runId */
    public void put(UUID playerId, UUID runId) {
        synchronized (ioLock) {
            Properties p = loadProps();
            p.setProperty(playerId.toString(), runId.toString());
            saveProps(p);
        }
    }

    public void remove(UUID playerId) {
        synchronized (ioLock) {
            Properties p = loadProps();
            p.remove(playerId.toString());
            saveProps(p);
        }
    }

    /** For /run debug: counts active entries */
    public int size() {
        Properties p = loadProps();
        return p.size();
    }

    private Properties loadProps() {
        synchronized (ioLock) {
            Properties p = new Properties();
            if (!Files.exists(file)) return p;

            try (InputStream in = Files.newInputStream(file)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to read registry: " + e.getMessage());
            }
            return p;
        }
    }

    private void saveProps(Properties p) {
        synchronized (ioLock) {
            try {
                Files.createDirectories(file.getParent());

                // Atomic write: write temp then move
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    p.store(new OutputStreamWriter(out, StandardCharsets.UTF_8), "playerUUID=runUUID");
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                plugin.getLogger().info("[Registry] wrote file: " + file.toAbsolutePath() + " entries=" + p.size());
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write registry: " + e.getMessage());
            }
        }
    }
}