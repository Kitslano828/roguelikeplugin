package TomDang.example.roguePlugin.stats;

import java.util.EnumMap;

public final class StatLine {
    private final EnumMap<StatId, Double> map = new EnumMap<>(StatId.class);

    public StatLine() {
        for (StatId id : StatId.values()) map.put(id, 0.0);
    }

    public void add(StatId id, double amount) {
        map.put(id, map.get(id) + amount);
    }

    public void set(StatId id, double value) {
        map.put(id, value);
    }

    public double get(StatId id) {
        return map.getOrDefault(id, 0.0);
    }
}
