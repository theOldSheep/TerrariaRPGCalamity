package terraria.event.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockBreakListener implements Listener {
    @EventHandler (priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent e) {
        e.setDropItems(false);
    }
}
