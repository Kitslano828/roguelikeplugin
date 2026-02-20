package TomDang.example.roguePlugin.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements Listener {

    private final CombatServiceImpl combat;

    public CombatListener(CombatServiceImpl combat) {
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        LivingEntity attacker = resolveAttacker(e);
        if (attacker == null) return;

        // Cancel vanilla damage, apply custom damage
        e.setCancelled(true);
        combat.meleeHit(attacker, victim);
    }

    private LivingEntity resolveAttacker(EntityDamageByEntityEvent e) {
        // Direct attacker (player/mob)
        if (e.getDamager() instanceof LivingEntity le) {
            return le;
        }

        // Projectile shooter (arrow/trident/etc.)
        if (e.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof LivingEntity le) {
                return le;
            }
        }

        return null;
    }
}
