package TomDang.example.roguePlugin.stats;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;


import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public final class StatsService {
    private final ProfileStore store;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(2);
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final List<StatsProvider> providers = new CopyOnWriteArrayList<>();

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

    public void registerProvider(StatsProvider provider) {
        providers.add(provider);
    }

    public void unregisterProvider(StatsProvider provider) {
        providers.remove(provider);
    }

    public StatsSnapshot snapshot(LivingEntity entity) {
        double lvlMult = 1.0;

        StatLine line = new StatLine();

        // 1) Base stats
        if (entity instanceof Player p) {
            PlayerProfile prof = cache.get(p.getUniqueId());
            if (prof != null) {
                lvlMult = dungeonMode ? prof.dungeonMultiplier() : 1.0;

                // Use "effective" (your existing scaling)
                for (StatId id : StatId.values()) {
                    line.add(id, prof.effective(id, dungeonMode));
                }
            }
        }

        // 2) Providers (items, buffs, mob templates, etc.)
        for (StatsProvider sp : providers) {
            sp.contribute(entity, line, dungeonMode);
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

    // NEW: apply to ANY living entity (players + mobs)
    public void apply(LivingEntity entity) {
        StatsSnapshot s = snapshot(entity);

        double newMax = Math.max(1.0, s.maxHealth());
        entity.setMaxHealth(newMax);

        // keep % health stable for players only; mobs can just clamp
        if (entity instanceof Player p) {
            double oldMax = p.getMaxHealth();
            double oldHealth = p.getHealth();
            if (oldMax > 0) {
                double pct = oldHealth / oldMax;
                p.setHealth(Math.max(1.0, Math.min(newMax, pct * newMax)));
            } else {
                p.setHealth(Math.min(oldHealth, newMax));
            }
            p.setAbsorptionAmount(s.shield());

            // clamp mana in profile (players only)
            PlayerProfile prof = cache.get(p.getUniqueId());
            if (prof != null && prof.currentMana > s.mana()) prof.currentMana = s.mana();
        } else {
            // mobs: just clamp health and optionally use absorption as "shield"
            entity.setHealth(Math.min(entity.getHealth(), newMax));
            // If you want shield visible on mobs, you *can* use absorption too:
            // if (entity instanceof org.bukkit.entity.Damageable d) ...
        }
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
