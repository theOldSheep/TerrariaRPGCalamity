package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCNurse extends TerrariaNPC {
    public TerrariaNPCNurse(World world) {
        super(world, NPCHelper.NPCType.NURSE);
    }
}
