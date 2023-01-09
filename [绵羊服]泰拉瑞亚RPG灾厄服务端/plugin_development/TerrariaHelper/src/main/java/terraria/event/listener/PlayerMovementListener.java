package terraria.event.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;

public class PlayerMovementListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onSprint(PlayerToggleSprintEvent e) {
        e.setCancelled(true);
    }
}
