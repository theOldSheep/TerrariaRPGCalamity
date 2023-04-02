package terraria.util;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.*;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.TerrariaArrowProjectile;
import terraria.entity.projectile.TerrariaPotionProjectile;
import terraria.gameplay.Event;

import java.util.*;
import java.util.logging.Level;

public class EntityHelper {
    // constants
    static HashMap<String, Set<String>> buffInferior, buffSuperior;
    static {
        buffSuperior = new HashMap<>(50);
        buffInferior = new HashMap<>(50);
        ConfigurationSection conflictSection = TerrariaHelper.buffConfig.getConfigurationSection("buffConflicts");
        Set<String> rules = conflictSection.getKeys(false);
        for (String rule : rules) {
            ConfigurationSection ruleSection = conflictSection.getConfigurationSection(rule);
            Set<String> specificBuffs = ruleSection.getKeys(false);
            for (String buff1 : specificBuffs) {
                int priority1 = ruleSection.getInt(buff1, 0);
                for (String buff2 : specificBuffs) {
                    if (buff1.equals(buff2)) continue;
                    int priority2 = ruleSection.getInt(buff2, 0);
                    // they have same priority, so they can both override each other
                    if (priority1 == priority2) {
                        if (priority1 != 0) {
                            if (!buffInferior.containsKey(buff1)) buffInferior.put(buff1, new HashSet<>(25));
                            buffInferior.get(buff1).add(buff2);
                            if (!buffInferior.containsKey(buff2)) buffInferior.put(buff2, new HashSet<>(25));
                            buffInferior.get(buff2).add(buff1);
                        }
                    } else {
                        if (priority1 > priority2) {
                            // buff2 can not be applied when buff1 is present, buff1 can override buff2
                            if (!buffInferior.containsKey(buff1)) buffInferior.put(buff1, new HashSet<>(25));
                            buffInferior.get(buff1).add(buff2);
                            if (!buffSuperior.containsKey(buff2)) buffSuperior.put(buff2, new HashSet<>(25));
                            buffSuperior.get(buff2).add(buff1);
                        } else {
                            // buff1 can not be applied when buff2 is present, buff2 can override buff1
                            if (!buffSuperior.containsKey(buff1)) buffSuperior.put(buff1, new HashSet<>(25));
                            buffSuperior.get(buff1).add(buff2);
                            if (!buffInferior.containsKey(buff2)) buffInferior.put(buff2, new HashSet<>(25));
                            buffInferior.get(buff2).add(buff1);
                        }
                    }
                }
            }
        }
        Bukkit.getLogger().info("[Entity Helper] buffSuperior: " + buffSuperior);
        Bukkit.getLogger().info("[Entity Helper] buffInferior: " + buffInferior);
    }
    public static class AimHelperOptions {
        double projectileSpeed = 0d, intensity = 1d, randomOffsetRadius = 0d, ticksOffset = 0;
        boolean useTickOrSpeedEstimation = false;
        public AimHelperOptions setTicksOffset(double ticksOffset) {
            this.ticksOffset = ticksOffset;
            return this;
        }
        public AimHelperOptions setProjectileSpeed(double projectileSpeed) {
            this.projectileSpeed = projectileSpeed;
            return this;
        }
        public AimHelperOptions setIntensity(double intensity) {
            this.intensity = intensity;
            return this;
        }
        public AimHelperOptions setRandomOffsetRadius(double randomOffsetRadius) {
            this.randomOffsetRadius = randomOffsetRadius;
            return this;
        }
        public AimHelperOptions setAimMode(boolean useTickOrSpeedEstimation) {
            this.useTickOrSpeedEstimation = useTickOrSpeedEstimation;
            return this;
        }
    }
    public static class WormSegmentMovementOptions {
        // "straightening" multiplier: last segment - next segment
        // note that this vector smoothens and straightens the worm.
        double straighteningMultiplier = 0;
        // "following" multiplier: last segment - current segment
        // note that this vector let the segment strictly follows the last.
        double followingMultiplier = 1;
        double followDistance = 1;
        boolean velocityOrTeleport = true;
        public WormSegmentMovementOptions setStraighteningMultiplier(double straighteningMultiplier) {
            this.straighteningMultiplier = straighteningMultiplier;
            return this;
        }
        public WormSegmentMovementOptions setFollowingMultiplier(double followingMultiplier) {
            this.followingMultiplier = followingMultiplier;
            return this;
        }
        public WormSegmentMovementOptions setFollowDistance(double followDistance) {
            this.followDistance = followDistance;
            return this;
        }
        public WormSegmentMovementOptions setVelocityOrTeleport(boolean velocityOrTeleport) {
            this.velocityOrTeleport = velocityOrTeleport;
            return this;
        }
    }
    // helper functions
    public static void initEntityMetadata(Entity entity) {
        setMetadata(entity, "damageType", "Melee");
        setMetadata(entity, "effects", new HashMap<String, Integer>());
        setMetadata(entity, "buffImmune", new HashMap<String, Integer>());
    }
    public static String getDamageType(Metadatable entity) {
        try {
            return getMetadata(entity, "damageType").asString();
        } catch (Exception e) {
            return "Melee";
        }
    }
    public static void setDamageType(Metadatable entity, String damageType) {
        if (damageType == null) setMetadata(entity, "damageType", "Melee");
        else setMetadata(entity, "damageType", damageType);
    }
    public static HashMap<String, Double> getAttrMap(Metadatable entity) {
        try {
            return (HashMap<String, Double>) getMetadata(entity, "attrMap").value();
        } catch (Exception e) {
            return new HashMap<>(0);
        }
    }
    public static MetadataValue getMetadata(Metadatable owner, String key) {
        try {
            return owner.getMetadata(key).get(0);
        } catch (Exception e) {
            return null;
        }
    }
    public static void setMetadata(Metadatable owner, String key, Object value) {
        if (value == null)
            owner.removeMetadata(key, TerrariaHelper.getInstance());
        else
            owner.setMetadata(key, new FixedMetadataValue(TerrariaHelper.getInstance(), value));
    }
    // helper functions for tweaking attribute
    public static void tweakAttribute(Entity entity, String key, String value, boolean addOrRemove) {
        if (key.equals("damageType")) {
            if (addOrRemove) setMetadata(entity, "damageType", value);
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
                Map<String, Integer> buffImmune = (Map<String, Integer>) getMetadata(entity, "buffImmune").value();
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
            Bukkit.getLogger().log(Level.SEVERE, "[Generic Helper] error when parsing value as a number (" + value + ") in tweakAttribute ", e);
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
            switch (key) {
                case "useTime":
                    value_number /= 2;
                    break;
                case "arrowConsumptionRate":
                case "ammoConsumptionRate":
                    value_number = 1 + value_number;
                    break;
                default:
                    if (key.endsWith("Multi"))
                        value_number = 1 + value_number;
            }
            switch (key) {
                case "damageTakenMulti":
                    if (addOrRemove)
                        attrMap.put(key, attrMap.getOrDefault(key, 1d) / (2 - value_number));
                    else
                        attrMap.put(key, attrMap.getOrDefault(key, 1d) * (2 - value_number));
                    break;
                case "ammoConsumptionRate":
                case "arrowConsumptionRate":
                    if (addOrRemove)
                        attrMap.put(key, attrMap.getOrDefault(key, 1d) * value_number);
                    else
                        attrMap.put(key, attrMap.getOrDefault(key, 1d) / value_number);
                    break;
                default:
                    if (key.endsWith("Multi")) {
                        if (addOrRemove)
                            attrMap.put(key, (attrMap.getOrDefault(key, 1d)) * value_number);
                        else
                            attrMap.put(key, (attrMap.getOrDefault(key, 1d)) / value_number);
                    } else {
                        if (addOrRemove)
                            attrMap.put(key, (attrMap.getOrDefault(key, 1d)) + value_number);
                        else
                            attrMap.put(key, (attrMap.getOrDefault(key, 1d)) - value_number);
                    }
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Generic Helper] error when parsing value as a number (" + value + ") in tweakAttribute ", e);
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
    // the two below is used in update player attribute
    public static void tweakAttribute(Entity entity, HashMap<String, Double> attrMap, String key, String value, boolean addOrRemove) {
        if (key.equals("damageType")) {
            if (addOrRemove) setMetadata(entity, "damageType", value);
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
                Map<String, Integer> buffImmune = (Map<String, Integer>) getMetadata(entity, "buffImmune").value();
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
            tweakAttribute(attrMap, key, value, addOrRemove);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Generic Helper] error when parsing value as a number (" + value + ") in tweakAttribute ", e);
        }
    }
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
        EntityHelper.tweakAllAttributes(entity, attrMap, allAttributes, addOrRemove);
        // attributes from the item's prefix
        String attributesPathPrefix = "prefixInfo." + itemInfo[0] +  ".attributes";
        ConfigurationSection allAttributesPrefix = TerrariaHelper.prefixConfig.getConfigurationSection(attributesPathPrefix);
        EntityHelper.tweakAllAttributes(entity, attrMap, allAttributesPrefix, addOrRemove);
    }
    public static void tweakAttribute(HashMap<String, Double> attrMap, ItemStack item, boolean addOrRemove) {
        String[] itemInfo = ItemHelper.splitItemName(item);
        // attributes from the item itself
        String attributesPath = itemInfo[1] + ".attributes";
        ConfigurationSection allAttributes = TerrariaHelper.itemConfig.getConfigurationSection(attributesPath);
        EntityHelper.tweakAllAttributes(attrMap, allAttributes, addOrRemove);
        // attributes from the item's prefix
        String attributesPathPrefix = "prefixInfo." + itemInfo[0] +  ".attributes";
        ConfigurationSection allAttributesPrefix = TerrariaHelper.prefixConfig.getConfigurationSection(attributesPathPrefix);
        EntityHelper.tweakAllAttributes(attrMap, allAttributesPrefix, addOrRemove);
    }
    public static void tweakAttribute(Entity entity, ItemStack item, boolean addOrRemove) {
        tweakAttribute(entity, getAttrMap(entity), item, addOrRemove);
    }
    public static void makeTarget(Entity entity, Entity target) {
        try {
            EntityInsentient nms_e = (EntityInsentient) (((CraftEntity) entity).getHandle());
            EntityLiving nms_t = ((CraftLivingEntity) target).getHandle();
            nms_e.setGoalTarget(nms_t, EntityTargetEvent.TargetReason.CUSTOM, true);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[ENTITY HELPER: MAKE TARGET]: error occurred while making " + entity + " targeting " + target,
                    e);
        }
    }
    public static void damageCD(List<Entity> dmgCdList, Entity damager, int cdTick) {
        dmgCdList.add(damager);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                () -> dmgCdList.remove(damager), cdTick);
    }
    public static void disguiseProjectile(Projectile projectile) {
        switch (projectile.getType()) {
            case SPLASH_POTION: {
                String name = projectile.getCustomName();
                if (name != null) {
                    ItemStack disguiseItem = new ItemStack(Material.SPLASH_POTION);
                    ItemMeta itemMeta = disguiseItem.getItemMeta();
                    itemMeta.setDisplayName(name);
                    ((PotionMeta) itemMeta).setColor(Color.fromRGB(255, 255, 255));
                    disguiseItem.setItemMeta(itemMeta);
                    ((ThrownPotion) projectile).setItem(disguiseItem);
                    if (name.contains("激光")) projectile.setGlowing(true);
                }
                break;
            }
            case SNOWBALL: {
                MiscDisguise disguise = new MiscDisguise(DisguiseType.ARROW);
                DisguiseAPI.disguiseEntity(projectile, disguise);
                break;
            }
        }
    }
    public static void slimeResize(Slime slime, int size) {
        if (size < 1) return;
        EntitySlime nms_slime = ((CraftSlime) slime).getHandle();
        double maxHealth = nms_slime.getAttributeInstance(GenericAttributes.maxHealth).getValue();
        double moveSpeed = nms_slime.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).getValue();
        nms_slime.setSize(size, false);
        nms_slime.getAttributeInstance(GenericAttributes.maxHealth).setValue(maxHealth);
        nms_slime.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(moveSpeed);
    }
    public static boolean checkMobTarget(Entity target, Entity mob) {
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (PlayerHelper.isProperlyPlaying(targetPlayer) &&
                targetPlayer.getWorld() == mob.getWorld() && targetPlayer.getGameMode() == GameMode.SURVIVAL) {
                double horDist = GenericHelper.getHorizontalDistance(mob.getLocation(), targetPlayer.getLocation());
                return horDist < 48;
            }
        }
        return false;
    }
    // potion effect
    public static boolean hasEffect(Entity entity, String effect) {
        try {
            return getEffectMap(entity).containsKey(effect);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] getEffectMap", e);
        }
        return false;
    }
    public static HashMap<String, Integer> getEffectMap(Entity entity) {
        try {
            return (HashMap<String, Integer>) getMetadata(entity, "effects").value();
        } catch (Exception e) {
            HashMap<String, Integer> effectMap = new HashMap<>();
            setMetadata(entity, "effects", effectMap);
            return effectMap;
        }
    }
    public static int getEffectLevelMax(String effect) {
        switch (effect) {
            case "破晓":
                return 8;
            case "伤害星云":
            case "生命星云":
            case "魔力星云":
                return 3;
            default:
                return 1;
        }
    }
    public static int getEffectLevelDuration(String effect) {
        switch (effect) {
            case "破晓":
                return 100;
            case "伤害星云":
            case "生命星云":
            case "魔力星云":
                return 160;
            default:
                return -1;
        }
    }
    public static int getEffectLevel(String effect, int durationTicks) {
        int levelDuration = getEffectLevelDuration(effect);
        if (levelDuration > 0) return Math.min((durationTicks + levelDuration - 1) / levelDuration, getEffectLevelMax(effect));
        return 1;
    }
    public static List<PotionEffectType> getVanillaEffectInflict(String effect) {
        ArrayList<PotionEffectType> result = new ArrayList<>(3);
        switch (effect) {
            case "阻塞":
                result.add(PotionEffectType.BLINDNESS);
                break;
            case "鱼鳃":
                result.add(PotionEffectType.WATER_BREATHING);
                break;
            case "黑曜石皮":
                result.add(PotionEffectType.FIRE_RESISTANCE);
                break;
            case "隐身":
                result.add(PotionEffectType.INVISIBILITY);
        }
        return result;
    }
    public static void tickEffect(Entity entity, String effect, int delay, double damagePerDelay) {
        try {
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            int timeRemaining = allEffects.getOrDefault(effect, 0) - delay;
            boolean shouldStop = false, removeEffectOnStop = true;
            // validate if the entity still needs ticking effect
            if (entity instanceof Player) {
                Player playerE = (Player) entity;
                // offline: do not clear effect time
                if (!playerE.isOnline()) {
                    shouldStop = true;
                    removeEffectOnStop = false;
                }
                // additionally, the effect time is being cleared if the player is not in survival mode (most likely dead).
                else if (playerE.getGameMode() != GameMode.SURVIVAL) {
                    shouldStop = true;
                }
            }
            if (((LivingEntity) entity).getHealth() < 1e-5) shouldStop = true;
            if (timeRemaining <= 0) {
                // removes the effect after the duration ends
                shouldStop = true;
            }
            // if the effect should terminate
            if (shouldStop) {
                endTickEffect(entity, effect, allEffects, removeEffectOnStop);
                return;
            }
            allEffects.put(effect, timeRemaining);
            // tick mechanism
            if (damagePerDelay > 0) {
                double damageMulti = 1;
                if (effect.equals("破晓")) damageMulti = getEffectLevel(effect, timeRemaining);
                handleDamage(entity, entity, damagePerDelay * damageMulti, "Debuff_" + effect);
            }
            switch (effect) {
                case "扭曲": {
                    World entityWorld = entity.getLocation().getWorld();
                    Location targetLoc = entityWorld.getHighestBlockAt(entity.getLocation()).getLocation();
                    targetLoc.add(0, 8 + MathHelper.xsin_degree(timeRemaining * 2.5) * 2, 0);
                    Vector velocity = targetLoc.subtract(entity.getLocation()).toVector();
                    velocity.multiply(1 / 6);
                    entity.setVelocity(velocity);
                    entity.setFallDistance(0);
                    break;
                }
                case "恐惧": {
                    if (BossHelper.bossMap.containsKey(BossHelper.BossType.WALL_OF_FLESH.msgName)) {
                        Entity damager = BossHelper.bossMap.get(BossHelper.BossType.WALL_OF_FLESH.msgName).get(0);
                        if (damager.getWorld() != entity.getWorld() ||
                                damager.getLocation().distanceSquared(entity.getLocation()) > 10000)
                            handleDamage(damager, entity, 114514, "Debuff_恐惧");
                    }
                    else {
                        allEffects.put(effect, 0);
                    }
                    break;
                }
            }
            // next delayed task
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> tickEffect(entity, effect, delay, damagePerDelay), delay);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] tickEffect", e);
        }
    }
    public static void endTickEffect(Entity entity, String effect, HashMap<String, Integer> allEffects, boolean removeEffectOnStop) {
        try {
            // this is to prevent removing effect from a player logging out
            if (removeEffectOnStop) allEffects.remove(effect);
            // tweak attrMap if the target is not a player
            if (!(entity instanceof Player)) {
                String attributesPath = "effects." + effect + ".attributes.";
                ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(attributesPath);
                tweakAllAttributes(entity, effectSection, false);
            } else {
                // remove the buff applied
                for (PotionEffectType effectInflict : getVanillaEffectInflict(effect))
                    ((LivingEntity) entity).removePotionEffect(effectInflict);
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] endTickEffect", e);
        }
    }
    public static void prepareTickEffect(Entity entity, String effect) {
        try {
            // setup constants
            int delay = 10, damagePerDelay = 0;
            if (effect.equals("扭曲")) {
                delay = 4;
            } else {
                delay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damageInterval", delay);
                damagePerDelay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damage", damagePerDelay);
                if (!(entity instanceof Player))
                    damagePerDelay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damageMonster", damagePerDelay);
            }
            // tweak attrMap if the target is not a player
            if (!(entity instanceof Player)) {
                String attributesPath = "effects." + effect + ".attributes.";
                ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(attributesPath);
                tweakAllAttributes(entity, effectSection, true);
            }
            // register delayed task for ticking potion
            int finalDamagePerDelay = damagePerDelay;
            int finalDelay = delay;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> tickEffect(entity, effect, finalDelay, finalDamagePerDelay), delay);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] prepareTickEffect", e);
        }
    }
    public static void applyEffect(Entity entity, String effect, int durationTicks) {
        try {
            // if the buff is not in config, do not do anything
            if (!TerrariaHelper.buffConfig.contains("effects." + effect)) return;
            // returns if the entity is immune to this effect (i.e. debuff)
            MetadataValue buffImmuneMetadata = getMetadata(entity, "buffImmune");
            if (buffImmuneMetadata != null) {
                Map<String, Integer> buffImmune = (Map<String, Integer>) buffImmuneMetadata.value();
                if (buffImmune.containsKey(effect)) return;
            }
            // does nothing if the target has a superior effect, otherwise tweak the inferior effects
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            for (String effectSuperior : buffSuperior.getOrDefault(effect, new HashSet<>()))
                if (allEffects.containsKey(effectSuperior)) return;
            for (String effectInferior : buffInferior.getOrDefault(effect, new HashSet<>()))
                if (allEffects.containsKey(effectInferior)) allEffects.put(effectInferior, 0);
            // apply the buff
            for (PotionEffectType effectInflict : getVanillaEffectInflict(effect))
                ((LivingEntity) entity).addPotionEffect(new PotionEffect(effectInflict, durationTicks, 0), true);
            // tweak duration of some special effects
            int finalDurationTicks;
            int currentDurationTicks = allEffects.getOrDefault(effect, 0);
            switch (effect) {
                case "护甲损伤":
                    finalDurationTicks = currentDurationTicks + durationTicks;
                    break;
                case "魔力疾病":
                    finalDurationTicks = currentDurationTicks + durationTicks;
                    if (finalDurationTicks > 400 && durationTicks < 400) finalDurationTicks = 400;
                    break;
                case "破晓":
                case "伤害星云":
                case "生命星云":
                case "魔力星云": {
                    int maxLevel = getEffectLevelMax(effect),
                            levelTime = getEffectLevelDuration(effect);
                    int currentLevel = getEffectLevel(effect, currentDurationTicks);
                    int applyLevel = getEffectLevel(effect, durationTicks);
                    if (applyLevel > currentLevel)
                        finalDurationTicks = Math.min(currentLevel + 1, maxLevel) * levelTime;
                    else
                        finalDurationTicks = currentLevel * levelTime;
                    break;
                }
                default:
                    finalDurationTicks = Math.max(currentDurationTicks, durationTicks);
            }
            // record effect info
            allEffects.put(effect, finalDurationTicks);
            // prepare to start ticking effect if the entity does not have it yet
            if (currentDurationTicks == 0) prepareTickEffect(entity, effect);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] applyEffect", e);
        }
    }
    private static void sendDeathMessage(Entity d, Entity v, String damageCause) {
        String dm = "";
        String killer = null;
        if (d != null) {
            killer = d.getCustomName();
        }

        List<String> deathMessages;
        if (TerrariaHelper.settingConfig.contains("deathMessages." + damageCause)) {
            deathMessages = TerrariaHelper.settingConfig.getStringList("deathMessages." + damageCause);
        } else {
            deathMessages = TerrariaHelper.settingConfig.getStringList("deathMessages.Generic");
        }
        dm = deathMessages.get((int) (Math.random() * deathMessages.size()));
        if (killer != null && d != v) {
            dm += "，凶手是" + killer;
        }
        dm = dm.replaceAll("<victim>", v.getName());
        Bukkit.broadcastMessage("§4" + dm);
    }
    public static boolean entityDamageEvent(Entity damager, Entity dPly, LivingEntity victim, LivingEntity damageTaker, double dmg, String damageCause, String damageReason) {
        if (damager == null) return true;
        String nameV = GenericHelper.trimText(victim.getName());
        Entity minion = damager;
        // special minion behaviour
        if (minion instanceof Projectile) {
            ProjectileSource projSrc = ((Projectile) minion).getShooter();
            if (projSrc instanceof Entity) minion = (Entity) projSrc;
        }
        if (minion.getScoreboardTags().contains("isMinion")) {
            if (victim.getScoreboardTags().contains("暗黑收割")) {
                victim.removeScoreboardTag("暗黑收割");
                handleEntityExplode(damager, 2, null, victim.getEyeLocation());
            }
        }
        // special victim behaviour
        switch (nameV) {
            case "史莱姆王": {
                if (dPly instanceof Player) {
                    MonsterHelper.spawnMob("史莱姆", victim.getLocation(), (Player) dPly);
                    if (Math.random() < 0.5) MonsterHelper.spawnMob("尖刺史莱姆", victim.getLocation(), (Player) dPly);
                    if (Math.random() < 0.25) MonsterHelper.spawnMob("尖刺史莱姆", victim.getLocation(), (Player) dPly);
                }
                break;
            }
            case "毁灭者": {
                if (dPly instanceof Player) {
                    if (victim.getScoreboardTags().contains("hasProbe") && Math.random() < 0.25) {
                        victim.setCustomName("毁灭者" + ChatColor.COLOR_CHAR + "4");
                        victim.removeScoreboardTag("hasProbe");
                        MonsterHelper.spawnMob("探测怪", victim.getLocation(), (Player) dPly);
                    }
                }
                break;
            }
            case "石巨人头": {
                if (dmg >= victim.getHealth()) {
                    victim.addScoreboardTag("noDamage");
                    ArrayList<LivingEntity> bossParts = BossHelper.bossMap.get("石巨人");
                    if (bossParts != null) {
                        bossParts.get(0).removeScoreboardTag("noDamage");
                        return false;
                    }
                }
                break;
            }
            case "月球领主手":
            case "月球领主": {
                if (dmg >= victim.getHealth()) {
                    victim.addScoreboardTag("noDamage");
                    victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    return false;
                }
                break;
            }
            case "蜥蜴人": {
                double healthThreshold = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() / 2;
                if (victim.getHealth() > healthThreshold) {
                    if ((victim.getHealth() - dmg) <= healthThreshold) {
                        HashMap<String, Double> attrMap = getAttrMap(victim);
                        attrMap.put("damageMulti", 1.5d);
                        attrMap.put("defenceMulti", 1.5d);
                        attrMap.put("knockbackResistance", 1d);
                    }
                }
                break;
            }
            case "胡桃夹士": {
                double healthThreshold = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() / 2;
                if (victim.getHealth() > healthThreshold) {
                    if ((victim.getHealth() - dmg) <= healthThreshold) {
                        HashMap<String, Double> attrMap = getAttrMap(victim);
                        attrMap.put("damageMulti", 1.25d);
                        attrMap.put("defenceMulti", 1.5d);
                        attrMap.put("knockbackResistance", 0.91d);
                    }
                }
                break;
            }
        }
        // player being damaged
        if (victim instanceof Player) {
            // health regen time reset
            setMetadata(victim, "regenTime", 0);
            Player vPly = (Player) victim;
            switch (damager.getName()) {
                case "水螺旋": {
                    // phrase 2 only
                    if (damager.getScoreboardTags().contains("isMonster")) {
                        damager.remove();
                        ArrayList<LivingEntity> bossList = BossHelper.bossMap.get("猪鲨公爵");
                        if (bossList != null) {
                            Entity dukeFishron = bossList.get(0);
                            // TODO: spawn a tornado
                        }
                        return false;
                    }
                    break;
                }
                case "吮脑怪": {
                    setMetadata(damager, "suckTarget", victim);
                    break;
                }
            }
            HashSet<String> accessories = (HashSet<String>) getMetadata(victim, "accessory").value();
            HashMap<String, Double> attrMap = getAttrMap(victim);
            boolean hasMagicCuff = accessories.contains("魔法手铐") || accessories.contains("天界手铐");
            if (hasMagicCuff) {
                int recovery = (int) Math.max(1, Math.floor(dmg / 4));
                PlayerHelper.restoreMana(vPly, recovery);
            }
            if (getMetadata(vPly, "armorSet").asString().equals("耀斑套装") && damageCause.equals("Melee")) {
                handleDamage(damageTaker, damager, Math.min(Math.max(dmg, 300), 1500), "Thorn");
            }
        }
        // thorn effect
        HashMap<String, Integer> victimEffects = getEffectMap(victim);
        if (victimEffects.containsKey("荆棘") && damageCause.equals("Melee")) {
            handleDamage(damageTaker, damager, Math.min(Math.max(dmg / 3, 25), 500), "Thorn");
        }
        if (victim.getScoreboardTags().contains("destroyOnDamage")) {
            victim.remove();
            return false;
        }
        if (dPly instanceof Player && damageCause.equals("Magic") && !damageReason.equals("Spectre")) {
            PlayerHelper.playerMagicArmorSet((Player) dPly, victim, dmg);
        }
        return true;
    }
    public static void handleDeath(Entity v, Entity dPly, Entity d, String damageCause) {
        if (v instanceof Player) {
            Player vPly = (Player) v;
            vPly.setVelocity(new Vector());
            int respawnTime = 15;
            for (ArrayList<LivingEntity> bossList : BossHelper.bossMap.values()) {
                HashMap<Player, Double> targets = (HashMap<Player, Double>) getMetadata(bossList.get(0), "targets").value();
                if (targets.containsKey(vPly)) {
                    respawnTime = Math.max(respawnTime, Math.min(targets.size() * 15, 75));
                }
            }
            int moneyDrop = (int) Math.floor(PlayerHelper.getMoney(vPly) / 100);
            moneyDrop = (int) Math.ceil(moneyDrop * 0.75);
            moneyDrop *= 100;
            PlayerHelper.setMoney(vPly, PlayerHelper.getMoney(vPly) - moneyDrop);
            GenericHelper.dropMoney(vPly.getEyeLocation(), moneyDrop, false);
            String moneyMsg = "";
            if (moneyDrop > 0) {
                moneyMsg = "§c§l掉了";
                int[] moneyConverted = GenericHelper.coinConversion(moneyDrop, false);
                if (moneyConverted[0] > 0)
                    moneyMsg += "§c§l " + moneyConverted[0] + "§f§l 铂";
                if (moneyConverted[1] > 0)
                    moneyMsg += "§c§l " + moneyConverted[1] + "§e§l 金";
                if (moneyConverted[2] > 0)
                    moneyMsg += "§c§l " + moneyConverted[2] + "§7§l 银";
                if (moneyConverted[3] > 0)
                    moneyMsg += "§c§l " + moneyConverted[3] + "§c§l 铜";
            }
            vPly.sendTitle("§c§l你死了！", moneyMsg, 0, respawnTime * 20, 0);
            vPly.closeInventory();
            sendDeathMessage(d, v, damageCause);
            vPly.setHealth(Math.min(400, vPly.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            setMetadata(vPly, "respawnCD", respawnTime * 20);
            vPly.setGameMode(GameMode.SPECTATOR);
            vPly.setFlySpeed(0);
            vPly.setFallDistance(0);
            // further respawn ticking mechanism in regen thread
        } else {
            // lacewing etc
            Set<String> vScoreboardTags = v.getScoreboardTags();
            if (dPly instanceof Player) {
                Player dPlayer = (Player) dPly;
                String victimName = GenericHelper.trimText(v.getName());
                if (victimName.equals("七彩草蛉")) {
                    BossHelper.spawnBoss(dPlayer, BossHelper.BossType.EMPRESS_OF_LIGHT);
                }
                // drop stars
                switch (damageCause) {
                    case "Magic":
                    case "spectre":
                        if (v.getScoreboardTags().contains("isMonster")) {
                            for (int i = 0; i < 3; i ++)
                                if (Math.random() < 0.1) {
                                    ItemStack star = new ItemStack(Material.CLAY_BALL);
                                    ItemMeta meta = star.getItemMeta();
                                    meta.setDisplayName("§9星");
                                    star.setItemMeta(meta);
                                    dPlayer.getWorld().dropItemNaturally(v.getLocation(), star);
                                }
                        }
                }
                // drop heart
                if (dPlayer.getHealth() < dPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() &&
                        vScoreboardTags.contains("isMonster") && Math.random() < 0.2) {
                        ItemStack heart = new ItemStack(Material.CLAY_BALL);
                        ItemMeta meta = heart.getItemMeta();
                        meta.setDisplayName("§c心");
                        heart.setItemMeta(meta);
                        dPlayer.getWorld().dropItemNaturally(v.getLocation(), heart);
                    }
            }
            // set health/directly remove
            if (v instanceof LivingEntity) ((LivingEntity) v).setHealth(0);
            else v.remove();
            // celestial pillars
            if (vScoreboardTags.contains("isPillar")) {
                Event.pillars.remove(v.getName());
                switch (Event.pillars.size()) {
                    case 3:
                        Bukkit.broadcastMessage("§d§o你的头脑变得麻木...");
                        break;
                    case 2:
                        Bukkit.broadcastMessage("§d§o你痛苦不堪...");
                        break;
                    case 1:
                        Bukkit.broadcastMessage("§d§o阴森的声音在你耳边萦绕不绝...");
                        break;
                    default:
                        Player bossTarget = null;
                        double minDistSqr = 1e7;
                        for (Player checkPlayer : v.getWorld().getPlayers()) {
                            double currDistSqr = v.getLocation().distanceSquared(checkPlayer.getLocation());
                            if (currDistSqr < minDistSqr) {
                                minDistSqr = currDistSqr;
                                bossTarget = checkPlayer;
                            }
                        }
                        if (bossTarget != null) {
                            Bukkit.broadcastMessage("§d§o月亮末日慢慢逼近...");
                            BossHelper.spawnBoss(bossTarget, BossHelper.BossType.MOON_LORD);
                        }
                }
            }
            // NPC should also get death message
            else if (vScoreboardTags.contains("isNPC"))
                sendDeathMessage(d, v, damageCause);
            // monster generic drop, event etc.
            else if (vScoreboardTags.contains("isMonster")) {
                LivingEntity vLiving = (LivingEntity) v;
                MetadataValue spawnEvt = getMetadata(v, "spawnEvent");
                // event monster
                if (spawnEvt != null && spawnEvt.asString().equals(Event.currentEvent)) {
                    HashMap<String, Double> eventInfo = Event.eventInfo;
                    String currentEvent = Event.currentEvent;
                    if (currentEvent.equals("史莱姆雨")) {
                        double slimeKill = eventInfo.getOrDefault("slimeKill", 0d) + 1;
                        if (slimeKill % 50 == 0) {
                            if (d instanceof Player) {
                                BossHelper.spawnBoss((Player) d, BossHelper.BossType.KING_SLIME);
                            } else slimeKill--;
                        }
                        eventInfo.put("slimeKill", slimeKill);
                    } else if (eventInfo.getOrDefault("isInvasion", 1d) > 0) {
                        MetadataValue progress = getMetadata(v, "killProgress");
                        if (progress != null) {
                            eventInfo.put("invadeProgress",
                                    eventInfo.getOrDefault("invadeProgress", 0d) + progress.asDouble());
                            if (eventInfo.get("invadeProgress") >= eventInfo.get("invadeProgressMax")) {
                                switch (currentEvent) {
                                    case "冰霜月":
                                    case "南瓜月": {
                                        break;
                                    }
                                    default:
                                        Bukkit.broadcastMessage("§d§l" + currentEvent + "被击退了！");
                                        if (currentEvent.equals("哥布林军团"))
                                            for (Player ply : Bukkit.getOnlinePlayers())
                                                YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml").set("bossDefeated." + currentEvent, true);
                                        Event.currentEvent = "";
                                }
                            }
                        }
                    }
                }
                // generic death drop etc.
                MetadataValue motherType = getMetadata(v, "motherType");
                if (motherType != null) {
                    switch (motherType.asString()) {
                        // lava slime leaves lava ticksBeforeHookingFish death
                        case "史莱姆": {
                            if (v.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)) {
                                Location deathLoc = vLiving.getEyeLocation();
                                org.bukkit.block.Block deathBlock = deathLoc.getBlock();
                                if (!deathBlock.getType().isSolid()) {
                                    deathBlock.setType(Material.LAVA);
                                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> {
                                        if (deathBlock.getType().equals(Material.LAVA))
                                            deathBlock.setType(Material.AIR);
                                    }, 100);
                                }
                            }
                            break;
                        }
                        // the hungry flies towards the player after breaks free
                        case "饿鬼Attached": {
                            if (dPly instanceof Player) {
                                MonsterHelper.spawnMob("饿鬼", v.getLocation(), (Player) dPly);
                                v.remove();
                            }
                            break;
                        }
                        // lunatic cultist spawns after killing the mob in the dungeon
                        case "拜月教教徒": {
                            if (dPly instanceof Player) {
                                BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.LUNATIC_CULTIST);
                            }
                            break;
                        }
                    }
                }
                if (dPly instanceof Player) {
                    Player dPlayer = (Player) dPly;
                    switch ( GenericHelper.trimText(v.getName()) ) {
                        // the Hive Mind
                        case "腐化囊": {
                            BossHelper.spawnBoss(dPlayer, BossHelper.BossType.THE_HIVE_MIND);
                            break;
                        }

                    }
                    // dungeon souls
                    if (WorldHelper.BiomeType.getBiome(dPlayer) == WorldHelper.BiomeType.DUNGEON) {
                        if (PlayerHelper.hasDefeated(dPlayer, "世纪之花") && Math.random() < 0.125)
                            MonsterHelper.spawnMob("地牢幽魂", v.getLocation(), dPlayer);
                    }
                    // souls and shards
                    if (PlayerHelper.hasDefeated(dPlayer, "血肉之墙")) {
                        if (!v.getScoreboardTags().contains("isBOSS")) {
                            Biome biome = v.getLocation().getBlock().getBiome();
                            switch (biome) {
                                case ICE_FLATS: // hallow surface
                                case MUSHROOM_ISLAND: // corruption surface
                                {
                                    if (v.getLocation().getY() > 50 && Math.random() < 0.02)
                                        v.getWorld().dropItemNaturally(vLiving.getEyeLocation(),
                                                ItemHelper.getItemFromDescription(biome == Biome.ICE_FLATS ? "光明碎块" : "暗黑碎块"));
                                    break;
                                }
                                case MUTATED_ICE_FLATS: // hallow underground
                                case MUSHROOM_ISLAND_SHORE: // corruption underground
                                {
                                    double dropChance = (255 - v.getLocation().getY()) / 125;
                                    if (Math.random() < dropChance)
                                        v.getWorld().dropItemNaturally(vLiving.getEyeLocation(),
                                                ItemHelper.getItemFromDescription(biome == Biome.MUTATED_ICE_FLATS ? "光明之魂" : "暗影之魂"));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    public static boolean checkCanDamage(Entity entity, Entity target) {
        return checkCanDamage(entity, target, true);
    }
    public static boolean checkCanDamage(Entity entity, Entity target, boolean strict) {
        // dead target
        if (target.isDead()) return false;
        if (target instanceof LivingEntity && ((LivingEntity) target).getHealth() <= 0) return false;
        // store scoreboard tags as it is requested frequently below
        Set<String> targetScoreboardTags = target.getScoreboardTags();
        Set<String> entityScoreboardTags = entity.getScoreboardTags();
        Entity damageSource = getDamageSource(entity);
        if (targetScoreboardTags.contains("isPillar")) {
            MetadataValue temp = getMetadata(target, "shield");
            if (temp != null && temp.asInt() > 0) return false;
        }
        if (!(target instanceof LivingEntity)) {
            if (target instanceof Projectile) {
                if (getAttrMap(target).containsKey("health")) {
                    ProjectileSource src = ((Projectile) target).getShooter();
                    if (src instanceof Entity) {
                        target = (Entity) src;
                        // can damage shooter (no strict checking needed):
                        // if strict mode return false(so homing weapons do not home to enemy projectiles)
                        // if not strict mode return true(so enemy can be damaged)
                        if (checkCanDamage(entity, target, false)) return !strict;
                    }
                }
            }
            return false;
        }
        // only players can damage armor stand
        if (target instanceof ArmorStand) return damageSource instanceof Player;
        // invulnerable target
        if (target.isInvulnerable()) return false;
        if (targetScoreboardTags.contains("noDamage")) return false;
        // fallen star etc. can damage players and NPC etc. without further check
        if (entityScoreboardTags.contains("ignoreCanDamageCheck")) return true;
        // can not attack oneself
        if (damageSource == target) return false;
        // check details about damage source and victim
        entityScoreboardTags = damageSource.getScoreboardTags();
        if (damageSource instanceof Player) {
            if (!PlayerHelper.isProperlyPlaying((Player) damageSource)) return false;
            // only both players are in pvp mode and the check is not strict
            // so homing weapons and minions will not target other players
            if (target instanceof Player)
                return (!strict) && (targetScoreboardTags.contains("PVP") && entityScoreboardTags.contains("PVP"));
            else {
                HashSet<String> accessories = PlayerHelper.getAccessories(damageSource);
                if (targetScoreboardTags.contains("isMonster")) return true;
                // homing weapons and minions should not willingly attack critters and NPCs
                if (strict) return false;
                if (targetScoreboardTags.contains("isNPC"))
                    return accessories.contains(target.getName() + "巫毒娃娃");
                if (targetScoreboardTags.contains("isAnimal"))
                    return (!accessories.contains("小动物友谊指南"));
            }
        } else if (target instanceof Player) {
            // non-player attacks player
            HashSet<String> accessories = PlayerHelper.getAccessories(target);
            Player targetPly = (Player) target;
            if (PlayerHelper.isProperlyPlaying(targetPly)) {
                // handle special mother type (slime damage neglected by royal gel)
                MetadataValue temp = getMetadata(damageSource, "motherType");
                if (temp != null) {
                    String motherType = temp.asString();
                    if (motherType.equals("史莱姆") && accessories.contains("皇家凝胶"))
                        return false;
                }
                // NPCs do not attack players
                return !entityScoreboardTags.contains("isNPC");
            } else {
                // players that are logged out are not supposed to get hurt.
                return false;
            }
        } else {
            // non-player attacks non-player
            // monster attack target: all non-monster targets are valid
            if (entityScoreboardTags.contains("isMonster")) {
                return !(targetScoreboardTags.contains("isMonster"));
            }
            // any entity other than monster -> monster: true
            return targetScoreboardTags.contains("isMonster");
        }
        return false;
    }
    public static Entity getDamageSource(Entity damager) {
        Entity source = damager;
        if (source instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) source).getShooter();
            if (shooter instanceof Entity) source = (Entity) shooter;
        }
        if (source.hasMetadata("damageSourcePlayer"))
            source = (Entity) getMetadata(source, "damageSourcePlayer").value();
        return source;
    }
    public static void knockback(Entity entity, Vector dir, boolean addOrReplace) {
        double kbResistance = getAttrMap(entity).getOrDefault("knockbackResistance", 0d);
        if (kbResistance >= 1) return;
        double kbMulti = Math.max(1 - kbResistance, 0);
        dir.multiply(kbMulti);
        setMetadata(entity, "kbFactor", kbMulti);
        Entity knockbackTaker = entity.getVehicle();
        if (knockbackTaker == null) knockbackTaker = entity;
        if (addOrReplace) {
            knockbackTaker.setVelocity(knockbackTaker.getVelocity().add(dir));
        } else {
            // multiply by 1-kbMulti, as some enemies have negative knockback resistance
            knockbackTaker.setVelocity(knockbackTaker.getVelocity().multiply(1 - kbMulti).add(dir));
        }
    }
    private static void displayDamageActionBar(Player damager, String victimName, int health, int maxHealth, int damage) {
        if (health > 0) {
            PlayerHelper.sendActionBar(damager,
                    "§r" + victimName + " §6[§a" + health + "§6/§a" + maxHealth + "§6] §b(-" + damage + ")");
        } else
            PlayerHelper.sendActionBar(damager, victimName + " §c领了盒饭");
    }
    public static void handleDamage(Entity damager, Entity victim, double damage, String damageReason) {
        HashMap<String, Double> victimAttrMap = getAttrMap(victim);
        Entity damageSource = getDamageSource(damager);
        // projectile etc
        if (!(victim instanceof LivingEntity)) {
            if (victimAttrMap.containsKey("health")) {
                double health = victimAttrMap.get("health") - damage;
                double maxHealth = victimAttrMap.getOrDefault("healthMax", health);
                victimAttrMap.put("health", health);
                if (damageSource instanceof Player)
                    displayDamageActionBar((Player) damageSource, victim.getName(), (int) health, (int) maxHealth, (int) damage);
                if (health < 0) {
                    victim.remove();
                }
            }
            return;
        }
        // living entities
        Set<String> victimScoreboardTags = victim.getScoreboardTags();
        if (victimScoreboardTags.contains("isDaawnlight")) {
            // Daawnlight target should not take any damage
            return;
        }
        LivingEntity victimLivingEntity = (LivingEntity) victim;
        LivingEntity damageTaker = victimLivingEntity;
        // no damage scenarios
        boolean canDamage = true;
        if (victimScoreboardTags.contains("isMinion")) canDamage = false;
        else if (victimScoreboardTags.contains("noDamage")) canDamage = false;
        else if (victim.isInvulnerable()) canDamage = false;
        else if (victim instanceof Player && ((Player) victim).getGameMode() != GameMode.SURVIVAL) canDamage = false;
        else if (victimLivingEntity.getHealth() <= 0) canDamage = false;
        // check minion damage
        boolean isMinionDmg = false;
        boolean isDirectAttackDamage = false;
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity && ((Entity) shooter).getScoreboardTags().contains("isMinion"))
                isMinionDmg = true;
        } else if (damager.getScoreboardTags().contains("isMinion")) isMinionDmg = true;
        // setup properties such as invulnerability tick and defence
        int damageInvulnerabilityTicks;
        double defence = victimAttrMap.getOrDefault("defence", 0d) * victimAttrMap.getOrDefault("defenceMulti", 1d);
        switch (damageReason) {
            case "DirectDamage":
            case "Projectile":
            case "Explosion":
            case "Thorn":
            case "Spectre":
                isDirectAttackDamage = true;
        }
        if (victimScoreboardTags.contains("isBOSS")) {
            if (isDirectAttackDamage) {
                MetadataValue bossTargets = getMetadata(victim, "targets");
                boolean canProperlyDamage = false;
                if (damageSource instanceof Player) {
                    if ( bossTargets == null ||
                            (((HashMap<Player, Double>) (bossTargets.value())).containsKey(damageSource)) )
                        canProperlyDamage = true;
                }
                // damage source of a boss is not a target
                if (!canProperlyDamage)
                    defence = 999999999;
            } else {
                // if the damage source of a boss is neither direct attack, thorn nor debuff (that is, lava etc.)
                // the damage is ignored.
                if ((!damageReason.startsWith("Debuff_")) && (!damageReason.equals("Thorn"))) canDamage = false;
            }
        }
        if (!canDamage) return;

        // damage taker is the entity that takes the damage
        if (victim.hasMetadata("damageTaker")) {
            Object dmgTaker = getMetadata(victim, "damageTaker").value();
            if (dmgTaker instanceof LivingEntity)
                damageTaker = (LivingEntity) dmgTaker;
        }
        // setup damager attribute and buff inflict
        HashMap<String, Double> damagerAttrMap = getAttrMap(damager);
        List<String> buffInflict = new ArrayList<>();
        String damageType;
        if (isDirectAttackDamage) {
            damageType = getDamageType(damager);
            if (damageReason.equals("DirectDamage"))
                damageType = damageType.equals("Melee") ? "TrueMelee" : "Melee";
        } else {
            damageType = damageReason;
        }
        // if the victim has invincibility frame on this damage type (usually player)
        String damageInvincibilityFrameName = "tempDamageCD_" + damageType;
        if (victimScoreboardTags.contains(damageInvincibilityFrameName)) return;
        // projectile buff inflict
        if (damager instanceof Projectile) {
            buffInflict.addAll(TerrariaHelper.projectileConfig.getStringList(damager.getName() + ".buffInflict"));
        }
        // player buff inflict
        if (damageSource instanceof Player) {
            // player minion buff inflict
            HashMap<String, ArrayList<String>> buffInflictMap = PlayerHelper.getPlayerEffectInflict(damageSource);
            buffInflict.addAll(buffInflictMap.getOrDefault("buffInflict", new ArrayList<>(0)));
            switch (damageType) {
                case "Arrow":
                case "Bullet":
                case "Rocket":
                    buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictRanged", new ArrayList<>(0)));
                    break;
                case "TrueMelee":
                    buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictMelee", new ArrayList<>(0)));
                    buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictTrueMelee", new ArrayList<>(0)));
                    break;
                default: // Summon, Melee, Magic
                    // whip
                    if (damageType.equals("Summon") && damageReason.equals("DirectDamage"))
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictMelee", new ArrayList<>(0)));
                    buffInflict.addAll(buffInflictMap.getOrDefault("buffInflict" + damageType, new ArrayList<>(0)));
            }
        }
        // monster buff inflict
        else {
            buffInflict.addAll(TerrariaHelper.entityConfig.getStringList(GenericHelper.trimText(damageSource.getName()) + ".buffInflict"));
        }
        // apply (de)buff(s) to victim
        for (String buff : buffInflict) {
            String[] buffInfo = buff.split("\\|");
            double chance;
            try {
                chance = Double.parseDouble(buffInfo[2]);
                if (Math.random() < chance)
                    applyEffect(victim, buffInfo[0], Integer.parseInt(buffInfo[1]));
            } catch (Exception ignored) {
            }
        }

        // further setup damage info
        damageInvulnerabilityTicks = victimAttrMap.getOrDefault("invulnerabilityTick", 0d).intValue();
        double dmg = damage;
        double knockback = 0d, critRate = -1e9;
        boolean damageFixed = false;
        switch (damageReason) {
            case "Fear":
            case "Boss_Angry":
                dmg = 114514;
                damageFixed = true;
                break;
            case "Fall":
                double fallDist = victim.getFallDistance();
                dmg = (fallDist - 12.5) * 20;
                if (dmg < 0) return;
                break;
            case "BlockExplosion":
                knockback = 5;
                dmg = 1000;
                break;
            case "Lava":
                if (victim.getLocation().getBlock().getBiome() == Biome.SAVANNA)
                    applyEffect(victim, "硫磺火", 150);
                else
                    applyEffect(victim, "燃烧", 150);
                // mainly prevents excessive damage dealt to enemies
                damageInvulnerabilityTicks = Math.max(damageInvulnerabilityTicks, 30);
                break;
            case "Drowning":
                dmg = 50;
                damageFixed = true;
                break;
            case "Suffocation":
                damageInvulnerabilityTicks = 0;
                dmg = 10;
                damageFixed = true;
                break;
            case "Thorn":
                damageInvulnerabilityTicks = 0;
                damageFixed = true;
                break;
            default:
                if (damageType.startsWith("Debuff_")) {
                    damageInvulnerabilityTicks = 0;
                    damageFixed = true;
                } else if (isDirectAttackDamage) {
                    // special minion whip etc
                    if (isMinionDmg) {
                        if (victimScoreboardTags.contains("鞭炮")) {
                            victim.removeScoreboardTag("鞭炮");
                            dmg *= 2.75;
                            victim.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, victim.getLocation(), 1);
                            victim.getWorld().playSound(victim.getLocation(), "entity.generic.explode", 1f, 1f);
                        }
                    }
                    knockback = damagerAttrMap.getOrDefault("knockback", 0d);
                    knockback *= damagerAttrMap.getOrDefault("knockbackMulti", 1d);
                    // no percentage dmg for spectre; critical strike only.
                    boolean canGetPercentageBonus = !damageReason.equals("Spectre");
                    if (canGetPercentageBonus)
                        dmg *= damagerAttrMap.getOrDefault("damageMulti", 1d);
                    critRate = damagerAttrMap.getOrDefault("crit", 0d);
                    switch (damageType) {
                        case "TrueMelee":
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damageTrueMeleeMulti", 1d);
                            critRate += damagerAttrMap.getOrDefault("critTrueMelee", 0d);
                        case "Melee":
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damageMeleeMulti", 1d);
                            critRate += damagerAttrMap.getOrDefault("critMelee", 0d);
                            knockback *= damagerAttrMap.getOrDefault("knockbackMeleeMulti", 1d);
                            break;
                        case "Arrow":
                        case "Bullet":
                        case "Rocket":
                            if (canGetPercentageBonus) {
                                dmg *= damagerAttrMap.getOrDefault("damage" + damageType + "Multi", 1d);
                                dmg *= damagerAttrMap.getOrDefault("damageRangedMulti", 1d);
                            }
                            critRate += damagerAttrMap.getOrDefault("critRanged", 0d);
                            break;
                        case "Magic":
                            critRate += damagerAttrMap.getOrDefault("critMagic", 0d);
                        case "Summon":
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damage" + damageType + "Multi", 1d);
                            break;
                        default:
                            Bukkit.getLogger().log(Level.SEVERE, "Unhandled damage type: " + damageType);
                            return;
                    }
                } else {
                    Bukkit.getLogger().log(Level.SEVERE, "Unhandled damage reason: " + damageReason);
                    return;
                }
        }

        // handle random damage floating and crit
        boolean crit = false;
        if (!damageFixed) {
            double damageTakenMulti = victimAttrMap.getOrDefault("damageTakenMulti", 1d);
            dmg *= Math.random() * 0.3 + 0.85;
            if (isMinionDmg) {
                MetadataValue temp;
                temp = getMetadata(victim, "minionWhipBonusDamage");
                double dmgBonus = temp != null ? temp.asDouble() : 0;
                temp = getMetadata(victim, "minionWhipBonusCrit");
                critRate += temp != null ? temp.asDouble() : 0;
            }
            if (!(victim instanceof Player)) {
                // crit, only applies to non-player victims
                if (Math.random() * 100 < critRate) {
                    crit = true;
                    dmg *= 1 + (damagerAttrMap.getOrDefault("critDamage", 1d) / 100);
                }
            } else {
                // paladin shield, only applies to player victims
                if (! hasEffect(victim, "圣骑士护盾")) {
                    String team = getMetadata(victim, "team").asString();
                    // works with players within 64 blocks
                    double dist = 4096;
                    Entity shieldPly = null;
                    for (Player ply : victim.getWorld().getPlayers()) {
                        if (!PlayerHelper.isProperlyPlaying(ply)) continue;
                        if (!hasEffect(ply, "圣骑士护盾")) continue;
                        String currTeam = getMetadata(ply, "team").asString();
                        if (!(currTeam.equals(team))) continue;
                        double currDist = ply.getLocation().distanceSquared(victim.getLocation());
                        if (currDist >= dist) continue;
                        dist = currDist;
                        shieldPly = ply;
                    }
                    if (shieldPly != null) {
                        handleDamage(damager, shieldPly, (dmg * 0.05), "Thorn");
                        dmg *= 0.85;
                    }
                }
            }
            dmg *= damageTakenMulti;
            defence = Math.max(defence - damagerAttrMap.getOrDefault("armorPenetration", 0d), 0);
            dmg -= defence * 0.75;
            if (victimScoreboardTags.contains("isBOSS")) {
                double dynamicDR = 1;
                MetadataValue temp = getMetadata(victim, "dynamicDR");
                if (temp != null) dynamicDR = temp.asDouble();
                BossHelper.BossType type = (BossHelper.BossType) getMetadata(victim, "bossType").value();
                if (! (damageSource instanceof Player && PlayerHelper.hasDefeated((Player) damageSource, type.msgName)) )
                    dmg *= dynamicDR;
            }
        }

        // call damage event
        if (! entityDamageEvent(damager, damageSource, victimLivingEntity, damageTaker, dmg, damageType, damageReason))
            return;
        dmg = Math.round(dmg);
        if (dmg < 1.5) dmg = crit ? 2 : 1;

        // damage/kill
        if (!(victim instanceof ArmorStand)) {
            if (victimScoreboardTags.contains("isBOSS") && damageSource instanceof Player) {
                MetadataValue temp = getMetadata(damageTaker, "targets");
                if (temp != null) {
                    HashMap<Player, Double> targets = (HashMap<Player, Double>) temp.value();
                    Player ply = (Player) damageSource;
                    if (targets.containsKey(ply))
                        targets.put(ply, targets.get(ply) + dmg);
                }
            }
            if (damageTaker.getHealth() <= dmg) {
                // kills the target
                handleDeath(damageTaker, damageSource, damager, damageType);
                String sound;
                if (victimScoreboardTags.contains("isMechanic")) sound = "entity.generic.explode";
                else sound = "entity." + damageTaker.getType() + ".death";
                sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundKilled", sound);
                victim.getWorld().playSound(victim.getLocation(), sound, 3, 1);
            } else {
                // hurts the target
                damageTaker.setHealth(Math.max(0, damageTaker.getHealth() - dmg));
                if (!damageType.startsWith("Debuff_")) {
                    // if damage cause is not debuff, play hurt sound
                    String sound;
                    if (victimScoreboardTags.contains("isMechanic")) sound = "entity.irongolem.hurt";
                    else sound = "entity." + damageTaker.getType() + ".hurt";
                    sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundDamaged", sound);
                    victim.getWorld().playSound(victim.getLocation(), sound, 3, 1);
                }
                // knockback
                if (knockback > 0) {
                    Vector vec = victim.getLocation().subtract(damager.getLocation()).toVector();
                    MathHelper.setVectorLength(vec, knockback / 20);
                    knockback(victim, vec, false);
                }
            }
        }

        // display damage
        GenericHelper.displayHolo(victim, dmg, crit, damageType);

        // send info message to damager player
        if (damageSource instanceof Player && victim != damageSource) {
            String vName = damageTaker.getName();
            int dmgInt = (int) dmg;
            int healthInt = (int) damageTaker.getHealth();
            int maxHealthInt = (int) damageTaker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            displayDamageActionBar((Player) damageSource, vName, healthInt, maxHealthInt, dmgInt);
        }

        // handle invincibility ticks
        if (damageInvulnerabilityTicks > 0) {
            victim.addScoreboardTag(damageInvincibilityFrameName);
            Entity finalVictim = victim;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> finalVictim.removeScoreboardTag(damageInvincibilityFrameName), damageInvulnerabilityTicks);
        }
    }
    public static void handleEntityExplode(Entity source, Collection<Entity> damageExceptions) {
        handleEntityExplode(source, damageExceptions, source.getLocation());
    }
    public static void handleEntityExplode(Entity source, Collection<Entity> damageExceptions, Location loc) {
        double blastRadius = TerrariaHelper.projectileConfig.getDouble(source.getName() + ".blastRadius", 1.5d);
        handleEntityExplode(source, blastRadius, damageExceptions, loc);
    }
    public static void handleEntityExplode(Entity source, double radius, Collection<Entity> damageExceptions, Location loc) {
        handleEntityExplode(source, radius, damageExceptions, loc, 1);
    }
    public static void handleEntityExplode(Entity source, double radius, Collection<Entity> damageExceptions, Location loc, int ticksDuration) {
        boolean damageShooter = false;
        boolean destroyBlock = false;
        ConfigurationSection sourceSection = TerrariaHelper.projectileConfig.getConfigurationSection(GenericHelper.trimText(source.getName()));
        if (sourceSection != null) {
            damageShooter = sourceSection.getBoolean("blastDamageShooter", false);
            destroyBlock = sourceSection.getBoolean("blastDestroyBlock", false);
        }
        double dmg = getAttrMap(source).getOrDefault("damage", 25d);
        // particles
        if (radius < 3)
            loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1);
        else if (radius < 5)
            loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        else {
            int amount = (int) (radius * radius);
            double actualRad = radius - 4;
            loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, amount, actualRad, actualRad, actualRad);
        }
        // sound
        loc.getWorld().playSound(loc, "entity.generic.explode", (float) radius + 2, 1f);
        // damage nearby entities
        Collection<Entity> entities = loc.getWorld().getNearbyEntities(loc, radius, radius, radius);
        Entity shooter = source;
        if (source instanceof Projectile) {
            ProjectileSource src = ((Projectile) source).getShooter();
            if (src instanceof Entity) shooter = (Entity) src;
        }
        for (Entity victim : entities) {
            if (victim == shooter) {
                if (damageShooter)
                    handleDamage(source, victim, dmg, "Explosion");
                continue;
            }
            if (damageExceptions.contains(victim)) continue;
            if (checkCanDamage(source, victim, false))
                handleDamage(source, victim, dmg, "Explosion");
        }
        // destroy block
        if (destroyBlock) {
            Player ply = null;
            if (source instanceof Projectile) {
                ProjectileSource src = ((Projectile) source).getShooter();
                if (src instanceof Player) ply = (Player) src;
            }
            if (ply != null) {
                org.bukkit.block.Block blastCenterBlock = loc.getBlock();
                // subtract 1e-3 from radiusSqr to prevent annoying unnatural shape
                double radiusSqr = radius * radius - 1e-3;
                int radInt = (int) Math.ceil(radius);
                double distSqrX, distSqrY, distSqrZ;
                for (int xOffset = radInt * -1; xOffset <= radInt; xOffset ++) {
                    distSqrX = xOffset * xOffset;
                    for (int yOffset = radInt * -1; yOffset <= radInt; yOffset++) {
                        distSqrY = yOffset * yOffset;
                        if (distSqrX + distSqrY > radiusSqr) continue;
                        for (int zOffset = radInt * -1; zOffset <= radInt; zOffset++) {
                            distSqrZ = zOffset * zOffset;
                            if (distSqrX + distSqrY + distSqrZ > radiusSqr) continue;
                            org.bukkit.block.Block currBlock = blastCenterBlock.getRelative(xOffset, yOffset, zOffset);
                            GameplayHelper.playerBreakBlock(currBlock, ply);
                        }
                    }
                }
            }
        }
        // lingering explosion
        int delay = 5;
        if (ticksDuration > delay) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleEntityExplode(source, radius, damageExceptions, loc, ticksDuration - delay),
                    delay);
        }
    }

    public static class ProjectileShootInfo {
        public ProjectileSource shooter;
        public Location shootLoc;
        public Vector velocity;
        public HashMap<String, Double> attrMap;
        public HashMap<String, Object> properties;
        public String damageType, projectileName;
        public boolean arrowOrPotion;
        // constructors
        public ProjectileShootInfo(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String projectileName) {
            this(shooter, velocity, attrMap, getDamageType(shooter), projectileName);
        }
        public ProjectileShootInfo(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String damageType, String projectileName) {
            this(shooter,
                    (shooter instanceof LivingEntity) ? ((LivingEntity) shooter).getEyeLocation() : shooter.getLocation(),
                    velocity, attrMap, damageType, projectileName);
        }
        public ProjectileShootInfo(Entity shooter, Location shootLoc, Vector velocity, HashMap<String, Double> attrMap, String damageType, String projectileName) {
            this.shooter = null;
            if (shooter instanceof ProjectileSource) this.shooter = (ProjectileSource) shooter;
            this.shootLoc = shootLoc;
            this.velocity = velocity;
            this.attrMap = attrMap;
            this.properties = new HashMap<>(25);
            {
                ConfigurationSection section = TerrariaHelper.projectileConfig.getConfigurationSection(projectileName);
                if (section != null) {
                    String[] keys;
                    // ints
                    {
                        keys = new String[]{"autoTraceMethod", "bounce", "enemyInvincibilityFrame", "liveTime",
                                "noAutoTraceTicks", "noGravityTicks", "trailLingerTime", "penetration"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getInt(key));
                        }
                    }
                    // strings
                    {
                        keys = new String[]{"blockHitAction", "trailColor"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getString(key));
                        }
                    }
                    // doubles
                    {
                        keys = new String[]{"autoTraceAbility", "autoTraceRadius", "blastRadius", "bounceVelocityMulti",
                                "frictionFactor", "gravity", "maxSpeed", "projectileSize", "speedMultiPerTick"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getDouble(key));
                        }
                    }
                    // booleans
                    {
                        keys = new String[]{"autoTrace", "autoTraceSharpTurning", "blastDamageShooter",
                                "blastOnContactBlock", "blastOnContactEnemy", "bouncePenetrationBonded",
                                "canBeReflected", "isGrenade", "slowedByWater"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getBoolean(key));
                        }
                    }
                }
            }
            this.damageType = damageType;
            this.projectileName = projectileName;
            boolean arrowOrPotion = projectileName.endsWith("箭");
//            this.arrowOrPotion = TerrariaHelper.projectileConfig.getBoolean(projectileName + ".arrowOrPotion", arrowOrPotion);
            this.arrowOrPotion = false; // chlorophyte arrows glitch on client side
        }

    }
    public static Projectile spawnProjectile(ProjectileShootInfo shootInfo) {
        CraftWorld wld = (CraftWorld) shootInfo.shootLoc.getWorld();
        Projectile bukkitProjectile;
        if (shootInfo.arrowOrPotion) {
            TerrariaArrowProjectile entity = new TerrariaArrowProjectile(
                    shootInfo.shootLoc, shootInfo.velocity, shootInfo.projectileName);
            wld.addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
            bukkitProjectile = new CraftArrow(wld.getHandle().getServer(), entity);
        } else {
            TerrariaPotionProjectile entity = new TerrariaPotionProjectile(shootInfo);
            bukkitProjectile = new CraftSplashPotion(wld.getHandle().getServer(), entity);
        }
        return bukkitProjectile;
    }
    // the spawn projectile below are not as flexible comparing to the function above
    public static Projectile spawnProjectile(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String projectileName) {
        return spawnProjectile(shooter, velocity, attrMap, getDamageType(shooter), projectileName);
    }
    public static Projectile spawnProjectile(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String damageType, String projectileName) {
        Location shootLoc;
        if (shooter instanceof LivingEntity) shootLoc = ((LivingEntity) shooter).getEyeLocation();
        else shootLoc = shooter.getLocation();
        return spawnProjectile(shooter, shootLoc, velocity, attrMap, damageType, projectileName);
    }
    public static Projectile spawnProjectile(Entity shooter, Location shootLoc, Vector velocity, HashMap<String, Double> attrMap, String damageType, String projectileName) {
        ProjectileShootInfo shootInfo = new ProjectileShootInfo(shooter, shootLoc, velocity, attrMap, damageType, projectileName);
        return spawnProjectile(shootInfo);
    }
    public static Location helperAimEntity(Entity source, Entity target, AimHelperOptions aimHelperOption) {
        Location targetLoc;
        // setup target location
        if (target instanceof LivingEntity) targetLoc = ((LivingEntity) target).getEyeLocation();
        else targetLoc = target.getLocation();
        // estimate the distance that the entity will move
        double predictionIntensity = aimHelperOption.intensity;
        if (source != null && predictionIntensity > 1e-5) {
            double ticksOffset = 0;
            if (aimHelperOption.useTickOrSpeedEstimation) ticksOffset = aimHelperOption.ticksOffset;
            else if (aimHelperOption.projectileSpeed > 0.2) {
                double distance = targetLoc.distance(source.getLocation());
                ticksOffset = distance / aimHelperOption.projectileSpeed;
            }
            ticksOffset = Math.ceil(ticksOffset);
            ticksOffset *= aimHelperOption.intensity;
            // get the prediction vector
            Vector velocity;
            if (target instanceof Player) {
                Location lastLoc = (Location) EntityHelper.getMetadata(target, "currLocation").value();
                if (lastLoc.distanceSquared(target.getLocation()) < 1e-5)
                    lastLoc = (Location) EntityHelper.getMetadata(target, "lastLocation").value();
                velocity = target.getLocation().subtract(lastLoc).toVector();
            }
            else
                velocity = target.getVelocity();
            targetLoc.add(velocity.multiply(ticksOffset));
        }
        // random offset for projectiles
        double randomOffset = aimHelperOption.randomOffsetRadius;
        if (randomOffset > 1e-5) {
            double randomOffsetHalved = randomOffset / 2;
            targetLoc.add(Math.random() * randomOffset - randomOffsetHalved,
                    Math.random() * randomOffset - randomOffsetHalved,
                    Math.random() * randomOffset - randomOffsetHalved);
        }
        return targetLoc;
    }
    public static void handleSegmentsFollow(List<LivingEntity> segments, WormSegmentMovementOptions moveOption) {
        handleSegmentsFollow(segments, moveOption, 0);
    }
    public static void handleSegmentsFollow(List<LivingEntity> segments, WormSegmentMovementOptions moveOption, int startIndex) {
        for (int i = (startIndex + 1) ; i < segments.size(); i ++) {
            LivingEntity segmentCurrent = segments.get(i);
            if (segmentCurrent.getHealth() < 1e-5 || segmentCurrent.isDead())
                return;
            LivingEntity segmentLast = segments.get(i - 1);
            LivingEntity segmentNext = segments.get(Math.min(i + 1, segments.size()) - 1);
            Vector segDVec = segmentLast.getLocation().subtract(segmentNext.getLocation()).toVector().normalize();
            Vector followDir = segmentLast.getLocation().subtract(segmentCurrent.getLocation()).toVector().normalize();
            Vector dVec = segDVec.multiply(moveOption.straighteningMultiplier)
                    .add(followDir.multiply(moveOption.followingMultiplier));
            if (dVec.lengthSquared() > 1e-9) {
                dVec.normalize().multiply(moveOption.followDistance);
                Location targetLoc = segmentLast.getLocation().subtract(dVec);
                if (moveOption.velocityOrTeleport) {
                    Vector velocity = targetLoc.subtract(segmentCurrent.getLocation()).toVector();
                    segmentCurrent.setVelocity(velocity);
                } else {
                    segmentCurrent.teleport(targetLoc);
                }
                ((CraftLivingEntity) segmentCurrent).getHandle().yaw = (float) MathHelper.getVectorYaw( dVec );
            }
        }
    }
}
