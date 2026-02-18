package TomDang.example.roguePlugin.stats;

public enum StatId {
    // Core survivability
    MAX_HEALTH(true, 20.0),   // 20 = 10 hearts
    SHIELD(true, 0.0),        // absorption hearts

    // Damage
    STRENGTH(true, 10.0),     // used later in damage formula
    MAGIC(true, 1.00),        // ability damage multiplier (1.00 = 100%)

    // Resources
    MANA(true, 100.0),        // max mana
    MANA_REGEN(true, 5.0);    // mana per second (or per tick later)

    public final boolean dungeonScaled;
    public final double defaultValue;

    StatId(boolean dungeonScaled, double defaultValue) {
        this.dungeonScaled = dungeonScaled;
        this.defaultValue = defaultValue;
    }
}
