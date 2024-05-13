package terraria.event.listener;

import net.minecraft.server.v1_12_R1.BossBattleServer;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.MetadataValue;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.gameplay.EventAndTime;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.ItemHelper;
import terraria.util.PlayerHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

public class PlayerJoinListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player joinedPly = e.getPlayer();
        // if this is a new player
        if (! new File(PlayerHelper.getPlayerDataFilePath(joinedPly)).exists()) {
            PlayerHelper.giveItem(joinedPly,
                    ItemHelper.getItemFromDescription("新手礼包", false), false);
            joinedPly.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(200);
            joinedPly.setHealth(200);
        }
        // init stats
        PlayerHelper.initPlayerStats(joinedPly, true);
        // tick existing effects
        {
            HashMap<String, Integer> effects = EntityHelper.getEffectMap(joinedPly);
            for (String effect : effects.keySet()) {
                EntityHelper.prepareTickEffect(joinedPly, effect);
            }
        }
        // teleport to spawn point if the player is not waiting for revive
        MetadataValue respawnCD = EntityHelper.getMetadata(joinedPly, EntityHelper.MetadataName.RESPAWN_COUNTDOWN);
        if (respawnCD == null) {
            joinedPly.setGameMode(GameMode.SURVIVAL);
            joinedPly.teleport(PlayerHelper.getSpawnLocation(joinedPly), PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        // for players after golem, show the celestial pillar boss bar to them
        EntityPlayer playerNMS = ((CraftPlayer) joinedPly).getHandle();
        if (PlayerHelper.hasDefeated(joinedPly, BossHelper.BossType.GOLEM.msgName))
            for (CelestialPillar pillar : EventAndTime.pillars.values())
                pillar.bossbar.addPlayer( playerNMS );
        // show boss bars
        for (ArrayList<LivingEntity> bossList : BossHelper.bossMap.values()) {
            if (bossList.isEmpty())
                continue;
            MetadataValue bossbarMetadata = EntityHelper.getMetadata(bossList.get(0), EntityHelper.MetadataName.BOSS_BAR);
            if (bossbarMetadata == null)
                TerrariaHelper.LOGGER.log(Level.SEVERE, "Boss " + bossList.get(0) +
                        " has no boss bar metadata when showing bossbar to a joined player.");
            else
                ((BossBattleServer) bossbarMetadata.value()).addPlayer(playerNMS);
        }
        // show the event progress bar if applicable
        if (EventAndTime.eventProgressBar != null)
            EventAndTime.eventProgressBar.addPlayer( playerNMS );
        // give permission to show item in chat
        joinedPly.addAttachment(TerrariaHelper.getInstance())
                .setPermission("chatitem.use", true);
    }

}
