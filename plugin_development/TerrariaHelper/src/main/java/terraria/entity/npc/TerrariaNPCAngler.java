package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCAngler extends TerrariaNPC {
    public TerrariaNPCAngler(World world) {
        super(world, NPCHelper.NPCType.ANGLER);
    }
}