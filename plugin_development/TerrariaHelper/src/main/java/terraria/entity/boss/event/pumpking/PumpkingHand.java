package terraria.entity.boss.event.pumpking;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class PumpkingHand extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.PUMPKING;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    int index, indexAI = 0;

    double horMulti, vertMulti;
    Vector dVec = null;
    Location destination = null;
    PumpkingHead head;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // remove on owner removal
            if (! head.isAlive()) {
                die();
                return;
            }
            // update target
            target = head.target;
            // disappear if no target is available
            if (target == null) {
                getAttributeInstance(GenericAttributes.maxHealth).setValue(1);
                die();
                return;
            }
            // if target is valid, attack
            indexAI = head.indexAI;
            // hand attack enemy
            int handIdx = indexAI - index * 15;
            Vector distanceVec = head.getBukkitEntity().getLocation()
                    .subtract(target.getLocation()).toVector().normalize();
            // setup variables
            boolean forceful = false;
            // placeholder, hover when shooting scythes etc.
            if (indexAI > 180)
                dVec = new Vector();
            else {
                switch (handIdx) {
                    // normal location
                    case 0:
                    case 60:
                    case 140:
                        // placeholder
                        dVec = new Vector(0, 0, 0);
                        break;
                    // strike
                    case 40:
                    case 120:
                        destination = target.getLocation();
                        forceful = true;
                        break;
                    // horizontal attack windup
                    case 20:
                        destination = target.getLocation().add(
                                distanceVec.getZ() * -22 * horMulti,
                                5,
                                distanceVec.getX() * 22 * horMulti);
                        dVec = null;
                        break;
                    // vertical attack windup
                    case 100:
                        destination = head.getBukkitEntity().getLocation().add(
                                distanceVec.getZ() * -8 * horMulti,
                                15,
                                distanceVec.getX() * 8 * horMulti);
                        dVec = null;
                        break;
                }
            }
            // move
            // if the hand should stay somewhere around the head
            if (dVec != null) {
                dVec = new Vector(distanceVec.getZ() * -10 * horMulti,
                        10 * vertMulti,
                        distanceVec.getX() * 10 * horMulti);
                destination = head.getBukkitEntity().getLocation().add(dVec);
                Vector vHand = destination.clone()
                        .subtract(bukkitEntity.getLocation()).toVector();
                vHand.multiply(0.05);
                bukkitEntity.setVelocity(vHand);
            }
            // if the hand is attacking
            else if (destination != null) {
                Vector vHand = destination.subtract(bukkitEntity.getLocation()).toVector();
                if (forceful) {
                    double vHandLen = vHand.length();
                    vHand.multiply(Math.max(vHandLen / 8, 2.5d) / vHandLen);
                }
                else {
                    vHand.multiply( 1d / 20);
                }
                bukkitEntity.setVelocity(vHand);
                destination = null;
            }


            // shoot scythes when needed
            if (indexAI > 245 && indexAI < 300) {
                int scytheIndex = indexAI - index * 5;
                if (scytheIndex % 10 == 0) {
                    new PumpkingScythe(target, bukkitEntity.getLocation());
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public PumpkingHand(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public PumpkingHand(Player summonedPlayer, ArrayList<LivingEntity> bossParts, PumpkingHead head, int index) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = bossParts.get(0).getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName + (index % 2 == 0 ? "镰刀" : "镰刀§1") );
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("noDamage");
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 390d);
            attrMap.put("defence", 0d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = head.targetMap;
            target = summonedPlayer;
            MetadataHelper.setMetadata(bukkitEntity, MetadataHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(5, false);
            double health = 1d;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            this.bossParts = bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;

            this.head = head;
            this.index = index;
            // relative location offset
            horMulti = index % 2 == 0 ? -1 : 1;
            vertMulti = index >= 2 ? -1 : 1;
        }
    }

    // rewrite AI
    @Override
    public void B_() {
        terraria.entity.boss.BossHelper.updateSpeedForAimHelper(bukkitEntity);
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // load nearby chunks
        {
            for (int i = -1; i <= 1; i ++)
                for (int j = -1; j <= 1; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}
