package terraria.util;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.SlimeWatcher;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;
import terraria.entity.boss.event.celestialPillar.CelestialPillar;
import terraria.entity.monster.*;
import terraria.gameplay.EventAndTime;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;

public class MonsterHelper {
    // prevent spawning next to any player
    public static final double NO_MONSTER_SPAWN_RADIUS = 16d;
    public static class UniqueMonsterInfo {
        Entity uniqueMonster;
        long spawnedTime;
        public UniqueMonsterInfo(Entity monster) {
            this.uniqueMonster = monster;
            spawnedTime = Calendar.getInstance().getTimeInMillis();
        }
    }
    public static HashMap<String, UniqueMonsterInfo> uniqueMonsters = new HashMap<>();
    private static boolean naturalMobSpawnType(Player ply, String spawnType) {
        // determine the monster spawning location
        WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(ply.getLocation());
        Location spawnLoc;
        int adjustHeight;
        if (heightLayer == WorldHelper.HeightLayer.SURFACE) {
            spawnLoc = ply.getLocation().add(Math.random() * 96 - 48, Math.random() * 32 - 16, Math.random() * 96 - 48);
            adjustHeight = 32;
        } else {
            spawnLoc = ply.getLocation().add(Math.random() * 64 - 32, Math.random() * 40 - 20, Math.random() * 64 - 32);
            adjustHeight = 24;
        }
        // determine the list of candidate spawning monsters
        ConfigurationSection candidateMonsterSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection("spawnInfo." + spawnType);
        if (candidateMonsterSection == null)
            return false;
        Collection<String> allMonsters = candidateMonsterSection.getKeys(false);
        HashMap<String, Double> spawnInfoMap = new HashMap<>();
        for (String monsterCandidate : allMonsters) {
            // if the gameplay progress criteria has been met
            if (terraria.entity.monster.MonsterHelper.validateMonsterProgress(ply, null, monsterCandidate)) {
                double spawnWeight = candidateMonsterSection.getDouble(monsterCandidate, 1d);
                spawnInfoMap.put(monsterCandidate, spawnWeight);
            }
        }
        if (spawnInfoMap.size() == 0)
            return false;
        // determine the monster to spawn
        String monsterSpawn = MathHelper.selectWeighedRandom(spawnInfoMap);
        if (monsterSpawn.length() == 0)
            return false;
        // tweak the spawning location to make it valid
        String spawnLocationType = TerrariaHelper.mobSpawningConfig.getString(
                "mobInfo." + monsterSpawn + ".spawnLocationType", "GROUND");
        switch (spawnLocationType) {
            case "GROUND": {
                // so that the loop does not terminate at once
                boolean lastLocValid = true;
                for (int i = 1; i <= adjustHeight; i++) {
                    boolean currLocValid = spawnLoc.getY() > 0 && spawnLoc.getY() < 256;
                    Material currBlockMat = spawnLoc.getBlock().getType();
                    // no spawning in liquid!
                    switch (currBlockMat) {
                        case WATER:
                        case STATIONARY_WATER:
                        case LAVA:
                        case STATIONARY_LAVA:
                            return true;
                    }
                    if (currBlockMat.isSolid()) currLocValid = false;
                    // the block became valid
                    if (!lastLocValid && currLocValid) {
                        spawnLoc.setY(Math.floor(spawnLoc.getY()));
                        break;
                    }
                    // tweak location to find appropriate spawn loc
                    if (lastLocValid) // last is valid (air): move down
                        spawnLoc.add(0, -1, 0);
                    else // last not valid (solid): move up
                        spawnLoc.add(0, 1, 0);
                    lastLocValid = currLocValid;
                    // no proper location found
                    if (i == adjustHeight) return true;
                }
                break;
            }
            case "AIR": {
                Material currBlockMat = spawnLoc.getBlock().getType();
                if (currBlockMat != Material.AIR) return true;
                break;
            }
            case "WATER": {
                Material currBlockMat = spawnLoc.getBlock().getType();
                switch (currBlockMat) {
                    case WATER:
                    case STATIONARY_WATER:
                        break;
                    default:
                        return true;
                }
                break;
            }
            case "SOLID": {
                Material currBlockMat = spawnLoc.getBlock().getType();
                if (!currBlockMat.isSolid()) return true;
                break;
            }
        }
        // finally, handle some special cases
        if (spawnType.equals("史莱姆雨"))
            spawnLoc.add(0, 30, 0);
        // prevent spawning next to any player
        for (Entity e : spawnLoc.getWorld().getNearbyEntities(spawnLoc, NO_MONSTER_SPAWN_RADIUS, NO_MONSTER_SPAWN_RADIUS, NO_MONSTER_SPAWN_RADIUS)) {
            if (e instanceof Player && PlayerHelper.isProperlyPlaying((Player) e) )
                return true;
        }
        // spawn mob and return success
        spawnMob(monsterSpawn, spawnLoc, ply);
        return true;
    }
    public static void naturalMobSpawning(Player ply) {
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(ply);
        int mobLimit = attrMap.getOrDefault("mobLimit", 10d).intValue();
        if (EntityHelper.getMetadata(ply, EntityHelper.MetadataName.PLAYER_MONSTER_SPAWNED_AMOUNT).asInt() >= mobLimit)
            return;
        // celestial pillars
        WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(ply.getLocation());
        boolean isSurfaceOrSpace =
                heightLayer == WorldHelper.HeightLayer.SURFACE ||
                heightLayer == WorldHelper.HeightLayer.SPACE;
        if (isSurfaceOrSpace) {
            for (CelestialPillar pillar : EventAndTime.pillars.values()) {
                if (pillar.getBukkitEntity().getWorld() != ply.getWorld())
                    continue;
                if (pillar.getBukkitEntity().getLocation().distanceSquared(ply.getLocation()) < CelestialPillar.EFFECTED_RADIUS_SQR) {
                    if ( naturalMobSpawnType(ply, pillar.getCustomName()) )
                        return;
                }
            }
        }
        // event mob
        PlayerHelper.GameProgress gameProgress = PlayerHelper.getGameProgress(ply);
        if (heightLayer == WorldHelper.HeightLayer.SURFACE) {
            switch (EventAndTime.currentEvent) {
                case FROST_MOON:
                case PUMPKIN_MOON:
                    boolean canSpawn = true;
                    switch (gameProgress) {
                        case PRE_WALL_OF_FLESH:
                        case PRE_PLANTERA:
                            canSpawn = false;
                    }
                    if (canSpawn) {
                        int eventTier = EventAndTime.eventInfo.getOrDefault(
                                EventAndTime.EventInfoMapKeys.EVENT_WAVE, 1d).intValue();
                        if (naturalMobSpawnType(ply, EventAndTime.currentEvent.toString() + eventTier))
                            return;
                    }
                    break;
            }
        }
        // reduced mob spawning rate if a boss is alive
        if (BossHelper.bossMap.size() > 0 && Math.random() < 0.75) {
            return;
        }
        // reduced mob spawning rate when no event is present
        if (EventAndTime.currentEvent == EventAndTime.Events.NONE && Math.random() < 0.5) {
            return;
        }
        // only attempt to spawn underworld mobs in underworld
        WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply);
        String  biomeStr = biomeType.toString().toLowerCase(),
                heightStr = heightLayer.toString().toLowerCase();
        // do not attempt anything else for underworld
        switch (biomeType) {
            case UNDERWORLD:
            case BRIMSTONE_CRAG:
                naturalMobSpawnType(ply, biomeStr);
                return;
        }
        // event mob spawning
        if (heightLayer == WorldHelper.HeightLayer.SURFACE) {
            if (EventAndTime.currentEvent != EventAndTime.Events.NONE &&
                    naturalMobSpawnType(ply, EventAndTime.currentEvent.toString()))
                return;
            if (WorldHelper.isDayTime(ply.getWorld()))
                heightStr = "day";
            else
                heightStr = "night";
        }
        if (naturalMobSpawnType(ply, biomeStr + "_" + heightStr))
            return;
        if (naturalMobSpawnType(ply, biomeStr))
            return;
        naturalMobSpawnType(ply, heightStr);
    }
    public static Entity spawnMob(String type, Location loc, Player target) {
        // monsters are prohibited to spawn when the active monsters are exceeding target's mob limit by 3 times
        HashMap<String, Double> attrMap = EntityHelper.getAttrMap(target);
        int mobLimit = attrMap.getOrDefault("mobLimit", 10d).intValue();
        if (EntityHelper.getMetadata(target, EntityHelper.MetadataName.PLAYER_MONSTER_SPAWNED_AMOUNT).asInt() >= mobLimit * 3)
            return null;
        // get monster info
        ConfigurationSection mobInfoSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection("mobInfo." + type);
        boolean unique = mobInfoSection.getBoolean("unique", false);
        if (unique && uniqueMonsters.containsKey(type)) {
            UniqueMonsterInfo currMonster = uniqueMonsters.get(type);
            // timeout after 2.5 minutes (150 seconds or 150 000 ms)
            if (currMonster.spawnedTime < Calendar.getInstance().getTimeInMillis() - 150000)
                currMonster.uniqueMonster.remove();
            else if (!currMonster.uniqueMonster.isDead())
                return null;
        }
        String entityType = mobInfoSection.getString("monsterType", "SLIME");
        Entity entity;
        {
            boolean isBaby = false;
            switch (entityType) {
                case "SLIME": {
                    entity = (new MonsterSlime(target, type, loc)).getBukkitEntity();
                    break;
                }
                case "BABY_ZOMBIE":
                    isBaby = true;
                case "ZOMBIE": {
                    entity = (new MonsterZombie(target, type, loc, isBaby)).getBukkitEntity();
                    break;
                }
                case "BABY_HUSK":
                    isBaby = true;
                case "HUSK": {
                    entity = (new MonsterHusk(target, type, loc, isBaby)).getBukkitEntity();
                    break;
                }
                case "SKELETON": {
                    entity = (new MonsterSkeleton(target, type, loc)).getBukkitEntity();
                    break;
                }
                case "WITHER_SKELETON": {
                    entity = (new MonsterWitherSkeleton(target, type, loc)).getBukkitEntity();
                    break;
                }
                case "SILVERFISH": {
                    entity = (new MonsterSilverfish(target, type, loc)).getBukkitEntity();
                    break;
                }
                case "SPIDER": {
                    entity = (new MonsterSpider(target, type, loc)).getBukkitEntity();
                    break;
                }
                case "CAVE_SPIDER": {
                    entity = (new MonsterCaveSpider(target, type, loc)).getBukkitEntity();
                    break;
                }
                default:
                    TerrariaHelper.getInstance().getLogger().log(Level.SEVERE, "UNHANDLED MONSTER TYPE: " + entityType);
                    return null;
            }
        }
        // set mechanic sound
        boolean isMechanic = mobInfoSection.getBoolean("isMechanic", false);
        if (isMechanic) {
            entity.addScoreboardTag("isMechanic");
        }
        // set disguise
        String disguiseType = mobInfoSection.getString("disguiseType");
        if (disguiseType != null) {
            Disguise disguise;
            if (disguiseType.startsWith("SLIME_")) {
                disguiseType = disguiseType.replace("SLIME_", "");
                int size = Integer.parseInt(disguiseType);
                disguise = new MobDisguise(DisguiseType.SLIME, true);
                ((SlimeWatcher) disguise.getWatcher()).setSize(size);
            } else {
                boolean isAdult = true;
                if (disguiseType.startsWith("BABY_")) {
                    isAdult = false;
                    disguiseType = disguiseType.replace("BABY_", "");
                }
                disguise = new MobDisguise(DisguiseType.valueOf(disguiseType), isAdult);
            }
            disguise.setReplaceSounds(true);
            disguise.setEntity(entity);
            DisguiseAPI.disguiseEntity(entity, disguise);
        }
        // set parent type
        EntityHelper.setMetadata(entity, EntityHelper.MetadataName.MONSTER_PARENT_TYPE, type);
        // unique monster cache
        if (unique) {
            uniqueMonsters.put(type, new UniqueMonsterInfo(entity) );
        }
        return entity;
    }
}
