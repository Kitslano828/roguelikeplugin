package TomDang.example.roguePlugin.mobs;

import TomDang.example.roguePlugin.stats.StatId;
import TomDang.example.roguePlugin.stats.StatLine;
import TomDang.example.roguePlugin.stats.StatsProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MobTemplateStatsProvider implements StatsProvider {
    private final YamlConfiguration mobsCfg;

    public MobTemplateStatsProvider(JavaPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "mobs.yml");
        this.mobsCfg = YamlConfiguration.loadConfiguration(f);
    }

    @Override
    public void contribute(LivingEntity entity, StatLine out, boolean dungeonMode) {
        if (!MobTags.isTagged(entity)) return;

        String template = MobTags.getTemplate(entity);
        int level = MobTags.getLevel(entity);

        ConfigurationSection sec = mobsCfg.getConfigurationSection("mobs." + template);
        if (sec == null) return;

        // Simple scaling rule
        double mult = 1.0 + 0.15 * Math.max(0, level - 1);

        addScaled(sec, out, StatId.MAX_HEALTH, mult);
        addScaled(sec, out, StatId.SHIELD, mult);
        addScaled(sec, out, StatId.STRENGTH, mult);
        addScaled(sec, out, StatId.MAGIC, mult);

        // Mana stats typically unscaled
        addFlat(sec, out, StatId.MANA);
        addFlat(sec, out, StatId.MANA_REGEN);
    }

    private void addScaled(ConfigurationSection sec, StatLine out, StatId id, double mult) {
        if (!sec.contains(id.name())) return;
        out.add(id, sec.getDouble(id.name()) * mult);
    }

    private void addFlat(ConfigurationSection sec, StatLine out, StatId id) {
        if (!sec.contains(id.name())) return;
        out.add(id, sec.getDouble(id.name()));
    }
}
