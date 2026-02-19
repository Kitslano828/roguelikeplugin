package TomDang.example.roguePlugin.mobs;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobStickCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    public static NamespacedKey KEY_INSPECTOR;

    public MobStickCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        KEY_INSPECTOR = new NamespacedKey(plugin, "mob_inspector");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.setDisplayName("§aMob Inspector");
        meta.getPersistentDataContainer().set(KEY_INSPECTOR, PersistentDataType.BYTE, (byte) 1);
        stick.setItemMeta(meta);

        p.getInventory().addItem(stick);
        p.sendMessage("§aGiven Mob Inspector stick.");
        return true;
    }
}
