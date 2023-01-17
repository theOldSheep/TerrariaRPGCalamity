package terraria.entity.monster;


import net.minecraft.server.v1_12_R1.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;
import terraria.util.EntityHelper;
import terraria.util.PlayerHelper;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MonsterHelper {
    // initializes monster size, attribute, disguise etc.
    private static class MonsterStatsMulti {
        public double
                healthMulti = 1d,
                defenceMulti = 1d,
                damageMulti = 1d;
    }
    public static Map.Entry<String, String> getMonsterVariantAndProgress(Player target, String monsterType) {
        String variant = "", progressRequired = "";
        // TODO
        return new AbstractMap.SimpleImmutableEntry<>(variant, progressRequired);
    }
    private static MonsterStatsMulti getMonsterStatsBonus(Player target, String monsterProgressRequired) {
        MonsterStatsMulti result = new MonsterStatsMulti();
        PlayerHelper.GameProgress playerProgress = PlayerHelper.getGameProgress(target);
        PlayerHelper.GameProgress monsterProgress = PlayerHelper.getGameProgress(monsterProgressRequired);
        switch (monsterProgress) {
            case PRE_WALL_OF_FLESH:
                switch (playerProgress) {
                    case PRE_PLANTERA:
                        result.healthMulti  = 5d;
                        result.defenceMulti = 5d;
                        result.damageMulti  = 5d;
                        break;
                    case PRE_MOON_LORD:
                        break;
                    case PRE_PROFANED_GODDESS:
                        break;
                    case POST_PROFANED_GODDESS:
                        break;
                }
                break;
            case PRE_PLANTERA:
                switch (playerProgress) {
                    case PRE_MOON_LORD:
                        break;
                    case PRE_PROFANED_GODDESS:
                        break;
                    case POST_PROFANED_GODDESS:
                        break;
                }
                break;
            case PRE_MOON_LORD:
                switch (playerProgress) {
                    case PRE_PROFANED_GODDESS:
                        break;
                    case POST_PROFANED_GODDESS:
                        break;
                }
                break;
            case PRE_PROFANED_GODDESS:
                if (playerProgress == PlayerHelper.GameProgress.POST_PROFANED_GODDESS) {

                }
                break;
        }
        return result;
    }
    public static void initMonsterInfo(Player target, String monsterProgressRequired, Entity monster, String type, String variant) {
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
        MonsterStatsMulti statsBoost = getMonsterStatsBonus(target, monsterProgressRequired);
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

    }
    // setup monster target

}
