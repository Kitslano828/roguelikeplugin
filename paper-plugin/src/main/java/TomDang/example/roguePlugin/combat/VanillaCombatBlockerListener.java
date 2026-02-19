package TomDang.example.roguePlugin.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

public final class VanillaCombatBlockerListener implements Listener {

    /**
     * Cancel vanilla PvE/PvP hits (melee, projectiles, etc.)
     * We'll re-apply our own damage later in the pipeline.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        e.setCancelled(true);
    }

    /**
     * Cancel vanilla environmental damage too (fall, fire, lava, void, etc.)
     * If you want some causes to stay vanilla, we can whitelist later.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;

        // Optional: allow void to behave normally (many servers prefer custom anyway)
        // if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;

        e.setCancelled(true);
    }

    /**
     * Cancel vanilla regeneration (food regen, etc.) so it doesn't fight your stats system.
     * You can allow specific reasons later (e.g. custom healing items).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        e.setCancelled(true);
    }
}
