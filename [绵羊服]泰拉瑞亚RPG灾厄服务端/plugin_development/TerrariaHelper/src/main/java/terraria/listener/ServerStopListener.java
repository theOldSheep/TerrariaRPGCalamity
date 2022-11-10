package terraria.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

public class ServerStopListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onServerShutdown(PluginDisableEvent e) {
        for (Player ply : Bukkit.getOnlinePlayers()) ply.kickPlayer("服务器已关闭！");
    }
}
