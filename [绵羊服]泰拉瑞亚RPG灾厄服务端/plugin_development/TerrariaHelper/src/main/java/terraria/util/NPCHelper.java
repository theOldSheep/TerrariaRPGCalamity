package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.TerrariaHelper;
import terraria.entity.npc.TerrariaNPC;
import terraria.entity.npc.TerrariaNPCGuide;
import terraria.entity.npc.TerrariaNPCNurse;

import java.util.HashMap;

public class NPCHelper {
    public static HashMap<String, Entity> NPCMap = new HashMap<>();
    public static Entity spawnNPC(String type) {
        org.bukkit.World surfaceWorld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        TerrariaNPC nmsNPC;
        switch (type) {
            case "向导":
                nmsNPC = new TerrariaNPCGuide( ((CraftWorld) surfaceWorld).getHandle() );
                break;
            case "护士":
                nmsNPC = new TerrariaNPCNurse( ((CraftWorld) surfaceWorld).getHandle() );
                break;
            default:
                nmsNPC = new TerrariaNPC( ((CraftWorld) surfaceWorld).getHandle(), type );
        }
        // add to world
        ((CraftWorld) surfaceWorld).addEntity(nmsNPC, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // return the NPC spawned
        return nmsNPC.getBukkitEntity();
    }
}
