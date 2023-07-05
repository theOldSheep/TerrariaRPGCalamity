package terraria.event.listener;

import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.MetadataValue;
import terraria.entity.boss.event.CelestialPillar;
import terraria.gameplay.Event;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

public class PlayerJoinListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKeyPressEvent(PlayerJoinEvent e) {
        Player joinedPly = e.getPlayer();
        PlayerHelper.initPlayerStats(joinedPly, true);
        MetadataValue respawnCD = EntityHelper.getMetadata(joinedPly, EntityHelper.MetadataName.RESPAWN_COUNTDOWN);
        // teleport to spawn point if the player is not waiting for revive
        if (respawnCD == null) {
            joinedPly.setGameMode(GameMode.SURVIVAL);
            joinedPly.teleport(PlayerHelper.getSpawnLocation(joinedPly));
        }
        // for players after golem, show the celestial pillar boss bar to them
        if (PlayerHelper.hasDefeated(joinedPly, BossHelper.BossType.GOLEM.msgName))
            for (CelestialPillar pillar : Event.pillars.values())
                pillar.bossbar.addPlayer( ((CraftPlayer) joinedPly).getHandle() );
    }
}
