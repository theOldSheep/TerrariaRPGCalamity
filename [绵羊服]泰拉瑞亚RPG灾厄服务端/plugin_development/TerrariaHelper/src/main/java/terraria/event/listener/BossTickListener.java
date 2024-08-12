package terraria.event.listener;

import net.minecraft.server.v1_12_R1.Entity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import terraria.TerrariaHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

// Ensures bosses would still tick even if they are very far away
public class BossTickListener implements Listener {
    HashSet<Entity> bossEntities = new HashSet<>();
    HashMap<UUID, Integer> bossEntityTicksLived = new HashMap<>();


    private void tick() {
        // invalid bosses (killed etc.)
        HashSet<Entity> toRemove = new HashSet<>();

        // tick all bosses recorded
        for (Entity e : bossEntities) {
            if (e.isAlive()) {
                int ticksLived = e.ticksLived;
                if (bossEntityTicksLived.getOrDefault(e.getUniqueID(), ticksLived) == ticksLived) {
                    e.B_();
                }
                bossEntityTicksLived.put(e.getUniqueID(), ticksLived);
            }
            else {
                toRemove.add(e);
            }
        }

        // remove dead bosses
        for (Entity e : toRemove) {
            bossEntities.remove(e);
            bossEntityTicksLived.remove(e.getUniqueID());
        }
    }
    public BossTickListener() {
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), this::tick, 0, 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossEntitySpawn(EntitySpawnEvent e) {
        if (e.isCancelled())
            return;
        Bukkit.getScheduler().runTaskLater(TerrariaHelper.getInstance(), () -> {
            if (e.getEntity().getScoreboardTags().contains("isBOSS")) {
                bossEntities.add( ((CraftEntity) e.getEntity()).getHandle() );
            }
        }, 1);
    }
}
