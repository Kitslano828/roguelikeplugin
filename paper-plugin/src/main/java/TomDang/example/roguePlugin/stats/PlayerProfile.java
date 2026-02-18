package TomDang.example.roguePlugin.stats;

import java.util.EnumMap;

public final class PlayerProfile {
    public int level = 1;
    public long xp = 0;

    // Store all stats here (easy to add new stats)
    public EnumMap<StatId, Double> stats = new EnumMap<>(StatId.class);

    // Runtime-only (not necessarily persisted, but we will persist current mana too)
    public double currentMana = 100.0;

    public PlayerProfile() {
        ensureDefaults();
    }

    public void ensureDefaults() {
        for (StatId id : StatId.values()) {
            stats.putIfAbsent(id, id.defaultValue);
        }
        // Make sure currentMana is reasonable
        double maxMana = get(StatId.MANA);
        if (currentMana <= 0) currentMana = maxMana;
        if (currentMana > maxMana) currentMana = maxMana;
    }

    public double get(StatId id) {
        return stats.getOrDefault(id, id.defaultValue);
    }

    public void set(StatId id, double value) {
        stats.put(id, value);
        if (id == StatId.MANA) {
            if (currentMana > value) currentMana = value;
        }
    }

    public double dungeonMultiplier() {
        // Tune later. Simple, predictable curve:
        return 1.0 + (level * 0.03); // +3% per level inside dungeons
    }

    public double effective(StatId id, boolean dungeonMode) {
        double base = get(id);
        if (!dungeonMode) return base;
        if (!id.dungeonScaled) return base;
        return base * dungeonMultiplier();
    }
}
