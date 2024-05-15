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
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.*;
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
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.HitEntityInfo;
import terraria.entity.projectile.TerrariaPotionProjectile;
import terraria.gameplay.EventAndTime;

import java.util.*;
import java.util.logging.Level;

public class EntityHelper {
    // constants
    static HashMap<String, Set<String>> buffInferior, buffSuperior;
    public static final HashMap<String, DamageType> DAMAGE_TYPE_INTERNAL_NAME_MAPPING = new HashMap<>(30);
    private static final HashMap<String, MetadataName> METADATA_NAME_MAPPING = new HashMap<>();
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
        TerrariaHelper.LOGGER.info("[Entity Helper] buffSuperior: " + buffSuperior);
        TerrariaHelper.LOGGER.info("[Entity Helper] buffInferior: " + buffInferior);
    }
    public static class AimHelperOptions {
        double projectileGravity = 0d, projectileSpeed = 0d, projectileSpeedMax = 99d, projectileSpeedMulti = 1d,
                intensity = 1d, randomOffsetRadius = 0d, ticksTotal = 0, ticksMonsterExtra = 0;
        boolean useAcceleration = false, useTickOrSpeedEstimation = false;
        int epoch = 5, noGravityTicks = 5;
        Vector accelerationOffset = new Vector();
        // constructor with default values
        public AimHelperOptions() {
            super();
        }
        public AimHelperOptions(String projectileType) {
            super();
            ConfigurationSection projConfSec = TerrariaHelper.projectileConfig.getConfigurationSection(projectileType);
            if (projConfSec == null) {
                setProjectileSpeedMax(99d);
                setProjectileSpeedMulti(1d);
                setProjectileGravity(0.05);
                setNoGravityTicks(5);
            }
            else {
                setProjectileSpeedMax(projConfSec.getDouble("maxSpeed", 99d));
                setProjectileSpeedMulti(projConfSec.getDouble("speedMultiPerTick", 1d));
                setProjectileGravity(projConfSec.getDouble("gravity", 0.05));
                setNoGravityTicks(projConfSec.getInt("noGravityTicks", 5));
            }
        }
        public AimHelperOptions setTicksTotal(double ticksTotal) {
            this.ticksTotal = ticksTotal;
            return this;
        }
        public AimHelperOptions setTicksMonsterExtra(double ticksMonsterExtra) {
            this.ticksMonsterExtra = ticksMonsterExtra;
            return this;
        }
        public AimHelperOptions setProjectileGravity(double projectileGravity) {
            this.projectileGravity = projectileGravity;
            return this;
        }
        public AimHelperOptions setProjectileSpeed(double projectileSpeed) {
            this.projectileSpeed = projectileSpeed;
            return this;
        }
        public AimHelperOptions setProjectileSpeedMax(double projectileSpeedMax) {
            this.projectileSpeedMax = projectileSpeedMax;
            return this;
        }
        public AimHelperOptions setProjectileSpeedMulti(double projectileSpeedMulti) {
            this.projectileSpeedMulti = projectileSpeedMulti;
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
        public AimHelperOptions setEpoch(int epoch) {
            this.epoch = epoch;
            return this;
        }
        public AimHelperOptions setNoGravityTicks(int noGravityTicks) {
            this.noGravityTicks = noGravityTicks;
            return this;
        }
        public AimHelperOptions setAccelerationMode(boolean useAcceleration) {
            this.useAcceleration = useAcceleration;
            return this;
        }
        public AimHelperOptions setAimMode(boolean useTickOrSpeedEstimation) {
            this.useTickOrSpeedEstimation = useTickOrSpeedEstimation;
            return this;
        }
        public AimHelperOptions setAccOffset(Vector accelerationOffset) {
            this.accelerationOffset = accelerationOffset;
            return this;
        }
        public AimHelperOptions addAccOffset(Vector accelerationOffset) {
            this.accelerationOffset.add(accelerationOffset);
            return this;
        }
        public AimHelperOptions subtractAccOffset(Vector accelerationOffset) {
            this.accelerationOffset.subtract(accelerationOffset);
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
    public enum MetadataName {
        ACCESSORIES("accessory"),
        ACCESSORIES_FLIGHT_BACKUP("accessoryThrust"),
        ACCESSORIES_LIST("accessoryList"),
        ARMOR_SET("armorSet"),
        ATTRIBUTE_MAP("attrMap"),
        BLOCK_BREAK_PROGRESS("breakProgress"),
        BOSS_BAR("bossbar"),
        BOSS_TARGET_MAP("targets"),
        BOSS_TYPE("bossType"),
        BUFF_IMMUNE("buffImmune"),
        BUFF_INFLICT("buffInflict"),
        CALAMITAS_PROJECTILE_TICKS_LIVED("projectileTicksLive"),
        CALAMITAS_PROJECTILE_ORIGINAL("projectileOriginal"),
        CELESTIAL_PILLAR_SHIELD("shield"),
        DAMAGE_SOURCE("damageSourcePlayer"),
        DAMAGE_TAKER("damageTaker"),
        DAMAGE_TYPE("damageType"),
        DYNAMIC_DAMAGE_REDUCTION("dynamicDR"),
        EFFECTS("effects"),
        ENTITY_CURRENT_VELOCITY("eCurrVel"),
        ENTITY_LAST_VELOCITY("eLastVel"),
        HEALTH_LOCKED_AT_AMOUNT("healthLock"),
        INVULNERABILITY_TICK_EDITION_MAP("ivtEdiMap"),
        LAST_ADAMANTITE_PARTICLE_TYPE("APAType"),
        KNOCKBACK_SLOW_FACTOR("kbFactor"),
        KILL_CONTRIBUTE_EVENT_PROGRESS("killProgress"),
        MINION_WHIP_BONUS_CRIT("minionWhipBonusCrit"),
        MINION_WHIP_BONUS_DAMAGE("minionWhipBonusDamage"),
        MONSTER_PARENT_TYPE("parentType"),
        NPC_FIRST_SELL_INDEX("firstSell"),
        NPC_GUI_VIEWERS("GUIViewers"),
        PLAYER_AIR("playerAir"),
        PLAYER_BIOME("playerBiome"),
        PLAYER_CRAFTING_RECIPE_INDEX("recipeNumber"),
        PLAYER_CRAFTING_STATION("craftingStation"),
        PLAYER_CURRENT_LOCATION("currLocation"),
        PLAYER_DASH_DIRECTION("chargeDir"),
        PLAYER_DASH_KEY_PRESSED_MS("chargeDirLastPressed"),
        PLAYER_BIOME_BLADE_SPIN_PITCH("spinPitch"),
        PLAYER_BUFF_INFLICT("effectInflict"),
        PLAYER_EXOSKELETON("plyAresESklt"),
        PLAYER_FORCED_BACKGROUND("forceBackground"),
        PLAYER_FORCED_BGM("forceBGM"),
        PLAYER_GRAPPLING_HOOKS("hooks"),
        PLAYER_GRAPPLING_HOOK_COLOR("color"),
        PLAYER_GRAPPLING_HOOK_ITEM("grapplingHookItem"),
        PLAYER_HEALTH_TIER("healthTier"),
        PLAYER_INTERNAL_ITEM_START_USE_CD("useCDInternal"),
        PLAYER_INTERNAL_LAST_ITEM_START_USE_CD("useCDInternalLast"),
        PLAYER_INVENTORIES("inventories"),
        PLAYER_ITEM_SWING_AMOUNT("swingAmount"),
        PLAYER_KEYS_PRESSED("keysPressed"),
        PLAYER_LAST_BACKGROUND("lastBackground"),
        PLAYER_LAST_BGM("lastBGM"),
        PLAYER_LAST_BGM_TIME("lastBGMTime"),
        PLAYER_LAST_LOCATION("lastLocation"),
        PLAYER_MANA_REGEN_DELAY("manaRegenDelay"),
        PLAYER_MANA_REGEN_COUNTER("manaRegenCounter"),
        PLAYER_MANA_TIER("manaTier"),
        PLAYER_MINION_LIST("minions"),
        PLAYER_MINION_WHIP_FOCUS("minionWhipFocus"),
        PLAYER_MONSTER_SPAWNED_AMOUNT("mobAmount"),
        PLAYER_NEG_REGEN_CAUSE("negRegenSrc"),
        PLAYER_NEXT_MINION_INDEX("nextMinionIndex"),
        PLAYER_NEXT_SENTRY_INDEX("nextSentryIndex"),
        PLAYER_NPC_INTERACTING("NPCViewing"),
        PLAYER_SECOND_LAST_LOCATION("secondLastLocation"),
        PLAYER_SENTRY_LIST("sentries"),
        PLAYER_TARGET_LOC_CACHE("targetLocCache"),
        PLAYER_TEAM("team"),
        PLAYER_TELEPORT_TARGET("teleportTarget"),
        PLAYER_THRUST_INDEX("thrustIndex"),
        PLAYER_THRUST_PROGRESS("thrustProgress"),
        PLAYER_TRASH_ITEMS("trashItems"),
        PLAYER_VELOCITY("plyVel"),
        PLAYER_VELOCITY_MULTI("plyVelMulti"),
        PROJECTILE_BOUNCE_LEFT("bounce"),
        PROJECTILE_DESTROY_REASON("destroyReason"),
        PROJECTILE_PENETRATION_LEFT("penetration"),
        PROJECTILE_ENTITIES_COLLIDED("collided"),
        REGEN_TIME("regenTime"),
        RESPAWN_COUNTDOWN("respawnCD"),
        SPAWN_IN_EVENT("spawnEvent"),
        SUCK_TARGET("suckTarget"),
        ;
        // fields
        String metadataName;
        // constructors
        MetadataName(String metadataName) {
            this.metadataName = metadataName;
            // test for collision
            if (METADATA_NAME_MAPPING.containsKey(metadataName)) {
                TerrariaHelper.LOGGER.log(
                        Level.SEVERE, "Metadata Name Collision: " + METADATA_NAME_MAPPING +
                                " between " + METADATA_NAME_MAPPING.get(metadataName) + " and " + this);
            }
            METADATA_NAME_MAPPING.put(metadataName, this);
        }

        @Override
        public String toString() {
            return metadataName;
        }
    }
    public enum DamageReason {
        BLOCK_EXPLOSION(false, DamageType.BLOCK_EXPLOSION),
        BOSS_ANGRY(true, null),
        DAAWNLIGHT(true, null),
        DIRECT_DAMAGE(true, null),
        DEBUFF(false, DamageType.DEBUFF),
        DROWNING(false, DamageType.DROWNING),
        FALL(false, DamageType.FALL),
        LAVA(false, DamageType.LAVA),
        PROJECTILE(true, null),
        EXPLOSION(true, null),
        FEAR(true, null),
        THORN(true, DamageType.THORN),
        SPECTRE(true, DamageType.SPECTRE),
        SUFFOCATION(false, DamageType.SUFFOCATION);
        // fields
        boolean isDirectDamage = false;
        DamageType damageType = null;
        // constructors
        DamageReason(boolean isDirectDamage, DamageType damageType) {
            this.isDirectDamage = isDirectDamage;
            this.damageType = damageType;
        }
        // getters
        boolean isDirectDamage() {
            return isDirectDamage;
        }
        DamageType getDamageType() {
            return damageType;
        }
    }
    public enum DamageType {
        ARROW("Arrow"),
        BLOCK_EXPLOSION("BlockExplosion"),
        BULLET("Bullet"),
        DEBUFF("Debuff"),
        DROWNING("Drowning"),
        FALL("Fall"),
        LAVA("Lava"),
        MAGIC("Magic"),
        MELEE("Melee"),
        NEGATIVE_REGEN("NegativeRegen"),
        ROCKET("Rocket"),
        SPECTRE("Spectre"),
        SUFFOCATION("Suffocation"),
        SUMMON("Summon"),
        THORN("Thorn"),
        TRUE_MELEE("TrueMelee");
        // field
        String internalName;
        // constructor
        DamageType(String internalName) {
            this.internalName = internalName;
            DAMAGE_TYPE_INTERNAL_NAME_MAPPING.put(internalName, this);
        }
        // getter
        @Override
        public String toString() {
            return internalName;
        }
    }
    // helper functions
    public static void initEntityMetadata(Entity entity) {
        // in this function call, potion effect map is initialized when needed
        getEffectMap(entity);

        setMetadata(entity, MetadataName.DAMAGE_TYPE, DamageType.MELEE);
        setMetadata(entity, MetadataName.BUFF_IMMUNE, new HashMap<String, Integer>());
    }
    public static DamageType getDamageType(Metadatable entity) {
        try {
            return (DamageType) getMetadata(entity, MetadataName.DAMAGE_TYPE).value();
        } catch (Exception e) {
            return DamageType.MELEE;
        }
    }
    public static void setDamageType(Metadatable entity, DamageType damageType) {
        if (damageType == null) setMetadata(entity, MetadataName.DAMAGE_TYPE, DamageType.MELEE);
        else setMetadata(entity, MetadataName.DAMAGE_TYPE, damageType);
    }
    public static HashMap<String, Double> getAttrMap(Metadatable entity) {
        try {
            MetadataValue mdv = getMetadata(entity, MetadataName.ATTRIBUTE_MAP);
            return mdv == null ? new HashMap<>(0) : (HashMap<String, Double>) mdv.value();
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
    public static MetadataValue getMetadata(Metadatable owner, MetadataName metadataName) {
        return getMetadata(owner, metadataName.toString());
    }
    @Deprecated
    public static void setMetadata(Metadatable owner, String key, Object value) {
        if (value == null)
            owner.removeMetadata(key, TerrariaHelper.getInstance());
        else
            owner.setMetadata(key, new FixedMetadataValue(TerrariaHelper.getInstance(), value));
    }
    public static void setMetadata(Metadatable owner, MetadataName key, Object value) {
        setMetadata(owner, key.toString(), value);
    }
    // helper functions for tweaking attribute
    public static void tweakAttribute(Entity entity, String key, String value, boolean addOrRemove) {
        if (key.equals(MetadataName.DAMAGE_TYPE.toString())) {
            if (addOrRemove) setMetadata(entity, MetadataName.DAMAGE_TYPE,
                    DAMAGE_TYPE_INTERNAL_NAME_MAPPING.getOrDefault(value, DamageType.MELEE));
            return;
        }
        try {
            if (key.startsWith(MetadataName.BUFF_INFLICT.toString())) {
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
            else if (key.equals(MetadataName.BUFF_IMMUNE.toString())) {
                Map<String, Integer> buffImmune = (Map<String, Integer>) getMetadata(entity, MetadataName.BUFF_IMMUNE).value();
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
    // the two below is used in update player attribute
    public static void tweakAttribute(Entity entity, HashMap<String, Double> attrMap, String key, String value, boolean addOrRemove) {
        if (key.equals(MetadataName.DAMAGE_TYPE.toString())) {
            if (addOrRemove) setMetadata(entity, MetadataName.DAMAGE_TYPE,
                    DAMAGE_TYPE_INTERNAL_NAME_MAPPING.getOrDefault(value, DamageType.MELEE));
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
                Map<String, Integer> buffImmune = (Map<String, Integer>) getMetadata(entity, MetadataName.BUFF_IMMUNE).value();
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
            TerrariaHelper.LOGGER.log(Level.SEVERE,
                    "[ENTITY HELPER: MAKE TARGET]: error occurred while making " + entity + " targeting " + target,
                    e);
        }
    }
    public static void damageCD(Collection<Entity> dmgCdList, Entity entityToAdd, int cdTick) {
        dmgCdList.add(entityToAdd);
        Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                () -> dmgCdList.remove(entityToAdd), cdTick);
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
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] getEffectMap", e);
        }
        return false;
    }
    public static LinkedHashMap<String, Integer> getEffectMap(Entity entity) {
        MetadataValue mdv = getMetadata(entity, MetadataName.EFFECTS);
        if (mdv == null) {
            LinkedHashMap<String, Integer> effectMap = new LinkedHashMap<>();
            setMetadata(entity, MetadataName.EFFECTS, effectMap);
            return effectMap;
        }
        return (LinkedHashMap<String, Integer>) mdv.value();
    }
    public static int getEffectLevelMax(String effect) {
        switch (effect) {
            case "破晓":
                return 8;
            case "伤害星云":
            case "生命星云":
            case "魔力星云":
                return 3;
            case "解离":
                return 10;
            default:
                return 1;
        }
    }
    public static int getEffectLevelDuration(String effect) {
        switch (effect) {
            case "破晓":
            case "解离":
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
            case "瘟疫狂暴":
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
                break;
        }
        return result;
    }
    private static void tickEffect(Entity entity, String effect, int delay, double damagePerDelay) {
        try {
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            int timeRemaining = allEffects.getOrDefault(effect, 0);
            // some buff should not (solely) disappear over time, they disappear over some other criterion.
            switch (effect) {
                case "血肉图腾":
                    if (! PlayerHelper.getAccessories(entity).contains("血肉图腾"))
                        timeRemaining = -1;
                    break;
                case "血炎防御损毁":
                    PlayerHelper.heal((LivingEntity) entity, 2);
                    timeRemaining -= 2;
                    break;
                case "保护矩阵":
                    HashMap<String, Double> attrMap = getAttrMap(entity);
                    // 1 second = 20 ticks = 10 damage
                    int barrierLimit = (int) Math.round( attrMap.getOrDefault("barrierMax", 0d) ) * 2;
                    // discharge slowly if exceeds the current limit / out of charge
                    if (timeRemaining > barrierLimit || timeRemaining == 0)
                        timeRemaining -= delay * 2;
                    // recharge slowly if below the current limit AND not on recharge cool down
                    else if (timeRemaining < barrierLimit && (! allEffects.containsKey("保护矩阵充能") ) )
                        timeRemaining += delay * 2;
                    break;
                case "魔影激怒":
                    timeRemaining -= delay;
                    if (! PlayerHelper.getArmorSet((Player) entity).equals("魔影套装"))
                        timeRemaining = -1;
                    break;
                default:
                    timeRemaining -= delay;
            }
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
            if (((LivingEntity) entity).getHealth() < 1e-5 || entity.isDead()) shouldStop = true;
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
                // for stacking damage effects such as day-break, its damage should also be stacked
                double damageMulti = getEffectLevel(effect, timeRemaining);
                handleDamage(entity, entity, damagePerDelay * damageMulti, DamageReason.DEBUFF, effect);
            }
            switch (effect) {
                case "扭曲": {
                    if (entity instanceof Player) {
                        Entity mount = PlayerHelper.getMount((Player) entity);
                        Entity twistedEntity = mount == null ? entity : mount;
                        World entityWorld = twistedEntity.getWorld();
                        double targetLocY = entityWorld.getHighestBlockAt(twistedEntity.getLocation()).getLocation().getY();
                        targetLocY += 8 + MathHelper.xsin_degree(timeRemaining * 2.5) * 2;
                        double velY = targetLocY - twistedEntity.getLocation().getY();
                        velY /= 6;
                        double maxVerticalSpeed = 0.5;
                        if (velY < -maxVerticalSpeed) {
                            velY = -maxVerticalSpeed;
                        } else if (velY > maxVerticalSpeed) {
                            velY = maxVerticalSpeed;
                        }
                        Vector velocity = getVelocity(twistedEntity);
                        velocity.setY(velY);
                        setVelocity(twistedEntity, velocity);
                        entity.setFallDistance(0);
                    }
                    break;
                }
                case "恐惧": {
                    if (BossHelper.bossMap.containsKey(BossHelper.BossType.WALL_OF_FLESH.msgName)) {
                        Entity damager = BossHelper.bossMap.get(BossHelper.BossType.WALL_OF_FLESH.msgName).get(0);
                        if (damager.getWorld() != entity.getWorld() ||
                                damager.getLocation().distanceSquared(entity.getLocation()) > 10000)
                            handleDamage(damager, entity, 114514, DamageReason.DEBUFF, effect);
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
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] tickEffect", e);
        }
    }
    private static void endTickEffect(Entity entity, String effect, HashMap<String, Integer> allEffects, boolean removeEffectOnStop) {
        try {
            String effectPath = "effects." + effect;
            ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(effectPath);
            // if the effect should be removed from metadata
            // this is to prevent removing effect from a player logging out
            if (removeEffectOnStop) {
                allEffects.remove(effect);
                // as the original effect is removed, apply subsequent effect if available
                String effectToApply = effectSection.getString("buffOnTimeout");
                if (effectToApply != null) {
                    applyEffect(entity, effectToApply, effectSection.getInt("buffOnTimeoutDuration", 200));
                }
            }
            // tweak attribute if the entity is alive
            if (entity instanceof Player)
                PlayerHelper.setupAttribute((Player) entity);
            else if (!entity.isDead()) {
                ConfigurationSection effectAttributeSection = effectSection.getConfigurationSection("attributes");
                tweakAllAttributes(entity, effectAttributeSection, false);
            }
            // remove the buff applied if the entity is a player
            if (entity instanceof Player) {
                for (PotionEffectType effectInflict : getVanillaEffectInflict(effect))
                    ((LivingEntity) entity).removePotionEffect(effectInflict);
            }
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] endTickEffect", e);
        }
    }
    public static void prepareTickEffect(Entity entity, String effect) {
        try {
            // setup constants
            int delay = 10, damagePerDelay = 0;
            switch (effect) {
                case "保护矩阵":
                case "扭曲":
                case "血炎防御损毁":
                    delay = 1;
                    break;
                default:
                    delay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damageInterval", delay);
                    damagePerDelay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damage", damagePerDelay);
                    if (!(entity instanceof Player))
                        damagePerDelay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damageMonster", damagePerDelay);
            }
            // tweak attrMap
            if (entity instanceof Player)
                PlayerHelper.setupAttribute((Player) entity);
            else {
                String attributesPath = "effects." + effect + ".attributes.";
                ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(attributesPath);
                tweakAllAttributes(entity, effectSection, true);
            }
            // register delayed task for ticking potion
            int finalDamagePerDelay = damagePerDelay;
            int finalDelay = delay;
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> tickEffect(entity, effect, finalDelay, finalDamagePerDelay), delay);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] prepareTickEffect", e);
        }
    }
    public static void applyEffect(Entity entity, String effect, int applyDurationTicks) {
        try {
            // if the buff is not in config, do not do anything
            if (!TerrariaHelper.buffConfig.contains("effects." + effect)) return;
            // returns if the entity is not applicable to this effect (e.g. a projectile)
            if (! (entity instanceof LivingEntity) ) return;
            // returns if the entity is immune to this effect (i.e. debuff)
            MetadataValue buffImmuneMetadata = getMetadata(entity, MetadataName.BUFF_IMMUNE);
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
                ((LivingEntity) entity).addPotionEffect(new PotionEffect(effectInflict, applyDurationTicks, 0), true);
            // tweak duration of some special effects
            int finalDurationTicks;
            int currentDurationTicks = allEffects.getOrDefault(effect, 0);
            switch (effect) {
                case "血炎防御损毁":
                case "血神之凋零":
                    finalDurationTicks = currentDurationTicks + applyDurationTicks;
                    break;
                case "魔力烧蚀":
                case "魔力熔蚀":
                case "魔力疾病":
                    finalDurationTicks = currentDurationTicks + applyDurationTicks;
                    if (finalDurationTicks > 400 && applyDurationTicks < 400) finalDurationTicks = 400;
                    break;
                default:
                    // if a buff has a cool down effect, do not refresh its duration.
                    if (TerrariaHelper.buffConfig.getString("effects." + effect + ".buffOnTimeout") != null) {
                        if (allEffects.containsKey(effect))
                            finalDurationTicks = Math.min(currentDurationTicks, applyDurationTicks);
                        else
                            finalDurationTicks = applyDurationTicks;
                    }
                    // for other buffs
                    else {
                        // check if this buff is stackable
                        int maxLevel = getEffectLevelMax(effect);
                        // for stackable buffs, attempt to add a stack
                        if (maxLevel > 1) {
                            int levelTime = getEffectLevelDuration(effect);
                            int currentLevel = getEffectLevel(effect, currentDurationTicks);
                            int applyLevel = getEffectLevel(effect, applyDurationTicks);
                            if (applyLevel > currentLevel)
                                finalDurationTicks = Math.min(currentLevel + 1, maxLevel) * levelTime;
                            else
                                finalDurationTicks = currentLevel * levelTime;
                        }
                        // otherwise, default to the maximum of current duration and applied duration
                        else
                            finalDurationTicks = Math.max(currentDurationTicks, applyDurationTicks);
                    }
            }
            // record effect info
            allEffects.put(effect, finalDurationTicks);
            // prepare to start ticking effect if the entity does not have it yet
            if (currentDurationTicks == 0) prepareTickEffect(entity, effect);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] applyEffect", e);
        }
    }
    private static void sendDeathMessage(Entity d, Entity v, DamageType damageType, String debuffType) {
        String dm = "";
        // special death message cases
        if (v instanceof Player) {
            Player plyV = (Player) v;
            if ( ItemHelper.splitItemName(plyV.getInventory().getItemInMainHand() )[1].equals("雷姆的复仇") )
                dm = "<victim>……是谁？";
            if ( PlayerHelper.getAccessories(plyV).contains("空灵护符") && Math.random() < 0.1 ) {
                String msg = "§4<victim>死了    <victim>死了".replaceAll("<victim>", v.getName());
                for (int i = 0; i < 3; i ++) {
                    Bukkit.broadcastMessage(msg);
                }
                return;
            }
        }
        if (dm.length() == 0) {
            // general msg
            String killer = null;
            if (d != null) {
                killer = d.getCustomName();
            }

            String deathMessageConfigDir = "deathMessages." + damageType;
            switch (damageType) {
                case DEBUFF:
                    deathMessageConfigDir = "deathMessages.Debuff_" + debuffType;
                    break;
                case NEGATIVE_REGEN: {
                    MetadataValue mdv = EntityHelper.getMetadata(v, EntityHelper.MetadataName.PLAYER_NEG_REGEN_CAUSE);
                    if (mdv != null) {
                        HashMap<String, Double> negRegenCause = (HashMap<String, Double>) mdv.value();
                        String randomizedCause = MathHelper.selectWeighedRandom(negRegenCause);
                        deathMessageConfigDir = "deathMessages.NegRegen_" + randomizedCause;
                    }
                    break;
                }
            }

            List<String> deathMessages;
            if (TerrariaHelper.settingConfig.contains(deathMessageConfigDir)) {
                deathMessages = TerrariaHelper.settingConfig.getStringList(deathMessageConfigDir);
            } else {
                deathMessages = TerrariaHelper.settingConfig.getStringList("deathMessages.Generic");
            }
            dm = deathMessages.get((int) (Math.random() * deathMessages.size()));
            if (killer != null && d != v) {
                dm += "，凶手是" + killer;
            }
        }
        dm = dm.replaceAll("<victim>", v.getName());
        Bukkit.broadcastMessage("§4" + dm);
    }
    public static boolean entityDamageEvent(Entity damager, Entity damageSource, LivingEntity victim, LivingEntity damageTaker, double dmg,
                                            DamageType damageType, DamageReason damageReason) {
        if (damager == null) return true;
        String nameV = GenericHelper.trimText(victim.getName());
        Entity minion = damager;
        // validate if the damage is direct (or more specifically, contact damage)
        boolean isContactDmg = false, isDirectDmg = false;
        switch (damageType) {
            case MELEE:
            case TRUE_MELEE:
                isContactDmg = true;
            case ARROW:
            case BULLET:
            case MAGIC:
            case SPECTRE:
            case ROCKET:
            case SUMMON:
                isDirectDmg = true;
        }
        // special minion behaviour
        if (minion instanceof Projectile) {
            ProjectileSource projSrc = ((Projectile) minion).getShooter();
            if (projSrc instanceof Entity) minion = (Entity) projSrc;
        }
        if (minion.getScoreboardTags().contains("isMinion")) {
            if (victim.getScoreboardTags().contains("暗黑收割")) {
                victim.removeScoreboardTag("暗黑收割");
                // prevent doubled damage on initial target due to the explosion
                ArrayList<Entity> damageExceptions = new ArrayList<>();
                damageExceptions.add(victim);
                handleEntityExplode(damager, 2, damageExceptions, victim.getEyeLocation());
            }
        }
        // special victim behaviour
        switch (nameV) {
            case "史莱姆王": {
                if (damageSource instanceof Player) {
                    // 100 slimes to spawn in total
                    int amountRemaining = (int) (100 * victim.getHealth() / victim.getMaxHealth());
                    int amountRemainingAfterDmg = (int) (100 * (victim.getHealth() - dmg) / victim.getMaxHealth());
                    int amountSpawn = amountRemaining - amountRemainingAfterDmg;
                    for (int i = 0; i < amountSpawn; i ++)
                        MonsterHelper.spawnMob(Math.random() < 0.2 ? "尖刺史莱姆" : "史莱姆",
                                victim.getLocation(), (Player) damageSource);
                }
                break;
            }
            case "毁灭者": {
                if (damageSource instanceof Player) {
                    if (victim.getScoreboardTags().contains("hasProbe") && Math.random() < 0.25) {
                        victim.setCustomName("毁灭者" + ChatColor.COLOR_CHAR + "4");
                        victim.removeScoreboardTag("hasProbe");
                        MonsterHelper.spawnMob("探测怪", victim.getLocation(), (Player) damageSource);
                    }
                }
                break;
            }
            case "石巨人头": {
                if (dmg >= victim.getHealth()) {
                    victim.addScoreboardTag("noDamage");
                    ArrayList<LivingEntity> bossParts = BossHelper.bossMap.get(BossHelper.BossType.GOLEM.msgName);
                    if (bossParts != null) {
                        bossParts.get(0).removeScoreboardTag("noDamage");
                        return false;
                    }
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

        // thorn effect
        HashMap<String, Integer> victimEffects = getEffectMap(victim);
        if (victimEffects.containsKey("荆棘") && isContactDmg) {
            handleDamage(damageTaker, damager, Math.min(Math.max(dmg / 3, 25), 500), DamageReason.THORN);
        }
        if (victim.getScoreboardTags().contains("destroyOnDamage")) {
            victim.remove();
            return false;
        }

        // player being damaged
        if (victim instanceof Player) {
            Player vPly = (Player) victim;
            // prevent damage and damage handling if in invulnerability
            if (hasEffect(vPly, "始源林海无敌") )
                return false;
            // health regen time reset
            setMetadata(victim, MetadataName.REGEN_TIME, 0);
            // special damager
            switch (damager.getName()) {
                case "水螺旋": {
                    damager.remove();
                    return false;
                }
                case "吮脑怪": {
                    victim.addPassenger(damager);
                    break;
                }
            }
            // accessories
            HashSet<String> accessories = PlayerHelper.getAccessories(victim);
            HashMap<String, Double> victimAttrMap = getAttrMap(victim);
            for (String accessory : accessories) {
                switch (accessory) {
                    // mana recovery on damage
                    case "魔法手铐":
                    case "天界手铐": {
                        int recovery = (int) Math.max(1, Math.floor(dmg / 4));
                        PlayerHelper.restoreMana(vPly, recovery);
                        break;
                    }
                    // defence-damage style damage reduction POST damage calculation
                    case "血炎晶核": {
                        if (isDirectDmg && dmg >= 50d) {
                            int duration = (int) Math.min(victimAttrMap.getOrDefault("defence", 0d), dmg);
                            applyEffect(victim, "血炎防御损毁", duration);
                        }
                        break;
                    }
                    case "血神圣杯": {
                        if (isDirectDmg && dmg >= 100d) {
                            int duration = (int) Math.min(victimAttrMap.getOrDefault("defence", 0d), dmg * 0.75);
                            applyEffect(victim, "血炎防御损毁", duration);
                            double recovery = Math.min(vPly.getMaxHealth() - vPly.getHealth(), dmg * 0.95);
                            PlayerHelper.heal(vPly, recovery);
                            applyEffect(victim, "血神之凋零", (int) Math.ceil(recovery / 10));
                        }
                        break;
                    }
                    // "revive" (heal by factor of 1.75, minimum of 200)
                    case "星云之核": {
                        if (dmg >= vPly.getHealth() && (! victimEffects.containsKey("星云之核冷却")) ) {
                            // cool down for 180 seconds (3600 ticks)
                            applyEffect(vPly, "星云之核冷却", 3600);
                            // heal for the damage amount
                            PlayerHelper.heal(vPly, Math.max(dmg * 1.75, 200) );
                        }
                        break;
                    }
                }
            }
            // armor sets
            String victimPlayerArmorSet = PlayerHelper.getArmorSet(vPly);
            switch (victimPlayerArmorSet) {
                case "掠夺者坦克套装": {
                    if (isDirectDmg && Math.random() < 0.25)
                        applyEffect(damageTaker, "掠夺者之怒", 60);
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg, 100), 500), DamageReason.THORN);
                    }
                    break;
                }
                case "耀斑套装": {
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg * 1.5, 300), 1000), DamageReason.THORN);
                    }
                    break;
                }
                case "龙蒿近战套装": {
                    if (isDirectDmg && Math.random() < 0.25)
                        applyEffect(damageTaker, "生命涌流", 80);
                    break;
                }
                case "弑神者近战套装": {
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg * 2.5, 500), 2500), DamageReason.THORN);
                    }
                    break;
                }
                case "金源近战套装": {
                    if (isDirectDmg && Math.random() < 0.25)
                        applyEffect(damageTaker, "生命涌流", 80);
                    if (isContactDmg) {
                        handleDamage(damageTaker, damager, Math.min(Math.max(dmg * 2.5, 500), 2500), DamageReason.THORN);
                    }
                    break;
                }
                case "始源林海魔法套装":
                case "始源林海召唤套装":
                case "金源魔法套装":
                case "金源召唤套装": {
                    if (dmg >= vPly.getHealth() && (! victimEffects.containsKey("始源林海无敌冷却")) ) {
                        applyEffect(vPly, "始源林海无敌", 160);
                        return false;
                    }
                    break;
                }
                case "天钻套装": {
                    if (isDirectDmg) {
                        int[] removeIndexOrder = {0, 4, 5};
                        switch (getDamageType(vPly)) {
                            // ranged
                            case ARROW:
                            case BULLET:
                            case ROCKET:
                                removeIndexOrder[0] = 1;
                                break;
                            // magic
                            case MAGIC:
                                removeIndexOrder[0] = 2;
                                break;
                            // summon
                            case SUMMON:
                                removeIndexOrder[0] = 3;
                                break;
                        }
                        // remove ONE gem, is possible.
                        for (int removeIdx : removeIndexOrder) {
                            String gemToRemove = PlayerHelper.GEM_TECH_GEMS[removeIdx];
                            if (hasEffect(vPly, gemToRemove)) {
                                applyEffect(vPly, gemToRemove + "冷却", 600);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            // special item
            ItemStack plyTool = vPly.getEquipment().getItemInMainHand();
            switch (ItemHelper.splitItemName(plyTool)[1]) {
                case "赤陨霸龙弓": {
                    if (isDirectDmg)
                        setMetadata(vPly, MetadataName.PLAYER_ITEM_SWING_AMOUNT, 0);
                    break;
                }
            }
            // buff
            if (isDirectDmg) {
                if (hasEffect(vPly, "弑神者冲刺"))
                    Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                            () -> applyEffect(vPly, "神弑者之停息", 600), 5);
            }
        }
        // player damage other entity
        if (damageSource instanceof Player) {
            Player dPly = (Player) damageSource;
            String armorSet = PlayerHelper.getArmorSet(dPly);

            // life steal
            String lifeStealTempCD = "temp_lifeStealCD";
            // debuff and self-damage may not trigger life steal
            if (damager != victim && ! dPly.getScoreboardTags().contains(lifeStealTempCD)) {
                // cool down
                EntityHelper.handleEntityTemporaryScoreboardTag(dPly, lifeStealTempCD, 1);

                HashMap<String, Double> plyAttrMap = getAttrMap(dPly);
                double lifeStealFactor = plyAttrMap.getOrDefault("lifeSteal", 0d);
                if (lifeStealFactor > 1e-9) {
                    double healAmount = dmg * lifeStealFactor;
                    healAmount = Math.min(healAmount, dPly.getMaxHealth() * 0.05);
                    PlayerHelper.heal(dPly, healAmount);
                }
            }

            // generic armor set (damage-type restricted armor set properties are handled below)
            if (isDirectDmg) {
                switch (armorSet) {
                    case "血炎近战套装":
                    case "血炎远程套装":
                    case "血炎魔法套装":
                    case "血炎召唤套装":
                    case "金源近战套装":
                    case "金源远程套装": {
                        String coolDownTag = "temp_bloodFlareHeart";
                        if (!dPly.getScoreboardTags().contains(coolDownTag)) {
                            // cool down (5 seconds)
                            handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 100);
                            // drop a heart
                            dropHeart(victim.getLocation());
                        }
                        break;
                    }
                    case "始源林海魔法套装":
                    case "始源林海召唤套装": {
                        String coolDownTag = "temp_silvaHealing";
                        if (!dPly.getScoreboardTags().contains(coolDownTag)) {
                            // cool down (1 second)
                            handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 20);
                            PlayerHelper.createSpectreProjectile(dPly, victim.getLocation().add(0, 1.5d, 0),
                                    10, true, "96|169|92");
                        }
                        break;
                    }
                    case "金源魔法套装":
                    case "金源召唤套装": {
                        // drop a heart
                        String coolDownTagHeart = "temp_bloodFlareHeart";
                        if (!dPly.getScoreboardTags().contains(coolDownTagHeart)) {
                            // cool down (5 seconds)
                            handleEntityTemporaryScoreboardTag(dPly, coolDownTagHeart, 100);
                            dropHeart(victim.getLocation());
                        }
                        // healing orb
                        String coolDownTagHealingOrb = "temp_silvaHealing";
                        if (!dPly.getScoreboardTags().contains(coolDownTagHealingOrb)) {
                            // cool down (1 second)
                            handleEntityTemporaryScoreboardTag(dPly, coolDownTagHealingOrb, 20);
                            PlayerHelper.createSpectreProjectile(dPly, victim.getLocation().add(0, 1.5d, 0),
                                    10, true, "96|169|92");
                        }
                        break;
                    }
                }
            }
            // magic damage
            switch (damageType) {
                case MAGIC: {
                    if (! (damageReason == DamageReason.SPECTRE)) {
                        PlayerHelper.playerMagicArmorSet(dPly, victim, dmg);
                    }

                    switch (armorSet) {
                        case "始源林海魔法套装":
                        case "金源魔法套装": {
                            String coolDownTag = "temp_silvaMagicBlast";
                            if (! dPly.getScoreboardTags().contains(coolDownTag)) {
                                // cool down (5 second)
                                handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 100);
                                // explosion
                                HashMap<String, Double> explosionAttribute = (HashMap<String, Double>) getAttrMap(dPly).clone();
                                double explosionDmg = 1600 + explosionAttribute.getOrDefault("damage", 10d) * 0.6;
                                explosionAttribute.put("damage", Math.min(explosionDmg, 2800d));
                                spawnProjectile(dPly, victim.getEyeLocation(), new Vector(),
                                        explosionAttribute, DamageType.MAGIC, "始源林海爆炸");
                            }
                            break;
                        }
                    }
                    break;
                }
                case MELEE:
                case TRUE_MELEE: {
                    switch (armorSet) {
                        case "渊泉近战套装": {
                            String coolDownTag = "temp_hydroThermicFireball";
                            if (!dPly.getScoreboardTags().contains(coolDownTag)) {
                                // cool down
                                handleEntityTemporaryScoreboardTag(dPly, coolDownTag, 30);
                                // damage setup
                                HashMap<String, Double> fireballAttrMap = (HashMap<String, Double>) getAttrMap(damager).clone();
                                double fireballDmg = Math.min(90, fireballAttrMap.getOrDefault("damage", 100d) * 0.15);
                                fireballAttrMap.put("damage", fireballDmg);

                                String projType = "小熔岩火球";
                                double projSpd = 1.75;
                                Vector aimDir = ItemUseHelper.getPlayerAimDir(dPly, dPly.getEyeLocation(),
                                        projSpd, projType, false, 0);
                                double aimYaw = MathHelper.getVectorYaw(aimDir);
                                double aimPitch = MathHelper.getVectorPitch(aimDir);
                                // projectiles
                                for (int i = 0; i < 3; i++) {
                                    Vector projVel = MathHelper.vectorFromYawPitch_quick(
                                            aimYaw + Math.random() * 10 - 5, aimPitch + Math.random() * 10 - 5);
                                    projVel.multiply(projSpd);
                                    EntityHelper.spawnProjectile(dPly, projVel,
                                            fireballAttrMap, EntityHelper.DamageType.MELEE, projType);
                                }
                            }
                            break;
                        }
                        case "血炎近战套装":
                        case "金源近战套装": {
                            if (damageType == DamageType.TRUE_MELEE)
                                applyEffect(dPly, "鲜血狂怒", 100);
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return true;
    }
    public static void dropHeart(Location loc) {
        ItemStack heart = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta = heart.getItemMeta();
        meta.setDisplayName("§c§l心");
        heart.setItemMeta(meta);
        ItemHelper.dropItem(loc, heart, false);
    }
    public static void handleDeath(Entity v, Entity dPly, Entity d, DamageType damageType, String debuffType) {
        if (v instanceof Player) {
            Player vPly = (Player) v;
            // prevent spectator getting in a wall
            setVelocity(vPly, new Vector());
            // respawn time, default to 15 seconds and increases if boss is alive
            int respawnTime = 15;
            for (ArrayList<LivingEntity> bossList : BossHelper.bossMap.values()) {
                HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targets =
                        (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>)
                                getMetadata(bossList.get(0), MetadataName.BOSS_TARGET_MAP).value();
                if (targets.containsKey(vPly.getUniqueId())) {
                    respawnTime = Math.max(respawnTime, Math.min(targets.size() * 15, 75));
                }
            }
            // drop money
            long moneyDrop = (long) Math.floor(PlayerHelper.getMoney(vPly) / 100);
            moneyDrop = (long) Math.ceil(moneyDrop * 0.75);
            if (PlayerHelper.hasPiggyBank(vPly)) {
                moneyDrop = Math.min(moneyDrop, 1104);
            }
            moneyDrop *= 100;
            PlayerHelper.setMoney(vPly, PlayerHelper.getMoney(vPly) - moneyDrop);
            GenericHelper.dropMoney(vPly.getEyeLocation(), moneyDrop, false);
            // death message
            String moneyMsg = "";
            if (moneyDrop > 0) {
                moneyMsg = "§c§l掉了";
                long[] moneyConverted = GenericHelper.coinConversion(moneyDrop, false);
                if (moneyConverted[0] > 0)
                    moneyMsg += "§c§l " + moneyConverted[0] + "§f§l 铂";
                if (moneyConverted[1] > 0)
                    moneyMsg += "§c§l " + moneyConverted[1] + "§e§l 金";
                if (moneyConverted[2] > 0)
                    moneyMsg += "§c§l " + moneyConverted[2] + "§7§l 银";
                if (moneyConverted[3] > 0)
                    moneyMsg += "§c§l " + moneyConverted[3] + "§c§l 铜";
            }
            String deathTitle = "你死了！";
            if ( ItemHelper.splitItemName( vPly.getInventory().getItemInMainHand() )[1].equals("雷姆的复仇") )
                deathTitle = "感谢款待！";
            vPly.sendTitle("§c§l" + deathTitle, moneyMsg, 0, respawnTime * 20, 0);
            sendDeathMessage(d, v, damageType, debuffType);
            // remove vanilla potion effects except for mining fatigue
            // terraria potion effects are removed in their threads
            for (PotionEffect effect : vPly.getActivePotionEffects()) {
                if (effect.getType() != PotionEffectType.SLOW_DIGGING)
                    vPly.removePotionEffect(effect.getType());
            }
            // initialize respawn countdown and other features
            vPly.closeInventory();
            double maxHealth = vPly.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double respawnHealth = Math.max(400, maxHealth / 2);
            // make sure the new health do not exceed maximum health
            vPly.setHealth(Math.min(respawnHealth, maxHealth) );
            setMetadata(vPly, MetadataName.RESPAWN_COUNTDOWN, respawnTime * 20);
            vPly.setGameMode(GameMode.SPECTATOR);
            vPly.setFlySpeed(0);
            vPly.setFallDistance(0);
            // further respawn ticking mechanism in regen thread
        } else {
            // when an entity is killed by a player
            Set<String> vScoreboardTags = v.getScoreboardTags();
            if (dPly instanceof Player) {
                Player dPlayer = (Player) dPly;
                String victimName = GenericHelper.trimText(v.getName());
                // spawn empress of light
                switch (victimName) {
                    // the Hive Mind
                    case "腐化囊": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.THE_HIVE_MIND);
                        break;
                    }
                    // anahita and leviathan spawns after killing ???
                    case "???": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.LEVIATHAN_AND_ANAHITA);
                        break;
                    }
                    // empress of light
                    case "七彩草蛉": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.EMPRESS_OF_LIGHT);
                        break;
                    }
                    // lunatic cultist spawns after killing the mob in the dungeon
                    case "拜月教教徒": {
                        BossHelper.spawnBoss((Player) dPly, BossHelper.BossType.LUNATIC_CULTIST, v.getLocation());
                        break;
                    }
                }
                // drop stars
                switch (damageType) {
                    case MAGIC:
                    case SPECTRE:
                        if (v.getScoreboardTags().contains("isMonster")) {
                            for (int i = 0; i < 3; i ++)
                                if (Math.random() < 0.1) {
                                    ItemStack star = new ItemStack(Material.CLAY_BALL);
                                    ItemMeta meta = star.getItemMeta();
                                    meta.setDisplayName("§9§l星");
                                    star.setItemMeta(meta);
                                    ItemHelper.dropItem(v.getLocation(), star, false);
                                }
                        }
                }
                // drop heart
                if (dPlayer.getHealth() < dPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() &&
                        vScoreboardTags.contains("isMonster") && Math.random() < 0.2) {
                    dropHeart(v.getLocation());
                }
            }
            // set health/directly remove
            if (v instanceof LivingEntity) ((LivingEntity) v).setHealth(0);
            else v.remove();
            // NPC should also get death message
            if (vScoreboardTags.contains("isNPC"))
                sendDeathMessage(d, v, damageType, debuffType);
            // monster generic drop, event etc.
            else if (vScoreboardTags.contains("isMonster")) {
                LivingEntity vLiving = (LivingEntity) v;
                MetadataValue spawnEvt = getMetadata(v, MetadataName.SPAWN_IN_EVENT);
                // event monster
                if (spawnEvt != null && spawnEvt.value() == EventAndTime.currentEvent ) {
                    HashMap<EventAndTime.EventInfoMapKeys, Double> eventInfo = EventAndTime.eventInfo;
                    if (eventInfo.getOrDefault(EventAndTime.EventInfoMapKeys.IS_INVASION, 1d) > 0) {
                        MetadataValue progress = getMetadata(v, MetadataName.KILL_CONTRIBUTE_EVENT_PROGRESS);
                        if (progress != null) {
                            double invadeProgress = eventInfo.getOrDefault(EventAndTime.EventInfoMapKeys.INVADE_PROGRESS, 0d);
                            eventInfo.put(EventAndTime.EventInfoMapKeys.INVADE_PROGRESS,
                                    invadeProgress + progress.asDouble());
                        }
                    }
                }
                // generic death drop etc.
                MetadataValue parentTypeMetadata = getMetadata(v, MetadataName.MONSTER_PARENT_TYPE);
                MetadataValue bossTypeMetadata = getMetadata(v, MetadataName.BOSS_TYPE);
                String parentType = "";
                if (bossTypeMetadata != null)
                    parentType = bossTypeMetadata.value().toString();
                if (parentTypeMetadata != null)
                    parentType = parentTypeMetadata.asString();

                switch (parentType) {
                    // lava slimes leave lava at death
                    case "史莱姆": {
                        if (v.getWorld().getName().equals(TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD)) {
                            Location deathLoc = vLiving.getEyeLocation();
                            Block deathBlock = deathLoc.getBlock();
                            WorldHelper.createTemporaryLava(deathBlock);
                        }
                        break;
                    }
                    // the hungry flies towards the player after breaking free
                    case "饿鬼Attached": {
                        if (dPly instanceof Player) {
                            MonsterHelper.spawnMob("饿鬼", v.getLocation(), (Player) dPly);
                            v.remove();
                        }
                        break;
                    }
                }
                // special, player-progression specific drops
                if (dPly instanceof Player) {
                    Player dPlayer = (Player) dPly;
                    // dungeon souls
                    if (WorldHelper.BiomeType.getBiome(dPlayer) == WorldHelper.BiomeType.DUNGEON) {
                        if (PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.PLANTERA.msgName) &&
                                Math.random() < 0.125)
                            MonsterHelper.spawnMob("地牢幽魂", v.getLocation(), dPlayer);
                    }
                    // souls and essences
                    if (PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.WALL_OF_FLESH.msgName)) {
                        if (!v.getScoreboardTags().contains("isBOSS")) {
                            Location deathLoc = v.getLocation();
                            WorldHelper.BiomeType biome = WorldHelper.BiomeType.getBiome(deathLoc);
                            switch (biome) {
                                case HALLOW: // hallow
                                case CORRUPTION: // corruption
                                {
                                    WorldHelper.HeightLayer height = WorldHelper.HeightLayer.getHeightLayer(deathLoc);
                                    if (height != WorldHelper.HeightLayer.CAVERN)
                                        return;
                                    double dropChance = (255 - v.getLocation().getY()) / 125;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                biome == WorldHelper.BiomeType.HALLOW ? "光明之魂" : "暗影之魂", false);
                                    break;
                                }
                                case TUNDRA: // tundra
                                {
                                    double dropChance = 0.25;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                "冰川精华", false);
                                    break;
                                }
                                case SPACE: // space
                                {
                                    double dropChance = 0.25;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                "日光精华", false);
                                    break;
                                }
                                case BRIMSTONE_CRAG: // brimstone crag
                                {
                                    double dropChance = 0.25;
                                    if (Math.random() < dropChance)
                                        ItemHelper.dropItem(vLiving.getEyeLocation(),
                                                "混乱精华", false);
                                    break;
                                }
                            }
                        }
                    }
                    // post-DoG essences
                    if (spawnEvt != null && PlayerHelper.hasDefeated(dPlayer, BossHelper.BossType.THE_DEVOURER_OF_GODS.msgName)) {
                        String itemType = null;
                        int dropAmountMin = 1, dropAmountMax = 1;
                        double dropChance = 0d;
                        EventAndTime.Events evt = (EventAndTime.Events) spawnEvt.value();
                        switch (evt) {
                            case PUMPKIN_MOON:
                                itemType = "梦魇魔能";
                                switch (parentType) {
                                    case "树精":
                                        dropChance = 0.5d;
                                        break;
                                    case "地狱犬":
                                    case "胡闹鬼":
                                        dropChance = 0.5d;
                                        dropAmountMax = 2;
                                        break;
                                    case "无头骑士":
                                        dropChance = 1d;
                                        dropAmountMin = 3;
                                        dropAmountMax = 5;
                                        break;
                                    case "哀木":
                                        dropChance = 1d;
                                        dropAmountMin = 5;
                                        dropAmountMax = 10;
                                        break;
                                    case "南瓜王":
                                        dropChance = 1d;
                                        dropAmountMin = 10;
                                        dropAmountMax = 20;
                                        break;
                                }
                                break;
                            case FROST_MOON:
                                itemType = "恒温能量";
                                switch (parentType) {
                                    case "胡桃夹士":
                                    case "精灵直升机":
                                    case "雪花怪":
                                        dropChance = 0.5d;
                                        break;
                                    case "坎卜斯":
                                    case "雪兽":
                                    case "礼物宝箱怪":
                                        dropChance = 0.5d;
                                        dropAmountMax = 2;
                                        break;
                                    case "常绿尖叫怪":
                                        dropChance = 1d;
                                        dropAmountMin = 3;
                                        dropAmountMax = 5;
                                        break;
                                    case "圣诞坦克":
                                        dropChance = 1d;
                                        dropAmountMin = 5;
                                        dropAmountMax = 10;
                                        break;
                                    case "冰雪女王":
                                        dropChance = 1d;
                                        dropAmountMin = 10;
                                        dropAmountMax = 20;
                                        break;
                                }
                                break;
                            case SOLAR_ECLIPSE:
                                itemType = "日蚀之阴碎片";
                                switch (parentType) {
                                    case "水月怪":
                                    case "弗里茨":
                                    case "沼泽怪":
                                    case "科学怪人":
                                        dropChance = 0.1d;
                                        break;
                                    case "死神":
                                    case "吸血鬼":
                                    case "攀爬魔":
                                    case "致命球":
                                        dropChance = 0.5d;
                                        break;
                                    case "眼怪":
                                        dropChance = 1d;
                                        dropAmountMax = 2;
                                        break;
                                    case "蛾怪":
                                        dropChance = 1d;
                                        dropAmountMin = 20;
                                        dropAmountMax = 30;
                                        break;
                                }
                                break;
                        }
                        // drop
                        if (itemType != null && Math.random() < dropChance) {
                            int dropAmountFinal = dropAmountMin + (int) (Math.random() * (dropAmountMax - dropAmountMin + 1));
                            ItemHelper.dropItem(vLiving.getEyeLocation(),
                                    itemType + ":" + dropAmountFinal, false);
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
        // store scoreboard tags as it is requested frequently below
        Set<String> targetScoreboardTags = target.getScoreboardTags();
        Set<String> entityScoreboardTags = entity.getScoreboardTags();
        Entity damageSource = getDamageSource(entity);
        if (targetScoreboardTags.contains("isPillar")) {
            MetadataValue temp = getMetadata(target, MetadataName.CELESTIAL_PILLAR_SHIELD);
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
                // homing weapons and minions should not willingly attack critters and NPCs (with voodoo doll)
                if (strict) return false;
                if (targetScoreboardTags.contains("isNPC"))
                    return accessories.contains(target.getName() + "巫毒娃娃");
                if (targetScoreboardTags.contains("isAnimal"))
                    return (!accessories.contains("小动物友谊指南"));
                // entities that are not animal, NPC or monster can be damaged but are not targeted actively
                return true;
            }
        } else if (target instanceof Player) {
            // non-player attacks player
            HashSet<String> accessories = PlayerHelper.getAccessories(target);
            Player targetPly = (Player) target;
            if (PlayerHelper.isProperlyPlaying(targetPly)) {
                // handle special parent type (slime damage neglected by royal gel)
                MetadataValue temp = getMetadata(damageSource, MetadataName.MONSTER_PARENT_TYPE);
                if (temp != null) {
                    String parentType = temp.asString();
                    if (parentType.equals("史莱姆") && accessories.contains("皇家凝胶"))
                        return false;
                }
                // NPCs do not attack players
                return !entityScoreboardTags.contains("isNPC");
            }
            // players that are logged out are not supposed to get hurt.
            else {
                return false;
            }
        } else {
            // non-player attacks non-player
            // monster attack target: npc and critters can be damaged by accident but not targeted on purpose
            if (entityScoreboardTags.contains("isMonster")) {
                if (targetScoreboardTags.contains("isNPC") || targetScoreboardTags.contains("isAnimal"))
                    return !strict;
                return false;
            }
            // any entity other than monster -> monster: true
            return targetScoreboardTags.contains("isMonster");
        }
    }
    public static Entity getDamageSource(Entity damager) {
        Entity source = damager;
        if (source instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) source).getShooter();
            if (shooter instanceof Entity) source = (Entity) shooter;
        }
        MetadataValue damageSourceMetadata = getMetadata(source, MetadataName.DAMAGE_SOURCE);
        if (damageSourceMetadata != null)
            source = (Entity) damageSourceMetadata.value();
        return source;
    }
    public static Entity getMount(Entity entity) {
        if (entity instanceof Player)
            return PlayerHelper.getMount((Player) entity);
        return entity.getVehicle();
    }
    // for player, use either this getVelocity or the getPlayerVelocity in PlayerHelper
    public static Vector getVelocity(Entity entity) {
        if (entity instanceof Player)
            return PlayerHelper.getPlayerVelocity((Player) entity);
        return entity.getVelocity();
    }
    // for player, use this setVelocity instead of vanilla one
    public static void setVelocity(Entity entity, Vector spd) {
        // handle unreasonable magnitude (> 100)
        if (spd.lengthSquared() > 1e5)
            spd.zero();
        if (entity instanceof Player) {
            setMetadata(entity, MetadataName.PLAYER_VELOCITY, spd);
            MetadataValue mtv = getMetadata(entity, MetadataName.PLAYER_VELOCITY_MULTI);
            spd = spd.clone();
            if (mtv != null)
                spd.multiply(mtv.asDouble());
        }
        entity.setVelocity(spd);
    }
    public static void knockback(Entity entity, Vector dir, boolean addOrReplace) {
        knockback(entity, dir, addOrReplace, -1);
    }
    public static void knockback(Entity entity, Vector dir, boolean addOrReplace, double speedLimit) {
        // entities immune to knockback should not be effected at all
        double kbResistance = getAttrMap(entity).getOrDefault("knockbackResistance", 0d);
        if (kbResistance >= 1) return;
        // determine the knockback acceleration
        double kbMulti = Math.max(1 - kbResistance, 0);
        dir = dir.clone().multiply(kbMulti);
        // update the knockback slow factor, which effects the walking speed of zombies etc.
        setMetadata(entity, MetadataName.KNOCKBACK_SLOW_FACTOR, kbMulti);
        // the entity subject to that knockback
        Entity knockbackTaker = getMount(entity);
        if (knockbackTaker == null) knockbackTaker = entity;
        // minecart takes no knockback
        if (knockbackTaker instanceof Minecart)
            return;
        // calculate the final velocity
        Vector finalVel = getVelocity(knockbackTaker);
        if (addOrReplace) {
            finalVel.add(dir);
        } else {
            // multiply by 1-kbMulti, as some enemies have negative knockback resistance
            finalVel.multiply(1 - kbMulti);
            finalVel.add(dir);
        }
        // for knockback with a speed limit, make sure it does not exceed the limit
        if (speedLimit > 0 && finalVel.lengthSquared() > speedLimit * speedLimit) {
            MathHelper.setVectorLength(finalVel, speedLimit);
        }
        setVelocity(knockbackTaker, finalVel);
    }
    public static String getInvulnerabilityTickName(DamageType damageType) {
        return "tempDamageCD_" + damageType;
    }
    public static void handleEntityTemporaryScoreboardTag(Entity entity, String scoreboardTagName, int scoreboardTagDuration) {
        if (scoreboardTagDuration > 0) {
            // apply the invulnerability tick
            entity.addScoreboardTag(scoreboardTagName);
            // record edition
            MetadataValue metadataValue = getMetadata(entity, MetadataName.INVULNERABILITY_TICK_EDITION_MAP);
            HashMap<String, Integer> invulnerabilityTickEditionMap;
            if (metadataValue == null)
                invulnerabilityTickEditionMap = new HashMap<>();
            else
                invulnerabilityTickEditionMap = (HashMap<String, Integer>) metadataValue.value();
            int iTickEdition = invulnerabilityTickEditionMap.getOrDefault(scoreboardTagName, 0) + 1;
            invulnerabilityTickEditionMap.put(scoreboardTagName, iTickEdition);
            setMetadata(entity, MetadataName.INVULNERABILITY_TICK_EDITION_MAP, invulnerabilityTickEditionMap);
            // remove it if the edition is not outdated
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> {
                        HashMap<String, Integer> currITickEditionMap = (HashMap<String, Integer>) getMetadata(
                                entity, MetadataName.INVULNERABILITY_TICK_EDITION_MAP).value();
                        if (currITickEditionMap.get(scoreboardTagName) == iTickEdition)
                            entity.removeScoreboardTag(scoreboardTagName);
                        switch (scoreboardTagName) {
                            case "temp_parryCD":
                                entity.sendMessage("§7方舟格挡冷却结束！");
                        }
                    }, scoreboardTagDuration);
        }
    }
    private static void displayDamageActionBar(Player damager, String victimName, int health, int maxHealth, int damage) {
        if (health > 0) {
            PlayerHelper.sendActionBar(damager,
                    "§r" + victimName + " §6[§a" + health + "§6/§a" + maxHealth + "§6] §b(-" + damage + ")");
        } else
            PlayerHelper.sendActionBar(damager, victimName + " §c领了盒饭");
    }
    public static void handleDamage(Entity damager, Entity victim, double damage, DamageReason damageReason) {
        handleDamage(damager, victim, damage, damageReason, null);
    }
    public static void handleDamage(Entity damager, Entity victim, double damage, DamageReason damageReason, String debuffType) {
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
        // Daawnlight target should not take any damage
        if (victimScoreboardTags.contains("isDaawnlight")) {
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
        boolean isDirectAttackDamage = damageReason.isDirectDamage();
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity && ((Entity) shooter).getScoreboardTags().contains("isMinion"))
                isMinionDmg = true;
        } else if (damager.getScoreboardTags().contains("isMinion")) isMinionDmg = true;
        // setup properties such as invulnerability tick and defence
        int damageInvulnerabilityTicks;
        double defence = victimAttrMap.getOrDefault("defence", 0d) * victimAttrMap.getOrDefault("defenceMulti", 1d);
        if (victimScoreboardTags.contains("isBOSS")) {
            if (isDirectAttackDamage) {
                MetadataValue bossTargets = getMetadata(victim, MetadataName.BOSS_TARGET_MAP);
                boolean canProperlyDamage = false;
                if (damageSource instanceof Player) {
                    if ( bossTargets == null ||
                            (((HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) (bossTargets.value()))
                                    .containsKey(damageSource.getUniqueId())) )
                        canProperlyDamage = true;
                }
                // damage source of a boss is not a target
                if (!canProperlyDamage)
                    defence = 999999999;
            } else {
                // if the damage source of a boss is neither direct attack, thorn nor debuff (that is, lava etc.)
                // the damage is ignored.
                if (! ((damageReason == DamageReason.DEBUFF) || (damageReason == DamageReason.THORN)))
                    canDamage = false;
            }
        }
        if (!canDamage) return;

        // damage taker is the entity that takes the damage
        if (victim.hasMetadata( MetadataName.DAMAGE_TAKER.toString() )) {
            Object dmgTaker = getMetadata(victim, MetadataName.DAMAGE_TAKER).value();
            if (dmgTaker instanceof LivingEntity)
                damageTaker = (LivingEntity) dmgTaker;
        }
        // setup damage type and damager attribute
        HashMap<String, Double> damagerAttrMap = getAttrMap(damager);
        DamageType damageType = damageReason.getDamageType();
        if (isDirectAttackDamage) {
            // if no mandatory damage type for the damage reason, default to the attacker(can be a projectile) damage type
            if (damageType == null)
                damageType = getDamageType(damager);
            if (damageReason == DamageReason.DIRECT_DAMAGE && damageType == DamageType.MELEE)
                damageType = DamageType.TRUE_MELEE;
        }
        // if the victim has invincibility frame on this damage type (usually player)
        String damageInvincibilityFrameName = getInvulnerabilityTickName(damageType);
        if (victimScoreboardTags.contains(damageInvincibilityFrameName)) return;


        HashSet<String> damagerAccessories = PlayerHelper.getAccessories(damageSource);
        HashSet<String> victimAccessories = PlayerHelper.getAccessories(victim);
        // inflict debuff
        if (isDirectAttackDamage) {
            List<String> buffInflict = new ArrayList<>();
            // projectile buff inflict
            if (damager instanceof Projectile) {
                buffInflict.addAll(TerrariaHelper.projectileConfig.getStringList(damager.getName() + ".buffInflict"));
            }
            // monster/minion direct damage buff inflict
            else if (! (damager instanceof Player)) {
                buffInflict.addAll(TerrariaHelper.entityConfig.getStringList(
                        GenericHelper.trimText(damager.getName()) + ".buffInflict"));
            }
            // player buff inflict
            if (damageSource instanceof Player) {
                // player minion buff inflict
                HashMap<String, ArrayList<String>> buffInflictMap = PlayerHelper.getPlayerEffectInflict(damageSource);
                buffInflict.addAll(buffInflictMap.getOrDefault("buffInflict", new ArrayList<>(0)));
                switch (damageType) {
                    case ARROW:
                    case BULLET:
                    case ROCKET:
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictRanged", new ArrayList<>(0)));
                        break;
                    case TRUE_MELEE:
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictMelee", new ArrayList<>(0)));
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictTrueMelee", new ArrayList<>(0)));
                        break;
                    // Summon, Melee, Magic
                    default:
                        // whip
                        if (damageType == DamageType.SUMMON && damageReason == DamageReason.DIRECT_DAMAGE)
                            buffInflict.addAll(buffInflictMap.getOrDefault("buffInflictMelee", new ArrayList<>(0)));
                        buffInflict.addAll(buffInflictMap.getOrDefault("buffInflict" + damageType, new ArrayList<>(0)));
                }
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
        }

        // further setup damage info (fixed amount etc.); damage setup for special damage types
        damageInvulnerabilityTicks = victimAttrMap.getOrDefault("invulnerabilityTick", 0d).intValue();
        double dmg = damage;
        double knockback = 0d, critRate = -1e9;
        boolean damageFixed = false;
        switch (damageReason) {
            case FEAR:
            case BOSS_ANGRY:
                dmg = 114514;
                damageFixed = true;
                break;
            case FALL:
                double fallDist = victim.getFallDistance();
                dmg = (fallDist - 12.5) * 20;
                if (dmg < 0) return;
                break;
            case BLOCK_EXPLOSION:
                knockback = 5;
                dmg = 1000;
                break;
            case LAVA:
                if (victim.getLocation().getBlock().getBiome() == Biome.SAVANNA)
                    applyEffect(victim, "硫磺火", 150);
                else
                    applyEffect(victim, "燃烧", 150);
                // mainly prevents excessive damage dealt to enemies
                damageInvulnerabilityTicks = Math.max(damageInvulnerabilityTicks, 30);
                break;
            case DROWNING:
                damageFixed = true;
                damageInvulnerabilityTicks = 0;
                break;
            case SUFFOCATION:
                damageInvulnerabilityTicks = 0;
                dmg = 10;
                damageFixed = true;
                break;
            case THORN:
                damageInvulnerabilityTicks = 0;
                damageFixed = true;
                break;
            default:
                if (damageReason == DamageReason.DEBUFF) {
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
                    boolean canGetPercentageBonus = !(damageReason == DamageReason.SPECTRE);
                    if (canGetPercentageBonus)
                        dmg *= damagerAttrMap.getOrDefault("damageMulti", 1d);
                    critRate = damagerAttrMap.getOrDefault("crit", 0d);
                    switch (damageType) {
                        case TRUE_MELEE:
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damageTrueMeleeMulti", 1d);
                            critRate += damagerAttrMap.getOrDefault("critTrueMelee", 0d);
                        case MELEE:
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damageMeleeMulti", 1d);
                            critRate += damagerAttrMap.getOrDefault("critMelee", 0d);
                            knockback *= damagerAttrMap.getOrDefault("knockbackMeleeMulti", 1d);
                            break;
                        case ARROW:
                        case BULLET:
                        case ROCKET:
                            if (canGetPercentageBonus) {
                                dmg *= damagerAttrMap.getOrDefault("damage" + damageType + "Multi", 1d);
                                dmg *= damagerAttrMap.getOrDefault("damageRangedMulti", 1d);
                            }
                            critRate += damagerAttrMap.getOrDefault("critRanged", 0d);
                            break;
                        case MAGIC:
                        case SPECTRE:
                            critRate += damagerAttrMap.getOrDefault("critMagic", 0d);
                        case SUMMON:
                            if (canGetPercentageBonus)
                                dmg *= damagerAttrMap.getOrDefault("damage" + damageType + "Multi", 1d);
                            break;
                        default:
                            TerrariaHelper.LOGGER.log(Level.SEVERE, "Unhandled damage type: " + damageType);
                            return;
                    }
                } else {
                    TerrariaHelper.LOGGER.log(Level.SEVERE, "Unhandled damage reason: " + damageReason);
                    return;
                }
        }

        // tweak damage, including minion whip bonus, accessories such as paladin shield, random damage floating and crit
        boolean crit = false;
        if (!damageFixed) {
            double damageTakenMulti = victimAttrMap.getOrDefault("damageTakenMulti", 1d);
            switch (damageType) {
                case MELEE:
                case TRUE_MELEE:
                    damageTakenMulti *= victimAttrMap.getOrDefault("damageContactTakenMulti", 1d);
                    if (hasEffect(victim, "血肉图腾"))
                        applyEffect(victim, "血肉图腾", 0);
            }
            dmg *= Math.random() * 0.3 + 0.85;
            // extra tweak on damage when victim is not a player
            if (!(victim instanceof Player)) {
                // minion damage effects and whips
                if (isMinionDmg) {
                    MetadataValue temp;
                    temp = getMetadata(victim, MetadataName.MINION_WHIP_BONUS_DAMAGE);
                    double dmgBonus = temp != null ? temp.asDouble() : 0;
                    dmg += dmgBonus;
                    temp = getMetadata(victim, MetadataName.MINION_WHIP_BONUS_CRIT);
                    critRate += temp != null ? temp.asDouble() : 0;
                    // on-hit effects from accessory
                    if (damagerAccessories.contains("幻魂神物")) {
                        double rdm = Math.random();
                        if (rdm < 0.3333)
                            EntityHelper.applyEffect(damageSource, "幻魂还生", 20);
                        else if (rdm < 0.6666)
                            EntityHelper.applyEffect(damageSource, "幻魂坚盾", 20);
                        else
                            EntityHelper.applyEffect(damageSource, "幻魂之力", 20);
                    }
                    else if (damagerAccessories.contains("神圣符文")) {
                        double rdm = Math.random();
                        if (rdm < 0.3333)
                            EntityHelper.applyEffect(damageSource, "神圣之辉", 20);
                        else if (rdm < 0.6666)
                            EntityHelper.applyEffect(damageSource, "神圣之佑", 20);
                        else
                            EntityHelper.applyEffect(damageSource, "神圣之力", 20);
                    }
                    else if (damagerAccessories.contains("灵魂浮雕")) {
                        double rdm = Math.random();
                        if (rdm < 0.3333)
                            EntityHelper.applyEffect(damageSource, "灵魂恢复", 20);
                        else if (rdm < 0.6666)
                            EntityHelper.applyEffect(damageSource, "灵魂防御", 20);
                        else
                            EntityHelper.applyEffect(damageSource, "灵魂力量", 20);
                    }
                }
                // special enemy damage tweak
                switch (GenericHelper.trimText( victim.getName() ) ) {
                    case "猪鲨公爵":
                    case "硫海遗爵":
                    case "利维坦":
                        // the damager is the flail
                        if (damager.getName().equals("雷姆的复仇"))
                            dmg *= 50;
                        break;

                }
                // accessories
                if (damageSource instanceof Player) {
                    Player damageSourcePly = (Player) damageSource;
                    for (String plyAcc : damagerAccessories) {
                        switch (plyAcc) {
                            case "魔能过载仪":
                            case "魔能熔毁仪": {
                                if (getDamageType(damageSourcePly) == DamageType.MAGIC) {
                                    int mana = damageSourcePly.getLevel();
                                    double consumption;
                                    double manaToDamageRate;
                                    double consumptionRatio;
                                    if (plyAcc.equals("魔能过载仪")) {
                                        consumption = 8;
                                        manaToDamageRate = 7.5;
                                        consumptionRatio = 1;
                                    }
                                    else {
                                        consumption = (int) Math.max(mana * 0.035, 5);
                                        manaToDamageRate = 15;
                                        double effectDuration = EntityHelper.getEffectMap(damageSourcePly).getOrDefault("魔力熔蚀", 0);
                                        // at 20 second = 400 ticks, mana use reduced by roughly 65%
                                        consumptionRatio = 1 - (effectDuration / 615);
                                    }
                                    if (ItemUseHelper.consumeMana(damageSourcePly, MathHelper.randomRound(consumption * consumptionRatio) )) {
                                        dmg += consumption * manaToDamageRate;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                // crit to non-player victims
                if (Math.random() * 100 < critRate) {
                    crit = true;
                    dmg *= 1 + (damagerAttrMap.getOrDefault("critDamage", 1d) / 100);
                }
            }
            // extra tweak on damage when victim is a player
            else {
                // crit to player pre-defence due to blood pact
                if (victimAccessories.contains("血契") && Math.random() < 0.25) {
                    crit = true;
                    dmg *= 1.75;
                }

                // paladin shield, only applies to player victims
                if (! hasEffect(victim, "圣骑士护盾")) {
                    String team = getMetadata(victim, MetadataName.PLAYER_TEAM).asString();
                    // works with players within 96 blocks
                    double dist = 9216;
                    Entity shieldPly = null;
                    for (Player ply : victim.getWorld().getPlayers()) {
                        if (!PlayerHelper.isProperlyPlaying(ply)) continue;
                        if (!hasEffect(ply, "圣骑士护盾")) continue;
                        String currTeam = getMetadata(ply, MetadataName.PLAYER_TEAM).asString();
                        if (!(currTeam.equals(team))) continue;
                        double currDist = ply.getLocation().distanceSquared(victim.getLocation());
                        if (currDist >= dist) continue;
                        dist = currDist;
                        shieldPly = ply;
                    }
                    if (shieldPly != null) {
                        handleDamage(damager, shieldPly, (dmg * 0.05), DamageReason.THORN);
                        dmg *= 0.85;
                    }
                }
            }
            dmg *= damageTakenMulti;
            defence = Math.max(defence - damagerAttrMap.getOrDefault("armorPenetration", 0d), 0);
            dmg -= defence * 0.75;
            // damage barrier
            if (hasEffect(victim, "保护矩阵")) {
                // 20 ticks = 10 dmg
                int damageShield = getEffectMap(victim).get("保护矩阵") / 2;
                int damageBlock = (int) Math.min(Math.ceil(dmg), damageShield);
                dmg -= damageBlock;
                applyEffect(victim, "保护矩阵", (damageShield - damageBlock) * 2);
                // interrupt barrier regen
                applyEffect(victim, "保护矩阵充能", 175);
            }
            if (hasEffect(victim, "狮心圣裁能量外壳")) {
                applyEffect(victim, "狮心圣裁能量外壳冷却", 900);
            }
            // boss damage reduction
            if (victimScoreboardTags.contains("isBOSS")) {
                double dynamicDR = 1;
                MetadataValue temp = getMetadata(victim, MetadataName.DYNAMIC_DAMAGE_REDUCTION);
                if (temp != null) dynamicDR = temp.asDouble();
                BossHelper.BossType type = (BossHelper.BossType) getMetadata(victim, MetadataName.BOSS_TYPE).value();
                if (damageSource instanceof Player &&  ! PlayerHelper.hasDefeated((Player) damageSource, type.msgName) )
                    dmg *= dynamicDR;
            }
            // NPC damage reduction ( for non-fixed damage, the damage is decreased by a factor of 4, and is upper capped at 50
            else if (victimScoreboardTags.contains("isNPC")) {
                dmg = Math.min(dmg / 4d, 50);
            }
        }
        // round damage
        dmg = Math.round(dmg);
        if (dmg < 1.5) dmg = crit ? 2 : 1;
        // for some entities that locks health at a specific value
        MetadataValue healthLockMetadata = getMetadata(damageTaker, MetadataName.HEALTH_LOCKED_AT_AMOUNT);
        if (healthLockMetadata != null) {
            double healthLock = healthLockMetadata.asDouble();
            if (damageTaker.getHealth() > healthLock) {
                dmg = Math.min(damageTaker.getHealth() - (healthLock + 1e-5), dmg);
            }
        }

        // call damage event
        if (! entityDamageEvent(damager, damageSource, victimLivingEntity, damageTaker, dmg, damageType, damageReason))
            return;

        // damage/kill
        if (!(victim instanceof ArmorStand)) {
            if (victimScoreboardTags.contains("isBOSS") && damageSource instanceof Player) {
                MetadataValue temp = getMetadata(damageTaker, MetadataName.BOSS_TARGET_MAP);
                if (temp != null) {
                    HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo> targets =
                            (HashMap<UUID, terraria.entity.boss.BossHelper.BossTargetInfo>) temp.value();
                    Player ply = (Player) damageSource;
                    UUID plyID = ply.getUniqueId();
                    if (targets.containsKey(plyID))
                        targets.get(plyID).addDamageDealt(dmg);
                }
            }
            String sound = null;
            // kills the target
            if (damageTaker.getHealth() <= dmg) {
                handleDeath(damageTaker, damageSource, damager, damageType, debuffType);
                if (victimScoreboardTags.contains("isMechanic")) sound = "entity.generic.explode";
                else sound = "entity." + damageTaker.getType() + ".death";
                sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundKilled", sound);
            }
            // hurts the target
            else {
                damageTaker.setHealth(Math.max(0, damageTaker.getHealth() - dmg));
                if (!(damageType == DamageType.DEBUFF)) {
                    // if damage cause is not debuff, play hurt sound
                    if (victimScoreboardTags.contains("isMechanic")) sound = "entity.irongolem.hurt";
                    else sound = "entity." + damageTaker.getType() + ".hurt";
                    sound = TerrariaHelper.entityConfig.getString(GenericHelper.trimText(damageTaker.getName()) + ".soundDamaged", sound);
                }
            }
            // fall damage sound
            if (damageReason == DamageReason.FALL) {
                if (dmg < 200)
                    sound = "entity.generic.fallSmall";
                else
                    sound = "entity.generic.fallBig";
            }
            if (sound != null)
                victim.getWorld().playSound(victim.getLocation(), sound, 3, 1);
            // knockback
            if (knockback > 0) {
                Vector vec = victim.getLocation().subtract(damager.getLocation()).toVector();
                double kbForce = knockback / 20;
                MathHelper.setVectorLength(vec, kbForce);
                // for non-downward knockback, amplify the upward component and push the victim off ground
                if (victim.isOnGround() && vec.getY() > -1e-3)
                    vec.setY(vec.getY() + Math.min(kbForce * 0.35, 1));
                knockback(victim, vec, false);
            }
        }
        // remove the not damaged marker scoreboard tag
        if (isDirectAttackDamage)
            victim.removeScoreboardTag("notDamaged");

        // display damage
        boolean displayDmg = true;
        switch (damageReason) {
            case SUFFOCATION:
            case DROWNING:
                displayDmg = false;
        }
        if (displayDmg) {
            String hologramInfo;
            if (damageType == DamageType.DEBUFF)
                hologramInfo = "Debuff_" + debuffType;
            else
                hologramInfo = damageType.toString();
            GenericHelper.displayHolo(victim, dmg, crit, hologramInfo);
        }

        // send info message to damager player
        if (damageSource instanceof Player && victim != damageSource) {
            String vName = victim.getName();
            int dmgInt = (int) dmg;
            int healthInt = (int) damageTaker.getHealth();
            int maxHealthInt = (int) damageTaker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            displayDamageActionBar((Player) damageSource, vName, healthInt, maxHealthInt, dmgInt);
        }

        // handle invincibility ticks
        handleEntityTemporaryScoreboardTag(victim, damageInvincibilityFrameName, damageInvulnerabilityTicks);
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
        handleEntityExplode(source, radius, damageExceptions, loc, ticksDuration, 5);
    }
    public static void handleEntityExplode(Entity source, double radius, Collection<Entity> damageExceptions, Location loc,
                                           int ticksDuration, int ticksDelay) {
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
        else if (radius < 8)
            loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1);
        else {
            int amount = (int) (radius);
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
                    handleDamage(source, victim, dmg, DamageReason.EXPLOSION);
                continue;
            }
            if (damageExceptions.contains(victim)) continue;
            if (checkCanDamage(source, victim, false))
                handleDamage(source, victim, dmg, DamageReason.EXPLOSION);
        }
        // destroy block
        if (destroyBlock) {
            Player ply = null;
            Entity sourceEntity = getDamageSource(source);
            if (sourceEntity instanceof Player)
                ply = (Player) sourceEntity;
            if (ply != null) {
                org.bukkit.block.Block blastCenterBlock = loc.getBlock();
                // subtract 1e-3 from radiusSqr to prevent annoying unnatural shape
                double radiusSqr = radius * radius - 1e-3;
                int radInt = (int) Math.ceil(radius);
                double distSqrX, distSqrY, distSqrZ;
                for (int xOffset = radInt * -1; xOffset <= radInt; xOffset ++) {
                    distSqrX = xOffset * xOffset;
                    // prevent a block of dirt held intact after blowing up a tree
                    for (int yOffset = radInt; yOffset >= -radInt; yOffset--) {
                        distSqrY = yOffset * yOffset;
                        if (distSqrX + distSqrY > radiusSqr) continue;
                        for (int zOffset = radInt * -1; zOffset <= radInt; zOffset++) {
                            distSqrZ = zOffset * zOffset;
                            if (distSqrX + distSqrY + distSqrZ > radiusSqr) continue;
                            org.bukkit.block.Block currBlock = blastCenterBlock.getRelative(xOffset, yOffset, zOffset);
                            GameplayHelper.playerBreakBlock(currBlock, ply, true, false);
                        }
                    }
                }
            }
        }
        // lingering explosion
        if (ticksDuration > ticksDelay) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(),
                    () -> handleEntityExplode(source, radius, damageExceptions, loc, ticksDuration - ticksDelay),
                    ticksDelay);
        }
    }

    public static class ProjectileShootInfo {
        public ProjectileSource shooter;
        public Location shootLoc;
        public Vector velocity;
        public HashMap<String, Double> attrMap;
        public HashMap<String, Object> properties;
        public DamageType damageType;
        public String projectileName;
        public boolean arrowOrPotion;
        // extracts the attributes that may impact the projectile's damage to prevent excessive memory usage
        private static HashMap<String, Double> extractAttrMap(HashMap<String, Double> original) {
            HashMap<String, Double> result = new HashMap<>();
            for (String key : original.keySet()) {
                boolean shouldInclude = false;
                switch (key) {
                    case "armorPenetration":
                    case "knockback":
                    case "knockbackMulti":
                    case "liveSteal":
                        shouldInclude = true;
                        break;
                    default:
                        if (key.startsWith("damage") && ! (key.endsWith("TakenMulti") ))
                            shouldInclude = true;
                        else if (key.startsWith("crit"))
                            shouldInclude = true;

                }
                if (shouldInclude)
                    result.put(key, original.get(key));
            }
            return result;
        }
        // constructors
        public ProjectileShootInfo(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String projectileName) {
            this(shooter, velocity, attrMap, getDamageType(shooter), projectileName);
        }
        public ProjectileShootInfo(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, DamageType damageType, String projectileName) {
            this(shooter,
                    (shooter instanceof LivingEntity) ? ((LivingEntity) shooter).getEyeLocation() : shooter.getLocation(),
                    velocity, attrMap, damageType, projectileName);
        }
        public ProjectileShootInfo(Entity shooter, Location shootLoc, Vector velocity, HashMap<String, Double> attrMap, DamageType damageType, String projectileName) {
            this.shooter = null;
            if (shooter instanceof ProjectileSource) this.shooter = (ProjectileSource) shooter;
            this.shootLoc = shootLoc;
            this.velocity = velocity;
            this.attrMap = extractAttrMap(attrMap);
            this.properties = new HashMap<>(25);
            if (projectileName.length() > 0) {
                ConfigurationSection section = TerrariaHelper.projectileConfig.getConfigurationSection(projectileName);
                if (section != null) {
                    String[] keys;
                    // ints
                    {
                        keys = new String[]{"homingMethod", "bounce", "enemyInvincibilityFrame", "liveTime",
                                "noHomingTicks", "noGravityTicks", "maxHomingTicks", "minimumDamageTicks",
                                "penetration", "trailLingerTime", "worldSpriteUpdateInterval"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getInt(key));
                        }
                    }
                    // strings
                    {
                        keys = new String[]{"blockHitAction", "trailColor", "spawnSound"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getString(key));
                        }
                    }
                    // doubles
                    {
                        keys = new String[]{"homingAbility", "homingEndSpeedMultiplier", "homingRadius",
                                "blastRadius", "bounceVelocityMulti",
                                "frictionFactor", "gravity", "maxSpeed", "projectileSize", "spawnSoundPitch",
                                "spawnSoundVolume", "speedMultiPerTick", "trailIntensityMulti", "trailSize",
                                "trailStepSize"};
                        for (String key : keys) {
                            if (section.contains(key))
                                this.properties.put(key, section.getDouble(key));
                        }
                    }
                    // booleans
                    {
                        keys = new String[]{"homing", "homingSharpTurning", "homingRetarget", "blastDamageShooter",
                                "blastOnContactBlock", "blastOnContactEnemy", "bouncePenetrationBonded",
                                "canBeReflected", "isGrenade", "slowedByWater", "trailVanillaParticle",
                                "worldSpriteMode"};
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
            this.arrowOrPotion = TerrariaHelper.projectileConfig.getBoolean(projectileName + ".arrowOrPotion", arrowOrPotion);
        }

    }
    public static Projectile spawnProjectile(ProjectileShootInfo shootInfo) {
        CraftWorld wld = (CraftWorld) shootInfo.shootLoc.getWorld();
        Projectile bukkitProjectile;
        TerrariaPotionProjectile entity = new TerrariaPotionProjectile(shootInfo);
        bukkitProjectile = new CraftSplashPotion(wld.getHandle().getServer(), entity);
        return bukkitProjectile;
    }
    // the spawn projectile below are not as flexible comparing to the function above
    public static Projectile spawnProjectile(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String projectileName) {
        return spawnProjectile(shooter, velocity, attrMap, getDamageType(shooter), projectileName);
    }
    public static Projectile spawnProjectile(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, DamageType damageType, String projectileName) {
        Location shootLoc;
        if (shooter instanceof LivingEntity) shootLoc = ((LivingEntity) shooter).getEyeLocation();
        else shootLoc = shooter.getLocation();
        return spawnProjectile(shooter, shootLoc, velocity, attrMap, damageType, projectileName);
    }
    public static Projectile spawnProjectile(Entity shooter, Location shootLoc, Vector velocity, HashMap<String, Double> attrMap, DamageType damageType, String projectileName) {
        ProjectileShootInfo shootInfo = new ProjectileShootInfo(shooter, shootLoc, velocity, attrMap, damageType, projectileName);
        return spawnProjectile(shootInfo);
    }
    // helps aim at an entity
    public static Location helperAimEntity(Location shootLoc, Entity target, AimHelperOptions aimHelperOption) {
        shootLoc.checkFinite();
        assert aimHelperOption.projectileSpeed != 0d;
        Vector enemyVel, enemyAcc;
        // get target velocity and acceleration
        Location targetLoc;
        if (target instanceof Player) {
            Player targetPly = (Player) target;
            targetLoc = PlayerHelper.getAccurateLocation(targetPly);

            Location lastLoc = (Location) EntityHelper.getMetadata(target, MetadataName.PLAYER_CURRENT_LOCATION).value();
            Location secondLastLoc = (Location) EntityHelper.getMetadata(target, MetadataName.PLAYER_LAST_LOCATION).value();
            if (lastLoc.distanceSquared(targetLoc) < 1e-5) {
                lastLoc = (Location) EntityHelper.getMetadata(target, MetadataName.PLAYER_LAST_LOCATION).value();
                secondLastLoc = (Location) EntityHelper.getMetadata(target, MetadataName.PLAYER_SECOND_LAST_LOCATION).value();
            }
            enemyVel = targetLoc.clone().subtract(lastLoc).toVector();
            Vector enemyVelSecondLast = lastLoc.clone().subtract(secondLastLoc).toVector();
            enemyAcc = enemyVel.clone().subtract(enemyVelSecondLast);
        }
        else {
            targetLoc = target.getLocation();
            MetadataValue currVelMetadata = getMetadata(target, MetadataName.ENTITY_CURRENT_VELOCITY);
            MetadataValue lastVelMetadata = getMetadata(target, MetadataName.ENTITY_LAST_VELOCITY);
            enemyVel = target.getVelocity();
            // if any of the two are not yet recorded, assume acceleration is none.
            if (currVelMetadata == null || lastVelMetadata == null) {
                enemyAcc = new Vector();
            }
            // otherwise, calculate acceleration.
            else {
                Vector currSavedVel = (Vector) currVelMetadata.value();
                Vector lastSavedVel = (Vector) lastVelMetadata.value();
                enemyAcc = currSavedVel.clone().subtract(lastSavedVel);
            }
            // for enemies with gravity, upper cap the acceleration at y = -0.08
            if (target.hasGravity())
                enemyAcc.setY( Math.min(enemyAcc.getY(), -0.08) );
        }
        // offset enemyAcc
        enemyAcc.add(aimHelperOption.accelerationOffset);

        // setup target location
        Location predictedLoc;
        // aim at the middle of the entity
        if (target instanceof LivingEntity) {
            EntityLiving targetNMS = ((CraftLivingEntity) target).getHandle();
            AxisAlignedBB boundingBox = targetNMS.getBoundingBox();
            targetLoc.add(0, boundingBox.e - boundingBox.b, 0);
        }
        // a placeholder, so that the function does not report an error
        predictedLoc = targetLoc.clone();
        // "hyper-params" for prediction; note that ticks offset is roughly estimated before entering the loop.
        boolean checkBlockColl;
        Entity targetMount = EntityHelper.getMount(target);
        if (targetMount == null)
            checkBlockColl = ! ((CraftEntity) target).getHandle().noclip;
        else {
            checkBlockColl = !(targetMount instanceof Minecart);
        }
        double predictionIntensity = aimHelperOption.intensity;
        double ticksElapse = targetLoc.distance(shootLoc) / aimHelperOption.projectileSpeed, lastTicksOffset;
        // approximate the velocity to use with epochs requested
        for (int currEpoch = 0; currEpoch < aimHelperOption.epoch; currEpoch ++) {
            // calculate the predicted enemy location
            double ticksMonsterMovement = ticksElapse + aimHelperOption.ticksMonsterExtra;
            {
                predictedLoc = targetLoc.clone();
                // account for displacement that are caused by velocity
                predictedLoc.add(enemyVel.clone().multiply(ticksMonsterMovement * predictionIntensity));
                // account for displacement that are caused by acceleration, IF NEEDED
                // first tick acc. is in effect for (n-1) times, second for (n-2) and so on
                // in total = sum(1, 2, ..., n-2, n-1) = n(n-1) / 2
                if (aimHelperOption.useAcceleration)
                    predictedLoc.add(enemyAcc.clone().multiply(ticksMonsterMovement * (ticksMonsterMovement - 1) * predictionIntensity / 2d));
                // before handling gravity, make sure entities that clip with block do not go through blocks
                // note that the loop is done with the "foot" position
                if ( checkBlockColl ) {
                    // loopBeginLoc is the position from where the rough entity movement check STARTS in the current loop call
                    Location loopBeginLoc = target.getLocation().add(0, 1e-5, 0);
                    Vector locOffset = targetLoc.clone().subtract(loopBeginLoc).toVector();
                    // the loop end loc should be the "foot" of entity, because they are effected by gravity.
                    Location loopEndLoc = predictedLoc.clone().subtract(locOffset);
                    World loopWorld = loopBeginLoc.getWorld();
                    // check for block collision for max of 3 times (this is meant to be a rough estimation after all).
                    for (int blockCheckIdx = 0; blockCheckIdx < 3; blockCheckIdx ++) {
                        // terminate if the initial loc and final loc are really close to each other
                        if (loopBeginLoc.distanceSquared(loopEndLoc) < 1e-5)
                            break;
                        MovingObjectPosition blockCollInfo = HitEntityInfo.rayTraceBlocks(loopWorld, loopBeginLoc.toVector(), loopEndLoc.toVector());
                        // no block hit: terminate loop
                        if (blockCollInfo == null)
                            break;
                        // block hit: handle velocity change
                        loopBeginLoc = MathHelper.toBukkitVector(blockCollInfo.pos).toLocation(loopWorld);
                        Vector updatedMoveDir = loopEndLoc.subtract(loopBeginLoc).toVector();
                        switch (blockCollInfo.direction) {
                            case UP:
                            case DOWN:
                                updatedMoveDir.setY(0);
                                break;
                            case EAST:
                            case WEST:
                                updatedMoveDir.setX(0);
                                break;
                            default:
                                updatedMoveDir.setZ(0);
                                break;
                        }
                        // update the expected new location based on new velocity
                        loopEndLoc = loopBeginLoc.clone().add(updatedMoveDir);
                    }
                    // account for the foot loc offset
                    predictedLoc = loopEndLoc.add(locOffset);
                }
                // projectile gravity, it is equivalent to target acceleration, for the ease of computation
                // it is not simply acceleration - it only takes effect after some time, usually 5 ticks
                if (ticksElapse >= aimHelperOption.noGravityTicks) {
                    predictedLoc.add(new Vector(0,
                            (ticksElapse - aimHelperOption.noGravityTicks + 1) * (ticksElapse - aimHelperOption.noGravityTicks + 2)
                                    * aimHelperOption.projectileGravity / 2d, 0));
                }
                // random offset
                {
                    double randomOffset = aimHelperOption.randomOffsetRadius;
                    if (randomOffset > 1e-5) {
                        double randomOffsetHalved = randomOffset / 2;
                        predictedLoc.add(Math.random() * randomOffset - randomOffsetHalved,
                                Math.random() * randomOffset - randomOffsetHalved,
                                Math.random() * randomOffset - randomOffsetHalved);
                    }
                }
            }

            // then, update actual ticks needed to reach the designated point
            lastTicksOffset = ticksElapse;
            if (aimHelperOption.useTickOrSpeedEstimation)
                ticksElapse = aimHelperOption.ticksTotal;
            else {
                double distance = predictedLoc.distance(shootLoc), currSpd = aimHelperOption.projectileSpeed;
                // if speed does not change over time, use that speed
                if (aimHelperOption.projectileSpeedMulti == 1) {
                    ticksElapse = distance / currSpd;
                }
                // if a speed multiplier is in place, account for it.
                else {
                    ticksElapse = 0;
                    double distTraveled = 0;
                    // prevent possible inf loop IF the projectile has decaying speed
                    while (distTraveled < distance && (aimHelperOption.projectileSpeedMulti >= 1d || ticksElapse < 20)) {
                        ticksElapse ++;
                        distTraveled += currSpd;
                        currSpd *= aimHelperOption.projectileSpeedMulti;
                        // after reaching the max speed, do not bother using the loop
                        if (currSpd > aimHelperOption.projectileSpeedMax) {
                            ticksElapse += (distance - distTraveled) / aimHelperOption.projectileSpeedMax;
                            break;
                        }
                    }
                }
                // account for at most 3 seconds
                ticksElapse = Math.min( Math.floor(ticksElapse),  60  );
            }

            // end the loop early if the last tick offset agrees with the current
            if (lastTicksOffset == ticksElapse)
                break;
        }
        return predictedLoc;
    }
    public static Location helperAimEntity(Entity source, Entity target, AimHelperOptions aimHelperOption) {
        Location shootLoc = source.getLocation();
        if (source instanceof LivingEntity)
            shootLoc = ((LivingEntity) source).getEyeLocation();
        return helperAimEntity(shootLoc, target, aimHelperOption);
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
                    segmentCurrent.setVelocity(new Vector());
                }
                setMetadata(segmentCurrent, "yaw", (float) MathHelper.getVectorYaw( dVec ));
                setMetadata(segmentCurrent, "pitch", (float) MathHelper.getVectorPitch( dVec ));
            }
        }
    }
}
