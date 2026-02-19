package TomDang.example.roguePlugin.mobs;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobTags {
    private static NamespacedKey KEY_TEMPLATE;
    private static NamespacedKey KEY_LEVEL;

    private MobTags() {}

    public static void init(JavaPlugin plugin) {
        KEY_TEMPLATE = new NamespacedKey(plugin, "mob_template");
        KEY_LEVEL = new NamespacedKey(plugin, "mob_level");
    }

    public static void set(LivingEntity e, String templateId, int level) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(KEY_TEMPLATE, PersistentDataType.STRING, templateId);
        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, level);
    }

    public static String getTemplate(LivingEntity e) {
        return e.getPersistentDataContainer().get(KEY_TEMPLATE, PersistentDataType.STRING);
    }

    public static int getLevel(LivingEntity e) {
        Integer lvl = e.getPersistentDataContainer().get(KEY_LEVEL, PersistentDataType.INTEGER);
        return lvl == null ? 1 : lvl;
    }

    public static boolean isTagged(LivingEntity e) {
        return getTemplate(e) != null;
    }
}
