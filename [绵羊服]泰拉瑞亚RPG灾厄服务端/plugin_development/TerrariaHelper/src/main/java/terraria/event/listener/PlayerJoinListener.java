package terraria.event.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import terraria.util.PlayerHelper;

public class PlayerJoinListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(PlayerJoinEvent e) {
        Player joinedPly = e.getPlayer();
        PlayerHelper.initPlayerStats(joinedPly, true);
        joinedPly.teleport(PlayerHelper.getSpawnLocation(joinedPly));
    }
}
