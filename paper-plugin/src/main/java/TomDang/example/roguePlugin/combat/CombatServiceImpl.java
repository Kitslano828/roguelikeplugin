package TomDang.example.roguePlugin.combat;

import TomDang.example.roguePlugin.stats.StatId;
import TomDang.example.roguePlugin.stats.StatsService;
import TomDang.example.roguePlugin.stats.StatsSnapshot;
import org.bukkit.entity.LivingEntity;

public final class CombatServiceImpl {

    private final StatsService stats;

    public CombatServiceImpl(StatsService stats) {
        this.stats = stats;
    }

    /**
     * Minimal v0: damage based on attacker STRENGTH only.
     */
    public void meleeHit(LivingEntity attacker, LivingEntity victim) {
        StatsSnapshot a = stats.snapshot(attacker);

        // v0 formula (tune later)
        double strength = a.get(StatId.STRENGTH);
        double damage = Math.max(0.0, strength * 0.50); // 10 STR -> 5 dmg (2.5 hearts)

        DamageApplier.apply(victim, damage);
    }
}
