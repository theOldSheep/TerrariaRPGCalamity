package terraria.entity.monster;


import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.Hash;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.HitEntityInfo;
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
        int mobAmount = EntityHelper.getMetadata(target, "mobAmount").asInt();
        mobAmount += addOrRemove ? 1 : -1;
        EntityHelper.setMetadata(target, "mobAmount", mobAmount);
    }
    // setup monster target
    public static Player updateMonsterTarget(Player target, EntityLiving monster) {
        EntityPlayer targetNMS = ((CraftPlayer) target).getHandle();
        org.bukkit.entity.Entity monsterBkt = monster.getBukkitEntity();
        // the monster's ticks lived is set to represent the ticks of losing any target
        if (
                // target is not online / not logged in etc.
                !PlayerHelper.isProperlyPlaying(target) ||
                // target is in a different world
                targetNMS.getWorld() != monster.getWorld() ||
                // distance > 64
                target.getLocation().distanceSquared(monsterBkt.getLocation()) > 4096) {
            monster.ticksLived = 9999;
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
                    double currDistSqr = monsterBkt.getLocation().distanceSquared(checkPlayer.getLocation());
                    if (currDistSqr > newTargetDistSqr) continue;
                    if (monster.hasLineOfSight( ((CraftPlayer) checkPlayer).getHandle() )) {
                        newTarget = checkPlayer;
                        newTargetDistSqr = currDistSqr;
                    }
                }
            }
            if (newTarget != null) {
                tweakPlayerMonsterSpawnedAmount(target, false);
                tweakPlayerMonsterSpawnedAmount(newTarget, true);
                return newTarget;
            } else {
                monster.die();
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
                // knockback speed reduction decreases linearly and disappears after at most 0.5 second
                double kbFactor = kbFactorMetadata.asDouble() - 0.1;
                if (kbFactor > 1e-5) {
                    speedMultiKnockback = 1 - kbFactor;
                    EntityHelper.setMetadata(monsterBkt, "kbFactor", kbFactor);
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
                                Vector velocity = monsterBkt.getVelocity();
                                velocity.add(acceleration);
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
                                Vector velocity = targetLoc.subtract(monsterBkt.getEyeLocation()).toVector().normalize();
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, velocity, EntityHelper.getAttrMap(monsterBkt), "鸟妖羽毛");
                                shootInfo.properties.put("penetration", 10);
                                EntityHelper.spawnProjectile(shootInfo);
                        }
                    }
                    break;
                }
            case "恶魔之眼":
            case "探测怪":
            case "飞蛇":
            case "钨钢悬浮坦克":
            case "钨钢无人机":
                {
                    if (type.equals("探测怪")) {
                        ArrayList<org.bukkit.entity.Entity> bossLst = BossHelper.bossMap.get("毁灭者");
                        if (bossLst != null) {
                            org.bukkit.entity.Entity boss = bossLst.get(0);
                            target = (Player) EntityHelper.getMetadata(boss, "target").value();
                        }
                    }
                    boolean isFleeing = false;
                    Vector acceleration = null;
                    switch (type) {
                        case "探测怪":
                        case "恶魔之眼":
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
                        if (acceleration.lengthSquared() > 1e-9) {
                            acceleration.normalize();
                        }
                        switch (type) {
                            case "飞蛇":
                                acceleration.multiply(0.1);
                                break;
                            case "钨钢悬浮坦克":
                                acceleration.multiply(0.05);
                                break;
                            case "恶魔之眼":
                                acceleration.multiply(0.25);
                                if (indexAI % 60 > 45)
                                    acceleration.multiply(-0.5);
                                else if (indexAI % 60 > 15)
                                    acceleration.multiply(0);
                                break;
                            default:
                                acceleration.multiply(0.175);
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
                                if (indexAI % shootInterval == 0) {
                                    Vector projectileV = (target.getEyeLocation()).subtract(monsterBkt.getEyeLocation()).toVector();
                                    if (projectileV.lengthSquared() > 1e-9) {
                                        projectileV.normalize();
                                        if (type.equals("探测怪"))
                                            projectileV.multiply(2.5);
                                        else
                                            projectileV.multiply(1.5);
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
                    }
                    Vector velocity = monsterBkt.getVelocity();
                    velocity.add(acceleration);
                    double maxSpeed;
                    if (type.equals("恶魔之眼"))
                        maxSpeed = 1.15 * speedMultiKnockback;
                    else
                        maxSpeed = 1.5 * speedMultiKnockback;
                    if (velocity.lengthSquared() > maxSpeed * maxSpeed) {
                        velocity.multiply(maxSpeed / velocity.length());
                    }
                    monsterBkt.setVelocity(velocity);
                    break;
                }
            case "":
                {
                    break;
                }
        }
        if (hasContactDamage) {
            AxisAlignedBB bb = monster.getBoundingBox();
            Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(monsterBkt.getWorld(),
                    monsterBkt.getLocation().toVector(), monsterBkt.getLocation().toVector().add(monsterBkt.getVelocity()),
                    bb.d - bb.a, bb.e - bb.b, bb.f - bb.c,
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
