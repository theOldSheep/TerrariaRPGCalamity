package terraria.entity.npc;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.util.*;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class TerrariaNPC extends EntityVillager {
    public String NPCType;
    public HashMap<String, Double> attrMap;
    public HashSet<Player> GUIViewers = new HashSet<>();

    public TerrariaNPC(World world) {
        super(world);
        die();
    }
    public TerrariaNPC(World world, String type) {
        super(world);
        Location spawnLoc = world.getWorld().getHighestBlockAt((int) (Math.random() * 64 - 32), (int) (Math.random() * 64 - 32)).getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        initTypeInfo(type);
    }
    protected void initTypeInfo(String type) {
        // navigation
        this.getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(72);
        // pathfinders
        this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);

        this.goalSelector.a(0, new PathfinderGoalFloat(this));
        this.goalSelector.a(0, new PathfinderGoalNPCRestrictLocation(this));
        this.goalSelector.a(2, new PathfinderGoalAvoidTarget(this, EntityLiving.class,
                (enemy) -> EntityHelper.checkCanDamage( ((EntityLiving) enemy).getBukkitEntity(), this.getBukkitEntity(), true),
                9.0F, 0.5, 0.75));
        this.goalSelector.a(3, new PathfinderGoalRandomStrollLand(this, 0.6));
        this.goalSelector.a(4, new PathfinderGoalMoveIndoors(this));
        this.goalSelector.a(5, new PathfinderGoalRestrictOpenDoor(this));
        this.goalSelector.a(6, new PathfinderGoalOpenDoor(this, true));
        this.goalSelector.a(8, new PathfinderGoalInteractVillagers(this));
        this.goalSelector.a(9, new PathfinderGoalLookAtPlayer(this, EntityInsentient.class, 12.0F));
        this.goalSelector.a(11, new PathfinderGoalRandomLookaround(this));
        // other variables and attributes etc.
        this.NPCType = type;
        this.ageLocked = true;
        EntityHelper.setMetadata(bukkitEntity, "GUIViewers", GUIViewers);
        NPCHelper.NPCMap.put(type, bukkitEntity);
        this.setCustomName(type);
        this.setCustomNameVisible(true);
        this.bukkitEntity.addScoreboardTag("isNPC");
        this.persistent = true;
        // attributes, max health and profession
        attrMap = new HashMap<>();
        attrMap.put("damage", 25d);
        attrMap.put("damageTakenMulti", 0.75d);
        attrMap.put("defence", 40d);
        attrMap.put("crit", 4d);
        attrMap.put("invulnerabilityTick", 20d);
        attrMap.put("knockbackResistance", 0.5d);
        // attrMap and damage type
        switch (type) {
            case "向导": {
                attrMap.put("damage", 30d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Arrow");
                break;
            }
            case "渔夫": {
                attrMap.put("damage", 24d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Arrow");
                break;
            }
            case "建材商人": {
                attrMap.put("damage", 32d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Melee");
                break;
            }
            case "裁缝": {
                attrMap.put("damage", 48d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Magic");
                break;
            }
            case "军火商": {
                attrMap.put("damage", 72d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Bullet");
                break;
            }
            case "哥布林工匠": {
                attrMap.put("damage", 44d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Arrow");
                break;
            }
            case "爆破专家": {
                attrMap.put("damage", 60d);
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Arrow");
                break;
            }
            default:
                EntityHelper.setMetadata(bukkitEntity, "damageType", "Melee");
        }
        EntityHelper.setMetadata(bukkitEntity, "attrMap", attrMap);
        // health
        double maxHealth;
        switch (type) {
            case "至尊灾厄":
                maxHealth = 50000;
                break;
            case "海王":
                maxHealth = 2500;
                break;
            default:
                maxHealth = 1000;
        }
        ((LivingEntity) bukkitEntity).getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        this.setHealth((float) maxHealth);
        // profession
        switch (type) {
            // librarian/cartographer
            case "向导":
            case "护士":
            case "建材商人":
            case "裁缝":
                setProfession(1);
                break;
            // cleric
            case "爆破专家":
                setProfession(2);
                break;
            // blacksmith
            case "军火商":
            case "哥布林工匠":
                setProfession(3);
                break;
            // nitwit
            case "渔夫":
                setAge(0);
                setProfession(5);
                break;
        }
    }

    protected void attackAttempt() {
        // normal projectile attack
        int shootInterval = -1;
        switch (NPCType) {
            case "向导":
            case "渔夫":
            {
                shootInterval = 12;
                break;
            }
            case "裁缝":
            {
                shootInterval = 35;
                break;
            }
            case "军火商":
            {
                shootInterval = 25;
                break;
            }
            case "建材商人":
            case "哥布林工匠":
            {
                shootInterval = 20;
                break;
            }
            case "爆破专家":
            {
                shootInterval = 50;
                break;
            }
        }
        if (shootInterval != -1 && ticksLived % shootInterval == 0) {
            List<Entity> toLoop = getWorld().getEntities(null, getBoundingBox().g(24d),
                    (entity) -> EntityHelper.checkCanDamage(this.getBukkitEntity(), entity.getBukkitEntity(), true));
            double minDistance;
            switch (NPCType) {
                case "建材商人":
                    minDistance = 5 * 5;
                    break;
                case "渔夫":
                    minDistance = 16 * 16;
                    break;
                case "军火商":
                    minDistance = 48 * 48;
                    break;
                default:
                    minDistance = 24 * 24;
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
                Vector dir = MathHelper.getDirection(((LivingEntity) bukkitEntity).getEyeLocation(), finalTarget.getEyeLocation(), 1d);
                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(bukkitEntity, dir, attrMap, "");
                shootInfo.properties.put("liveTime", 80);
                switch (NPCType) {
                    case "建材商人": {
                        GenericHelper.handleStrikeLine(bukkitEntity, ((LivingEntity) bukkitEntity).getEyeLocation(),
                                MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir), 6.0, 0.25,
                                "", "150|150|0", new ArrayList<>(),
                                (HashMap<String, Double>) attrMap.clone(), new GenericHelper.StrikeLineOptions());
                        return;
                    }
                    case "向导": {
                        shootInfo.projectileName = "木箭";
                        shootInfo.velocity.multiply(1.5);
                        break;
                    }
                    case "渔夫": {
                        shootInfo.projectileName = "木箭";
                        shootInfo.properties.put("penetration", 2);
                        break;
                    }
                    case "裁缝": {
                        shootInfo.projectileName = "骷髅头";
                        shootInfo.velocity.multiply(1.25);
                        shootInfo.properties.put("gravity", 0d);
                        shootInfo.properties.put("penetration", 2);
                        break;
                    }
                    case "军火商": {
                        shootInfo.projectileName = "火枪子弹";
                        shootInfo.velocity.multiply(2);
                        shootInfo.properties.put("gravity", 0d);
                        break;
                    }
                    case "哥布林工匠": {
                        shootInfo.projectileName = "尖刺球";
                        shootInfo.velocity.multiply(0.35);
                        shootInfo.properties.put("noGravityTicks", 0);
                        shootInfo.properties.put("penetration", 9);
                        shootInfo.properties.put("blockHitAction", "bounce");
                        shootInfo.properties.put("bounce", 999999);
                        shootInfo.properties.put("frictionFactor", 0.5);
                        shootInfo.properties.put("liveTime", 240);
                        break;
                    }
                    case "爆破专家": {
                        shootInfo.projectileName = "手榴弹";
                        shootInfo.velocity.multiply(0.75);
                        shootInfo.properties.put("noGravityTicks", 0);
                        shootInfo.properties.put("blockHitAction", "bounce");
                        shootInfo.properties.put("bounce", 999999);
                        shootInfo.properties.put("frictionFactor", 0.5);
                        shootInfo.properties.put("isGrenade", true);
                        shootInfo.properties.put("blastRadius", 2.75);
                        shootInfo.properties.put("liveTime", 40);
                        break;
                    }
                }
                EntityHelper.spawnProjectile(shootInfo);
            }
        }
        // other attack type
        switch (NPCType) {
            case "护士": {
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
        if (EntityHelper.getMetadata(bukkitEntity, "attrMap") == null)
            initTypeInfo(NPCType);
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
        for (Player ply : GUIViewers) {
            ply.closeInventory();
        }
    }
}