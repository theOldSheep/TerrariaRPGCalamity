package terraria.event.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.util.EntityHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class EntitySpawnListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntitySpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            e.setCancelled(true);
            // TODO: handle TNT explode
            // TNT explode
            Entity entity = e.getEntity();
            if (entity instanceof TNTPrimed) {
                TNTPrimed entityTNT = (TNTPrimed) entity;
                entityTNT.setCustomName("TNT");
                // explode instantly
                Collection<? extends Player> allPlayers = Bukkit.getOnlinePlayers();
                if (allPlayers.size() > 0) {
                    // find the nearest player
                    Location blastLoc = entityTNT.getLocation();
                    double closestDistSqr = 1e5;
                    Player damageCausePlayer = allPlayers.iterator().next();
                    for (Player currPly : allPlayers) {
                        if (currPly.getWorld() != blastLoc.getWorld())
                            continue;
                        double currDistSqr = currPly.getLocation().distanceSquared(blastLoc);
                        if (currDistSqr < closestDistSqr) {
                            closestDistSqr = currDistSqr;
                            damageCausePlayer = currPly;
                        }
                    }
                    // make the player responsible for the explosion
                    HashMap<String, Double> attrMap = new HashMap<>();
                    attrMap.put("damage", 1200d);
                    EntityHelper.setMetadata(entityTNT, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
                    EntityHelper.setMetadata(entityTNT, EntityHelper.MetadataName.DAMAGE_SOURCE, damageCausePlayer);
                    EntityHelper.handleEntityExplode(entityTNT, 7.5, new ArrayList<>(), blastLoc);
                }
            }
        }
    }
}
