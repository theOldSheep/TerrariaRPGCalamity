package terraria.listener;

import eos.moe.dragoncore.api.event.KeyPressEvent;
import eos.moe.dragoncore.api.event.KeyReleaseEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import terraria.util.EntityHelper;

public class playerKeyToggleListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyReleaseEvent(KeyReleaseEvent e) {
        Bukkit.getServer().broadcastMessage("KEYUP: " + e.getKey());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(KeyPressEvent e) {
        Player ply = e.getPlayer();
        switch (e.getKey()) {
            case "0":
            case "1":
            case "2":
            case "3":
            case "4":
            case "5":
            case "6":
            case "7":
            case "8":
            case "9": {
                int targetSlot = Integer.parseInt(e.getKey());
                targetSlot = (targetSlot + 9) % 10;
                if (ply.getScoreboardTags().contains("useCD")) {
                    EntityHelper.setMetadata(ply, "nextHeldSlot", targetSlot);
                } else {
                    EntityHelper.setMetadata(ply, "heldSlot", targetSlot);
                }
                break;
            }
        }
        Bukkit.getServer().broadcastMessage("KEYDOWN: " + e.getKey());
    }

}
