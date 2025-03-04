package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCBandit extends TerrariaNPC {
    public TerrariaNPCBandit(World world) {
        super(world, NPCHelper.NPCType.BANDIT);
    }
}
