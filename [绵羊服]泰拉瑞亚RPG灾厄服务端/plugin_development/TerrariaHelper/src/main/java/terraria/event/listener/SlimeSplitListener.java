package terraria.event.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SlimeSplitEvent;

public class SlimeSplitListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onSlimeSplit(SlimeSplitEvent e) {
        e.setCancelled(true);
    }
}
