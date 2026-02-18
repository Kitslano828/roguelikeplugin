package TomDang.example.roguePlugin.stats;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class StatsService {
    private final ProfileStore store;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(2);
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    private volatile boolean dungeonMode;

    public StatsService(Path playerDataDir, boolean dungeonMode) {
        this.store = new ProfileStore(playerDataDir);
        this.dungeonMode = dungeonMode;
    }

    public void setDungeonMode(boolean dungeonMode) {
        this.dungeonMode = dungeonMode;
    }

    public CompletableFuture<PlayerProfile> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerProfile p = store.loadOrCreate(uuid);
                cache.put(uuid, p);
                return p;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, ioPool);
    }

    public void apply(Player player) {
        PlayerProfile prof = cache.get(player.getUniqueId());
        if (prof == null) return;

        double oldMax = player.getMaxHealth();
        double oldHealth = player.getHealth();

        double newMax = prof.effective(StatId.MAX_HEALTH, dungeonMode);
        double shield = prof.effective(StatId.SHIELD, dungeonMode);

        // Set max
        player.setMaxHealth(newMax);

        // Keep same % health when max changes
        if (oldMax > 0) {
            double pct = oldHealth / oldMax;
            double newHealth = Math.max(1.0, Math.min(newMax, pct * newMax));
            player.setHealth(newHealth);
        } else {
            player.setHealth(Math.min(oldHealth, newMax));
        }

        player.setAbsorptionAmount(shield);

        double maxMana = prof.effective(StatId.MANA, dungeonMode);
        if (prof.currentMana > maxMana) prof.currentMana = maxMana;
    }


    public PlayerProfile get(UUID uuid) {
        return cache.get(uuid);
    }

    public void saveAsync(UUID uuid) {
        PlayerProfile p = cache.get(uuid);
        if (p == null) return;

        CompletableFuture.runAsync(() -> {
            try { store.save(uuid, p); }
            catch (Exception ignored) {}
        }, ioPool);
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    public void shutdown() {
        ioPool.shutdownNow();
    }
}
