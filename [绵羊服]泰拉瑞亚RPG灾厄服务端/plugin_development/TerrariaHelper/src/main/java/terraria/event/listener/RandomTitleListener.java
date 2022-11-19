package terraria.event.listener;

import eos.moe.dragoncore.config.Config;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public class RandomTitleListener implements Listener {
    String[] titles;
    public RandomTitleListener() {
        titles = new String[]{
                "泰拉瑞亚：被沙子制服了",
                "泰拉瑞亚第3部分：向导归来",
                "泰拉瑞亚：试试《我的世界》！",
                "泰拉瑞亚：泰拉瑞亚：泰拉瑞亚：",
                "泰拉瑞亚：按alt-f4",
                "泰拉瑞亚：试试《旷野之息》！",
                "泰拉瑞亚：也试试饥荒吧！",
                "泰拉瑞亚：美味又易燃",
                "泰拉瑞亚：内含小块块，不适合5岁以下的儿童",
                "泰拉瑞亚：现已加入音效",
                "泰拉瑞亚：兄弟你疯了吧？",
                "泰拉瑞亚：重挖好吗？",
                "泰拉瑞亚：哎呦你干嘛~~",
                "泰拉瑞亚：强迫症现象模仿者",
                "泰拉瑞亚：老公挖啊挖！",
                "泰拉瑞亚：神土",
                "泰拉瑞亚：向导死亡的谣言被严重夸大了",
                "泰拉瑞亚：为什么丢向导玩偶会死别的npc",
                "泰拉瑞亚：现在有更多小黑子！？",
                "泰拉瑞亚：试试《星露谷》！",
                "泰拉瑞亚：试试《地心护核者》！",
                "泰拉瑞亚：强迫症现象模仿者",
                "泰拉瑞亚：试试《太空边缘》！",
                "泰拉瑞亚：Cenx邪教",
                "泰拉瑞亚：Maxx传奇",
                "泰拉瑞亚：查询Red精神状态",
                "泰拉瑞亚：克苏鲁疯了……还失去了一只眼睛！",
                "泰拉瑞亚：我不知道--啊啊啊~哎呦~！",
                "泰拉瑞亚：生存时长两年半！！",
                "泰拉瑞亚：建议观看《边缘行者》",
                "泰拉瑞亚：奥克瑞姆重回江湖！！",
                "泰拉瑞亚：远超生命",
                "泰拉瑞亚：除数为零",
                "泰拉瑞亚：我同情这些工具……",
                "泰拉瑞亚：Red开发救赎",
                "泰拉瑞亚：有矿自有天相",
                "泰拉瑞亚：大地边境",
                "泰拉瑞亚：灾厄：查询山猪喝醉没",
                "泰拉瑞亚：灾厄：为什么山猪砍刀砍儿子",
                "泰拉瑞亚：来试试《死亡细胞》",
                "泰拉瑞亚：原来你也是OP",
                "泰拉瑞亚：试试《地牢守护者2》",
                "泰拉瑞亚：我明白了，原来你们各个都身怀撅寄啊！！",
                "泰拉瑞亚：ln（0）= 0",
                "泰拉瑞亚：旅途的终点，不，是旅途的中点！！！",
                "泰拉瑞亚：兔兔传说",
                "泰拉瑞亚：这绝对是最后一次更新",
                "泰拉瑞亚：欢迎来到泰拉瑞亚！",
                "泰拉瑞亚：别对一个东西期望太高，这样你就会有惊喜",
                "泰拉瑞亚：1帧能玩，2帧流畅，3帧电竞",
                "泰拉瑞亚：灾厄：为什么一个怕被虫子吞了的飞蛾比虫子强？",
                "泰拉瑞亚：别玩《守望先锋》",
                "泰拉瑞亚：暴风雪绝迹",
                "泰拉瑞亚：别玩暴雪游戏，要不小心你的身份证给放了高利贷",
        };
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Config.fileMap.get("config.yml").set("ClientTitle", titles[(int) (Math.random() * titles.length)]);
    }
}
