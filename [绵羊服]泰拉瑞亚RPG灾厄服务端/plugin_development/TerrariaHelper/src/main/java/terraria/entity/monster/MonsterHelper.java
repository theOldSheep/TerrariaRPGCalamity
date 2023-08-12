package terraria.entity.monster;


import net.minecraft.server.v1_12_R1.*;
import net.minecraft.server.v1_12_R1.Entity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.projectile.HitEntityInfo;
import terraria.gameplay.EventAndTime;
import terraria.util.*;
import terraria.util.MathHelper;

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
                EventAndTime.currentEvent != null && EventAndTime.currentEvent != EventAndTime.Events.NONE)
            prefixToCheck.add(EventAndTime.currentEvent.toString());
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
        // -> prevent normal slime being spawned instead of lava slime etc.
        prefixToCheck.add(playerHeight == WorldHelper.HeightLayer.UNDERWORLD ? "underworld" : "default");
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
                        result.healthMulti  = 6d;
                        result.defenceMulti = 3.5d;
                        result.damageMulti  = 2d;
                        break;
                    case PRE_MOON_LORD:
                        result.healthMulti  = 10d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 3d;
                        break;
                    case PRE_PROFANED_GODDESS:
                        result.healthMulti  = 10d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 3d;
                        // TODO: umcomment after finishing post-moonlord contents
//                        result.healthMulti  = 20d;
//                        result.defenceMulti = 10d;
//                        result.damageMulti  = 3.5d;
                        break;
                    case POST_PROFANED_GODDESS:
                        result.healthMulti  = 35d;
                        result.defenceMulti = 15d;
                        result.damageMulti  = 4d;
                        break;
                }
                break;
            case PRE_PLANTERA:
                switch (playerProgress) {
                    case PRE_MOON_LORD:
                        result.healthMulti  = 5d;
                        result.defenceMulti = 3d;
                        result.damageMulti  = 2d;
                        break;
                    case PRE_PROFANED_GODDESS:
                        result.healthMulti  = 5d;
                        result.defenceMulti = 3d;
                        result.damageMulti  = 2d;
                        // TODO: umcomment after finishing post-moonlord contents
//                        result.healthMulti  = 8d;
//                        result.defenceMulti = 4d;
//                        result.damageMulti  = 2.75d;
                        break;
                    case POST_PROFANED_GODDESS:
                        result.healthMulti  = 15d;
                        result.defenceMulti = 6d;
                        result.damageMulti  = 3.5d;
                        break;
                }
                break;
            case PRE_MOON_LORD:
                switch (playerProgress) {
                    case PRE_PROFANED_GODDESS:
                        // TODO: umcomment after finishing post-moonlord contents
//                        result.healthMulti  = 4d;
//                        result.defenceMulti = 3d;
//                        result.damageMulti  = 1.5d;
                        break;
                    case POST_PROFANED_GODDESS:
                        result.healthMulti  = 6d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 2.5d;
                        break;
                }
                break;
            case PRE_PROFANED_GODDESS:
                if (playerProgress == PlayerHelper.GameProgress.POST_PROFANED_GODDESS) {
                    result.healthMulti  = 3.5d;
                    result.defenceMulti = 2.5d;
                    result.damageMulti  = 1.5d;
                }
                break;
        }
        return result;
    }
    public static void initMonsterInfo(Player target, String monsterProgressRequiredMin, EntityLiving monster,
                                       String type, String variant, HashMap<String, Object> extraVariables, boolean isMonsterPart) {
        // determine if the player's game progress is appropriate
        ConfigurationSection typeConfigSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection("mobInfo." + type);
        ConfigurationSection variantConfigSection = typeConfigSection.getConfigurationSection("variants." + variant);
        ConfigurationSection attributeConfigSection = variantConfigSection.getConfigurationSection("attributes");
        org.bukkit.entity.Entity bukkitMonster = monster.getBukkitEntity();
        // if the monster is an event mob, setup progress info
        if (EventAndTime.currentEvent != EventAndTime.Events.NONE &&
                variant.startsWith(EventAndTime.currentEvent.toString()) ) {
            double killProgress = typeConfigSection.getDouble("eventProgress", 1d);
            org.bukkit.entity.Entity monsterBkt = monster.getBukkitEntity();
            EntityHelper.setMetadata(monsterBkt, EntityHelper.MetadataName.SPAWN_IN_EVENT, EventAndTime.currentEvent);
            EntityHelper.setMetadata(monsterBkt, EntityHelper.MetadataName.KILL_CONTRIBUTE_EVENT_PROGRESS, killProgress);
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
                    EntityHelper.setDamageType(bukkitMonster,
                            EntityHelper.damageTypeInternalNameMapping.getOrDefault(
                                    attributeConfigSection.getString(attribute), EntityHelper.DamageType.MELEE));
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
            ArrayList<LivingEntity> bossList = null;
            if (owningBoss != null) {
                bossList = BossHelper.getBossList(owningBoss);
            }
            if (bossList != null) {
                HashMap<String, Double> targets = (HashMap<String, Double>) EntityHelper.getMetadata(bossList.get(0), EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
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
        EntityHelper.setMetadata(bukkitMonster, EntityHelper.MetadataName.ATTRIBUTE_MAP, attrMap);
        LivingEntity bukkitMonsterLivingEntity = (LivingEntity) bukkitMonster;
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(444);
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25);
        bukkitMonsterLivingEntity.setHealth(health);
        // add 1 to target's amount of active monster
        if (!isMonsterPart)
            tweakPlayerMonsterSpawnedAmount(target, true);
        // set the monster's special info
        // no gravity
        switch (type) {
            case "水母":
            case "秃鹰":
            case "恶魔之眼":
            case "噬魂怪":
            case "恶魔":
            case "巫毒恶魔":
            case "红恶魔":
            case "饿鬼":
            case "巨型诅咒骷髅头":
            case "胡闹鬼":
            case "鸟妖":
            case "飞龙":
            case "吞噬者":
            case "骨蛇":
            case "妖精":
            case "飞蛇":
            case "诅咒骷髅头":
            case "地牢幽魂":
            case "致命球":
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
            case "流星火怪":
            case "千足蜈蚣":
            case "异星黄蜂":
            case "异星蜂王":
            case "漩泥怪":
            case "吮脑怪":
            case "星云浮怪":
            case "星云球":
            case "星尘细胞":
            case "银河织妖":
            case "流体入侵怪":
            case "克苏鲁的仆从":
            case "沼泽之眼":
            case "飞翔史莱姆":
            case "探测怪":
            case "???":
            case "深海吞食者":
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
            case "深海吞食者":
            case "精灵直升机":
            case "蛾怪":
            case "陨石怪":
            case "沼泽之眼":
            case "幽灵":
            case "死神":
            case "飞龙":
            case "吞噬者":
            case "骨蛇":
            case "流星火怪":
            case "千足蜈蚣":
            case "吮脑怪":
            case "星云浮怪":
            case "星云球":
            case "星尘细胞":
            case "银河织妖":
            case "克苏鲁的仆从":
                monster.noclip = true;
        }
        // glowing
        switch (type) {
            case "诅咒骷髅头":
            case "妖精":
            case "巨型诅咒骷髅头":
            case "巨像蛤":
            case "肉后巨像蛤":
                monster.glowing = true;
        }
        // AI removed for certain monsters to prevent bug
        {
            boolean removeAI = false;
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
                    case "独角兽":
                    case "白羊座":
                    case "地狱犬":
                    case "火龙怪":
                    case "闪耀炮手":
                    case "星辉史莱姆":
                        break;
                    default:
                        removeAI = true;
                }
            }
            // some special monsters (sea monsters most likely)
            switch (type) {
                case "???":
                    removeAI = true;
            }
            // remove AI when necessary
            if (removeAI) {
                EntityInsentient insMonster = (EntityInsentient) monster;
                World world = monster.world;
                MethodProfiler methodProfiler = world != null && world.methodProfiler != null ? world.methodProfiler : null;
                insMonster.goalSelector = new PathfinderGoalSelector(methodProfiler);
                insMonster.targetSelector = new PathfinderGoalSelector(methodProfiler);
            }
        }
        // attributes and other properties
        switch (type) {
            case "钨钢回转器":
            case "钨钢漫步者":
            {
                bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.45);
                break;
            }
            case "飞龙":
            case "骨蛇":
            case "吞噬者":
            case "千足蜈蚣":
            case "银河织妖":
            {
                if (!isMonsterPart) {
                    int additionalSegAmount;
                    switch (type) {
                        case "千足蜈蚣":
                            additionalSegAmount = 15;
                            break;
                        case "银河织妖":
                            additionalSegAmount = 10;
                            break;
                        default:
                            additionalSegAmount = 14;
                    }
                    Location loc = bukkitMonster.getLocation();
                    ArrayList<Slime> segments = new ArrayList<>(additionalSegAmount + 1);
                    segments.add((Slime) bukkitMonster);
                    for (int i = 0; i < additionalSegAmount; i++) {
                        org.bukkit.entity.Slime segment = (Slime) (new MonsterSlime(target, type, loc, true))
                                .getBukkitEntity();
                        HashMap<String, Double> attrMapSegment = EntityHelper.getAttrMap(segment);
                        attrMapSegment.put("damageMulti", 0.45);
                        attrMapSegment.put("defenceMulti", 2d);
                        EntityHelper.setMetadata(segment, EntityHelper.MetadataName.DAMAGE_TAKER, bukkitMonster);
                        segment.setCustomName(type + "§1");
                        segments.add(segment);
                        // extra segment info
                        switch (type) {
                            case "千足蜈蚣":
                                segment.setCustomName(type);
                                if (i + 1 < additionalSegAmount) {
                                    segment.addScoreboardTag("noDamage");
                                }
                                else {
                                    attrMapSegment.put("damageTakenMulti", 10d);
                                }
                                break;
                            case "银河织妖":
                                segment.setCustomName(type);
                                segment.addScoreboardTag("noDamage");
                                break;
                        }
                    }
                    extraVariables.put("attachments", segments);
                    // following option
                    EntityHelper.WormSegmentMovementOptions followInfo = new EntityHelper.WormSegmentMovementOptions();
                    switch (type) {
                        case "千足蜈蚣":
                            followInfo.setFollowDistance(1.5);
                            break;
                    }
                    extraVariables.put("wormMoveOption", followInfo);
                    // other properties
                    switch (type) {
                        case "千足蜈蚣":
                            monster.addScoreboardTag("noDamage");
                            break;
                        case "银河织妖":
                            Location[] formerLoc = new Location[10];
                            for (int i = 0; i < 10; i ++) {
                                formerLoc[i] = monster.getBukkitEntity().getLocation();
                            }
                            extraVariables.put("locs", formerLoc);
                            break;
                    }
                }
                break;
            }
            case "巨型陆龟":
            case "冰雪陆龟":
            {
                bukkitMonster.addScoreboardTag("noFallDamage");
                break;
            }
            case "白羊座":
            case "独角兽":
            case "腐化宝箱怪":
            case "神圣宝箱怪":
            case "地狱犬":
            case "火龙怪":
            {
                    bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.75);
                    break;
                }
            case "圣骑士":
            case "骷髅狙击手":
            {
                bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.25);
                break;
            }
            case "死灵法师":
            case "魔教徒":
            case "褴褛邪教徒法师":
            {
                bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
                break;
            }
            case "骷髅特警":
            {
                bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3);
                break;
            }
            case "精灵直升机":
            case "深海吞食者":
            {
                ((MonsterSlime) monster).indexAI = (int) (Math.random() * 3600);
                break;
            }
            // minions should not actively damage those
            case "腐化囊":
            case "???":
            {
                monster.removeScoreboardTag("isMonster");
                break;
            }
            // this is a pseudo-projectile and should take no damage at all.
            case "星云球":
            {
                monster.addScoreboardTag("noDamage");
                break;
            }
        }
        ((LivingEntity) monster.getBukkitEntity()).getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
    }
    public static void tweakPlayerMonsterSpawnedAmount(Player target, boolean addOrRemove) {
        if (target == null) return;
        int mobAmount = EntityHelper.getMetadata(target, EntityHelper.MetadataName.PLAYER_MONSTER_SPAWNED_AMOUNT).asInt();
        mobAmount += addOrRemove ? 1 : -1;
        EntityHelper.setMetadata(target, EntityHelper.MetadataName.PLAYER_MONSTER_SPAWNED_AMOUNT, mobAmount);
    }
    // setup monster target
    public static Player updateMonsterTarget(Player target, EntityLiving monster, String type) {
        EntityPlayer targetNMS = ((CraftPlayer) target).getHandle();
        org.bukkit.entity.Entity monsterBkt = monster.getBukkitEntity();
        // boss summoned monsters should attack the boss's target
        String owningBoss = TerrariaHelper.mobSpawningConfig.getString("mobInfo." + type + ".owningBoss");
        if (owningBoss != null) {
            ArrayList<LivingEntity> bossLst = BossHelper.getBossList(type);
            if (bossLst != null) {
                org.bukkit.entity.Entity boss = bossLst.get(0);
                return (Player) EntityHelper.getMetadata(boss, EntityHelper.MetadataName.BOSS_TARGET_MAP).value();
            }
        }
        // the monster's ticks lived is set to represent the ticks of losing any target
        // this is triggered when target is not online / not logged in; target is in a different world
        if (
                !PlayerHelper.isProperlyPlaying(target) ||
                        targetNMS.getWorld() != monster.getWorld()) {
            monster.ticksLived = Math.max(monster.ticksLived, 9999);
        }
        // distance > 96
        else if (
                target.getLocation().distanceSquared(monsterBkt.getLocation()) > 9216) {
            monster.ticksLived = Math.max(monster.ticksLived, 150);
        }
        // reset ticks out of combat when target can be seen
        else if (monster.hasLineOfSight(targetNMS)) {
            monster.ticksLived = 1;
        }
        // retarget mechanic
        {
            Player newTarget = null;
            switch (type) {
                case "千足蜈蚣": {
                    if (monster.ticksLived >= 150 || !canCrawltipedeCharge(target))
                        for (org.bukkit.entity.Entity checkEntity : monsterBkt.getNearbyEntities(64, 128, 64)) {
                            if (checkEntity instanceof Player) {
                                Player checkPlayer = (Player) checkEntity;
                                if (canCrawltipedeCharge(checkPlayer)) {
                                    newTarget = checkPlayer;
                                    break;
                                }
                            }
                        }
                    break;
                }
                // for default retargeting
                // after ten seconds without any proper target, attempt to retarget
                default:
                    if (monster.ticksLived >= 200) {
                        // find possible target
                        double targetAttemptRadius = 96;
                        double newTargetDistSqr = 9216;
                        for (org.bukkit.entity.Entity checkEntity : monsterBkt.getNearbyEntities(targetAttemptRadius, targetAttemptRadius, targetAttemptRadius)) {
                            if (checkEntity instanceof Player) {
                                Player checkPlayer = (Player) checkEntity;
                                if (PlayerHelper.isProperlyPlaying(checkPlayer)) {
                                    double currDistSqr = monsterBkt.getLocation().distanceSquared(checkPlayer.getLocation());
                                    if (currDistSqr > newTargetDistSqr) continue;
                                    if (monster.hasLineOfSight(((CraftPlayer) checkPlayer).getHandle())) {
                                        newTarget = checkPlayer;
                                        newTargetDistSqr = currDistSqr;
                                    }
                                }
                            }
                        }
                        if (newTarget == null) {
                            // target's monster spawned amount is tweaked in die()
                            monster.die();
                            return null;
                        }
                    }
            }
            // update target
            if (newTarget != null) {
                tweakPlayerMonsterSpawnedAmount(target, false);
                tweakPlayerMonsterSpawnedAmount(newTarget, true);
                return newTarget;
            }
        }
        return target;
    }
    public static void handleMonsterDrop(LivingEntity monsterBkt) {
        String type = GenericHelper.trimText(monsterBkt.getName());
        // other mechanisms after death
        switch (type) {
            case "沼泽之眼": {
                EntityHelper.spawnProjectile(monsterBkt, new Vector(), EntityHelper.getAttrMap(monsterBkt), "腐蚀之云");
                break;
            }
        }
        ArrayList<String> toDrop = new ArrayList<>();
        // celestial pillar
        if (monsterBkt.getScoreboardTags().contains("isPillar")) {
            String dropType = type.replace("柱", "碎片");
            ItemStack itemToDrop = ItemHelper.getItemFromDescription(dropType);
            for (int i = 0; i < 25; i ++) {
                // loop 25 times, each time dropping 1-3
                itemToDrop.setAmount( (int) (1 + Math.random() * 3) );
                Location dropLoc = monsterBkt.getEyeLocation().add(
                        Math.random() * 10 - 5, Math.random() * 16 - 8, Math.random() * 10 - 5);
                ItemHelper.dropItem(dropLoc, itemToDrop);
            }
        }
        // mimic extra loot
        else {
            switch (type) {
                case "神圣宝箱怪":
                case "腐化宝箱怪": {
                    String[] candidateDrops = new String[]{"魔法箭袋", "再生手环", "泰坦手套", "炼金石", "十字项链", "天神之剑",
                            type.equals("神圣宝箱怪") ? "代达罗斯风暴弓" : "腐香囊"};
                    toDrop.add(candidateDrops[(int) (Math.random() * candidateDrops.length)]);
                    break;
                }
            }
            // default monsters
            // drops from its own type
            toDrop.addAll(TerrariaHelper.entityConfig.getStringList(type + ".drop"));
            // drops inherited from its parent type
            MetadataValue parentTypeVal = EntityHelper.getMetadata(monsterBkt, EntityHelper.MetadataName.MONSTER_PARENT_TYPE);
            if (parentTypeVal != null) {
                String parentType = parentTypeVal.asString();
                if (!parentType.equals(type)) {
                    toDrop.addAll(TerrariaHelper.entityConfig.getStringList(parentType + ".drop"));
                }
            }
        }
        for (String dropDesc : toDrop) {
            ItemStack itemToDrop = ItemHelper.getItemFromDescription(dropDesc);
            if (itemToDrop != null && itemToDrop.getAmount() > 0 && itemToDrop.getType() != Material.AIR) {
                if (dropDesc.startsWith("铜币"))
                    GenericHelper.dropMoney(monsterBkt.getLocation(), itemToDrop.getAmount());
                else
                    ItemHelper.dropItem(monsterBkt.getLocation(), itemToDrop);
            }
        }
    }
    // monster AI and helper functions
    private static boolean canCrawltipedeCharge(Player target) {
        for (int i = -1; i >= -6; i--) {
            if (target.getLocation().getBlock().getRelative(0, i, 0).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }
    public static int monsterAI(EntityInsentient monster, double defaultMovementSpeed, Player target, String type,
                                int indexAI, HashMap<String, Object> extraVariables, boolean isMonsterPart) {
        monster.setGoalTarget( ((CraftPlayer) target).getHandle(), EntityTargetEvent.TargetReason.CUSTOM, false);
        LivingEntity monsterBkt = (LivingEntity) monster.getBukkitEntity();
        // knockback can then be used to modify monster's movement speed temporarily
        double speedMultiKnockback = 1;
        {
            MetadataValue kbFactorMetadata = EntityHelper.getMetadata(monsterBkt, EntityHelper.MetadataName.KNOCKBACK_SLOW_FACTOR);
            if (kbFactorMetadata != null) {
                double kbFactor = kbFactorMetadata.asDouble();
                if (kbFactor > 1e-5) {
                    speedMultiKnockback = 1 - kbFactor;
                    // knockback speed reduction decreases every tick
                    EntityHelper.setMetadata(monsterBkt, EntityHelper.MetadataName.KNOCKBACK_SLOW_FACTOR, kbFactor - 0.1);
                } else {
                    EntityHelper.setMetadata(monsterBkt, EntityHelper.MetadataName.KNOCKBACK_SLOW_FACTOR, null);
                }
            }
        }
        monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(defaultMovementSpeed * speedMultiKnockback);
        // the monster's additional AI and drop
        boolean hasContactDamage = true;
        boolean lookAtPlayer = true;
        if (!isMonsterPart) {
            switch (type) {
                case "僵尸":
                case "钨钢回转器":
                case "稻草人": {
                    if (monster.getHealth() > 0) {
                        if (indexAI == 0)
                            indexAI = (int) (Math.random() * 100);
                        else if (indexAI > 160 && monster.onGround) {
                            indexAI = (int) (Math.random() * 100);
                            monster.motY = speedMultiKnockback;
                        }
                    }
                    break;
                }
                case "沼泽怪": {
                    if (monster.getHealth() > 0) {
                        if (indexAI == 0)
                            indexAI = (int) (Math.random() * 100);
                        else if (indexAI > 160) {
                            indexAI = (int) (Math.random() * 100);
                            // 0.25 ~ 0.55
                            double moveSpeed = 0.25 + Math.random() * 0.3;
                            monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(moveSpeed);
                            ((MonsterHusk) monster).defaultSpeed = moveSpeed;
                        }
                    }
                    break;
                }
                case "鸟妖": {
                    if (monster.getHealth() > 0) {
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
                    }
                    break;
                }
                case "探测怪":
                case "飞蛇":
                case "钨钢悬浮坦克":
                case "钨钢无人机": {
                    if (monster.getHealth() > 0) {
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
                                case "钨钢无人机": {
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
                    }
                    break;
                }
                case "秃鹰": {
                    if (monster.getHealth() > 0) {
                        // flies after being hurt or within 10 blocks of target
                        boolean fly = monster.getHealth() + 1e-5 < monster.getMaxHealth() ||
                                monsterBkt.getLocation().distanceSquared(target.getLocation()) < 100;
                        // when flying, alternating from flying into target and flying above target
                        if (fly) {
                            Location targetLoc = target.getEyeLocation();
                            if (indexAI % 160 < 75)
                                targetLoc.add(0, 10, 0);
                            Vector velocity = monsterBkt.getVelocity();
                            Vector acceleration = MathHelper.getDirection(monsterBkt.getLocation(), targetLoc, 0.15);
                            velocity.add(acceleration);
                            // regularize velocity
                            double velLen = velocity.length();
                            if (velLen > 0.5)
                                velocity.multiply(0.5 / velLen);
                            monsterBkt.setVelocity(velocity);
                        }
                        else {
                            indexAI = -1;
                        }
                    }
                    break;
                }
                case "水母": {
                    if (monster.getHealth() > 0) {
                        // periodically becomes invincible
                        {
                            if (indexAI % 200 == 0) {
                                monsterBkt.setGlowing(true);
                                monsterBkt.addScoreboardTag("noDamage");
                                EntityHelper.tweakAttribute(monsterBkt, "damageMulti", "0.3", true);
                            } else if (indexAI % 200 == 100) {
                                monsterBkt.setGlowing(false);
                                monsterBkt.removeScoreboardTag("noDamage");
                                EntityHelper.tweakAttribute(monsterBkt, "damageMulti", "0.3", false);
                            }
                        }
                        // dashes into player when in water, can not move on land.
                        switch (monsterBkt.getLocation().getBlock().getType()) {
                            case WATER:
                            case STATIONARY_WATER: {
                                if (indexAI % 30 == 0) {
                                    indexAI += (int) (Math.random() * 10);
                                    double accLen = Math.random() * 0.2 + 0.65;
                                    Vector velocity = monsterBkt.getVelocity();
                                    Vector acceleration = MathHelper.getDirection(
                                            monsterBkt.getEyeLocation(), target.getEyeLocation(), accLen);
                                    velocity.add(acceleration);
                                    monsterBkt.setVelocity(velocity);
                                }
                                monsterBkt.setGravity(false);
                                break;
                            }
                            default:
                                monsterBkt.setGravity(true);
                        }
                    }
                    break;
                }
                case "巨像蛤":
                case "肉后巨像蛤": {
                    if (monster.getHealth() > 0) {
                        // stationary on full health
                        if (monster.getHealth() + 1e-5 > monster.getMaxHealth()) {
                            monsterBkt.setVelocity(new Vector());
                        }
                        // smashes enemy periodically otherwise
                        else {
                            // when smashing
                            if (indexAI == -1) {
                                monsterBkt.setVelocity(new Vector(0, -1, 0));
                                indexAI--;
                                // prepare to land
                                Location futureLoc = monsterBkt.getLocation().add(monsterBkt.getVelocity()).subtract(0, 0.1, 0);
                                if (futureLoc.getY() < target.getEyeLocation().getY() &&
                                        futureLoc.getBlock().getType().isSolid()) {
                                    monster.noclip = false;
                                    monsterBkt.removeScoreboardTag("noDamage");
                                    indexAI = 0;
                                }
                            }
                            Bukkit.broadcastMessage(indexAI + ", " + monsterBkt.getScoreboardTags());
                            if (indexAI > 30) {
                                monster.noclip = true;
                                monsterBkt.teleport(target.getLocation().add(0, 16, 0));
                                monsterBkt.addScoreboardTag("noDamage");
                                indexAI = -2;
                            }
                        }
                    }
                    break;
                }
                case "恶魔之眼":
                case "妖精":
                case "噬魂怪":
                case "地狱蝙蝠":
                case "丛林蝙蝠":
                case "恶魔":
                case "红恶魔":
                case "巫毒恶魔":
                case "饿鬼":
                case "飞翔史莱姆":
                case "雪花怪": {
                    if (monster.getHealth() > 0) {
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
                        Vector velocity = monsterBkt.getVelocity().multiply(0.9)
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
                    }
                    break;
                }
                case "深海吞食者": {
                    if (monster.getHealth() > 0) {
                        // determines if the direction should be updated
                        if (indexAI % 100 < 80) {
                            Vector velocity = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                            velocity.multiply(0.05);
                            monsterBkt.setVelocity(velocity);
                        }
                        else if (indexAI % 100 == 80) {
                            monsterBkt.setVelocity(MathHelper.getDirection(
                                    monsterBkt.getEyeLocation(), target.getEyeLocation(), 2));
                        }
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
                case "沼泽之眼":
                case "陨石怪": {
                    if (monster.getHealth() > 0) {
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
                        } else {
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
                    }
                    break;
                }
                case "尖刺史莱姆":
                case "水晶史莱姆": {
                    if (indexAI >= 0 && monster.getHealth() > 0) {
                        if (monsterBkt.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) {
                            String projectileName = type.equals("尖刺史莱姆") ? "尖刺" : "水晶";
                            HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                            for (int i = 0; i < 25; i++) {
                                Vector projVel = new Vector(Math.random() * 2 - 1, 0, Math.random() * 2 - 1);
                                double projVelLen = projVel.length();
                                if (projVelLen < 1e-9) continue;
                                if (type.equals("尖刺史莱姆")) {
                                    projVel.multiply((0.3 + Math.random() * 0.2) / projVelLen);
                                    projVel.setY(0.4 + Math.random() * 0.2);
                                } else {
                                    projVel.multiply((0.4 + Math.random() * 0.4) / projVelLen);
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
                case "弹力史莱姆": {
                    if (indexAI % 10 == 9 && monster.getHealth() > 0) {
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
                case "飞龙":
                case "骨蛇":
                case "吞噬者": {
                    lookAtPlayer = false;
                    ArrayList<org.bukkit.entity.LivingEntity> segments = (ArrayList<org.bukkit.entity.LivingEntity>) extraVariables.get("attachments");
                    for (org.bukkit.entity.Entity entity : segments) {
                        ((LivingEntity) entity).setHealth(monsterBkt.getHealth());
                    }
                    if (monster.getHealth() > 0) {
                        int chargeStayTicks;
                        switch (type) {
                            case "骨蛇":
                                chargeStayTicks = 20;
                                break;
                            case "飞龙":
                                chargeStayTicks = 30;
                                break;
                            default:
                                chargeStayTicks = 25;
                        }
                        // strike
                        if (indexAI == 0) {
                            // to undo the automatic +1 below
                            indexAI--;
                            Location targetLoc = target.getLocation().add(0, 1, 0);
                            Vector vec = targetLoc.clone().subtract(monsterBkt.getLocation()).toVector();
                            double dist, minSpd;
                            switch (type) {
                                case "骨蛇":
                                    dist = 6;
                                    minSpd = 0.6;
                                    break;
                                case "飞龙":
                                    dist = 4;
                                    minSpd = 0.7;
                                    break;
                                default:
                                    dist = 8;
                                    minSpd = 0.85;
                            }
                            double len = vec.length();
                            if (len <= dist) indexAI = 1;
                            vec.multiply(Math.max(len / 20, minSpd) / len);
                            monsterBkt.setVelocity(vec);
                        }
                        // recoil
                        else if (indexAI >= chargeStayTicks) {
                            Vector recoilVec;
                            double recoilMaxSpeed = 1, distVerticalRequired = 35;
                            switch (type) {
                                case "骨蛇":
                                    recoilVec = new Vector(0, -0.2, 0);
                                    break;
                                case "吞噬者":
                                    recoilVec = new Vector(0, -0.15, 0);
                                    break;
                                case "飞龙":
                                    recoilVec = new Vector(0, 0.2, 0);
                                    break;
                                default:
                                    recoilVec = new Vector(0, 0.3, 0);
                            }
                            Vector newVel = monsterBkt.getVelocity().add(recoilVec);
                            if (newVel.lengthSquared() > recoilMaxSpeed * recoilMaxSpeed) {
                                newVel.multiply(recoilMaxSpeed / newVel.length());
                            }
                            monsterBkt.setVelocity(newVel);
                            double distVertical = monsterBkt.getLocation().getY() - target.getLocation().getY();
                            if (Math.abs(distVertical) >= distVerticalRequired &&
                                    ((distVertical < 0) == (recoilVec.getY() < 0)))
                                indexAI = -1;
                        }
                    }
                    EntityHelper.handleSegmentsFollow(segments, (EntityHelper.WormSegmentMovementOptions) extraVariables.get("wormMoveOption"));
                    break;
                }
                case "瘟疫龟":
                case "巨型陆龟":
                case "冰雪陆龟": {
                    if (monster.getHealth() > 0) {
                        boolean hasBeenDamaged = ! (monsterBkt.getScoreboardTags().contains("notDamaged"));
                        if (hasBeenDamaged) {
                            monsterBkt.setCustomName(type);
                            indexAI = 999999;
                        }
                        monsterBkt.addScoreboardTag("notDamaged");
                        // setup wait time before rolling attack
                        int waitTime;
                        {
                            double distSqr = target.getLocation().distanceSquared(monsterBkt.getLocation());
                            if (distSqr > 25) waitTime = 15;
                            else if (distSqr > 12) waitTime = 25;
                            else waitTime = 60;
                        }
                        int rollProgress = indexAI - waitTime;
                        // prepare to roll
                        if (rollProgress == 0) {
                            monsterBkt.setGravity(false);
                            monsterBkt.setCustomName(type + "§1");
                            HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                            attrMap.put("damageMulti", 2d);
                            attrMap.put("defenceMulti", 2d);
                            ((MonsterHusk) monster).defaultSpeed = 0;
                        }
                        // leap
                        else if (rollProgress == 10) {
                            monsterBkt.setVelocity(monsterBkt.getVelocity().add( new Vector(0, 1, 0)) );
                            monster.setNoGravity(true);
                        }
                        // roll towards enemy
                        else if (rollProgress < 50 && rollProgress > 10) {
                            Vector dV = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                            if (dV.lengthSquared() > 1e-9) {
                                if (monsterBkt.getEyeLocation().getY() < target.getLocation().getY())
                                    dV.setY(dV.getY() + 5);
                                dV.normalize().multiply(8);
                                Vector newVel = monsterBkt.getVelocity().add(dV);
                                if (newVel.lengthSquared() > 1e-9) {
                                    newVel.multiply(0.1);
                                    // regulate the horizontal speed
                                    double yComp = newVel.getY();
                                    newVel.setY(0);
                                    double newVelLen = newVel.length();
                                    newVel.multiply(Math.min(newVelLen, 2) / newVelLen);
                                    newVel.setY(yComp);
                                    monsterBkt.setVelocity(newVel);
                                }
                            }
                        }
                        // land
                        else if (rollProgress == 50 || rollProgress >= 75) {
                            monster.setNoGravity(false);
                            HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                            attrMap.put("damageMulti", 1d);
                            attrMap.put("defenceMulti", 1d);
                            if (monsterBkt.isOnGround()) {
                                monsterBkt.setCustomName(type);
                                ((MonsterHusk) monster).defaultSpeed = 0.2;
                                indexAI = -1;
                            }
                        }
                    }
                    break;
                }
                case "蛾怪": {
                    if (monster.getHealth() > 0) {
                        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                        Vector v = monsterBkt.getVelocity();
                        if (indexAI < 10) {
                            if (target.getLocation().getY() > monsterBkt.getLocation().getY())
                                v.setY(v.getY() + 0.025);
                            else
                                v.setY(v.getY() - 0.025);
                            if (v.lengthSquared() > 0.75 * 0.75)
                                v.multiply(1.5 / v.length());
                        } else {
                            if (indexAI == 10) {
                                v = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                                if (v.lengthSquared() < 1e-9)
                                    v = new Vector(1, 0, 0);
                                v.setY(v.getY() * 0.5);
                                v.normalize();
                                // regulate velocity length
                                if (Math.random() < 0.5) {
                                    v.multiply(3);
                                    attrMap.put("damageMulti", 1.5);
                                } else {
                                    v.multiply(2.5);
                                    attrMap.put("damageMulti", 0.8);
                                }
                                extraVariables.put("vl", v);
                            }
                            v = (Vector) extraVariables.get("vl");
                            if (indexAI > 40)
                                indexAI = -1;
                        }
                        monsterBkt.setVelocity(v);
                    }
                    break;
                }
                case "精灵直升机": {
                    if (monster.getHealth() > 0) {
                        double indexY = (double) extraVariables.getOrDefault("iY", Math.random() * 360);
                        indexY += 2.5;
                        extraVariables.put("iY", indexY);
                        Vector v = target.getEyeLocation().add(MathHelper.xsin_degree(indexAI) * 16,
                                10 + MathHelper.xsin_degree(indexY) * 2,
                                MathHelper.xcos_degree(indexAI) * 16).subtract(monsterBkt.getLocation()).toVector();
                        v.multiply(0.025);
                        monsterBkt.setVelocity(v);
                        // shoot projectile
                        if (indexAI % 7 == 6) {
                            ItemUseHelper.playerUseItemSound(monsterBkt, "GUN", true);
                            Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(),
                                    target.getEyeLocation().add(
                                            5 * (Math.random() - 0.5), 5 * (Math.random() - 0.5), 5 * (Math.random() - 0.5)),
                                    1.25);
                            EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                    monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "火枪子弹");
                            EntityHelper.spawnProjectile(shootInfo);
                        }
                    }
                    break;
                }
                case "腐化宝箱怪":
                case "神圣宝箱怪": {
                    if (monster.getHealth() > 0) {
                        // regular jumping towards enemy
                        if (indexAI < 100) {}
                        // stop and reflect projectiles
                        else if (indexAI == 100) {
                            HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                            // tweak defence to be almost invincible
                            EntityHelper.tweakAttribute(attrMap, "defence", "500", true);
                            monsterBkt.addScoreboardTag("reflectProjectile");
                        }
                        else if (indexAI < 200) {
                            monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0d);
                            hasContactDamage = false;
                        }
                        // jump towards enemy again
                        else if (indexAI < 300) {
                            if (indexAI == 200) {
                                monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.75d);
                                HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                                // remove increased defence
                                EntityHelper.tweakAttribute(attrMap, "defence", "500", false);
                                monsterBkt.removeScoreboardTag("reflectProjectile");
                            }
                        }
                        switch (indexAI) {
                            // charge enemy
                            case 300:
                            case 350:
                                {
                                    monster.setNoGravity(true);
                                    monster.noclip = true;
                                    Vector v = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                                    double dist = v.length();
                                    if (dist < 1e-9)
                                        v = new Vector(0, 1, 0);
                                    else
                                        v.multiply(2 / v.length());
                                    monsterBkt.setVelocity(v);
                                    if (dist <= 10) {
                                        monster.setNoGravity(false);
                                        monster.noclip = false;
                                    } else {
                                        indexAI --;
                                    }
                                    break;
                                }
                            // prepare to smash enemy
                            case 400:
                                {
                                    monster.setNoGravity(true);
                                    monster.noclip = true;
                                    Location destination = target.getLocation().add(0, 8, 0);
                                    Vector v = destination.subtract(monsterBkt.getLocation()).toVector();
                                    if (v.lengthSquared() < 1e-9)
                                        v = new Vector(0, 1, 0);
                                    double vLen = v.length();
                                    v.multiply( Math.max(vLen / 4, 1) / vLen);
                                    monsterBkt.setVelocity(v);
                                    if (vLen > 3)
                                        indexAI --;
                                    break;
                                }
                            // smash enemy
                            case 401:
                                {
                                    Vector v = target.getEyeLocation().subtract(monsterBkt.getLocation()).toVector();
                                    monsterBkt.setVelocity(v.multiply(0.2));
                                    break;
                                }
                            // move to next cycle
                            case 406:
                                {
                                    monster.setNoGravity(false);
                                    monster.noclip = false;
                                    indexAI = -1;
                                    break;
                                }
                        }
                    }
                    break;
                }
                case "圣骑士": {
                    if (monster.getHealth() > 0) {
                        switch (indexAI) {
                            case 80:
                            case 100:
                            case 120:
                            case 140:
                            case 160:
                                // throw hammer
                                if (monsterBkt.hasLineOfSight(target)) {
                                    Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 1.5);
                                    EntityHelper.ProjectileShootInfo projInfo = new EntityHelper.ProjectileShootInfo(
                                            monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), EntityHelper.DamageType.MELEE, "圣骑士锤");
                                    projInfo.properties.put("blockHitAction", "thru");
                                    projInfo.properties.put("gravity", 0d);
                                    projInfo.properties.put("penetration", 999);
                                    projInfo.properties.put("liveTime", 24);
                                    EntityHelper.spawnProjectile(projInfo);
                                }
                                break;
                            case 180:
                                indexAI = 0;
                        }
                        if (indexAI < 180 && indexAI > 60) {
                            monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0d);
                        }
                    }
                    break;
                }
                case "死灵法师":
                case "褴褛邪教徒法师":
                case "魔教徒":
                case "骷髅狙击手":
                case "骷髅特警": {
                    if (monster.getHealth() > 0) {
                        double lastHealth = (double) extraVariables.getOrDefault("lh", monsterBkt.getHealth());
                        // prepare to teleport
                        if (monsterBkt.getHealth() < lastHealth - 1e-5) {
                            switch (type) {
                                case "死灵法师":
                                case "褴褛邪教徒法师":
                                case "魔教徒":
                                    indexAI = 10000;
                                    break;
                                default:
                                    indexAI = 0;
                            }
                        }
                        // teleport
                        if (indexAI > 10050)
                            switch (type) {
                                case "死灵法师":
                                case "褴褛邪教徒法师":
                                case "魔教徒":
                                    for (int i = 0; i < 3; i ++) {
                                        Location targetLoc = monsterBkt.getLocation().add(
                                                Math.random() * 24 - 12, Math.random() * 24 - 12, Math.random() * 24 - 12);
                                        Block blk = targetLoc.getBlock();
                                        if (! blk.getType().isSolid()) {
                                            while (! (blk.getRelative(0, 1, 0).getType().isSolid() &&
                                                      blk.getRelative(0, 2, 0).getType().isSolid()) ) {
                                                blk = blk.getRelative(0, -1, 0);
                                            }
                                            monsterBkt.teleport(blk.getLocation().add(0.5, 1, 0.5));
                                            indexAI = -1;
                                            break;
                                        }
                                    }
                            }
                        extraVariables.put("lh", monsterBkt.getHealth());
                        // attack AI
                        if (indexAI >= 60) {
                            monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0d);
                            switch (type) {
                                case "死灵法师":
                                    switch (indexAI) {
                                        case 70:
                                        case 90:
                                        case 110:
                                        case 130:
                                        case 150:
                                            extraVariables.put("dir", target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector());
                                            break;
                                        case 80:
                                        case 100:
                                        case 120:
                                        case 140:
                                        case 160:
                                            Vector dir = (Vector) extraVariables.get("dir");
                                            GenericHelper.handleStrikeLine(monsterBkt, monsterBkt.getEyeLocation(),
                                                    MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir),
                                                    48, 0.25, "", "255|125|255",
                                                    new ArrayList<>(), EntityHelper.getAttrMap(monsterBkt),
                                                    new GenericHelper.StrikeLineOptions().setBounceWhenHitBlock(true));
                                            break;
                                        case 161:
                                            indexAI = 10001;
                                    }
                                    break;
                                case "褴褛邪教徒法师":
                                    switch (indexAI) {
                                        case 80:
                                        case 90:
                                        case 100:
                                            if (monsterBkt.hasLineOfSight(target)) {
                                                Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 1.5);
                                                EntityHelper.ProjectileShootInfo projInfo = new EntityHelper.ProjectileShootInfo(
                                                        monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "亡魂");
                                                projInfo.properties.put("penetration", 2);
                                                projInfo.properties.put("liveTime", 24);
                                                EntityHelper.spawnProjectile(projInfo);
                                                break;
                                            }
                                            break;
                                        case 101:
                                            indexAI = 10001;
                                    }
                                    break;
                                case "魔教徒":
                                    switch (indexAI) {
                                        case 80:
                                        case 100:
                                        case 120:
                                            if (monsterBkt.hasLineOfSight(target)) {
                                                Vector projVel = target.getEyeLocation().subtract(monsterBkt.getEyeLocation()).toVector();
                                                double pVL = projVel.length(), spd = 1.25;
                                                int ticksTravel = (int) Math.round(pVL / spd);
                                                projVel.multiply(1d / pVL);
                                                EntityHelper.ProjectileShootInfo projInfo = new EntityHelper.ProjectileShootInfo(
                                                        monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "狱火爆破弹");
                                                projInfo.properties.put("penetration", 2);
                                                projInfo.properties.put("liveTime", ticksTravel);
                                                EntityHelper.spawnProjectile(projInfo);
                                                break;
                                            }
                                            break;
                                        case 121:
                                            indexAI = 10001;
                                    }
                                    break;
                                case "骷髅狙击手":
                                case "骷髅特警":
                                    if (indexAI > 60) {
                                        double shootAmount, shootInterval, spd;
                                        if (type.equals("骷髅特警")) {
                                            shootAmount = 6;
                                            shootInterval = 15;
                                            spd = 1;
                                        }
                                        // 骷髅狙击手
                                        else {
                                            shootAmount = 1;
                                            shootInterval = 25;
                                            spd = 5;
                                        }
                                        // fire projectile
                                        if (indexAI % shootInterval == 0) {
                                            ItemUseHelper.playerUseItemSound(monsterBkt, "GUN", type.equals("骷髅特警"));
                                            for (int i = 0; i < shootAmount; i ++) {
                                                Vector projVel = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), spd);
                                                if (type.equals("骷髅特警")) {
                                                    projVel.add(new Vector(0.2 * (Math.random() - 0.5),
                                                            0.2 * (Math.random() - 0.5),
                                                            0.2 * (Math.random() - 0.5)));
                                                    projVel.normalize().multiply(2);
                                                }
                                                EntityHelper.ProjectileShootInfo projInfo = new EntityHelper.ProjectileShootInfo(
                                                        monsterBkt, projVel, EntityHelper.getAttrMap(monsterBkt), "火枪子弹");
                                                EntityHelper.spawnProjectile(projInfo);
                                            }
                                            break;
                                        }
                                    }
                                    break;
                            }
                        }
                    }
                    break;
                }
                case "流星火怪": {
                    if (monster.getHealth() > 0) {
                        Vector velocity = (Vector) extraVariables.getOrDefault("v", new Vector());
                        // initialize dash direction
                        if (indexAI == 0) {
                            velocity = MathHelper.getDirection(
                                    monsterBkt.getEyeLocation(), target.getEyeLocation(), 2.5);
                        }
                        // maintain velocity & ready for next dash
                        else {
                            if (indexAI >= 20) {
                                if (monsterBkt.getLocation().distanceSquared(target.getLocation()) > 25 * 25) {
                                    indexAI = (int) Math.min(indexAI * -0.5, -30);
                                    velocity = new Vector(0, 0.75, 0);
                                }
                            }
                        }
                        extraVariables.put("v", velocity);
                        monsterBkt.setVelocity(velocity);
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.SOLAR, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "千足蜈蚣": {
                    ArrayList<org.bukkit.entity.LivingEntity> segments = (ArrayList<org.bukkit.entity.LivingEntity>) extraVariables.get("attachments");
                    for (org.bukkit.entity.Entity entity : segments) {
                        ((LivingEntity) entity).setHealth(monsterBkt.getHealth());
                    }

                    if (monster.getHealth() > 0) {
                        // strike
                        Location targetLoc;
                        double accMagnitude, speed;
                        if (canCrawltipedeCharge(target) ) {
                            targetLoc = target.getLocation().add(0, 1, 0);
                            accMagnitude = 0.5;
                            speed = 2.25;
                        }
                        // roam above target
                        else {
                            Vector offsetDir = MathHelper.vectorFromYawPitch_quick(indexAI * 2.75, 0);
                            offsetDir.multiply(25);
                            offsetDir.setY(16);
                            targetLoc = target.getLocation().add(offsetDir);
                            accMagnitude = 1.5;
                            speed = 5;
                        }
                        // normalize current velocity first
                        Vector vec = monsterBkt.getVelocity();
                        double vecLen = vec.length();
                        if (vecLen < 1e-5) {
                            vec = MathHelper.randomVector();
                            vecLen = 1;
                        }
                        vec.multiply(1 / vecLen);
                        // add acceleration
                        Vector acc = MathHelper.getDirection(monsterBkt.getLocation(), targetLoc, accMagnitude);
                        vec.add(acc);
                        // normalize new velocity
                        vecLen = vec.length();
                        if (vecLen < 1e-5) {
                            vec = MathHelper.randomVector();
                            vecLen = 1;
                        }
                        if (vecLen > speed)
                            vec.multiply(speed / vecLen);
                        monsterBkt.setVelocity(vec);
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.SOLAR, monsterBkt.getEyeLocation());
                    }
                    EntityHelper.handleSegmentsFollow(segments, (EntityHelper.WormSegmentMovementOptions) extraVariables.get("wormMoveOption"));
                    break;
                }
                case "火月怪":
                case "火滚怪": {
                    if (monster.getHealth() > 0) {
                        if (indexAI == 0) {
                            if (type.equals("火月怪") ) {
                                monsterBkt.removeScoreboardTag("reflectProjectile");
                            }
                        }
                        else if (indexAI == 120) {
                            if (monsterBkt.isOnGround())
                                monsterBkt.setVelocity(new Vector(0, 1, 0));
                            else
                                indexAI --;
                        }
                        else if (indexAI >= 135) {
                            monsterBkt.setFallDistance(0);
                            // start dashes
                            Vector acceleration = (Vector) extraVariables.getOrDefault("acc", new Vector());
                            if (indexAI == 135) {
                                // init attributes etc.
                                if (type.equals("火月怪") ) {
                                    monsterBkt.addScoreboardTag("notDamaged");
                                    monsterBkt.addScoreboardTag("reflectProjectile");
                                }
                                // init speed
                                Location tempLoc = target.getLocation();
                                tempLoc.setY(monsterBkt.getLocation().getY());
                                acceleration = MathHelper.getDirection(monsterBkt.getLocation(), tempLoc, 0.25);
                                acceleration.setY(0.1);
                            }
                            // mechanism during dash
                            if (type.equals("火月怪")) {
                                if (indexAI >= 200 ||
                                        monsterBkt.isOnGround() ||
                                        !monsterBkt.getScoreboardTags().contains("notDamaged") )
                                    indexAI = -1;
                                acceleration.setY(acceleration.getY() - 0.015);
                            }
                            else {
                                if (indexAI >= 250)
                                    indexAI = -1;
                                else {
                                    if (monsterBkt.isOnGround()) {
                                        Location tempLoc = target.getLocation();
                                        tempLoc.setY(monsterBkt.getLocation().getY());
                                        acceleration = MathHelper.getDirection(monsterBkt.getLocation(), tempLoc, 0.25);
                                        acceleration.setY(0.2);
                                    }
                                }
                                acceleration.setY(acceleration.getY() - 0.015);
                            }
                            // update acceleration
                            extraVariables.put("acc", acceleration);
                            Vector velocity = monsterBkt.getVelocity().add(acceleration);
                            if (velocity.lengthSquared() > 4)
                                velocity.normalize().multiply(2);
                            monsterBkt.setVelocity( velocity );
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.SOLAR, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "星璇怪":
                case "漩泥怪": {
                    if (monster.getHealth() > 0) {
                        // adjust velocity
                        if (type.equals("漩泥怪")) {
                            monsterBkt.setFallDistance(0);
                            Vector velocity = monsterBkt.getVelocity();
                            // horizontal acceleration when far away
                            Location tempLoc = target.getLocation();
                            tempLoc.setY(monsterBkt.getLocation().getY());
                            if (tempLoc.distanceSquared(monsterBkt.getLocation()) > 400) {
                                Vector horAcc = MathHelper.getDirection(monsterBkt.getLocation(), tempLoc, 0.05);
                                velocity.add(horAcc);
                            }
                            // vertical acceleration
                            boolean aboveOrBelow = monsterBkt.getLocation().getY() > target.getLocation().getY();
                            velocity.setY(velocity.getY() + 0.075 * (aboveOrBelow ? -1 : 1) );
                            monsterBkt.setVelocity(velocity);
                        }
                        // shoot projectiles
                        if (indexAI % 125 == 100) {
                            EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                    monsterBkt, new Vector(), EntityHelper.getAttrMap(monsterBkt), "星旋激光");
                            EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                    .setProjectileSpeed(2)
                                    .setRandomOffsetRadius(2);
                            for (int i = 0; i < 6; i ++) {
                                Location targetLoc = EntityHelper.helperAimEntity(monsterBkt, target, aimHelper);
                                shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, targetLoc, 2);
                                EntityHelper.spawnProjectile(shootInfo);
                            }
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.VORTEX, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "异星幼虫": {
                    if (monster.getHealth() > 0) {
                        // evolve after living for a period of time
                        if (indexAI > 300) {
                            monsterBkt.remove();
                            terraria.util.MonsterHelper.spawnMob("异星黄蜂", monsterBkt.getLocation(), target);
                        }
                    }
                    break;
                }
                case "异星黄蜂": {
                    if (monster.getHealth() > 0) {
                        // evolve after living for a period of time
                        if (indexAI > 300) {
                            monsterBkt.remove();
                            terraria.util.MonsterHelper.spawnMob("异星蜂王", monsterBkt.getLocation(), target);
                            break;
                        }
                        // dashes every 20 ticks
                        if (indexAI % 20 == 0) {
                            monsterBkt.setVelocity(MathHelper.getDirection(
                                    monsterBkt.getLocation(), target.getLocation().add(0, 1, 0), 0.6));
                        }
                        Vector velocity = monsterBkt.getVelocity();
                        if (velocity.lengthSquared() > 1e-5) {
                            velocity.normalize().multiply(0.6);
                            monsterBkt.setVelocity(velocity);
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.VORTEX, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "异星蜂王": {
                    if (monster.getHealth() > 0) {
                        // fly around the player
                        {
                            Vector offsetDir = MathHelper.vectorFromYawPitch_quick(indexAI, 0);
                            offsetDir.multiply(16);
                            offsetDir.setY(MathHelper.xsin_degree(indexAI + 90) * 4 + 12);
                            Location targetLoc = target.getLocation().add(offsetDir);
                            monsterBkt.setVelocity(MathHelper.getDirection(
                                    monsterBkt.getLocation(), targetLoc, 0.65, true));
                        }
                        // shoot projectile
                        if (indexAI % 30 == 0) {
                            EntityHelper.spawnProjectile(
                                    monsterBkt,
                                    MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 1.5),
                                    EntityHelper.getAttrMap(monsterBkt),
                                    "异星黏液");
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        for (int i = 0; i < 2; i++) {
                            org.bukkit.entity.Entity spawnedMob = terraria.util.MonsterHelper.spawnMob(
                                    "异星幼虫", monsterBkt.getLocation(), target);
                            if (spawnedMob != null)
                                spawnedMob.setVelocity(MathHelper.randomVector());
                        }
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.VORTEX, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "吮脑怪": {
                    if (monster.getHealth() > 0) {
                        // move when not sucked onto a player
                        Vector velocity = monsterBkt.getVelocity();
                        Vector acc = MathHelper.getDirection(
                                monsterBkt.getEyeLocation(), target.getEyeLocation(), 0.1);
                        velocity.add(acc);
                        if (velocity.lengthSquared() > 1e-5) {
                            velocity.normalize().multiply(1);
                        }
                        monsterBkt.setVelocity(velocity);
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.NEBULA, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "预言怪": {
                    if (monster.getHealth() > 0) {
                        // stop for a brief while before shooting
                        if (indexAI >= 75) {
                            monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0d);
                            // shoot projectile
                            if (indexAI >= 100) {
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, new Vector(), EntityHelper.getAttrMap(monsterBkt), "星云针弹");
                                EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                        .setProjectileSpeed(2)
                                        .setRandomOffsetRadius(2);
                                for (int i = 0; i < 10; i ++) {
                                    Location targetLoc = EntityHelper.helperAimEntity(monsterBkt, target, aimHelper);
                                    shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, targetLoc, 2);
                                    EntityHelper.spawnProjectile(shootInfo);
                                }
                                indexAI = -1;
                            }
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.NEBULA, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "星云浮怪": {
                    if (monster.getHealth() > 0) {
                        // handle acceleration
                        {
                            Vector acc = (Vector) extraVariables.getOrDefault("acc", new Vector());
                            if (indexAI < 75) {
                                acc = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 0.075);
                            } else {
                                acc.setY(acc.getY() + 0.005);
                            }
                            extraVariables.put("acc", acc);
                            Vector velocity = monsterBkt.getVelocity();
                            velocity.add(acc);
                            if (velocity.lengthSquared() > 1e-5)
                                velocity.normalize().multiply(0.75);
                            monsterBkt.setVelocity(velocity);
                        }
                        // laser and teleport
                        switch (indexAI) {
                            // shoot projectile
                            case 85:
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt,
                                        MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 2.5),
                                        EntityHelper.getAttrMap(monsterBkt), "星云针弹");
                                EntityHelper.spawnProjectile(shootInfo);
                                break;
                            // teleport
                            case 100:
                                Vector offset = MathHelper.randomVector();
                                offset.multiply(15);
                                monsterBkt.teleport(target.getEyeLocation().add(offset));
                                indexAI = -1;
                                break;
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.NEBULA, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "进化兽": {
                    if (monster.getHealth() > 0) {
                        // walks slowly before shooting
                        if (indexAI >= 150) {
                            monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.05d);
                            // fire a projectile and continue walking
                            if (indexAI >= 200) {
                                // shoot projectile
                                terraria.util.MonsterHelper.spawnMob("星云球", monsterBkt.getEyeLocation(), target);
                                // recover walking speed
                                monsterBkt.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35d);
                                indexAI = -1;
                            }
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.NEBULA, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "星云球": {
                    if (monster.getHealth() > 0) {
                        // lasts 12.5 seconds
                        if (indexAI > 250)
                            monsterBkt.remove();
                        else {
                            Vector velocity = monsterBkt.getVelocity();
                            Vector acc = MathHelper.getDirection(
                                    monsterBkt.getEyeLocation(), target.getEyeLocation(), 0.05, true);
                            velocity.add(acc);
                            if (velocity.lengthSquared() > 1e-5) {
                                velocity.normalize().multiply(1);
                            }
                            monsterBkt.setVelocity(velocity);
                        }
                    }
                    break;
                }
                case "星尘细胞": {
                    if (monster.getHealth() > 0) {
                        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(monsterBkt);
                        Slime slimeMonsterBkt = (Slime) monsterBkt;
                        // baby
                        if ( slimeMonsterBkt.getSize() < 4) {
                            attrMap.put("damage", 420d);
                            attrMap.put("defence", 0d);
                            // baby grow into full size after 10 seconds
                            if (indexAI >= 200) {
                                slimeMonsterBkt.setSize(5);
                            }
                            // floats upwards
                            Vector velocity = monsterBkt.getVelocity();
                            velocity.multiply(0.9);
                            velocity.setY(velocity.getY() + 0.045);
                            monsterBkt.setVelocity(velocity);
                        }
                        // full size
                        else {
                            attrMap.put("damage", 720d);
                            attrMap.put("defence", 100d);
                            // dash into target
                            Vector velocity = (Vector) extraVariables.getOrDefault("v", new Vector(0, 1, 0));
                            if (monsterBkt.getLocation().distanceSquared(target.getLocation()) > 100) {
                                velocity = MathHelper.getDirection(monsterBkt.getLocation(), target.getLocation(), 1.5);
                            }
                            monsterBkt.setVelocity(velocity);
                            extraVariables.put("v", velocity);
                        }
                    }
                    // if the monster is killed just now
                    else if ( (!extraVariables.containsKey("SPN")) && ((Slime) monsterBkt).getSize() > 4 ) {
                        for (int i = (int) (Math.random() * 2); i < 4; i++) {
                            org.bukkit.entity.Entity spawnedMob = terraria.util.MonsterHelper.spawnMob(
                                    "星尘细胞", monsterBkt.getLocation(), target);
                            if (spawnedMob != null) {
                                ((Slime) spawnedMob).setSize(3);
                                spawnedMob.setVelocity(MathHelper.randomVector());
                            }
                        }
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.STARDUST, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "银河织妖": {
                    // update segment health
                    ArrayList<org.bukkit.entity.LivingEntity> segments = (ArrayList<org.bukkit.entity.LivingEntity>) extraVariables.get("attachments");
                    for (LivingEntity entity : segments) {
                        entity.setHealth(monsterBkt.getHealth());
                    }
                    if (monster.getHealth() > 0) {
                        // update location of segments
                        {
                            Location[] locations = (Location[]) extraVariables.get("locs");
                            int indexLocationInitial = indexAI / 3, indexLocation = indexLocationInitial;
                            for (int i = 1; i < segments.size(); i ++) {
                                LivingEntity entity = segments.get(i);
                                entity.teleport(locations[indexLocation % locations.length]);
                                indexLocation ++;
                            }
                            // record new location
                            if (indexAI % 3 == 0)
                                locations[ (indexLocationInitial + 1) % locations.length] = monsterBkt.getLocation();
                        }
                        // acceleration of head
                        Vector velocity = (Vector) extraVariables.getOrDefault("v", new Vector());
                        Vector acc = (Vector) extraVariables.getOrDefault("a", new Vector());
                        int ticksFarAway = (int) extraVariables.getOrDefault("tfa", 1919);
                        if (ticksFarAway >= 40) {
                            acc = target.getLocation().subtract(monsterBkt.getLocation()).toVector();
                            double accLen = acc.length();
                            if (accLen < 6) {
                                ticksFarAway = 0;
                            }
                            if (accLen < 1e-5) {
                                acc = MathHelper.randomVector();
                                accLen = 1;
                            }
                            acc.multiply(0.2 / accLen);
                        }
                        else {
                            ticksFarAway ++;
                        }
                        // update velocity
                        velocity.add(acc);
                        if (velocity.lengthSquared() > 1) {
                            velocity.normalize();
                        }
                        monsterBkt.setVelocity(velocity);
                        // save possibly changed variables
                        extraVariables.put("tfa", ticksFarAway);
                        extraVariables.put("v", velocity);
                        extraVariables.put("a", acc);
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.STARDUST, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "流体入侵怪": {
                    int amountProjectile = (int) extraVariables.getOrDefault("P", 0);
                    if (monster.getHealth() > 0) {
                        // velocity update
                        {
                            Vector offsetDir = MathHelper.vectorFromYawPitch_quick(indexAI * 2, 0);
                            offsetDir.multiply(12);
                            offsetDir.setY(8);
                            Location targetLoc = target.getEyeLocation().add(offsetDir);
                            Vector acc = MathHelper.getDirection(monsterBkt.getLocation(), targetLoc, 0.075);
                            Vector velocity = monsterBkt.getVelocity();
                            velocity.add(acc);
                            if (velocity.lengthSquared() < 1e-5)
                                velocity = MathHelper.randomVector();
                            velocity.normalize().multiply(0.5);
                            monsterBkt.setVelocity(velocity);
                        }
                        // display projectiles
                        for (int i = 0; i < amountProjectile; i ++) {
                            Vector offsetDir = MathHelper.vectorFromYawPitch_quick(
                                    indexAI + i * 360d / amountProjectile, 0);
                            offsetDir.multiply(2.5);
                            offsetDir.setY(1.25);
                            GenericHelper.handleParticleLine(new Vector(0, 1, 0),
                                    monsterBkt.getLocation().add(offsetDir),
                                    new GenericHelper.ParticleLineOptions()
                                            .setLength(0.99)
                                            .setWidth(0.5)
                                            .setParticleColor("100|150|255")
                                            .setTicksLinger(1));
                        }
                        // projectiles
                        {
                            if (indexAI % 30 == 0)
                                amountProjectile ++;
                            if (amountProjectile > 5) {
                                amountProjectile--;
                                EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                        monsterBkt, new Vector(), EntityHelper.getAttrMap(monsterBkt), "流体入侵怪");
                                EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                        .setProjectileSpeed(2);
                                Location targetLoc = EntityHelper.helperAimEntity(monsterBkt, target, aimHelper);
                                shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, targetLoc, 2);
                                EntityHelper.spawnProjectile(shootInfo);
                            }
                        }
                    }
                    else {
                        // if the monster is killed just now
                        if (!extraVariables.containsKey("SPN")) {
                            extraVariables.put("SPN", true);
                            CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.STARDUST, monsterBkt.getEyeLocation());
                        }
                        // shoot remaining projectiles on death
                        if (amountProjectile > 0) {
                            EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                    monsterBkt, new Vector(), EntityHelper.getAttrMap(monsterBkt), "流体入侵怪");
                            EntityHelper.AimHelperOptions aimHelper = new EntityHelper.AimHelperOptions()
                                    .setProjectileSpeed(2)
                                    .setRandomOffsetRadius(3);
                            for (int i = 0; i < amountProjectile; i ++) {
                                Location targetLoc = EntityHelper.helperAimEntity(monsterBkt, target, aimHelper);
                                shootInfo.velocity = MathHelper.getDirection(shootInfo.shootLoc, targetLoc, 2);
                                EntityHelper.spawnProjectile(shootInfo);
                            }
                            amountProjectile = 0;
                        }
                    }
                    extraVariables.put("P", amountProjectile);
                    break;
                }
                case "观星怪": {
                    if (monster.getHealth() > 0) {
                        switch (indexAI % 120) {
                            case 105:
                                monsterBkt.setGlowing(true);
                                break;
                            case 116:
                                Location targetEyeLoc = target.getEyeLocation();
                                extraVariables.put("L", targetEyeLoc);
                                break;
                            case 119:
                                Location aimLoc = (Location) extraVariables.get("L");
                                Vector dir = aimLoc.subtract(monsterBkt.getEyeLocation()).toVector();
                                if (dir.lengthSquared() < 1e-5)
                                    dir = MathHelper.randomVector();
                                GenericHelper.handleStrikeLine(monsterBkt, monsterBkt.getEyeLocation(),
                                        MathHelper.getVectorYaw(dir), MathHelper.getVectorPitch(dir), 48, 1,
                                        "", "100|150|255", new ArrayList<>(), EntityHelper.getAttrMap(monsterBkt),
                                        new GenericHelper.StrikeLineOptions());
                                monsterBkt.setGlowing(false);
                                break;
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.STARDUST, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "闪耀炮手": {
                    if (monster.getHealth() > 0) {
                        if (indexAI % 60 == 0) {
                            org.bukkit.entity.Entity spawnedMob = terraria.util.MonsterHelper.spawnMob(
                                    "闪耀怪", monsterBkt.getLocation(), target);
                            if (spawnedMob != null) {
                                spawnedMob.setVelocity(new Vector(0, 1, 0));
                            }
                        }
                    }
                    // if the monster is killed just now
                    else if (!extraVariables.containsKey("SPN")) {
                        extraVariables.put("SPN", true);
                        CelestialPillar.handlePillarMonsterDeath(CelestialPillar.PillarTypes.STARDUST, monsterBkt.getEyeLocation());
                    }
                    break;
                }
                case "闪耀怪": {
                    if (monster.getHealth() > 0) {
                        // explode into a barrage of projectiles on timeout or when it comes close to target
                        if (indexAI >= 150 ||
                                monsterBkt.getLocation().add(0, 4, 0).distanceSquared(target.getLocation()) < 16) {
                            EntityHelper.ProjectileShootInfo shootInfo = new EntityHelper.ProjectileShootInfo(
                                    monsterBkt, new Vector(), EntityHelper.getAttrMap(monsterBkt), "闪耀怪");
                            for (int i = 0; i < 20; i ++) {
                                shootInfo.velocity = new Vector(
                                        Math.random() * 1 - 0.5,
                                        Math.random() * 0.75 + 0.5,
                                        Math.random() * 1 - 0.5);
                                EntityHelper.spawnProjectile(shootInfo);
                            }
                            monsterBkt.remove();
                        }
                    }
                    break;
                }
                case "": {
                    if (monster.getHealth() > 0) {

                    }
                    break;
                }
            }
            // look at player
            if (lookAtPlayer) {
                Vector facingDir = MathHelper.getDirection(monsterBkt.getEyeLocation(), target.getEyeLocation(), 1);
                monster.yaw = (float) MathHelper.getVectorYaw(facingDir);
                monster.pitch = (float) MathHelper.getVectorPitch(facingDir);
            }
            // drop
            if (monsterBkt.getHealth() <= 0d && ! monsterBkt.getScoreboardTags().contains("dropHandled")) {
                handleMonsterDrop(monsterBkt);
                monsterBkt.addScoreboardTag("dropHandled");
            }
        }
        if (hasContactDamage && monster.getHealth() > 0) {
            AxisAlignedBB bb = monster.getBoundingBox();
            double xWidth = (bb.d - bb.a) / 2, zWidth = (bb.f - bb.c) / 2, height = (bb.e - bb.b) / 2;
            Vector initLoc = new Vector(bb.a + xWidth, bb.b + height, bb.c + zWidth);
            Set<HitEntityInfo> toDamage = HitEntityInfo.getEntitiesHit(monsterBkt.getWorld(),
                    initLoc, initLoc.clone().add(monsterBkt.getVelocity()),
                    xWidth, height, zWidth,
                    (Entity entity) -> EntityHelper.checkCanDamage(monsterBkt, entity.getBukkitEntity(), false));
            double damage = EntityHelper.getAttrMap(monsterBkt).getOrDefault("damage", 1d);
            for (HitEntityInfo hitEntityInfo : toDamage) {
                EntityHelper.handleDamage(monsterBkt, hitEntityInfo.getHitEntity().getBukkitEntity(),
                        damage, EntityHelper.DamageReason.DIRECT_DAMAGE);
            }
        }
        return indexAI + 1;
    }
}
