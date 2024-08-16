package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCClothier extends TerrariaNPC {
    public TerrariaNPCClothier(World world) {
        super(world, NPCHelper.NPCType.CLOTHIER);
    }
}
