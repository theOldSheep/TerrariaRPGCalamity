package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.util.DamageHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class MinionCaveSpider extends EntityCaveSpider {
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
    // store other parts in here, so in case of removal, those parts are guaranteed to go away
    ArrayList<net.minecraft.server.v1_12_R1.Entity> otherParts = new ArrayList<>();
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MinionCaveSpider(World world) {
        super(world);
        die();
    }
    public MinionCaveSpider(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
                      boolean sentryOrMinion, boolean hasContactDamage,
                      String minionType, HashMap<String, Double> attrMap, ItemStack originalStaff) {
        this(owner, minionSlot, minionSlotMax, null, sentryOrMinion, hasContactDamage, minionType, attrMap, originalStaff);
    }
    public MinionCaveSpider(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
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
        this.originalStaff = originalStaff.clone();
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
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.DAMAGE_SOURCE, owner);
        DamageHelper.setDamageType(getBukkitEntity(), DamageHelper.DamageType.SUMMON);
        addScoreboardTag("noDamage");
        addScoreboardTag("noMelee");
        setCustomName(minionType);
        setCustomNameVisible(true);
        // minions other than certain ones should not need any goal selector
        switch (minionType) {
            case "蜘蛛":
            case "寄居蟹":
                break;
            default:
                this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        }
        this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        switch (minionType) {
            case "蜘蛛": {
                damageInvincibilityTicks = 5;
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.6d);
                // navigation
                getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(48);
                ((LivingEntity) getBukkitEntity()).addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP,
                        999999,
                        9,
                        false,
                        false
                ));
                break;
            }
            case "寄居蟹": {
                damageInvincibilityTicks = 8;
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.4d);
                // navigation
                getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(32);
                break;
            }
            case "灵魂吞噬者宝宝": {
                damageInvincibilityTicks = 5;
                setNoGravity(true);
            }
            default: {
                noclip = true;
                setNoGravity(true);
            }
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
        if (this.ticksLived <= 1 || this.ticksLived % 10 == 0) {
            MinionHelper.updateAttrMap(this.attrMap, owner, originalStaff);
        }
        // validation
        if (!MinionHelper.validate(this.getBukkitEntity(), owner, minionSlot, minionSlotMax, minionInList, sentryOrMinion)) {
            die();
            return;
        }
        // setup target, mandatory twice per second AND if the current target is invalid
        EntityPlayer ownerNMS = ((CraftPlayer) owner).getHandle();
        if ( !(MinionHelper.checkTargetIsValidEnemy(
                this, ownerNMS, getGoalTarget(), targetNeedLineOfSight) &&
                MinionHelper.checkDistanceIsValid(this, ownerNMS) )
                || ticksLived % 10 == 0)
            MinionHelper.setTarget(this, ownerNMS, sentryOrMinion, targetNeedLineOfSight, protectOwner);
        // extra ticking AI
        Vector velocity = new Vector(motX, motY, motZ);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner,
                sentryOrMinion ? EntityHelper.MetadataName.PLAYER_SENTRY_LIST : EntityHelper.MetadataName.PLAYER_MINION_LIST).value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        switch (minionType) {
            case "蜘蛛": {
                if (!targetIsOwner && damageCD.contains(target)) {
                    MinionHelper.attemptTeleport(minionBukkit, target.getEyeLocation());
                }
                break;
            }
            case "灵魂吞噬者宝宝": {
                // wonder around the owner when idle
                if (targetIsOwner) {
                    Location targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 16) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.5);
                    }
                }
                // attack enemy
                else {
                    // stuck to enemy that has been attacked
                    if (damageCD.contains(target)) {
                        MinionHelper.attemptTeleport(minionBukkit, target.getEyeLocation());
                    }
                    // if not yet attached to target, dash into it
                    else {
                        switch (index % 12) {
                            case 0: {
                                velocity = MathHelper.getDirection(
                                        minionBukkit.getEyeLocation(), target.getEyeLocation(), 2);
                                break;
                            }
                            case 8: {
                                velocity.multiply(0.6);
                                break;
                            }
                        }
                    }
                }
                break;
            }
        }
        // strike all enemies in path
        if (hasContactDamage) {
            MinionHelper.handleContactDamage(this, hasTeleported, 0.5, basicDamage, damageCD, damageInvincibilityTicks);
        }
        motX = velocity.getX();
        motY = velocity.getY();
        motZ = velocity.getZ();
        // reset teleported
        hasTeleported = false;
        // add 1 to index
        index ++;
    }
    // on death - remove parts as well
    @Override
    public void die() {
        super.die();
        for (net.minecraft.server.v1_12_R1.Entity e : otherParts) {
            e.die();
        }
    }
}
