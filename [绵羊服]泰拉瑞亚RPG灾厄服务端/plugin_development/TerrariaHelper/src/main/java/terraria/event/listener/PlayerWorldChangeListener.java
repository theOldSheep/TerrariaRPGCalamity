package terraria.event.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import terraria.TerrariaHelper;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

public class PlayerWorldChangeListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onWorldChange(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
        // prevent the player from entering vanilla worlds
        String destinationWorldName = e.getTo().getWorld().getName();
        if (! ( destinationWorldName.equals(TerrariaHelper.Constants.WORLD_NAME_SURFACE) ||
                destinationWorldName.equals(TerrariaHelper.Constants.WORLD_NAME_CAVERN) ||
                destinationWorldName.equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD))) {
            e.setCancelled(true);
            return;
        }
        // reset player stats after world change
        PlayerHelper.initPlayerStats(e.getPlayer(), true);
    }
    // stop any portal from forming
    @EventHandler(priority = EventPriority.LOW)
    public void onPortalForm(PortalCreateEvent e) {
        e.setCancelled(false);
    }
}
