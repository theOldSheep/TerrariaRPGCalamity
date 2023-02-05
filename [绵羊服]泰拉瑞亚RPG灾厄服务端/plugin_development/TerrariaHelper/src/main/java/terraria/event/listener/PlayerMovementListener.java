package terraria.event.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

public class PlayerMovementListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onSprint(PlayerToggleSprintEvent e) {
        e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onTeleport(PlayerTeleportEvent e) {
        switch (e.getCause()) {
            case END_PORTAL:
            case NETHER_PORTAL:
            case END_GATEWAY:
            case SPECTATE:
                e.setCancelled(true);
        }
    }
}
