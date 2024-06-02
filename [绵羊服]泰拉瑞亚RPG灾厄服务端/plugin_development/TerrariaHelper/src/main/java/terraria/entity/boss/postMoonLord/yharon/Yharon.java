package terraria.entity.boss.postMoonLord.yharon;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.EntityType;
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

public class Yharon extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.YHARON_DRAGON_OF_REBIRTH;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = null;
    public static final double BASIC_HEALTH = 255600 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    static HashMap<String, Double> attrMapProjectile;
    EntityHelper.ProjectileShootInfo shootInfo;

    static {
        attrMapProjectile = new HashMap<>();
        attrMapProjectile.put("damage", 540d);
        attrMapProjectile.put("knockback", 1.5d);
    }

    int phase = 1;
    Vector velocity = new Vector(0, 0, 0);
    LivingEntity entity = (LivingEntity) getBukkitEntity();
    private int phaseTick = 0;
    private int phaseStep = 1;


    TornadoSlime tornado;




    public void phase1() {
        switch (this.phaseStep) {
            case 1:
                this.charge();
                if (this.phaseTick > 40) {
                    this.phaseStep = 2;
                    this.phaseTick = 0;
                }
                break;
            case 2:
                this.teleport(this.target.getLocation().add(0, 2, 0));
                this.fireBlasts();
                if (this.phaseTick > 20) {
                    this.phaseStep = 3;
                    this.phaseTick = 0;
                }
                break;
            case 3:
                if (this.phaseTick > 60) {
                    this.phaseStep = 4;
                    this.phaseTick = 0;
                }
                break;
            case 4:
                this.charge();
                if (this.phaseTick > 40) {
                    this.phaseStep = 5;
                    this.phaseTick = 0;
                }
                break;
            case 5:
                this.flyLoop();
                if (this.phaseTick > 100) {
                    this.phaseStep = 6;
                    this.phaseTick = 0;
                }
                break;
            case 6:
                this.fireball();
                if (this.phaseTick == 1) {
                    tornado = new TornadoSlime(bukkitEntity.getLocation());
                }
                tornado.update();
                // Flame tornado behavior will go here
                if (this.phaseTick > 80) {
                    this.phaseStep = 1;
                    this.phaseTick = 0;
                }
                break;
        }
        this.phaseTick++;
    }
    private void teleport(Location location) {
        this.setPosition(location.getX(), location.getY(), location.getZ());
    }
    private void charge() {
        this.velocity = this.target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.5);
    }
    private void fireBlasts() {
        for (int i = 0; i < 10; i++) {
            Location loc = entity.getLocation();
            Vector dir = this.target.getLocation().toVector().subtract(loc.toVector()).normalize();
            loc.getWorld().spawnEntity(loc.add(dir.multiply(2)), EntityType.FIREBALL);
        }
    }
    private void flyLoop() {
        Location loc = entity.getLocation();
        double angle = Math.toRadians(this.phaseTick);
        Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
        this.velocity = dir.multiply(0.5);
    }
    private void fireball() {
        Location loc = entity.getLocation();
        Vector dir = this.target.getLocation().toVector().subtract(loc.toVector()).normalize();
        loc.getWorld().spawnEntity(loc.add(dir.multiply(2)), EntityType.FIREBALL);
    }



    public void tick() {
        // Update velocity
//        this.velocity.setY(this.velocity.getY() - 0.05);
        this.setPosition(this.locX + this.velocity.getX(), this.locY + this.velocity.getY(), this.locZ + this.velocity.getZ());

        // Update phase
        double healthPercentage = (this.getHealth() / this.getMaxHealth()) * 100;
        if (healthPercentage < 80 && this.phase == 1) {
            this.phase = 2;
            System.out.println("Phase 2");
            this.phaseStep = 1;
            this.phaseTick = 0;
        } else if (healthPercentage < 50 && this.phase == 2) {
            this.phase = 3;
            System.out.println("Phase 3");
        } else if (healthPercentage < 35 && this.phase == 3) {
            this.phase = 4;
            System.out.println("Phase 4");
        } else if (healthPercentage < 16 && this.phase == 4) {
            this.phase = 5;
            System.out.println("Phase 5");
        }

        if (this.phase == 1) {
            this.phase1();
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

                // TODO
                tick();
            }
        }
        // facing
        if (false)
            this.yaw = (float) MathHelper.getVectorYaw( velocity );
        else
            this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public Yharon(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return true;
    }
    // a constructor for actual spawning
    public Yharon(Player summonedPlayer) {
        super( ((CraftPlayer) summonedPlayer).getHandle().getWorld() );
        // spawn location
        double angle = Math.random() * 720d, dist = 40;
        Location spawnLoc = summonedPlayer.getLocation().add(
                MathHelper.xsin_degree(angle) * dist, 0, MathHelper.xcos_degree(angle) * dist);
        spawnLoc.setY( spawnLoc.getWorld().getHighestBlockYAt(spawnLoc) );
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        // add to world
        ((CraftWorld) summonedPlayer.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // basic characteristics
        setCustomName(BOSS_TYPE.msgName);
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
            attrMap.put("damage", 594d);
            attrMap.put("damageTakenMulti", 0.7);
            attrMap.put("defence", 100d);
            attrMap.put("knockback", 4d);
            attrMap.put("knockbackResistance", 1d);
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
                    getBukkitEntity(), BossHelper.BossType.MOON_LORD.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.BOSS_TARGET_MAP, targetMap);
        }
        // init health and slime size
        {
            setSize(16, false);
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
        }
        // shoot info's
        {
            shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapProjectile,
                    EntityHelper.DamageType.MAGIC, "projectile");
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