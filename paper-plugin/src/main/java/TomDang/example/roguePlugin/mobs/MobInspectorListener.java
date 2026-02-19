package TomDang.example.roguePlugin.mobs;

import TomDang.example.roguePlugin.stats.StatId;
import TomDang.example.roguePlugin.stats.StatsService;
import TomDang.example.roguePlugin.stats.StatsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MobInspectorListener implements Listener {
    private final StatsService stats;

    public MobInspectorListener(StatsService stats) {
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInspect(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();

        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand.getType() != Material.STICK) return;
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null) return;
        Byte mark = meta.getPersistentDataContainer().get(MobStickCommand.KEY_INSPECTOR, PersistentDataType.BYTE);
        if (mark == null || mark != (byte)1) return;

        if (!(e.getRightClicked() instanceof LivingEntity mob)) return;
        if (!MobTags.isTagged(mob)) {
            p.sendMessage("§cThis mob has no template tag.");
            return;
        }

        e.setCancelled(true);
        openGui(p, mob);
    }

    private void openGui(Player viewer, LivingEntity mob) {
        StatsSnapshot s = stats.snapshot(mob);

        Inventory inv = Bukkit.createInventory(null, 27, "Mob Stats");

        inv.setItem(10, item(Material.RED_DYE, "Health", String.valueOf((int)Math.ceil(s.get(StatId.MAX_HEALTH)))));
        inv.setItem(11, item(Material.SHIELD, "Shield", String.valueOf((int)Math.ceil(s.get(StatId.SHIELD)))));
        inv.setItem(12, item(Material.IRON_SWORD, "Strength", String.valueOf((int)Math.ceil(s.get(StatId.STRENGTH)))));
        inv.setItem(13, item(Material.BLAZE_POWDER, "Magic", String.valueOf((int)Math.ceil(s.get(StatId.MAGIC)))));
        inv.setItem(14, item(Material.LAPIS_LAZULI, "Mana", String.valueOf((int)Math.ceil(s.get(StatId.MANA)))));
        inv.setItem(15, item(Material.PRISMARINE_CRYSTALS, "Mana Regen", String.valueOf(s.get(StatId.MANA_REGEN))));

        String template = MobTags.getTemplate(mob);
        int level = MobTags.getLevel(mob);
        inv.setItem(16, item(Material.NAME_TAG, "Info", "Template: " + template, "Level: " + level));

        viewer.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§e" + name);
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String s : lore) l.add("§7" + s);
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }
}
