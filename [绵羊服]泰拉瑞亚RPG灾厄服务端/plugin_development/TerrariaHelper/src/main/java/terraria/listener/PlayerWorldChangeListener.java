package terraria.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import terraria.util.EntityHelper;

public class PlayerWorldChangeListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
        // reset the music to play
        EntityHelper.setMetadata(e.getPlayer(), "lastBGMTime", 0L);
    }
}
