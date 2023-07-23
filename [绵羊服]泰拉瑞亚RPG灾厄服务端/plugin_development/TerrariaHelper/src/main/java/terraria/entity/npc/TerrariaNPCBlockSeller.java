package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.World;
import terraria.util.NPCHelper;

public class TerrariaNPCBlockSeller extends TerrariaNPC {
    public TerrariaNPCBlockSeller(World world) {
        super(world, NPCHelper.NPCType.BLOCK_SELLER);
    }
}
