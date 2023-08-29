package terraria.entity.boss.skeletron;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SkeletronHand extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SKELETRON;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 2580;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int index, indexAI = 0, lastIndexAI = -1;

    double horMulti, vertMulti;
    Vector dVec = null;
    Location destination = null;
    SkeletronHead head;
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
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
            if (lastIndexAI != indexAI) {
                lastIndexAI = indexAI;
                int handIdx = indexAI - index * 5;
                Vector distanceVec = head.getBukkitEntity().getLocation()
                        .subtract(target.getLocation()).toVector().normalize();
                // setup variables
                boolean forceful = false;
                switch (handIdx) {
                    // normal location
                    case 0:
                    case 30:
                    case 70:
                        // placeholder
                        dVec = new Vector(0, 0, 0);
                        break;
                    // strike
                    case 20:
                    case 60:
                        destination = target.getLocation();
                        forceful = true;
                        break;
                    // horizontal attack windup
                    case 10:
                        destination = target.getLocation().add(
                                distanceVec.getZ() * -22 * horMulti,
                                5,
                                distanceVec.getX() * 22 * horMulti);
                        dVec = null;
                        break;
                    // vertical attack windup
                    case 50:
                        destination = head.getBukkitEntity().getLocation().add(
                                distanceVec.getZ() * -8 * horMulti,
                                15,
                                distanceVec.getX() * 8 * horMulti);
                        dVec = null;
                        break;
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
                        vHand.multiply(Math.max(vHandLen / 15, 2d) / vHandLen);
                    }
                    else {
                        vHand.multiply( 1d / 25);
                    }
                    bukkitEntity.setVelocity(vHand);
                    destination = null;
                }
            }
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public SkeletronHand(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public SkeletronHand(Player summonedPlayer, ArrayList<LivingEntity> bossParts, SkeletronHead head, int index) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = bossParts.get(0).getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName + (index % 2 == 0 ? "左手" : "右手") );
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 198d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 28d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(5, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
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
        super.B_();
        // undo air resistance etc.
        motX /= 0.91;
        motY /= 0.98;
        motZ /= 0.91;
        // load nearby chunks
        {
            for (int i = -2; i <= 2; i ++)
                for (int j = -2; j <= 2; j ++) {
                    org.bukkit.Chunk currChunk = bukkitEntity.getLocation().add(i << 4, 0, j << 4).getChunk();
                    currChunk.load();
                }
        }
        // AI
        AI();
    }
}
