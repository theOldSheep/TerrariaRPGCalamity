package terraria.event.listener;

import eos.moe.dragoncore.config.Config;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import terraria.TerrariaHelper;

import java.util.List;


public class RandomTitleListener implements Listener {
    static List<String> titles = TerrariaHelper.settingConfig.getStringList("titleMessages");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (titles.size() > 0)
            Config.fileMap.get("config.yml").set("ClientTitle", titles.get( (int) (Math.random() * titles.size()) ));
    }
}
