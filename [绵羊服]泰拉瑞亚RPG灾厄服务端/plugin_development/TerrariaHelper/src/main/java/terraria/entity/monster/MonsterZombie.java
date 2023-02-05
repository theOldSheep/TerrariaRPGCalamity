package terraria.entity.monster;

import net.minecraft.server.v1_12_R1.EntityZombie;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.HashMap;

public class MonsterZombie extends EntityZombie {
    protected HashMap<String, Object> extraVariables = new HashMap<>();
    Player target;
    String monsterType, monsterVariant;
    int indexAI = 0;
    double defaultSpeed = 0.2;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MonsterZombie(World world) {
        super(world);
        die();
    }
    protected void initExtraInformation(Player ply, String[] monsterProgressRequired) {
        MonsterHelper.initMonsterInfo(ply, monsterProgressRequired[0], this, monsterType, monsterVariant);
        defaultSpeed = ((LivingEntity) bukkitEntity).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
    }
    public MonsterZombie(org.bukkit.entity.Player target, String type, Location spawnLoc, boolean isBaby) {
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
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        if (getHealth() > 0) {
            this.target = MonsterHelper.updateMonsterTarget(this.target, this);
            indexAI = MonsterHelper.monsterAI(this, defaultSpeed, this.target, this.monsterType, indexAI, extraVariables);
        }
    }
}
