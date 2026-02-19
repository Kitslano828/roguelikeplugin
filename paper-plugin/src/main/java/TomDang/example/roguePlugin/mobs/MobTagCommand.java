package TomDang.example.roguePlugin.mobs;

import TomDang.example.roguePlugin.stats.StatsService;
import org.bukkit.command.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobTagCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final StatsService stats;

    public MobTagCommand(JavaPlugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 1) {
            p.sendMessage("§cUsage: /mobtag <template> [level]");
            return true;
        }
        String template = args[0];
        int level = 1;
        if (args.length >= 2) {
            try { level = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        LivingEntity target = (LivingEntity) p.getTargetEntity(6);
        if (target == null) {
            p.sendMessage("§cLook at a mob within 6 blocks.");
            return true;
        }

        MobTags.set(target, template, level);
        stats.apply(target);
        MobHealthDisplayListener.updateName(target);

        p.sendMessage("§aTagged mob: " + template + " level " + level);
        return true;
    }
}
