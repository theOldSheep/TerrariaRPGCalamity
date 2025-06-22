package terraria.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import terraria.TerrariaHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class AttributeHelper {
    // helper functions for tweaking attribute
    public static void tweakAttribute(Entity entity, String key, String value, boolean addOrRemove) {
        if (key.equals(MetadataHelper.MetadataName.DAMAGE_TYPE.toString())) {
            if (addOrRemove) MetadataHelper.setMetadata(entity, MetadataHelper.MetadataName.DAMAGE_TYPE,
                    DamageHelper.DAMAGE_TYPE_INTERNAL_NAME_MAPPING.getOrDefault(value, DamageHelper.DamageType.MELEE));
            return;
        }
        try {
            if (key.startsWith(MetadataHelper.MetadataName.BUFF_INFLICT.toString())) {
                if (entity instanceof Player) {
                    HashMap<String, ArrayList<String>> effectInflict = PlayerHelper.getPlayerEffectInflict(entity);
                    if (!effectInflict.containsKey(key)) return;
                    if (addOrRemove)
                        effectInflict.get(key).add(value);
                    else
                        effectInflict.get(key).remove(value);
                }
                return;
            }
            else if (key.equals(MetadataHelper.MetadataName.BUFF_IMMUNE.toString())) {
                Map<String, Integer> buffImmune = (Map<String, Integer>) MetadataHelper.getMetadata(entity, MetadataHelper.MetadataName.BUFF_IMMUNE).value();
                int layers = buffImmune.getOrDefault(value, 0);
                if (addOrRemove)
                    layers ++;
                else
                    layers --;
                if (layers > 0)
                    buffImmune.put(value, layers);
                else
                    buffImmune.remove(value);
                return;
            }
            // tweak double value in attribute map
            HashMap<String, Double> attrMap = getAttrMap(entity);
            tweakAttribute(attrMap, key, value, addOrRemove);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Generic Helper] error when parsing value as a number (" + value + ") in tweakAttribute ", e);
        }
    }

    public static void tweakAllAttributes(Entity entity, ConfigurationSection attributes, boolean addOrRemove) {
        if (attributes != null) {
            Set<String> attributesTweaked = attributes.getKeys(false);
            for (String attr : attributesTweaked) {
                tweakAttribute(entity, attr,
                        attributes.getString(attr), addOrRemove);
            }
        }
    }

    public static void tweakAttribute(HashMap<String, Double> attrMap, String key, String value, boolean addOrRemove) {
        if (key.equals("damageType")) {
            return;
        }
        try {
            if (key.startsWith("buffInflict")) {
                return;
            }
            if (key.equals("buffImmune")) {
                return;
            }
            // tweak double value in attribute map
            if (!attrMap.containsKey(key)) return;
            double value_number = Double.parseDouble(value);
            if ("useTime".equals(key)) {
                value_number /= 3;
            }
            // tweak attribute
            switch (key) {
                // damage reduction handled in a special way to prevent unreasonable accumulation of DR
                case "damageTakenMulti":
                case "damageContactTakenMulti": {
                    // calamity change: DR scale by 1 - 1/(1+DR)
                    // damage taken multiplier = 1/(1+DR)
                    if (addOrRemove)
                        attrMap.put(key, attrMap.getOrDefault(key, 1d) / (1 - value_number));
                    else
                        attrMap.put(key, attrMap.getOrDefault(key, 1d) * (1 - value_number));
                    break;
                }
                // multiplying
                case "ammoConsumptionRate":
                case "arrowConsumptionRate":
                case "manaUseMulti":
                case "mobSpawnRateMulti":
                case "speedMulti": {
                    value_number = 1 + value_number;
                    if (addOrRemove)
                        attrMap.put(key, (attrMap.getOrDefault(key, 1d)) * value_number);
                    else
                        attrMap.put(key, (attrMap.getOrDefault(key, 1d)) / value_number);
                    break;
                }
                // scalar adding
                default:
                    if (addOrRemove)
                        attrMap.put(key, (attrMap.getOrDefault(key, 1d)) + value_number);
                    else
                        attrMap.put(key, (attrMap.getOrDefault(key, 1d)) - value_number);
            }
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Generic Helper] error when parsing value as a number (" + value + ") in tweakAttribute ", e);
        }
    }

    public static void tweakAllAttributes(HashMap<String, Double> attrMap, ConfigurationSection attributes, boolean addOrRemove) {
        if (attributes != null) {
            Set<String> attributesTweaked = attributes.getKeys(false);
            for (String attr : attributesTweaked) {
                tweakAttribute(attrMap, attr,
                        attributes.getString(attr), addOrRemove);
            }
        }
    }

    // usually used in updating player attribute
    public static void tweakAttribute(Entity entity, HashMap<String, Double> attrMap, String key, String value, boolean addOrRemove) {
        if (key.equals(MetadataHelper.MetadataName.DAMAGE_TYPE.toString())) {
            if (addOrRemove) MetadataHelper.setMetadata(entity, MetadataHelper.MetadataName.DAMAGE_TYPE,
                    DamageHelper.DAMAGE_TYPE_INTERNAL_NAME_MAPPING.getOrDefault(value, DamageHelper.DamageType.MELEE));
            return;
        }
        try {
            if (key.startsWith("buffInflict")) {
                if (entity instanceof Player) {
                    HashMap<String, ArrayList<String>> effectInflict = PlayerHelper.getPlayerEffectInflict(entity);
                    if (!effectInflict.containsKey(key)) return;
                    if (addOrRemove)
                        effectInflict.get(key).add(value);
                    else
                        effectInflict.get(key).remove(value);
                }
                return;
            }
            if (key.equals("buffImmune")) {
                Map<String, Integer> buffImmune = (Map<String, Integer>) MetadataHelper.getMetadata(entity, MetadataHelper.MetadataName.BUFF_IMMUNE).value();
                for (String immune : value.split("\\|")) {
                    int layers = buffImmune.getOrDefault(immune, 0);
                    if (addOrRemove)
                        layers++;
                    else
                        layers--;
                    if (layers > 0)
                        buffImmune.put(immune, layers);
                    else
                        buffImmune.remove(immune);
                }
                return;
            }
            // tweak double value in attribute map
            tweakAttribute(attrMap, key, value, addOrRemove);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Generic Helper] error when parsing value as a number (" + value + ") in tweakAttribute ", e);
        }
    }

    // usually used in updating player attribute
    public static void tweakAllAttributes(Entity entity, HashMap<String, Double> attrMap, ConfigurationSection attributes, boolean addOrRemove) {
        if (attributes != null) {
            Set<String> attributesTweaked = attributes.getKeys(false);
            for (String attr : attributesTweaked) {
                tweakAttribute(entity, attrMap, attr,
                        attributes.getString(attr), addOrRemove);
            }
        }
    }

    public static void tweakAttribute(Entity entity, HashMap<String, Double> attrMap, ItemStack item, boolean addOrRemove) {
        String[] itemInfo = ItemHelper.splitItemName(item);
        // attributes from the item itself
        String attributesPath = itemInfo[1] + ".attributes";
        ConfigurationSection allAttributes = TerrariaHelper.itemConfig.getConfigurationSection(attributesPath);
        tweakAllAttributes(entity, attrMap, allAttributes, addOrRemove);
        // attributes from the item's prefix
        String attributesPathPrefix = "prefixInfo." + itemInfo[0] +  ".attributes";
        ConfigurationSection allAttributesPrefix = TerrariaHelper.prefixConfig.getConfigurationSection(attributesPathPrefix);
        tweakAllAttributes(entity, attrMap, allAttributesPrefix, addOrRemove);
        // attributes from the item's switchable properties
        String attributesPathSwitchable = itemInfo[1] +
                ( entity.getScoreboardTags().contains(PlayerHelper.TAG_SWITCHED_SWITCHABLE_ACCESSORY) ? ".attributesFormII" : ".attributesFormI" );
        ConfigurationSection allAttributesSwitchable = TerrariaHelper.itemConfig.getConfigurationSection(attributesPathSwitchable);
        tweakAllAttributes(entity, attrMap, allAttributesSwitchable, addOrRemove);
    }

    public static void tweakAttribute(HashMap<String, Double> attrMap, ItemStack item, boolean addOrRemove) {
        String[] itemInfo = ItemHelper.splitItemName(item);
        // attributes from the item itself
        String attributesPath = itemInfo[1] + ".attributes";
        ConfigurationSection allAttributes = TerrariaHelper.itemConfig.getConfigurationSection(attributesPath);
        tweakAllAttributes(attrMap, allAttributes, addOrRemove);
        // attributes from the item's prefix
        String attributesPathPrefix = "prefixInfo." + itemInfo[0] +  ".attributes";
        ConfigurationSection allAttributesPrefix = TerrariaHelper.prefixConfig.getConfigurationSection(attributesPathPrefix);
        tweakAllAttributes(attrMap, allAttributesPrefix, addOrRemove);
    }

    public static void tweakAttribute(Entity entity, ItemStack item, boolean addOrRemove) {
        tweakAttribute(entity, getAttrMap(entity), item, addOrRemove);
    }

    public static HashMap<String, Double> getAttrMap(Metadatable entity) {
        try {
            MetadataValue mdv = MetadataHelper.getMetadata(entity, MetadataHelper.MetadataName.ATTRIBUTE_MAP);
            return mdv == null ? new HashMap<>(0) : (HashMap<String, Double>) mdv.value();
        } catch (Exception e) {
            return new HashMap<>(0);
        }
    }
}
