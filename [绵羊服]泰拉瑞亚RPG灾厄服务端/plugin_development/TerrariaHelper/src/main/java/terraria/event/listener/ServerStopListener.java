package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import terraria.util.PlayerHelper;

public class ServerStopListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onServerShutdown(PluginDisableEvent e) {
        for (Player ply : Bukkit.getOnlinePlayers()) {
            PlayerHelper.saveInventories(ply);
            ply.kickPlayer("服务器已关闭！");
        }
    }
}
