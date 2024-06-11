package terraria.entity.boss.hardMode.skeletronPrime;

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
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class SkeletronPrimeHand extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SKELETRON_PRIME;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    Player target = null;
    // other variables and AI
    enum HandType {
        LASER, CANNON, SAW, VICE;
    }
    HandType handType;
    int index, indexAI = 0;
    EntityHelper.ProjectileShootInfo shootInfoRocket, shootInfoLaser;
    double horMulti, vertMulti;
    Vector dVec = null;
    Location destination = null;
    SkeletronPrimeHead head;
    private void shootLaser() {
        shootInfoLaser.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        for (Vector velocity : MathHelper.getCircularProjectileDirections(
                7, 3, 30, target, shootInfoLaser.shootLoc, 2.25)) {
            shootInfoLaser.velocity = velocity;
            EntityHelper.spawnProjectile(shootInfoLaser);
        }
    }
    private void shootRocket(boolean singleOrSpread) {
        double rocketSpeed = 1;
        shootInfoRocket.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        shootInfoRocket.setLockedTarget(target);
        if (singleOrSpread) {
            shootInfoRocket.velocity = MathHelper.getDirection(shootInfoRocket.shootLoc, target.getEyeLocation(), rocketSpeed);
            EntityHelper.spawnProjectile(shootInfoRocket);
        }
        else {
            for (Vector velocity : MathHelper.getCircularProjectileDirections(
                    5, 2, 22.5, target, shootInfoRocket.shootLoc, rocketSpeed)) {
                shootInfoRocket.velocity = velocity;
                EntityHelper.spawnProjectile(shootInfoRocket);
            }
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = head.target;
            if (! head.isAlive())
                target = null;
            // disappear if no target is available
            if (target == null) {
                getAttributeInstance(GenericAttributes.maxHealth).setValue(1);
                die();
                return;
            }
            // if target is valid, attack
            Location usualLocation;
            {
                Vector distanceVec = head.getBukkitEntity().getLocation()
                        .subtract(target.getLocation()).toVector().normalize();
                dVec = new Vector(distanceVec.getZ() * -10 * horMulti,
                        10 * vertMulti,
                        distanceVec.getX() * 10 * horMulti);
                usualLocation = head.getBukkitEntity().getLocation().add(dVec);
            }
            int attackFrequency = 5 - head.handsAlive;
            switch (handType) {
                case LASER: {
                    Vector moveDir = usualLocation.subtract(bukkitEntity.getLocation()).toVector();
                    moveDir.multiply(0.1);
                    bukkitEntity.setVelocity(moveDir);
                    int shootInterval = 10 + 5 * head.handsAlive;
                    if (indexAI % shootInterval == 0)
                        shootLaser();
                    break;
                }
                case VICE: {
                    // stay with the head
                    int attackInterval = head.handsAlive * 10;
                    if (indexAI % attackInterval > attackInterval / 2) {
                        Vector moveDir = usualLocation.subtract(bukkitEntity.getLocation()).toVector();
                        moveDir.multiply(0.2);
                        bukkitEntity.setVelocity(moveDir);
                    }
                    // dash into enemy
                    else {
                        if (indexAI % attackInterval == 0) {
                            Vector velocity = target.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
                            // dash duration = attackInterval / 2, actual distance = velocity length * 1.5
                            velocity.multiply(3d / attackInterval);
                            bukkitEntity.setVelocity(velocity);
                        }
                    }
                    break;
                }
                case SAW: {
                    int attackSwitchInterval = 40 + attackFrequency * 10;
                    // go back to usual location
                    if (indexAI % 80 > attackSwitchInterval) {
                        Vector moveDir = usualLocation.subtract(bukkitEntity.getLocation()).toVector();
                        moveDir.multiply(0.1);
                        bukkitEntity.setVelocity(moveDir);
                    }
                    // follow player
                    else {
                        double followVelocity = 1 + attackFrequency * 0.15;
                        Vector velocity = MathHelper.getDirection(
                                ((LivingEntity) bukkitEntity).getEyeLocation(), target.getEyeLocation(),
                                followVelocity, true);
                        bukkitEntity.setVelocity(velocity);
                    }
                    break;
                }
                case CANNON: {
                    Vector moveDir = usualLocation.subtract(bukkitEntity.getLocation()).toVector();
                    moveDir.multiply(0.1);
                    bukkitEntity.setVelocity(moveDir);
                    int shootInterval = head.handsAlive * 10;
                    if (indexAI % (shootInterval * 2) == 0)
                        shootRocket(true);
                    else if (indexAI % shootInterval == 0)
                        shootRocket(false);
                    break;
                }
            }
            indexAI ++;
        }
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public SkeletronPrimeHand(World world) {
        super(world);
        super.die();
    }
    // a constructor for actual spawning
    public SkeletronPrimeHand(Player summonedPlayer, ArrayList<LivingEntity> bossParts, SkeletronPrimeHead head, int index) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        Location spawnLoc = bossParts.get(0).getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setSize(5, false);
        setCustomNameVisible(true);
        addScoreboardTag("isMechanic");
        addScoreboardTag("isMonster");
        addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) EntityHelper.getMetadata(bossParts.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // get hand type
        switch (index) {
            case 0:
                handType = HandType.LASER;
                break;
            case 1:
                handType = HandType.CANNON;
                break;
            case 2:
                handType = HandType.SAW;
                break;
            case 3:
                handType = HandType.VICE;
                break;
        }
        // hand type specific attributes, health etc.
        {
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = 100;
            switch (handType) {
                case SAW:
                    setCustomName("机械锯");
                    attrMap.put("damage", 408d);
                    attrMap.put("defence", 76d);
                    health = 22374;
                    break;
                case VICE:
                    setCustomName("机械钳");
                    attrMap.put("damage", 408d);
                    attrMap.put("defence", 68d);
                    health = 22374;
                    break;
                case LASER:
                    setCustomName("机械激光");
                    attrMap.put("damage", 204d);
                    attrMap.put("defence", 40d);
                    health = 14916;
                    break;
                case CANNON:
                    setCustomName("机械炮");
                    attrMap.put("damage", 204d);
                    attrMap.put("defence", 46d);
                    health = 17400;
                    break;
            }
            health *= healthMulti;
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
        // shoot info
        {
            shootInfoLaser = new EntityHelper.ProjectileShootInfo(
                    bukkitEntity, new Vector(), SkeletronPrimeHead.attrMapRocket, EntityHelper.DamageType.MAGIC, "死亡激光");
            shootInfoLaser.properties.put("liveTime", 40);
            shootInfoRocket = new EntityHelper.ProjectileShootInfo(
                    bukkitEntity, new Vector(), SkeletronPrimeHead.attrMapRocket, EntityHelper.DamageType.ROCKET, "红烟花火箭");
            shootInfoRocket.properties.put("autoTrace", true);
            shootInfoRocket.properties.put("autoTraceMethod", 2);
            shootInfoRocket.properties.put("autoTraceRadius", 64d);
            shootInfoRocket.properties.put("autoTraceSharpTurning", false);
            shootInfoRocket.properties.put("autoTraceAbility", 0.35d);
            shootInfoRocket.properties.put("noAutoTraceTicks", 10);
            shootInfoRocket.properties.put("maxAutoTraceTicks", 15);
            shootInfoRocket.properties.put("autoTraceEndSpeedMultiplier", 5d);
            shootInfoRocket.properties.put("liveTime", 22);
            shootInfoRocket.properties.put("gravity", 0d);
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
