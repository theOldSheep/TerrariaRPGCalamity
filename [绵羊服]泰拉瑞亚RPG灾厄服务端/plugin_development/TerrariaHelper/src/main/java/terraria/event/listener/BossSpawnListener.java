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
import terraria.util.PlayerHelper;

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
        summonItems.put("极寒之匙", BossHelper.BossType.CRYOGEN);
        summonItems.put("机械魔眼", BossHelper.BossType.THE_TWINS);
        summonItems.put("海鲜饵料", BossHelper.BossType.AQUATIC_SCOURGE);
        summonItems.put("机械蠕虫", BossHelper.BossType.THE_DESTROYER);
        summonItems.put("焦炭玩偶", BossHelper.BossType.BRIMSTONE_ELEMENTAL);
        summonItems.put("机械骷髅头", BossHelper.BossType.SKELETRON_PRIME);
        summonItems.put("荒芜之眼", BossHelper.BossType.CALAMITAS_CLONE);
        summonItems.put("世纪之花花苞", BossHelper.BossType.PLANTERA);
        // pre-moon lord
        summonItems.put("星辉碎块", BossHelper.BossType.ASTRUM_AUREUS);
        summonItems.put("丛林蜥蜴电池", BossHelper.BossType.GOLEM);
        summonItems.put("瘟疫起动装置", BossHelper.BossType.THE_PLAGUEBRINGER_GOLIATH);
        summonItems.put("唤死笛哨", BossHelper.BossType.RAVAGER);
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
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
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
