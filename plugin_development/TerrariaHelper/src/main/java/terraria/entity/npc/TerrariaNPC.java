package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

public class TerrariaNPC extends EntityVillager {
    public NPCHelper.NPCType NPCType;
    public HashMap<String, Double> attrMap;
    public HashSet<Player> GUIViewers = new HashSet<>();

    public TerrariaNPC(World world) {
        super(world);
        die();
    }
    public TerrariaNPC(World world, NPCHelper.NPCType type) {
        super(world);
        this.NPCType = type;
        Location spawnLoc = world.getWorld().getHighestBlockAt((int) (Math.random() * 32 - 16), (int) (Math.random() * 32 - 16)).getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        initTypeInfo();
    }

    protected void initTypeInfo() {
        // navigation
        this.getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(72);
        // pathfinders
        this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);

        this.goalSelector.a(0, new PathfinderGoalFloat(this));
        this.goalSelector.a(0, new PathfinderGoalNPCRestrictLocation(this));
        this.goalSelector.a(2, new PathfinderGoalAvoidTarget(this, EntityLiving.class,
                (enemy) -> DamageHelper.checkCanDamage( ((EntityLiving) enemy).getBukkitEntity(), this.getBukkitEntity(), true),
                9.0F, 0.5, 0.75));
        this.goalSelector.a(3, new PathfinderGoalRandomStrollLand(this, 0.6));
        this.goalSelector.a(4, new PathfinderGoalMoveIndoors(this));
        this.goalSelector.a(5, new PathfinderGoalRestrictOpenDoor(this));
        this.goalSelector.a(6, new PathfinderGoalOpenDoor(this, true));
        this.goalSelector.a(8, new PathfinderGoalInteractVillagers(this));
        this.goalSelector.a(9, new PathfinderGoalLookAtPlayer(this, EntityInsentient.class, 12.0F));
        this.goalSelector.a(11, new PathfinderGoalRandomLookaround(this));
        // other variables and attributes etc.
        this.ageLocked = true;
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.NPC_GUI_VIEWERS, GUIViewers);
        NPCHelper.NPCMap.put(NPCType, (LivingEntity) bukkitEntity);
        this.setCustomName(NPCType.displayName);
        this.setCustomNameVisible(true);
        this.bukkitEntity.addScoreboardTag("isNPC");
        this.persistent = true;
        // attributes, max health and profession
        attrMap = new HashMap<>();
        attrMap.put("damage", 25d);
        attrMap.put("damageTakenMulti", 0.5d);
        attrMap.put("defence", 40d);
        attrMap.put("crit", 4d);
        attrMap.put("invulnerabilityTick", 20d);
        attrMap.put("knockbackResistance", 0.5d);
        // attrMap and damage type
        switch (NPCType) {
            case GUIDE: {
                attrMap.put("damage", 30d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ARROW);
                break;
            }
            case ANGLER: {
                attrMap.put("damage", 24d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ARROW);
                break;
            }
            case BANDIT: {
                attrMap.put("damage", 27.5d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ROGUE);
                break;
            }
            case BLOCK_SELLER: {
                attrMap.put("damage", 32d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
                break;
            }
            case CLOTHIER: {
                attrMap.put("damage", 48d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MAGIC);
                break;
            }
            case ARMS_DEALER: {
                attrMap.put("damage", 72d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.BULLET);
                break;
            }
            case GOBLIN_TINKERER: {
                attrMap.put("damage", 44d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ARROW);
                break;
            }
            case DEMOLITIONIST: {
                attrMap.put("damage", 60d);
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.ARROW);
                break;
            }
            default:
                DamageHelper.setDamageType(bukkitEntity, DamageHelper.DamageType.MELEE);
        }
        EntityHelper.setMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        // health
        double maxHealth = 1000;
        ((LivingEntity) bukkitEntity).getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        this.setHealth((float) maxHealth);
        // profession & age
        setProfession(getProfession());
        setAgeRaw(0);
    }

    @Override
    public int getAge() {
        return this.NPCType == NPCHelper.NPCType.ANGLER ? -32767 : 0;
    }
    @Override
    public void setAgeRaw(int i) {
        int age = this.getAge();
        this.b = age;
        super.setAgeRaw(age);
    }

    @Override
    public int getProfession() {
        if (this.NPCType == null) return 0;
        // profession
        switch (this.NPCType) {
            // librarian/cartographer
            case GUIDE:
            case NURSE:
            case BLOCK_SELLER:
            case CLOTHIER:
                return 1;
            // librarian
            case BANDIT:
                return 2;
            // blacksmith
            case ARMS_DEALER:
            case GOBLIN_TINKERER:
                return 3;
            // butcher
            case DEMOLITIONIST:
                return 4;
            // nitwit
            case ANGLER:
                return 5;
        }
        return 0;
    }
    @Override
    public void setProfession(int i) {
        super.setProfession(this.getProfession());
    }

    protected void attackAttempt() {
        // normal projectile attack
        int shootInterval = -1;
        switch (NPCType) {
            case GUIDE:
            case ANGLER:
            case BANDIT:
            {
                shootInterval = 8;
                break;
            }
            case CLOTHIER:
            {
                shootInterval = 15;
                break;
            }
            case ARMS_DEALER:
            {
                shootInterval = 5;
                break;
            }
            case BLOCK_SELLER:
            {
                shootInterval = 12;
                break;
            }
            case DEMOLITIONIST:
            case GOBLIN_TINKERER:
            {
                shootInterval = 20;
                break;
            }
        }
        if (shootInterval != -1 && ticksLived % shootInterval == 0) {
            List<Entity> toLoop = getWorld().getEntities(null, getBoundingBox().g(24d),
                    (entity) -> DamageHelper.checkCanDamage(this.getBukkitEntity(), entity.getBukkitEntity(), true));
            double minDistance;
            switch (NPCType) {
                case BLOCK_SELLER:
                    minDistance = 6 * 6;
                    break;
                case ARMS_DEALER:
                    minDistance = 48 * 48;
                    break;
                case GOBLIN_TINKERER:
                    minDistance = 20 * 20;
                    break;
                default:
                    minDistance = 32 * 32;
            }
            org.bukkit.entity.LivingEntity finalTarget = null;
            for (Entity toCheck : toLoop) {
                if ( toCheck.getBukkitEntity() instanceof LivingEntity &&
                        ((LivingEntity) bukkitEntity).hasLineOfSight(toCheck.getBukkitEntity()) ) {
                    double currDist = toCheck.getBukkitEntity().getLocation().distanceSquared(this.getBukkitEntity().getLocation());
                    if (currDist < minDistance) {
                        minDistance = currDist;
                        finalTarget = (LivingEntity) (toCheck.getBukkitEntity());
                    }
                }
            }
            // attack final target
            if (finalTarget != null) {
                String projType = null;
                double projectileSpd = 1;
                switch (NPCType) {
                    case BLOCK_SELLER: {
                        Vector dir = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), finalTarget.getEyeLocation(), 1d);
                        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                                MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir), 6.0, 0.25,
                                "", "t/bls", new ArrayList<>(),
                                (HashMap<String, Double>) attrMap.clone(),
                                new GenericHelper.StrikeLineOptions()
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false));
                        return;
                    }
                    case GUIDE: {
                        projType = "木箭";
                        projectileSpd = 2;
                        break;
                    }
                    case ANGLER: {
                        projType = "木箭";
                        projectileSpd = 1.5;
                        break;
                    }
                    case BANDIT: {
                        projType = "钨钢飞刀";
                        projectileSpd = 2.25;
                        break;
                    }
                    case CLOTHIER: {
                        projType = "骷髅头";
                        projectileSpd = 1.25;
                        break;
                    }
                    case ARMS_DEALER: {
                        projType = "火枪子弹";
                        projectileSpd = 2.5;
                        break;
                    }
                    case GOBLIN_TINKERER: {
                        projType = "尖刺球";
                        projectileSpd = 0.75;
                        break;
                    }
                    case DEMOLITIONIST: {
                        projType = "手榴弹";
                        projectileSpd = 1.25;
                        break;
                    }
                }
                // help aim enemy
                AimHelper.AimHelperOptions aimHelper = new AimHelper.AimHelperOptions(projType)
                        .setProjectileSpeed(projectileSpd);
                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, new Vector(), attrMap, projType);
                Location aimLoc = AimHelper.helperAimEntity(shootInfo.shootLoc, finalTarget, aimHelper);
                shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, aimLoc, projectileSpd);

                EntityHelper.spawnProjectile(shootInfo);
            }
        }
        // other attack type
        switch (NPCType) {
            case NURSE: {
                if (ticksLived % 10 == 0) {
                    List<Entity> toLoop = getWorld().getEntities(null, getBoundingBox().g(32d),
                            (entity) -> entity.getBukkitEntity().getScoreboardTags().contains("isNPC"));
                    double minHealthRatio = 0.99999;
                    org.bukkit.entity.LivingEntity finalHealTarget = null;
                    for (Entity toCheck : toLoop) {
                        if (toCheck.getBukkitEntity() instanceof LivingEntity) {
                            LivingEntity toCheckBKT = (LivingEntity) toCheck.getBukkitEntity();
                            double healthRatio = toCheckBKT.getHealth() /
                                    toCheckBKT.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            // do not heal dead NPC!
                            if (healthRatio < minHealthRatio && healthRatio > 1e-9) {
                                minHealthRatio = healthRatio;
                                finalHealTarget = toCheckBKT;
                            }
                        }
                    }
                    if (finalHealTarget != null) {
                        PlayerHelper.heal(finalHealTarget, 25, true);
                    }
                }
                break;
            }
        }
    }
    @Override
    public void B_() {
        super.B_();
        // initialize the NPC again if the chunk reloaded
        if (EntityHelper.getMetadata(bukkitEntity, EntityHelper.MetadataName.ATTRIBUTE_MAP) == null)
            initTypeInfo();
        // remove duplicate
        if (ticksLived % 5 == 4 && NPCHelper.NPCMap.get(NPCType) != bukkitEntity) {
            die();
            return;
        }
        // do not move if trading
        if (GUIViewers.size() > 0) {
            getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0d);
        } else {
            getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.5d);
        }
        attackAttempt();
        this.ticksFarFromPlayer = 0;
        // health regen
        if (getHealth() > 0d && ticksLived % 15 == 0) {
            setHealth((float) Math.min(getHealth() + 1,
                    ((LivingEntity) bukkitEntity).getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
        }
    }
    @Override
    public void die() {
        super.die();
        if (GUIViewers != null)
            for (Player ply : GUIViewers) {
                ply.closeInventory();
            }
    }
}