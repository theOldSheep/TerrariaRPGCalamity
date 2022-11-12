package terraria.event.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import terraria.util.PlayerHelper;


public class PlayerQuitListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        Player ply = e.getPlayer();
        PlayerHelper.saveInventories(ply);
    }
}
