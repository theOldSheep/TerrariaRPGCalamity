package terraria.util;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.*;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.*;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.projectile.GenericProjectile;
import terraria.gameplay.Setting;

import java.util.*;
import java.util.logging.Level;

public class EntityHelper {
    // constants
    static HashMap<String, Set<String>> buffInferior, buffSuperior;
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
        BULLET_HELL_PROJECTILE_DIRECTION("bltHellDir"),
        CALAMITAS_PROJECTILE_TICKS_LIVED("projectileTicksLive"),
        CALAMITAS_PROJECTILE_ORIGINAL("projectileOriginal"),
        CELESTIAL_PILLAR_SHIELD("shield"),
        DAMAGE_SOURCE("damageSourcePlayer"),
        DAMAGE_TAKER("damageTaker"),
        DAMAGE_TYPE("damageType"),
        DPS_HITS("dpsHits"),
        DPS_DMG_TOTAL("dpsDmg"),
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
        PLAYER_ACCELERATION("playerAcl"),
        PLAYER_AIR("playerAir"),
        PLAYER_BIOME("playerBiome"),
        PLAYER_CRAFTING_RECIPE_INDEX("recipeNumber"),
        PLAYER_CRAFTING_STATION("craftingStation"),
        PLAYER_DAMAGE_SOUND_MEMO("lastDmgPlayed"),
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
        // the velocity in world, for the last tick
        PLAYER_LAST_VELOCITY_ACTUAL("plyLastVel"),
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
        PLAYER_SENTRY_LIST("sentries"),
        PLAYER_STEALTH("stealth"),
        PLAYER_TARGET_LOC_CACHE("targetLocCache"),
        PLAYER_TEAM("team"),
        PLAYER_TELEPORT_TARGET("teleportTarget"),
        PLAYER_THRUST_INDEX("thrustIndex"),
        PLAYER_THRUST_PROGRESS("thrustProgress"),
        PLAYER_TRASH_ITEMS("trashItems"),
        PLAYER_VELOCITY_MULTI("plyVelMulti"),
        PLAYER_VELOCITY_INTERNAL("plyVelItn"),
        PROJECTILE_BOUNCE_LEFT("bounce"),
        PROJECTILE_DESTROY_REASON("destroyReason"),
        PROJECTILE_ENTITIES_COLLIDED("collided"),
        PROJECTILE_LAST_HIT_ENTITY("projLHE"),
        PROJECTILE_PENETRATION_LEFT("penetration"),
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

    // helper functions
    public static void initEntityMetadata(Entity entity) {
        // in this function call, potion effect map is initialized when needed
        getEffectMap(entity);

        setMetadata(entity, MetadataName.DAMAGE_TYPE, DamageHelper.DamageType.MELEE);
        setMetadata(entity, MetadataName.BUFF_IMMUNE, new HashMap<String, Integer>());
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
    // It's OKAY to use this, but prefer the MetadataName for consistency!
    public static void setMetadata(Metadatable owner, String key, Object value) {
        if (value == null)
            owner.removeMetadata(key, TerrariaHelper.getInstance());
        else
            owner.setMetadata(key, new FixedMetadataValue(TerrariaHelper.getInstance(), value));
    }
    public static void setMetadata(Metadatable owner, MetadataName key, Object value) {
        setMetadata(owner, key.toString(), value);
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

    public static void slimeResize(Slime slime, int size) {
        if (size < 1) return;
        EntitySlime nms_slime = ((CraftSlime) slime).getHandle();
        double maxHealth = nms_slime.getAttributeInstance(GenericAttributes.maxHealth).getValue();
        double moveSpeed = nms_slime.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).getValue();
        nms_slime.setSize(size, false);
        nms_slime.getAttributeInstance(GenericAttributes.maxHealth).setValue(maxHealth);
        nms_slime.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(moveSpeed);
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

    private static void tickEffect(Entity entity, String effect, int delay, double damagePerDelay, String particleID, boolean isFirstTicking) {
        try {
            HashMap<String, Integer> allEffects = getEffectMap(entity);
            int timeRemaining = allEffects.getOrDefault(effect, 0);
            // some buff should not (solely) disappear over time, they disappear over some other criterion.
            switch (effect) {
                case "血肉图腾":
                    if (! PlayerHelper.getAccessories(entity).contains("血肉图腾"))
                        timeRemaining = -1;
                    break;
                case "防御损毁":
                    if (! allEffects.containsKey("防御修补冷却"))
                        timeRemaining -= 2;
                    break;
                case "血炎防御损毁":
                    PlayerHelper.heal((LivingEntity) entity, 2);
                    timeRemaining -= 2;
                    break;
                case "保护矩阵":
                    HashMap<String, Double> attrMap = AttributeHelper.getAttrMap(entity);
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
                DamageHelper.handleDamage(entity, entity, damagePerDelay * damageMulti, DamageHelper.DamageReason.DEBUFF, effect);
            }
            switch (effect) {
                case "扭曲": {
                    if (entity instanceof Player) {
                        Entity mount = PlayerHelper.getMount((Player) entity);
                        Entity twistedEntity = mount == null ? entity : mount;
                        double targetLocY = WorldHelper.getHighestBlockBelow(twistedEntity.getLocation()).getLocation().getY();
                        targetLocY += 8 + MathHelper.xsin_degree(timeRemaining * 2.5) * 2;
                        double velY = targetLocY - twistedEntity.getLocation().getY();
                        velY /= 6;
                        double maxVerticalSpeed = 0.5;
                        if (velY < -maxVerticalSpeed) {
                            velY = -maxVerticalSpeed;
                        } else if (velY > maxVerticalSpeed) {
                            velY = maxVerticalSpeed;
                        }
                        Vector velocity = EntityMovementHelper.getRawVelocity(twistedEntity);
                        velocity.setY(velY);
                        EntityMovementHelper.setVelocity(twistedEntity, velocity);
                        entity.setFallDistance(0);
                    }
                    break;
                }
                case "恐惧": {
                    if (BossHelper.bossMap.containsKey(BossHelper.BossType.WALL_OF_FLESH.msgName)) {
                        Entity damager = BossHelper.bossMap.get(BossHelper.BossType.WALL_OF_FLESH.msgName).get(0);
                        if (damager.getWorld() != entity.getWorld() ||
                                damager.getLocation().distanceSquared(entity.getLocation()) > 10000)
                            DamageHelper.handleDamage(damager, entity, 114514, DamageHelper.DamageReason.DEBUFF, effect);
                    }
                    else {
                        allEffects.put(effect, 0);
                    }
                    break;
                }
            }
            // particle
            if (particleID != null && (isFirstTicking || timeRemaining % 20 <= delay) ) {
                DragoncoreHelper.displayBlizzardParticle((ply) -> Setting.getOptionBool(ply, Setting.Options.DEBUFF_PARTICLE_TOGGLE),
                        new DragoncoreHelper.DragonCoreParticleInfo(particleID, entity), 20);
            }
            // next delayed task
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () -> tickEffect(entity, effect, delay, damagePerDelay, particleID, false), delay);
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
                AttributeHelper.tweakAllAttributes(entity, effectAttributeSection, false);
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
                case "防御损毁":
                case "血炎防御损毁":
                    delay = 1;
                    break;
                default:
                    delay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damageInterval", delay);
                    damagePerDelay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damage", damagePerDelay);
                    if (!(entity instanceof Player || entity.getScoreboardTags().contains("isNPC")))
                        damagePerDelay = TerrariaHelper.buffConfig.getInt("effects." + effect + ".damageMonster", damagePerDelay);
            }
            // tweak attrMap
            if (entity instanceof Player)
                PlayerHelper.setupAttribute((Player) entity);
            else {
                String attributesPath = "effects." + effect + ".attributes.";
                ConfigurationSection effectSection = TerrariaHelper.buffConfig.getConfigurationSection(attributesPath);
                AttributeHelper.tweakAllAttributes(entity, effectSection, true);
            }
            // register delayed task for ticking potion
            int finalDamagePerDelay = damagePerDelay;
            int finalDelay = delay;
            String particleID = TerrariaHelper.buffConfig.getString("effects." + effect + ".snowStormParticle");
            Bukkit.getScheduler().scheduleSyncDelayedTask(TerrariaHelper.getInstance(), () ->
                    tickEffect(entity, effect, finalDelay, finalDamagePerDelay, particleID, true), delay);
        } catch (Exception e) {
            TerrariaHelper.LOGGER.log(Level.SEVERE, "[Entity Helper] prepareTickEffect", e);
        }
    }

    /**
     * Applies an effect with the given duration to the entity.
     * @param entity The entity to apply the effect.
     * @param effect The effect name to apply.
     * @param applyDurationTicks The number of ticks to apply the effect.
     */
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
                case "防御损毁":
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
    /**
     * Removes the specified effect for the entity.
     * @param entity The entity from which to remove an effect.
     * @param effect The effect name to be removed.
     */
    public static void removeEffect(Entity entity, String effect) {
        endTickEffect(entity, effect, getEffectMap(entity), true);
    }

    public static void knockback(Entity entity, Vector dir, boolean addOrReplace) {
        knockback(entity, dir, addOrReplace, -1);
    }

    public static void knockback(Entity entity, Vector dir, boolean addOrReplace, double speedLimit) {
        knockback(entity, dir, addOrReplace, speedLimit, false);
    }

    public static void knockback(Entity entity, Vector dir, boolean addOrReplace, double speedLimit, boolean ignoreKBR) {
        double kbMulti;
        if (ignoreKBR) {
            kbMulti = 1d;
        }
        else {
            // entities immune to knockback should not be effected at all
            double kbResistance = AttributeHelper.getAttrMap(entity).getOrDefault("knockbackResistance", 0d);
            if (kbResistance >= 1) return;
            // determine the knockback acceleration
            kbMulti = Math.max(1 - kbResistance, 0);
            dir = dir.clone().multiply(kbMulti);
        }
        // update the knockback slow factor, which effects the walking speed of zombies etc.
        setMetadata(entity, MetadataName.KNOCKBACK_SLOW_FACTOR, kbMulti);
        // the entity subject to that knockback
        Entity knockbackTaker = EntityMovementHelper.getMount(entity);
        if (knockbackTaker == null) knockbackTaker = entity;
        // minecart takes no knockback
        if (knockbackTaker instanceof Minecart)
            return;
        // calculate the final velocity
        Vector finalVel = EntityMovementHelper.getRawVelocity(knockbackTaker);
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
        EntityMovementHelper.setVelocity(knockbackTaker, finalVel);
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
        double dmg = AttributeHelper.getAttrMap(source).getOrDefault("damage", 25d);
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
        loc.getWorld().playSound(loc, "entity.generic.explode", SoundCategory.HOSTILE, (float) radius + 2, 1f);
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
                    DamageHelper.handleDamage(source, victim, dmg, DamageHelper.DamageReason.EXPLOSION);
                continue;
            }
            if (damageExceptions.contains(victim)) continue;
            if (DamageHelper.checkCanDamage(source, victim, false))
                DamageHelper.handleDamage(source, victim, dmg, DamageHelper.DamageReason.EXPLOSION);
        }
        // destroy block
        if (destroyBlock) {
            Player ply = null;
            Entity sourceEntity = DamageHelper.getDamageSource(source);
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
        public net.minecraft.server.v1_12_R1.Entity lockedTarget;
        public Location shootLoc;
        public Vector velocity;
        public HashMap<String, Double> attrMap;
        public HashMap<String, Object> properties;
        public DamageHelper.DamageType damageType;
        public String projectileName, projectileItemName;
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
                    // projectiles with health would need those attributes
                    case "health":
                    case "healthMax":
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
        public void setLockedTarget(Entity lockedTargetBukkit) {
            if (lockedTargetBukkit != null) {
                this.lockedTarget = ((CraftEntity) lockedTargetBukkit).getHandle();
            } else {
                this.lockedTarget = null;
            }
        }
        // constructors
        public ProjectileShootInfo(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String projectileName) {
            this(shooter, velocity, attrMap, DamageHelper.getDamageType(shooter), projectileName);
        }
        public ProjectileShootInfo(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, DamageHelper.DamageType damageType, String projectileName) {
            this(shooter,
                    (shooter instanceof LivingEntity) ? ((LivingEntity) shooter).getEyeLocation() : shooter.getLocation(),
                    velocity, attrMap, damageType, projectileName);
        }
        public ProjectileShootInfo(Entity shooter, Location shootLoc, Vector velocity, HashMap<String, Double> attrMap, DamageHelper.DamageType damageType, String projectileName) {
            this.shooter = null;
            if (shooter instanceof ProjectileSource) this.shooter = (ProjectileSource) shooter;
            this.lockedTarget = null;
            this.shootLoc = shootLoc;
            this.velocity = velocity;
            this.attrMap = extractAttrMap(attrMap);
            this.properties = new HashMap<>(25);
            this.damageType = damageType;
            this.projectileName = projectileName;
            this.projectileItemName = null;
            boolean arrowOrPotion = projectileName.endsWith("箭");
            this.arrowOrPotion = TerrariaHelper.projectileConfig.getBoolean(projectileName + ".arrowOrPotion", arrowOrPotion);

            if (projectileName.length() > 0) {
                ConfigurationSection section = TerrariaHelper.projectileConfig.getConfigurationSection(projectileName);
                if (section != null) {
                    this.projectileItemName = section.getString("projDisguiseName");
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
                                "frictionFactor", "gravity", "healOnHit", "maxSpeed", "projectileSize", "spawnSoundPitch",
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
        }

    }

    public static Projectile spawnProjectile(ProjectileShootInfo shootInfo) {
        CraftWorld wld = (CraftWorld) shootInfo.shootLoc.getWorld();
        Projectile bukkitProjectile;
        GenericProjectile entity = new GenericProjectile(shootInfo);
        bukkitProjectile = new CraftSplashPotion(wld.getHandle().getServer(), entity);
        return bukkitProjectile;
    }

    // the signatures below are not as flexible comparing to the function above
    public static Projectile spawnProjectile(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, String projectileName) {
        return spawnProjectile(shooter, velocity, attrMap, DamageHelper.getDamageType(shooter), projectileName);
    }

    public static Projectile spawnProjectile(Entity shooter, Vector velocity, HashMap<String, Double> attrMap, DamageHelper.DamageType damageType, String projectileName) {
        Location shootLoc;
        if (shooter instanceof LivingEntity) shootLoc = ((LivingEntity) shooter).getEyeLocation();
        else shootLoc = shooter.getLocation();
        return spawnProjectile(shooter, shootLoc, velocity, attrMap, damageType, projectileName);
    }

    public static Projectile spawnProjectile(Entity shooter, Location shootLoc, Vector velocity, HashMap<String, Double> attrMap, DamageHelper.DamageType damageType, String projectileName) {
        ProjectileShootInfo shootInfo = new ProjectileShootInfo(shooter, shootLoc, velocity, attrMap, damageType, projectileName);
        return spawnProjectile(shootInfo);
    }

    // returns a random point in the bounding box, for damage hologram display etc.
    public static class RandomPosInBBInfo {
        boolean sideEdgesOnly = false;
        double BBShrinkX = 0;
        double BBShrinkYTop = 0;
        double BBShrinkYBottom = 0;
        double BBShrinkZ = 0;

        public RandomPosInBBInfo setSideEdgesOnly(boolean sideEdgesOnly) {
            this.sideEdgesOnly = sideEdgesOnly;
            return this;
        }
        public RandomPosInBBInfo setBBShrinkX(double BBShrinkX) {
            this.BBShrinkX = BBShrinkX;
            return this;
        }
        public RandomPosInBBInfo setBBShrinkYTop(double BBShrinkYTop) {
            this.BBShrinkYTop = BBShrinkYTop;
            return this;
        }
        public RandomPosInBBInfo setBBShrinkYBottom(double BBShrinkYBottom) {
            this.BBShrinkYBottom = BBShrinkYBottom;
            return this;
        }
        public RandomPosInBBInfo setBBShrinkZ(double BBShrinkZ) {
            this.BBShrinkZ = BBShrinkZ;
            return this;
        }
    }
    public static Location getRandomPosInEntity(Entity entity, RandomPosInBBInfo info) {
        return getRandomPosInBoundingBox(entity.getWorld(), ((CraftEntity) entity).getHandle().getBoundingBox(), info);
    }
    public static Location getRandomPosInBoundingBox(World world, AxisAlignedBB boundingBox, RandomPosInBBInfo info) {
        double ySizeChange = (info.BBShrinkYTop + info.BBShrinkYBottom) / 2;
        AxisAlignedBB boundingBoxActual = boundingBox
                .grow(-info.BBShrinkX, -ySizeChange, -info.BBShrinkZ)
                .d(0, ySizeChange - info.BBShrinkYTop, 0);
        // get the random loc
        double xRatio, yRatio, zRatio;
        if (info.sideEdgesOnly) {
            yRatio = Math.random();
            if (Math.random() < 0.5) {
                xRatio = Math.random() < 0.5 ? 0 : 1;
                zRatio = Math.random();
            }
            else {
                xRatio = Math.random();
                zRatio = Math.random() < 0.5 ? 0 : 1;
            }
        }
        else {
            xRatio = Math.random();
            yRatio = Math.random();
            zRatio = Math.random();
        }
        return new Location(world,
                boundingBoxActual.a + (boundingBoxActual.d - boundingBoxActual.a) * xRatio,
                boundingBoxActual.b + (boundingBoxActual.e - boundingBoxActual.b) * yRatio,
                boundingBoxActual.c + (boundingBoxActual.f - boundingBoxActual.c) * zRatio);
    }

}
