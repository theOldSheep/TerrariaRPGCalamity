package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCGuide extends TerrariaNPC {
    public TerrariaNPCGuide(World world) {
        super(world, NPCHelper.NPCType.GUIDE);
    }
}
