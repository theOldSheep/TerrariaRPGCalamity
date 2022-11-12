package terraria.event.listener;

import eos.moe.dragoncore.api.event.KeyPressEvent;
import eos.moe.dragoncore.api.event.KeyReleaseEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PlayerKeyToggleListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyReleaseEvent(KeyReleaseEvent e) {
        Bukkit.getServer().broadcastMessage("KEYUP: " + e.getKey());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(KeyPressEvent e) {
        Player ply = e.getPlayer();
        switch (e.getKey()) {
            case "W":
            case "A":
            case "S":
            case "D":
            case "SPACE":

        }
        Bukkit.getServer().broadcastMessage("KEYDOWN: " + e.getKey());
    }

}
