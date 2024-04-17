package terraria.entity.boss.wallOfFlesh;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class WallOfFleshMouth extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.WALL_OF_FLESH;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 54834;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int indexAI = 0, enragedCounter = 0;
    boolean enraged = false;
    Vector horizontalMoveDirection;
    static double ENRAGE_DIST_SQR = 48 * 48, MAX_HORIZONTAL_SPD = 0.8, MIN_EYE_SPACE = 12, Y_COORD_ADJUST_RATE = 0.05;
    static HashMap<String, Double> demon_scythe_attrMap;
    static {
        demon_scythe_attrMap = new HashMap<>();
        demon_scythe_attrMap.put("damage", 336d);
        demon_scythe_attrMap.put("damageMulti", 1d);
    }
    private void spawnDemonScythe() {
        Vector velocity = target.getEyeLocation().subtract(bukkitEntity.getLocation()).toVector();
        double velLen = velocity.length();
        if (velLen < 1e-5) {
            velocity = horizontalMoveDirection.clone();
            velLen = 1;
        }
        velocity.multiply(1 / velLen);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, velocity, demon_scythe_attrMap, "--");
        shootInfo.projectileName = "恶魔之镰";
        shootInfo.properties.put("autoTrace", true);
        shootInfo.properties.put("autoTraceMethod", 2);
        shootInfo.properties.put("autoTraceRadius", 24d);
        shootInfo.properties.put("autoTraceSharpTurning", false);
        shootInfo.properties.put("autoTraceAbility", 0.075);
        shootInfo.properties.put("liveTime", 100);
        shootInfo.properties.put("gravity", 0d);
        shootInfo.properties.put("projectileSize", 0.25);
        EntityHelper.spawnProjectile(shootInfo);
    }
    private void spawnHungry() {
        for (int i = 0; i < 1; i ++) {
            MonsterHelper.spawnMob("饿鬼", bukkitEntity.getLocation().add(
                    Math.random() * 4 - 2, Math.random() * 4 - 2, Math.random() * 4 - 2), target);
        }
    }
    private void toggleEnraged(boolean newEnragedState) {
        if (this.enraged == newEnragedState)
            return;
        this.enraged = newEnragedState;
        if (enraged) {
            bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1);
            bossbar.color = BossBattle.BarColor.RED;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        }
        else {
            bossbar.color = BossBattle.BarColor.GREEN;
            bossbar.sendUpdate(PacketPlayOutBoss.Action.UPDATE_STYLE);
        }
    }
    private void AI() {
        // no AI after death
        if (getHealth() <= 0d)
            return;
        // AI
        {
            // update target
            target = terraria.entity.boss.BossHelper.updateBossTarget(target, getBukkitEntity(),
                    IGNORE_DISTANCE, BIOME_REQUIRED, targetMap.keySet());
            // disappear if no target is available
            if (target == null) {
                getAttributeInstance(GenericAttributes.maxHealth).setValue(1);
                die();
                return;
            }
            // increase player aggro duration
            targetMap.get(target.getUniqueId()).addAggressionTick();
            // AI
            if (ticksLived % 3 == 0) {
                double healthRatio = getHealth() / getMaxHealth();
                double moveSpeedMultiplier;
                {
                    if (enraged)
                        moveSpeedMultiplier = 1.25;
                    else if (healthRatio < 0.025)
                        moveSpeedMultiplier = 1;
                    else if (healthRatio < 0.035)
                        moveSpeedMultiplier = 0.75;
                    else if (healthRatio < 0.05)
                        moveSpeedMultiplier = 0.6;
                    else if (healthRatio < 0.25)
                        moveSpeedMultiplier = 0.5;
                    else if (healthRatio < 0.33)
                        moveSpeedMultiplier = 0.4;
                    else if (healthRatio < 0.5)
                        moveSpeedMultiplier = 0.3;
                    else if (healthRatio < 0.66)
                        moveSpeedMultiplier = 0.25;
                    else if (healthRatio < 0.75)
                        moveSpeedMultiplier = 0.2;
                    else
                        moveSpeedMultiplier = 0.15;
                }
                // movement of all parts of boss
                {
                    Location targetLoc = bukkitEntity.getLocation()
                            .add(horizontalMoveDirection.clone().multiply(3 * MAX_HORIZONTAL_SPD * moveSpeedMultiplier));
                    // get target Y
                    double targetY = target.getLocation().getY();
                    double eyeSpace;
                    {
                        int ceilHeightOffset, floorHeightOffset;
                        Location targetYLoc = bukkitEntity.getLocation();
                        targetYLoc.setY(targetY);
                        Block locBlock = targetYLoc.getBlock();
                        for (ceilHeightOffset = 0; ceilHeightOffset < 255; ceilHeightOffset ++) {
                            if (locBlock.getRelative(0, ceilHeightOffset, 0).getType().isSolid())
                                break;
                        }
                        for (floorHeightOffset = 0; floorHeightOffset > -255; floorHeightOffset --) {
                            if (locBlock.getRelative(0, floorHeightOffset, 0).getType().isSolid())
                                break;
                        }
                        eyeSpace = Math.max((double) (ceilHeightOffset - floorHeightOffset) / 4, MIN_EYE_SPACE);
                        targetY += (double) (ceilHeightOffset + floorHeightOffset) / 2;
                    }
                    targetLoc.setY( targetLoc.getY() * (1 - Y_COORD_ADJUST_RATE) + targetY * Y_COORD_ADJUST_RATE );
                    Vector velocity;
                    // mouth
                    velocity = targetLoc.clone().subtract(bukkitEntity.getLocation()).toVector();
                    velocity.multiply(1d / 3);
                    bukkitEntity.setVelocity(velocity);
                    // top eye
                    velocity = targetLoc.clone().add(0, eyeSpace, 0).subtract(bossParts.get(1).getLocation()).toVector();
                    velocity.multiply(1d / 3);
                    bossParts.get(1).setVelocity(velocity);
                    // bottom eye
                    velocity = targetLoc.clone().add(0, -eyeSpace, 0).subtract(bossParts.get(2).getLocation()).toVector();
                    velocity.multiply(1d / 3);
                    bossParts.get(2).setVelocity(velocity);
                }
                // apply fear to players within 100 blocks; drag players behind the wall
                // also, enrage if a targeted player is too far away.
                if (indexAI % 5 == 0) {
                    for (Player ply : bukkitEntity.getWorld().getPlayers()) {
                        if (!PlayerHelper.isProperlyPlaying(ply))
                            continue;
                        Vector horDir = ply.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                        horDir.setY(0);
                        double distSqr = horDir.lengthSquared();
                        if (distSqr > 10000)
                            continue;
                        EntityHelper.applyEffect(ply, "恐惧", 1200);
                        // enrage?
                        if (targetMap.containsKey(ply) && distSqr > ENRAGE_DIST_SQR) {
                            toggleEnraged(true);
                            enragedCounter = 25;
                        }
                        // behind wall of flesh?
                        if (horDir.dot(horizontalMoveDirection) < 0) {
                            EntityHelper.handleDamage(bukkitEntity, ply, 100, EntityHelper.DamageReason.DIRECT_DAMAGE);
                            Vector dragDir = bukkitEntity.getLocation().subtract(ply.getLocation()).toVector();
                            double dragLen = dragDir.length();
                            if (dragLen > 1e-5) {
                                dragDir.multiply(2 / dragLen);
                                EntityHelper.knockback(ply, dragDir, false);
                            }
                        }
                    }
                }
                // spawn hungry
                if (indexAI % 50 == 0)
                    spawnHungry();
                // shoot sickles
                if ( (healthRatio < 0.66 && indexAI % 65 == 0) || (healthRatio < 0.33 && indexAI % 40 == 0))
                    spawnDemonScythe();

                indexAI ++;
                if (--enragedCounter < 0)
                    toggleEnraged(false);
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public WallOfFleshMouth(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return NPCHelper.NPCMap.containsKey(NPCHelper.NPCType.GUIDE) && !( NPCHelper.NPCMap.get(NPCHelper.NPCType.GUIDE).isDead() );
    }
    // a constructor for actual spawning
    public WallOfFleshMouth(Location burntItemLocation) {
        super( ((CraftWorld) burntItemLocation.getWorld()).getHandle() );
        // attempt to summon wall of flesh kills the guide
        LivingEntity guideNPC = NPCHelper.NPCMap.get(NPCHelper.NPCType.GUIDE);
        if (guideNPC == null || guideNPC.isDead()) {
            getAttributeInstance(GenericAttributes.maxHealth).setValue(1);
            die();
            return;
        }
        guideNPC.getLocation().getChunk().load();
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
            LivingEntity NpcGuide = NPCHelper.NPCMap.get(NPCHelper.NPCType.GUIDE);
            // make sure guide will not survive this damage
            NpcGuide.setHealth(1d);
            EntityHelper.handleDamage(NpcGuide, NpcGuide, 114514d, EntityHelper.DamageReason.LAVA);
        }, 1);
        // target player
        Player summonedPlayer = null;
        double nearestPlyDistSqr = 1600;
        for (Player ply : burntItemLocation.getWorld().getPlayers()) {
            if (!PlayerHelper.isProperlyPlaying(ply))
                continue;
            double distSqr = ply.getLocation().distanceSquared(burntItemLocation);
            if (distSqr < nearestPlyDistSqr) {
                nearestPlyDistSqr = distSqr;
                summonedPlayer = ply;
            }
        }
        if (summonedPlayer == null) {
            getAttributeInstance(GenericAttributes.maxHealth).setValue(1);
            die();
            return;
        }
        // spawn location
        horizontalMoveDirection = summonedPlayer.getLocation().subtract(burntItemLocation).toVector();
        horizontalMoveDirection.setY(0);
        horizontalMoveDirection.normalize();
        Location spawnLoc = summonedPlayer.getLocation().subtract(horizontalMoveDirection.clone().multiply(24));
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName + "§1");
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
            attrMap.put("damage", 450d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("damageTakenMulti", 0.5d);
            attrMap.put("defence", 36d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_BAR, bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), "", summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(8, false);
            double healthMulti = terraria.entity.boss.BossHelper.getBossHealthMulti(targetMap.size());
            double health = BASIC_HEALTH * healthMulti;
            getAttributeInstance(GenericAttributes.maxHealth).setValue(health);
            setHealth((float) health);
        }
        // boss parts and other properties
        {
            bossParts = new ArrayList<>();
            bossParts.add((LivingEntity) bukkitEntity);
            BossHelper.bossMap.put(BOSS_TYPE.msgName, bossParts);
            this.noclip = true;
            this.setNoGravity(true);
            this.persistent = true;
            // summon the two eyes
            new WallOfFleshEye(summonedPlayer, bossParts);
            new WallOfFleshEye(summonedPlayer, bossParts);
            spawnHungry();
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        // this function is called elsewhere before boss bar is set up, so it is necessary to check if the bar is valid
        if (bossbar != null)
            bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            // send loot
            terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
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
        // update boss bar and dynamic DR
        terraria.entity.boss.BossHelper.updateBossBarAndDamageReduction(bossbar, bossParts, BOSS_TYPE);
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
