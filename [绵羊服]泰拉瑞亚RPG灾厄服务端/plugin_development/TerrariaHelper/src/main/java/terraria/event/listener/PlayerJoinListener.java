package terraria.event.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.MetadataValue;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

public class PlayerJoinListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(PlayerJoinEvent e) {
        Player joinedPly = e.getPlayer();
        PlayerHelper.initPlayerStats(joinedPly, true);
        MetadataValue respawnCD = EntityHelper.getMetadata(joinedPly, "respawnCD");
        // teleport to spawn point if the player is not waiting for revive
        if (respawnCD == null) {
            joinedPly.setGameMode(GameMode.SURVIVAL);
            joinedPly.teleport(PlayerHelper.getSpawnLocation(joinedPly));
        }
    }
}
