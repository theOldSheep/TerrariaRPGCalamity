package terraria.event.listener;

import net.minecraft.server.v1_12_R1.EntityFishingHook;
import net.minecraft.server.v1_12_R1.EntityHuman;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftFish;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import terraria.entity.TerrariaFishingHook;

public class FishHookShootListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public static void onFishhookSpawn(ProjectileLaunchEvent e) {
        Entity spawned = e.getEntity();
        if (spawned.getType() == EntityType.FISHING_HOOK) {
            EntityFishingHook hook = ((CraftFish) spawned).getHandle();
            if (!(hook instanceof TerrariaFishingHook)) {
                e.setCancelled(true);
                spawned.remove();
                // spawn fishhook
                EntityHuman shooter = hook.owner;
                TerrariaFishingHook entity = new TerrariaFishingHook(hook.getWorld(), shooter, true, 10f);
                CraftWorld wld = (CraftWorld) spawned.getWorld();
                wld.addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
            }
        }
    }
}
