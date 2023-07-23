package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCDemolitionist extends TerrariaNPC {
    public TerrariaNPCDemolitionist(World world) {
        super(world, NPCHelper.NPCType.DEMOLITIONIST);
    }
}
