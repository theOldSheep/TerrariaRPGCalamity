package terraria.entity.monster;

import net.minecraft.server.v1_12_R1.EntityZombieHusk;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashMap;

public class MonsterHusk extends EntityZombieHusk {
    protected HashMap<String, Object> extraVariables = new HashMap<>();
    Player target;
    String monsterType, monsterVariant;
    int indexAI = 0;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MonsterHusk(World world) {
        super(world);
        die();
    }
    protected void initExtraInformation(Player ply, String[] monsterProgressRequired) {
        MonsterHelper.initMonsterInfo(ply, monsterProgressRequired[0], this, monsterType, monsterVariant);
    }
    public MonsterHusk(org.bukkit.entity.Player target, String type, Location spawnLoc, boolean isBaby) {
        super(((CraftWorld) target.getWorld()).getHandle());
        this.target = target;
        // does not get removed if far away.
        this.persistent = true;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        setBaby(isBaby);
        // get the variant to use
        String[] progressRequirement;
        {
            this.monsterType = type;
            this.monsterVariant = MonsterHelper.getMonsterVariant(target, type);
            progressRequirement = MonsterHelper.getMonsterProgressRequirement(type);
        }
        // add to world
        ((CraftWorld) target.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // attributes etc.
        initExtraInformation(target, progressRequirement);
    }
    @Override
    public void die() {
        super.die();
        MonsterHelper.tweakPlayerMonsterSpawnedAmount(target, false);
    }
    @Override
    public void B_() {
        super.B_();
        indexAI = MonsterHelper.monsterAI(this, this.monsterType, indexAI, extraVariables);
        this.target = MonsterHelper.updateMonsterTarget(this.target, this);
    }
}
