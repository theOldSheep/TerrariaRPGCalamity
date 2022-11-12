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
import terraria.entity.TerrariaPotionProjectile;
import terraria.gameplay.Event;

import java.util.*;
import java.util.logging.Level;

public class EntityHelper {
    // constants
    static final YmlHelper.YmlSection settingConfig = YmlHelper.getFile("plugins/Data/setting.yml");
    static final YmlHelper.YmlSection buffConfig = YmlHelper.getFile("plugins/Data/buff.yml");
    static final HashMap<String, Set<String>> buffInferior, buffSuperior;
    static {
        buffSuperior = new HashMap<>(50);
        buffInferior = new HashMap<>(50);
        ConfigurationSection conflictSection = buffConfig.getConfigurationSection("buffConflicts");
        Set<String> rules = conflictSection.getKeys(false);
        for (String rule : rules) {
            ConfigurationSection ruleSection = conflictSection.getConfigurationSection(rule);
            Set<String> specificBuffs = conflictSection.getKeys(false);
            for (String buff1 : specificBuffs) {
                int priority1 = ruleSection.getInt(buff1, 0);
                for (String buff2 : specificBuffs) {
                    if (buff1.equals(buff2)) continue;
                    int priority2 = ruleSection.getInt(buff2, 0);
                    // they have same priority, so they can both override each other
                    if (priority1 == priority2) {
                        if (priority1 != 0) {
                            buffInferior.getOrDefault(buff1, new HashSet<>(25)).add(buff2);
                            buffInferior.getOrDefault(buff2, new HashSet<>(25)).add(buff1);
                        }
                    } else {
                        if (priority1 > priority2) {
                            // buff2 can not be applied when buff1 is present, buff1 can override buff2
                            buffInferior.getOrDefault(buff1, new HashSet<>(25)).add(buff2);
                            buffSuperior.getOrDefault(buff2, new HashSet<>(25)).add(buff1);
                        } else {
                            // buff1 can not be applied when buff2 is present, buff2 can override buff1
                            buffSuperior.getOrDefault(buff1, new HashSet<>(25)).add(buff2);
                            buffInferior.getOrDefault(buff2, new HashSet<>(25)).add(buff1);
                        }
                    }
                }
            }
        }
    }
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
            switch (key) {
                case "useTime":
                    value_number /= 2;
                    break;
                case "damageTakenMulti":
                case "arrowConsumptionRate":
                case "ammoConsumptionRate":
                    value_number = 1 + value_number;
                    break;
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
                    if (addOrRemove)
                        attrMap.put(key, (attrMap.getOrDefault(key, 1d)) + value_number);
                    else
                        attrMap.put(key, (attrMap.getOrDefault(key, 1d)) - value_number);
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
                if (buffConfig.contains(attributesPath)) {
                    Set<String> attributesTweaked =
                            buffConfig.getConfigurationSection(attributesPath).getKeys(false);
                    for (String attr : attributesTweaked) {
                        tweakAttribute(entity, attr, buffConfig.getString(attributesPath + attr), false);
                    }
                }
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
                delay = buffConfig.getInt("effects." + effect + ".damageInterval", delay);
                damagePerDelay = buffConfig.getInt("effects." + effect + ".damage", 0);
                if (!(entity instanceof Player))
                    damagePerDelay = buffConfig.getInt("effects." + effect + ".damageMonster", damagePerDelay);
            }
            // tweak attrMap if the target is not a player
            String attributesPath = "effects." + effect + ".attributes";
            if (buffConfig.contains(attributesPath)) {
                Set<String> attributesTweaked =
                        buffConfig.getConfigurationSection(attributesPath).getKeys(false);
                for (String attr : attributesTweaked) {
                    tweakAttribute(entity, attr, buffConfig.getString(attributesPath + attr), true);
                }
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
            // if the buff is not in config, do not do anything
            if (!buffConfig.contains("effects." + effect)) return;
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
            for (PotionEffectType effectInflict : getVanillaEffectInflict(effect))
                ((LivingEntity) entity).addPotionEffect(new PotionEffect(effectInflict, durationTicks, 0), true);
            // tweak effect time of some special effects
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            // does nothing if the target has a superior effect, otherwise tweak the inferior effects
            for (String effectSuperior : buffSuperior.getOrDefault(effect, new HashSet<>()))
                if (allEffects.containsKey(effectSuperior)) return;
            for (String effectInferior : buffInferior.getOrDefault(effect, new HashSet<>()))
                if (allEffects.containsKey(effectInferior)) allEffects.put(effectInferior, 0);
            // tweak duration of special effects
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
        switch (damageCause) {
            case "fire":
            case "burning":
            case "debuff_咒火":
            case "debuff_霜火":
            case "debuff_破晓":
                dm = "<victim> 没能及时把火扑灭";
                break;
            case "suffocation":
            case "void":
                dm = "<victim> 没法呼吸";
                break;
            case "debuff_中毒":
            case "debuff_剧毒":
                dm = "<victim> 没找到解药";
                break;
            case "恐惧":
                dm = "<victim> 被恐惧吞没...";
                break;
            default:
                List<String> deathMessages;
                if (settingConfig.contains("deathMessages." + damageCause)) {
                    deathMessages = settingConfig.getStringList("deathMessages." + damageCause);
                } else {
                    deathMessages = settingConfig.getStringList("deathMessages.Generic");
                }
                dm = deathMessages.get((int) (Math.random() * deathMessages.size()));
                if (killer != null) {
                    dm += "，凶手是" + killer;
                }
        }
        dm.replaceAll("<victim>", v.getName());
        Bukkit.broadcastMessage("§4" + dm);
    }
    public static boolean entityDamageEvent(Entity d, Entity dPly, LivingEntity v, LivingEntity damageTaker, double dmg, String damageCause) {
        if (d == null) return true;
        String nameV = GenericHelper.trimText(v.getName());
        Entity minion = d;
        // special minion behaviour
        if (minion instanceof Projectile) {
            ProjectileSource projSrc = ((Projectile) minion).getShooter();
            if (projSrc instanceof Entity) minion = (Entity) projSrc;
        }
        if (minion.getScoreboardTags().contains("isMinion")) {
            if (v.getScoreboardTags().contains("暗黑收割")) {
                v.removeScoreboardTag("暗黑收割");
                handleEntityExplode(d, null, v.getEyeLocation());
            }
        }
        // special victim behaviour
        switch (nameV) {
            case "史莱姆王": {
                if (dPly instanceof Player) {
                    MonsterHelper.spawnMob("史莱姆", v.getLocation(), (Player) dPly);
                    if (Math.random() < 0.5) MonsterHelper.spawnMob("尖刺史莱姆", v.getLocation(), (Player) dPly);
                    if (Math.random() < 0.25) MonsterHelper.spawnMob("尖刺史莱姆", v.getLocation(), (Player) dPly);
                }
                break;
            }
            case "毁灭者": {
                if (dPly instanceof Player) {
                    if (v.getScoreboardTags().contains("hasProbe") && Math.random() < 0.25) {
                        v.setCustomName("毁灭者" + ChatColor.COLOR_CHAR + "4");
                        v.removeScoreboardTag("hasProbe");
                        MonsterHelper.spawnMob("探测怪", v.getLocation(), (Player) dPly);
                    }
                }
                break;
            }
            case "石巨人头": {
                if (dmg >= v.getHealth()) {
                    v.addScoreboardTag("noDamage");
                    ArrayList<Entity> bossParts = BossHelper.bossMap.get("石巨人");
                    if (bossParts != null) {
                        bossParts.get(0).removeScoreboardTag("noDamage");
                        return false;
                    }
                }
                break;
            }
            case "月球领主手":
            case "月球领主": {
                if (dmg >= v.getHealth()) {
                    v.addScoreboardTag("noDamage");
                    v.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1);
                    return false;
                }
                break;
            }
            case "蜥蜴人": {
                double healthThreshold = v.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() / 2;
                if (v.getHealth() > healthThreshold) {
                    if ((v.getHealth() - dmg) <= healthThreshold) {
                        HashMap<String, Double> attrMap = getAttrMap(v);
                        attrMap.put("damageMulti", 1.5d);
                        attrMap.put("defenceMulti", 1.5d);
                        attrMap.put("knockbackResistance", 1d);
                    }
                }
                break;
            }
            case "胡桃夹士": {
                double healthThreshold = v.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() / 2;
                if (v.getHealth() > healthThreshold) {
                    if ((v.getHealth() - dmg) <= healthThreshold) {
                        HashMap<String, Double> attrMap = getAttrMap(v);
                        attrMap.put("damageMulti", 1.25d);
                        attrMap.put("defenceMulti", 1.5d);
                        attrMap.put("knockbackResistance", 0.91d);
                    }
                }
                break;
            }
        }
        // player being damaged
        if (v instanceof Player) {
            Player vPly = (Player) v;
            switch (d.getName()) {
                case "水螺旋": {
                    // phrase 2 only
                    if (d.getScoreboardTags().contains("isMonster")) {
                        d.remove();
                        ArrayList<Entity> bossList = BossHelper.bossMap.get("猪鲨公爵");
                        if (bossList != null) {
                            Entity dukeFishron = bossList.get(0);
                            // TODO: spawn a tornados
                        }
                        return false;
                    }
                    break;
                }
                case "吮脑怪": {
                    setMetadata(d, "suckTarget", v);
                    break;
                }
            }
            HashSet<String> accessories = (HashSet<String>) getMetadata(v, "accessory").value();
            HashMap<String, Double> attrMap = getAttrMap(v);
            boolean hasMagicCuff = accessories.contains("魔法手铐") || accessories.contains("天界手铐");
            if (hasMagicCuff) {
                int recovery = (int) Math.max(1, Math.floor(dmg / 4));
                PlayerHelper.restoreMana(vPly, recovery);
            }
            if (getMetadata(vPly, "armorSet").asString().equals("耀斑套装") && damageCause.equals("Melee")) {
                handleDamage(d, damageTaker, Math.min(Math.max(dmg, 300), 1500), "Thorn");
            }
        }
        // thorn effect
        HashMap<String, Integer> victimEffects = getEffectMap(v);
        if (victimEffects.containsKey("荆棘") && damageCause.equals("Melee")) {
            handleDamage(d, damageTaker, Math.min(Math.max(dmg / 3, 25), 500), "Thorn");
        }
        if (v.getScoreboardTags().contains("destroyOnDamage")) {
            v.remove();
            return false;
        }
        if (dPly instanceof Player && damageCause.equals("Magic")) {
            PlayerHelper.playerSpectreArmor((Player) dPly, v, dmg);
        }
        return true;
    }
    public static void handleDeath(Entity v, Entity dPly, Entity d, String damageCause) {
        if (v instanceof Player) {
            Player vPly = (Player) v;
            vPly.setVelocity(new Vector());
            int respawnTime = 15;
            for (ArrayList<Entity> bossList : BossHelper.bossMap.values()) {
                HashMap<String, Double> targets = (HashMap<String, Double>) getMetadata(bossList.get(0), "targets").value();
                if (targets.containsKey(vPly.getName())) {
                    respawnTime = Math.max(respawnTime, targets.size() * 15);
                }
            }
            int moneyDrop = (int) Math.floor(PlayerHelper.getMoney(vPly) / 100);
            moneyDrop = (int) Math.ceil(moneyDrop * 0.75);
            PlayerHelper.setMoney(vPly, PlayerHelper.getMoney(vPly) - moneyDrop * 100);
            GenericHelper.dropMoney(vPly.getEyeLocation(), moneyDrop);
            String moneyMsg = "";
            if (moneyDrop > 0) {
                int[] moneyConverted = GenericHelper.coinConversion(moneyDrop);
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
                    // TODO: spawn empress of light
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
                        Bukkit.broadcastMessage("§d§oo月亮末日慢慢逼近...");
                        // TODO: summon moon lord
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
                                // TODO: spawn slime king
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
                                                YmlHelper.getFile("plugins/PlayerData/" + ply.getName() + ".yml").set("bossDefeated.GoblinArmy", true);
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
                        // lava slime leaves lava at death
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
                                // TODO: spawn lunatic cultist
                            }
                            break;
                        }
                    }
                }
                if (dPly instanceof Player) {
                    Player dPlayer = (Player) dPly;
                    // TODO: dungeon
                    if (v.getLocation().getBlock().getBiome().equals(Biome.FOREST_HILLS)) {
                        if (PlayerHelper.hasDefeated(dPlayer, "plantera") && Math.random() < 0.125)
                            MonsterHelper.spawnMob("地牢幽魂", v.getLocation(), dPlayer);
                    }
                    // souls and shards
                    if (PlayerHelper.hasDefeated(dPlayer, "wall_of_flesh")) {
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
        return true;
    }
    public static void handleDamage(Entity damager, Entity target, double damage, String damageCause) {
        // TODO
    }
    public static void handleEntityExplode(Entity source, Entity damageException, Location loc) {
        // TODO
    }
    public static Projectile spawnProjectile(Location loc, Vector velocity, String projectileName, ProjectileSource src) {
        TerrariaPotionProjectile entity = new TerrariaPotionProjectile(loc, TerrariaPotionProjectile.generateItemStack(projectileName), velocity, projectileName);
        CraftWorld wld = (CraftWorld) loc.getWorld();
        wld.addEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
        Projectile bukkitProjectile = new CraftSplashPotion(wld.getHandle().getServer(), entity);
        bukkitProjectile.setShooter(src);
        return bukkitProjectile;
    }
}
