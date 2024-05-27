package terraria.entity.boss.postMoonLord.ceaselessVoid;

import net.minecraft.server.v1_12_R1.EntitySlime;
import net.minecraft.server.v1_12_R1.GenericAttributes;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import net.minecraft.server.v1_12_R1.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianCommander;
import terraria.entity.boss.postMoonLord.profanedGuardians.GuardianDefender;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.HashMap;

public class DarkEnergy extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CEASELESS_VOID;
    public static final double BASIC_HEALTH = 36000;
    HashMap<String, Double> attrMap;
    Player target = null;
    // other variables and AI
    CeaselessVoid owner;
    int indexAI = 0;
    Vector velocity = new Vector();

    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = owner.target;
            // disappear if no target is available
            if (target == null) {
                die();
                return;
            }
            // if target is valid, attack
            else {
                // TODO

                indexAI++;
                bukkitEntity.setVelocity(velocity);
            }
        }
        this.yaw = (float) MathHelper.getVectorYaw(
                MathHelper.getDirection(owner.getBukkitEntity().getLocation(), bukkitEntity.getLocation(), 1) );

        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    public void updateArcPosition(Location center, double angle, double radius, Vector axis, double rotationAngle) {
        // Calculate the position on the circle
        double circleX = radius * MathHelper.xcos_radian(angle);
        double circleZ = radius * MathHelper.xsin_radian(angle);
        Vector circleOffset = new Vector(circleX, 0, circleZ);

        // Rotate the circle offset around the axis
        Vector rotatedOffset = MathHelper.rotateAroundAxisRadian(circleOffset, axis, rotationAngle);

        // Calculate the final position by adding the rotated offset to the center
        Location newLoc = center.clone().add(rotatedOffset);

        velocity = newLoc.subtract(bukkitEntity.getLocation()).toVector();
    }
    // default constructor to handle chunk unload
    public DarkEnergy(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public DarkEnergy(CeaselessVoid owner) {
        super( owner.getWorld() );
        this.owner = owner;
        // spawn location
        Location spawnLoc = ((LivingEntity) owner.getBukkitEntity()).getEyeLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        owner.getWorld().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName("暗能量");
        setCustomNameVisible(true);
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 780d);
            attrMap.put("damageTakenMulti", 1d);
            attrMap.put("defence", 100d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target
        {
            target = owner.target;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, owner.targetMap);
        }
        // init health and slime size
        {
            setSize(6, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(owner.targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        super.B_();
        // AI
        AI();
    }
}