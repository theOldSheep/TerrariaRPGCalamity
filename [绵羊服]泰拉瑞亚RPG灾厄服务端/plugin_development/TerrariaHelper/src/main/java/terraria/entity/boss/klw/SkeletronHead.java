package terraria.entity.boss.klw;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;

public class SkeletronHead extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.SKELETRON;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 11220;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int indexAI = 0;
    boolean spinning = false, extraHandsSpawned = false;
    Location teleportLoc = null;
    static final double SPINNING_DMG = 342, REGULAR_DMG = 132;
    static HashMap<String, Double> skull_attrMap, shadow_flame_attrMap;
    static {
        skull_attrMap = new HashMap<>();
        skull_attrMap.put("damage", 252d);
        skull_attrMap.put("damageMulti", 1d);
        shadow_flame_attrMap = new HashMap<>();
        shadow_flame_attrMap.put("damage", 228d);
        shadow_flame_attrMap.put("damageMulti", 1d);
    }
    private void shootSkull() {
        if (target == null) return;
        Vector velocity = target.getEyeLocation().subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
        double velLen = velocity.length();
        if (velLen < 1e-5) {
            velLen = 1d;
            velocity = new Vector(0, 1, 0);
        }
        velocity.multiply(1.75 / velLen);
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, velocity, skull_attrMap, "--");
        shootInfo.projectileName = "诅咒头";
        shootInfo.properties.put("autoTrace", true);
        shootInfo.properties.put("autoTraceMethod", 2);
        shootInfo.properties.put("autoTraceRadius", 24d);
        shootInfo.properties.put("autoTraceSharpTurning", false);
        shootInfo.properties.put("autoTraceAbility", 0.5);
        shootInfo.properties.put("noAutoTraceTicks", 5);
        shootInfo.properties.put("liveTime", 80);
        shootInfo.properties.put("gravity", 0d);
        shootInfo.properties.put("blockHitAction", "thru");
        EntityHelper.spawnProjectile(shootInfo).setGlowing(true);
    }
    private void spitShadowFlame() {
        if (target == null) return;
        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                bukkitEntity, new Vector(), skull_attrMap, "--");
        shootInfo.projectileName = "诅咒骷髅头";
        shootInfo.properties.put("gravity", 0d);
        shootInfo.properties.put("blockHitAction", "thru");
        double offsetSize = 4.5;
        int i = -3, j = -3, k = -3;
        while (k <= 3) {
            i += 1 + (int) (Math.random() * 5);
            if (i > 3) {
                i -= 6;
                j ++;
            }
            if (j > 3) {
                j -= 6;
                k ++;
            }
            Vector velocity = target.getEyeLocation().add(j * offsetSize, i * offsetSize, k * offsetSize).subtract(((LivingEntity) bukkitEntity).getEyeLocation()).toVector();
            double velLen = velocity.length();
            if (velLen < 1e-5) {
                velLen = 1d;
                velocity = new Vector(0, 1, 0);
            }
            velocity.multiply(1.5 / velLen);
            shootInfo.velocity = velocity;
            EntityHelper.spawnProjectile(shootInfo).setGlowing(true);
        }
    }
    private void spawnHands(Player targetPlayer) {
        for (int i = 0; i < 4; i ++) {
            new SkeletronHand(targetPlayer, bossParts, this, i);
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
                for (org.bukkit.entity.LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // AI
            if (ticksLived % 3 == 0) {
                double healthRatio = getHealth() / getMaxHealth();
                // respawn hands on low health
                if (healthRatio < 0.25 && !extraHandsSpawned) {
                    spawnHands(target);
                    extraHandsSpawned = true;
                }
                int handsAlive = 0;
                // update attribute according to current state
                {
                    for (int i = 1; i < bossParts.size(); i++)
                        if (!bossParts.get(i).isDead()) handsAlive++;
                    // if hands are alive, the head should not be subject to auto-target etc.
                    if (handsAlive > 0) {
                        bukkitEntity.addScoreboardTag("noDamage");
                    } else {
                        bukkitEntity.removeScoreboardTag("noDamage");
                    }
                    if (WorldHelper.isDayTime(bukkitEntity.getWorld())) {
                        attrMap.put("defence", 9999d);
                        attrMap.put("damage", 9999d);
                    } else {
                        attrMap.put("damage", spinning ? SPINNING_DMG : REGULAR_DMG);
                    }
                }
                // attack
                // hand sweeping phase
                if (indexAI < 90) {
                    // teleport
                    switch (indexAI) {
                        // decide teleport location and spawn particle warning
                        case 70:
                            double angle = Math.random() * 360;
                            teleportLoc = target.getLocation().add(
                                    MathHelper.xsin_degree(angle) * 25,
                                    8 + Math.random() * 4,
                                    MathHelper.xcos_degree(angle) * 25);
                        case 75:
                        case 80:
                            bukkitEntity.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, teleportLoc, 1);
                            bukkitEntity.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, bukkitEntity.getLocation(), 1);
                            break;
                        // teleport
                        case 85:
                            ArrayList<Entity> toTeleport = new ArrayList<>(5);
                            for (org.bukkit.entity.LivingEntity loopEntity : bossParts)
                                if (loopEntity.getHealth() > 1e-5) toTeleport.add( ((CraftEntity) loopEntity).getHandle() );
                            for (Entity teleportedEntity : toTeleport) {
                                teleportedEntity.lastX = teleportLoc.getX();
                                teleportedEntity.lastY = teleportLoc.getY();
                                teleportedEntity.lastZ = teleportLoc.getZ();
                                teleportedEntity.getBukkitEntity().teleport(teleportLoc);
                            }
                            spitShadowFlame();
                            break;
                    }
                    // move
                    Vector vHead = target.getLocation().add(0, 8, 0).subtract(bukkitEntity.getLocation()).toVector();
                    vHead.multiply(1d / 25);
                    double vHeadLen = vHead.length();
                    if (vHeadLen > 0.7)
                        vHead.multiply(0.7 / vHeadLen);
                    bukkitEntity.setVelocity(vHead);
                    // shoot skull
                    boolean shouldShootSkull = false;
                    if (healthRatio > 0.4) {
                        if (indexAI % 10 == 0)
                            shouldShootSkull = handsAlive <= 3 || healthRatio < 0.8;
                    }
                    else {
                        shouldShootSkull = indexAI % 4 == 0;
                    }
                    if (shouldShootSkull) {
                        shootSkull();
                    }
                }
                // ready to spin
                else if (indexAI == 90) {
                    bukkitEntity.getWorld().playSound(bukkitEntity.getLocation(), "entity.enderdragon.growl", 10, 1f);
                    setCustomName(BOSS_TYPE.msgName + "§1");
                    spinning = true;
                }
                // spinning phase
                else {
                    Vector vHead = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                    vHead.multiply(1d / 12);
                    double vHeadLen = vHead.length();
                    if (vHeadLen > 1.5)
                        vHead.multiply(1.5d / vHeadLen);
                    bukkitEntity.setVelocity(vHead);
                    // return to hand sweeping phase
                    if (indexAI >= 170) {
                        indexAI = -1;
                        setCustomName(BOSS_TYPE.msgName);
                        spinning = false;
                    }
                }

                indexAI++;
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public SkeletronHead(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        if ( WorldHelper.isDayTime(player.getWorld()) ) return false;
        return true;
    }
    // a constructor for actual spawning
    public SkeletronHead(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        org.bukkit.entity.Entity clothier = NPCHelper.NPCMap.get("裁缝");
        if (clothier == null) {
            die();
            return;
        }
        Location spawnLoc = clothier.getLocation().add(0, 5, 0);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        clothier.remove();
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
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
            attrMap.put("damage", 264d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 20d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, "Melee");
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, "bossbar", targetMap);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), "", summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
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
            spawnHands(summonedPlayer);
        }
    }

    // disable death function to remove boss bar
    @Override
    public void die() {
        super.die();
        // disable boss bar
        bossbar.setVisible(false);
        BossHelper.bossMap.remove(BOSS_TYPE.msgName);
        // if the boss has been defeated properly
        if (getMaxHealth() > 10) {
            // drop items
            terraria.entity.monster.MonsterHelper.handleMonsterDrop((LivingEntity) bukkitEntity);

            Bukkit.broadcastMessage("§d§l" + BOSS_TYPE.msgName + " 被击败了.");
            // send out loot
            double[] healthInfo = terraria.entity.boss.BossHelper.getHealthInfo(bossParts);
            double dmgDealtReq = healthInfo[1] / targetMap.size() / 10;
            ItemStack loopBag = ItemHelper.getItemFromDescription( BOSS_TYPE.msgName + "的 专家模式福袋");
            for (Player ply : targetMap.keySet()) {
                if (targetMap.get(ply) >= dmgDealtReq) {
                    ply.sendMessage("§a恭喜你击败了BOSS[§r" + BOSS_TYPE.msgName + "§a]!");
                    PlayerHelper.setDefeated(ply, BOSS_TYPE.msgName, true);
                    PlayerHelper.giveItem(ply, loopBag, true);
                }
                else {
                    ply.sendMessage("§aBOSS " + BOSS_TYPE.msgName + " 已经被击败。很遗憾，您的输出不足以获得一份战利品。");
                }
            }
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
