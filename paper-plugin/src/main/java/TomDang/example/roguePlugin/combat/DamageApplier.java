package TomDang.example.roguePlugin.combat;

import org.bukkit.entity.LivingEntity;

public final class DamageApplier {

    private DamageApplier() {}

    /**
     * Applies damage by consuming absorption first, then health.
     * Returns the amount that actually reduced health (not absorption).
     */
    public static double apply(LivingEntity victim, double rawDamage) {
        if (rawDamage <= 0) return 0;

        double damage = rawDamage;

        // 1) Absorption (shield)
        double absorption = Math.max(0.0, victim.getAbsorptionAmount());
        double absorbed = Math.min(absorption, damage);
        if (absorbed > 0) {
            victim.setAbsorptionAmount(absorption - absorbed);
            damage -= absorbed;
        }

        // 2) Health
        if (damage <= 0) return 0;

        double health = victim.getHealth();
        double newHealth = Math.max(0.0, health - damage);
        victim.setHealth(newHealth);

        return (health - newHealth);
    }
}
