package terraria.event.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import terraria.util.WorldHelper;

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
        summonItems.put("天界符", BossHelper.BossType.MOON_LORD);
        // pre-providence
        summonItems.put("奇异信息素", BossHelper.BossType.THE_DRAGONFOLLY);
        summonItems.put("亵渎碎片", BossHelper.BossType.PROFANED_GUARDIANS);
        summonItems.put("亵渎晶核", BossHelper.BossType.PROVIDENCE_THE_PROFANED_GODDESS);
        summonItems.put("宇宙符文", null); // Handled as a special case
        summonItems.put("死灵魂炬", BossHelper.BossType.POLTERGHAST);
        summonItems.put("宇宙之虫", BossHelper.BossType.THE_DEVOURER_OF_GODS);
        summonItems.put("圣佑烬昭龙蛋", BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH);
        summonItems.put("金源量子冷却电池", BossHelper.BossType.EXO_MECHS);
        summonItems.put("祭魂瓮", BossHelper.BossType.SUPREME_WITCH_CALAMITAS);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent evt) {
        if (evt.getClickedBlock() != null && evt.getClickedBlock().getType().isSolid()) {
            if (spawnBossAtBlock(evt.getPlayer(), evt.getClickedBlock()))
                return;
        }
        spawnBoss(evt.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickEntity(PlayerInteractEntityEvent evt) {
        if (evt.isCancelled()) return;
        spawnBoss(evt.getPlayer());
    }
    private static void removeSummoningItem(Player ply, ItemStack tool) {
        tool.setAmount(tool.getAmount() - 1);
        ply.getInventory().setItemInMainHand(tool);
    }
    private static void spawnBoss(Player ply) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return;
        ItemStack tool = ply.getInventory().getItemInMainHand();
        String toolType = ItemHelper.splitItemName(tool)[1];
        // handle boss spawning
        BossHelper.BossType bossType = null;
        if (toolType.equals("宇宙符文")) {
            switch (WorldHelper.BiomeType.getBiome(ply)) {
                case SPACE:
                    bossType = BossHelper.BossType.STORM_WEAVER;
                    break;
                case DUNGEON:
                    bossType = BossHelper.BossType.CEASELESS_VOID;
                    break;
                case UNDERWORLD:
                    bossType = BossHelper.BossType.SIGNUS_ENVOY_OF_THE_DEVOURER;
                    break;
            }
        }
        else
            bossType = summonItems.get(toolType);
        if (bossType != null) {
            if ( BossHelper.spawnBoss(ply, bossType) ) {
                removeSummoningItem(ply, tool);
            }
        }
    }
    private static boolean spawnBossAtBlock(Player ply, Block block) {
        if (!PlayerHelper.isProperlyPlaying(ply))
            return false;
        ItemStack tool = ply.getInventory().getItemInMainHand();
        String toolType = ItemHelper.splitItemName(tool)[1];
        // handle boss spawning
        boolean shouldConsume = false, summoned = false;
        Location locAboveBlock = block.getLocation().add(0.5, 1, 0.5);
        switch (toolType) {
            case "泰坦之心":
                shouldConsume = true;
            case "星核": {
                summoned = block.getType() == Material.ENDER_PORTAL_FRAME
                        && BossHelper.spawnBoss(ply, BossHelper.BossType.ASTRUM_DEUS, locAboveBlock);
                break;
            }
        }
        if (summoned && shouldConsume) {
            removeSummoningItem(ply, tool);
        }
        return summoned;
    }
}
