package TomDang.example.roguePlugin.stats;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public final class StatsService {

    private final ProfileStore store;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(2);
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final List<StatsProvider> providers = new CopyOnWriteArrayList<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    private volatile boolean dungeonMode;

    public StatsService(Path playerDataDir, boolean dungeonMode) {
        this.store = new ProfileStore(playerDataDir);
        this.dungeonMode = dungeonMode;
    }

    /* =========================
       Player persistence
       ========================= */

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

    /* =========================
       Providers
       ========================= */

    public void registerProvider(StatsProvider provider) {
        providers.add(provider);
    }

    public void unregisterProvider(StatsProvider provider) {
        providers.remove(provider);
    }

    /* =========================
       Snapshot + Apply
       ========================= */

    public StatsSnapshot snapshot(LivingEntity entity) {
        StatLine line = new StatLine();

        double lvlMult = 1.0;

        // Base stats (players only)
        if (entity instanceof Player p) {
            PlayerProfile prof = cache.get(p.getUniqueId());
            if (prof != null) {
                lvlMult = dungeonMode ? prof.dungeonMultiplier() : 1.0;

                for (StatId id : StatId.values()) {
                    line.add(id, prof.effective(id, dungeonMode));
                }
            }
        }
        // Providers (mobs, items, buffs, etc.)
        for (StatsProvider provider : providers) {
            provider.contribute(entity, line, dungeonMode);
        }


        return new StatsSnapshot(
                line.get(StatId.MAX_HEALTH),
                line.get(StatId.SHIELD),
                line.get(StatId.STRENGTH),
                line.get(StatId.MAGIC),
                line.get(StatId.MANA),
                line.get(StatId.MANA_REGEN),
                lvlMult
        );
    }

    public void apply(LivingEntity entity) {
        StatsSnapshot s = snapshot(entity);

        double newMax = Math.max(1.0, s.maxHealth());
        double oldMax = entity.getMaxHealth();
        double oldHealth = entity.getHealth();

        entity.setMaxHealth(newMax);

        // Keep % HP consistent
        if (oldMax > 0) {
            double pct = oldHealth / oldMax;
            entity.setHealth(Math.max(1.0, Math.min(newMax, pct * newMax)));
        } else {
            entity.setHealth(Math.min(oldHealth, newMax));
        }

        // Shield → absorption (players + mobs)
        entity.setAbsorptionAmount(s.shield());

        // Clamp mana (players only)
        if (entity instanceof Player p) {
            PlayerProfile prof = cache.get(p.getUniqueId());
            if (prof != null && prof.currentMana > s.mana()) {
                prof.currentMana = s.mana();
            }
        }
    }

    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
    }

    public Set<UUID> drainDirtyPlayers(int max) {
        Set<UUID> out = new HashSet<>();
        Iterator<UUID> it = dirtyPlayers.iterator();
        while (it.hasNext() && out.size() < max) {
            UUID id = it.next();
            it.remove();
            out.add(id);
        }
        return out;
    }

}
