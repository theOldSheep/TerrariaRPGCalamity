package terraria.entity.monster;


import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.entity.projectile.TerrariaPotionProjectile;
import terraria.gameplay.Event;
import terraria.util.BossHelper;
import terraria.util.EntityHelper;
import terraria.util.MathHelper;
import terraria.util.PlayerHelper;
import terraria.util.WorldHelper;

import java.util.*;

public class MonsterHelper {
    // initializes monster size, attribute, disguise etc.
    private static class MonsterStatsMulti {
        public double
                healthMulti = 1d,
                defenceMulti = 1d,
                damageMulti = 1d;
    }
    public static String[] getMonsterProgressRequirement(String monsterType) {
        String progMin = TerrariaHelper.mobSpawningConfig.getString("mobTier." + monsterType, "");
        String progMax = TerrariaHelper.mobSpawningConfig.getString("mobTierMax." + monsterType, "");
        return new String[] {progMin, progMax};
    }
    public static boolean validateMonsterProgress(Player target, Entity monster, String type) {
        return validateMonsterProgress(getMonsterProgressRequirement(type), target, monster);
    }
    public static boolean validateMonsterProgress(String[] progressRequirement, Player target, Entity monster) {
        boolean minRequirementMet = progressRequirement[0].length() == 0 || PlayerHelper.hasDefeated(target, progressRequirement[0]);
        boolean maxRequirementMet = progressRequirement[1].length() > 0 && PlayerHelper.hasDefeated(target, progressRequirement[1]);
        if (!minRequirementMet || maxRequirementMet) {
            if (monster != null)
                monster.die();
            return false;
        }
        return true;
    }
    public static String getMonsterVariant(Player target, String monsterType) {
        String variant = "";
        ConfigurationSection variantsSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection(
                "mobInfo." + monsterType + ".variants");
        // setup prefix list
        Collection<String> availableVariants = variantsSection.getKeys(false);
        Collection<String> prefixToCheck = new ArrayList<>(5);
        WorldHelper.HeightLayer playerHeight = WorldHelper.HeightLayer.getHeightLayer(target.getLocation());
        WorldHelper.BiomeType   playerBiome = WorldHelper.BiomeType.getBiome(target.getLocation(), false);
        // -> event mobs
        if (playerHeight == WorldHelper.HeightLayer.SURFACE &&
                Event.currentEvent != null && Event.currentEvent.length() > 0)
            prefixToCheck.add(Event.currentEvent);
        // -> biome specific and height specific
        {
            String biomeStr = playerBiome.toString().toLowerCase();
            switch (playerHeight) {
                case UNDERGROUND:
                    if (playerBiome == WorldHelper.BiomeType.NORMAL)
                        prefixToCheck.add("underground");
                    else
                        prefixToCheck.add(biomeStr + "_underground");
                    break;
                case CAVERN:
                    if (playerBiome == WorldHelper.BiomeType.NORMAL)
                        prefixToCheck.add("cavern");
                    else
                        prefixToCheck.add(biomeStr + "_cavern");
                    break;
            }
            prefixToCheck.add(biomeStr);
        }
        // -> default, prevent normal slime being spawned instead of lava slime
        if (playerHeight != WorldHelper.HeightLayer.UNDERWORLD)
            prefixToCheck.add("default");
        // setup available variant list
        List<String> variantCandidates = new ArrayList<>(10);
        for (String situationPrefix : prefixToCheck) {
            int index = 1;
            String variantName = situationPrefix + index;
            while (availableVariants.contains(variantName)) {
                variantCandidates.add(variantName);
                variantName = situationPrefix + (++index);
            }
        }
        // select a random variant
        variant = variantCandidates.get((int) (Math.random() * variantCandidates.size()));
        return variant;
    }
    private static MonsterStatsMulti getMonsterStatsBonus(Player target, String monsterProgressRequired) {
        MonsterStatsMulti result = new MonsterStatsMulti();
        PlayerHelper.GameProgress playerProgress = PlayerHelper.getGameProgress(target);
        PlayerHelper.GameProgress monsterProgress = PlayerHelper.getGameProgress(monsterProgressRequired);
        switch (monsterProgress) {
            case PRE_WALL_OF_FLESH:
                switch (playerProgress) {
                    case PRE_PLANTERA:
                        result.healthMulti  = 4d;
                        result.defenceMulti = 3d;
                        result.damageMulti  = 2d;
                        break;
                    case PRE_MOON_LORD:
                        result.healthMulti  = 5d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 2.5d;
                        break;
                    case PRE_PROFANED_GODDESS:
                        result.healthMulti  = 7d;
                        result.defenceMulti = 8d;
                        result.damageMulti  = 3.5d;
                        break;
                    case POST_PROFANED_GODDESS:
                        result.healthMulti  = 10d;
                        result.defenceMulti = 12.5d;
                        result.damageMulti  = 5d;
                        break;
                }
                break;
            case PRE_PLANTERA:
                switch (playerProgress) {
                    case PRE_MOON_LORD:
                        result.healthMulti  = 4d;
                        result.defenceMulti = 3d;
                        result.damageMulti  = 2d;
                        break;
                    case PRE_PROFANED_GODDESS:
                        result.healthMulti  = 5d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 2.5d;
                        break;
                    case POST_PROFANED_GODDESS:
                        result.healthMulti  = 7d;
                        result.defenceMulti = 8d;
                        result.damageMulti  = 3.5d;
                        break;
                }
                break;
            case PRE_MOON_LORD:
                switch (playerProgress) {
                    case PRE_PROFANED_GODDESS:
                        result.healthMulti  = 4d;
                        result.defenceMulti = 3d;
                        result.damageMulti  = 2d;
                        break;
                    case POST_PROFANED_GODDESS:
                        result.healthMulti  = 5d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 2.5d;
                        break;
                }
                break;
            case PRE_PROFANED_GODDESS:
                if (playerProgress == PlayerHelper.GameProgress.POST_PROFANED_GODDESS) {
                    result.healthMulti  = 4d;
                    result.defenceMulti = 3d;
                    result.damageMulti  = 2d;
                }
                break;
        }
        return result;
    }
    public static void initMonsterInfo(Player target, String monsterProgressRequiredMin, EntityLiving monster, String type, String variant) {
        // determine if the player's game progress is appropriate
        ConfigurationSection typeConfigSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection("mobInfo." + type);
        ConfigurationSection variantConfigSection = typeConfigSection.getConfigurationSection("variants." + variant);
        ConfigurationSection attributeConfigSection = variantConfigSection.getConfigurationSection("attributes");
        org.bukkit.entity.Entity bukkitMonster = monster.getBukkitEntity();
        // if the monster is an event mob, setup progress info
        if (Event.currentEvent.length() > 0 && variant.startsWith(Event.currentEvent)) {
            double killProgress = typeConfigSection.getDouble("eventProgress", 1d);
            org.bukkit.entity.Entity monsterBkt = monster.getBukkitEntity();
            EntityHelper.setMetadata(monsterBkt, "spawnEvent", Event.currentEvent);
            EntityHelper.setMetadata(monsterBkt, "killProgress", killProgress);
        }
        // name, size etc.
        if (variantConfigSection.contains("name"))
            monster.setCustomName(variantConfigSection.getString("name"));
        else if (typeConfigSection.contains("name"))
            monster.setCustomName(typeConfigSection.getString("name"));
        else
            monster.setCustomName(type);
        monster.setCustomNameVisible(true);
        if (monster instanceof EntitySlime)
            ((EntitySlime) monster).setSize(typeConfigSection.getInt("slimeSize", 2), false);
        // attribute
        HashMap<String, Double> attrMap = new HashMap<>(15);
        Collection<String> attributes = attributeConfigSection.getKeys(false);
        double health = 100;
        for (String attribute : attributes) {
            switch (attribute) {
                case "health":
                    health = attributeConfigSection.getDouble(attribute);
                    break;
                case "damageType":
                    EntityHelper.setDamageType(bukkitMonster, attributeConfigSection.getString(attribute));
                    break;
                default:
                    attrMap.put(attribute, attributeConfigSection.getDouble(attribute));
            }
        }
        // stats bonus
        MonsterStatsMulti statsBoost = getMonsterStatsBonus(target, monsterProgressRequiredMin);
        attrMap.put("damageMulti", statsBoost.damageMulti);
        attrMap.put("defenceMulti", statsBoost.defenceMulti);
        health *= statsBoost.healthMulti;
        // health bonus for the entity
        if (typeConfigSection.getBoolean("hasHealthMulti", false)) {
            String owningBoss = typeConfigSection.getString("owningBoss");
            ArrayList<org.bukkit.entity.Entity> bossList = null;
            if (owningBoss != null) {
                bossList = BossHelper.getBossList(owningBoss);
            }
            if (bossList != null) {
                HashMap<String, Double> targets = (HashMap<String, Double>) EntityHelper.getMetadata(bossList.get(0), "targets").value();
                health *= targets.size();
            } else {
                int totalPlyNearby = 0;
                for (org.bukkit.entity.Entity e : bukkitMonster.getNearbyEntities(96, 96, 96)) {
                    if (e instanceof Player) totalPlyNearby ++;
                }
                health *= Math.max(totalPlyNearby, 1);
            }
        }
        // set the monster's stats
        bukkitMonster.addScoreboardTag("isMonster");
        EntityHelper.setMetadata(bukkitMonster, "attrMap", attrMap);
        LivingEntity bukkitMonsterLivingEntity = (LivingEntity) bukkitMonster;
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(444);
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25);
        bukkitMonsterLivingEntity.setHealth(health);
        // add 1 to target's amount of active monster
        tweakPlayerMonsterSpawnedAmount(target, true);
        // set the monster's special info
        // no gravity
        switch (type) {
            case "恶魔之眼":
            case "噬魂怪":
            case "恶魔":
            case "巫毒恶魔":
            case "红恶魔":
            case "饿鬼":
            case "巨型诅咒骷髅头":
            case "胡闹鬼":
            case "克苏鲁的仆从":
            case "飞翔史莱姆":
            case "鸟妖":
            case "飞龙":
            case "骨蛇":
            case "飞蛇":
            case "诅咒骷髅头":
            case "地牢幽魂":
            case "致命球":
            case "探测怪":
            case "精灵直升机":
            case "雪花怪":
            case "钨钢悬浮坦克":
            case "钨钢无人机":
            case "蛾怪":
            case "陨石怪":
            case "地狱蝙蝠":
            case "丛林蝙蝠":
            case "幽灵":
            case "死神":
                monster.setNoGravity(true);
        }
        // no clip
        switch (type) {
            case "巨型诅咒骷髅头" :
            case "诅咒骷髅头":
            case "地牢幽魂":
            case "致命球":
            case "胡闹鬼":
            case "探测怪":
            case "精灵直升机":
            case "蛾怪":
            case "陨石怪":
            case "克苏鲁的仆从":
            case "幽灵":
            case "死神":
                monster.noclip = true;
        }
        // glowing
        switch (type) {
            case "巨型诅咒骷髅头":
            case "诅咒骷髅头":
                monster.glowing = true;
        }
        // no AI for some slime, to disable unwanted jumping.
        if (monster instanceof EntitySlime) {
            switch (type) {
                case "史莱姆":
                case "丛林史莱姆":
                case "尖刺史莱姆":
                case "水晶史莱姆":
                case "弹力史莱姆":
                case "礼物宝箱怪":
                case "神圣宝箱怪":
                case "腐化宝箱怪":
                    break;
                default:
                    EntitySlime slimeMonster = (EntitySlime) monster;
                    World world = monster.world;
                    MethodProfiler methodProfiler = world != null && world.methodProfiler != null ? world.methodProfiler : null;
                    slimeMonster.goalSelector = new PathfinderGoalSelector(methodProfiler);
                    slimeMonster.targetSelector = new PathfinderGoalSelector(methodProfiler);
            }
        }
        // attributes and other properties
        switch (type) {
            case "钨钢回转器":
            case "钨钢漫步者": {
                bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.45);
                break;
            }
        }
        ((LivingEntity) monster.getBukkitEntity()).getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
    }
    public static void tweakPlayerMonsterSpawnedAmount(Player target, boolean addOrRemove) {
        if (target == null) return;
        int mobAmount = EntityHelper.getMetadata(target, "mobAmount").asInt();
        mobAmount += addOrRemove ? 1 : -1;
        EntityHelper.setMetadata(target, "mobAmount", mobAmount);
    }
    // setup monster target
    public static Player updateMonsterTarget(Player target, EntityLiving monster, String type) {
        EntityPlayer targetNMS = ((CraftPlayer) target).getHandle();
        org.bukkit.entity.Entity monsterBkt = monster.getBukkitEntity();
        // boss summoned monsters should attack the boss's target
        String owningBoss = TerrariaHelper.mobSpawningConfig.getString("mobInfo." + type + ".owningBoss");
        if (owningBoss != null) {
            ArrayList<org.bukkit.entity.Entity> bossLst = BossHelper.getBossList(type);
            if (bossLst != null) {
                org.bukkit.entity.Entity boss = bossLst.get(0);
                return (Player) EntityHelper.getMetadata(boss, "target").value();
            }
        }
        // the monster's ticks lived is set to represent the ticks of losing any target
        if (
                // target is not online / not logged in etc.
                !PlayerHelper.isProperlyPlaying(target) ||
                // target is in a different world
                targetNMS.getWorld() != monster.getWorld() ||
                // distance > 64
                target.getLocation().distanceSquared(monsterBkt.getLocation()) > 4096) {
            monster.ticksLived = Math.max(monster.ticksLived, 150);
        } else if (monster.hasLineOfSight(targetNMS)) {
            monster.ticksLived = 1;
        }
        // after ten seconds without any proper target, attempt to retarget
        if (monster.ticksLived >= 200) {
            // find possible target
            Player newTarget = null;
            double targetAttemptRadius = 64;
            double newTargetDistSqr = 4096;
            for (org.bukkit.entity.Entity checkEntity : monsterBkt.getNearbyEntities(targetAttemptRadius, targetAttemptRadius, targetAttemptRadius)) {
                if (checkEntity instanceof Player) {
                    Player checkPlayer = (Player) checkEntity;
                    if (PlayerHelper.isProperlyPlaying(checkPlayer)) {
                        double currDistSqr = monsterBkt.getLocation().distanceSquared(checkPlayer.getLocation());
                        if (currDistSqr > newTargetDistSqr) continue;
                        if (monster.hasLineOfSight( ((CraftPlayer) checkPlayer).getHandle() )) {
                            newTarget = checkPlayer;
                            newTargetDistSqr = currDistSqr;
                        }
                    }
                }
            }
            if (newTarget != null) {
                tweakPlayerMonsterSpawnedAmount(target, false);
                tweakPlayerMonsterSpawnedAmount(newTarget, true);
                return newTarget;
            } else {
                // target's monster spawned amount is tweaked in die()
                monster.die();
                return null;
            }
        }
        return target;
    }
    // TODO
    public static int monsterAI(EntityLiving monster, double defaultMovementSpeed, Player target, String type,
                                int indexAI, HashMap<String, Object> extraVariables) {
        if (monster.getHealth() <= 0f) return indexAI;
        LivingEntity monsterBkt = (LivingEntity) monster.getBukkitEntity();
        // knockback can then be used to modify monster's movement speed temporarily
        double speedMultiKnockback = 1;
        {
            MetadataValue kbFactorMetadata = EntityHelper.getMetadata(monsterBkt, "kbFactor");
            if (kbFactorMetadata != null) {
                double kbFactor = kbFactorMetadata.asDouble();
                if (kbFactor > 1e-5) {
                    speedMultiKnockback = 1 - kbFactor;
                    // knockback speed reduction decreases every tick
                    EntityHelper.setMetadata(monsterBkt, "kbFactor", kbFactor - 0.1);
                } else {
                    EntityHelper.setMetadata(monsterBkt, "kbFactor", null);
                }
            }
        }
        monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(defaultMovementSpeed * speedMultiKnockback);
        // the monster's additional AI
        boolean hasContactDamage = true;
        switch (type) {
            case "僵尸":
            case "钨钢回转器":
            case "稻草人":
                {
                if (indexAI == 0)
                    indexAI = (int) (Math.random() * 100);
                else if (indexAI > 160 && monster.onGround) {
                    indexAI = (int) (Math.random() * 100);
                    monster.motY = speedMultiKnockback;
                }
                break;
            }
            case "沼泽怪":
                {
                if (indexAI == 0)
                    indexAI = (int) (Math.random() * 100);
                else if (indexAI > 160) {
                    indexAI = (int) (Math.random() * 100);
                    // 0.25 ~ 0.55
                    double moveSpeed = 0.25 + Math.random() * 0.3;
                    monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(moveSpeed);
                    ((MonsterHusk) monster).defaultSpeed = moveSpeed;
                }
                break;
            }
            case "眼怪":
                {
                if (indexAI == 0)
                    indexAI = (int) (Math.random() * 100);
                else if (indexAI > 160) {
                    indexAI = (int) (Math.random() * 100);
                    Vector v = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                    double vLen = v.length();
                    if (vLen > 0) {
                        v.multiply(1 / vLen);
                        EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                monsterBkt, v, EntityHelper.getAttrMap(monsterBkt), "激光");
                        shootInfo.properties.put("penetration", 10);
                        EntityHelper.spawnProjectile(shootInfo);
                    }
                }
                break;
            }
            case "鸟妖":
                {
                    // fly towards target
                    {
                        int indexFlight = indexAI % 60;
                        if (indexFlight == 0) {
                            extraVariables.put("targetLoc", target.getEyeLocation());
                        }
                        Location targetLoc = (Location) extraVariables.get("targetLoc");
                        if (targetLoc != null) {
                            // do not even bother cloning targetLoc; simply multiply by -1 later on to flip the direction
                            Vector acceleration = monsterBkt.getEyeLocation().subtract(targetLoc).toVector();
                            double dist = acceleration.length();
                            double maxSpd = 2 * speedMultiKnockback;
                            if (dist > maxSpd) {
                                acceleration.multiply(-0.05 / dist);
                                Vector velocity = monsterBkt.getVelocity().multiply(0.975);
                                velocity.add(acceleration.clone().multiply(speedMultiKnockback));
                                if (velocity.lengthSquared() > maxSpd * maxSpd) {
                                    velocity.multiply(maxSpd / velocity.length());
                                }
                                monsterBkt.setVelocity(velocity);
                            }
                            // if the harpy is very close to target location, remove its target loc
                            else
                                extraVariables.remove("targetLoc");
                        }
                    }
                    // shoot feathers
                    {
                        switch (indexAI % 80) {
                            case 20:
                            case 30:
                            case 40:
                                Location targetLoc = target.getEyeLocation();
                                Vector velocity = MathHelper.getDirection(monsterBkt.getEyeLocation(), targetLoc, 1);
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, velocity, EntityHelper.getAttrMap(monsterBkt), "鸟妖羽毛");
                                shootInfo.properties.put("penetration", 10);
                                EntityHelper.spawnProjectile(shootInfo);
                        }
                    }
                    break;
                }
            case "探测怪":
            case "飞蛇":
            case "钨钢悬浮坦克":
            case "钨钢无人机":
                {
                    boolean isFleeing = false;
                    Vector acceleration = null;
                    if ("探测怪".equals(type)) {
                        if (WorldHelper.isDayTime(monsterBkt.getWorld())) {
                            acceleration = new Vector(0, 0.25, 0);
                            isFleeing = true;
                        }
                    }
                    if (!isFleeing) {
                        // movement
                        Location targetLoc = target.getEyeLocation();
                        switch (type) {
                            case "探测怪":
                                targetLoc.add(MathHelper.xsin_degree(indexAI) * 16, 5, MathHelper.xcos_degree(indexAI) * 16);
                                break;
                            case "钨钢无人机":
                                targetLoc.add(MathHelper.xsin_degree(indexAI) * 6, 5, MathHelper.xcos_degree(indexAI) * 6);
                                break;
                        }
                        acceleration = targetLoc.subtract(monsterBkt.getLocation()).toVector();
                        double accLen = acceleration.length();
                        if (accLen > 1e-9) {
                            acceleration.multiply(1 / accLen);
                        }
                        switch (type) {
                            case "飞蛇":
                                acceleration.multiply(0.035);
                                break;
                            case "钨钢悬浮坦克":
                                acceleration.multiply(0.02);
                                break;
                            default:
                                acceleration.multiply(0.04);
                        }
                        // shoot projectile
                        switch (type) {
                            case "探测怪":
                            case "钨钢无人机":
                            {
                                int shootInterval = 0;
                                String projectileType = "";
                                switch (type) {
                                    case "探测怪":
                                        shootInterval = 10;
                                        projectileType = "激光";
                                        break;
                                    case "钨钢无人机":
                                        shootInterval = 15;
                                        projectileType = "钨钢光球";
                                        break;
                                }
                                if ((indexAI + 1) % shootInterval == 0) {
                                    double shootSpd;
                                    if (type.equals("探测怪"))
                                        shootSpd = 2.5;
                                    else
                                        shootSpd = 1.5;
                                    Vector projectileV = MathHelper.getDirection(
                                            monsterBkt.getEyeLocation(), target.getEyeLocation(), shootSpd);
                                    EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                            monsterBkt, projectileV, EntityHelper.getAttrMap(monsterBkt), projectileType);
                                    shootInfo.properties.put("gravity", 0d);
                                    shootInfo.properties.put("penetration", 10);
                                    if (type.equals("探测怪"))
                                        shootInfo.properties.put("blockHitAction", "thru");
                                    EntityHelper.spawnProjectile(shootInfo);
                                }
                            }
                        }
                    }
                    Vector velocity = monsterBkt.getVelocity().multiply(0.975);
                    velocity.add(acceleration.clone().multiply(speedMultiKnockback));
                    double maxSpeed;
                    maxSpeed = speedMultiKnockback;
                    if (velocity.lengthSquared() > maxSpeed * maxSpeed) {
                        velocity.multiply(maxSpeed / velocity.length());
                    }
                    monsterBkt.setVelocity(velocity);
                    break;
                }
            case "恶魔之眼":
            case "噬魂怪":
            case "地狱蝙蝠":
            case "丛林蝙蝠":
            case "恶魔":
            case "红恶魔":
            case "巫毒恶魔":
            case "饿鬼":
            case "飞翔史莱姆":
            case "雪花怪":
                {
                    // determines if the direction should be updated
                    boolean changeDirection = indexAI % 60 <= 10;
                    // update acceleration if needed
                    if (changeDirection) {
                        Vector acceleration = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                        double accLen = acceleration.length();
                        if (accLen < 1e-9)
                            acceleration = new Vector(0, 0.05, 0);
                        else
                            acceleration.multiply(0.1 / accLen);
                        extraVariables.put("acc", acceleration);
                    }
                    // tweak velocity
                    Vector acc = (Vector) extraVariables.get("acc");
                    if (acc == null) {
                        acc = new Vector(0, 0.1, 0);
                        extraVariables.put("acc", acc);
                    }
                    switch (type) {
                        case "噬魂怪":
                        case "雪花怪":
                            if (indexAI % 60 == 45) {
                                acc = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                                double accLen = acc.length();
                                if (accLen < 1e-9)
                                    acc = new Vector(0, 0.03, 0);
                                else
                                    acc.multiply(-0.03 / accLen);
                                acc.setY(0.05);
                                extraVariables.put("acc", acc);
                            }
                    }
                    Vector velocity = monsterBkt.getVelocity().multiply(0.975)
                            .add(acc.clone().multiply(speedMultiKnockback));
                    double spd = 0.75 * speedMultiKnockback;
                    double velLen = velocity.length();
                    if (velLen > spd)
                        velocity.multiply(spd / velLen);
                    monsterBkt.setVelocity(velocity);
                    // projectiles
                    switch (type) {
                        case "恶魔":
                        case "巫毒恶魔":
                            if (indexAI % 20 == 0) {
                                Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 0.05);
                                EntityHelper.ProjectileShootInfo projectileShootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "恶魔之镰");
                                EntityHelper.spawnProjectile(projectileShootInfo);
                            }
                            break;
                        case "红恶魔":
                            if (indexAI % 20 == 0) {
                                Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 1);
                                EntityHelper.ProjectileShootInfo projectileShootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "邪恶三叉戟");
                                EntityHelper.spawnProjectile(projectileShootInfo);
                            }
                            break;
                    }
                    break;
                }
            case "幽灵":
            case "诅咒骷髅头":
            case "巨型诅咒骷髅头":
            case "地牢幽魂":
            case "死神":
            case "致命球":
            case "胡闹鬼":
            case "克苏鲁的仆从":
            case "陨石怪":
                {
                    double accelerationLen, speed, retargetDist;
                    switch (type) {
                        case "致命球":
                            accelerationLen = 0.15;
                            speed = 1.75;
                            retargetDist = 16;
                            break;
                        case "地牢幽魂":
                            accelerationLen = 0.1;
                            speed = 2;
                            retargetDist = 8;
                            break;
                        default:
                            accelerationLen = 0.075;
                            retargetDist = 12;
                            speed = 1;
                    }
                    // movement
                    speed *= speedMultiKnockback;
                    Vector acc;
                    if (monsterBkt.getLocation().distanceSquared(target.getLocation()) > retargetDist * retargetDist)
                        indexAI = (int) (Math.random() * 5);
                    if (indexAI < 8) {
                        acc = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), accelerationLen);
                        extraVariables.put("acc", acc);
                    }
                    else {
                        acc = (Vector) extraVariables.get("acc");
                        if (acc == null) {
                            acc = new Vector(0, accelerationLen, 0);
                            extraVariables.put("acc", acc);
                        }
                    }
                    Vector vel = monsterBkt.getVelocity().multiply(0.975)
                            .add(acc.clone().multiply(speedMultiKnockback));
                    double velLen = vel.length();
                    if (velLen > speed) {
                        vel.multiply(speed / velLen);
                    }
                    monsterBkt.setVelocity(vel);
                    // projectile
                    if (type.equals("巨型诅咒骷髅头")) {
                        if (indexAI % 40 == 39) {
                            Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 1.5);
                            EntityHelper.ProjectileShootInfo projectileShootInfo = new EntityHelper.ProjectileShootInfo(
                                    monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "诅咒骷髅头");
                            EntityHelper.spawnProjectile(projectileShootInfo);
                        }
                    }
                    break;
                }
            case "尖刺史莱姆":
            case "水晶史莱姆":
                {
                    if (indexAI >= 0) {
                        if (monsterBkt.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                            String projectileName = type.equals("尖刺史莱姆") ? "尖刺" : "水晶";
                            HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                            for (int i = 0; i < 25; i ++) {
                                Vector projVel = new Vector(Math.random() * 2 - 1, 0, Math.random() * 2 - 1);
                                double projVelLen = projVel.length();
                                if (projVelLen < 1e-9) continue;
                                if (type.equals("尖刺史莱姆")) {
                                    projVel.multiply( (0.3 + Math.random() * 0.2) / projVelLen);
                                    projVel.setY(0.4 + Math.random() * 0.2);
                                } else {
                                    projVel.multiply( (0.4 + Math.random() * 0.4) / projVelLen);
                                    projVel.setY(0.4 + Math.random() * 0.6);
                                }
                                EntityHelper.ProjectileShootInfo projInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, projVel, attrMap, projectileName);
                                EntityHelper.spawnProjectile(projInfo);
                            }
                            // -40 ~ -25
                            indexAI = (int) (-25 - 15 * Math.random());
                        }
                    }
                    break;
                }
            case "弹力史莱姆":
                {
                    if (indexAI % 10 == 9) {
                        Vector projVel = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                        double projVelLen = projVel.length();
                        if (projVelLen > 1e-9 && projVelLen < 48) {
                            double ticksFlight = Math.ceil(projVelLen);
                            projVel.multiply(1 / projVelLen);
                            projVel.setY(projVel.getY() + ticksFlight * 0.025);
                            EntityHelper.ProjectileShootInfo projInfo = new EntityHelper.ProjectileShootInfo(
                                    monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "挥发明胶");
                            projInfo.properties.put("penetration", 1);
                            projInfo.properties.put("blockHitAction", "bounce");
                            projInfo.properties.put("bounce", 5);
                            projInfo.properties.put("noGravityTicks", 0);
                            EntityHelper.spawnProjectile(projInfo);
                        }
                    }
                    break;
                }
            case "":
                {
                    break;
                }
        }
        if (hasContactDamage) {
            AxisAlignedBB bb = monster.getBoundingBox();
            double xWidth = (bb.d - bb.a) / 2, zWidth = (bb.f - bb.c) / 2, height = (bb.e - bb.b) / 2;
            Vector initLoc = new Vector(bb.a + xWidth, bb.b + height, bb.c + zWidth);
            Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(monsterBkt.getWorld(),
                    initLoc, initLoc.clone().add(monsterBkt.getVelocity()),
                    xWidth, height, zWidth,
                    (Entity entity) -> entity instanceof EntityPlayer);
            double damage = EntityHelper.getAttrMap(monsterBkt).getOrDefault("damage", 1d);
            for (HitEntityInfo hitEntityInfo : toDamage) {
                EntityHelper.handleDamage(monsterBkt, hitEntityInfo.getHitEntity().getBukkitEntity(),
                        damage, "DirectDamage");
            }
        }
        return indexAI + 1;
    }
}
