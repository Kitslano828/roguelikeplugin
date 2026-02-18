package TomDang.example.roguePlugin.stats;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class StatsCommand implements org.bukkit.command.CommandExecutor {
    private final StatsService stats;

    public StatsCommand(StatsService stats) {
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        PlayerProfile prof = stats.get(p.getUniqueId());
        if (prof == null) {
            p.sendMessage(Component.text("Profile not loaded yet."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("view")) {
            double mult = prof.dungeonMultiplier();
            p.sendMessage(Component.text("Level: " + prof.level + " (dungeon mult=" + String.format(Locale.ROOT,"%.2f", mult) + ")"));
            for (StatId id : StatId.values()) {
                double base = prof.get(id);
                double eff  = prof.effective(id, true);
                p.sendMessage(Component.text(id.name() + ": base=" + base + " effective(dungeon)=" + String.format(Locale.ROOT,"%.2f", eff)));
            }
            p.sendMessage(Component.text("Mana: " + String.format(Locale.ROOT,"%.1f", prof.currentMana) + "/" + prof.get(StatId.MANA)));
            return true;
        }

        // /stats level set 10
        if (args.length >= 3 && args[0].equalsIgnoreCase("level") && args[1].equalsIgnoreCase("set")) {
            int lvl = Integer.parseInt(args[2]);
            prof.level = Math.max(1, lvl);
            stats.apply(p);
            p.sendMessage(Component.text("Level set to " + prof.level));
            return true;
        }

        // /stats set MAX_HEALTH 40
        if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
            StatId id = StatId.valueOf(args[1].toUpperCase(Locale.ROOT));
            double v = Double.parseDouble(args[2]);
            prof.set(id, v);
            stats.apply(p);
            p.sendMessage(Component.text("Set " + id.name() + " = " + v));
            return true;
        }

        // /stats mana add 25
        if (args.length >= 3 && args[0].equalsIgnoreCase("mana") && args[1].equalsIgnoreCase("add")) {
            double add = Double.parseDouble(args[2]);
            double max = prof.get(StatId.MANA);
            prof.currentMana = Math.min(max, prof.currentMana + add);
            p.sendMessage(Component.text("Mana: " + prof.currentMana + "/" + max));
            return true;
        }

        p.sendMessage(Component.text("Usage: /stats view | /stats level set <n> | /stats set <STAT> <value> | /stats mana add <value>"));
        return true;
    }
}
