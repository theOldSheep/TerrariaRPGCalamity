package terraria.entity.boss.thePlaguebringerGoliath;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftChatMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import terraria.entity.boss.astrumAureus.AureusSpawn;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class ThePlaguebringerGoliath extends EntitySlime {
    // basic variables
    public static final BossHelper.BossType BOSS_TYPE = BossHelper.BossType.ASTRUM_AUREUS;
    public static final WorldHelper.BiomeType BIOME_REQUIRED = WorldHelper.BiomeType.ASTRAL_INFECTION;
    public static final double BASIC_HEALTH = 255600 * 2;
    public static final boolean IGNORE_DISTANCE = false;
    HashMap<String, Double> attrMap;
    HashMap<Player, Double> targetMap;
    ArrayList<LivingEntity> bossParts;
    BossBattleServer bossbar;
    Player target = null;
    // other variables and AI
    enum AIPhase {
        RECHARGE, CRAWL, JUMP;
    }
    AIPhase phaseAI = AIPhase.RECHARGE;
    int indexAI = -40, attacksDuringPhase = 0;
    double healthRatio = 1;
    static HashMap<String, Double> attrMapStingerMissile, attrMapNukeBarrage;
    static final double SPEED_NORMAL = 2.25, SPEED_DASH = 3;
    EntityHelper.ProjectileShootInfo shootInfoStinger, shootInfoMissile, shootInfoNukeBarrage;
    static {
        attrMapStingerMissile = new HashMap<>();
        attrMapStingerMissile.put("damage", 540d);
        attrMapStingerMissile.put("knockback", 1.5d);
        attrMapNukeBarrage = new HashMap<>();
        attrMapNukeBarrage.put("damage", 660d);
        attrMapNukeBarrage.put("knockback", 2d);
    }
    private void changePhase() {
        // change phase
        switch (phaseAI) {
            case JUMP:
                if (attacksDuringPhase > 5)
                    phaseAI = AIPhase.RECHARGE;
                else
                    return;
                break;
            case RECHARGE:
                phaseAI = AIPhase.CRAWL;
                break;
            case CRAWL:
                phaseAI = AIPhase.JUMP;
                break;
        }
        // aftermath
        indexAI = -1;
        attacksDuringPhase = 0;
    }

    private Vector getHorizontalDirection() {
        Location targetLoc = target.getLocation();
        Location currLoc = bukkitEntity.getLocation();
        targetLoc.setY(currLoc.getY());
        return MathHelper.getDirection(currLoc, targetLoc, 1);
    }

    private void AIPhaseJump() {

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
                healthRatio = getHealth() / getMaxHealth();
                switch (phaseAI) {
                    case RECHARGE:
                        AIPhaseRecharge();
                        break;
                    case CRAWL:
                        AIPhaseCrawl();
                        break;
                    case JUMP:
                        AIPhaseJump();
                        break;
                }
                indexAI ++;
            }
        }
        // face the player
        // TODO: phase dashing direction when dashing
        this.yaw = (float) MathHelper.getVectorYaw( target.getLocation().subtract(bukkitEntity.getLocation()).toVector() );
        // collision dmg
        if (phaseAI != AIPhase.RECHARGE)
            terraria.entity.boss.BossHelper.collisionDamage(this);
    }
    // default constructor to handle chunk unload
    public ThePlaguebringerGoliath(World world) {
        super(world);
        super.die();
    }
    // validate if the condition for spawning is met
    public static boolean canSpawn(Player player) {
        return WorldHelper.BiomeType.getBiome(player) == BIOME_REQUIRED;
    }
    // a constructor for actual spawning
    public ThePlaguebringerGoliath(Player summonedPlayer) {
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
        bukkitEntity.addScoreboardTag("isMonster");
        bukkitEntity.addScoreboardTag("isBOSS");
        EntityHelper.setMetadata(bukkitEntity, "bossType", BOSS_TYPE);
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
            EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        }
        // init boss bar
        bossbar = new BossBattleServer(CraftChatMessage.fromString(BOSS_TYPE.msgName, true)[0],
                BossBattle.BarColor.GREEN, BossBattle.BarStyle.PROGRESS);
        EntityHelper.setMetadata(bukkitEntity, "bossbar", bossbar);
        // init target map
        {
            targetMap = terraria.entity.boss.BossHelper.setupBossTarget(
                    getBukkitEntity(), BossHelper.BossType.PLANTERA.msgName, summonedPlayer, true, bossbar);
            target = summonedPlayer;
            EntityHelper.setMetadata(bukkitEntity, "targets", targetMap);
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
            shootInfoStinger = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapStingerMissile,
                    EntityHelper.DamageType.ARROW, "瘟疫导弹");
            shootInfoMissile = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapStingerMissile,
                    EntityHelper.DamageType.ROCKET, "制导瘟疫弹");
            shootInfoNukeBarrage = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMapNukeBarrage,
                    EntityHelper.DamageType.ROCKET, "瘟疫核弹");
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
