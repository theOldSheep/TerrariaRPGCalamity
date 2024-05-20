package terraria.entity.boss.crabulon;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.util.MathHelper;
import terraria.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class Crabulon extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.CRABULON;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.JUNGLE;
    public static final double BASIC_HEALTH = 9600 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    int indexAI = 0;
    boolean lastOnGround = true;
    static final HashMap<String, Double> attrMapSpore, attrMapShroom;
    EntityHelper.ProjectileShootInfo psiSpore, psiShroom;
    HashSet<Entity> shrooms = new HashSet<>();
    static {
        attrMapSpore = new HashMap<>();
        attrMapSpore.put("damage", 180d);
        attrMapShroom = new HashMap<>();
        attrMapShroom.put("damage", 150d);
        attrMapShroom.put("health", 90d);
        attrMapShroom.put("healthMax", 90d);
    }
    private boolean isOnGround() {
        if (noclip) return false;
        return (onGround ||
                bukkitEntity.getLocation().subtract(0, 0.25, 0).getBlock().getType() != org.bukkit.Material.AIR) && !noclip;
    }
    private void shootProjectiles() {
        psiSpore.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        psiShroom.shootLoc = ((LivingEntity) bukkitEntity).getEyeLocation();
        // shoot spores
        double yaw = MathHelper.getVectorYaw(target.getLocation().subtract(psiSpore.shootLoc).toVector());
        for (int i = 0; i < 30; i ++) {
            Vector velocity = MathHelper.vectorFromYawPitch_approx(yaw + (Math.random() * 90 - 45), -90 + Math.random() * 60);
            velocity.multiply(1.25 + Math.random());
            psiSpore.velocity = velocity;
            EntityHelper.spawnProjectile(psiSpore);
        }
        // shoot shrooms
        for (int i = 0; i < 15; i ++) {
            Vector velocity = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, -90 + Math.random() * 30);
            velocity.multiply(1 + Math.random());
            psiShroom.velocity = velocity;
            shrooms.add( EntityHelper.spawnProjectile(psiShroom) );
        }
    }
    private void handleShroomFollow() {
        ArrayList<Entity> toRemove = new ArrayList<>();
        for (Entity shroom : shrooms) {
            if (shroom.isDead()) {
                toRemove.add(shroom);
                continue;
            }
            Vector velocity = shroom.getVelocity();
            if (ticksLived % 30 != 0) {
                velocity.setY( Math.max(velocity.getY(), -0.125) );
            }
            else {
                Vector offset = target.getLocation().subtract(shroom.getLocation()).toVector();
                offset.setY(0);
                if (offset.lengthSquared() < 1e-5) {
                    continue;
                }
                double offsetLen = offset.length();
                double velLen = velocity.length();
                offset.multiply( velLen * 2 / offsetLen);
                velocity.add(offset);
                velLen = velocity.length();
                if (velLen > 1) {
                    velocity.multiply(1 / velLen);
                }
                velocity.setY( Math.max(velocity.getY(), -0.25) );
            }
            shroom.setVelocity(velocity);
        }
        for (Entity deadShroom : toRemove) {
            shrooms.remove(deadShroom);
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
                for (LivingEntity entity : bossParts) {
                    entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    entity.remove();
                }
                return;
            }
            // if target is valid, attack
            else {
                // increase player aggro duration
                targetMap.get(target.getUniqueId()).addAggressionTick();
                // AI
                handleShroomFollow();
                if (ticksLived % 2 == 0) {
                    boolean onGround = isOnGround();
                    if (onGround) {
                        // upon landing, shoot projectiles
                        if (!lastOnGround) {
                            shootProjectiles();
                            lastOnGround = true;
                        }
                        // jump
                        if (indexAI >= 10) {
                            Vector velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                            velocity.setY(0);
                            velocity.normalize().multiply(1.75);
                            velocity.setY(1.6);
                            bukkitEntity.setVelocity(velocity);
                            indexAI = 0;
                            noclip = true;
                            lastOnGround = false;
                        }
                        // move towards target
                        else {
                            Vector velocity = target.getLocation().subtract(bukkitEntity.getLocation()).toVector();
                            velocity.setY(0);
                            velocity.normalize().multiply(0.75);
                            bukkitEntity.setVelocity(velocity);
                        }
                        // add 1 to index
                        indexAI++;
                    }
                    else {
                        if (!lastOnGround) {
                            this.noclip = motY > 0 || this.locY > target.getEyeLocation().getY() + 5;
                        }
                        else {
                            this.noclip = false;
                        }
                    }
                }
            }
        }
        // face the player
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Crabulon(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.HeightLayer.getHeightLayer(player.getLocation()) == WorldHelper.HeightLayer.CAVERN &&
                WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public Crabulon(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 25;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
        setCustomNameVisible(true);
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TYPE, BOSS_TYPE);
        goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        goalSelector.a(0, new PathfinderGoalFloat(this));
        targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // init attribute map
        {
            attrMap = new HashMap<>();
            attrMap.put("crit", 0.04);
            attrMap.put("damage", 240d);
            attrMap.put("damageMeleeMulti", 1d);
            attrMap.put("damageMulti", 1d);
            attrMap.put("defence", 16d);
            attrMap.put("defenceMulti", 1d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
            attrMap.put("knockbackMeleeMulti", 1d);
            attrMap.put("knockbackMulti", 1d);
            EntityHelper.setDamageType(bukkitEntity, EntityHelper.DamageType.MELEE);
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        }
        // init projectile shoot info
        {
            psiSpore = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapSpore,
                    EntityHelper.DamageType.ARROW, "-");
            psiSpore.projectileName = "孢子";
            psiShroom = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapShroom,
                    EntityHelper.DamageType.ARROW, "-");
            psiShroom.projectileName = "孢子";
            psiShroom.properties.put("gravity", 0.01);
            psiShroom.properties.put("blockHitAction", "thru");
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
            setSize(10, false);
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
            this.persistent = true;
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
            // send loot
            terraria.entity.boss.BossHelper.handleBossDeath(BOSS_TYPE, bossParts, targetMap);
        }
    }
    // rewrite AI
    @Override
    public void B_() {
        super.B_();
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
