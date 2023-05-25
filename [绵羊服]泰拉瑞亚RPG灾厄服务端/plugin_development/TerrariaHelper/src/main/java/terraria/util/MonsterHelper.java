package terraria.util;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.SlimeWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.omg.CORBA.TypeCodePackage.BadKind;
import terraria.TerrariaHelper;
import terraria.entity.monster.MonsterHusk;
import terraria.entity.monster.MonsterSlime;
import terraria.entity.monster.MonsterZombie;
import terraria.gameplay.Event;

import java.util.Collection;
import java.util.HashMap;

public class MonsterHelper {
    // prevent spawning next to any player
    public static final double NO_MONSTER_SPAWN_RADIUS = 16d;
    public static HashMap<String, Entity> uniqueMonsters = new HashMap<>();
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
                    if (currBlockMat == Material.WATER || currBlockMat == Material.LAVA) return true;
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
                if (currBlockMat != Material.WATER) return true;
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
        if (monsterSpawn.equals("拜月教教徒") && BossHelper.bossMap.containsKey("拜月教邪教徒"))
            return true;
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
        for (Entity pillar : Event.pillars) {
            if (isSurfaceOrSpace) {
                if (pillar.getWorld() != ply.getWorld())
                    continue;
                if (pillar.getLocation().distanceSquared(ply.getLocation()) < 22500) {
                    // TODO: spawn celestial pillar monster
                    return;
                }
            }
        }
        // event mob
        PlayerHelper.GameProgress gameProgress = PlayerHelper.getGameProgress(ply);
        if (heightLayer == WorldHelper.HeightLayer.SURFACE) {
            switch (Event.currentEvent) {
                case "冰霜月":
                case "南瓜月":
                    boolean canSpawn = true;
                    switch (gameProgress) {
                        case PRE_WALL_OF_FLESH:
                        case PRE_PLANTERA:
                            canSpawn = false;
                    }
                    if (canSpawn) {
                        int eventTier = Event.eventInfo.getOrDefault("tier", 1d).intValue();
                        naturalMobSpawnType(ply, Event.currentEvent + eventTier);
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
        if (Event.currentEvent.length() > 0 && Math.random() < 0.5) {
            return;
        }
        // only attempt to spawn underworld mobs in underworld
        WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply);
        String  biomeStr = biomeType.toString().toLowerCase(),
                heightStr = heightLayer.toString().toLowerCase();
        if (biomeType == WorldHelper.BiomeType.UNDERWORLD) {
            naturalMobSpawnType(ply, biomeStr);
            return;
        }
        // event mob spawning
        if (heightLayer == WorldHelper.HeightLayer.SURFACE) {
            if (Event.currentEvent.length() > 0 && naturalMobSpawnType(ply, Event.currentEvent))
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
        ConfigurationSection mobInfoSection = TerrariaHelper.mobSpawningConfig.getConfigurationSection("mobInfo." + type);
        boolean unique = mobInfoSection.getBoolean("unique", false);
        if (unique && uniqueMonsters.containsKey(type)) {
            if (!uniqueMonsters.get(type).isDead())
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
                default:
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
                boolean isBaby = false;
                if (disguiseType.startsWith("BABY_")) {
                    isBaby = true;
                    disguiseType = disguiseType.replace("BABY_", "");
                }
                disguise = new MobDisguise(DisguiseType.valueOf(disguiseType), isBaby);
            }
            disguise.setReplaceSounds(true);
            disguise.setEntity(entity);
            DisguiseAPI.disguiseEntity(entity, disguise);
        }
        // set parent type
        EntityHelper.setMetadata(entity, EntityHelper.MetadataName.MONSTER_PARENT_TYPE, type);
        // unique monster cache
        if (unique) {
            uniqueMonsters.put(type, entity);
        }
        return entity;
    }
}
