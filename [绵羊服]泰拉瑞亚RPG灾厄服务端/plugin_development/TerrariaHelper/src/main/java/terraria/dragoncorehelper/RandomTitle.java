package terraria.dragoncorehelper;

import eos.moe.dragoncore.config.Config;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public class RandomTitle implements Listener {
    String[] titles;
    public RandomTitle() {
        titles = new String[]{
                "泰拉瑞亚：少说多挖之外传！",
                "泰拉瑞亚：沙子被制服了",
                "泰拉瑞亚：兔兔的传说",
                "泰拉瑞亚：5岁以下的儿童不宜使用小块块",
                "泰拉瑞亚：没有奶牛层",
                "泰拉瑞亚：挖土之王",
                "泰拉瑞亚：这边的草地更绿",
                "泰拉瑞亚第3部分：向导归来",
                "泰拉瑞亚：骷髅博士和血月神庙",
                "泰拉瑞亚：紫草地！",
                "泰拉瑞亚：劳工挖啊挖！",
                "泰拉瑞亚：没有人在后面挖！",
                "泰拉瑞亚：史莱姆侏罗纪公园",
                "泰拉瑞亚：可疑魔眼",
                "泰拉瑞亚：神土",
                "泰拉瑞亚：试试《我的世界》！",
                "泰拉瑞亚：泰拉瑞亚：泰拉瑞亚：",
                "泰拉瑞亚：按alt-f4",
                "泰拉瑞亚：除数为零",
                "泰拉瑞亚：克苏鲁疯了...还弄丢了眼睛！",
                "泰拉瑞亚：向导死亡的谣言被严重夸大了",
                "泰拉瑞亚：天罚粘土",
                "泰拉瑞亚：远超生命",
                "泰拉瑞亚：物竞天择！",
                "泰拉瑞亚：强迫症现象模仿者",
                "泰拉瑞亚：那紫色的尖玩艺儿是什么？",
                "泰拉瑞亚：现已加入音效",
                "泰拉瑞亚：挖矿吉日",
                "泰拉瑞亚：挖挖总有收获",
                "泰拉瑞亚：我回答一些关于PC版更新的东西...",
                "泰拉瑞亚：红色开发救赎",
                "泰拉瑞亚：洞穴探险者惊道“那是什么”？",
                "泰拉瑞亚：现在有更多东西想杀你！",
                "泰拉瑞亚：陆地生物的麻烦",
                "泰拉瑞亚：我同情这些工具...",
                "泰拉瑞亚：我同情这些工具",
                "泰拉瑞亚2：电布加洛舞",
                "泰拉瑞亚：现推出3D",
                "泰拉瑞亚：重挖好吗？",
                "泰拉瑞亚：我想成为向导",
                "泰拉瑞亚：愿块块与你同在",
                "泰拉瑞亚：史莱姆的崛起",
                "泰拉瑞亚：我不知道--啊啊啊！",
                "泰拉瑞亚：兄弟你疯了吧？",
                "泰拉瑞亚：麦克斯传奇",
                "泰拉瑞亚：伪蜜蜂！！！",
                "泰拉瑞亚：地球冒险",
                "泰拉瑞亚：有矿自有天相",
                "泰拉瑞亚：邻机即将拥有",
                "泰拉瑞亚：Cenx邪教",
                "泰拉瑞亚：绝世精金！",
                "泰拉瑞亚：试试《旷野之息》！",
                "泰拉瑞亚：我只想知道金子在哪？",
                "泰拉瑞亚：9 + 1 = 11",
                "泰拉瑞亚：无限世纪之花",
                "泰拉瑞亚：现在有更多鸭子！",
                "泰拉瑞亚：也试试饥荒吧！",
                "泰拉瑞亚：美味又易燃"
        };
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Config.fileMap.get("config.yml").set("ClientTitle", titles[(int) (Math.random() * titles.length)]);
    }
}
