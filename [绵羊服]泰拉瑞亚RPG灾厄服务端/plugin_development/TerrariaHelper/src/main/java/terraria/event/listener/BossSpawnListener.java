package terraria.event.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import terraria.util.BossHelper;
import terraria.util.ItemHelper;

import java.util.HashMap;

public class BossSpawnListener implements Listener {
    static HashMap<String, BossHelper.BossType> summonItems = new HashMap<>();
    static {
        // pre-wall of flesh
        summonItems.put("史莱姆王冠", BossHelper.BossType.KING_SLIME);
        summonItems.put("荒漠吊坠", BossHelper.BossType.DESERT_SCOURGE);
        summonItems.put("可疑的眼球", BossHelper.BossType.EYE_OF_CTHULHU);
        summonItems.put("节肢动物芽孢", BossHelper.BossType.CRABULON);
        summonItems.put("虫饵", BossHelper.BossType.EATER_OF_WORLDS);
        summonItems.put("畸形肿瘤", BossHelper.BossType.THE_HIVE_MIND);
        summonItems.put("过载淤泥", BossHelper.BossType.THE_SLIME_GOD);
        // pre-plantera
        summonItems.put("明胶水晶", BossHelper.BossType.QUEEN_SLIME);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent evt) {
        if (evt.isCancelled() && evt.getClickedBlock() != null) return;
        spawnBoss(evt.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickEntity(PlayerInteractEntityEvent evt) {
        if (evt.isCancelled()) return;
        spawnBoss(evt.getPlayer());
    }
    private static void spawnBoss(Player ply) {
        ItemStack tool = ply.getInventory().getItemInMainHand();
        String toolType = ItemHelper.splitItemName(tool)[1];
        // handle boss spawning
        BossHelper.BossType bossType = summonItems.get(toolType);
        if (bossType != null) {
            if ( BossHelper.spawnBoss(ply, bossType) ) {
                tool.setAmount(tool.getAmount() - 1);
                ply.getInventory().setItemInMainHand(tool);
            }
        }
    }
}
