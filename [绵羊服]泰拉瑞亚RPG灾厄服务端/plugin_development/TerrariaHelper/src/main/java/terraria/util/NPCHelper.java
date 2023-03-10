package terraria.util;

import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import terraria.TerrariaHelper;
import terraria.entity.npc.*;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class NPCHelper {
    public static HashMap<String, Entity> NPCMap = new HashMap<>();
    public static HashMap<String, Class> NPCTypeMap = new HashMap<>();
    static {
        NPCTypeMap.put("渔夫", TerrariaNPCAngler.class);
        NPCTypeMap.put("军火商", TerrariaNPCArmsDealer.class);
        NPCTypeMap.put("建材商人", TerrariaNPCBlockSeller.class);
        NPCTypeMap.put("裁缝", TerrariaNPCClothier.class);
        NPCTypeMap.put("爆破专家", TerrariaNPCDemolitionist.class);
        NPCTypeMap.put("哥布林工匠", TerrariaNPCGoblinTinkerer.class);
        NPCTypeMap.put("向导", TerrariaNPCGuide.class);
        NPCTypeMap.put("护士", TerrariaNPCNurse.class);
    }
    public static Entity spawnNPC(String type) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        org.bukkit.World surfaceWorld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        TerrariaNPC nmsNPC;
        if (NPCTypeMap.containsKey(type)) {
            Class NPCClass = NPCTypeMap.get(type);
            nmsNPC = (TerrariaNPC) ( NPCClass.getConstructor(World.class)
                    .newInstance( ((CraftWorld) surfaceWorld).getHandle() ) );
        } else {
            nmsNPC = new TerrariaNPC( ((CraftWorld) surfaceWorld).getHandle(), type );
        }
        // add to world
        ((CraftWorld) surfaceWorld).addEntity(nmsNPC, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // return the NPC spawned
        return nmsNPC.getBukkitEntity();
    }
}
