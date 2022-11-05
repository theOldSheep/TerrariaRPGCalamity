package terraria.util;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MiscDisguise;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftSlime;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;

import java.util.*;
import java.util.logging.Level;

public class EntityHelper {
    // constants
    static YmlHelper.YmlSection effectConfig = YmlHelper.getFile("plugins/Data/setting.yml");
    // helper functions
    public static void initEntityMetadata(Entity entity) {
        setMetadata(entity, "effects", new HashMap<String, Integer>());
    }
    public static HashMap<String, Double> getAttrMap(Metadatable entity) {
        try {
            return (HashMap<String, Double>) getMetadata(entity, "attrMap").value();
        } catch (Exception e) {
            return null;
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
    public static void tweakAttribute(Entity entity, String key, String value, boolean addOrRemove) {
        if (key.equals("damageType")) {
            setMetadata(entity, "damageType", value);
            return;
        }
        double value_number = 1;
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
            HashMap<String, Double> attrMap = getAttrMap(entity);
            if (!attrMap.containsKey(key)) return;
            value_number = Double.parseDouble(value);
            if (key.equals("useTime")) value_number /= 2;
            switch (key) {
                case "damageTakenMulti":
                    if (addOrRemove)
                        attrMap.put(key, (double) attrMap.getOrDefault(key, 1d) / (2 - value_number));
                    else
                        attrMap.put(key, (double) attrMap.getOrDefault(key, 1d) * (2 - value_number));
                    break;
                case "ammoConsumptionRate":
                case "arrowConsumptionRate":
                    if (addOrRemove)
                        attrMap.put(key, (double) attrMap.getOrDefault(key, 1d) * value_number);
                    else
                        attrMap.put(key, (double) attrMap.getOrDefault(key, 1d) / value_number);
                    break;
                default:
                    if (addOrRemove)
                        attrMap.put(key, ((double) (attrMap.getOrDefault(key, 1d)) + value_number));
                    else
                        attrMap.put(key, ((double) (attrMap.getOrDefault(key, 1d)) - value_number));
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Generic Helper] error when parsing value as a number in tweakAttribute ", e);
        }
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
    public static HashMap<String, Integer> getEffectMap(Entity entity) {
        try {
            return (HashMap<String, Integer>) getMetadata(entity, "effects").value();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] getEffectMap", e);
        }
        return new HashMap<>();
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
    public static void tickEffect(Entity entity, String effect, int delay, double damagePerDelay) {
        try {
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            int timeRemaining = allEffects.getOrDefault(effect, 0) - delay;
            boolean shouldStop = false, removeEffectOnStop = true;
            // validate if the entity still needs ticking effect
            if (entity instanceof Player) {
                Player playerE = (Player) entity;
                if (!playerE.isOnline()) {
                    // offline: do not clear effect time
                    shouldStop = true;
                    removeEffectOnStop = false;
                }
                else if (playerE.getGameMode() != GameMode.SURVIVAL) {
                    // additionally, the effect time is being cleared if the player dies.
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
            Bukkit.broadcastMessage("Ticking Potion Effect " + effect + " for entity " + entity.getName());
            if (damagePerDelay > 0) {
                double damageMulti = 1;
                if (effect.equals("破晓")) damageMulti = getEffectLevel(effect, timeRemaining);
                handleDamage(entity, entity, damagePerDelay * damageMulti, "debuff_" + effect);
            }
            if (effect.equals("扭曲")) {
                World entityWorld = entity.getLocation().getWorld();
                Location targetLoc = entityWorld.getHighestBlockAt(entity.getLocation()).getLocation();
                targetLoc.add(0, 8 + MathHelper.xsin_degree(timeRemaining * 2.5) * 2, 0);
                Vector velocity = targetLoc.subtract(entity.getLocation()).toVector();
                velocity.multiply(1/6);
                entity.setVelocity(velocity);
                entity.setFallDistance(0);
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
                String attributesPath = "effects." + effect + ".attributes";
                Set<String> attributesTweaked =
                        effectConfig.getConfigurationSection(attributesPath).getKeys(false);
                for (String attr : attributesTweaked) {
                    tweakAttribute(entity, attr, effectConfig.getString(attributesPath + attr), false);
                }
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
                delay = effectConfig.getInt("effects." + effect + ".damageInterval", delay);
                damagePerDelay = effectConfig.getInt("effects." + effect + ".damage", 0);
                if (!(entity instanceof Player))
                    damagePerDelay = effectConfig.getInt("effects." + effect + ".damageMonster", damagePerDelay);
            }
            // tweak attrMap if the target is not a player
            String attributesPath = "effects." + effect + ".attributes";
            Set<String> attributesTweaked =
                    effectConfig.getConfigurationSection(attributesPath).getKeys(false);
            for (String attr : attributesTweaked) {
                tweakAttribute(entity, attr, effectConfig.getString(attributesPath + attr), true);
            }
            // register delayed task
            int finalDamagePerDelay = damagePerDelay;
            int finalDelay = delay;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> tickEffect(entity, effect, finalDelay, finalDamagePerDelay), delay);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] prepareTickEffect", e);
        }
    }
    public static void applyEffect(Entity entity, String effect, int durationTicks) {
        try {
            // returns if the entity is immune to this effect (i.e. debuff)
            if (!(entity instanceof Player)) {
                ArrayList<String> checkNode = new ArrayList<>();
                checkNode.add(getMetadata(entity, "motherType").asString());
                checkNode.add(GenericHelper.trimText(entity.getName()));
                for (String check : checkNode) {
                    List<String> immune = YmlHelper.getFile("plugins/Data/entities.yml").getStringList(check + ".buffImmune");
                    if (immune.contains(effect)) return;
                }
            } else {
                HashSet<String> accessories = PlayerHelper.getAccessories(entity);
                switch (effect) {
                    case "黑暗":
                        if (accessories.contains("蒙眼布")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "虚弱":
                        if (accessories.contains("维生素")) return;
                        if (accessories.contains("盔甲背带")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "破损护甲":
                        if (accessories.contains("盔甲抛光剂")) return;
                        if (accessories.contains("盔甲背带")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "流血":
                        if (accessories.contains("粘性绷带")) return;
                        if (accessories.contains("药用绷带")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "中毒":
                        if (accessories.contains("牛黄")) return;
                        if (accessories.contains("药用绷带")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "诅咒":
                        if (accessories.contains("邪眼")) return;
                        if (accessories.contains("反诅咒咒语")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "沉默":
                        if (accessories.contains("扩音器")) return;
                        if (accessories.contains("反诅咒咒语")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                    case "缓慢":
                        if (accessories.contains("快走时钟")) return;
                        if (accessories.contains("十字章护身符")) return;
                        if (accessories.contains("十字章护盾")) return;
                        break;
                }
            }
            // apply the buff
            PotionEffectType effectInflict = null;
            switch (effect) {
                case "阻塞":
                    effectInflict = PotionEffectType.BLINDNESS;
                    break;
                case "鱼鳃":
                    effectInflict = PotionEffectType.WATER_BREATHING;
                    break;
                case "黑曜石皮":
                    effectInflict = PotionEffectType.FIRE_RESISTANCE;
                    break;
                case "隐身":
                    effectInflict = PotionEffectType.INVISIBILITY;
            }
            if (effectInflict != null)
                ((LivingEntity) entity).addPotionEffect(new PotionEffect(effectInflict, durationTicks, 0), true);
            // tweak effect time of some special effects
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            int finalDurationTicks;
            int currentDurationTicks = allEffects.getOrDefault(effect, 0);
            switch (effect) {
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
            getEffectMap(entity).put(effect, finalDurationTicks);
            // prepare to start ticking effect if the entity does not have it yet
            if (currentDurationTicks == 0) prepareTickEffect(entity, effect);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Entity Helper] applyEffect", e);
        }
    }
    public static void handleDamage(Entity victim, Entity target, double damage, String damageCause) {
        // TODO
    }
}
