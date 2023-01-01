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
import terraria.entity.HitEntityInfo;
import terraria.util.EntityHelper;
import terraria.util.GenericHelper;
import terraria.util.MathHelper;

import java.util.*;

public class MinionSlime extends EntitySlime {
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
        setSize(1, false);
        EntityHelper.setMetadata(getBukkitEntity(), "attrMap", attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), "damageSourcePlayer", owner);
        EntityHelper.setDamageType(getBukkitEntity(), "Summon");
        addScoreboardTag("noDamage");
        addScoreboardTag("noMelee");
        setCustomName(minionType);
        setCustomNameVisible(true);
        // minions other than baby slime should not need any goal selector etc. that are redundant and laggy
        if (!minionType.equals("史莱姆宝宝")) {
            this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
            this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        }
        switch (minionType) {
            case "史莱姆宝宝": {
                this.damageInvincibilityTicks = 5;
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(1d);
                break;
            }
            case "小鬼":
            case "钨钢无人机":
            {
                index = (int) (Math.random() * 360);
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
            case "致命球": {
                setNoGravity(true);
                break;
            }
            case "星尘之龙": {
//                setSize(2, false);
                protectOwner = false;
                targetNeedLineOfSight = false;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "泰拉棱镜": {
                GenericHelper.StrikeLineOptions strikeLineOption =
                        new GenericHelper.StrikeLineOptions()
                                .setDamageCD(damageInvincibilityTicks)
                                .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                        .setWidth(0.2)
                                        .setLength(6)
                                        .setTicksLinger(3)
                                        .setParticleColor(
                                                "255|0|0",
                                                "255|255|0",
                                                "0|255|0",
                                                "0|255|255",
                                                "0|0|255",
                                                "255|0|255"));
                extraVariables.put("strikeLineOption", strikeLineOption);
                noclip = true;
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
        if (!MinionHelper.checkTargetIsValidEnemy(this, ((CraftPlayer) owner).getHandle(), getGoalTarget(), targetNeedLineOfSight)
                || ticksLived % 10 == 0)
            MinionHelper.setTarget(this, ((CraftPlayer) owner).getHandle(), targetNeedLineOfSight, protectOwner);
        // extra ticking AI
        Vector velocity = new Vector(motX, motY, motZ);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner, sentryOrMinion ? "sentries" : "minions").value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        switch (minionType) {
            case "小鬼":
                setCustomName("小鬼§" + (index / 4) % 4);
            case "钨钢无人机": {
                // move towards enemy
                Location targetLoc;
                double angle = index * 3;
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
                boolean shouldUpdateVelocity = targetIsOwner || index % 5 == 0;
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
                boolean shouldUpdateVelocity = index % (targetIsOwner ? 20 : 4) == 0;
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
                    if (index % 20 == 0) {
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
                switch (index % 10) {
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
            case "致命球": {
                int roundDuration = 15;
                int round = (index / roundDuration) % 4;
                if (round < 3 || targetIsOwner) {
                    int roundPhase = index % roundDuration;
                    // velocity init
                    if (roundPhase == 0) {
                        Location targetLoc;
                        if (targetIsOwner)
                            targetLoc = target.getEyeLocation().add(
                                    Math.random() * 10 - 5,
                                    Math.random() * 6 - 2,
                                    Math.random() * 10 - 5);
                        else
                            targetLoc = target.getEyeLocation();
                        velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                        double distance = velocity.length();
                        if (targetIsOwner) {
                            velocity.multiply(Math.max(1, distance / roundDuration) / distance);
                        } else {
                            velocity.multiply(Math.max(2, distance / roundDuration / 2) / distance);
                        }
                    }
                    // slow down; float up if target is not the owner
                    if (roundPhase * 1.5 >= roundDuration) {
                        velocity.multiply(0.8);
                        if (!targetIsOwner)
                            velocity.setY(velocity.getY() + 0.25);
                    }
                } else {
                    // waiting for next triple dash
                    velocity.multiply(0.75);
                }
                break;
            }
            case "星尘细胞": {
                // teleport and shooting projectile
                int teleportCD = (int) extraVariables.getOrDefault("teleportCD", 15);
                Location targetLoc;
                if (!targetIsOwner) {
                    // velocity
                    velocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double distance = velocity.length();
                    if (distance < 1e-9) velocity = new Vector(0, 0.5, 0);
                    else if (distance < 8) velocity.multiply(-0.5 / distance);
                    else velocity.multiply(0.5 / distance);
                    // basic fire
                    int fireDelay = (int) extraVariables.getOrDefault("fireDelay", 0);
                    int amountFired = 0;
                    if (--fireDelay <= 0) {
                        amountFired = 1;
                        fireDelay = 24;
                    }
                    if (--teleportCD < 0) {
                        teleportCD = (int) (Math.random() * 30 + 15);
                        Location destination = target.getEyeLocation().add(
                                MathHelper.xsin_degree(index) * 2,
                                2,
                                MathHelper.xcos_degree(index) * 2);
                        Vector trailVec = minionBukkit.getEyeLocation().subtract(destination).toVector();
                        // prevent having a trace vector that is too long and introduces too much unnecessary visual effect
                        double particleLineLength = trailVec.length();
                        if (particleLineLength > 7) {
                            trailVec.multiply(7 / particleLineLength);
                            particleLineLength = 7;
                        }
                        GenericHelper.handleParticleLine(trailVec, destination,
                                new GenericHelper.ParticleLineOptions()
                                        .setParticleColor("102|204|255")
                                        .setLength(particleLineLength));
                        minionBukkit.teleport(destination);
                        amountFired ++;
                    }
                    for (int i = 0; i < amountFired; i ++) {
                        Vector projectileDir = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                        if (projectileDir.lengthSquared() < 1e-9) {
                            projectileDir = new Vector(0, 1, 0);
                        }
                        projectileDir.normalize().multiply(3);
                        EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, "星尘细胞弹");
                    }
                    extraVariables.put("fireDelay", fireDelay);
                } else {
                    teleportCD = (int) (Math.random() * 5 + 5);
                    targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 4) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.7);
                    }
                }
                extraVariables.put("teleportCD", teleportCD);
                break;
            }
            case "星尘之龙": {
                // get all segments
                boolean isHeadSegment = true;
                ArrayList<Entity> allSegments = new ArrayList<>(allMinions.size());
                for (Entity currMinion : allMinions) {
                    if (currMinion.isDead()) continue;
                    if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                    if (allSegments.size() == 0 && currMinion != minionBukkit) {
                        isHeadSegment = false;
                        break;
                    }
                    allSegments.add(currMinion);
                }
                // if the segment is the head, handle strike and segments following
                if (isHeadSegment) {
                    // tweak speed
                    if (targetIsOwner) {
                        if (minionBukkit.getLocation().distanceSquared(owner.getEyeLocation()) > 400 || index % 30 == 0) {
                            Vector v = target.getEyeLocation().add(
                                    Math.random() * 16 - 8,
                                    4,
                                    Math.random() * 16 - 8).subtract(minionBukkit.getLocation()).toVector();
                            if (v.lengthSquared() < 1e-9) v = new Vector(0, 1, 0);
                            double distance = v.length();
                            v.multiply(Math.max(distance / 20, 0.75)  / distance);
                            velocity = v;
                        }
                    } else if (index % 8 == 0) {
                        Vector v = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                        if (v.lengthSquared() < 1e-9) v = new Vector(0, 1, 0);
                        double distance = v.length();
                        v.multiply(Math.min(
                                ((allSegments.size() + 1) * 0.75 + distance * 0.25) / 6, 3)  / distance);
                        velocity = v;
                    }
                    EntityHelper.handleSegmentsFollow(allSegments,
                            new EntityHelper.WormSegmentMovementOptions()
                                    .setStraighteningMultiplier(-0.75)
                                    .setFollowingMultiplier(1)
                                    .setFollowDistance(0.5)
                                    .setVelocityOrTeleport(false));
                }
                // set display name according to segment info
                if (isHeadSegment) {
                    setCustomName(minionType + "§1");
                } else {
                    setCustomName(minionType);
                }
                break;
            }
            case "泰拉棱镜": {
                double currYaw = (double) extraVariables.getOrDefault("yaw", 0.0);
                double currPitch = (double) extraVariables.getOrDefault("pitch", 0.0);
                double dYaw = (double) extraVariables.getOrDefault("dYaw", 0.0);
                double dPitch = (double) extraVariables.getOrDefault("dPitch", 0.0);
                // stab or swing
                boolean attackMethod = (boolean) extraVariables.getOrDefault("atkMethod", true);
                // handle velocity and dYaw dPitch
                if (!targetIsOwner) {
                    int ind = index % 24;
                    if (ind == 0) {
                        attackMethod = !attackMethod;
                    }
                    Vector direction = target.getEyeLocation().subtract(minionBukkit.getLocation()).toVector();
                    if (direction.lengthSquared() < 1e-9) direction = new Vector(0, 1, 0);
                    // stab
                    if (attackMethod) {
                        if (ind == 0) {
                            Vector dir = direction.clone().subtract(new Vector(0, 4, 0));
                            if (dir.lengthSquared() < 1e-9) dir = new Vector(0, 1, 0);
                            Map.Entry<Double, Double> newDeltaDir = GenericHelper.getDirectionInterpolateOffset(
                                    new AbstractMap.SimpleImmutableEntry<>(currYaw, currPitch),
                                    new AbstractMap.SimpleImmutableEntry<>(MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir)),
                                    1d / 12);
                            dYaw = newDeltaDir.getKey();
                            dPitch = newDeltaDir.getValue();
                            velocity = new Vector(0, 0.5, 0);
                        }
                        else if (ind == 12) {
                            velocity = direction.multiply(1d / 8);
                            dYaw = 0;
                            dPitch = 0;
                        }
                    }
                    // swing
                    else {
                        if (ind == 0) {
                            Vector dir = direction.clone().add(new Vector(0, 4, 0));
                            if (dir.lengthSquared() < 1e-9) dir = new Vector(0, 1, 0);
                            Map.Entry<Double, Double> newDeltaDir = GenericHelper.getDirectionInterpolateOffset(
                                    new AbstractMap.SimpleImmutableEntry<>(currYaw, currPitch),
                                    new AbstractMap.SimpleImmutableEntry<>(MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir)),
                                    1d / 12);
                            dYaw = newDeltaDir.getKey();
                            dPitch = newDeltaDir.getValue();
                            velocity = direction.multiply(0.1);
                        }
                    }
                }
                // target is owner
                else {
                    // the next time any enemy is spotted, the minions are ready to stab the enemy
                    index = 12 + (int) (Math.random() * 12);
                    attackMethod = false;
                    // move to the proper location for this minion
                    Entity firstPrism = minionBukkit;
                    int idxCurr = 1, totalPrism = 1;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (totalPrism == 1) firstPrism = currMinion;
                        if (currMinion == minionBukkit)
                            idxCurr = totalPrism;
                        totalPrism ++;
                    }
                    double ownerYaw = ((CraftPlayer) owner).getHandle().yaw;
                    double  idleIndex = MathHelper.xsin_degree(firstPrism.getTicksLived() * 12),
                            targetYaw = ownerYaw < 0 ? ownerYaw + 180 : ownerYaw - 180,
                            targetPitch = -20 - idleIndex * 5;
                    targetYaw += 180d / totalPrism * idxCurr - 90d;
                    Map.Entry<Double, Double> newDeltaDir = GenericHelper.getDirectionInterpolateOffset(
                            new AbstractMap.SimpleImmutableEntry<>(currYaw, currPitch),
                            new AbstractMap.SimpleImmutableEntry<>(targetYaw, targetPitch),
                            0.25);
                    dYaw = newDeltaDir.getKey();
                    dPitch = newDeltaDir.getValue();
                    // every prism should move periodically according to the first prism.
                    Vector dPos = MathHelper.vectorFromYawPitch_quick(targetYaw, targetPitch);
                    Location targetLoc = owner.getLocation().add(dPos.multiply(
                            idleIndex / 3 + 1.5 ));
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                    velocity.multiply(0.35);
                }
                currYaw += dYaw;
                currPitch += dPitch;
                // handle strike
                if (ticksLived % 2 == 0)
                    GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(), currYaw, currPitch, 6.0, 0.2,
                            "", "0|0|0", damageCD, (HashMap<String, Double>) attrMap.clone(),
                            (GenericHelper.StrikeLineOptions) extraVariables.get("strikeLineOption"));
                extraVariables.put("yaw", currYaw);
                extraVariables.put("pitch", currPitch);
                extraVariables.put("dYaw", dYaw);
                extraVariables.put("dPitch", dPitch);
                extraVariables.put("atkMethod", attackMethod);
                break;
            }
        }
        // strike all enemies in path
        if (hasContactDamage) {
            MinionHelper.handleContactDamage(this, hasTeleported, getSize() * 0.5, basicDamage, damageCD, damageInvincibilityTicks);
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
