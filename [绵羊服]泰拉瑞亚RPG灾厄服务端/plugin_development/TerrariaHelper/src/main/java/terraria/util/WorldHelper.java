package terraria.util;

import net.ess3.api.events.UserWarpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import terraria.TerrariaHelper;

public class WorldHelper {
    public static int getMoonPhase() {
        World overworld = Bukkit.getWorld(TerrariaHelper.Constants.WORLD_NAME_SURFACE);
        if (overworld != null) {
            return (int) ((overworld.getFullTime() / 24000) % 8);
        }
        return 0;
    }
    public static boolean isDayTime(World wld) {
        return isDayTime(wld.getTime());
    }
    public static boolean isDayTime(long timeInTick) {
        return ! (MathHelper.isBetween(timeInTick, 13500, 22500));
    }
    public static String getBiome(Location loc) {
//        String height = getHeightLayer(loc);
//        if (height.equals("space")) {
//            return "space";
//        }
        Biome biome = loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockZ());
        switch (biome) {
//            case HELL:
//                return "underworld";
//            case SAVANNA:
//                return "brimstone_crag";
//            case MUSHROOM_ISLAND:
//            case MUSHROOM_ISLAND_SHORE:
//                return "corruption";
//            case ICE_FLATS:
//            case MUTATED_ICE_FLATS:
//                return "hallow";
//            case MESA:
//            case MUTATED_MESA:
//                return "astral_infection";
//            case DESERT:
//                return "desert";
//            case MUTATED_DESERT:
//                return "sunken_sea";
//            case BEACHES:
//            case OCEAN:
//                return "ocean";
//            case FROZEN_OCEAN:
//            case COLD_BEACH:
//                return "sulphurous_ocean";
//            case DEEP_OCEAN:
//                return "abyss";
//            case TAIGA_COLD:
//            case MUTATED_TAIGA_COLD:
//                return "tundra";
//            case JUNGLE:
//            case MUTATED_JUNGLE:
//                return "jungle";
            default:
                return "normal";
        }
    }
    public enum HeightLayer {
        SPACE, SURFACE, UNDERGROUND, CAVERN, UNDERWORLD;
        public static HeightLayer getHeightLayer(Location loc) {
            switch (loc.getWorld().getName()) {
                case TerrariaHelper.Constants.WORLD_NAME_SURFACE: {
                    if (loc.getY() > 200) return SPACE;
                    if (loc.getY() > 50) return SURFACE;
                    return UNDERGROUND;
                }
                case TerrariaHelper.Constants.WORLD_NAME_CAVERN: {
                    return CAVERN;
                }
                case TerrariaHelper.Constants.WORLD_NAME_UNDERWORLD: {
                    return UNDERWORLD;
                }
                default:
                    return SURFACE;
            }
        }
    }
    public enum BiomeType {
        ABYSS, ASTRAL_INFECTION, BRIMSTONE_CRAG, CORRUPTION, DESERT, DUNGEON, HALLOW, JUNGLE, NORMAL, OCEAN,
        SPACE, SULPHUROUS_OCEAN, SUNKEN_SEA, TEMPLE, TUNDRA, UNDERWORLD;
        public static BiomeType getBiome(Location loc) {
            return getBiome(loc, true);
        }
        public static BiomeType getBiome(Location loc, boolean considerSpace) {
            if (considerSpace) {
                HeightLayer height = HeightLayer.getHeightLayer(loc);
                if (height == HeightLayer.SPACE) {
                    return SPACE;
                }
            }
            Biome biome = loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockZ());
            switch (biome) {
                case HELL:
                    return UNDERWORLD;
                case SAVANNA:
                    return BRIMSTONE_CRAG;
                case MUSHROOM_ISLAND:
                case MUSHROOM_ISLAND_SHORE:
                    return CORRUPTION;
                case ICE_FLATS:
                case MUTATED_ICE_FLATS:
                    return HALLOW;
                case MESA:
                case MUTATED_MESA:
                    return ASTRAL_INFECTION;
                case DESERT:
                    return DESERT;
                case MUTATED_DESERT:
                    return SUNKEN_SEA;
                case BEACHES:
                case OCEAN:
                    return OCEAN;
                case FROZEN_OCEAN:
                case COLD_BEACH:
                    return SULPHUROUS_OCEAN;
                case DEEP_OCEAN:
                    return ABYSS;
                case TAIGA_COLD:
                case MUTATED_TAIGA_COLD:
                    return TUNDRA;
                case JUNGLE:
                case MUTATED_JUNGLE:
                    return JUNGLE;
                default:
                    return NORMAL;
            }
        }
    }
}
