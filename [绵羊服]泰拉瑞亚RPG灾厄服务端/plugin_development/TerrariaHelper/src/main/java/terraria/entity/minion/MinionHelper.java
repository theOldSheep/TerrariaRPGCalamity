package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.Vec3D;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import terraria.entity.HitEntityInfo;
import terraria.util.EntityHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class MinionHelper {
    private static final double maxDistBeforeReturn = 64, maxDistBeforeTeleport = 80, targetRadius = 64;
    public static void updateAttrMap(HashMap<String, Double> attrMap, Player owner, ItemStack originalStaff) {
        HashMap<String, Double> ownerAttrMap = EntityHelper.getAttrMap(owner);
        for (String attribute : ownerAttrMap.keySet())
            attrMap.put(attribute, ownerAttrMap.getOrDefault(attribute, 1d));
        org.bukkit.inventory.ItemStack currentTool = owner.getInventory().getItemInMainHand();
        // account for changed player tool
        if (!currentTool.isSimilar(originalStaff)) {
            EntityHelper.tweakAttribute(attrMap, originalStaff, true);
            EntityHelper.tweakAttribute(attrMap, currentTool, false);
        }
        // account for non-summon tool
        if (!EntityHelper.getDamageType(owner).equals("Summon")) {
            attrMap.put("damageMulti",
                    attrMap.getOrDefault("damageMulti", 1d) *
                    ownerAttrMap.getOrDefault("minionDamagePenaltyMulti", 0.5d));
        }
        attrMap.put("crit", 0d);
    }
    public static boolean validate(Entity minion, Player owner, int minionSlot, int minionSlotMax, Entity minionInList, boolean sentryOrMinion) {
        if (!owner.isOnline()) return false;
        // setup variables
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(owner);
        ArrayList<Entity> minionList;
        int minionLimit;
        {
            if (sentryOrMinion) {
                minionList = (ArrayList<Entity>) EntityHelper.getMetadata(owner, "sentries").value();
                minionLimit = attrMap.getOrDefault("sentryLimit", 1d).intValue();
            } else {
                minionList = (ArrayList<Entity>) EntityHelper.getMetadata(owner, "minions").value();
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
        return true;
    }
    public static void handleContactDamage(net.minecraft.server.v1_12_R1.Entity minion, boolean hasTeleported, double size, double basicDamage, ArrayList<Entity> damageCD, int invincibilityTick) {
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
            EntityHelper.handleDamage(minionBukkit, victimBukkit, basicDamage, "DirectDamage");
        }
    }
    private static double getHorDistSqr(double x1, double x2, double y1, double y2) {
        return (x1-x2)*(x1-x2) + (y1-y2)*(y1-y2);
    }
    private static Collection<net.minecraft.server.v1_12_R1.Entity> getNearbyEntities(net.minecraft.server.v1_12_R1.Entity e, double radius, com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication) {
        return e.getWorld().getEntities(e, e.getBoundingBox().g(radius), predication);
    }
    public static void setTarget(EntityInsentient minion, EntityPlayer owner, boolean needLineOfSight, boolean protectOwner) {
        // check distance from the player
        {
            double horDistSqrToOwner = getHorDistSqr(minion.locX, owner.locX, minion.locZ, owner.locZ);
            if (horDistSqrToOwner > maxDistBeforeReturn * maxDistBeforeReturn) {
                minion.setGoalTarget(owner, EntityTargetEvent.TargetReason.CUSTOM, false);
                if (horDistSqrToOwner > maxDistBeforeTeleport * maxDistBeforeTeleport)
                    minion.setLocation(owner.locX, owner.locY, owner.locZ, 0f, 0f);
                return;
            }
        }
        Entity minionBukkit = minion.getBukkitEntity();
        EntityLiving finalTarget = owner;
        // strict mode is on so that the minion does not target critters.
        com.google.common.base.Predicate<? super net.minecraft.server.v1_12_R1.Entity> predication;
        if (needLineOfSight)
            predication = (entity) ->
                    EntityHelper.checkCanDamage(minionBukkit, entity.getBukkitEntity(), true) &&
                            minion.hasLineOfSight(entity);
        else
            predication = (entity) ->
                    EntityHelper.checkCanDamage(minionBukkit, entity.getBukkitEntity(), true);
        // check if current target is valid
        {
            EntityLiving goalTarget = minion.getGoalTarget();
            if (goalTarget != null && predication.test(goalTarget))
                finalTarget = goalTarget;
        }
        // whip target
        {
            MetadataValue whipTargetMetadata = EntityHelper.getMetadata(owner.getBukkitEntity(), "minionWhipFocus");
            if (whipTargetMetadata != null) {
                Entity whipTarget = (Entity) whipTargetMetadata.value();
                net.minecraft.server.v1_12_R1.Entity whipTargetNMS = ((CraftEntity) whipTarget).getHandle();
                if (whipTargetNMS instanceof EntityLiving) {
                    EntityLiving whipTargetEntityLiving = (EntityLiving) whipTargetNMS;
                    if (predication.test(whipTargetEntityLiving))
                        finalTarget = whipTargetEntityLiving;
                }
            }
        }
        // target the nearest enemy
        net.minecraft.server.v1_12_R1.Entity findNearestFrom = protectOwner ? owner : minion;
        double nearestTargetDistSqr = 1e9;
        for (net.minecraft.server.v1_12_R1.Entity entity : getNearbyEntities(minion, targetRadius, predication)) {
            if (!(entity instanceof EntityLiving)) continue;
            double distSqr = getHorDistSqr(entity.locX, findNearestFrom.locX, entity.locZ, findNearestFrom.locZ);
            if (distSqr > nearestTargetDistSqr) continue;
            nearestTargetDistSqr = distSqr;
            finalTarget = (EntityLiving) entity;
        }
        for (net.minecraft.server.v1_12_R1.Entity entity : getNearbyEntities(owner, targetRadius, predication)) {
            if (!(entity instanceof EntityLiving)) continue;
            double distSqr = getHorDistSqr(entity.locX, findNearestFrom.locX, entity.locZ, findNearestFrom.locZ);
            if (distSqr > nearestTargetDistSqr) continue;
            nearestTargetDistSqr = distSqr;
            finalTarget = (EntityLiving) entity;
        }
        minion.setGoalTarget(finalTarget, EntityTargetEvent.TargetReason.CUSTOM, finalTarget != owner);
    }
}
