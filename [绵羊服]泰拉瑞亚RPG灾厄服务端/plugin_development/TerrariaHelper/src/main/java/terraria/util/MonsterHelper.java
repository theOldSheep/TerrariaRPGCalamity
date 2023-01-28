package terraria.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import terraria.TerrariaHelper;
import terraria.entity.boss.Boss;
import terraria.gameplay.Event;

import java.util.Collection;
import java.util.HashMap;

public class MonsterHelper {
    private static boolean naturalMobSpawnType(Player ply, String spawnType) {
        // determine the monster spawning location
        WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(ply.getLocation());
        Location spawnLoc;
        int adjustHeight;
        if (heightLayer == WorldHelper.HeightLayer.SURFACE) {
            spawnLoc = ply.getLocation().add(Math.random() * 96 - 48, Math.random() * 20 - 10, Math.random() * 96 - 48);
            adjustHeight = 32;
        } else {
            spawnLoc = ply.getLocation().add(Math.random() * 64 - 32, Math.random() * 24 - 12, Math.random() * 64 - 32);
            adjustHeight = 8;
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
                    boolean currLocValid = spawnLoc.getY() >= 255;
                    Material currBlockMat = spawnLoc.getBlock().getType();
                    // no spawning in water!
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
        }
        // finally, handle some special cases
        if (spawnType.equals("史莱姆雨"))
            spawnLoc.add(0, 30, 0);
        if (monsterSpawn.equals("拜月教教徒") && BossHelper.bossMap.containsKey("拜月教邪教徒"))
            return true;
        // prevent spawning next to any player
        double noMonsterSpawnRadius = 16;
        for (Entity e : spawnLoc.getWorld().getNearbyEntities(spawnLoc, noMonsterSpawnRadius, noMonsterSpawnRadius, noMonsterSpawnRadius)) {
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
        if (EntityHelper.getMetadata(ply, "mobAmount").asInt() >= mobLimit)
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
        // regular mob spawning
        if (Event.currentEvent.length() > 0 && Math.random() < 0.5) {
            return;
        }
        // only attempt to spawn underworld mobs in underworld
        WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply.getLocation());
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
    public static void spawnMob(String type, Location loc, Player target) {
        // TODO
    }
}
