package terraria.entity.minion;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.entity.projectile.HitEntityInfo;
import terraria.entity.projectile.GenericProjectile;
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
        setSize(1, false);
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        addScoreboardTag("isMinion");
        EntityHelper.setMetadata(getBukkitEntity(), EntityHelper.MetadataName.DAMAGE_SOURCE, owner);
        DamageHelper.setDamageType(getBukkitEntity(), DamageHelper.DamageType.SUMMON);
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
            case "小血腥史莱姆": {
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
            case "脆弱之星":
            case "沼泽之眼":
            case "附魔飞刀":
            case "深海海星":
            case "硫火搜寻者":
            case "乌鸦仆从":
            case "耀目圣刃":
            case "灭兆渡鸦": {
                this.damageInvincibilityTicks = 5;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "小鬼":
            case "钨钢无人机": {
                index = (int) (Math.random() * 360);
                setNoGravity(true);
                break;
            }
            case "元素斧头": {
                damageInvincibilityTicks = 5;
                noclip = true;
                setNoGravity(true);
                setSize(3, false);
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
                        "迷你灾难", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, false,
                        "迷你灾祸", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "迷你灾祸": {
                attrMap.put("damage", attrMap.getOrDefault("damage", 10d) * 0.4);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "紫蝶": {
                new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, false,
                        "粉蝶", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "血炎骨龙": {
                for (int i = 0; i < 2; i ++)
                    new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                            this.sentryOrMinion, false,
                            "血炎骨龙宝宝", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                setSize(3, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "血炎骨龙宝宝": {
                index = (int) (Math.random() * 100);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "磁铁无人机": {
                if (this.minionInList == bukkitEntity)
                    new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                            this.sentryOrMinion, false,
                            "磁铁无人机", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "光阴流时伞": {
                new MinionHusk(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                        this.sentryOrMinion, true,
                        "葱茏之锋", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                String[] meleeExtraMinions = {"耀目圣刃", "灭兆渡鸦"},
                        rangedExtraMinions = {"宇宙炮艇", "极昼飞行物"};
                for (String minion : meleeExtraMinions)
                    new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                            this.sentryOrMinion, true,
                            minion, (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                for (String minion : rangedExtraMinions)
                    new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                            this.sentryOrMinion, false,
                            minion, (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());

                setSize(3, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "小乌贼": {
                index = (int) (Math.random() * 100);
                damageInvincibilityTicks = 7;
                noclip = true;
                setNoGravity(true);
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
            case "极寒冰块":
            case "永劫信标":
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
            case "小星宇神卫": {
                setSize(2, false);
                damageInvincibilityTicks = 5;
                protectOwner = false;
                targetNeedLineOfSight = false;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "黑色天龙":
            case "黑色天龙体节":
            case "白色天龙":
            case "白色天龙体节": {
                // for heads only
                switch (minionType) {
                    case "黑色天龙":
                        new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                            this.sentryOrMinion, true,
                            "白色天龙", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                    case "白色天龙": {
                        ArrayList<LivingEntity> segments = new ArrayList<>(11);
                        // add this head
                        segments.add((LivingEntity) bukkitEntity);
                        // add body
                        for (int i = 0; i < 10; i ++) {
                            MinionSlime newSeg = new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                                    this.sentryOrMinion, true,
                                    minionType + "体节", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                            segments.add((LivingEntity) newSeg.getBukkitEntity());
                        }
                        // save segment info
                        extraVariables.put("s", segments);
                    }
                }
                // basic attributes
                index = -10;
                setSize(2, false);
                damageInvincibilityTicks = 10;
                targetNeedLineOfSight = false;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "灾坟仆从":
            case "灾坟仆从体节":
            case "灾坟仆从尾": {
                // for heads only, summon body segments
                if (minionType.equals("灾坟仆从")) {
                    ArrayList<LivingEntity> segments = new ArrayList<>(11);
                    // add this head
                    segments.add((LivingEntity) bukkitEntity);
                    // add body (10 segments)
                    for (int i = 0; i < 10; i++) {
                        MinionSlime newSeg1 = new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                                this.sentryOrMinion, true,
                                minionType + "体节", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                        segments.add((LivingEntity) newSeg1.getBukkitEntity());
                    }
                    // add tail
                    MinionSlime tailSeg = new MinionSlime(this.owner, this.minionSlot, this.minionSlotMax, this.minionInList,
                            this.sentryOrMinion, true,
                            minionType + "尾", (HashMap<String, Double>) this.attrMap.clone(), this.originalStaff.clone());
                    segments.add((LivingEntity) tailSeg.getBukkitEntity());
                    // save segment info
                    extraVariables.put("s", segments);
                }

                // basic attributes
                setSize(3, false);
                targetNeedLineOfSight = false;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "炽焰天龙": {
                setSize(5, false);
                damageInvincibilityTicks = 7;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "泰拉棱镜": {
                GenericHelper.StrikeLineOptions strikeLineOption =
                        new GenericHelper.StrikeLineOptions()
                                .setDamageCD(damageInvincibilityTicks)
                                .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                        .setVanillaParticle(false)
                                        .setWidth(0.25)
                                        .setLength(4)
                                        .setTicksLinger(1)
                                        .setParticleColor("m/trp"));
                extraVariables.put("strikeLineOption", strikeLineOption);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "极昼飞行物": {
                GenericHelper.StrikeLineOptions strikeLineOption =
                        new GenericHelper.StrikeLineOptions()
                                .setDamageCD(damageInvincibilityTicks)
                                .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false)
                                        .setWidth(0.2)
                                        .setLength(6)
                                        .setTicksLinger(2)
                                        .setParticleColor("t/bls"));
                extraVariables.put("sLO", strikeLineOption);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "七彩水晶": {
                setSize(3, false);
                noclip = true;
                setNoGravity(true);
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
            case "松鼠侍从": {
                setNoGravity(true);
                break;
            }
            case "冰结体":
            case "告死之花": {
                setSize(3, false);
                setNoGravity(true);
                break;
            }
            case "蜘蛛女王":
            case "珊瑚堆": {
                setSize(2, false);
                break;
            }
            case "脉冲炮塔": {
                setSize(3, false);
                break;
            }
            case "岩刺": {
                setSize(4, false);
                damageInvincibilityTicks = 4;
                break;
            }
            case "硫海遗爵之首": {
                setSize(5, false);
                break;
            }
            case "霜之华":
            case "天狼星":
            case "灿烂光环":
            case "恂戒探魂者": {
                setSize(2, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "小硫海遗爵": {
                setSize(2, false);
                damageInvincibilityTicks = 8;
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "阿瑞斯外骨骼": {
                // laser info
                GenericHelper.StrikeLineOptions strikeLineOption =
                        new GenericHelper.StrikeLineOptions()
                                .setDamageCD(1)
                                .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false)
                                        .setWidth(0.2)
                                        .setLength(64)
                                        .setTicksLinger(2)
                                        .setParticleColor("t/rls"));
                extraVariables.put("sLO", strikeLineOption);
                // reset cannon types to default
                PlayerHelper.initAresExoskeletonConfig(owner, true);

                setSize(12, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "归虚之灵": {
                setSize(5, false);
                noclip = true;
                setNoGravity(true);
                break;
            }
            case "源生寒晶": {
                // laser info
                GenericHelper.StrikeLineOptions strikeLineOption =
                        new GenericHelper.StrikeLineOptions()
                                .setDamageCD(1)
                                .setParticleInfo(new GenericHelper.ParticleLineOptions()
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false)
                                        .setWidth(0.25)
                                        .setLength(64)
                                        .setTicksLinger(2)
                                        .setParticleColor("t/bls"))
                                .setDamagedFunction((strikeNum, entityHit, hitLoc) -> {
                                    EntityHelper.applyEffect(entityHit, "夜魇", 100);
                                });
                extraVariables.put("sLO", strikeLineOption);

                extraVariables.put("AI", 0);
                damageInvincibilityTicks = 3;
                setSize(5, false);
                noclip = true;
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
        return 5;
    }
    // basic ticking
    @Override
    public void B_() {
        float lastYaw = yaw;
        super.B_();
        // these worm minions should maintain their own facing direction
        switch (minionType) {
            case "星尘之龙":
            case "黑色天龙":
            case "白色天龙":
            case "小星宇神卫":
            case "灾坟仆从":
                yaw = lastYaw;
        }
        // update attribute
        if (this.ticksLived <= 1 || this.ticksLived % 10 == 0) {
            MinionHelper.updateAttrMap(this.attrMap, owner, originalStaff);
        }
        // validation
        if (!MinionHelper.validate(this.getBukkitEntity(), owner, minionSlot, minionSlotMax, minionInList, sentryOrMinion)) {
            die();
            return;
        }
        // setup target, mandatory every 15 ticks AND if the current target is invalid
        // some minion type ( dragon tails etc. ) should not handle target AT ALL.
        EntityPlayer ownerNMS = ((CraftPlayer) owner).getHandle();
        switch (minionType) {
            case "黑色天龙体节":
            case "黑色天龙尾":
            case "白色天龙体节":
            case "白色天龙尾":
            case "灾坟仆从体节":
            case "灾坟仆从尾":
                break;
            default:
                if ( !(MinionHelper.checkTargetIsValidEnemy(
                        this, ownerNMS, getGoalTarget(), targetNeedLineOfSight) &&
                        MinionHelper.checkDistanceIsValid(this, ownerNMS) )
                        || ticksLived % 15 == 0)
                    MinionHelper.setTarget(this, ownerNMS, sentryOrMinion, targetNeedLineOfSight, protectOwner);
        }
        // extra ticking AI
        Vector velocity = new Vector(motX / 0.91, motY / 0.98, motZ / 0.91);
        Collection<Entity> allMinions = (Collection<Entity>) EntityHelper.getMetadata(owner,
                sentryOrMinion ? EntityHelper.MetadataName.PLAYER_SENTRY_LIST : EntityHelper.MetadataName.PLAYER_MINION_LIST).value();
        LivingEntity target, minionBukkit = (LivingEntity) getBukkitEntity();
        if (getGoalTarget() != null) target = (LivingEntity) (getGoalTarget().getBukkitEntity());
        else target = owner;
        boolean targetIsOwner = target == owner;
        boolean bypassVelocityLimit = false;

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
            case "黑鹰战斗机":
            case "瘟疫战机":
            case "宇宙炮艇": {
                double flightSpd = 0.5;
                double ammoConsumptionRate = 0.5;
                switch (minionType) {
                    case "黑鹰战斗机":
                        flightSpd = 1.25;
                        break;
                    case "瘟疫战机":
                        flightSpd = 2;
                        ammoConsumptionRate = 0.33;
                        break;
                    case "宇宙炮艇":
                        flightSpd = 3;
                        ammoConsumptionRate = 0.25;
                        break;
                }
                if (!targetIsOwner) {
                    // velocity
                    velocity = target.getEyeLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                    double distance = velocity.length();
                    if (distance < 1e-9) velocity = new Vector(0, flightSpd, 0);
                    else if (distance < 6) velocity.multiply(-flightSpd / distance);
                    else velocity.multiply(flightSpd / distance);
                    // basic fire
                    if (index % 25 == 0) {
                        String ammoItem = ItemUseHelper.consumePlayerAmmo(owner,
                                (itemStack) -> itemStack.getType() == Material.SLIME_BALL, ammoConsumptionRate);
                        if (ammoItem != null) {
                            double projSpd = 1;
                            AimHelper.AimHelperOptions aimHelper = new AimHelper.AimHelperOptions().setProjectileGravity(0);
                            switch (minionType) {
                                case "黑鹰战斗机":
                                    if (ammoItem.equals("火枪子弹")) ammoItem = "黑鹰子弹";
                                    projSpd = 2;
                                    aimHelper.setAccelerationMode(false);
                                    break;
                                case "瘟疫战机":
                                    projSpd = 3;
                                    aimHelper.setAccelerationMode(true);
                                    if (Math.random() < 0.35) ammoItem = "瘟疫导弹";
                                    break;
                                case "宇宙炮艇":
                                    projSpd = 4;
                                    aimHelper.setAccelerationMode(true);
                                    if (index % 50 == 0) {
                                        ammoItem = Math.random() < 0.35 ? "宙蝰子母火箭" : "宙蝰制导火箭";
                                    }
                                    break;
                            }
                            aimHelper.setProjectileSpeed(projSpd);
                            Location aimLoc = AimHelper.helperAimEntity(minionBukkit, target, aimHelper);
                            Vector projectileDir = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), aimLoc, projSpd);
                            EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, ammoItem);
                        }
                    }
                } else {
                    Location targetLoc = target.getLocation().add(
                            Math.random() * 2 - 1,
                            Math.random() + 6,
                            Math.random() * 2 - 1);
                    if (minionBukkit.getLocation().distanceSquared(targetLoc) > 4) {
                        velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                        velocity.normalize().multiply(flightSpd * 1.25);
                    }
                }
                break;
            }
            case "极昼飞行物": {
                Location flightLoc;
                double speed;
                // fly ahead and above the owner
                if (targetIsOwner) {
                    index = -1;

                    flightLoc = target.getLocation()
                            .add(0, 8, 0)
                            .add( MathHelper.vectorFromYawPitch_approx(ownerNMS.yaw, 0).multiply(2) );
                    speed = 2.5;
                }
                // alternate between two phases below
                else {
                    // circle around target, fire projectiles
                    if (index < 100) {
                        flightLoc = target.getEyeLocation()
                                .add(0, 12, 0)
                                .add( MathHelper.vectorFromYawPitch_approx(ownerNMS.yaw, 0).multiply(5) );
                        speed = 3;

                        // projectile
                        if (index % 10 == 0) {
                            double projSpd = 3;
                            Location aimLoc = AimHelper.helperAimEntity( minionBukkit, target,
                                    new AimHelper.AimHelperOptions("极昼激光")
                                            .setProjectileSpeed(projSpd) );
                            EntityHelper.spawnProjectile(minionBukkit,
                                    MathHelper.getDirection(minionBukkit.getEyeLocation(), aimLoc, projSpd),
                                    attrMap, "极昼激光");
                        }
                    }
                    // hover above target, fire laser
                    else {
                        flightLoc = target.getEyeLocation()
                                .add(0, 4, 0);
                        speed = 4;

                        // laser
                        if (index >= 120 && index % 3 == 0) {
                            Vector laserDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                            GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                    MathHelper.getVectorYaw(laserDir), MathHelper.getVectorPitch(laserDir), 6.0, 0.2,
                                    "", "t/bls", damageCD, (HashMap<String, Double>) attrMap.clone(),
                                    (GenericHelper.StrikeLineOptions) extraVariables.get("sLO"));
                        }
                        // repeat AI cycle
                        if (index == 200)
                            index = -1;
                    }
                }
                velocity = MathHelper.getDirection(minionBukkit.getLocation(), flightLoc, speed);
                break;
            }
            case "海贝": {
                if (!targetIsOwner && damageCD.contains(target)) {
                    MinionHelper.attemptTeleport(minionBukkit, target.getEyeLocation());
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
                else {
                    targetLoc.add(0, 4, 0);
                }
                // setup velocity
                boolean shouldUpdateVelocity = targetIsOwner || index % 8 == 0;
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
            case "磁铁无人机": {
                boolean shouldUpdateVelocity = targetIsOwner || index % 10 == 0;
                double maxSpeed = targetIsOwner ? 2 : 3;
                // setup velocity
                if (shouldUpdateVelocity) {
                    // setup target location
                    Location targetLoc;
                    if (targetIsOwner) {
                        // the next time an enemy is spotted, dash at once
                        index = -1;
                        // get location
                        double indexCurr = 0, indexMax = 0;
                        Entity firstMinion = minionInList;
                        for (Entity currMinion : allMinions) {
                            if (currMinion.isDead()) continue;
                            if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                            if (indexMax == 0) firstMinion = currMinion;
                            if (currMinion == minionInList) indexCurr = indexMax;
                            indexMax ++;
                        }
                        double angle = 360 * indexCurr / indexMax + firstMinion.getTicksLived() * 6;
                        double radius = 7.5;
                        if (minionInList != minionBukkit)
                            radius = 10;
                        targetLoc = target.getEyeLocation().add(
                                MathHelper.vectorFromYawPitch_approx(angle, 0).multiply(radius) );
                    }
                    else {
                        // predict enemy location
                        targetLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                new AimHelper.AimHelperOptions()
                                        .setProjectileSpeed(maxSpeed)
                                        .setAccelerationMode(true));
                    }
                    velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, maxSpeed, targetIsOwner);
                }
                break;
            }
            case "乌鸦仆从":
            case "灭兆渡鸦": {
                // setup velocity
                boolean shouldUpdateVelocity = targetIsOwner || index % 8 == 0;
                boolean isCrow = minionType.equals("乌鸦仆从");
                double speed;
                if (isCrow)
                    speed = targetIsOwner ? 1.75 : 2;
                else
                    speed = targetIsOwner ? 2 : 3;
                // setup target location
                Location targetLoc = target.getEyeLocation();
                if (targetIsOwner) {
                    targetLoc.add(0, 5, 0);
                }
                else {
                    // FOR RAVEN ONLY: teleport if not close enough to target ( 12 blocks, squared = 144 )
                    if (!isCrow && minionBukkit.getLocation().distanceSquared(target.getEyeLocation() ) > 144) {
                        // update velocity at the current and the next tick
                        index = -1;
                        shouldUpdateVelocity = true;
                        impulse = false;
                        // updated raven should teleport to a closer location
                        MinionHelper.attemptTeleport( minionBukkit, target.getEyeLocation().add(
                                MathHelper.randomVector().multiply(10) ) );
                    }
                    // predict target location
                    targetLoc = AimHelper.helperAimEntity(
                        minionBukkit.getEyeLocation(), target,
                        new AimHelper.AimHelperOptions()
                                .setProjectileGravity(0)
                                .setProjectileSpeed(speed)
                                .setAccelerationMode(minionType.equals("灭兆渡鸦")) );
                }
                if (shouldUpdateVelocity) {
                    velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, speed);
                }
                break;
            }
            case "宇宙灯笼": {
                // hover above the owner
                {
                    double indexCurr = 0, indexMax = 0;
                    Location targetLoc = owner.getEyeLocation();
                    Entity firstMinion = null, lastLoopedMinion = null;
                    for (Entity currMinion : allMinions) {
                        // the minion takes up 2 slots.
                        // the following two lines makes sure that minions correctly spread out when idle.
                        if (firstMinion != null && currMinion == firstMinion) continue;
                        if (lastLoopedMinion != null && currMinion == lastLoopedMinion) continue;
                        lastLoopedMinion = currMinion;
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionBukkit) indexCurr = indexMax;
                        indexMax ++;
                    }
                    // this technically is never true, but it is here just in case.
                    if (firstMinion == null)
                        firstMinion = minionBukkit;

                    targetLoc.add(0, 2 + MathHelper.xsin_degree(firstMinion.getTicksLived() * 6), 0);
                    if (indexMax > 1) {
                        double angle = 360 * indexCurr / indexMax + firstMinion.getTicksLived() * 5;
                        targetLoc.add(MathHelper.xsin_degree(angle) * 2, 0, MathHelper.xcos_degree(angle) * 2);
                    }
                    MinionHelper.attemptTeleport(minionBukkit, targetLoc);
                }
                // fire projectiles
                if (!targetIsOwner && index % 20 == 0) {
                    double projectileSpeed = 3.5;
                    Location aimLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                            new AimHelper.AimHelperOptions("宇宙灯笼光束")
                                    .setProjectileSpeed(projectileSpeed).setRandomOffsetRadius(3));
                    Vector projVel = MathHelper.getDirection(minionBukkit.getEyeLocation(), aimLoc, projectileSpeed);
                    EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, "宇宙灯笼光束");
                }
                break;
            }
            case "小乌贼": {
                boolean noMeleeAttack =
                        owner.getLocation().distanceSquared(target.getLocation()) > 400 ||
                        targetIsOwner;
                Location targetLoc = target.getEyeLocation();
                // set target location if using ranged attack / idle
                if (noMeleeAttack) {
                    targetLoc = owner.getLocation().add(
                            Math.random() * 20 - 10, Math.random() * 8 + 8, Math.random() * 20 - 10);
                    if (! targetIsOwner && index % 15 == 0 ) {
                        double projectileSpeed = 3.25;
                        Location aimLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                                new AimHelper.AimHelperOptions("追踪墨汁")
                                        .setProjectileSpeed(projectileSpeed)
                                        .setRandomOffsetRadius(2));
                        EntityHelper.spawnProjectile(minionBukkit,
                                MathHelper.getDirection(minionBukkit.getEyeLocation(), aimLoc, projectileSpeed),
                                attrMap, "追踪墨汁");
                    }
                }
                // set velocity
                velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 2.25, true);
                break;
            }
            case "炽焰天龙": {
                double speed = 4.25;
                // if no target is spotted
                if (targetIsOwner) {
                    if (index % 10 == 0)
                        velocity = MathHelper.getDirection(
                                minionBukkit.getEyeLocation(), target.getEyeLocation().add(0, 8, 0), speed);
                }
                // if enemy is present
                else {
                    boolean shouldFireProjectile = false;
                    // if close to enemy (20 blocks), dash and fire an additional projectile per dash
                    if (minionBukkit.getLocation().distanceSquared(target.getLocation()) < 400) {
                        if (index % 10 == 0) {
                            shouldFireProjectile = true;
                            addScoreboardTag("dashed");
                            Location dashAimLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                    new AimHelper.AimHelperOptions()
                                            .setProjectileGravity(0)
                                            .setProjectileSpeed(speed)
                                            .setAccelerationMode(true));
                            velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), dashAimLoc, speed);
                        }
                    }
                    // otherwise, approach enemy and fire direct projectiles at a quicker rate
                    else {
                        shouldFireProjectile = index % 6 == 0;
                        velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), speed);
                    }
                    // projectile
                    if (shouldFireProjectile) {
                        HashMap<String, Double> projAttrMap = (HashMap<String, Double>) attrMap.clone();
                        projAttrMap.put("damage", projAttrMap.get("damage") * 0.5);
                        EntityHelper.spawnProjectile(minionBukkit,
                                MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 4),
                                projAttrMap, "炽焰天龙火球");
                    }
                }
                break;
            }
            case "恂戒探魂者": {
                double speed = 4.5;
                // if no target is spotted
                if (targetIsOwner) {
                    // fly towards target as soon as it is found
                    index = -1;
                    // set velocity
                    double indexCurr = 0, indexMax = 0;
                    Entity firstMinion = minionInList;
                    for (Entity currMinion : allMinions) {
                        if (currMinion.isDead()) continue;
                        if (!GenericHelper.trimText(currMinion.getName()).equals(minionType)) continue;
                        if (indexMax == 0) firstMinion = currMinion;
                        if (currMinion == minionInList) indexCurr = indexMax;
                        indexMax ++;
                    }
                    double angle = 360 * indexCurr / indexMax + firstMinion.getTicksLived() * 6;
                    Location targetLoc = target.getEyeLocation().add(
                            MathHelper.vectorFromYawPitch_approx(angle, 0).multiply(10) );
                    velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, speed, true);
                }
                // if enemy is present, dash & fire projectile
                else {
                    int subIndex = index % 20;
                    // dash first
                    if (subIndex < 10) {
                        if (subIndex == 0) {
                            Location dashAimLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                    new AimHelper.AimHelperOptions()
                                            .setProjectileGravity(0)
                                            .setProjectileSpeed(speed)
                                            .setAccelerationMode(true));
                            velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), dashAimLoc, speed);
                        }
                    }
                    // keep a proper distance and launch projectile
                    else {
                        // keep an appropriate distance ( 16 blocks is almost exactly half a dash )
                        Vector offsetDir = MathHelper.getDirection(target.getEyeLocation(), minionBukkit.getEyeLocation(), 16);
                        velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(),
                                target.getEyeLocation().add( offsetDir ), speed, true);

                        // projectile
                        if (subIndex == 10) {
                            double projSpd = 3.5;
                            Location projAimLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                    new AimHelper.AimHelperOptions("硫火仆从飞弹")
                                            .setProjectileSpeed(projSpd)
                                            .setAccelerationMode(true));
                            EntityHelper.spawnProjectile(minionBukkit,
                                    MathHelper.getDirection(minionBukkit.getEyeLocation(), projAimLoc, projSpd),
                                    attrMap, "硫火仆从飞弹");
                        }
                    }
                }
                break;
            }
            case "小硫海遗爵": {
                Location targetLoc = null;
                // if no target is there, sit behind the player
                if (targetIsOwner) {
                    index = -1;

                    targetLoc = target.getLocation().subtract(
                            MathHelper.vectorFromYawPitch_approx(ownerNMS.yaw, 0).multiply(2) );
                }
                // if target is present
                else {
                    // quick dash for 8 times, each for 10 tick
                    if (index < 80) {
                        if (index % 10 == 0) {
                            targetLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                    new AimHelper.AimHelperOptions()
                                            .setProjectileGravity(0)
                                            .setProjectileSpeed(3)
                                            .setAccelerationMode(true));
                        }
                    }
                    // launch 6 spike balls, each for 20 tick; 80 + 120 = 200
                    else if (index < 200) {
                        // keep a nice distance
                        Vector offsetDir = MathHelper.getDirection(target.getEyeLocation(), minionBukkit.getEyeLocation(), 16);
                        targetLoc = target.getEyeLocation().add(offsetDir);
                        // projectile
                        if (index % 20 == 0) {
                            EntityHelper.spawnProjectile(minionBukkit,
                                    MathHelper.getDirection( minionBukkit.getEyeLocation(), target.getEyeLocation(), 3),
                                    attrMap, "小遗爵鲨牙刺球");
                        }
                    }
                    // finally, spin around the target for 5 second ( 100 ticks ), accumulating a projectile that sucks in enemies
                    else {
                        switch (index) {
                            // projectile at enemy location
                            case 200:
                                EntityHelper.spawnProjectile(minionBukkit, target.getEyeLocation(), new Vector(),
                                        attrMap, DamageHelper.DamageType.MAGIC, "小遗爵硫海漩涡");
                                extraVariables.put("c", target.getEyeLocation());
                                break;
                            // AI phase reset
                            case 300:
                                index = -1;
                                break;
                        }
                        // spin around the vortex
                        targetLoc = ((Location) extraVariables.get("c")).clone().add(
                                MathHelper.vectorFromYawPitch_approx(ticksLived * 12, 0).multiply(12.5) );
                    }
                }
                if (targetLoc != null)
                    velocity = MathHelper.getDirection(minionBukkit.getLocation(),
                            targetLoc, 3);
                break;
            }
            case "元素斧头": {
                // setup velocity
                boolean shouldUpdateVelocity = index % 8 == 0;
                double moveSpd = targetIsOwner ? 2 : 3.5;
                if (shouldUpdateVelocity) {
                    // setup target location
                    Location targetLoc = target.getEyeLocation();
                    if (targetIsOwner) {
                        targetLoc.add(0, 5, 0);
                    } else {
                        // predicted location based on acceleration
                        targetLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                new AimHelper.AimHelperOptions()
                                        .setAccelerationMode(true)
                                        .setProjectileGravity(0)
                                        .setProjectileSpeed(moveSpd));
                    }
                    velocity = targetLoc.subtract(minionBukkit.getLocation()).toVector();
                    MathHelper.setVectorLength(velocity, moveSpd);
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
                        // move towards enemy
                        if (ticksLived <= 2 || minionBukkit.getLocation().distanceSquared(target.getEyeLocation()) >= 225) {
                            velocity = MathHelper.getDirection(
                                    minionBukkit.getEyeLocation(), target.getEyeLocation(), 2);
                            minionBukkit.addScoreboardTag("dashed");
                        }
                        // maintain velocity
                        else {
                            index = -12;
                        }
                    }
                }
                break;
            }
            case "硫火搜寻者": {
                int dashDuration = 10;
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
                if (index % dashDuration >= 7) {
                    velocity.multiply(0.8);
                }
                break;
            }
            case "致命球":
            case "耀目圣刃": {
                boolean isDeadlySphere = minionType.equals("致命球");
                int roundDuration = isDeadlySphere ? 12 : 8;
                int round = (index / roundDuration) % 4;
                if (! isDeadlySphere)
                    round = 0;
                // dazzling stabber should hover above the player in a circle
                if (!isDeadlySphere && targetIsOwner) {
                    double indexCurr = 0, indexMax = 0;
                    Location targetLoc = target.getEyeLocation();
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
                    // set velocity
                    velocity = MathHelper.getDirection(
                            bukkitEntity.getLocation(), targetLoc, 2.25, true);
                }
                else if (round < 3 || targetIsOwner) {
                    int roundPhase = index % roundDuration;
                    // velocity init
                    if (roundPhase == 0) {
                        Location targetLoc;
                        // calculate speed
                        double speed;
                        {
                            Vector dir = target.getLocation().subtract(minionBukkit.getEyeLocation()).toVector();
                            double distance = dir.length();
                            double expectedDist;
                            if (targetIsOwner) {
                                expectedDist = Math.max(6, distance);
                            } else {
                                expectedDist = 5 + distance + Math.min(10, distance / 2);
                            }
                            speed = expectedDist / roundDuration;
                        }
                        if (targetIsOwner)
                            targetLoc = target.getEyeLocation().add(
                                    Math.random() * 10 - 5,
                                    Math.random() * 6 - 2,
                                    Math.random() * 10 - 5);
                        else {
                            targetLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                                    new AimHelper.AimHelperOptions().setProjectileGravity(0).setProjectileSpeed(speed));
                        }
                        velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, speed);
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
                double speed, projSpeed;
                int shootInterval;
                String projectileType;
                this.noclip = targetIsOwner;
                speed = 1.5;
                projSpeed = 1.75;
                shootInterval = 20;
                projectileType = "激光";
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
                            velocity.multiply(Math.max(distance / 20, speed) / distance);
                        }
                    } else {
                        targetLoc = target.getEyeLocation();
                        velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                        double distance = velocity.length();
                        if (distance > 1e-9) {
                            velocity.multiply(speed / distance);
                            if (distance < 16)
                                velocity.multiply(-1);
                        }
                    }
                }
                // shoot laser
                if (!targetIsOwner) {
                    if (index % shootInterval == 0) {
                        Vector projectileVelocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), projSpeed);
                        EntityHelper.spawnProjectile(minionBukkit, projectileVelocity, attrMap, projectileType);
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
            case "迷你灾难": {
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
            case "迷你灾祸": {
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
                            Location targetLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                                    new AimHelper.AimHelperOptions()
                                            .setProjectileGravity(0)
                                            .setProjectileSpeed(2.5));
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
                        for (Vector projVel : MathHelper.getCircularProjectileDirections(3, 1,
                                22.5, target, minionBukkit.getEyeLocation(),1.75)) {
                            EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, "樱花弹");
                        }
                    }
                }
                break;
            }
            case "血炎骨龙": {
                Location targetLoc = target.getEyeLocation().add(0, 3, 0);
                targetLoc.add( MathHelper.vectorFromYawPitch_approx(ticksLived * 9, 0).multiply(5) );
                velocity = MathHelper.getDirection(bukkitEntity.getLocation(), targetLoc, 3, true);
                // fire projectile
                if (! targetIsOwner) {
                    if (index % 30 >= 20)
                        EntityHelper.spawnProjectile(minionBukkit,
                                MathHelper.getDirection(
                                        minionBukkit.getEyeLocation(), target.getEyeLocation(), 2.5),
                                attrMap, "鲜血龙息");
                }
                else
                    index = -1;
                break;
            }
            case "血炎骨龙宝宝": {
                double speed, projSpeed;
                int shootInterval;
                String projectileType;
                this.noclip = true;
                speed = 2.5;
                projSpeed = 3;
                shootInterval = 10;
                projectileType = "血炎龙息";
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
                            velocity.multiply(Math.max(distance / 20, speed) / distance);
                        }
                    } else {
                        targetLoc = target.getEyeLocation();
                        velocity = targetLoc.subtract(minionBukkit.getEyeLocation()).toVector();
                        double distance = velocity.length();
                        if (distance > 1e-9) {
                            velocity.multiply(speed / distance);
                            if (distance < 16)
                                velocity.multiply(-1);
                        }
                    }
                }
                // shoot projectile
                if (!targetIsOwner) {
                    if (index % shootInterval == 0) {
                        Location aimLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                                new AimHelper.AimHelperOptions(projectileType).setProjectileSpeed(projSpeed));
                        Vector projectileVelocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), aimLoc, projSpeed);
                        EntityHelper.spawnProjectile(owner, minionBukkit.getEyeLocation(), projectileVelocity,
                                attrMap, DamageHelper.DamageType.MAGIC, projectileType);
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
                        String projectileType = "瘟疫细胞";
                        double projectileSpd = 1.5;
                        Location aimedLoc = AimHelper.helperAimEntity(bukkitEntity, target,
                                new AimHelper.AimHelperOptions(projectileType)
                                        .setProjectileSpeed(projectileSpd));
                        Vector projectileDir = MathHelper.getDirection(
                                minionBukkit.getEyeLocation(), aimedLoc, projectileSpd);
                        EntityHelper.spawnProjectile(minionBukkit, projectileDir, attrMap, projectileType);
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
                    Vector locOffset = MathHelper.vectorFromYawPitch_approx(indexCurr * 360d / indexMax, 0);
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
                if (!targetIsOwner && index % 15 == 0) {
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
                    Vector offset = MathHelper.vectorFromYawPitch_approx(targetNMS.yaw + 180, 0);
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
                            Location aimLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                                    new AimHelper.AimHelperOptions().setProjectileGravity(0).setProjectileSpeed(2.25));
                            velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), aimLoc, 2.25);
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
                                Location aimLoc = AimHelper.helperAimEntity(minionBukkit.getEyeLocation(), target,
                                        new AimHelper.AimHelperOptions("追踪瘟疫导弹").setProjectileSpeed(1.75));
                                Vector missileVel = MathHelper.getDirection(
                                        minionBukkit.getEyeLocation(), aimLoc, 1.75);
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
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false)
                                        .setParticleColor("t/bls")
                                        .setLength(particleLineLength));
                        MinionHelper.attemptTeleport(minionBukkit, destination);
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
            case "小幽花": {
                // hover above the target
                {
                    Location hoverDestination = target.getLocation().add(
                            Math.random() * 16 - 8, 7.5, Math.random() * 16 - 8);
                    if (bukkitEntity.getLocation().distanceSquared(hoverDestination) > 25)
                        velocity = MathHelper.getDirection(bukkitEntity.getLocation(), hoverDestination,
                                targetIsOwner ? 2 : 3, true);
                }
                // reset fire index when no target is available
                if (targetIsOwner) {
                    index = -1;
                }
                // shoot 3 projectiles then rest for a brief duration
                else {
                    switch (index % 50) {
                        case 5:
                        case 10:
                        case 15: {
                            Vector projVel = MathHelper.randomVector();
                            EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, "幽花追踪能量弹");
                        }
                    }
                }
                break;
            }
            case "星尘之龙":
            case "小星宇神卫": {
                // get all segments
                boolean isHeadSegment = true;
                ArrayList<LivingEntity> allSegments = new ArrayList<>(allMinions.size());
                for (Entity currMinion : allMinions) {
                    if (currMinion.isDead()) continue;
                    if (!currMinion.getName().startsWith(minionType)) continue;
                    if (allSegments.size() == 0 && currMinion != minionBukkit) {
                        isHeadSegment = false;
                        break;
                    }
                    allSegments.add((LivingEntity) currMinion);
                }
                // if the segment is the head, handle strike and segments following
                if (isHeadSegment) {
                    boolean isStardust = minionType.equals("星尘之龙");
                    // mech-worm should not retarget during teleport
                    if (! isStardust && index < 0) {
                        target = (LivingEntity) extraVariables.get("t");
                        targetIsOwner = false;
                    }
                    // tweak speed
                    if (targetIsOwner) {
                        // when spotting the next target, it will attempt to dash right into it
                        index = -1;
                        if (minionBukkit.getLocation().distanceSquared(owner.getEyeLocation()) > 400 || ticksLived % 30 == 1) {
                            Vector v = target.getEyeLocation().add(
                                    Math.random() * 16 - 8,
                                    4,
                                    Math.random() * 16 - 8).subtract(minionBukkit.getLocation()).toVector();
                            if (v.lengthSquared() < 1e-9) v = new Vector(0, 1, 0);
                            double distance = v.length();
                            v.multiply(Math.max(distance / 15, 1)  / distance);
                            velocity = v;
                        }
                    }
                    else {
                        // mech-worm teleports near the target if far away ( all segment 15 blocks away basically )
                        if ( (!isStardust) ) {
                            // only if index >= 0, otherwise it will be always set to -3
                            if ( index >= 0 &&
                                    allSegments.get(allSegments.size() - 1).getLocation().distanceSquared(target.getLocation()) > 225 &&
                                    minionBukkit.getLocation().distanceSquared(target.getLocation()) > 225) {
                                index = -5;
                                extraVariables.put("t", target);
                            }
                            // sit below y=0 for at least 2 ticks to eliminate client interpolation
                            if (index < 0) {
                                Location teleportLoc;
                                if ( index == -1 )
                                    teleportLoc = target.getEyeLocation().add( MathHelper.randomVector().multiply( 14 ) );
                                else {
                                    teleportLoc = target.getLocation();
                                    teleportLoc.setY(-50);
                                }
                                for (Entity e : allSegments) {
                                    MinionHelper.attemptTeleport(e, teleportLoc);
                                }
                            }
                        }


                        boolean shouldUpdateDirection = false;
                        if (isStardust)
                            shouldUpdateDirection = true;
                        else if (index <= 1)
                            shouldUpdateDirection = true;
                        if (shouldUpdateDirection) {
                            // speed:
                            // for stardust dragon, 1 to 4 (max at 12 segments)
                            // for mech-worm (DOG), 2 to 5 (max at 12 segments)
                            double speed;
                            if (isStardust)
                                speed = 1 + Math.min((double) (allSegments.size()) / 4, 3);
                            else
                                speed = 2 + Math.min((double) (allSegments.size()) / 4, 3);
                            double distanceSqr = target.getEyeLocation().distanceSquared(minionBukkit.getEyeLocation()),
                                    distMax = ((double) allSegments.size() + 1) / 2;

                            Vector v = AimHelper.helperAimEntity(minionBukkit, target,
                                            new AimHelper.AimHelperOptions()
                                                    .setProjectileGravity(0)
                                                    .setProjectileSpeed(speed)
                                                    .setAccelerationMode(!isStardust))
                                    .subtract(minionBukkit.getEyeLocation())
                                    .toVector();
                            double vLen = v.length();
                            if (vLen < 1e-9) {
                                v = new Vector(0, 1, 0);
                                vLen = 1;
                            }
                            if (velocity.lengthSquared() < 1e-5 || distanceSqr > distMax * distMax) {
                                v.multiply(speed / vLen);
                                velocity = v;
                            }
                        }
                    }
                    EntityMovementHelper.handleSegmentsFollow(allSegments,
                            new EntityMovementHelper.WormSegmentMovementOptions()
                                    .setStraighteningMultiplier(0)
                                    .setFollowingMultiplier(1)
                                    .setFollowDistance(isStardust ? 0.5 : 1)
                                    .setVelocityOrTeleport(false));
                    // facing
                    this.yaw = (float) MathHelper.getVectorYaw(velocity);
                    for (int i = 1; i < allSegments.size(); i ++) {
                        ((CraftLivingEntity) allSegments.get(i)).getHandle().yaw =
                                EntityHelper.getMetadata(allSegments.get(i), "yaw").asFloat();
                    }
                }
                // set display name according to segment info
                if (isHeadSegment) {
                    setCustomName(minionType + "头");
                } else {
                    setCustomName(minionType);
                }
                break;
            }
            case "黑色天龙":
            case "白色天龙": {
                // get all segments
                ArrayList<LivingEntity> allSegments = (ArrayList<LivingEntity>) extraVariables.get("s");
                // tweak speed
                if (targetIsOwner) {
                    // dash into the next target
                    index = -10;
                    // fly around the player
                    Location flightTargetLoc = owner.getLocation().add(0, 6, 0);
                    Vector locRotationOffset = MathHelper.vectorFromYawPitch_approx(ticksLived * 6, 0)
                            .multiply(minionType.equals("白色天龙") ? 6 : -6);
                    flightTargetLoc.add(locRotationOffset);
                    velocity = MathHelper.getDirection(bukkitEntity.getLocation(), flightTargetLoc, 3, true);
                } else {
                    double speed = 4;
                    double distanceSqr = target.getEyeLocation().distanceSquared( minionBukkit.getEyeLocation() );
                    // aim for dash after 16 blocks away
                    if (distanceSqr > 256)
                        index = -10;
                    // dash after closer than 5 blocks
                    else if (distanceSqr < 25 && velocity.lengthSquared() > 1e-9)
                        index = 0;
                    if (index < 0) {
                        Location targetLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                new AimHelper.AimHelperOptions()
                                        .setProjectileGravity(0)
                                        .setProjectileSpeed(speed)
                                        .setAccelerationMode(true));
                        velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, speed);
                    }
                }
                // the segment is the head, handle segments following
                EntityMovementHelper.handleSegmentsFollow(allSegments,
                        new EntityMovementHelper.WormSegmentMovementOptions()
                                .setStraighteningMultiplier(0)
                                .setFollowingMultiplier(1)
                                .setFollowDistance(1)
                                .setVelocityOrTeleport(false));
                // facing
                this.yaw = (float) MathHelper.getVectorYaw(velocity);
                for (int i = 1; i < allSegments.size(); i ++) {
                    ((CraftLivingEntity) allSegments.get(i)).getHandle().yaw =
                            EntityHelper.getMetadata(allSegments.get(i), "yaw").asFloat();
                }
                break;
            }
            case "灾坟仆从": {
                // get all segments
                ArrayList<LivingEntity> allSegments = (ArrayList<LivingEntity>) extraVariables.get("s");
                // tweak speed, circle around the target
                Location targetLoc = target.getEyeLocation()
                        .add(0, 8, 0).add( MathHelper.vectorFromYawPitch_approx(ticksLived * 6, 0).multiply(12) );
                velocity = MathHelper.getDirection( minionBukkit.getEyeLocation(), targetLoc, 3, true);
                // projectile
                int fireIdx = allSegments.size() - 1 - (index % 30);
                if (ticksLived > 50 && fireIdx > 0) {
                    LivingEntity fireSeg = allSegments.get(fireIdx);
                    double projSpd = 3;
                    Location aimLoc = targetIsOwner ? target.getEyeLocation() :
                            AimHelper.helperAimEntity( fireSeg, target,
                                    new AimHelper.AimHelperOptions("硫火仆从飞弹")
                                            .setProjectileSpeed(projSpd) );
                    EntityHelper.spawnProjectile(fireSeg,
                                    MathHelper.getDirection(fireSeg.getEyeLocation(), aimLoc, projSpd),
                                    attrMap, "硫火仆从飞弹")
                            .addScoreboardTag("ignoreCanDamageCheck");
                }
                // the segment is the head, handle segments following
                EntityMovementHelper.handleSegmentsFollow(allSegments,
                        new EntityMovementHelper.WormSegmentMovementOptions()
                                .setStraighteningMultiplier(0)
                                .setFollowingMultiplier(1)
                                .setFollowDistance(1.5)
                                .setVelocityOrTeleport(false));
                // facing
                this.yaw = (float) MathHelper.getVectorYaw(velocity);
                for (int i = 1; i < allSegments.size(); i ++) {
                    ((CraftLivingEntity) allSegments.get(i)).getHandle().yaw =
                            EntityHelper.getMetadata(allSegments.get(i), "yaw").asFloat();
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
                    Vector locOffset = MathHelper.vectorFromYawPitch_approx(
                            firstMinion.getTicksLived() * 4 + indexCurr * 360d / indexMax, 0);
                    locOffset.multiply(4.5);
                    targetLoc.add(locOffset);
                    // velocity
                    velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 2, true);
                }
                // shoot at the target
                if (!targetIsOwner && index % 12 == 0) {
                    // predict location
                    Location shootAimLoc = AimHelper.helperAimEntity(minionBukkit, target,
                            new AimHelper.AimHelperOptions("星幻激光")
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
                    Vector locOffset = MathHelper.vectorFromYawPitch_approx(
                            firstMinion.getTicksLived() * 6 + indexCurr * 360d / indexMax, 0);
                    locOffset.multiply(3);
                    targetLoc.add(locOffset);
                    MinionHelper.attemptTeleport(minionBukkit, targetLoc);
                }
                // strike the target if it comes close
                if (!targetIsOwner && index % 10 == 0 &&
                        target.getLocation().distanceSquared(bukkitEntity.getLocation()) < 400) {
                    Vector strikeDir = MathHelper.getDirection(
                            minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                    GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                            .setVanillaParticle(false)
                            .setSnowStormRawUse(false)
                            .setDamageCD(5)
                            .setLingerDelay(1);
                    GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                            MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                            20, 0.25, "", "t/rls",
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
                    double ownerYaw = ownerNMS.yaw;
                    double  idleIndex = MathHelper.xsin_degree(firstPrism.getTicksLived() * 12),
                            targetYaw = ownerYaw < 0 ? ownerYaw + 180 : ownerYaw - 180,
                            targetPitch = -20 - idleIndex * 5;
                    targetYaw += 180d / totalPrism * idxCurr - 90d;
                    double[] newDeltaDir = GenericHelper.getDirectionInterpolateOffset(
                            currYaw, currPitch, targetYaw, targetPitch, 0.25);
                    dYaw = newDeltaDir[0];
                    dPitch = newDeltaDir[1];
                    // every prism should move periodically according to the first prism.
                    Vector dPos = MathHelper.vectorFromYawPitch_approx(targetYaw, targetPitch);
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
                        "", "m/trp", damageCD, (HashMap<String, Double>) attrMap.clone(),
                        (GenericHelper.StrikeLineOptions) extraVariables.get("strikeLineOption"));
                extraVariables.put("yaw", currYaw);
                extraVariables.put("pitch", currPitch);
                extraVariables.put("dYaw", dYaw);
                extraVariables.put("dPitch", dPitch);
                break;
            }
            case "蛇眼": {
                // this minion does not move; instead it teleports.
                velocity.zero();
                Location teleportLoc = null;
                if (targetIsOwner) {
                    extraVariables.remove("c");
                    extraVariables.remove("p");
                    index = -1;
                    if (bukkitEntity.getLocation().distanceSquared( owner.getLocation() ) > 100)
                        teleportLoc = owner.getLocation().add( MathHelper.randomVector().multiply(
                                5 + Math.random() * 5 ) );
                }
                // fire projectile at enemy
                else {
                    // if the fired projectile is still available, check if bounce is needed
                    if (extraVariables.containsKey("p") ) {
                        // do not create a new projectile IMMEDIATELY after bouncing the former
                        index = -15;

                        Projectile proj = (Projectile) extraVariables.get("p");
                        // if the projectile is destroyed, remove cached info
                        if (proj.isDead()) {
                            extraVariables.remove("c");
                            extraVariables.remove("p");
                        }
                        else {
                            int bounceCountdown = -1;
                            if (extraVariables.containsKey("c"))
                                bounceCountdown = (int) extraVariables.get("c");
                                // first detection of collision: init countdown
                            else {
                                GenericProjectile projNMS = (GenericProjectile) ((CraftProjectile) proj).getHandle();
                                if (projNMS.penetration < 999999) {
                                    bounceCountdown = 5;
                                    extraVariables.put("c", bounceCountdown);
                                }
                            }
                            // check if bounce is needed
                            if (bounceCountdown > 0) {
                                bounceCountdown--;
                                if (bounceCountdown == 0) {
                                    extraVariables.remove("c");
                                    extraVariables.remove("p");
                                    teleportLoc = proj.getLocation();
                                    proj.remove();
                                    // fire reflected projectile
                                    Location fireLoc = teleportLoc.clone();
                                    Location predictedLoc = AimHelper.helperAimEntity(fireLoc, target,
                                            new AimHelper.AimHelperOptions("蛇眼反弹光电矢")
                                                    .setAccelerationMode(true)
                                                    .setProjectileSpeed(2.5));
                                    EntityHelper.spawnProjectile(bukkitEntity, fireLoc, MathHelper.getDirection(
                                                    fireLoc, predictedLoc, 2.5),
                                            attrMap, DamageHelper.DamageType.BULLET, "蛇眼反弹光电矢");
                                } else {
                                    extraVariables.put("c", bounceCountdown);
                                }
                            }
                        }
                    }
                    // if no projectile is available, teleport around the target and fire a new one.
                    else if (index >= 0) {
                        teleportLoc = target.getLocation().add( MathHelper.randomVector().multiply(
                                10 ) );
                        // fire original projectile
                        Location fireLoc = teleportLoc.clone();
                        Location predictedLoc = AimHelper.helperAimEntity(fireLoc, target,
                                new AimHelper.AimHelperOptions("蛇眼光电矢")
                                        .setAccelerationMode(true)
                                        .setProjectileSpeed(3));
                        Projectile spawnedProj = EntityHelper.spawnProjectile(bukkitEntity, fireLoc, MathHelper.getDirection(
                                        fireLoc, predictedLoc, 3),
                                attrMap, DamageHelper.DamageType.BULLET, "蛇眼光电矢");
                        extraVariables.put("p", spawnedProj);
                    }
                }
                // teleport if needed
                if (teleportLoc != null) {
                    MinionHelper.attemptTeleport(minionBukkit, teleportLoc);
                }
                break;
            }
            case "霜之华": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 32) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setVanillaParticle(false)
                                .setSnowStormRawUse(false)
                                .setMaxTargetHit(1)
                                .setDamagedFunction((strikeNum, entityHit, hitLoc) -> {
                                    ArrayList<Entity> exceptions = new ArrayList<>();
                                    exceptions.add(entityHit);
                                    EntityHelper.handleEntityExplode(minionBukkit, 1, exceptions, hitLoc);
                                });
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.1, 0.5, "", "t/bls",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "烬之英": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 10 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            40, false);
                    double length = strikeDir.length();
                    GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                            .setVanillaParticle(false)
                            .setSnowStormRawUse(false)
                            .setLingerDelay(3)
                            .setDamagedFunction((strikeNum, entityHit, hitLoc) -> {
                                ArrayList<Entity> exceptions = new ArrayList<>();
                                exceptions.add(entityHit);
                                EntityHelper.handleEntityExplode(minionBukkit, 1, exceptions, hitLoc);
                            });
                    GenericHelper.handleStrikeLightning(minionBukkit, minionBukkit.getEyeLocation(),
                            MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                            length, 4, 0.1, 0.5, 0, 1,"t/ols",
                            new ArrayList<>(), attrMap, strikeOption);
                }
                break;
            }
            case "微型克苏鲁之眼": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 30 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 48) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setVanillaParticle(false)
                                .setSnowStormRawUse(false)
                                .setMaxTargetHit(2);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.5, "", "t/ols",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "极寒冰块": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 10 == 0) {
                    Vector shootDir = MathHelper.vectorFromYawPitch_approx(index * 4.5, 0);
                    shootDir.multiply(1.6);
                    EntityHelper.spawnProjectile(minionBukkit, shootDir,
                            attrMap, "冰钉");
                }
                break;
            }
            case "永冻之焱华": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    double shootYaw = Math.random() * 360;
                    for (int i = 0; i < 3; i ++) {
                        Vector shootDir = MathHelper.vectorFromYawPitch_approx(shootYaw, 0);
                        shootDir.multiply(1.7);
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
                    MinionHelper.attemptTeleport(minionBukkit, location);
                }
                // shoots a laser at the target
                if (!targetIsOwner && index % 15 == 0) {
                    Vector strikeDir = MathHelper.getDirection(
                            minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                    GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                            .setVanillaParticle(false)
                            .setSnowStormRawUse(false)
                            .setDecayCoef(0.5);
                    GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                            MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                            48, 0.1, 0.5, "", "t/rls",
                            new ArrayList<>(), attrMap, strikeOption);
                }
                break;
            }
            case "太阳之灵": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 48) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setVanillaParticle(false)
                                .setSnowStormRawUse(false)
                                .setMaxTargetHit(2);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.1, 0.5, "", "t/ols",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "太阳神之灵": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 8 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 64) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setVanillaParticle(false)
                                .setSnowStormRawUse(false)
                                .setMaxTargetHit(2);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                length, 0.1, 0.5, "", "t/ols",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "天狼星": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 7.5 + 2.5 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 15 == 0) {
                    Vector strikeDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(),
                            512, true);
                    double length = strikeDir.length();
                    if (length < 80) {
                        GenericHelper.StrikeLineOptions strikeOption = new GenericHelper.StrikeLineOptions()
                                .setVanillaParticle(false)
                                .setSnowStormRawUse(false)
                                .setDamagedFunction( (Integer hitIdx, Entity hitEntity, Location hitLoc) ->
                                        EntityHelper.applyEffect(hitEntity, "夜魇", 60) )
                                .setMaxTargetHit(1);
                        GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                80, 0.25, 0.25, "", "t/dbls",
                                new ArrayList<>(), attrMap, strikeOption);
                    }
                }
                break;
            }
            case "凋零枯花": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 2 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    double shootYaw = Math.random() * 360;
                    for (int i = 0; i < 4; i ++) {
                        Vector shootDir = MathHelper.vectorFromYawPitch_approx(shootYaw, 0);
                        shootDir.multiply(1.85);
                        EntityHelper.spawnProjectile(minionBukkit, shootDir,
                                attrMap, "瘟疫矢");
                        shootYaw += 90;
                    }
                }
                break;
            }
            case "告死之花": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 6 + 3 * MathHelper.xsin_degree(index * 5), 0));
                // attack
                if (!targetIsOwner && index % 15 == 0) {
                    double shootYaw = Math.random() * 360;
                    for (int i = 0; i < 5; i ++) {
                        Vector shootDir = MathHelper.vectorFromYawPitch_approx(shootYaw, 0);
                        shootDir.multiply(2.5);
                        EntityHelper.spawnProjectile(minionBukkit, shootDir,
                                attrMap, "告死之花弹幕");
                        shootYaw += 72;
                    }
                }
                break;
            }
            case "灿烂光环": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit, owner.getLocation().add(0, 7.5, 0) );
                // attack
                if (!targetIsOwner && index % 20 == 0) {
                    double shootYaw = Math.random() * 360;
                    for (int i = 0; i < 3; i ++) {
                        Vector shootDir = MathHelper.vectorFromYawPitch_approx(shootYaw, 0);
                        shootDir.multiply(3.25);
                        EntityHelper.spawnProjectile(minionBukkit, shootDir,
                                attrMap, "星律辐射溶解球");
                        shootYaw += 120;
                    }
                }
                break;
            }
            case "永劫信标": {
                // move above owner
                Location targetLoc = owner.getLocation().add(0, 5, 0);
                velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 2.5, true);
                // attack
                if (!targetIsOwner && index % 2 == 0) {
                    Vector projVel = MathHelper.vectorFromYawPitch_approx(Math.random() * 360, -70 - Math.random() * 20);
                    Location fireLoc = target.getEyeLocation().subtract( projVel.clone().multiply(32) );
                    String projType;
                    switch ((int) (Math.random() * 3)) {
                        case 0:
                            projType = "异端火焰";
                            break;
                        case 1:
                            projType = "异端镀金灵魂";
                            break;
                        default:
                            projType = "异端迷失灵魂";
                    }
                    EntityHelper.spawnProjectile(minionBukkit, fireLoc, projVel.multiply(3.5),
                            attrMap, DamageHelper.DamageType.MAGIC, projType);
                }
                break;
            }
            case "阿瑞斯外骨骼": {
                // teleport to the owner's back; the two locations below are on the lower center.
                Location surfaceLoc, centerLoc;
                Vector exoSkeletonOffsetDir = MathHelper.vectorFromYawPitch_approx(ownerNMS.yaw + 180, 0);
                surfaceLoc = owner.getLocation().add(exoSkeletonOffsetDir.clone().multiply(4.5));
                // the minion stay well above the player to benefit the supreme calamitas fight
                surfaceLoc.add(0, 10, 0);
                centerLoc = surfaceLoc.clone().add( exoSkeletonOffsetDir.clone().multiply(3) ).add(0, -2, 0);
                MinionHelper.attemptTeleport(minionBukkit, centerLoc);
                // face whatever direction the owner is facing
                yaw = ownerNMS.yaw;
                // attack
                if (!targetIsOwner) {
                    Vector fireLocUnitOffset = MathHelper.vectorFromYawPitch_approx(ownerNMS.yaw + 90, 0);
                    ArrayList<Short> weaponConfig = PlayerHelper.getAresExoskeletonConfig(owner);
                    // loop through all 4 weapons
                    // 0    1
                    // 2    3
                    for (int i = 0; i < 4; i ++) {
                        Location shootLoc = surfaceLoc.clone()
                                .add( fireLocUnitOffset.clone().multiply(i % 2 == 1 ? 2 : -2 ) )
                                .add(0, i < 2 ? 2 : -0.75, 0);
                        short weaponType = weaponConfig.get(i);
                        int correctedIdx;
                        switch (weaponType) {
                            // plasma cannon
                            case 0: {
                                correctedIdx = index + (int) (i * 10 / 4d);
                                double projSpd = 2.5;
                                if (correctedIdx % 10 == 0) {
                                    EntityHelper.spawnProjectile(minionBukkit, shootLoc,
                                            MathHelper.getDirection(shootLoc, target.getEyeLocation(), projSpd),
                                            attrMap, DamageHelper.DamageType.ROCKET, "阿瑞斯外骨骼等离子光球");
                                }
                                break;
                            }
                            // tesla cannon
                            case 1: {
                                correctedIdx = index + (int) (i * 15 / 4d);
                                if (correctedIdx % 15 == 0) {
                                    double projSpd = 2;
                                    Location aimLoc = AimHelper.helperAimEntity(shootLoc, target,
                                            new AimHelper.AimHelperOptions("阿瑞斯外骨骼球状闪电")
                                                    .setProjectileSpeed(projSpd)
                                                    .setAccelerationMode(true));
                                    EntityHelper.spawnProjectile(minionBukkit, shootLoc,
                                            MathHelper.getDirection(shootLoc, aimLoc, projSpd),
                                            attrMap, DamageHelper.DamageType.ROCKET, "阿瑞斯外骨骼球状闪电");
                                }
                                break;
                            }
                            // laser cannon
                            case 2: {
                                correctedIdx = index + i * 15;
                                // 25tick projectile, 25tick laser then 10tick idle, total 60 ticks
                                int internalIdx = correctedIdx % 60;

                                if (internalIdx < 25) {
                                    if (internalIdx % 5 == 0) {
                                        double projSpd = 3;
                                        EntityHelper.spawnProjectile(minionBukkit, shootLoc,
                                                MathHelper.getDirection(shootLoc, target.getEyeLocation(), projSpd),
                                                attrMap, DamageHelper.DamageType.ROCKET, "阿瑞斯外骨骼热能激光");
                                    }
                                }
                                else if (internalIdx < 50) {
                                    if (internalIdx % 3 == 0) {
                                        Vector laserDir = MathHelper.getDirection(shootLoc, target.getEyeLocation(), 1);
                                        GenericHelper.handleStrikeLine(minionBukkit, shootLoc,
                                                MathHelper.getVectorYaw(laserDir), MathHelper.getVectorPitch(laserDir), 64.0, 0.2,
                                                "", "t/rls", damageCD, (HashMap<String, Double>) attrMap.clone(),
                                                (GenericHelper.StrikeLineOptions) extraVariables.get("sLO"));
                                    }
                                }
                                break;
                            }
                            // gauss nuke launcher
                            case 3: {
                                correctedIdx = index + i * 20;
                                double projSpd = 2.5;
                                if (correctedIdx % 80 == 0) {
                                    EntityHelper.spawnProjectile(minionBukkit, shootLoc,
                                            MathHelper.getDirection(shootLoc, target.getEyeLocation(), projSpd),
                                            attrMap, DamageHelper.DamageType.ROCKET, "阿瑞斯外骨骼高斯核弹");
                                }
                                break;
                            }
                        }
                    }
                }
                break;
            }
            case "光阴流时伞": {
                // teleport above owner
                MinionHelper.attemptTeleport(minionBukkit,
                        owner.getLocation().add(0, 8, 0));
                break;
            }
            case "归虚之灵": {
                // get the location to stay
                Location targetLoc;
                if (targetIsOwner)
                    targetLoc = target.getEyeLocation().add(0, 8, 0);
                else {
                    Location tempLoc = target.getLocation(), ownerLoc = owner.getLocation();
                    tempLoc.setY(ownerLoc.getY());
                    Vector dir = MathHelper.getDirection(tempLoc, ownerLoc, 5);
                    dir.setY(12);
                    targetLoc = target.getEyeLocation().add(dir);
                }
                // update velocity
                velocity = MathHelper.getDirection(minionBukkit.getLocation(), targetLoc, 3, true);
                // attack when needed
                if (! targetIsOwner && index % 15 == 0) {
                    String projType = "归墟跟踪能源箭";
                    double projSpd = 2.5;
                    for (int i = 0; i < 6; i ++) {
                        if (i == 5) {
                            projType = "归墟爆破之星";
                            projSpd = 3;
                        }
                        EntityHelper.spawnProjectile(minionBukkit,
                                MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), projSpd),
                                attrMap, projType);
                    }
                }
                break;
            }
            case "源生寒晶": {
                // get and update AI
                int AIMode = (int) (extraVariables.get("AI") );
                if (getScoreboardTags().contains("reSummoned")) {
                    removeScoreboardTag("reSummoned");
                    AIMode = (AIMode + 1) % 4;
                    extraVariables.put("AI", AIMode);
                }
                // get the location to stay
                Location targetLoc;
                if (targetIsOwner)
                    targetLoc = target.getEyeLocation().add(0, 6, 0);
                else {
                    Vector dir = MathHelper.getDirection(target.getEyeLocation(), owner.getEyeLocation(), 16);
                    targetLoc = target.getEyeLocation().add(dir);
                }
                // attack when needed
                if (! targetIsOwner) {
                    // dash deals contact damage
                    hasContactDamage = AIMode == 2;
                    switch (AIMode) {
                        // laser
                        case 0: {
                            if (index % 3 == 0) {
                                Vector laserDir = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                                GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                        MathHelper.getVectorYaw(laserDir), MathHelper.getVectorPitch(laserDir), 64.0, 0.25,
                                        "", "t/bls", damageCD, (HashMap<String, Double>) attrMap.clone(),
                                        (GenericHelper.StrikeLineOptions) extraVariables.get("sLO"));
                            }
                            break;
                        }
                        // burst of homing shards
                        case 1: {
                            double projSpd = 2.5;
                            if (index % 30 == 0) {
                                for (int i = 0; i < 12; i ++) {
                                    EntityHelper.spawnProjectile(minionBukkit,
                                            MathHelper.randomVector().multiply(projSpd),
                                            attrMap, "源生冰片");
                                }
                            }
                            break;
                        }
                        // ram enemy
                        case 2: {
                            targetLoc = AimHelper.helperAimEntity(minionBukkit, target,
                                    new AimHelper.AimHelperOptions()
                                            .setProjectileGravity(0)
                                            .setAimMode(true)
                                            .setTicksTotal(1));
                            break;
                        }
                        // fire blasts of ice shards
                        case 3: {
                            double projSpd = 2.25;
                            if (index % 15 == 0) {
                                Location fireLoc = minionBukkit.getEyeLocation();
                                for (Vector dir : MathHelper.getCircularProjectileDirections(
                                        3, 2, 5, target, fireLoc, projSpd)) {
                                    EntityHelper.spawnProjectile(minionBukkit,
                                            dir, attrMap, "源生冰锥");
                                }
                            }
                            break;
                        }
                    }
                }
                // update velocity
                velocity = MathHelper.getDirection(minionBukkit.getEyeLocation(), targetLoc, 3, true);
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
                                    .setVanillaParticle(false)
                                    .setSnowStormRawUse(false);
                            GenericHelper.handleStrikeLine(minionBukkit, minionBukkit.getEyeLocation(),
                                    MathHelper.getVectorYaw(strikeDir), MathHelper.getVectorPitch(strikeDir),
                                    length, 0.1, 0.5, "", "t/rls",
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
                        Location predictedLoc = AimHelper.helperAimEntity(bukkitEntity, target,
                                new AimHelper.AimHelperOptions()
                                        .setAimMode(true)
                                        .setTicksTotal(10));
                        GenericHelper.ParticleLineOptions particleInfo = new GenericHelper.ParticleLineOptions()
                                .setVanillaParticle(false)
                                .setSnowStormRawUse(false)
                                .setParticleColor("t/rbws")
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
                                64, 1.5, "", "t/bls",
                                damageExceptions, attrMap,
                                new GenericHelper.StrikeLineOptions()
                                        .setThruWall(false)
                                        .setVanillaParticle(false)
                                        .setSnowStormRawUse(false)
                                        .setDamageCD(5)
                                        .setLingerDelay(2));
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
            case "硫海遗爵之首": {
                // fire every 3 ticks, IF the distance is less than 32 blocks (32 * 32 = 1024)
                if ( !targetIsOwner && index % 3 == 0 &&
                        target.getLocation().distanceSquared( minionBukkit.getLocation() ) < 1024 ) {
                    Vector projVel = MathHelper.getDirection(minionBukkit.getEyeLocation(), target.getEyeLocation(), 1);
                    double projVelPitch = MathHelper.getVectorPitch(projVel), minPitch = -45;
                    if (projVelPitch > minPitch)
                        projVel = MathHelper.vectorFromYawPitch_approx(MathHelper.getVectorYaw(projVel), minPitch);
                    projVel.multiply(1.25);

                    EntityHelper.spawnProjectile(minionBukkit, projVel, attrMap, "干缩硫海龙鲨");
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
                    if (!hitInfo.isEmpty()) {
                        String scoreboardTagHealCD = "temp_healCD";
                        if (! getScoreboardTags().contains(scoreboardTagHealCD)) {
                            PlayerHelper.heal(owner, 1);
                            EntityHelper.handleEntityTemporaryScoreboardTag(minionBukkit, scoreboardTagHealCD, 8);
                        }
                    }
                    break;
                // receives knockback on hit
                case "远古岩鲨":
                case "炽焰天龙":
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
