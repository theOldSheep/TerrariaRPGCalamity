package terraria.entity.monster;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class MonsterSkeleton extends EntitySkeleton {
    protected HashMap<String, Object> extraVariables = new HashMap<>();
    boolean isMonsterPart = false;
    Player target;
    String monsterType, monsterVariant;
    int indexAI = 0, idx = 0;
    double defaultSpeed = 0.2;
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MonsterSkeleton(World world) {
        super(world);
        die();
    }
    protected void initExtraInformation(Player ply, String[] monsterProgressRequired) {
        MonsterHelper.initMonsterInfo(ply, monsterProgressRequired[0], this, monsterType, monsterVariant, extraVariables, isMonsterPart);
        defaultSpeed = ((LivingEntity) bukkitEntity).getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
    }
    public MonsterSkeleton(Player target, String type, Location spawnLoc) {
        this(target, type, spawnLoc, false);
    }
    public MonsterSkeleton(Player target, String type, Location spawnLoc, boolean isMonsterPart) {
        super(((CraftWorld) target.getWorld()).getHandle());
        this.target = target;
        // does not get removed if far away.
        this.persistent = true;
        // is it a part of a certain monster?
        this.isMonsterPart = isMonsterPart;
        // set location
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
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
        if (!isMonsterPart)
            MonsterHelper.tweakPlayerMonsterSpawnedAmount(target, false);
        if (extraVariables.containsKey("attachments")) {
            ArrayList<Entity> attachments = (ArrayList<Entity>) extraVariables.get("attachments");
            for (Entity e : attachments)
                if (e != bukkitEntity)
                    e.remove();
        }
    }
    @Override
    public void B_() {
        super.B_();
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        if (!isMonsterPart) {
            if (getHealth() > 0) {
                if (++this.idx % 10 == 0 || target.getWorld() != bukkitEntity.getWorld())
                    this.target = MonsterHelper.updateMonsterTarget(this.target, this, this.monsterType);
            }
            if (this.target == null) return;
        }
        indexAI = MonsterHelper.monsterAI(this, defaultSpeed, this.target, this.monsterType,
                indexAI, extraVariables, isMonsterPart);
    }
}
