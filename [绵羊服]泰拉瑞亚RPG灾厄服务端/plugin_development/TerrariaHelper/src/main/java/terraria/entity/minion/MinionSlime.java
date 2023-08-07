package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.entity.projectile.HitEntityInfo;
import terraria.util.*;
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
    // default constructor when the chunk loads with one of these custom entity to prevent bug
    public MinionSlime(World world) {
        super(world);
        die();
    }
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
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.DAMAGE_SOURCE, owner);
        EntityHelper.setDamageType(getBukkitEntity(), EntityHelper.DamageType.SUMMON);
        addScoreboardTag("noDamage");
        addScoreboardTag("noMelee");
        setCustomName(minionType);
        setCustomNameVisible(true);
        // most minions should not need any goal selector etc. that are redundant and laggy
        switch (minionType) {
            case "史莱姆宝宝":
            case "噬星者":
            case "小腐化史莱姆":
            case "小血腥史莱姆":
            case "海贝":
                break;
            default:
                this.goalSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        }
        this.targetSelector = new PathfinderGoalSelector(world != null && world.methodProfiler != null ? world.methodProfiler : null);
        switch (minionType) {
            case "史莱姆宝宝":
            case "噬星者":
            case "小腐化史莱姆":
            case "小血腥史莱姆":{
                this.damageInvincibilityTicks = 5;
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(1d);
                break;
            }
            case "海贝": {
                this.damageInvincibilityTicks = 5;
                getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.8d);
                ((LivingEntity) getBukkitEntity()).addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP,
                        999999,
                        1,
                        false,
                        false
                ));
                break;
            }
            case "小鬼":
            case "钨钢无人机":
            {
                index = (int) (Math.random() * 360);
                setNoGravity(true);
                break;
            }
            case "沼泽之眼":
            case "附魔飞刀": {
                damageInvincibilityTicks = 5;
                noclip = true;
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
            case "迷你灾厄": {
                new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, true,
                        "迷你灾难之眼", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, false,
                        "迷你灾祸之眼", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "迷你灾祸之眼": {
                attrMap.put("damage", attrMap.getOrDefault("damage", 10d) * 0.4);
                noclip = true;
                setNoGravity(true);
            }
            case "紫蝶": {
                new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, false,
                        "粉蝶", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "致命球": {
                damageInvincibilityTicks = 10;
                setNoGravity(true);
                break;
            }
            case "真菌块": {
                setSize(2, false);
                damageInvincibilityTicks = 5;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "远古岩鲨":
            case "极寒冰块": {
                setSize(3, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "瘟疫雌蜂": {
                setSize(3, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "星尘之龙": {
                damageInvincibilityTicks = 10;
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
                                        .setLength(3)
                                        .setTicksLinger(2)
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
                break;
            }
            case "七彩水晶": {
                setSize(3, false);
                noclip = true;
                setNoGravity(true);
                ArrayList<String> particleColor = new ArrayList<>(6);
                particleColor.add("255|0|0");
                particleColor.add("255|255|0");
                particleColor.add("0|255|0");
                particleColor.add("0|255|255");
                particleColor.add("0|0|255");
                particleColor.add("255|0|255");
                extraVariables.put("colors", particleColor);
                ArrayList<Location> locations = new ArrayList<>();
                for (int i = 0; i < 10; i ++)
                    locations.add(null);
                extraVariables.put("locations", locations);
                break;
            }
            case "月亮传送门": {
                setSize(6, false);
                noclip = true;
                setNoGravity(true);
                extraVariables.put("dmgCDs", new ArrayList<Entity>());
                break;
            }
            case "松鼠侍从":
            case "冰结体": {
                setSize(3, false);
                setNoGravity(true);
                break;
            }
            case "珊瑚堆": {
                setSize(2, false);
                break;
            }
            case "蜘蛛女王":
            case "脉冲炮塔": {
                setSize(3, false);
                break;
            }
            case "岩刺": {
                setSize(4, false);
                damageInvincibilityTicks = 4;
                break;
            }
            case "霜之华": {
                setSize(2, false);
                setNoGravity(true);
                break;
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
        return 2;
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
            MinionHelper.setTarget(this, ((CraftPlayer) owner).getHandle(), sentryOrMinion, targetNeedLineOfSight, protectOwner);
        // extra ticking AI
        Vector velocity = new Vector(motX / 0.91, motY / 0.98, motZ / 0.91);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner,
                sentryOrMinion ? EntityHelper.MetadataName.PLAYER_SENTRY_LIST : EntityHelper.MetadataName.PLAYER_MINION_LIST).value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        switch (minionType) {
            case "小鬼":
                // no breaking here. AI is handled below.
                setCustomName("小鬼§" + (index / 4) % 4);
            case "钨钢无人机": {
                // move towards enemy
                Location targetLoc;
                double angle = index * 3;
                if (target == owner)
                    targetLoc = target.getEyeLocation().add(MathHelper.xsin_degree(angle) * 2, 1, MathHelper.xcos_degree(angle) * 2);
                else
                    targetLoc = target.getEyeLocation().add(MathHelper.xsin_degree(angle) * 3, 4, MathHelper.xcos_degree(angle) * 3);
                velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                // tweak velocity
                {
                    double maxSpeed = 3;
                    double dist = velocity.length();
                    velocity.multiply(Math.min(1d / 15, maxSpeed / dist));
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
                        projectileVelocity.multiply(2.75);
                    } else {
                        projectileType = "钨钢光球";
                        projectileVelocity.multiply(2);
                    }
                    EntityHelper.spawnProjectile(minionBukkit, projectileVelocity, attrMap, projectileType);
                    shootDelay = (int) (Math.random() * 5) + 15;
                }
                extraVariables.put("shootDelay", shootDelay);
                break;
            }
            case "黑鹰战斗机": {
                if (!targetIsOwner) {
                    // velocity
                    velocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double distance = velocity.length();
                    if (distance < 1e-9) velocity = new Vector(0, 0.5, 0);
                    else if (distance < 6) velocity.multiply(-0.5 / distance);
                    else velocity.multiply(0.5 / distance);
                    // basic fire
                    if (index % 25 == 0) {
                        String ammoItem = ItemUseHelper.consumePlayerAmmo(owner,
                                (itemStack) -> itemStack.getType() == Material.SLIME_BALL, 0.5);
                        if (ammoItem != null) {
                            if (ammoItem.equals("火枪子弹")) ammoItem = "黑鹰子弹";
                            Vector projectileDir = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation(), 2.25);
                            EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, ammoItem);
                        }
                    }
                } else {
                    Location targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 4) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.6);
                    }
                }
                break;
            }
            case "海贝": {
                if (!targetIsOwner && damageCD.contains(target)) {
                    hasTeleported = true;
                    minionBukkit.teleport(target.getEyeLocation());
                }
                break;
            }
            case "脆弱之星":
            case "深海海星": {
                // setup target location
                Location targetLoc = target.getEyeLocation();
                // the minion will attempt to dash a bit under enemy's eye location
                if (!targetIsOwner) {
                    targetLoc.multiply(0.75).add(target.getLocation().toVector().multiply(0.25));
                }
                // setup velocity
                boolean shouldUpdateVelocity = targetIsOwner || index % 10 == 0;
                double maxSpeed = targetIsOwner ? 1 : 1.5;
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
                    // a bit under the eye
                    targetLoc.multiply(0.75).add(target.getLocation().toVector().multiply(0.25));
                }
                // setup velocity
                boolean shouldUpdateVelocity = targetIsOwner || index % 5 == 0;
                double maxSpeed = targetIsOwner ? 2 : 3;
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
            case "远古岩鲨": {
                // wonder around the owner
                if (targetIsOwner) {
                    Location targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 10) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.7);
                    }
                }
                // attack enemies
                else {
                    if (index > 0) {
                        // dash
                        if (minionBukkit.getLocation().distanceSquared(target.getEyeLocation()) < 225) {
                            index = -12;
                        }
                        // move towards enemy if not on dash cool down
                        else {
                            velocity = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation(), 2);
                            minionBukkit.addScoreboardTag("dashed");
                        }
                    }
                }
                break;
            }
            case "硫火搜寻者": {
                int dashDuration = 15;
                // velocity init
                if (index % dashDuration == 0) {
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
                        velocity.multiply(Math.max(1.25, distance / dashDuration) / distance);
                    } else {
                        velocity.multiply(Math.max(2, distance / dashDuration * 1.5) / distance);
                    }
                }
                // slow down
                if (index % dashDuration >= 10) {
                    velocity.multiply(0.8);
                }
                break;
            }
            case "致命球": {
                int roundDuration = 12;
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
                            velocity.multiply(Math.max(1.25, distance / roundDuration) / distance);
                        } else {
                            velocity.multiply(Math.max(3, distance / roundDuration * 1.5) / distance);
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
                            velocity.multiply(Math.max(distance / 15, 1) / distance);
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
                                velocity.multiply(Math.max(distance / 15, 0.75) / distance);
                            else
                                velocity.multiply(3 / distance);
                        }
                        break;
                    }
                    case 6: {
                        if (!targetIsOwner)
                            velocity.multiply(0.6);
                        break;
                    }
                }
                break;
            }
            case "迷你灾厄": {
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
                            velocity.multiply(Math.max(distance / 15, 1) / distance);
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
                // shoot fireball
                if (!targetIsOwner) {
                    if (index % 15 == 0) {
                        Vector projectileVelocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                        if (projectileVelocity.lengthSquared() > 1e-9) {
                            projectileVelocity.normalize().multiply(1.75);
                            EntityHelper.spawnProjectile(minionBukkit, projectileVelocity, attrMap, "硫磺火球");
                        }
                    }
                }
                break;
            }
            case "迷你灾难之眼": {
                // velocity
                switch (index % 12) {
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
                                velocity.multiply(Math.max(distance / 15, 0.75) / distance);
                            else
                                velocity.multiply(3 / distance);
                        }
                        break;
                    }
                    case 8: {
                        if (!targetIsOwner)
                            velocity.multiply(0.5);
                        break;
                    }
                }
                break;
            }
            case "迷你灾祸之眼": {
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
                            velocity.multiply(Math.max(distance / 15, 1) / distance);
                        }
                    } else {
                        velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.5);
                    }
                }
                // shoot flame
                if (!targetIsOwner) {
                    if (index % 3 == 0) {
                        Vector projectileVelocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                        if (projectileVelocity.lengthSquared() > 1e-9) {
                            projectileVelocity.normalize().multiply(1);
                            EntityHelper.spawnProjectile(minionBukkit, projectileVelocity, attrMap, "小型硫火喷射");
                        }
                    }
                }
                break;
            }
            case "紫蝶": {
                // idle
                if (targetIsOwner) {
                    Location targetLoc = target.getEyeLocation().add(
                            Math.random() * 10 - 5,
                            Math.random() * 3 + 2,
                            Math.random() * 10 - 5);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 9) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.6);
                    }
                }
                // dash
                else {
                    // velocity
                    switch (index % 12) {
                        case 0: {
                            Location targetLoc = target.getEyeLocation();
                            velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                            double distance = velocity.length();
                            if (distance > 1e-9) {
                                velocity.multiply(2.5 / distance);
                            }
                            break;
                        }
                        case 8: {
                            velocity.multiply(0.6);
                            break;
                        }
                    }
                }
                break;
            }
            case "粉蝶": {
                // idle
                if (targetIsOwner) {
                    Location targetLoc = target.getEyeLocation().add(
                            Math.random() * 10 - 5,
                            Math.random() * 3 + 2,
                            Math.random() * 10 - 5);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 9) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.6);
                    }
                }
                // hover around and shoot projectiles
                else {
                    Vector offset = MathHelper.getDirection(target.getEyeLocation(), minionBukkit.getEyeLocation(), 8);
                    Location targetLoc = target.getEyeLocation().add(offset);
                    velocity.add(MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, 0.3));
                    double velLen = velocity.length();
                    if (velLen > 0.75)
                        velocity.multiply(0.75 / velLen);

                    if (index % 50 == 0) {
                        Vector fwdDir = target.getEyeLocation().subtract( minionBukkit.getEyeLocation() ).toVector();
                        for (Vector projVel : MathHelper.getCircularProjectileDirections(3, 1,
                                22.5, fwdDir,1.75)) {
                            EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, "樱花弹");
                        }
                    }
                }
                break;
            }
            case "真菌块":
            case "沼泽之眼": {
                // setup target location
                Location targetLoc = target.getEyeLocation();
                // the minion will attempt to stay above owner
                if (targetIsOwner) {
                    targetLoc.add(0, 5, 0);
                }
                // the minion will attempt to dash a bit under enemy's eye location
                else {
                    targetLoc.multiply(0.75).add(target.getLocation().toVector().multiply(0.25));
                }
                // setup velocity
                double accelerationMag = targetIsOwner ? 0.1 : 0.75;
                double maxSpeed = targetIsOwner ? 1 : 1.5;
                velocity.add(MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, accelerationMag));
                double velLen = velocity.length();
                if (velLen > maxSpeed)
                    velocity.multiply(maxSpeed / velLen);
                break;
            }
            case "噬星者": {
                if (!targetIsOwner) {
                    switch (index % 50) {
                        case 30:
                        case 35:
                        case 40:
                            Vector shootDir = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                            EntityHelper.spawnProjectile(minionBukkit, shootDir,
                                    attrMap, "酸液滴");
                    }
                }
                break;
            }
            case "永夜眼球": {
                if (!targetIsOwner) {
                    // velocity
                    velocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double distance = velocity.length();
                    if (distance < 1e-9) velocity = new Vector(0, 0.5, 0);
                    else if (distance < 5) velocity.multiply(-0.5 / distance);
                    else velocity.multiply(0.5 / distance);
                    // basic fire
                    if (index % 12 == 0) {
                        Vector projectileDir = MathHelper.getDirection(
                                minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.75);
                        EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, "瘟疫细胞");
                    }
                } else {
                    Location targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 4) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.5);
                    }
                }
                break;
            }
            case "迷你世纪之花": {
                // rotate above player when idle
                if (targetIsOwner) {
                    Location targetLoc = target.getLocation().add(
                            MathHelper.xsin_degree(index * 3) * 5,
                            5,
                            MathHelper.xcos_degree(index * 3) * 5);
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                }
                // charge against enemy and shoot projectiles
                else {
                    // charge
                    {
                        velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.75);
                    }
                    // projectiles
                    if (index % 8 == 0) {
                        double randomNum = Math.random();
                        int shootAmount;
                        double speed;
                        EntityHelper.ProjectileShootInfo shootInfo;
                        if (randomNum < 0.7) {
                            shootAmount = (Math.random() < 0.35) ? 3 : 1;
                            speed = 1.25;
                            shootInfo = new EntityHelper.ProjectileShootInfo(
                                    minionBukkit, new Vector(), attrMap, (randomNum < 0.5) ? "种子" : "毒种子");
                            shootInfo.properties.put("penetration", 0);
                        }
                        else if (randomNum < 0.85) {
                            shootAmount = 3;
                            speed = 1.5;
                            shootInfo = new EntityHelper.ProjectileShootInfo(
                                    minionBukkit, new Vector(), attrMap, "孢子云");
                            shootInfo.properties.put("liveTime", 175);
                        }
                        else {
                            shootAmount = 1;
                            speed = 1;
                            shootInfo = new EntityHelper.ProjectileShootInfo(
                                    minionBukkit, new Vector(), attrMap, "刺球");
                        }
                        for (Vector projVel : MathHelper.getCircularProjectileDirections(shootAmount, 2, 60,
                                target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector(), speed)) {
                            shootInfo.velocity = projVel;
                            EntityHelper.spawnProjectile(shootInfo);
                        }
                    }
                }
                break;
            }
            case "鼠尾草之灵": {
                // stay around the target; if the target is an enemy, the follow radius gets bigger.
                {
                    // get minion index
                    int indexCurr = 0, indexMax = 0;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax ++;
                    }
                    // setup target location
                    Location targetLoc = target.getLocation().add(0, 1, 0);
                    Vector locOffset = MathHelper.vectorFromYawPitch_quick(indexCurr * 360d / indexMax, 0);
                    locOffset.multiply(targetIsOwner ? 3 : 5);
                    targetLoc.add(locOffset);
                    // velocity
                    velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 2, true);
                }
                // shoot at the target
                if (!targetIsOwner && index % 15 == 0) {
                    Vector shootFwdDir = MathHelper.getDirection(
                            minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                            minionBukkit, new Vector(), attrMap, "鼠尾草针叶");
                    for (Vector shootVel : MathHelper.getCircularProjectileDirections(
                            3, 1, 22.5, shootFwdDir, 1.75)) {
                        shootInfo.velocity = shootVel;
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
                break;
            }
            case "沙龙卷":
            case "暴风雨": {
                // movement
                Location targetLoc = target.getEyeLocation();
                if (targetIsOwner) {
                    targetLoc.add(
                            MathHelper.xsin_degree(index * 75) * 5,
                            5,
                            MathHelper.xcos_degree(index * 75) * 5);
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                }
                else {
                    targetLoc.add(0, 2, 0);
                }
                velocity.add(MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 0.6));
                double velLen = velocity.length();
                if (velLen > 1.5) velocity.multiply(1.5 / velLen);
                // shoot projectiles
                if (!targetIsOwner && index % 10 == 0) {
                    EntityHelper.spawnProjectile(minionBukkit,
                            MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.75),
                            attrMap, minionType.equals("沙龙卷") ? "微型沙鲨" : "迷你鲨鱼龙");
                }
                break;
            }
            case "畸变吞食者": {
                // follow owner when idle
                if (targetIsOwner) {
                    Location targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 10) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.75);
                    }
                }
                // attack
                else {
                    // velocity
                    velocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double distance = velocity.length();
                    if (distance < 1e-9) velocity = new Vector(0, 0.5, 0);
                    else if (distance < 6) velocity.multiply(-0.5 / distance);
                    else velocity.multiply(0.75 / distance);
                    // fire vomit
                    if (index % 10 == 0) {
                        Vector projectileDir = MathHelper.getDirection(
                                minionBukkit.getEyeLocation(), target.getEyeLocation(), 2.25);
                        EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, "呕吐物");
                    }
                    // fire bubble
                    if (index % 30 == 0) {
                        for (int i = 0; i < 5; i ++) {
                            Vector offset = MathHelper.randomVector();
                            offset.multiply(2.5);
                            Vector projectileDir = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation().add(offset), 1);
                            EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, "硫酸泡泡");
                        }
                    }
                }
                break;
            }
            case "迷你瘟疫使者": {
                // stay behind the target
                {
                    // get the index of current minion
                    double indexCurr = 0;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (currMinion == minionBukkit)
                            break;
                        indexCurr++;
                    }
                    // get the location for this minion
                    Location targetLoc = target.getEyeLocation();
                    EntityLiving targetNMS = ((CraftLivingEntity) target).getHandle();
                    Vector offset = MathHelper.vectorFromYawPitch_quick(targetNMS.yaw + 180, 0);
                    offset.multiply(indexCurr + 1);
                    targetLoc.add(offset);
                    // set velocity
                    velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 1, true);
                }
                // the attack speed stack resets
                if (targetIsOwner) {
                    index = -1;
                }
                else if (index % 30 == 0) {
                    // shoot projectile
                    EntityHelper.spawnProjectile(minionBukkit, MathHelper.getDirection(minionBukkit.getEyeLocation(),
                            target.getEyeLocation(), 2), attrMap,
                            Math.random() < 0.75 ? "爆炸导弹" : "追踪瘟疫导弹");
                    // this minion gains stacking attack speed, maximizes after 30 attacks
                    index += Math.min(index / 45, 20);
                }
                break;
            }
            case "瘟疫雌蜂": {
                // resets attack pattern and return to owner
                if (targetIsOwner) {
                    Location
                            targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 5,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 16) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(0.75);
                    }
                    index = -1;
                }
                // attack
                else {
                    // dash 6 times
                    if (index < 90) {
                        int phaseIndex = index % 15;
                        // move diagonally above enemy
                        if (phaseIndex < 6) {
                            Location tempLoc = minionBukkit.getEyeLocation();
                            tempLoc.setY(target.getEyeLocation().getY());
                            Vector hoverDir = MathHelper.getDirection(tempLoc, target.getEyeLocation(), 6);
                            hoverDir.setY(-4);
                            Location hoverLoc = target.getEyeLocation().subtract(hoverDir);

                            velocity = hoverLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                            velocity.multiply(1d / (6 - phaseIndex));
                        }
                        // dash into enemy
                        else if (phaseIndex == 6) {
                            velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.75);
                        }
                    }
                    // release a barrage of missiles for 6 times
                    else if (index < 210) {
                        int phaseIndex = (index - 90) % 20;
                        // move diagonally above enemy
                        if (phaseIndex < 8) {
                            Location tempLoc = minionBukkit.getEyeLocation();
                            tempLoc.setY(target.getEyeLocation().getY());
                            Vector hoverDir = MathHelper.getDirection(tempLoc, target.getEyeLocation(), 10);
                            hoverDir.setY(-6);
                            Location hoverLoc = target.getEyeLocation().subtract(hoverDir);

                            velocity = hoverLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                            velocity.multiply(1d / (8 - phaseIndex));
                        }
                        // dash horizontally
                        else if (phaseIndex == 8) {
                            Location tempLoc = minionBukkit.getEyeLocation();
                            tempLoc.setY(target.getEyeLocation().getY());
                            velocity = MathHelper.getDirection(tempLoc, target.getEyeLocation(), 1.5);
                        }
                        // launch missiles
                        switch (phaseIndex) {
                            case 9:
                            case 11:
                            case 13:
                            case 15:
                            case 17:
                            case 19:
                                Vector missileVel = MathHelper.getDirection(
                                        minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.75);
                                EntityHelper.spawnProjectile(minionBukkit, missileVel, attrMap, "追踪瘟疫导弹");
                        }
                    }
                    // hover and send 12 bees
                    else if (index < 330) {
                        Location targetLoc = target.getEyeLocation().add(0, 8, 0);
                        velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc,
                                2, true);
                        // spawn bees
                        if (index % 10 == 8) {
                            Vector beeVel = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation(), 1.25);
                            EntityHelper.spawnProjectile(minionBukkit, beeVel, attrMap, "瘟疫蜜蜂");
                        }
                    }
                    // repeat the cycle
                    else {
                        index = -1;
                    }
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
                ArrayList<LivingEntity> allSegments = new ArrayList<>(allMinions.size());
                for (Entity currMinion : allMinions) {
                    if (currMinion.isDead()) continue;
                    if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                    if (allSegments.size() == 0 && currMinion != minionBukkit) {
                        isHeadSegment = false;
                        break;
                    }
                    allSegments.add((LivingEntity) currMinion);
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
                            v.multiply(Math.max(distance / 15, 1)  / distance);
                            velocity = v;
                        }
                    } else {
                        // speed: 1 to 6(max at 20 segments)
                        double speed = 1 + Math.min( (double) (allSegments.size()) / 4, 5);
                        double distanceSqr = target.getEyeLocation().distanceSquared(minionBukkit.getEyeLocation()),
                                distMax = ((double) allSegments.size() + 1) / 2;
                        Vector v = EntityHelper.helperAimEntity(minionBukkit, target,
                                new EntityHelper.AimHelperOptions()
                                        .setProjectileSpeed(speed))
                                .subtract(minionBukkit.getEyeLocation())
                                .toVector();
                        double vLen = v.length();
                        if (vLen < 1e-9) {
                            v = new Vector(0, 1, 0);
                            vLen = 1;
                        }
                        if (velocity.lengthSquared() < 1e-5 || distanceSqr > distMax * distMax) {
                            v.multiply( speed / vLen);
                            velocity = v;
                        }
                    }
                    EntityHelper.handleSegmentsFollow(allSegments,
                            new EntityHelper.WormSegmentMovementOptions()
                                    .setStraighteningMultiplier(-0.75)
                                    .setFollowingMultiplier(1)
                                    .setFollowDistance(0.5)
                                    .setVelocityOrTeleport(false));
                }
                // body segments should not have velocity
                else {
                    velocity = new Vector(0, 0, 0);
                }
                // set display name according to segment info
                if (isHeadSegment) {
                    setCustomName(minionType + "§1");
                } else {
                    setCustomName(minionType);
                }
                break;
            }
            case "幻星探测器": {
                // stay around the owner
                {
                    // get minion index
                    Entity firstMinion = minionBukkit;
                    int indexCurr = 0, indexMax = 0;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax ++;
                    }
                    // setup target location
                    Location targetLoc = owner.getLocation().add(0, 1, 0);
                    Vector locOffset = MathHelper.vectorFromYawPitch_quick(
                            firstMinion.getTicksLived() * 4 + indexCurr * 360d / indexMax, 0);
                    locOffset.multiply(4.5);
                    targetLoc.add(locOffset);
                    // velocity
                    velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 2, true);
                }
                // shoot at the target
                if (!targetIsOwner && index % 12 == 0) {
                    // predict location
                    Location shootAimLoc = EntityHelper.helperAimEntity(minionBukkit, target,
                            new EntityHelper.AimHelperOptions()
                                    .setProjectileSpeed(2.25));
                    Vector projVel = MathHelper.getDirection(
                            minionBukkit.getEyeLocation(), shootAimLoc, 2.25);
                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                            minionBukkit, projVel, attrMap, "星幻激光");
                    shootInfo.properties.put("penetration", 0);
                    EntityHelper.spawnProjectile(shootInfo);
                }
                break;
            }
            case "熔火古刀": {
                // stay around the owner
                {
                    Entity firstMinion = minionBukkit;
                    int indexCurr = 0, indexMax = 0;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax ++;
                    }
                    velocity = new Vector();
                    Location targetLoc = owner.getLocation().add(0, 1, 0);
                    Vector locOffset = MathHelper.vectorFromYawPitch_quick(
                            firstMinion.getTicksLived() * 6 + indexCurr * 360d / indexMax, 0);
                    locOffset.multiply(3);
                    targetLoc.add(locOffset);
                    minionBukkit.teleport(targetLoc);
                }
                // strike the target if it comes close
                if (!targetIsOwner && index % 10 == 0 &&
                        target.getLocation().distanceSquared(bukkitEntity.getLocation()) < 100) {
                    Vector strikeDir = MathHelper.getDirection(
                            minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                    GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                            .setDecayCoef(0.6);
                    GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                            MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                            10, 0.1, 0.75, "", "100|30|50",
                            new ArrayList<>(), attrMap, strikeOption);
                }
                break;
            }
            case "泰拉棱镜": {
                double currYaw = (double) extraVariables.getOrDefault("yaw", 0.0);
                double currPitch = (double) extraVariables.getOrDefault("pitch", 0.0);
                double dYaw = (double) extraVariables.getOrDefault("dYaw", 0.0);
                double dPitch = (double) extraVariables.getOrDefault("dPitch", 0.0);
                // handle velocity and dYaw dPitch
                if (!targetIsOwner) {
                    int ind = index % 16;
                    Vector direction = target.getEyeLocation().subtract(minionBukkit.getLocation()).toVector();
                    if (direction.lengthSquared() < 1e-9) direction = new Vector(0, 1, 0);
                    // fly upward
                    if (ind < 8) {
                        if (ind == 0) {
                            Vector dir = direction.clone().subtract(new Vector(0, 6, 0));
                            if (dir.lengthSquared() < 1e-9) dir = new Vector(0, 1, 0);
                            double[] newDeltaDir = GenericHelper.getDirectionInterpolateOffset(
                                    currYaw, currPitch, MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir), 1d / 8);
                            dYaw = newDeltaDir[0];
                            dPitch = newDeltaDir[1];
                        } else if (ind == 7) {
                            extraVariables.put("tgt", target);
                        }
                        velocity = new Vector(0, 0.75, 0);
                    }
                    // charge targeted enemy
                    else if (ind < 14) {
                        direction = ((LivingEntity) (extraVariables.getOrDefault("tgt", target))).getEyeLocation()
                                .subtract(minionBukkit.getLocation()).toVector();
                        dYaw = 0;
                        dPitch = 0;
                        currYaw = MathHelper.getVectorYaw(direction);
                        currPitch = MathHelper.getVectorPitch(direction);
                        velocity = direction;
                        double distance = velocity.length();
                        if (distance > 1e-9)
                            velocity.multiply(Math.max(distance / (14 - ind), 2 + Math.random())  / distance);
                    }
                    // make a group of prisms attack at slightly different pace, adding some randomness to it
                    else if (Math.random() < 0.2)
                        index --;
                }
                // target is owner
                else {
                    // the next time any enemy is spotted, the minions are ready to attack
                    index = 15;
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
                    double[] newDeltaDir = GenericHelper.getDirectionInterpolateOffset(
                            currYaw, currPitch, targetYaw, targetPitch, 0.25);
                    dYaw = newDeltaDir[0];
                    dPitch = newDeltaDir[1];
                    // every prism should move periodically according to the first prism.
                    Vector dPos = MathHelper.vectorFromYawPitch_quick(targetYaw, targetPitch);
                    Location targetLoc = owner.getLocation().add(dPos.multiply(
                            idleIndex / 3 + 1.5 ));
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                    velocity.multiply(0.35);
                }
                double[] newDir = GenericHelper.interpolateDirection(currYaw, currPitch, dYaw, dPitch);
                currYaw = newDir[0];
                currPitch = newDir[1];
                // handle strike
                GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(), currYaw, currPitch, 3.0, 0.2,
                        "", "0|0|0", damageCD, (HashMap<String, Double>) attrMap.clone(),
                        (GenericHelper.StrikeLineOptions) extraVariables.get("strikeLineOption"));
                extraVariables.put("yaw", currYaw);
                extraVariables.put("pitch", currPitch);
                extraVariables.put("dYaw", dYaw);
                extraVariables.put("dPitch", dPitch);
                break;
            }
            case "霜之华": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 32) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setMaxTargetHit(1)
                                .setDamagedFunction((strikeNum, entityHit, hitLoc) -> {
                                    ArrayList<Entity> exceptions = new ArrayList<>();
                                    exceptions.add(entityHit);
                                    EntityHelper.handleEntityExplode(minionBukkit, 1, exceptions, hitLoc);
                                });
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.1, 0.5, "", "163|244|255",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "太阳之灵": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 32) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setMaxTargetHit(2);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.1, 0.5, "", "255|225|122",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "烬之英": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 10 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            40, false);
                    double length = strikeDir.length();
                    GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                            .setLingerDelay(3)
                            .setDamagedFunction((strikeNum, entityHit, hitLoc) -> {
                                ArrayList<Entity> exceptions = new ArrayList<>();
                                exceptions.add(entityHit);
                                EntityHelper.handleEntityExplode(minionBukkit, 1, exceptions, hitLoc);
                            });
                    GenericHelper.handleStrikeLightning(minionBukkit, minionBukkit.getEyeLocation(),
                            MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                            length, 4, 0.1, 0.5, 0, 1,"255|160|80",
                            new ArrayList<>(), attrMap, strikeOption);
                }
                break;
            }
            case "微型克苏鲁之眼": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 30 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 48) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setMaxTargetHit(2);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.5, "", "255|200|0",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "极寒冰块": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 10 == 0) {
                    Vector shootDir = MathHelper.vectorFromYawPitch_quick(index * 4.5, 0);
                    shootDir.multiply(1.25);
                    EntityHelper.spawnProjectile(minionBukkit, shootDir,
                            attrMap, "冰钉");
                }
                break;
            }
            case "永冻之焱华": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    double shootYaw = Math.random() * 360;
                    for (int i = 0; i < 3; i ++) {
                        Vector shootDir = MathHelper.vectorFromYawPitch_quick(shootYaw, 0);
                        shootDir.multiply(1.5);
                        EntityHelper.spawnProjectile(minionBukkit, shootDir,
                                attrMap, "追踪花球");
                        shootYaw += 120;
                    }
                }
                break;
            }
            case "架式扫射机": {
                // stay above the owner
                {
                    Entity firstMinion = minionBukkit;
                    int indexCurr = 0, indexMax = 0;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax++;
                    }
                    Location location = owner.getEyeLocation();
                    location.add(0, 2, 0);
                    if (indexMax >= 1) {
                        double angle = 360d * indexCurr / indexMax + firstMinion.getTicksLived() * 5;
                        location.add(MathHelper.xsin_degree(angle) * 2, 0, MathHelper.xcos_degree(angle) * 2);
                    }
                    minionBukkit.teleport(location);
                }
                // shoots a laser at the target
                if (!targetIsOwner && index % 15 == 0) {
                    Vector strikeDir = MathHelper.getDirection(
                            minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                    GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                            .setDecayCoef(0.5);
                    GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                            MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                            48, 0.1, 0.5, "", "165|45|35",
                            new ArrayList<>(), attrMap, strikeOption);
                }
                break;
            }
            case "太阳神之灵": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 8 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 48) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setMaxTargetHit(2);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.1, 0.5, "", "255|225|122",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "凋零枯花": {
                // teleport above owner
                minionBukkit.teleport(owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    double shootYaw = Math.random() * 360;
                    for (int i = 0; i < 4; i ++) {
                        Vector shootDir = MathHelper.vectorFromYawPitch_quick(shootYaw, 0);
                        EntityHelper.spawnProjectile(minionBukkit, shootDir,
                                attrMap, "瘟疫矢");
                        shootYaw += 90;
                    }
                }
                break;
            }
            case "松鼠侍从":
            case "珊瑚堆":
            case "冰结体":
            case "蜘蛛女王": {
                if (targetIsOwner)
                    index = 0;
                else {
                    int shootInterval;
                    double shootSpeed;
                    String projectileType;
                    switch (minionType) {
                        case "松鼠侍从":
                            shootInterval = 15;
                            shootSpeed = 2;
                            projectileType = "橡子";
                            velocity = new Vector(0, -0.05, 0);
                            break;
                        case "珊瑚堆":
                            shootInterval = 10;
                            shootSpeed = 1;
                            projectileType = "珊瑚块";
                            break;
                        case "冰结体":
                            shootInterval = 5;
                            shootSpeed = 1.5;
                            projectileType = "冰锥";
                            break;
                        case "蜘蛛女王":
                            shootInterval = 15;
                            shootSpeed = 2.5;
                            projectileType = "蜘蛛卵";
                            break;
                        default:
                            shootInterval = 20;
                            shootSpeed = 1;
                            projectileType = "";
                    }
                    if (index % shootInterval == 0) {
                        Vector v = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), shootSpeed);
                        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                minionBukkit, v, attrMap, projectileType);
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
                break;
            }
            case "脉冲炮塔": {
                // attack
                if (!targetIsOwner && index % 15 == 0) {
                        Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                                512, true);
                        double length = strikeDir.length();
                        if (length < 40) {
                            GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                    .setMaxTargetHit(1)
                                    .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                            .setParticleColor("164|255|255", "243|136|248")
                                            .setTicksLinger(8));
                            GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                    MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                    length, 0.1, 0.5, "", "164|255|255",
                                    new ArrayList<>(), attrMap, strikeOption);
                        }
                    }
                break;
            }
            case "七彩水晶": {
                if (index % 8 == 0) {
                    ArrayList<String> colors = (ArrayList<String>) extraVariables.get("colors");
                    ArrayList<Location> lastLocations = (ArrayList<Location>) extraVariables.get("locations");
                    int indexInList = index % 16 == 0 ? 0 : 5;
                    // detonate
                    for (int idx = 0; idx < 5; idx ++) {
                        Location currLoc = lastLocations.get(indexInList);
                        if (currLoc != null) {
                            EntityHelper.handleEntityExplode(bukkitEntity, new ArrayList<>(), currLoc);
                            lastLocations.set(indexInList, null);
                        }
                        indexInList ++;
                    }
                    // new locations
                    if (!targetIsOwner) {
                        String color = colors.get( (int) (Math.random() * colors.size()) );
                        Location predictedLoc = EntityHelper.helperAimEntity(bukkitEntity, target,
                                new EntityHelper.AimHelperOptions()
                                        .setAimMode(true)
                                        .setTicksOffset(10));
                        GenericHelper.ParticleLineOptions particleInfo = new GenericHelper.ParticleLineOptions()
                                .setWidth(0.25)
                                .setStepsize(0.6)
                                .setParticleColor(color)
                                .setTicksLinger(10);
                        for (int i = (int) (Math.random() * 3); i < 5; i ++) {
                            Vector offset = MathHelper.randomVector();
                            offset.multiply(4);
                            Location targetLoc = predictedLoc.clone().add(offset);
                            lastLocations.set(indexInList % 10, targetLoc);
                            indexInList ++;
                            // handle particle line
                            Vector particleDir = targetLoc.clone().subtract(minionBukkit.getEyeLocation()).toVector();
                            GenericHelper.handleParticleLine(particleDir, minionBukkit.getEyeLocation(),
                                    particleInfo
                                            .setLength(particleDir.length()));
                        }
                    }
                }
                break;
            }
            case "月亮传送门": {
                if (targetIsOwner)
                    index = 0;
                else {
                    if (index == 8) {
                        Vector dir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                        extraVariables.put("y", MathHelper.getVectorYaw(dir));
                        extraVariables.put("p", MathHelper.getVectorPitch(dir) + 5);
                    } else if (index > 8) {
                        double yaw = (double) extraVariables.get("y");
                        double pitch = (double) extraVariables.get("p");
                        ArrayList<Entity> damageExceptions = (ArrayList<Entity>) extraVariables.get("dmgCDs");
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(), yaw, pitch,
                                64, 1.5, "", "0|225|125",
                                damageExceptions, attrMap,
                                new GenericHelper.StrikeLineOptions()
                                        .setThruWall(false)
                                        .setDamageCD(5)
                                        .setLingerDelay(1));
                        pitch--;
                        extraVariables.put("p", pitch);
                        // reset
                        if (index > 25) {
                            index = -1;
                        }
                    }
                }

                break;
            }
        }
        // strike all enemies in path
        if (hasContactDamage) {
            Set<HitEntityInfo> hitInfo = MinionHelper.handleContactDamage(this, hasTeleported,
                    getSize() * 0.5, basicDamage, damageCD, damageInvincibilityTicks);
            switch (minionType) {
                // heals the owner
                case "真菌块":
                    if (!hitInfo.isEmpty())
                        PlayerHelper.heal(owner, 1, false);
                    break;
                // receives knockback on hit
                case "远古岩鲨":
                    if (!hitInfo.isEmpty() && minionBukkit.getScoreboardTags().contains("dashed")) {
                        velocity.multiply(-1);
                        minionBukkit.removeScoreboardTag("dashed");
                    }
                    break;
            }
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
