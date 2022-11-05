package terraria.listener;

import eos.moe.dragoncore.api.event.KeyReleaseEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class playerTestListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyReleaseEvent(KeyReleaseEvent e) {
        Bukkit.getServer().broadcastMessage("KEYUP: " + e.getKey());
    }
}
