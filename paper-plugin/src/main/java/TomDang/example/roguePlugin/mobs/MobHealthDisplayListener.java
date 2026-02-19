package TomDang.example.roguePlugin.mobs;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobHealthDisplayListener implements Listener {

    private final JavaPlugin plugin;

    public MobHealthDisplayListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (!MobTags.isTagged(mob)) return;

        Bukkit.getScheduler().runTask(plugin, () -> updateName(mob));
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (!MobTags.isTagged(mob)) return;

        Bukkit.getScheduler().runTask(plugin, () -> updateName(mob));
    }

    public static void updateName(LivingEntity mob) {
        double hp = Math.max(0, mob.getHealth());
        double max = Math.max(1, mob.getMaxHealth());

        String name = (int) Math.ceil(hp) + "❤ / " + (int) Math.ceil(max) + "❤";
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }
}
