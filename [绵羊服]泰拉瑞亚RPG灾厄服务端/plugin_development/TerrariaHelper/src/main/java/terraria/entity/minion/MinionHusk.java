package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class MinionHusk extends EntityZombieHusk {
    org.bukkit.entity.Player owner;
    int damageInvincibilityTicks, index, minionSlot, minionSlotMax;
    double basicDamage;
    org.bukkit.entity.Entity minionInList;
    boolean hasContactDamage, hasTeleported, protectOwner, sentryOrMinion, targetNeedLineOfSight;
    String minionType;
    ArrayList<Entity> damageCD;
    ItemStack originalStaff;
    HashMap<String, Double> attrMap;
    HashMap<String, Object> extraVariables = new HashMap<>();
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MinionHusk(World world) {
        super(world);
        die();
    }
    public MinionHusk(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
                       boolean sentryOrMinion, boolean hasContactDamage,
                       String minionType, HashMap<String, Double> attrMap, ItemStack originalStaff) {
        this(owner, minionSlot, minionSlotMax, null, sentryOrMinion, hasContactDamage, minionType, attrMap, originalStaff);
    }
    public MinionHusk(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
                       org.bukkit.entity.Entity minionInList, boolean sentryOrMinion, boolean hasContactDamage,
                       String minionType, HashMap<String, Double> attrMap, ItemStack originalStaff) {
        super(((CraftWorld) owner.getWorld()).getHandle());
        this.owner = owner;
        this.minionSlot = minionSlot;
        this.minionSlotMax = minionSlotMax;
        if (minionInList == null)
            this.minionInList = this.getBukkitEntity();
        else
            this.minionInList = minionInList;
        this.sentryOrMinion = sentryOrMinion;
        this.hasContactDamage = hasContactDamage;
        this.minionType = minionType;
        this.hasTeleported = false;
        this.targetNeedLineOfSight = true;
        this.protectOwner = true;
        this.damageInvincibilityTicks = 15;
        this.damageCD = new ArrayList<>();
        this.attrMap = attrMap;
        this.originalStaff = originalStaff;
        this.basicDamage = attrMap.getOrDefault("damage", 10d);
        // does not get removed if far away.
        this.persistent = true;
        // set location
        Location spawnLoc = owner.getLocation();
        setLocation(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0, 0);
        setHeadRotation(0);
        // add to world
        ((CraftWorld) owner.getWorld()).addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM);
        // attributes etc.
        setBaby(true);
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.DAMAGE_SOURCE, owner);
        EntityHelper.setDamageType(getBukkitEntity(), EntityHelper.DamageType.SUMMON);
        addScoreboardTag("noDamage");
        addScoreboardTag("noMelee");
        setCustomName(minionType);
        setCustomNameVisible(true);
        // prevent items being picked up by the minion
        ((Husk) getBukkitEntity()).getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        // minions other than dwarves should not need any goal selector etc. that are redundant and laggy
        switch (minionType) {
            case "小骷髅":
            case "缠怨鬼碟":
            case "矮人":
                break;
            default:
                this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        }
        this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        // move speed, potion effects etc.
        switch (minionType) {
            case "小骷髅":
            case "缠怨鬼碟": {
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.5d);
                break;
            }
            case "矮人": {
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.5d);
                ((LivingEntity) getBukkitEntity()).addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP,
                        999999,
                        1,
                        false,
                        false
                ));
                break;
            }
            default:
                noclip = true;
                setNoGravity(true);
        }
        getAttributeInstance(GenericAttributes.maxHealth).setValue(444);
        getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(444);
        setHealth(444f);
    }
    // basic ticking
    @Override
    public void B_() {
        super.B_();
        // update attribute
        if (this.ticksLived % 10 == 0) {
            MinionHelper.updateAttrMap(this.attrMap, owner, originalStaff);
        }
        // validation
        if (!MinionHelper.validate(this.getBukkitEntity(), owner, minionSlot, minionSlotMax, minionInList, sentryOrMinion)) {
            die();
            return;
        }
        // setup target
        if (!MinionHelper.checkTargetIsValidEnemy(this, ((CraftPlayer) owner).getHandle(), getGoalTarget(), targetNeedLineOfSight)
                || ticksLived % 10 == 0)
            MinionHelper.setTarget(this, ((CraftPlayer) owner).getHandle(),
                    sentryOrMinion, targetNeedLineOfSight, protectOwner);
        // extra ticking AI
        Vector velocity = new Vector(motX, motY, motZ);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner,
                sentryOrMinion ? EntityHelper.MetadataName.PLAYER_SENTRY_LIST : EntityHelper.MetadataName.PLAYER_MINION_LIST).value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        switch (minionType) {
            case "颠茄之灵": {
                Location targetLoc = owner.getLocation().add(
                        Math.random() * 2 - 1,
                        Math.random() + 5,
                        Math.random() * 2 - 1);
                // wonder around the owner.
                if (minionBukkit.getLocation().distanceSquared(targetLoc) > (targetIsOwner ? 16 : 4)) {
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                    velocity.normalize().multiply(0.5);
                }
                // shoot projectile
                if (!targetIsOwner) {
                    if (index % 15 == 0) {
                        EntityHelper.spawnProjectile(minionBukkit, new Vector(0, 0.5, 0),
                                EntityHelper.getAttrMap(minionBukkit), "剧毒花瓣");
                    }
                }
                break;
            }
            case "蚀骨之龙": {
                // stay behind the owner when idle
                if (targetIsOwner) {
                    // get the index of current minion
                    double indexCurr = 0;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (currMinion == minionBukkit)
                            break;
                        indexCurr ++;
                    }
                    // get the location for this minion
                    Location targetLoc = target.getEyeLocation();
                    EntityPlayer plyNMS = ((CraftPlayer) owner).getHandle();
                    Vector offset = MathHelper.vectorFromYawPitch_quick(plyNMS.yaw + 180, 0);
                    offset.multiply(indexCurr + 1);
                    targetLoc.add(offset);
                    // set velocity
                    velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 1, true);
                }
                else {
                    // fly into enemy
                    velocity.add(MathHelper.getDirection(minionBukkit.getLocation(),
                            target.getEyeLocation().add(0, 2, 0), 0.1));
                    double velLen = velocity.length();
                    if (velLen > 0.6)
                        velocity.multiply(0.6 / velLen);
                    // shoot projectile
                    if (index % 12 == 0) {
                        EntityHelper.spawnProjectile(minionBukkit, MathHelper.getDirection(minionBukkit.getEyeLocation(),
                                target.getEyeLocation(), 1.5), EntityHelper.getAttrMap(minionBukkit), "蚀骨尖刺");
                    }
                }
                break;
            }
            case "小骷髅": {
                // occasionally leaves explosion when attacking
                if (!targetIsOwner && Math.random() < 0.05) {
                    EntityHelper.handleEntityExplode(minionBukkit, 1, new ArrayList<>(), minionBukkit.getEyeLocation());
                }
                break;
            }
            case "缠怨鬼碟": {
                if (!targetIsOwner && ticksLived % 10 == 0) {
                    Vector projVel = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 2);
                    EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, "缠怨鬼碟");
                }
                break;
            }
            case "矮人": {
                if (!targetIsOwner && ticksLived % 12 == 0) {
                    Vector v = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double dist = v.length();
                    if (dist > 1e-9) {
                        v.multiply(2.5 / dist);
                        EntityHelper.spawnProjectile(minionBukkit, v, attrMap, "投矛");
                    }
                }
                break;
            }
        }
        // strike all enemies in path
        if (hasContactDamage) {
            MinionHelper.handleContactDamage(this, hasTeleported, isBaby() ? 0.5 : 1, basicDamage, damageCD, damageInvincibilityTicks);
        }
        motX = velocity.getX();
        motY = velocity.getY();
        motZ = velocity.getZ();
        // reset teleported
        hasTeleported = false;
        // add 1 to index
        index ++;
    }
}
