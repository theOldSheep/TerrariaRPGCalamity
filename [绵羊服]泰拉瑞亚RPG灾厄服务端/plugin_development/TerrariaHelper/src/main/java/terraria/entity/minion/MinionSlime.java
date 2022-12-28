package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.MathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class MinionSlime extends EntitySlime {
    org.bukkit.entity.Player owner;
    int damageInvincibilityTicks, minionSlot, minionSlotMax;
    double basicDamage;
    org.bukkit.entity.Entity minionInList;
    boolean hasContactDamage, hasTeleported, protectOwner, sentryOrMinion, targetNeedLineOfSight;
    String minionType;
    ArrayList<Entity> damageCD;
    ItemStack originalStaff;
    HashMap<String, Double> attrMap;
    HashMap<String, Object> extraVariables = new HashMap<>();
    public MinionSlime(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
                       boolean sentryOrMinion, boolean hasContactDamage,
                       String minionType, HashMap<String, Double> attrMap, ItemStack originalStaff) {
        this(owner, minionSlot, minionSlotMax, null, sentryOrMinion, hasContactDamage, minionType, attrMap, originalStaff);
    }
    public MinionSlime(org.bukkit.entity.Player owner, int minionSlot, int minionSlotMax,
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
        this.damageInvincibilityTicks = 5;
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
        setSize(1, false);
        getAttributeInstance(GenericAttributes.maxHealth).setValue(444);
        getAttributeInstance(GenericAttributes.FOLLOW_RANGE).setValue(444);
        setHealth(444f);
        EntityHelper.setMetadata(getBukkitEntity(), "attrMap", attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), "damageSourcePlayer", owner);
        EntityHelper.setDamageType(getBukkitEntity(), "Summon");
        addScoreboardTag("noDamage");
        addScoreboardTag("noMelee");
        setCustomName(minionType);
        setCustomNameVisible(true);
        switch (minionType) {
            case "史莱姆宝宝": {
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(1d);
                break;
            }
            case "小鬼":
            case "钨钢无人机":
            {
                ticksLived = (int) (Math.random() * 360);
                setNoGravity(true);
                break;
            }
            case "小激光眼": {
                new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, true,
                        "小魔焰眼", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                setNoGravity(true);
                break;
            }
            default: {
                noclip = true;
                setNoGravity(true);
            }
        }
    }
    // jumping CD
    @Override
    protected int df() {
        return 0;
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
        if (ticksLived % 5 == 0)
            MinionHelper.setTarget(this, ((CraftPlayer) owner).getHandle(), targetNeedLineOfSight, protectOwner);
        // strike all enemies in path
        if (hasContactDamage) {
            MinionHelper.handleContactDamage(this, hasTeleported, getSize() * 0.5, basicDamage, damageCD, damageInvincibilityTicks);
        }
        // extra ticking AI
        Vector velocity = new Vector(motX, motY, motZ);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner, sentryOrMinion ? "sentries" : "minions").value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        switch (minionType) {
            case "小鬼":
                setCustomName("小鬼§" + (ticksLived / 4) % 4);
            case "钨钢无人机": {
                // move towards enemy
                Location targetLoc;
                double angle = ticksLived * 3;
                if (target == owner)
                    targetLoc = target.getEyeLocation().add(MathHelper.xsin_degree(angle) * 2, 1, MathHelper.xcos_degree(angle) * 2);
                else
                    targetLoc = target.getEyeLocation().add(MathHelper.xsin_degree(angle) * 4, 6, MathHelper.xcos_degree(angle) * 4);
                velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                // tweak velocity
                {
                    double maxSpeed = targetIsOwner ? 1.75 : 1;
                    double dist = velocity.length();
                    velocity.multiply(Math.min(1d / 30, maxSpeed / dist));
                }
                // shoot projectile
                int shootDelay = (int) extraVariables.getOrDefault("shootDelay", 20);
                if (targetIsOwner) shootDelay = 0;
                else if (--shootDelay <= 0) {
                    Vector projectileVelocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    projectileVelocity.normalize();
                    String projectileType;
                    if (minionType.equals("小鬼")) {
                        projectileType = "小火花";
                        projectileVelocity.multiply(1.5);
                    } else {
                        projectileType = "钨钢光球";
                    }
                    EntityHelper.spawnProjectile(minionBukkit, projectileVelocity, attrMap, projectileType);
                    shootDelay = (int) (Math.random() * 10) + 15;
                }
                extraVariables.put("shootDelay", shootDelay);
                break;
            }
            case "附魔飞刀": {
                // setup target location
                double indexCurr = 0, indexMax = 0;
                Location targetLoc = target.getEyeLocation();
                if (targetIsOwner) {
                    Entity firstMinion = minionBukkit;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax ++;
                    }
                    targetLoc.add(0, 1, 0);
                    if (indexMax > 1) {
                        double angle = 360 * indexCurr / indexMax + firstMinion.getTicksLived() * 5;
                        targetLoc.add(MathHelper.xsin_degree(angle), 0, MathHelper.xcos_degree(angle));
                    }
                } else {
                    targetLoc.multiply(0.75).add(target.getLocation().toVector().multiply(0.25));
                }
                // setup velocity
                boolean shouldUpdateVelocity = targetIsOwner || ticksLived % 5 == 0;
                double maxSpeed = targetIsOwner ? 2 : 1.75;
                if (shouldUpdateVelocity) {
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                    double distance = velocity.length();
                    if (targetIsOwner) {
                        double finalSpeed = Math.min(distance * 0.8, maxSpeed);
                        velocity.multiply(finalSpeed / distance);
                    } else {
                        if (distance > 1e-9) {
                            velocity.multiply(maxSpeed / distance);
                        }
                    }
                }
                break;
            }
            case "小激光眼": {
                this.noclip = targetIsOwner;
                boolean shouldUpdateVelocity = ticksLived % (targetIsOwner ? 20 : 4) == 0;
                // velocity
                if (shouldUpdateVelocity) {
                    Location targetLoc;
                    if (targetIsOwner) {
                        targetLoc = target.getEyeLocation().add(
                                Math.random() * 10 - 5,
                                Math.random() * 5 - 2,
                                Math.random() * 10 - 5);
                        velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                        double distance = velocity.length();
                        if (distance > 1e-9) {
                            velocity.multiply(Math.max(distance / 20, 0.75) / distance);
                        }
                    } else {
                        targetLoc = target.getEyeLocation();
                        velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                        double distance = velocity.length();
                        if (distance > 1e-9) {
                            velocity.multiply(1.5 / distance);
                            if (distance < 16)
                                velocity.multiply(-1);
                        }
                    }
                }
                // shoot laser
                if (!targetIsOwner) {
                    if (ticksLived % 20 == 0) {
                        Vector projectileVelocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                        if (projectileVelocity.lengthSquared() > 1e-9) {
                            projectileVelocity.normalize().multiply(1.75);
                            EntityHelper.spawnProjectile(minionBukkit, projectileVelocity, attrMap, "激光");
                        }
                    }
                }
                break;
            }
            case "小魔焰眼": {
                this.noclip = targetIsOwner;
                // velocity
                switch (ticksLived % 10) {
                    case 0: {
                        Location targetLoc;
                        if (targetIsOwner) {
                            targetLoc = target.getEyeLocation().add(
                                    Math.random() * 10 - 5,
                                    Math.random() * 5 - 2,
                                    Math.random() * 10 - 5);
                        } else {
                            targetLoc = target.getEyeLocation();
                        }
                        velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                        double distance = velocity.length();
                        if (distance > 1e-9) {
                            if (targetIsOwner)
                                velocity.multiply(Math.max(distance / 20, 0.75) / distance);
                            else
                                velocity.multiply(2 / distance);
                        }
                        break;
                    }
                    case 6: {
                        if (!targetIsOwner)
                            velocity.multiply(0.3);
                        break;
                    }
                }
                break;
            }
        }
        motX = velocity.getX();
        motY = velocity.getY();
        motZ = velocity.getZ();
        // reset teleported
        hasTeleported = false;
    }
}
