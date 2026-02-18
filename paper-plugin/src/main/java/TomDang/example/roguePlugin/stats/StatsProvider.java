package TomDang.example.roguePlugin.stats;

import org.bukkit.entity.LivingEntity;

public interface StatsProvider {
    /** Contribute stats to this entity. Use add() for bonuses. */
    void contribute(LivingEntity entity, StatLine out, boolean dungeonMode);
}
