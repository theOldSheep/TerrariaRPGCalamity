package terraria.event.listener;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Set;


public class VanillaMechanicListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDeath(EntityDeathEvent evt) {
        evt.setDroppedExp(0);
        evt.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEat(PlayerItemConsumeEvent evt) {
        evt.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSleep(PlayerBedEnterEvent evt) {
        evt.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChunkUnload(ChunkUnloadEvent evt) {
        for (Entity entity : evt.getChunk().getEntities()) {
            // do not unload chunks with boss/celestial pillar/npc in it
            Set<String> scoreboardTags = entity.getScoreboardTags();
            if (scoreboardTags.contains("isBOSS") || scoreboardTags.contains("isPillar") || scoreboardTags.contains("isNPC")) {
                evt.setCancelled(true);
                return;
            }
        }
    }
}
