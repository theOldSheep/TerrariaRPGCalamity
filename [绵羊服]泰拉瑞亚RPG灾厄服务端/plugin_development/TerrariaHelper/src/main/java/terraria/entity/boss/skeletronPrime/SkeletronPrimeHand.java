package terraria.entity.boss.skeletronPrime;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.skeletron.SkeletronHead;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class SkeletronPrimeHand extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SKELETRON_PRIME;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 2580;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
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
                7, 3, 75, target, shootInfoLaser.shootLoc, 1)) {
            shootInfoLaser.velocity = velocity;
            EntityHelper.spawnProjectile(shootInfoLaser).setGlowing(true);
        }
    }
    private void shootRocket(boolean singleOrSpread) {
        double rocketSpeed = 2;
        shootInfoRocket.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        if (singleOrSpread) {
            shootInfoRocket.velocity = MathHelper.getDirection(shootInfoRocket.shootLoc, target.getEyeLocation(), rocketSpeed);
            EntityHelper.spawnProjectile(shootInfoRocket).setGlowing(true);
        }
        else {
            for (Vector velocity : MathHelper.getCircularProjectileDirections(
                    3, 2, 22.5, target, shootInfoRocket.shootLoc, rocketSpeed)) {
                shootInfoRocket.velocity = velocity;
                EntityHelper.spawnProjectile(shootInfoRocket).setGlowing(true);
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
                    if (indexAI % (attackFrequency * 20) == 0)
                        shootLaser();
                    break;
                }
                case VICE: {
                    // stay with the head
                    int attackInterval = 12 + head.handsAlive * 3;
                    if (indexAI % (attackInterval * 10) < attackInterval * 5) {
                        Vector moveDir = usualLocation.subtract(bukkitEntity.getLocation()).toVector();
                        moveDir.multiply(0.1);
                        bukkitEntity.setVelocity(moveDir);
                    }
                    // dash into enemy
                    else {
                        if (indexAI % attackInterval == 0) {
                            Vector velocity = target.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
                            velocity.multiply(1.5d / attackInterval);
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
                        double followVelocity = 1 + attackFrequency * 0.2;
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
                    int shootIndex = indexAI % (attackFrequency * 20);
                    if (shootIndex == attackFrequency * 5)
                        shootRocket(true);
                    else if (shootIndex == attackFrequency * 15)
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
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init target map
        {
            targetMap = (HashMap<Player, Double>) EntityHelper.getMetadata(bossParts.get(0), "targets").value();
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
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
            double health;
            switch (handType) {
                case SAW:
                    setCustomName("机械锯");
                    attrMap.put("damage", 408d);
                    attrMap.put("defence", 76d);
                    health = 22374 * healthMulti;
                    getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
                    setHealth((float) health);
                    break;
                case VICE:
                    setCustomName("机械钳");
                    attrMap.put("damage", 408d);
                    attrMap.put("defence", 68d);
                    health = 22374 * healthMulti;
                    getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
                    setHealth((float) health);
                    break;
                case LASER:
                    setCustomName("机械激光");
                    attrMap.put("damage", 204d);
                    attrMap.put("defence", 40d);
                    health = 14916 * healthMulti;
                    getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
                    setHealth((float) health);
                    break;
                case CANNON:
                    setCustomName("机械炮");
                    attrMap.put("damage", 204d);
                    attrMap.put("defence", 46d);
                    health = 17400 * healthMulti;
                    getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
                    setHealth((float) health);
                    break;
            }
        }
        // boss parts and other properties
        {
            this.bossParts = bossParts;
            bossParts.add((LivingEntity) bukkitEntity);
            this.noclip = true;
            this.setNoGravity(true);
            this.glowing = true;
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
                    bukkitEntity, new Vector(), SkeletronPrimeHead.attrMapRocket, "Magic", "死亡激光");
            shootInfoRocket = new EntityHelper.ProjectileShootInfo(
                    bukkitEntity, new Vector(), SkeletronPrimeHead.attrMapRocket, "Rocket", "");
            shootInfoRocket.projectileName = "红烟花火箭";
            shootInfoRocket.properties.put("autoTrace", true);
            shootInfoRocket.properties.put("autoTraceMethod", 2);
            shootInfoRocket.properties.put("autoTraceRadius", 24d);
            shootInfoRocket.properties.put("autoTraceSharpTurning", false);
            shootInfoRocket.properties.put("autoTraceAbility", 0.35);
            shootInfoRocket.properties.put("noAutoTraceTicks", 20);
            shootInfoRocket.properties.put("liveTime", 80);
            shootInfoRocket.properties.put("gravity", 0d);
            shootInfoRocket.properties.put("blockHitAction", "thru");
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
