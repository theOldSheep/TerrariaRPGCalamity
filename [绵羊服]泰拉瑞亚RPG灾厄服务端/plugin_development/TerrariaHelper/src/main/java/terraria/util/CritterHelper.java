package terraria.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import terraria.TerrariaHelper;
import terraria.entity.others.TerrariaCritter;

import java.util.HashMap;
import java.util.List;

public class CritterHelper {
    private static Location getCritterSpawnLoc(Player ply, String critterCategory) {
        switch (critterCategory) {
            case "MagmaSnail":
            case "GroundNight":
            case "Ground": {
                Vector offsetDir = MathHelper.vectorFromYawPitch_quick(Math.random() * 360, 0);
                offsetDir.multiply(16 + Math.random() * 8);
                Location spawnLoc = ply.getLocation().add(0, Math.random() * 20 - 6, 0).add(offsetDir);
                // move downwards until the location is above ground
                int attempts = 0;
                Block currBlock = spawnLoc.getBlock();
                while (currBlock.getType().isSolid() ||
                        (! currBlock.getRelative(0, -1, 0).getType().isSolid()) ) {
                    currBlock = currBlock.getRelative(0, -1, 0);
                    // give up after many unsuccessful attempts
                    if (++attempts > 25)
                        return null;
                }
                return currBlock.getLocation().add(0.5, 0, 0.5);
            }
            default: {
                for (int i = 0; i < 4; i ++) {
                    Vector offsetDir = MathHelper.randomVector();
                    offsetDir.multiply(16 + Math.random() * 8);
                    Location spawnLoc = ply.getLocation().add(offsetDir);
                    if (spawnLoc.getBlock().getType() == Material.AIR)
                        return spawnLoc;
                }
            }
        }
        return null;
    }
    public static void naturalCritterSpawn(Player ply) {
        if (Math.random() > 0.05)
            return;
        WorldHelper.HeightLayer heightLayer = WorldHelper.HeightLayer.getHeightLayer(ply.getLocation());
        WorldHelper.BiomeType biomeType = WorldHelper.BiomeType.getBiome(ply);
        String  biomeStr = biomeType.toString().toLowerCase(),
                heightStr = heightLayer.toString().toLowerCase();
        // get spawn generic category
        String critterCategory;
        // underworld
        if (biomeType == WorldHelper.BiomeType.UNDERWORLD) {
            if (Math.random() < 0.75)
                critterCategory = "Lavafly";
            else
                critterCategory = "MagmaSnail";
        }
        // daytime
        else if (WorldHelper.isDayTime(ply.getWorld())) {
            double randomNum = Math.random();
            switch (heightLayer) {
                case SPACE:
                    if (randomNum < 0.7)
                        critterCategory = "Bird";
                    else
                        critterCategory = "Butterfly";
                    break;
                case SURFACE:
                    if (randomNum < 0.3)
                        critterCategory = "Bird";
                    else if (randomNum < 0.65)
                        critterCategory = "Butterfly";
                    else
                        critterCategory = "Ground";
                    break;
                default:
                    critterCategory = "Ground";
            }
        }
        // night
        else {
            double randomNum = Math.random();
            switch (heightLayer) {
                case SPACE:
                    critterCategory = "Firefly";
                    break;
                case SURFACE:
                    if (randomNum < 0.55)
                        critterCategory = "Firefly";
                    else if (randomNum < 0.65)
                        critterCategory = "GroundNight";
                    else
                        critterCategory = "Ground";
                    break;
                default:
                    if (randomNum < 0.35)
                        critterCategory = "GroundNight";
                    else
                        critterCategory = "Ground";
            }
        }
        // get possible critters to spawn
        ConfigurationSection spawnCandidateSection = TerrariaHelper.animalConfig.getConfigurationSection(
                "subTypes." + critterCategory);
        HashMap<String, Double> candidates = new HashMap<>();
        for (String type : spawnCandidateSection.getKeys(false)) {
            ConfigurationSection currTypeSection = spawnCandidateSection.getConfigurationSection(type);
            List<String> biomesAllowed = currTypeSection.getStringList("biome");
            if (! (biomesAllowed.isEmpty() || biomesAllowed.contains(biomeStr)) )
                continue;
            List<String> heightsAllowed = currTypeSection.getStringList("height");
            if (! (heightsAllowed.isEmpty() || heightsAllowed.contains(heightStr)) )
                continue;
            candidates.put(type, currTypeSection.getDouble("weight", 1d) );
        }
        if (candidates.size() > 0) {
            String spawnCritterType = MathHelper.selectWeighedRandom(candidates);
            Location spawnLoc = getCritterSpawnLoc(ply, critterCategory);
            if (spawnLoc != null)
                spawnCritter(spawnCritterType, spawnLoc, critterCategory);
        }
    }
    public static void spawnCritter(String type, Location loc, String category) {
        new TerrariaCritter(type, loc, category);
    }
}
