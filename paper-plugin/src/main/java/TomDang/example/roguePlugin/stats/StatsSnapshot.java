package TomDang.example.roguePlugin.stats;

public record StatsSnapshot(
        double maxHealth,
        double shield,
        double strength,
        double magic,
        double mana,
        double manaRegen,
        double levelMultiplier
) {
    public double get(StatId id) {
        return switch (id) {
            case MAX_HEALTH -> maxHealth;
            case SHIELD -> shield;
            case STRENGTH -> strength;
            case MAGIC -> magic;
            case MANA -> mana;
            case MANA_REGEN -> manaRegen;
        };
    }
}
