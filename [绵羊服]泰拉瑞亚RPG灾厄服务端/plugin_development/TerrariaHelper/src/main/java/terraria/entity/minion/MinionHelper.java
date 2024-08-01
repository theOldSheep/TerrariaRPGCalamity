package terraria.entity.minion;

import com.earth2me.essentials.Settings;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.Setting;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

import java.util.*;
import java.util.logging.Level;

public class MinionHelper {
    private static final double MAX_DIST_BEFORE_RETURN = 90, MAX_DIST_BEFORE_TELEPORT = 90,
            TARGET_RADIUS = 75;
    public static final String[] minionRelevantAttributes = {
            "armorPenetration", "damage", "damageMulti", "damageSummonMulti", "knockback", "knockbackMulti"
    };
    public static void updateAttrMap(HashMap<String, Double> attrMap, Player owner, ItemStack originalStaff) {
        HashMap<String, Double> ownerAttrMap = EntityHelper.getAttrMap(owner);
        // clear the attribute
        attrMap.clear();

        // update attributes
        for (String attribute : minionRelevantAttributes)
            attrMap.put(attribute, ownerAttrMap.getOrDefault(attribute, 1d));

        org.bukkit.inventory.ItemStack currentTool = owner.getInventory().getItemInMainHand();
        if (currentTool == null)
            currentTool = new ItemStack(Material.AIR);
        // account for changed player tool
        if (!currentTool.isSimilar(originalStaff)) {
            EntityHelper.tweakAttribute(attrMap, originalStaff, true);
            EntityHelper.tweakAttribute(attrMap, currentTool, false);
        }
        // account for non-summon tool
        if (EntityHelper.getDamageType(owner) != EntityHelper.DamageType.SUMMON) {
            attrMap.put("damageMulti",
                    attrMap.getOrDefault("damageMulti", 1d) *
                    ownerAttrMap.getOrDefault("minionDamagePenaltyFactor", 0.5d));
        }

        attrMap.put("crit", 0d);
    }
    public static boolean validate(Entity minion, Player owner, int minionSlot, int minionSlotMax, Entity minionInList, boolean sentryOrMinion) {
        if (!owner.isOnline()) return false;
        if (!PlayerHelper.isProperlyPlaying(owner)) return false;
        // setup variables
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(owner);
        ArrayList<Entity> minionList;
        int minionLimit;
        {
            if (sentryOrMinion) {
                minionList = (ArrayList<Entity>) EntityHelper.getMetadata(owner,
                        EntityHelper.MetadataName.PLAYER_SENTRY_LIST).value();
                minionLimit = attrMap.getOrDefault("sentryLimit", 1d).intValue();
            } else {
                minionList = (ArrayList<Entity>) EntityHelper.getMetadata(owner,
                        EntityHelper.MetadataName.PLAYER_MINION_LIST).value();
                minionLimit = attrMap.getOrDefault("minionLimit", 1d).intValue();
            }
        }
//        Bukkit.broadcastMessage(minionSlot + "->" + minionSlotMax + ", max " + minionLimit + ":::" + minionList);
        // if the minion list is smaller, return false to prevent error.
        if (minionList.size() <= minionSlotMax) return false;
        // if the minion limit shrinks, the minion should be removed.
        if (minionSlotMax >= minionLimit) return false;
        // if the minion is overridden, the minion should be removed.
        if (minionList.get(minionSlot) != minionInList) return false;
        // world is different
        if (owner.getWorld() != minion.getWorld()) return false;
        // the minion is valid
        return true;
    }
    public static Set<HitEntityInfo> handleContactDamage(net.minecraft.server.v1_12_R1.Entity minion, boolean hasTeleported, double size, double basicDamage, ArrayList<Entity> damageCD, int invincibilityTick) {
        Vec3D startLoc;
        if (hasTeleported)
            startLoc = new Vec3D(minion.locX, minion.locY, minion.locZ);
        else
            startLoc = new Vec3D(minion.lastX, minion.lastY, minion.lastZ);
        Vec3D terminalLoc = new Vec3D(minion.locX, minion.locY, minion.locZ);
        Entity minionBukkit = minion.getBukkitEntity();
        // find all enemies close to the minion. Strict mode is off so the minion could potentially damage critters etc.
        Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(minion.getWorld(), startLoc, terminalLoc,
                size,
                (e) -> {
                    Entity bukkitE = e.getBukkitEntity();
                    return !damageCD.contains(bukkitE) && EntityHelper.checkCanDamage(minionBukkit, bukkitE, false);
                });
        for (HitEntityInfo info : toDamage) {
            Entity victimBukkit = info.getHitEntity().getBukkitEntity();
            EntityHelper.damageCD(damageCD, victimBukkit, invincibilityTick);
            EntityHelper.handleDamage(minionBukkit, victimBukkit, basicDamage, EntityHelper.DamageReason.CONTACT_DAMAGE);
        }
        return toDamage;
    }
    private static double getHorDistSqr(double x1, double x2, double y1, double y2) {
        return (x1-x2)*(x1-x2) + (y1-y2)*(y1-y2);
    }
    private static double getHorDistSqr(Entity minion, Entity target) {
        double targetRadius = 0.5;
        if (target instanceof Slime) {
            targetRadius = 0.25 * ((Slime) target).getSize();
        }
        Location minionLoc = minion.getLocation();
        Location targetLoc = target.getLocation();
        double xDist = Math.abs(minionLoc.getX() - targetLoc.getX());
        double zDist = Math.abs(minionLoc.getZ() - targetLoc.getZ());
        xDist = Math.max(xDist - targetRadius, 0);
        zDist = Math.max(zDist - targetRadius, 0);
        return xDist * xDist + zDist * zDist;
    }
    private static Collection<net.minecraft.server.v1_12_R1.Entity> getNearbyEntities(net.minecraft.server.v1_12_R1.Entity e, double radius, com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication) {
        return e.getWorld().getEntities(e, e.getBoundingBox().g(radius), predication);
    }
    private static com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> getTargetPredication(
            EntityInsentient minion, EntityPlayer owner, boolean needLineOfSight) {
        Entity minionBukkit = minion.getBukkitEntity();
        if (needLineOfSight)
            return (entity) ->
                    entity != null &&
                            EntityHelper.checkCanDamage(minionBukkit, entity.getBukkitEntity(), true) &&
                            (minion.hasLineOfSight(entity) || owner.hasLineOfSight(entity));
        else
            return (entity) ->
                    entity != null &&
                            EntityHelper.checkCanDamage(minionBukkit, entity.getBukkitEntity(), true);
    }
    public static boolean checkTargetIsValidEnemy(EntityInsentient minion, EntityPlayer owner, EntityLiving target, boolean needLineOfSight) {
        com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication = getTargetPredication(minion, owner, needLineOfSight);
        return target != owner && predication.test(target);
    }
    public static boolean checkDistanceIsValid(EntityInsentient minion, EntityPlayer owner) {
        double horDistSqrToOwner = getHorDistSqr(minion.locX, owner.locX, minion.locZ, owner.locZ);
        if (horDistSqrToOwner > MAX_DIST_BEFORE_RETURN * MAX_DIST_BEFORE_RETURN) {
            minion.setGoalTarget(owner, EntityTargetEvent.TargetReason.CUSTOM, false);
            if (horDistSqrToOwner > MAX_DIST_BEFORE_TELEPORT * MAX_DIST_BEFORE_TELEPORT) {
                attemptTeleport(minion.getBukkitEntity(), owner.getBukkitEntity().getLocation().add(0, 5, 0));
            }
            return false;
        }
        return true;
    }
    public static void setTarget(EntityInsentient minion, EntityPlayer owner,
                                 boolean sentryOrMinion, boolean needLineOfSight, boolean protectOwner) {
        // check distance from the player
        if (!sentryOrMinion) {
            checkDistanceIsValid(minion, owner);
        }
        EntityLiving finalTarget = owner;
        // strict mode is on so that the minion does not target critters.
        com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication = getTargetPredication(minion, owner, needLineOfSight);
        // check if current target is valid
        {
            EntityLiving goalTarget = minion.getGoalTarget();
            if (goalTarget != null && predication.test(goalTarget))
                finalTarget = goalTarget;
        }
        // whip target
        boolean whipTargetValid = false;
        {
            MetadataValue whipTargetMetadata = EntityHelper.getMetadata(owner.getBukkitEntity(),
                    EntityHelper.MetadataName.PLAYER_MINION_WHIP_FOCUS);
            if (whipTargetMetadata != null) {
                Entity whipTarget = (Entity) whipTargetMetadata.value();
                net.minecraft.server.v1_12_R1.Entity whipTargetNMS = ((CraftEntity) whipTarget).getHandle();
                if (whipTargetNMS instanceof EntityLiving) {
                    EntityLiving whipTargetEntityLiving = (EntityLiving) whipTargetNMS;
                    if (predication.test(whipTargetEntityLiving)) {
                        finalTarget = whipTargetEntityLiving;
                        whipTargetValid = true;
                    }
                }
            }
        }
        // target the nearest enemy
        if (!whipTargetValid) {
            net.minecraft.server.v1_12_R1.Entity findNearestFrom = protectOwner ? owner : minion;
            double nearestTargetDistSqr = 1e9,
                    targetRadiusActual;
            if (finalTarget == owner)
                targetRadiusActual = TARGET_RADIUS;
            else
                targetRadiusActual =
                        Math.sqrt( getHorDistSqr(findNearestFrom.getBukkitEntity(), finalTarget.getBukkitEntity()) )
                                - Setting.getOptionDouble(owner.getBukkitEntity(), Setting.Options.MINION_RETARGET_THRESHOLD);
            ArrayList<net.minecraft.server.v1_12_R1.Entity> toCheck = new ArrayList<>(50);
            toCheck.addAll(getNearbyEntities(minion, targetRadiusActual, predication));
            toCheck.addAll(getNearbyEntities(owner, targetRadiusActual, predication));
            for (net.minecraft.server.v1_12_R1.Entity entity : toCheck) {
                if (!(entity instanceof EntityLiving)) continue;
                double distSqr = getHorDistSqr(findNearestFrom.getBukkitEntity(), entity.getBukkitEntity());
                if (distSqr > nearestTargetDistSqr) continue;
                nearestTargetDistSqr = distSqr;
                finalTarget = (EntityLiving) entity;
            }
        }
        minion.setGoalTarget(finalTarget, EntityTargetEvent.TargetReason.CUSTOM, finalTarget != owner);
    }
    public static void attemptTeleport(Entity minion, Location loc) {
        if (minion.isDead())
            return;
        minion.teleport(loc);
        net.minecraft.server.v1_12_R1.Entity minionNMS = ((CraftEntity) minion).getHandle();
        if (minionNMS instanceof MinionSlime)
            ((MinionSlime) minionNMS).hasTeleported = true;
        else if (minionNMS instanceof MinionHusk)
            ((MinionHusk) minionNMS).hasTeleported = true;
        else if (minionNMS instanceof MinionCaveSpider)
            ((MinionCaveSpider) minionNMS).hasTeleported = true;
        else
            TerrariaHelper.LOGGER.log(Level.SEVERE, "Minion teleport flag set issue: unknown minion class " + minion);
    }
}
