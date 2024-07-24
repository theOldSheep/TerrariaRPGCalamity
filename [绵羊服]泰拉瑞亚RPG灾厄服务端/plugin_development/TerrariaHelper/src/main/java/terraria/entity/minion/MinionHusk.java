package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
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
import terraria.util.ItemUseHelper;
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
            case "代达罗斯守卫":
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
            case "代达罗斯守卫":
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
            case "葱茏之锋": {
                damageInvincibilityTicks = 5;
                setBaby(false);
                noclip = true;
                setNoGravity(true);
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
                                attrMap, "剧毒花瓣");
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
                    Vector offset = MathHelper.vectorFromYawPitch_approx(plyNMS.yaw + 180, 0);
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
                        String projType = "蚀骨尖刺";
                        double projSpd = 1.5;
                        Location aimLoc = EntityHelper.helperAimEntity(bukkitEntity, target,
                                new EntityHelper.AimHelperOptions(projType).setProjectileSpeed(projSpd));
                        EntityHelper.spawnProjectile(minionBukkit, MathHelper.getDirection(minionBukkit.getEyeLocation(),
                                aimLoc, projSpd), attrMap, projType);
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
                if (!targetIsOwner && ticksLived % 8 == 0) {
                    String projType = "缠怨鬼碟";
                    double projSpd = 2;
                    Location aimLoc = EntityHelper.helperAimEntity(bukkitEntity, target,
                            new EntityHelper.AimHelperOptions(projType).setProjectileSpeed(projSpd));
                    Vector projVel = MathHelper.getDirection(minionBukkit.getEyeLocation(), aimLoc, projSpd);
                    EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, projType);
                }
                break;
            }
            case "代达罗斯守卫": {
                // reset attack
                if (targetIsOwner) {
                    index = -1;
                }
                // attack enemy
                else {
                    // attack using spheres
                    if (index > 0) {
                        if (index % 10 == 0) {
                            Vector projVel = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation(), 2.5);
                            EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                    minionBukkit, projVel, attrMap, "紫蓝色小球");
                            EntityHelper.spawnProjectile(shootInfo);
                            // a small chance to initialize a lightning bolt section
                            if (Math.random() < 0.2)
                                index = -40;
                        }
                    }
                    // strike lightning bolts
                    else {
                        switch (index) {
                            case -20:
                            case -15:
                            case -10:
                            case -5:
                                Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                                        1, false);
                                GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                        .setLingerDelay(10);
                                int greenBlueValue = (int) (Math.random() * 150);
                                GenericHelper.handleStrikeLightning(minionBukkit, minionBukkit.getEyeLocation(),
                                        MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                        57.5, 6, 0.5, 1,3.5, 2,
                                        "255|" + greenBlueValue + "|" + greenBlueValue,
                                        new ArrayList<>(), attrMap, strikeOption);
                                minionBukkit.getWorld().playSound(
                                        minionBukkit.getLocation(), ItemUseHelper.SOUND_BOW_SHOOT, 3, 1);
                        }
                    }
                }
                break;
            }
            case "矮人": {
                if (!targetIsOwner && ticksLived % 12 == 0) {
                    Location targetLoc = EntityHelper.helperAimEntity(minionBukkit, target,
                            new EntityHelper.AimHelperOptions("投矛")
                                    .setProjectileSpeed(2.5));
                    Vector v = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, 2.5);
                    EntityHelper.spawnProjectile(minionBukkit, v, attrMap, "投矛");
                }
                break;
            }
            case "葱茏之锋": {
                // this minion is constantly being teleported to destination.
                // rotate around owner if idle
                if (targetIsOwner || index < 0) {
                    // next attack for all blades should be almost identical
                    if (targetIsOwner)
                        index = (int) (Math.random() * -3) - 15;

                    double indexCurr = 0, indexMax = 0;
                    Location targetLoc = target.getLocation();
                    Entity firstMinion = minionBukkit;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax ++;
                    }
                    double angle = firstMinion.getTicksLived() * 5;
                    if (indexMax > 1)
                        angle += 360 * indexCurr / indexMax;
                    targetLoc.add(MathHelper.xsin_degree(angle) * 3, 0, MathHelper.xcos_degree(angle) * 3);

                    velocity = MathHelper.getDirection(
                            bukkitEntity.getLocation(), targetLoc, 3, true);
                }
                else {
                    // ↑↓↑
                    int subIdx = index % 86;
                    if (subIdx < 36) {
                        double phaseProgress = Math.sqrt( (subIdx % 9) / 9d );
                        double verticalOffsetMulti;
                        if (subIdx < 9)
                            verticalOffsetMulti = 2 * phaseProgress - 1;
                        else if (subIdx < 18)
                            verticalOffsetMulti = 1 - 2 * phaseProgress;
                        else if (subIdx < 27)
                            verticalOffsetMulti = 1.75 * phaseProgress - 1;
                        else
                            verticalOffsetMulti = 0.75 + (subIdx % 9) / 36d;
                        Location targetLoc = target.getEyeLocation().add(0, verticalOffsetMulti * 10, 0);

                        velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
                    }
                    // ←→←→←
                    else {
                        subIdx -= 36;
                        double phaseProgress = ( subIdx % 7) / 7d;
                        double horizontalOffsetMulti;
                        if (subIdx < 7)
                            horizontalOffsetMulti = 2 * phaseProgress - 1;
                        else if (subIdx < 14)
                            horizontalOffsetMulti = 1 - 2 * phaseProgress;
                        else if (subIdx < 21)
                            horizontalOffsetMulti = 2 * phaseProgress - 1;
                        else if (subIdx < 28)
                            horizontalOffsetMulti = 1 - 2 * phaseProgress;
                        else if (subIdx < 35)
                            horizontalOffsetMulti = 1.75 * phaseProgress - 1;
                        else
                            // 15 * 4
                            horizontalOffsetMulti = 0.75 + (subIdx - 35) / 60d;
                        Vector offset = target.getLocation().subtract(owner.getLocation()).toVector();
                        offset = MathHelper.vectorFromYawPitch_approx( MathHelper.getVectorYaw(offset) + 90, 0 );
                        Location targetLoc = target.getEyeLocation().add( offset.multiply( horizontalOffsetMulti * 10 ) );

                        velocity = targetLoc.subtract(bukkitEntity.getLocation()).toVector();
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
