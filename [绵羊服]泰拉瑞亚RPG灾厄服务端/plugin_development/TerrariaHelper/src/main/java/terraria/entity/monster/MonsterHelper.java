package terraria.entity.monster;


import net.minecraft.server.v1_12_R1.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;
import terraria.gameplay.Event;
import terraria.util.EntityHelper;
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
        boolean minRequirementMet = progressRequirement[0].length() > 0 && PlayerHelper.hasDefeated(target, progressRequirement[0]);
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
            while (availableVariants.contains(situationPrefix + index)) {
                variantCandidates.add(situationPrefix + index);
                index ++;
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
    public static void initMonsterInfo(Player target, String monsterProgressRequiredMin, Entity monster, String type, String variant) {
        // determine if the player's game progress is appropriate
        ConfigurationSection typeConfigSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection("mobInfo." + type);
        ConfigurationSection variantConfigSection = typeConfigSection.getConfigurationSection("variants." + variant);
        ConfigurationSection attributeConfigSection = variantConfigSection.getConfigurationSection("attributes");
        org.bukkit.entity.Entity bukkitMonster = monster.getBukkitEntity();
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
        EntityHelper.setMetadata(bukkitMonster, "attrMap", attrMap);
        LivingEntity bukkitMonsterLivingEntity = (LivingEntity) bukkitMonster;
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        bukkitMonsterLivingEntity.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(444);
        bukkitMonsterLivingEntity.setHealth(health);
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
            tweakPlayerMonsterSpawnedAmount(target, false);
            // find possible target
            Player newTarget = null;
            double newTargetDistSqr = 4096;
            for (org.bukkit.entity.Entity checkEntity : monsterBkt.getNearbyEntities(64, 64, 64)) {
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
                tweakPlayerMonsterSpawnedAmount(newTarget, true);
                return newTarget;
            } else {
                monster.die();
            }
        }
        return target;
    }
}
