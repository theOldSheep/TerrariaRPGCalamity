package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import terraria.TerrariaHelper;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

public class MetadataHelper {
    private static final HashMap<String, MetadataName> METADATA_NAME_MAPPING = new HashMap<>();
    // blocks' metadata should last for 1 minute
    private static final long BLOCK_METADATA_LIFETIME_MS = 60 * 1000;
    // entities and keys of their metadata
    private static Map<Entity, Collection<String>> entityMetadataKeys = new HashMap<>();
    // blocks and keys of their metadata
    private static Map<Block, Collection<String>> blockMetadataKeys = new HashMap<>();
    // blocks and the timestamp (ms) of their most recent metadata update
    private static Map<Block, Long> blockMetadataUpdateMs = new HashMap<>();

    // init the entity's metadata when necessary
    public static void initEntityMetadata(Entity entity) {
        // in this function call, potion effect map is initialized when needed
        EntityHelper.getEffectMap(entity);

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
        if (value == null) {
            owner.removeMetadata(key, TerrariaHelper.getInstance());
        }
        else {
            owner.setMetadata(key, new FixedMetadataValue(TerrariaHelper.getInstance(), value));
            if (owner instanceof Entity) {
                entityMetadataKeys
                        .computeIfAbsent((Entity) owner, (e) -> new HashSet<>())
                        .add(key);
            } else if (owner instanceof Block) {
                blockMetadataKeys
                        .computeIfAbsent((Block) owner, (e) -> new HashSet<>())
                        .add(key);
                blockMetadataUpdateMs.put((Block) owner, System.currentTimeMillis());
            }
        }
    }

    public static void setMetadata(Metadatable owner, MetadataName key, Object value) {
        setMetadata(owner, key.toString(), value);
    }

    // bukkit do not remove entity/block metadata; manual GC is necessary.
    public static void threadMetadataGC() {
        // every 10 seconds (200 ticks)
        Bukkit.getScheduler().runTaskTimer(TerrariaHelper.getInstance(), () -> {
            // entity metadata GC
            {
                HashSet<Entity> removedEntities = new HashSet<>();
                for (Entity e : entityMetadataKeys.keySet()) {
                    boolean shouldGC = e.isDead();
                    // do not GC metadata of players if they are reconnected
                    if (shouldGC && e instanceof Player) {
                        Player plyE = (Player) e;
                        if (Bukkit.getPlayer(plyE.getUniqueId()) != null) {
                            shouldGC = false;
                        }
                    }
                    if (shouldGC) {
                        // remove all metadata associated
                        for (String key : entityMetadataKeys.get(e)) {
                            setMetadata(e, key, null);
                        }
                        removedEntities.add(e);
                    }
                }
                // purge the entity metadata tracking
                for (Entity e : removedEntities) {
                    entityMetadataKeys.remove(e);
                }
            }
            // block metadata GC
            {
                HashSet<Block> removedBlocks = new HashSet<>();
                for (Block b : blockMetadataKeys.keySet()) {
                    if (System.currentTimeMillis() - blockMetadataUpdateMs.getOrDefault(b, System.currentTimeMillis()) > BLOCK_METADATA_LIFETIME_MS) {
                        // remove all metadata associated
                        for (String key : blockMetadataKeys.get(b)) {
                            setMetadata(b, key, null);
                        }
                        removedBlocks.add(b);
                    }
                }
                // purge the block metadata tracking
                for (Block b : removedBlocks) {
                    blockMetadataKeys.remove(b);
                    blockMetadataUpdateMs.remove(b);
                }
            }
        }, 0, 200);
    }

    // use this enum members for consistency & eliminating typos
    public enum MetadataName {
        ACCESSORIES("accessory"),
        ACCESSORIES_FLIGHT_BACKUP("accessoryThrust"),
        ACCESSORIES_LIST("accessoryList"),
        ACCESSORY_SWITCHABLE_DISPLAY("accSwtDspl"),
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
        DPS_DMG_TOTAL("dpsDmg"),
        DPS_HITS("dpsHits"),
        DPS_VERSION("dpsVersion"),
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
        THROTTLE_DMG_HOLOGRAM("tDHl"),
        THROTTLE_DPS_ACTION_BAR("tDPS");
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
}
