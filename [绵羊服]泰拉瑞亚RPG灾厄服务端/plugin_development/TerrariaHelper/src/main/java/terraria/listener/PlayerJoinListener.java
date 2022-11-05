package terraria.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import terraria.util.PlayerHelper;

public class PlayerJoinListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(PlayerJoinEvent e) {
        PlayerHelper.initPlayerStats(e.getPlayer());
    }
}
