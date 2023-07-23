package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCArmsDealer extends TerrariaNPC {
    public TerrariaNPCArmsDealer(World world) {
        super(world, NPCHelper.NPCType.ARMS_DEALER);
    }
}
